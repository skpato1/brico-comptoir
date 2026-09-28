package tn.bricocomptoir.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.DriverManager;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import tn.bricocomptoir.media.application.port.out.ObjectStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "BRICO_LOCAL_MINIO_TEST", matches = "true")
@SpringBootTest(classes = BricoComptoirApplication.class)
class MediaMinioLocalIT {
    private static final String USER = "media_minio_test_app";
    private static final String PASSWORD = UUID.randomUUID().toString();
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_media_minio_test").withUsername("migrator")
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
    @Autowired ObjectStorage objects;
    @MockitoBean JavaMailSender mail;

    @Test void managerUploadStoresObjectsAndPublicApiServesRenditions() throws Exception {
        UUID category = UUID.randomUUID(), product = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_category(id,slug,name) VALUES (?,?,?)", category,
                "local-minio-" + category.toString().substring(0, 8), "DÉMO local MinIO");
        jdbc.update("INSERT INTO catalog_product(id,category_id,name,status) VALUES (?,?,?,?)",
                product, category, "DÉMO produit MinIO", "PUBLISHED");
        jdbc.update("INSERT INTO catalog_variant(id,product_id,sku,label,unit,price_tnd,status) "
                + "VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), product,
                "DEMO-" + UUID.randomUUID().toString().toUpperCase(), "Format fictif", "pièce",
                new java.math.BigDecimal("1.000"), "PUBLISHED");
        var image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        byte[] source = output.toByteArray();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String endpoint = "/api/v1/admin/catalog/products/" + product + "/images";
        mvc.perform(multipart(endpoint).file(new MockMultipartFile("files", "demo.png", "image/png", source))
                .with(user("manager").roles("CATALOG_MANAGER")).with(csrf())).andExpect(status().isCreated());
        var stored = jdbc.queryForMap("SELECT id,original_key,card_key,detail_key FROM media_product_image WHERE product_id=?",
                product);
        String original = stored.get("original_key").toString();
        String card = stored.get("card_key").toString();
        String detail = stored.get("detail_key").toString();
        try {
            assertThat(objects.get(original)).isEqualTo(source);
            assertThat(ImageIO.read(new java.io.ByteArrayInputStream(objects.get(card))).getWidth()).isEqualTo(360);
            assertThat(mvc.perform(get("/api/v1/media/" + stored.get("id") + "/detail"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isNotEmpty();
        } finally {
            objects.delete(original); objects.delete(card); objects.delete(detail);
        }
    }
}
