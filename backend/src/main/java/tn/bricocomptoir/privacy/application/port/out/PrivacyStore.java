package tn.bricocomptoir.privacy.application.port.out;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
public interface PrivacyStore {
    record Consent(boolean marketing,String noticeVersion,Instant updatedAt) { }
    Consent consent(UUID id);
    void consent(UUID id,boolean value,String version);
    void audit(String scope,UUID actor,String action,String version);
    void holdAudit(UUID actor,UUID orderId,boolean held,String reason);
    void archiveAudit(UUID actor,UUID orderId,String caseReference);
    String history(UUID id,int page);
    int purgeTokens(Instant now);
    void pending(UUID id,String email,String hash,Instant expiresAt);
    Optional<String> consume(UUID id,String hash,Instant now);
    void removePending(UUID id);
    int retain(Instant now,int auditDays);
}
