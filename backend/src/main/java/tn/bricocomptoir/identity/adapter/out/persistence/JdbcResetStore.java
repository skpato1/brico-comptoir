package tn.bricocomptoir.identity.adapter.out.persistence;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.identity.application.port.out.ResetStore;

@Repository
public class JdbcResetStore implements ResetStore {
    private final JdbcTemplate jdbc;

    public JdbcResetStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void save(String tokenHash, UUID accountId, Instant expiresAt) {
        jdbc.update("INSERT INTO identity_password_reset (token_hash, account_id, expires_at) VALUES (?, ?, ?)",
                tokenHash, accountId, Timestamp.from(expiresAt));
    }

    @Override
    public Optional<UUID> consume(String tokenHash, Instant now) {
        return jdbc.query("DELETE FROM identity_password_reset WHERE token_hash = ? AND expires_at > ? "
                + "RETURNING account_id", (rs, row) -> (UUID) rs.getObject(1), tokenHash, Timestamp.from(now))
                .stream().findFirst();
    }

    @Override
    public void deleteForAccount(UUID accountId) {
        jdbc.update("DELETE FROM identity_password_reset WHERE account_id = ?", accountId);
    }
    @Override public Optional<UUID> accountFor(String hash) {
        return jdbc.query("SELECT account_id FROM identity_password_reset WHERE token_hash=?",
            (rs,n)->rs.getObject(1,UUID.class),hash).stream().findFirst();
    }
}
