package tn.bricocomptoir.catalog;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.regex.Pattern;
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
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.identity.domain.Role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = BricoComptoirApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogIT {
    private static final String USER = "catalog_test_app";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String LOGIN_PASSWORD = "Correct-Horse-2026!";
    private static final Pattern ID = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_catalog_test").withUsername("migrator")
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
    @Autowired IdentityTransactions identity;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;

    @Test void publishedVisibilityFiltersPaginationVersionsAndPermissions() throws Exception {
        String adminEmail = "catalog-admin-" + UUID.randomUUID() + "@example.tn";
        var admin = identity.bootstrapAdmin(adminEmail, LOGIN_PASSWORD);
        var managerAccount = identity.createInternal(admin.id(), "catalog-manager-" + UUID.randomUUID()
                + "@example.tn", LOGIN_PASSWORD, java.util.Set.of(Role.CATALOG_MANAGER));
        var orderAccount = identity.createInternal(admin.id(), "order-manager-" + UUID.randomUUID()
                + "@example.tn", LOGIN_PASSWORD, java.util.Set.of(Role.ORDER_MANAGER));
        var customerAccount = identity.register("catalog-customer-" + UUID.randomUUID()
                + "@example.tn", LOGIN_PASSWORD);
        Browser anonymous = new Browser();
        Browser manager = new Browser(); manager.login(managerAccount.email());
        Browser order = new Browser(); order.login(orderAccount.email());
        Browser customer = new Browser(); customer.login(customerAccount.email());
        Browser root = new Browser(); root.login(adminEmail);

        assertThat(anonymous.get("/products").statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/admin/catalog/products").statusCode()).isEqualTo(401);
        assertThat(order.get("/admin/catalog/products").statusCode()).isEqualTo(403);
        assertThat(customer.get("/admin/catalog/products").statusCode()).isEqualTo(403);
        assertThat(manager.get("/admin/catalog/products").statusCode()).isEqualTo(200);
        assertThat(manager.postWithoutCsrf("/admin/catalog/categories", "{}").statusCode()).isEqualTo(403);
        assertThat(order.post("/admin/catalog/categories", "{}").statusCode()).isEqualTo(403);

        var parent = manager.post("/admin/catalog/categories",
                "{\"slug\":\"test-fixations\",\"name\":\"Fixations test\",\"active\":true}");
        assertThat(parent.statusCode()).isEqualTo(201);
        String parentId = id(parent.body());
        var child = manager.post("/admin/catalog/categories", "{\"parentId\":\"" + parentId
                + "\",\"slug\":\"test-vis\",\"name\":\"Vis test\",\"active\":true}");
        String childId = id(child.body());
        assertThat(child.statusCode()).isEqualTo(201);
        assertThat(manager.put("/admin/catalog/categories/" + parentId,
                "{\"parentId\":\"" + childId
                + "\",\"slug\":\"test-fixations\",\"name\":\"Fixations test\",\"active\":true,\"version\":0}")
                .statusCode()).isEqualTo(409);

        String brandId = id(root.post("/admin/catalog/brands",
                "{\"slug\":\"marque-fictive-test\",\"name\":\"Marque fictive test\",\"active\":true}").body());
        String draftId = id(manager.post("/admin/catalog/products", productJson(childId, brandId,
                "DÉMO Brouillon", "DRAFT", null)).body());
        String publicId = id(manager.post("/admin/catalog/products", productJson(childId, brandId,
                "DÉMO Vis exemple", "PUBLISHED", null)).body());
        assertThat(anonymous.get("/products/" + draftId).statusCode()).isEqualTo(404);
        assertThat(anonymous.get("/products/" + publicId).statusCode()).isEqualTo(404);
        assertThat(manager.get("/admin/catalog/products/" + draftId).statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/products").body()).doesNotContain("DÉMO Brouillon", "DÉMO Vis exemple");

        var variant = manager.post("/admin/catalog/products/" + publicId + "/variants",
                "{\"sku\":\"DEMO-VIS-001\",\"label\":\"Format fictif\",\"unit\":\"pièce\","
                + "\"priceTnd\":\"2.375\",\"status\":\"PUBLISHED\",\"options\":{}}");
        assertThat(variant.statusCode()).isEqualTo(201);
        assertThat(variant.body()).contains("\"amount\":\"2.375\"", "\"currency\":\"TND\"");
        assertThat(anonymous.get("/products/" + publicId).body()).contains("DEMO-VIS-001", "DÉMO Vis exemple");
        assertThat(anonymous.get("/products/" + draftId).statusCode()).isEqualTo(404);
        assertThat(anonymous.get("/products?q=DEMO-VIS-001").body()).contains("DÉMO Vis exemple")
                .doesNotContain("DÉMO Brouillon");
        assertThat(anonymous.get("/products?categoryId=" + parentId + "&brandId=" + brandId
                + "&minPrice=2.000&maxPrice=3.000").body()).contains("\"totalElements\":1");
        assertThat(anonymous.get("/products?maxPrice=2.000").body()).contains("\"totalElements\":0");
        assertThat(anonymous.get("/products?page=0&size=1&sort=price_desc").body()).contains("\"size\":1");
        assertThat(anonymous.get("/products?size=101").statusCode()).isEqualTo(400);
        assertThat(manager.get("/admin/catalog/products").body()).contains("DÉMO Brouillon");

        assertThat(manager.put("/admin/catalog/products/" + publicId, productJson(childId, brandId,
                "DÉMO Vis modifiée", "DRAFT", 0)).statusCode()).isEqualTo(409);
        assertThat(manager.put("/admin/catalog/products/" + publicId, productJson(childId, brandId,
                "DÉMO Vis modifiée", "DRAFT", 1)).statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/products/" + publicId).statusCode()).isEqualTo(404);
        assertThat(manager.put("/admin/catalog/products/" + publicId, productJson(childId, brandId,
                "DÉMO Vis modifiée", "PUBLISHED", 2)).statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/products/" + publicId).statusCode()).isEqualTo(200);

        assertThat(manager.post("/admin/catalog/products/" + publicId + "/variants",
                "{\"sku\":\"DEMO-VIS-010\",\"label\":\"Autre prix fictif\",\"unit\":\"pièce\","
                + "\"priceTnd\":\"10.000\",\"status\":\"PUBLISHED\"}").statusCode()).isEqualTo(201);
        assertThat(anonymous.get("/products?minPrice=3.000&maxPrice=9.000").body())
                .contains("\"totalElements\":0");
        assertThat(manager.post("/admin/catalog/products/" + publicId + "/variants",
                "{\"sku\":\"DEMO-SECRET-DRAFT\",\"label\":\"Brouillon\",\"unit\":\"pièce\","
                + "\"priceTnd\":\"7.000\",\"status\":\"DRAFT\"}").statusCode()).isEqualTo(201);
        assertThat(anonymous.get("/products/" + publicId).body()).doesNotContain("DEMO-SECRET-DRAFT");
        assertThat(anonymous.get("/products?q=DEMO-SECRET-DRAFT").body())
                .contains("\"totalElements\":0");
        assertThat(anonymous.get("/products?q=%25").body()).contains("\"totalElements\":0");

        String otherCategory = id(manager.post("/admin/catalog/categories",
                "{\"slug\":\"test-outils\",\"name\":\"Outils test\",\"active\":true}").body());
        String otherProduct = id(manager.post("/admin/catalog/products", productJson(otherCategory,
                brandId, "DÉMO Outil exemple", "PUBLISHED", null)).body());
        assertThat(manager.post("/admin/catalog/products/" + otherProduct + "/variants",
                "{\"sku\":\"DEMO-OUTIL-001\",\"label\":\"Autre outil fictif\",\"unit\":\"pièce\","
                + "\"priceTnd\":\"99.999\",\"status\":\"PUBLISHED\"}").statusCode()).isEqualTo(201);
        assertThat(anonymous.get("/products?sort=price_asc&size=1&page=0").body())
                .contains("DEMO-VIS-001").doesNotContain("DEMO-OUTIL-001");
        assertThat(anonymous.get("/products?sort=price_desc&size=1&page=0").body())
                .contains("DEMO-OUTIL-001").doesNotContain("DEMO-VIS-001");
        assertThat(anonymous.get("/products?sort=price_asc&size=1&page=1").body())
                .contains("DEMO-OUTIL-001", "\"totalElements\":2");

        assertThat(manager.post("/admin/catalog/products/" + publicId + "/variants",
                "{\"sku\":\"DEMO-VIS-001\",\"label\":\"Doublon\",\"unit\":\"pièce\","
                + "\"priceTnd\":\"1.000\",\"status\":\"PUBLISHED\"}").statusCode()).isEqualTo(409);
        assertThat(manager.post("/admin/catalog/products/" + publicId + "/variants",
                "{\"sku\":\"DEMO-VIS-002\",\"label\":\"Prix invalide\",\"unit\":\"pièce\","
                + "\"priceTnd\":\"1.0001\",\"status\":\"PUBLISHED\"}").statusCode()).isEqualTo(400);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO catalog_variant(id,product_id,sku,label,unit,price_tnd) "
                + "VALUES (?,?,?,?,?,?)", UUID.randomUUID(), UUID.fromString(publicId), "DB-BAD-PRICE",
                "Invalide", "pièce", new java.math.BigDecimal("0.000")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private static String productJson(String category, String brand, String name, String status, Integer version) {
        return "{\"categoryId\":\"" + category + "\",\"brandId\":\"" + brand
                + "\",\"name\":\"" + name + "\",\"description\":\"Exemple fictif, sans caractéristique"
                + " technique réelle\",\"characteristics\":{},\"status\":\"" + status + "\""
                + (version == null ? "" : ",\"version\":" + version) + "}";
    }
    private static String id(String body) {
        var match = ID.matcher(body);
        assertThat(match.find()).as(body).isTrue();
        return match.group(1);
    }
    private final class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path) throws Exception { return request("GET", path, null, false); }
        HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body, true); }
        HttpResponse<String> postWithoutCsrf(String path, String body) throws Exception {
            return request("POST", path, body, false);
        }
        HttpResponse<String> put(String path, String body) throws Exception { return request("PUT", path, body, true); }
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
