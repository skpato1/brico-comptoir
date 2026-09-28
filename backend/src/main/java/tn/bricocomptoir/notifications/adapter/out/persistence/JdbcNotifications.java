package tn.bricocomptoir.notifications.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tn.bricocomptoir.notifications.application.port.in.NotificationOperations.Email;
import tn.bricocomptoir.notifications.application.port.in.OrderEventQueries;
import tn.bricocomptoir.notifications.application.port.out.OutboxStore;
import tn.bricocomptoir.notifications.domain.*;

@Component
public class JdbcNotifications implements OutboxStore,OrderEventQueries {
    private final JdbcTemplate jdbc;
    private final PayloadCipher cipher;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    public JdbcNotifications(JdbcTemplate jdbc, PayloadCipher cipher, ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.cipher=cipher;this.json=json;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(10);
    }
    public void enqueue(Email e) {
        Mail.validate(e.key(),e.recipient(),e.subject(),e.text(),e.resetScope());
        UUID id=UUID.randomUUID();
        byte[] payload=cipher.encrypt(id.toString(),json.writeValueAsString(new Payload(e.recipient(),e.subject(),e.text())));
        jdbc.update("INSERT INTO notification_mail_outbox(id,dedup_key,reset_scope,payload,expires_at) VALUES (?,?,?,?,?) ON CONFLICT(dedup_key) DO NOTHING",
            id,e.key(),e.resetScope(),payload,ts(e.expiresAt()));
    }
    public void cancelReset(String scope) {
        if (scope==null || !scope.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid scope");
        jdbc.update("UPDATE notification_mail_outbox SET state='CANCELLED',payload=NULL,lease_token=NULL,lease_until=NULL "
            +"WHERE reset_scope=? AND state IN ('PENDING','PROCESSING','FAILED')",scope);
    }
    public void created(UUID orderId) {
        // No recipient data is included in these manager events.
        if (jdbc.queryForObject("SELECT count(*) FROM notification_order_event WHERE order_id=?",Long.class,orderId)>0) return;
        Long next=jdbc.queryForObject("UPDATE notification_event_cursor SET last_id=last_id+1 WHERE id=1 RETURNING last_id",Long.class);
        jdbc.update("INSERT INTO notification_order_event(id,order_id,type) VALUES (?,?,'ORDER_CREATED')",next,orderId);
    }
    public void associate(String key,String scope) {
        if(scope==null || !scope.matches("[CG]:[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid scope");
        jdbc.update("UPDATE notification_mail_outbox SET subject_scope=? WHERE dedup_key=?",scope,key);
    }
    @Override public Optional<Mail> claim(Instant now) {
        return transaction.execute(status -> {
            jdbc.update("UPDATE notification_mail_outbox SET state='CANCELLED',payload=NULL,lease_token=NULL,lease_until=NULL,last_error='EXPIRED' "
                +"WHERE state IN ('PENDING','PROCESSING','FAILED') AND expires_at<=?",ts(now));
            // A worker that died on its final attempt must become terminal after its lease expires.
            jdbc.update("UPDATE notification_mail_outbox SET state='FAILED',lease_token=NULL,lease_until=NULL,last_error='LEASE_EXPIRED' "
                +"WHERE state='PROCESSING' AND attempts=5 AND lease_until<=?",ts(now));
            var ids=jdbc.query("SELECT id FROM notification_mail_outbox WHERE attempts<5 AND "
                +"((state='PENDING' AND next_attempt_at<=?) OR (state='PROCESSING' AND lease_until<=?)) "
                +"ORDER BY next_attempt_at,created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",
                (rs,n)->rs.getObject(1,UUID.class),ts(now),ts(now));
            if(ids.isEmpty()) return Optional.empty();
            UUID id=ids.getFirst(),lease=UUID.randomUUID();
            jdbc.update("UPDATE notification_mail_outbox SET state='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=? WHERE id=?",
                lease,ts(now.plus(DeliveryPolicy.LEASE)),id);
            return jdbc.queryForObject("SELECT payload,attempts,expires_at FROM notification_mail_outbox WHERE id=?",(rs,n)-> {
                try {
                    var payload=json.readValue(cipher.decrypt(id.toString(),rs.getBytes(1)),Payload.class);
                    var expiry=rs.getTimestamp(3);
                    return Optional.of(new Mail(id,lease,rs.getInt(2),payload.recipient(),payload.subject(),payload.text(),expiry==null?null:expiry.toInstant()));
                } catch(RuntimeException unreadable) {
                    jdbc.update("UPDATE notification_mail_outbox SET state='FAILED',last_error='PAYLOAD_UNREADABLE',lease_token=NULL,lease_until=NULL WHERE id=?",id);
                    return Optional.<Mail>empty();
                }
            },id);
        });
    }
    @Override public boolean current(Mail mail, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM notification_mail_outbox WHERE id=? AND state='PROCESSING' AND lease_token=? AND lease_until>? AND (expires_at IS NULL OR expires_at>?))",
            Boolean.class,mail.id(),mail.leaseToken(),ts(now),ts(now)));
    }
    @Override public void sent(Mail mail, Instant now) {
        transaction.executeWithoutResult(s->jdbc.update("UPDATE notification_mail_outbox SET state='SENT',sent_at=?,payload=NULL,last_error=NULL,lease_token=NULL,lease_until=NULL "
            +"WHERE id=? AND state='PROCESSING' AND lease_token=?",ts(now),mail.id(),mail.leaseToken()));
    }
    @Override public void failed(Mail mail, Instant now) {
        transaction.executeWithoutResult(s->jdbc.update("UPDATE notification_mail_outbox SET state=?,next_attempt_at=?,last_error='PROVIDER_UNAVAILABLE',lease_token=NULL,lease_until=NULL "
            +"WHERE id=? AND state='PROCESSING' AND lease_token=?",mail.attempt()>=DeliveryPolicy.MAX_ATTEMPTS?"FAILED":"PENDING",
            ts(now.plus(DeliveryPolicy.retryDelay(mail.attempt()))),mail.id(),mail.leaseToken()));
    }
    public long latest() { return jdbc.queryForObject("SELECT last_id FROM notification_event_cursor WHERE id=1",Long.class); }
    public List<Event> events(long after,int size) {
        if(after<0 || size<1 || size>100 || after>latest()) throw new IllegalArgumentException("Invalid cursor");
        return jdbc.query("SELECT id,order_id,type,created_at FROM notification_order_event WHERE id>? ORDER BY id LIMIT ?",
            (rs,n)->new Event(rs.getLong(1),rs.getObject(2,UUID.class),rs.getString(3),rs.getTimestamp(4).toInstant()),after,size);
    }
    public record Metadata(UUID id,String key,String state,int attempts,Instant nextAttemptAt,Instant createdAt,Instant sentAt,String lastError) { }
    public record Page(List<Metadata> items,int page,int size,long totalElements) { }
    public Page page(int page,int size) {
        if(page<0 || page>100000 || size<1 || size>100) throw new IllegalArgumentException("Invalid page");
        var rows=jdbc.query("SELECT * FROM notification_mail_outbox ORDER BY created_at DESC,id LIMIT ? OFFSET ?",(rs,n)->
            new Metadata(rs.getObject("id",UUID.class),rs.getString("dedup_key"),rs.getString("state"),rs.getInt("attempts"),
            rs.getTimestamp("next_attempt_at").toInstant(),rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("sent_at")==null?null:rs.getTimestamp("sent_at").toInstant(),rs.getString("last_error")),size,page*size);
        return new Page(rows,page,size,jdbc.queryForObject("SELECT count(*) FROM notification_mail_outbox",Long.class));
    }
    public boolean retry(UUID id,Instant now) {
        return jdbc.update("UPDATE notification_mail_outbox SET state='PENDING',attempts=0,next_attempt_at=?,last_error=NULL "
            +"WHERE id=? AND state='FAILED' AND payload IS NOT NULL AND (expires_at IS NULL OR expires_at>?)",ts(now),id,ts(now))==1;
    }
    public record Payload(String recipient,String subject,String text) { }
    private Timestamp ts(Instant instant) { return instant==null?null:Timestamp.from(instant); }
}
