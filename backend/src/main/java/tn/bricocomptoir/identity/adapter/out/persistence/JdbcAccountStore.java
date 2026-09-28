package tn.bricocomptoir.identity.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.identity.application.port.out.AccountStore;
import tn.bricocomptoir.identity.domain.Account;
import tn.bricocomptoir.identity.domain.Role;

@Repository
public class JdbcAccountStore implements AccountStore {
    private final JdbcTemplate jdbc;

    public JdbcAccountStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Account> byEmail(String email) {
        return jdbc.query("SELECT * FROM identity_account WHERE email = ?", this::map, email).stream().findFirst();
    }

    @Override
    public Optional<Account> byId(UUID id) {
        return jdbc.query("SELECT * FROM identity_account WHERE id = ?", this::map, id).stream().findFirst();
    }
    @Override public void lockAccount(UUID id) {
        jdbc.queryForObject("SELECT id FROM identity_account WHERE id=? FOR UPDATE",UUID.class,id);
    }

    private Account map(ResultSet rs, int row) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        List<Role> roles = jdbc.query("SELECT role FROM identity_role WHERE account_id = ?",
                (r, index) -> Role.valueOf(r.getString(1)), id);
        return new Account(id, rs.getString("email"), rs.getString("password_hash"),
                Set.copyOf(roles), rs.getBoolean("active"), rs.getLong("version"));
    }

    @Override
    public void insert(Account account) {
        jdbc.update("INSERT INTO identity_account (id, email, password_hash, active, version) VALUES (?, ?, ?, ?, ?)",
                account.id(), account.email(), account.passwordHash(), account.active(), account.version());
        insertRoles(account);
    }

    @Override
    public void update(Account account) {
        int updated = jdbc.update("UPDATE identity_account SET email = ?, password_hash = ?, active = ?, version = ? "
                        + "WHERE id = ? AND version = ?", account.email(), account.passwordHash(), account.active(),
                account.version(), account.id(), account.version() - 1);
        if (updated != 1) throw new IllegalStateException("Account changed concurrently");
        jdbc.update("DELETE FROM identity_role WHERE account_id = ?", account.id());
        insertRoles(account);
    }

    private void insertRoles(Account account) {
        for (Role role : account.roles()) {
            jdbc.update("INSERT INTO identity_role (account_id, role) VALUES (?, ?)", account.id(), role.name());
        }
    }

    @Override
    public void lockAdministration() {
        jdbc.execute("SELECT pg_advisory_xact_lock(738462914)");
    }

    @Override
    public long activeAdminCount() {
        return jdbc.queryForObject("SELECT count(*) FROM identity_account a JOIN identity_role r ON r.account_id = a.id "
                + "WHERE a.active AND r.role = 'ADMIN'", Long.class);
    }

    @Override
    public void audit(UUID actor, UUID target, String action) {
        jdbc.update("INSERT INTO identity_access_audit (actor_id, target_id, action) VALUES (?, ?, ?)",
                actor, target, action);
    }
}
