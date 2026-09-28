package tn.bricocomptoir.privacy.domain;

import java.time.Duration;
import java.time.Instant;

/** Technical durations, not a statement of statutory retention requirements. */
public record PrivacyPolicy(int contactDays, int cartDays, int mailDays, int auditDays,
                            int legalDays, boolean retentionEnabled) {
    public PrivacyPolicy {
        if(contactDays<1 || contactDays>36500 || cartDays<1 || cartDays>36500 || mailDays<1 || mailDays>36500 || auditDays<1 || auditDays>36500 || legalDays<0 || legalDays>36500
            || (retentionEnabled && legalDays==0)) throw new IllegalArgumentException("INVALID_RETENTION_POLICY");
    }
    public Instant cutoff(Instant now,int days) { return now.minus(Duration.ofDays(days)); }
    public Instant legalUntil(Instant orderCreated) {
        if(legalDays==0) throw new IllegalStateException("LEGAL_RETENTION_NOT_CONFIGURED");
        return orderCreated.plus(Duration.ofDays(legalDays));
    }
    public static void page(int page) {
        if(page<0 || page>100000) throw new IllegalArgumentException("INVALID_PAGE");
    }
}
