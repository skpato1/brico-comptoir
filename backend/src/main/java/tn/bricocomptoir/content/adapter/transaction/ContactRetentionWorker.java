package tn.bricocomptoir.content.adapter.transaction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContactRetentionWorker {
    private final JdbcTemplate jdbc;
    private final int days;
    public ContactRetentionWorker(JdbcTemplate jdbc,
            @Value("${brico.contact.message-retention-days:0}") int days) {
        if (days < 0 || days > 3650) throw new IllegalArgumentException("Invalid contact retention");
        this.jdbc = jdbc;
        this.days = days;
    }
    @Scheduled(initialDelay = 3600000, fixedDelay = 86400000)
    @Transactional
    public void run() {
        if (days > 0) jdbc.update("DELETE FROM content_contact_message WHERE created_at < now() - (? * interval '1 day')", days);
    }
}
