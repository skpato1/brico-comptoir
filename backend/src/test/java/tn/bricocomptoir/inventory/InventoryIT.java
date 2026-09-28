package tn.bricocomptoir.inventory;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import tn.bricocomptoir.catalog.adapter.transaction.CatalogTransactions;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.identity.domain.Role;
import tn.bricocomptoir.inventory.adapter.transaction.InventoryTransactions;
import tn.bricocomptoir.inventory.domain.InventoryModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = BricoComptoirApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryIT {
    private static final String USER = "inventory_test_app";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String LOGIN_PASSWORD = "Correct-Horse-2026!";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_inventory_test").withUsername("migrator")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + USER + " LOGIN PASSWORD '" + PASSWORD + "'");
        }
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", () -> USER);
        registry.add("DB_PASSWORD", () -> PASSWORD);
        registry.add("DB_MIGRATION_USERNAME", POSTGRES::getUsername);
        registry.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired CatalogTransactions catalog;
    @Autowired InventoryTransactions inventory;
    @Autowired IdentityTransactions identity;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @MockitoBean JavaMailSender mail;

    @Test void reserveReleaseConsumeAreAtomicAndIdempotent() {
        UUID first = variant(true);
        UUID second = variant(true);
        UUID actor = UUID.randomUUID();
        inventory.adjust(UUID.randomUUID(), first, 4, "Réception test", actor.toString());
        inventory.adjust(UUID.randomUUID(), second, 2, "Réception test", actor.toString());
        UUID reservation = UUID.randomUUID();
        List<Line> lines = List.of(new Line(second, 1), new Line(first, 2), new Line(first, 1));
        assertThat(inventory.reserve(reservation, lines).status()).isEqualTo(Status.ACTIVE);
        assertThat(inventory.stock(first)).isEqualTo(new Stock(first, 4, 3));
        assertThat(inventory.stock(second)).isEqualTo(new Stock(second, 2, 1));
        int beforeReplay = movementCount(reservation);
        assertThat(inventory.reserve(reservation, lines).status()).isEqualTo(Status.ACTIVE);
        assertThat(movementCount(reservation)).isEqualTo(beforeReplay);
        assertThatThrownBy(() -> inventory.reserve(reservation, List.of(new Line(first, 1))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(inventory.consume(reservation).status()).isEqualTo(Status.CONSUMED);
        assertThat(inventory.stock(first)).isEqualTo(new Stock(first, 1, 0));
        assertThat(inventory.stock(second)).isEqualTo(new Stock(second, 1, 0));
        int afterConsume = movementCount(reservation);
        inventory.consume(reservation);
        assertThat(movementCount(reservation)).isEqualTo(afterConsume);
        assertThatThrownBy(() -> inventory.release(reservation)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inventory.reserve(reservation, lines)).isInstanceOf(IllegalStateException.class);

        UUID next = UUID.randomUUID();
        inventory.reserve(next, List.of(new Line(first, 1)));
        inventory.release(next);
        inventory.release(next);
        assertThat(inventory.stock(first)).isEqualTo(new Stock(first, 1, 0));
        assertThatThrownBy(() -> inventory.consume(next)).isInstanceOf(IllegalStateException.class);
    }

    @Test void insufficientSecondSkuRollsBackWholeTransaction() {
        UUID first = variant(true);
        UUID second = variant(true);
        UUID operation = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        assertThatThrownBy(() -> transactions.executeWithoutResult(ignored -> {
            inventory.adjust(operation, first, 1, "Préparation test", "admin@test.invalid");
            inventory.reserve(reservation, List.of(new Line(first, 1), new Line(second, 1)));
        })).isInstanceOf(IllegalStateException.class);
        assertThat(inventory.stock(first)).isEqualTo(new Stock(first, 0, 0));
        assertThat(inventory.stock(second)).isEqualTo(new Stock(second, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation WHERE id = ?",
                Integer.class, reservation)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_adjustment WHERE id = ?",
                Integer.class, operation)).isZero();
    }

    @Test void twoTransactionsCannotReserveTheLastUnitTwice() throws Exception {
        UUID sku = variant(true);
        inventory.adjust(UUID.randomUUID(), sku, 1, "Dernière unité test", "admin@test.invalid");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                try {
                    inventory.reserve(UUID.randomUUID(), List.of(new Line(sku, 1)));
                    return true;
                } catch (IllegalStateException insufficient) {
                    return false;
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long successes = 0;
            for (var task : tasks) if (task.get(20, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(inventory.stock(sku)).isEqualTo(new Stock(sku, 1, 1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation_line WHERE variant_id = ?",
                Integer.class, sku)).isEqualTo(1);
    }

    @Test void postgresConstraintsAndHttpPermissionsHold() throws Exception {
        UUID published = variant(true);
        UUID draft = variant(false);
        assertThat(new Browser().get("/availability/" + published).body()).contains("\"available\":0");
        assertThat(new Browser().get("/availability/" + draft).statusCode()).isEqualTo(404);
        assertThat(new Browser().get("/admin/stock/" + published).statusCode()).isEqualTo(401);

        String adminEmail = "inventory-admin-" + UUID.randomUUID() + "@example.tn";
        var admin = identity.bootstrapAdmin(adminEmail, LOGIN_PASSWORD);
        var manager = identity.createInternal(admin.id(), "inventory-catalog-" + UUID.randomUUID()
                + "@example.tn", LOGIN_PASSWORD, Set.of(Role.CATALOG_MANAGER));
        Browser catalogManager = new Browser(); catalogManager.login(manager.email());
        Browser root = new Browser(); root.login(adminEmail);
        String adjustment = "{\"operationId\":\"" + UUID.randomUUID() + "\",\"variantId\":\""
                + published + "\",\"delta\":2,\"reason\":\"Réception test\"}";
        assertThat(catalogManager.get("/admin/stock/" + published).statusCode()).isEqualTo(403);
        assertThat(catalogManager.post("/admin/stock/adjustments", adjustment).statusCode()).isEqualTo(403);
        assertThat(root.postWithoutCsrf("/admin/stock/adjustments", adjustment).statusCode()).isEqualTo(403);
        assertThat(root.post("/admin/stock/adjustments", adjustment).statusCode()).isEqualTo(200);
        assertThat(root.post("/admin/stock/adjustments", adjustment).statusCode()).isEqualTo(200);
        assertThat(inventory.stock(published)).isEqualTo(new Stock(published, 2, 0));
        assertThat(root.get("/admin/stock/" + draft).statusCode()).isEqualTo(200);
        assertThat(new Browser().get("/availability/" + published).body()).contains("\"available\":2");
        assertThat(root.post("/admin/stock/adjustments", adjustment.replace("\"delta\":2", "\"delta\":3"))
                .statusCode()).isEqualTo(409);
        assertThat(root.post("/admin/stock/adjustments", adjustment.replace("\"delta\":2", "\"delta\":-3"))
                .statusCode()).isEqualTo(409);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_stock SET reserved = 3 WHERE variant_id = ?",
                published)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_movement WHERE variant_id = ?", published))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .satisfies(error -> assertThat(((java.sql.SQLException) error.getCause()).getSQLState())
                        .isEqualTo("42501"));
    }

    private UUID variant(boolean published) {
        String token = UUID.randomUUID().toString().replace("-", "");
        var category = catalog.saveCategory(null, null, "inventory-" + token, "Catégorie test", true, null);
        var product = catalog.saveProduct(null, category.id(), null, "Produit test", "", Map.of(),
                published ? "PUBLISHED" : "DRAFT", null);
        return catalog.saveVariant(null, product.id(), "INV" + token.toUpperCase(), "Variante test",
                "pièce", Map.of(), "1.000", published ? "PUBLISHED" : "DRAFT", null).id();
    }

    private int movementCount(UUID reservation) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE reservation_id = ?",
                Integer.class, reservation);
    }

    private final class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path) throws Exception { return request("GET", path, null, false); }
        HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body, true); }
        HttpResponse<String> postWithoutCsrf(String path, String body) throws Exception {
            return request("POST", path, body, false);
        }
        void login(String email) throws Exception {
            get("/auth/csrf");
            assertThat(post("/auth/login", "{\"email\":\"" + email + "\",\"password\":\""
                    + LOGIN_PASSWORD + "\"}").statusCode()).isEqualTo(200);
            get("/auth/csrf");
        }
        private HttpResponse<String> request(String method, String path, String body, boolean csrf) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
            if (body != null) builder.header("Content-Type", "application/json");
            if (csrf) cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("XSRF-TOKEN")).findFirst()
                    .ifPresent(cookie -> builder.header("X-XSRF-TOKEN", cookie.getValue()));
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
