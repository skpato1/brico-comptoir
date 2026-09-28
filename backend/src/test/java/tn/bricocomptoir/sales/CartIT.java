package tn.bricocomptoir.sales;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import tn.bricocomptoir.catalog.adapter.transaction.CatalogTransactions;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.inventory.adapter.transaction.InventoryTransactions;
import tn.bricocomptoir.packs.adapter.transaction.PackTransactions;
import tn.bricocomptoir.packs.domain.PackModels.Component;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = BricoComptoirApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartIT {
    private static final String USER = "cart_test_app";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String LOGIN_PASSWORD = "Correct-Horse-2026!";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_cart_test").withUsername("migrator")
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
    @Autowired PackTransactions packs;
    @Autowired InventoryTransactions inventory;
    @Autowired IdentityTransactions identity;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;

    @Test void guestEstimateAccumulatesSharedSkuAndKeepsDeliveryOutOfEstimate() throws Exception {
        Fixture fixture = offers();
        inventory.adjust(UUID.randomUUID(), fixture.skuId(), 4, "Stock test", "admin@test.invalid");
        int cartsBeforeEstimate = jdbc.queryForObject("SELECT count(*) FROM customer_cart", Integer.class);
        Browser guest = new Browser(); guest.csrf();
        String lines = items(line("PRODUCT", fixture.skuId(), 1), line("PACK", fixture.packVariantId(), 2));
        assertThat(guest.postWithoutCsrf("/cart/estimate", lines).statusCode()).isEqualTo(403);
        var estimate = guest.post("/cart/estimate", lines);
        assertThat(estimate.statusCode()).isEqualTo(200);
        assertThat(estimate.body()).contains("\"amount\":\"22.375\"", "\"required\":5",
                "\"available\":4", "\"kind\":\"PRODUCT\"", "\"kind\":\"PACK\"")
                .doesNotContain("deliveryFee", "shipping", "payment");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_cart", Integer.class))
                .isEqualTo(cartsBeforeEstimate);
        assertThat(guest.get("/cart").statusCode()).isEqualTo(401);
        var invalid = guest.post("/cart/estimate", items(line("PACK", fixture.packVariantId(), 1000)));
        assertThat(invalid.statusCode()).isEqualTo(400);
    }

    @Test void customerCartPersistsPrivatelyAndMergeIsIdempotent() throws Exception {
        Fixture fixture = offers();
        String email = "cart-customer-" + UUID.randomUUID() + "@example.tn";
        String otherEmail = "cart-other-" + UUID.randomUUID() + "@example.tn";
        var customer = identity.register(email, LOGIN_PASSWORD);
        identity.register(otherEmail, LOGIN_PASSWORD);
        var admin = identity.bootstrapAdmin("cart-admin-" + UUID.randomUUID() + "@example.tn", LOGIN_PASSWORD);
        Browser first = new Browser(); first.login(email);
        Browser second = new Browser(); second.login(otherEmail);
        Browser internal = new Browser(); internal.login(admin.email());
        assertThat(internal.get("/cart").statusCode()).isEqualTo(403);
        assertThat(first.get("/cart").body()).contains("\"version\":0", "\"items\":[]");
        assertThat(first.putWithoutCsrf("/cart", "{\"version\":0,\"items\":[]}").statusCode()).isEqualTo(403);
        String initial = "{\"version\":0,\"items\":[" + line("PRODUCT", fixture.skuId(), 1) + "]}";
        assertThat(first.put("/cart", initial).statusCode()).isEqualTo(200);
        assertThat(first.get("/cart").body()).contains("\"version\":1", "\"quantity\":1");
        assertThat(second.get("/cart").body()).contains("\"version\":0", "\"items\":[]");
        UUID mergeId = UUID.randomUUID();
        String merge = "{\"mergeId\":\"" + mergeId + "\",\"items\":["
                + line("PRODUCT", fixture.skuId(), 2) + "," + line("PACK", fixture.packVariantId(), 1) + "]}";
        assertThat(first.post("/cart/merge", merge).body()).contains("\"version\":2", "\"quantity\":3");
        assertThat(first.post("/cart/merge", merge).body()).contains("\"version\":2", "\"quantity\":3");
        String unchanged = "{\"version\":2,\"items\":[" + line("PRODUCT", fixture.skuId(), 3)
                + "," + line("PACK", fixture.packVariantId(), 1) + "]}";
        assertThat(first.put("/cart", unchanged).body()).contains("\"version\":2");
        String overflowMerge = "{\"mergeId\":\"" + UUID.randomUUID() + "\",\"items\":["
                + line("PRODUCT", fixture.skuId(), 999) + "]}";
        assertThat(first.post("/cart/merge", overflowMerge).statusCode()).isEqualTo(400);
        assertThat(first.post("/cart/merge", merge.replace("\"quantity\":2", "\"quantity\":4"))
                .statusCode()).isEqualTo(409);
        assertThat(first.put("/cart", initial).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_cart_merge WHERE customer_id=?",
                Integer.class, customer.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM customer_cart_line WHERE customer_id=? "
                + "AND kind='PRODUCT' AND offer_id=?", Long.class, customer.id(), fixture.skuId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_cart_line WHERE customer_id=?",
                Integer.class, customer.id())).isEqualTo(2);
        catalog.saveVariant(fixture.skuId(), fixture.productId(), fixture.sku(), "SKU test", "pièce",
                Map.of(), "2.375", "DRAFT", 0L);
        assertThat(first.get("/cart").body()).contains("\"subtotalEstimate\":null", "\"label\":null");
        assertThat(first.put("/cart", "{\"version\":2,\"items\":[]}").statusCode()).isEqualTo(200);
        assertThat(first.get("/cart").body()).contains("\"version\":3", "\"items\":[]");
    }

    private Fixture offers() {
        String token = UUID.randomUUID().toString().replace("-", "");
        var category = catalog.saveCategory(null, null, "cart-" + token, "Catégorie test", true, null);
        var product = catalog.saveProduct(null, category.id(), null, "Produit test", "", Map.of(),
                "PUBLISHED", null);
        String sku = "CRT" + token.toUpperCase();
        var variant = catalog.saveVariant(null, product.id(), sku, "SKU test", "pièce", Map.of(),
                "2.375", "PUBLISHED", null);
        var pack = packs.savePack(null, "cart-pack-" + token, "Pack test", "", "", "DRAFT", null);
        var packVariant = packs.saveVariant(null, pack.id(), "base", "Base", "10.000", "PUBLISHED",
                List.of(new Component(variant.id(), 2)), null);
        packs.savePack(pack.id(), pack.code(), pack.name(), "", "", "PUBLISHED", pack.version());
        return new Fixture(variant.id(), product.id(), sku, packVariant.id());
    }
    private record Fixture(UUID skuId, UUID productId, String sku, UUID packVariantId) { }
    private String line(String kind, UUID offerId, int quantity) {
        return "{\"kind\":\"" + kind + "\",\"offerId\":\"" + offerId + "\",\"quantity\":" + quantity + "}";
    }
    private String items(String... lines) { return "{\"items\":[" + String.join(",", lines) + "]}"; }

    private final class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path) throws Exception { return request("GET", path, null, false); }
        HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body, true); }
        HttpResponse<String> postWithoutCsrf(String path, String body) throws Exception {
            return request("POST", path, body, false);
        }
        HttpResponse<String> put(String path, String body) throws Exception { return request("PUT", path, body, true); }
        HttpResponse<String> putWithoutCsrf(String path, String body) throws Exception {
            return request("PUT", path, body, false);
        }
        void csrf() throws Exception { assertThat(get("/auth/csrf").statusCode()).isEqualTo(204); }
        void login(String email) throws Exception {
            csrf();
            assertThat(post("/auth/login", "{\"email\":\"" + email + "\",\"password\":\""
                    + LOGIN_PASSWORD + "\"}").statusCode()).isEqualTo(200);
            csrf();
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
