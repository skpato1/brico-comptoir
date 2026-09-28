package tn.bricocomptoir.privacy.adapter.out.persistence;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.privacy.application.port.out.PrivacyStore;
import tn.bricocomptoir.privacy.application.service.PrivacyService;
@Repository
public class JdbcPrivacyStore implements PrivacyStore {
    private final JdbcTemplate jdbc;
    private final tools.jackson.databind.ObjectMapper json;
    public JdbcPrivacyStore(JdbcTemplate jdbc,tools.jackson.databind.ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    public Consent consent(UUID id) {
        return jdbc.query("SELECT marketing,notice_version,updated_at FROM privacy_consent WHERE account_id=?",
            (r,n)->new Consent(r.getBoolean(1),r.getString(2),r.getTimestamp(3).toInstant()),id).stream().findFirst()
            .orElse(new Consent(false,PrivacyService.NOTICE,null));
    }
    public void consent(UUID id,boolean value,String version) {
        jdbc.update("INSERT INTO privacy_consent(account_id,marketing,notice_version) VALUES (?,?,?) ON CONFLICT(account_id) DO UPDATE SET marketing=excluded.marketing,notice_version=excluded.notice_version,updated_at=now()",id,value,version);
    }
    public void audit(String scope,UUID actor,String action,String version) {
        jdbc.update("INSERT INTO privacy_audit(subject_scope,actor_id,action,notice_version) VALUES (?,?,?,?)",scope,actor,action,version);
    }
    public void holdAudit(UUID actor,UUID orderId,boolean held,String reason) {
        jdbc.update("INSERT INTO privacy_audit(subject_scope,actor_id,action,target_order_id,reason_code) VALUES (?,?,?,?,?)",
            "C:"+actor,actor,held?"LEGAL_HOLD_SET":"LEGAL_HOLD_RELEASED",orderId,reason);
    }
    public void archiveAudit(UUID actor,UUID orderId,String reference) {
        jdbc.update("INSERT INTO privacy_audit(subject_scope,actor_id,action,target_order_id,reason_code) VALUES (?,?,'ARCHIVE_EXPORTED',?,?)","C:"+actor,actor,orderId,reference);
    }
    public String history(UUID id,int page) {
        return json.writeValueAsString(jdbc.query("SELECT action,notice_version,created_at FROM privacy_audit WHERE subject_scope=? ORDER BY created_at,id LIMIT 100 OFFSET ?",
            (r,n)->new Action(r.getString(1),r.getString(2),r.getTimestamp(3).toInstant()),"C:"+id,page*100));
    }
    public record Action(String action,String noticeVersion,Instant createdAt) { }
    public int purgeTokens(Instant now) { return jdbc.update("DELETE FROM privacy_email_change WHERE expires_at<=?",ts(now)); }
    public void pending(UUID id,String email,String hash,Instant expiry) {
        jdbc.update("INSERT INTO privacy_email_change(account_id,new_email,token_hash,expires_at) VALUES (?,?,?,?) ON CONFLICT(account_id) DO UPDATE SET new_email=excluded.new_email,token_hash=excluded.token_hash,expires_at=excluded.expires_at",id,email,hash,ts(expiry));
    }
    public Optional<String> consume(UUID id,String hash,Instant now) {
        return jdbc.query("DELETE FROM privacy_email_change WHERE account_id=? AND token_hash=? AND expires_at>? RETURNING new_email",
            (r,n)->r.getString(1),id,hash,ts(now)).stream().findFirst();
    }
    public void removePending(UUID id) { jdbc.update("DELETE FROM privacy_email_change WHERE account_id=?",id); }
    public int retain(Instant now,int days) {
        return purgeTokens(now)+
            jdbc.update("DELETE FROM privacy_audit WHERE created_at<?",ts(now.minus(Duration.ofDays(days))));
    }
    private java.sql.Timestamp ts(Instant time) { return java.sql.Timestamp.from(time); }
}
