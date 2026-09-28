package tn.bricocomptoir.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import tn.bricocomptoir.media.application.port.out.ObjectStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = BricoComptoirApplication.class)
class MediaCatalogIT {
    private static final String USER = "media_test_app";
    private static final String PASSWORD = UUID.randomUUID().toString();
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_media_test").withUsername("migrator")
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

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mail;
    @MockitoBean ObjectStorage objects;
    MockMvc mvc;
    Map<String, byte[]> stored;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        stored = new ConcurrentHashMap<>();
        doAnswer(call -> { stored.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(objects).put(anyString(), any(byte[].class), anyString());
        when(objects.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
    }

    @Test void uploadRejectsInvalidFilesAndRolesAndKeepsPrivateMetadata() throws Exception {
        UUID category = category("media-" + UUID.randomUUID());
        UUID published = product(category, "PUBLISHED", true);
        UUID draft = product(category, "DRAFT", false);
        byte[] png = png(640, 480);
        var file = file(png, "image/png");
        var endpoint = "/api/v1/admin/catalog/products/" + published + "/images";
        mvc.perform(multipart(endpoint).file(file).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(endpoint).file(file).with(user("customer").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(endpoint).file(file).with(user("manager").roles("CATALOG_MANAGER")))
                .andExpect(status().isForbidden());
        assertThat(stored).isEmpty();
        mvc.perform(multipart(endpoint).file(file("<svg/>".getBytes(), "image/png"))
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(endpoint).file(file(png, "image/jpeg"))
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(endpoint).file(file(png(200, 200), "image/png"))
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(endpoint).file(file).file(file("not an image".getBytes(), "image/png"))
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(stored).isEmpty();

        mvc.perform(multipart(endpoint).file(file).file(file(png(800, 600), "image/png"))
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf()))
                .andExpect(status().isCreated());
        assertThat(stored).hasSize(6);
        var images = jdbc.queryForList("SELECT id,source_mime,source_bytes,width,height,sort_order,is_primary,original_key "
                + "FROM media_product_image WHERE product_id=? ORDER BY sort_order", published);
        assertThat(images).hasSize(2);
        assertThat(images.getFirst()).containsEntry("source_mime", "image/png").containsEntry("width", 640)
                .containsEntry("height", 480).containsEntry("sort_order", 0).containsEntry("is_primary", true);
        String first = images.getFirst().get("id").toString(), second = images.get(1).get("id").toString();
        assertThat(images.getFirst().get("original_key").toString()).startsWith("products/");
        var list = mvc.perform(get("/api/v1/media/products").param("ids", published + "," + draft))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains(first, "cardUrl", "detailUrl").doesNotContain("original_key", "originalKey", draft.toString());
        mvc.perform(get("/api/v1/media/" + first + "/card")).andExpect(status().isOk());
        mvc.perform(put(endpoint + "/order").contentType("application/json")
                .content("{\"imageIds\":[\"" + second + "\",\"" + first
                        + "\"],\"primaryImageId\":\"" + second + "\"}")
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT id FROM media_product_image WHERE product_id=? AND is_primary", UUID.class,
                published)).isEqualTo(UUID.fromString(second));
        mvc.perform(multipart("/api/v1/admin/catalog/products/" + draft + "/images").file(file)
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isCreated());
        String hidden = jdbc.queryForObject("SELECT id FROM media_product_image WHERE product_id=?", UUID.class, draft).toString();
        mvc.perform(get("/api/v1/media/" + hidden + "/detail")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/catalog/products/" + draft + "/images/" + hidden + "/card")
                .with(user("manager").roles("CATALOG_MANAGER"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/catalog/products/" + draft + "/images/" + hidden + "/card")
                .with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/catalog/products/" + published + "/images/" + hidden + "/card")
                .with(user("manager").roles("CATALOG_MANAGER"))).andExpect(status().isNotFound());
        assertThat(mvc.perform(get("/api/v1/media/products").param("ids", draft.toString()))
                .andReturn().getResponse().getContentAsString()).doesNotContain(hidden);
    }

    @Test void csvPreviewReportsErrorsAndApplyCreatesAtomicallyWithDigest() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        category("import-" + suffix);
        String header = "productKey;categorySlug;brandSlug;productName;description;sku;variantLabel;unit;priceTnd;status\n";
        String row = "demo-one;import-" + suffix + ";;Exemple fictif;;DEMO-" + suffix.toUpperCase()
                + "-1;Format fictif;pièce;2.375;PUBLISHED\n";
        String second = "demo-one;import-" + suffix + ";;Exemple fictif;;DEMO-" + suffix.toUpperCase()
                + "-2;Autre format;pièce;3.000;PUBLISHED\n";
        byte[] csv = (header + row + second).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = new MockMultipartFile("file", "demo.csv", "text/csv", csv);
        mvc.perform(multipart("/api/v1/admin/catalog/imports/preview").file(upload)
                .with(user("customer").roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
        String preview = mvc.perform(multipart("/api/v1/admin/catalog/imports/preview").file(upload)
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(preview).contains("\"rowCount\":2", "\"productCount\":1", "\"issues\":[]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_variant WHERE sku LIKE ?", Integer.class,
                "DEMO-" + suffix.toUpperCase() + "%")).isZero();
        String digest = preview.split("\"digest\":\"")[1].split("\"")[0];
        mvc.perform(multipart("/api/v1/admin/catalog/imports/apply").file(upload)
                .param("expectedDigest", "0".repeat(64))
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf())).andExpect(status().isBadRequest());
        byte[] bad = (header + row + second.replace("3.000", "0.000"))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String errors = mvc.perform(multipart("/api/v1/admin/catalog/imports/preview")
                .file(new MockMultipartFile("file", "bad.csv", "text/csv", bad))
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(errors).contains("priceTnd", "Invalid TND price");
        mvc.perform(multipart("/api/v1/admin/catalog/imports/apply").file(upload)
                .param("expectedDigest", digest)
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_variant WHERE sku LIKE ?", Integer.class,
                "DEMO-" + suffix.toUpperCase() + "%")).isEqualTo(2);
        mvc.perform(multipart("/api/v1/admin/catalog/imports/apply").file(upload)
                .param("expectedDigest", digest)
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf())).andExpect(status().isBadRequest());
    }

    private UUID category(String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_category(id,slug,name) VALUES (?,?,?)", id, slug, "DÉMO catégorie test");
        return id;
    }
    private UUID product(UUID category, String status, boolean variant) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(id,category_id,name,status) VALUES (?,?,?,?)",
                id, category, "DÉMO produit test", status);
        if (variant) jdbc.update("INSERT INTO catalog_variant(id,product_id,sku,label,unit,price_tnd,status) "
                + "VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), id, "DEMO-" + UUID.randomUUID().toString().toUpperCase(),
                "Format fictif", "pièce", new java.math.BigDecimal("2.375"), "PUBLISHED");
        return id;
    }
    private static MockMultipartFile file(byte[] bytes, String mime) {
        return new MockMultipartFile("files", "photo.png", mime, bytes);
    }
    private static byte[] png(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
