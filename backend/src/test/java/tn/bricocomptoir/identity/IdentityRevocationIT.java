package tn.bricocomptoir.identity;

import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.identity.application.port.out.AccountStore;
import tn.bricocomptoir.identity.domain.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = BricoComptoirApplication.class)
class IdentityRevocationIT {
    private static final String PASSWORD = UUID.randomUUID().toString();
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withUsername("migrator").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE audit_app LOGIN PASSWORD '" + PASSWORD + "'");
        }
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", () -> "audit_app");
        registry.add("DB_PASSWORD", () -> PASSWORD);
        registry.add("DB_MIGRATION_USERNAME", POSTGRES::getUsername);
        registry.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
    }
    @Autowired IdentityTransactions identity;
    @Autowired AccountStore accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @ParameterizedTest @ValueSource(strings = {"roles", "active", "create"})
    void queuedAdministrativeWriteCannotOutliveRoleRevocation(String action) throws Exception {
        UUID root = UUID.randomUUID(), actor = UUID.randomUUID(), target = UUID.randomUUID();
        String newEmail = UUID.randomUUID() + "@test.invalid";
        var transaction = new TransactionTemplate(manager);
        transaction.executeWithoutResult(status -> {
            for (var id : new UUID[]{root, actor, target}) accounts.insert(new Account(id, id + "@test.invalid",
                    "!TEST_FIXTURE", Set.of(id.equals(target) ? Role.CUSTOMER : Role.ADMIN), true, 0));
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = new java.util.concurrent.atomic.AtomicReference<Future<?>>();
            transaction.executeWithoutResult(status -> {
                accounts.lockAdministration();
                future.set(pool.submit(() -> {
                    switch (action) {
                        case "roles" -> identity.changeRoles(actor, actor, Set.of(Role.ADMIN));
                        case "active" -> identity.changeActive(actor, target, false);
                        default -> identity.createInternal(actor, newEmail, "Long-Password-2026!", Set.of(Role.ADMIN));
                    }
                }));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND objid=738462914 AND NOT granted", Long.class) == 0) {
                    if (future.get().isDone() || System.nanoTime() > deadline) fail("Administrative mutation did not wait for the lock");
                    java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
                }
                identity.changeRoles(root, actor, Set.of(Role.CATALOG_MANAGER));
            });
            assertThatThrownBy(() -> future.get().get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(SecurityException.class);
            assertThat(accounts.byId(actor).orElseThrow().roles()).containsExactly(Role.CATALOG_MANAGER);
            assertThat(accounts.byId(target).orElseThrow().active()).isTrue();
            assertThat(accounts.byEmail(newEmail)).isEmpty();
        }
    }
}
