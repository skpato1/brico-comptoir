package tn.bricocomptoir.privacy.adapter.transaction;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class TokenCleanupWorker {
    private final PrivacyTransactions privacy;
    public TokenCleanupWorker(PrivacyTransactions privacy) { this.privacy=privacy; }
    @Scheduled(initialDelay=3600000,fixedDelay=3600000)
    public void run() {
        try { privacy.purgeTokens(); }
        catch(RuntimeException unavailable) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("TOKEN_CLEANUP_FAILED"); }
    }
}
