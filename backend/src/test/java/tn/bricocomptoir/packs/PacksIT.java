package tn.bricocomptoir.packs;

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
import tn.bricocomptoir.identity.domain.Role;
import tn.bricocomptoir.inventory.adapter.transaction.InventoryTransactions;
import tn.bricocomptoir.packs.adapter.transaction.PackTransactions;
import tn.bricocomptoir.packs.domain.PackModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = BricoComptoirApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PacksIT {
    private static final String USER = "packs_test_app";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String LOGIN_PASSWORD = "Correct-Horse-2026!";
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_packs_test").withUsername("migrator")
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
    @Autowired PackTransactions packs;
    @Autowired IdentityTransactions identity;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;

    @Test void variantsAggregateRepeatedSkuAndSharePhysicalStock() {
        Sku fix = sku();
        Sku tool = sku();
        inventory.adjust(UUID.randomUUID(), fix.id(), 8, "Stock test", "admin@test.invalid");
        inventory.adjust(UUID.randomUUID(), tool.id(), 2, "Stock test", "admin@test.invalid");
        Pack pack = packs.savePack(null, code(), "DÉMO Pack test", "Slogan fictif",
                "Guide de démonstration", "DRAFT", null);
        PackVariant without = packs.saveVariant(null, pack.id(), "sans-outils", "Sans outils",
                "10.000", "PUBLISHED", List.of(new Component(fix.id(), 2)), null);
        PackVariant complete = packs.saveVariant(null, pack.id(), "tout-compris", "Tout compris",
                "30.500", "PUBLISHED", List.of(new Component(fix.id(), 2),
                        new Component(tool.id(), 1), new Component(fix.id(), 1)), null);
        assertThatThrownBy(() -> packs.pack(pack.id(), false)).isInstanceOf(IllegalArgumentException.class);
        Pack published = packs.savePack(pack.id(), pack.code(), pack.name(), pack.slogan(),
                pack.guide(), "PUBLISHED", pack.version());
        assertThat(published.version()).isEqualTo(1);
        assertThat(packs.pack(pack.id(), false).variants()).hasSize(2);
        assertThat(packs.availability(published).get(without.id())).isEqualTo(4);
        assertThat(packs.availability(published).get(complete.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT quantity FROM pack_component WHERE pack_variant_id=? "
                + "AND catalog_variant_id=?", Long.class, complete.id(), fix.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pack_component WHERE pack_variant_id=?",
                Integer.class, complete.id())).isEqualTo(2);
        var offer = packs.offer(complete.id()).orElseThrow();
        assertThat(offer.priceTnd().toPlainString()).isEqualTo("30.500");
        assertThat(offer.packVersion()).isEqualTo(1);
        assertThat(offer.components()).hasSize(2);

        Pack other = packs.savePack(null, code(), "DÉMO Autre pack", "", "", "DRAFT", null);
        PackVariant shared = packs.saveVariant(null, other.id(), "base", "Base", "8.000",
                "PUBLISHED", List.of(new Component(fix.id(), 2)), null);
        other = packs.savePack(other.id(), other.code(), other.name(), "", "", "PUBLISHED", other.version());
        assertThat(packs.availability(other).get(shared.id())).isEqualTo(4);
        inventory.reserve(UUID.randomUUID(), List.of(new tn.bricocomptoir.inventory.domain.InventoryModels.Line(fix.id(), 5)));
        assertThat(packs.availability(packs.pack(pack.id(), false)).get(without.id())).isEqualTo(1);
        assertThat(packs.availability(packs.pack(pack.id(), false)).get(complete.id())).isEqualTo(1);
        assertThat(packs.availability(packs.pack(other.id(), false)).get(shared.id())).isEqualTo(1);
        inventory.adjust(UUID.randomUUID(), tool.id(), -2, "Épuisement test", "admin@test.invalid");
        assertThat(packs.availability(packs.pack(pack.id(), false)).get(complete.id())).isZero();
        assertThat(packs.availability(packs.pack(pack.id(), false)).get(without.id())).isEqualTo(1);

        PackVariant edited = packs.saveVariant(complete.id(), pack.id(), complete.code(),
                complete.label(), "31.000", "PUBLISHED", List.of(new Component(fix.id(), 1)), complete.version());
        assertThat(edited.version()).isEqualTo(1);
        assertThat(packs.offer(complete.id()).orElseThrow().variantVersion()).isEqualTo(1);
        assertThatThrownBy(() -> packs.saveVariant(complete.id(), pack.id(), complete.code(),
                complete.label(), "31.000", "PUBLISHED", List.of(new Component(fix.id(), 1)), 0L))
                .isInstanceOf(IllegalStateException.class);
        catalog.saveVariant(fix.id(), fix.productId(), fix.sku(), "SKU test", "pièce", Map.of(),
                "1.000", "DRAFT", 0L);
        assertThat(packs.offer(complete.id())).isEmpty();
        assertThatThrownBy(() -> packs.pack(pack.id(), false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void publicVisibilityAndAdministrativePermissions() throws Exception {
        Sku fix = sku();
        Pack draft = packs.savePack(null, code(), "DÉMO Brouillon", "Slogan exemple", "Guide exemple", "DRAFT", null);
        PackVariant variant = packs.saveVariant(null, draft.id(), "base", "Base", "9.000",
                "DRAFT", List.of(new Component(fix.id(), 1)), null);
        UUID demoId = UUID.randomUUID();
        jdbc.update("INSERT INTO pack_offer(id, code, name, status, demo) VALUES (?, ?, ?, 'DRAFT', true)",
                demoId, code(), "DÉMO non validé");
        Pack demo = packs.pack(demoId, true);
        assertThatThrownBy(() -> packs.savePack(demo.id(), demo.code(), demo.name(),
                demo.slogan(), demo.guide(), "PUBLISHED", demo.version()))
                .isInstanceOf(IllegalStateException.class);
        Browser anonymous = new Browser();
        assertThat(anonymous.get("/packs").statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/packs").body()).doesNotContain("DÉMO Brouillon");
        assertThat(anonymous.get("/packs/" + draft.id()).statusCode()).isEqualTo(404);
        assertThat(anonymous.get("/admin/packs").statusCode()).isEqualTo(401);

        String adminEmail = "packs-admin-" + UUID.randomUUID() + "@example.tn";
        var admin = identity.bootstrapAdmin(adminEmail, LOGIN_PASSWORD);
        var manager = identity.createInternal(admin.id(), "packs-manager-" + UUID.randomUUID()
                + "@example.tn", LOGIN_PASSWORD, Set.of(Role.CATALOG_MANAGER));
        var order = identity.createInternal(admin.id(), "packs-order-" + UUID.randomUUID()
                + "@example.tn", LOGIN_PASSWORD, Set.of(Role.ORDER_MANAGER));
        var customer = identity.register("packs-customer-" + UUID.randomUUID() + "@example.tn", LOGIN_PASSWORD);
        Browser catalogManager = new Browser(); catalogManager.login(manager.email());
        Browser orderManager = new Browser(); orderManager.login(order.email());
        Browser client = new Browser(); client.login(customer.email());
        Browser root = new Browser(); root.login(adminEmail);
        String create = "{\"code\":\"" + code() + "\",\"name\":\"DÉMO Nouveau pack\","
                + "\"slogan\":\"Exemple\",\"guide\":\"Exemple\",\"status\":\"DRAFT\"}";
        assertThat(catalogManager.get("/admin/packs/" + draft.id()).body()).contains("DÉMO Brouillon");
        assertThat(orderManager.get("/admin/packs").statusCode()).isEqualTo(403);
        assertThat(client.get("/admin/packs").statusCode()).isEqualTo(403);
        assertThat(catalogManager.postWithoutCsrf("/admin/packs", create).statusCode()).isEqualTo(403);
        assertThat(orderManager.post("/admin/packs", create).statusCode()).isEqualTo(403);
        assertThat(catalogManager.post("/admin/packs", create).statusCode()).isEqualTo(201);
        assertThat(root.post("/admin/packs", create.replace("DÉMO Nouveau pack", "DÉMO Autre pack"))
                .statusCode()).isEqualTo(409);
        String variantInput = "{\"code\":\"autre\",\"label\":\"Autre variante\","
                + "\"priceTnd\":\"11.000\",\"status\":\"DRAFT\",\"components\":[{\"variantId\":\""
                + fix.id() + "\",\"quantity\":1}]}";
        assertThat(catalogManager.postWithoutCsrf("/admin/packs/" + draft.id() + "/variants", variantInput)
                .statusCode()).isEqualTo(403);
        assertThat(orderManager.post("/admin/packs/" + draft.id() + "/variants", variantInput)
                .statusCode()).isEqualTo(403);
        assertThat(catalogManager.post("/admin/packs/" + draft.id() + "/variants", variantInput)
                .statusCode()).isEqualTo(201);

        PackVariant publishedVariant = packs.saveVariant(variant.id(), draft.id(), variant.code(),
                variant.label(), "9.000", "PUBLISHED", variant.components(), variant.version());
        Pack publishedPack = packs.savePack(draft.id(), draft.code(), draft.name(), draft.slogan(),
                draft.guide(), "PUBLISHED", draft.version());
        assertThat(anonymous.get("/packs/" + draft.id()).body())
                .contains("DÉMO Brouillon", "Slogan exemple", "Guide exemple", "\"amount\":\"9.000\"",
                        "\"available\":0", "componentNames", "DÉMO Produit test", fix.sku());
        assertThat(anonymous.get("/packs").body()).contains("DÉMO Brouillon");
        assertThatThrownBy(() -> packs.saveVariant(publishedVariant.id(), draft.id(),
                publishedVariant.code(), publishedVariant.label(), "9.000", "DRAFT",
                publishedVariant.components(), publishedVariant.version())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> packs.savePack(publishedPack.id(), publishedPack.code(),
                publishedPack.name(), publishedPack.slogan(), publishedPack.guide(), "PUBLISHED", 0L))
                .isInstanceOf(IllegalStateException.class);
    }

    private Sku sku() {
        String token = UUID.randomUUID().toString().replace("-", "");
        var category = catalog.saveCategory(null, null, "pack-" + token, "Catégorie test", true, null);
        var product = catalog.saveProduct(null, category.id(), null, "DÉMO Produit test", "", Map.of(),
                "PUBLISHED", null);
        String sku = "PCK" + token.toUpperCase();
        var variant = catalog.saveVariant(null, product.id(), sku, "SKU test", "pièce",
                Map.of(), "1.000", "PUBLISHED", null);
        return new Sku(variant.id(), product.id(), sku);
    }
    private String code() { return "pack-" + UUID.randomUUID().toString(); }
    private record Sku(UUID id, UUID productId, String sku) { }

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
