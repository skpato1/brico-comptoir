package tn.bricocomptoir.sales;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import tn.bricocomptoir.catalog.adapter.transaction.CatalogTransactions;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.identity.domain.Role;
import tn.bricocomptoir.inventory.adapter.transaction.InventoryTransactions;
import tn.bricocomptoir.packs.adapter.transaction.PackTransactions;
import tn.bricocomptoir.packs.domain.PackModels.Component;
import tn.bricocomptoir.sales.adapter.out.persistence.JdbcOrderStore;
import tn.bricocomptoir.sales.domain.OrderModels.Order;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = BricoComptoirApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderIT {
    private static final String USER = "order_test_app", PASSWORD = UUID.randomUUID().toString();
    private static final String LOGIN_PASSWORD = "Correct-Horse-2026!";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_order_test").withUsername("migrator").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) throws Exception {
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var s = c.createStatement()) { s.execute("CREATE ROLE " + USER + " LOGIN PASSWORD '" + PASSWORD + "'"); }
        r.add("DB_URL", POSTGRES::getJdbcUrl); r.add("DB_USERNAME", () -> USER); r.add("DB_PASSWORD", () -> PASSWORD);
        r.add("DB_MIGRATION_USERNAME", POSTGRES::getUsername); r.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired CatalogTransactions catalog;
    @Autowired PackTransactions packs;
    @Autowired InventoryTransactions inventory;
    @Autowired IdentityTransactions identity;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender mail;
    @MockitoSpyBean JdbcOrderStore store;
    private static UUID administratorId;
    private static final String ADDRESS = "{\"recipient\":\"Client test\",\"phone\":\"+21620123456\","
            + "\"street\":\"Rue test\",\"city\":\"Tunis\",\"postalCode\":\"1000\",\"governorate\":\"TUNIS\",\"country\":\"TN\"}";

    @Test void guestPackCartOrderSharesSkuAndDoubleClickCreatesOneImmutableOrder() throws Exception {
        Fixture f = fixture(10);
        Browser guest = new Browser(); guest.csrf();
        String cart = "{\"items\":[{\"kind\":\"PRODUCT\",\"offerId\":\"" + f.sku + "\",\"quantity\":1},"
                + "{\"kind\":\"PACK\",\"offerId\":\"" + f.packVariant + "\",\"quantity\":1}]}";
        assertThat(guest.post("/cart/estimate", cart).body()).contains("\"amount\":\"12.375\"");
        String body = previewBody(product(f, 1), pack(f, 1));
        var preview = guest.post("/checkout/preview", body);
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.body()).contains("\"amount\":\"19.375\"", "\"amount\":\"7.000\"", f.skuCode);
        assertThat(guest.request("POST", "/checkout/preview", body, null, false).statusCode()).isEqualTo(403);
        String confirmed = confirmed(body, preview);
        UUID key = UUID.randomUUID();
        var results = race(() -> guest.place(confirmed, key), () -> guest.place(confirmed, key));
        assertThat(results).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(201));
        UUID id = id(results.getFirst());
        assertThat(id(results.getLast())).isEqualTo(id);
        assertThat(inventory.stock(f.sku).reserved()).isEqualTo(3);
        assertThat(count("sales_order_request", "order_id", id)).isEqualTo(1);
        assertThat(count("inventory_reservation_line", "reservation_id", id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_reservation_line WHERE reservation_id=?", Long.class, id)).isEqualTo(3);
        assertThat(guest.get("/orders/" + id).statusCode()).isEqualTo(200);
        Browser other = new Browser(); other.csrf();
        assertThat(other.place(confirmed, UUID.randomUUID()).body()).contains("OFFER_CHANGED");
        assertThat(other.get("/orders/" + id).statusCode()).isEqualTo(404);
        assertThat(other.post("/orders/" + id + "/cancel", "{}").statusCode()).isEqualTo(404);
        assertThat(guest.place(confirmed.replace("Client test", "Autre nom"), key).statusCode()).isEqualTo(409);
        catalog.saveVariant(f.sku, f.product, f.skuCode, "SKU modifié", "pièce", Map.of(), "9.000", "PUBLISHED", 0L);
        assertThat(guest.place(confirmed, key).body()).contains("\"amount\":\"19.375\"", f.skuCode);
        assertThat(guest.get("/orders/" + id).body()).contains("\"amount\":\"2.375\"").doesNotContain("SKU modifié");
        assertThatThrownBy(() -> jdbc.update("UPDATE sales_order SET snapshot=snapshot WHERE id=?", id))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(guest.post("/orders/" + id + "/cancel", "{}").statusCode()).isEqualTo(200);
        assertThat(guest.post("/orders/" + id + "/cancel", "{}").statusCode()).isEqualTo(200);
        assertThat(inventory.stock(f.sku).onHand()).isEqualTo(10);
        assertThat(inventory.stock(f.sku).reserved()).isZero();
        assertThat(count("inventory_movement", "reservation_id", id)).isEqualTo(2);
    }

    @Test void twoSimultaneousOrdersNeverReserveLastUnitTwice() throws Exception {
        Fixture f = fixture(1);
        Browser a = new Browser(), b = new Browser(); a.csrf(); b.csrf();
        String request = previewBody(product(f, 1));
        String first = confirmed(request, a.post("/checkout/preview", request));
        String second = confirmed(request, b.post("/checkout/preview", request));
        var results = race(() -> a.place(first, UUID.randomUUID()), () -> b.place(second, UUID.randomUUID()));
        assertThat(results.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201, 409);
        assertThat(inventory.stock(f.sku).reserved()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation_line WHERE variant_id=?", Integer.class, f.sku)).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow().body()).contains("STOCK_UNAVAILABLE");
    }

    @Test void stalePricesCompositionAndInvalidAddressNeverCreateOrders() throws Exception {
        Fixture f = fixture(10); Browser guest = new Browser(); guest.csrf();
        String body = previewBody(product(f, 1));
        String request = confirmed(body, guest.post("/checkout/preview", body));
        UUID key = UUID.randomUUID();
        catalog.saveVariant(f.sku, f.product, f.skuCode, "SKU test", "pièce", Map.of(), "3.000", "PUBLISHED", 0L);
        assertThat(guest.place(request, key).body()).contains("OFFER_CHANGED");
        assertThat(guest.post("/checkout/preview", body).statusCode()).isEqualTo(409);
        String newBody = previewBody(item("PRODUCT", f.sku, 1, 1, 2));
        assertThat(guest.post("/checkout/preview", newBody).body()).contains("\"amount\":\"10.000\"");
        String packBody = previewBody(pack(f, 1));
        String packRequest = confirmed(packBody, guest.post("/checkout/preview", packBody));
        packs.saveVariant(f.packVariant, f.packId, "base", "Base", "10.000", "PUBLISHED",
                List.of(new Component(f.sku, 3)), 0L);
        assertThat(guest.place(packRequest, UUID.randomUUID()).body()).contains("OFFER_CHANGED");
        assertThat(guest.post("/checkout/preview", newBody.replace("\"TN\"", "\"FR\"")).statusCode()).isEqualTo(400);
        assertThat(guest.post("/checkout/preview", newBody.replace("\"TUNIS\"", "\"SOUSSE\"")).body()).contains("DELIVERY_ZONE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order_request WHERE request_key=?", Integer.class, key)).isZero();
        assertThat(inventory.stock(f.sku).reserved()).isZero();
    }

    @Test void shortageAndPersistenceFailureRollbackOrderReservationReceiptAndMovements() throws Exception {
        Fixture f = fixture(4), g = fixture(1); Browser guest = new Browser(); guest.csrf();
        String body = previewBody(product(f, 1), product(g, 1));
        String request = confirmed(body, guest.post("/checkout/preview", body));
        inventory.adjust(UUID.randomUUID(), g.sku, -1, "Rupture test", "admin@test.invalid");
        UUID shortageKey = UUID.randomUUID();
        assertThat(guest.place(request, shortageKey).body()).contains("STOCK_UNAVAILABLE");
        assertThat(inventory.stock(f.sku).reserved()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order_request WHERE request_key=?", Integer.class, shortageKey)).isZero();
        inventory.adjust(UUID.randomUUID(), g.sku, 1, "Réassort test", "admin@test.invalid");
        UUID failureKey = UUID.randomUUID();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Injected persistence failure after reservation"))
                .when(store).insert(any(Order.class));
        try { assertThat(guest.place(request, failureKey).statusCode()).isEqualTo(500); }
        finally { reset(store); }
        assertThat(inventory.stock(f.sku).reserved()).isZero();
        assertThat(inventory.stock(g.sku).reserved()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation_line WHERE variant_id IN (?,?)", Integer.class, f.sku, g.sku)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE variant_id IN (?,?) AND type='RESERVE'", Integer.class, f.sku, g.sku)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order_request WHERE request_key=?", Integer.class, failureKey)).isZero();
        assertThat(guest.place(request, failureKey).statusCode()).isEqualTo(201);
    }

    @Test void customerOwnershipManagerPermissionsAndShipmentConsumeOnlyOnce() throws Exception {
        Fixture f = fixture(10);
        Browser customer = customer(), other = customer(), manager = internal(Role.ORDER_MANAGER), catalogManager = internal(Role.CATALOG_MANAGER);
        String body = previewBody(pack(f, 2), product(f, 1));
        UUID id = id(customer.place(confirmed(body, customer.post("/checkout/preview", body)), UUID.randomUUID()));
        assertThat(customer.get("/orders").body()).contains(id.toString());
        assertThat(other.get("/orders").body()).doesNotContain(id.toString());
        assertThat(other.get("/orders/" + id).statusCode()).isEqualTo(404);
        assertThat(customer.post("/admin/orders/" + id + "/ship", "{}").statusCode()).isEqualTo(403);
        assertThat(catalogManager.get("/admin/orders").statusCode()).isEqualTo(403);
        assertThat(manager.post("/checkout/preview", body).statusCode()).isEqualTo(403);
        assertThat(manager.get("/admin/orders?status=CONFIRMED").body()).contains(id.toString());
        assertThat(manager.post("/admin/orders/" + id + "/ship", "{}").body()).contains("INVALID_TRANSITION");
        assertThat(manager.post("/admin/orders/" + id + "/prepare", "{}").statusCode()).isEqualTo(200);
        assertThat(customer.post("/orders/" + id + "/cancel", "{}").statusCode()).isEqualTo(409);
        assertThat(manager.post("/admin/orders/" + id + "/ship", "{}").statusCode()).isEqualTo(200);
        assertThat(manager.post("/admin/orders/" + id + "/ship", "{}").statusCode()).isEqualTo(200);
        assertThat(inventory.stock(f.sku).onHand()).isEqualTo(5);
        assertThat(inventory.stock(f.sku).reserved()).isZero();
        assertThat(manager.post("/admin/orders/" + id + "/cancel", "{}").statusCode()).isEqualTo(409);
        assertThat(manager.post("/admin/orders/" + id + "/deliver", "{}").statusCode()).isEqualTo(200);
        assertThat(count("inventory_movement", "reservation_id", id)).isEqualTo(2);
        assertThat(count("sales_order_event", "order_id", id)).isEqualTo(4);
        assertThat(customer.get("/orders/" + id).body()).contains("DELIVERED", "CASH_ON_DELIVERY", "\"amount\":\"29.375\"");
    }

    @Test void cancellationAndShipmentRaceHasOnlyOneTerminalStockEffect() throws Exception {
        Fixture f = fixture(2); Browser customer = customer(), manager = internal(Role.ORDER_MANAGER);
        String body = previewBody(product(f, 1));
        UUID id = id(customer.place(confirmed(body, customer.post("/checkout/preview", body)), UUID.randomUUID()));
        manager.post("/admin/orders/" + id + "/prepare", "{}");
        var results = race(() -> manager.post("/admin/orders/" + id + "/cancel", "{}"),
                () -> manager.post("/admin/orders/" + id + "/ship", "{}"));
        assertThat(results.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200, 409);
        String status = json.readTree(manager.get("/admin/orders/" + id).body()).get("status").asString();
        assertThat(status).isIn("CANCELLED", "SHIPPED");
        assertThat(inventory.stock(f.sku).reserved()).isZero();
        assertThat(inventory.stock(f.sku).onHand()).isEqualTo(status.equals("SHIPPED") ? 1 : 2);
        assertThat(count("inventory_movement", "reservation_id", id)).isEqualTo(2);
    }

    private Browser customer() throws Exception {
        String email = "order-customer-" + UUID.randomUUID() + "@example.tn";
        identity.register(email, LOGIN_PASSWORD); Browser b = new Browser(); b.login(email); return b;
    }
    private Browser internal(Role role) throws Exception {
        if (administratorId == null) administratorId = identity.bootstrapAdmin(
                "order-admin-" + UUID.randomUUID() + "@example.tn", LOGIN_PASSWORD).id();
        var account = identity.register("order-internal-" + UUID.randomUUID() + "@example.tn", LOGIN_PASSWORD);
        identity.changeRoles(administratorId, account.id(), Set.of(role));
        Browser b = new Browser(); b.login(account.email()); return b;
    }
    private int count(String table, String column, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + "=?", Integer.class, id);
    }
    private Fixture fixture(long stock) {
        String token = UUID.randomUUID().toString().replace("-", "");
        var category = catalog.saveCategory(null, null, "order-" + token, "Catégorie test", true, null);
        var product = catalog.saveProduct(null, category.id(), null, "Produit test", "", Map.of(), "PUBLISHED", null);
        String code = "ORD" + token.toUpperCase();
        var sku = catalog.saveVariant(null, product.id(), code, "SKU test", "pièce", Map.of(), "2.375", "PUBLISHED", null);
        var pack = packs.savePack(null, "order-pack-" + token, "Pack test", "", "", "DRAFT", null);
        var variant = packs.saveVariant(null, pack.id(), "base", "Base", "10.000", "PUBLISHED", List.of(new Component(sku.id(), 2)), null);
        packs.savePack(pack.id(), pack.code(), pack.name(), "", "", "PUBLISHED", pack.version());
        inventory.adjust(UUID.randomUUID(), sku.id(), stock, "Stock test", "admin@test.invalid");
        return new Fixture(product.id(), sku.id(), code, pack.id(), variant.id());
    }
    private record Fixture(UUID product, UUID sku, String skuCode, UUID packId, UUID packVariant) { }
    private String product(Fixture f, int qty) { return item("PRODUCT", f.sku, qty, 0, 1); }
    private String pack(Fixture f, int qty) { return item("PACK", f.packVariant, qty, 0, 1); }
    private String item(String kind, UUID id, int qty, int version, int parent) {
        return "{\"kind\":\"" + kind + "\",\"offerId\":\"" + id + "\",\"quantity\":" + qty
                + ",\"offerVersion\":" + version + ",\"parentVersion\":" + parent + "}";
    }
    private String previewBody(String... items) { return "{\"items\":[" + String.join(",", items) + "],\"address\":" + ADDRESS + "}"; }
    private String confirmed(String body, HttpResponse<String> preview) {
        assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);
        String hash = json.readTree(preview.body()).get("quoteHash").asString();
        return body.substring(0, body.length() - 1) + ",\"quoteHash\":\"" + hash + "\"}";
    }
    private UUID id(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).get("id").asString());
    }
    private List<HttpResponse<String>> race(Callable<HttpResponse<String>> first, Callable<HttpResponse<String>> second) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            var a = executor.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            var b = executor.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }
    private final class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        void csrf() throws Exception { assertThat(get("/auth/csrf").statusCode()).isEqualTo(204); }
        void login(String email) throws Exception {
            csrf(); assertThat(post("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + LOGIN_PASSWORD + "\"}").statusCode()).isEqualTo(200); csrf();
        }
        HttpResponse<String> get(String path) throws Exception { return request("GET", path, null, null, false); }
        HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body, null, true); }
        HttpResponse<String> place(String body, UUID key) throws Exception { return request("POST", "/orders", body, key, true); }
        HttpResponse<String> request(String method, String path, String body, UUID key, boolean csrf) throws Exception {
            var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
            if (body != null) b.header("Content-Type", "application/json");
            if (key != null) b.header("Idempotency-Key", key.toString());
            if (csrf) cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                    .findFirst().ifPresent(c -> b.header("X-XSRF-TOKEN", c.getValue()));
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
