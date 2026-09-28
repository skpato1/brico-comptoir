package tn.bricocomptoir.privacy.adapter.transaction;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
@Component @ConditionalOnProperty(name="brico.privacy.retention-enabled",havingValue="true")
public class RetentionWorker {
    private final PrivacyTransactions privacy;
    public RetentionWorker(PrivacyTransactions privacy) { this.privacy=privacy; }
    @Scheduled(initialDelay=3600000,fixedDelay=3600000)
    public void run() {
        try { privacy.retain(); }
        catch(RuntimeException unavailable) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("RETENTION_FAILED"); }
    }
}
