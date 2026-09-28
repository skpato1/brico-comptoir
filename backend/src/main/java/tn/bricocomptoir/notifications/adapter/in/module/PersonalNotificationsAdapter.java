package tn.bricocomptoir.notifications.adapter.in.module;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.notifications.application.port.in.PersonalNotifications;
@Service @Transactional
public class PersonalNotificationsAdapter implements PersonalNotifications {
    private final JdbcTemplate jdbc;
    public PersonalNotificationsAdapter(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void cancelOrders(String scope) {
        jdbc.update("UPDATE notification_mail_outbox SET state='CANCELLED',payload=NULL,lease_token=NULL,lease_until=NULL WHERE subject_scope=? AND state IN ('PENDING','PROCESSING','FAILED')",scope);
    }
    public void cancelEmailChange(UUID id) {
        jdbc.update("UPDATE notification_mail_outbox SET state='CANCELLED',payload=NULL,lease_token=NULL,lease_until=NULL WHERE dedup_key LIKE ? AND state IN ('PENDING','PROCESSING','FAILED')","EMAIL_CHANGE:"+id+":%");
    }
    public int retain(Instant now,int days) {
        return jdbc.update("DELETE FROM notification_mail_outbox WHERE created_at<? AND (state<>'PROCESSING' OR lease_until<?)",
            java.sql.Timestamp.from(now.minus(Duration.ofDays(days))),java.sql.Timestamp.from(now));
    }
}
