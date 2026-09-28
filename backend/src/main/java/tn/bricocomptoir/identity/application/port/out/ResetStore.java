package tn.bricocomptoir.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ResetStore {
    void save(String tokenHash, UUID accountId, Instant expiresAt);
    Optional<UUID> consume(String tokenHash, Instant now);
    Optional<UUID> accountFor(String tokenHash);
    void deleteForAccount(UUID accountId);
}
