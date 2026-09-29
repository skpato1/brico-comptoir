package tn.bricocomptoir.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
import tn.bricocomptoir.catalog.adapter.transaction.CatalogTransactions;
import tn.bricocomptoir.media.adapter.transaction.PackImageTransactions;
import tn.bricocomptoir.media.application.port.out.ObjectStorage;
import tn.bricocomptoir.media.application.service.PackImageService;
import tn.bricocomptoir.packs.adapter.transaction.PackTransactions;
import tn.bricocomptoir.packs.domain.PackModels.Component;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = BricoComptoirApplication.class)
class PackMediaIT {
    private static final String USER = "pack_media_app", PASSWORD = UUID.randomUUID().toString();
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_pack_media").withUsername("migrator")
            .withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + USER + " LOGIN PASSWORD '" + PASSWORD + "'");
        }
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", () -> USER); registry.add("DB_PASSWORD", () -> PASSWORD);
        registry.add("DB_MIGRATION_USERNAME", POSTGRES::getUsername);
        registry.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
    }
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PackTransactions packs;
    @Autowired CatalogTransactions catalog;
    @Autowired PackImageTransactions media;
    @MockitoBean JavaMailSender mail;
    @MockitoBean ObjectStorage objects;
    MockMvc mvc;
    Map<String, byte[]> stored;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        stored = new ConcurrentHashMap<>();
        doAnswer(call -> { stored.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(objects).put(anyString(), any(byte[].class), anyString());
        doAnswer(call -> { stored.remove(call.getArgument(0)); return null; }).when(objects).delete(anyString());
        when(objects.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
    }
    @Test void validatesUploadsRbacCsrfMetadataAndDraftOwnership() throws Exception {
        UUID pack = draft(), other = draft();
        String endpoint = endpoint(pack);
        var file = file(png(640, 480), "image/png");
        mvc.perform(multipart(endpoint).file(file).with(csrf())).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "ORDER_MANAGER")) {
            mvc.perform(multipart(endpoint).file(file).with(user("test").roles(role)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(get(endpoint).with(user("test").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(multipart(endpoint).file(file).with(user("test").roles("CATALOG_MANAGER")))
                .andExpect(status().isForbidden());
        for (var invalid : List.of(file("<svg/>".getBytes(), "image/png"), file(png(640,480), "image/jpeg"),
                file(png(200,200), "image/png"), file(new byte[6 * 1024 * 1024 + 1], "image/png")))
            mvc.perform(multipart(endpoint).file(file).file(invalid).with(user("test").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isBadRequest());
        assertThat(stored).isEmpty();
        mvc.perform(multipart(endpoint).file(file).file(file(png(800,600), "image/png"))
                .with(user("test").roles("CATALOG_MANAGER")).with(csrf())).andExpect(status().isCreated());
        assertThat(stored).hasSize(6);
        var images = media.adminImages(pack);
        assertThat(images).hasSize(2);
        var first = images.getFirst(); var second = images.get(1);
        assertThat(first.sourceSha256()).matches("[0-9a-f]{64}");
        assertThat(first.width()).isEqualTo(640); assertThat(first.height()).isEqualTo(480);
        assertThat(first.originalKey()).startsWith("packs/" + pack + "/");
        assertThat(first.primary()).isTrue();
        assertThat(ImageIO.read(new ByteArrayInputStream(stored.get(first.cardKey()))).getWidth()).isEqualTo(360);
        assertThat(mvc.perform(get(endpoint).with(user("test").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("cardUrl", "detailUrl").doesNotContain("originalKey", "sourceSha256");
        mvc.perform(get("/api/v1/media/packs/" + first.id() + "/card")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/media/packs").param("ids", pack.toString())).andExpect(content().json("{}"));
        mvc.perform(get(endpoint + "/" + first.id() + "/detail").with(user("test").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        mvc.perform(get(endpoint(other) + "/" + first.id() + "/card").with(user("test").roles("ADMIN")))
                .andExpect(status().isNotFound());
        String order = "{\"imageIds\":[\"" + second.id() + "\",\"" + first.id() + "\"],\"primaryImageId\":\"" + second.id() + "\"}";
        mvc.perform(put(endpoint + "/order").contentType("application/json").content(order)
                .with(user("test").roles("ORDER_MANAGER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put(endpoint + "/order").contentType("application/json").content(order)
                .with(user("test").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        assertThat(media.adminImages(pack).getFirst().id()).isEqualTo(second.id());
        assertThat(media.adminImages(pack).getFirst().primary()).isTrue();
        assertThatThrownBy(() -> media.reorder(other, List.of(first.id()), first.id()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> media.reorder(pack, List.of(first.id(), first.id()), first.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void publicImagesFollowPackAndComponentPublicationAndNeverExposeOriginals() throws Exception {
        var category = catalog.saveCategory(null, null, "media-" + UUID.randomUUID(), "Test", true, null);
        var product = catalog.saveProduct(null, category.id(), null, "Test", "", Map.of(), "DRAFT", null);
        var sku = catalog.saveVariant(null, product.id(), "TEST-" + UUID.randomUUID().toString().toUpperCase(),
                "Test", "pièce", Map.of(), "2.375", "PUBLISHED", null);
        product = catalog.product(product.id(), true);
        catalog.saveProduct(product.id(), category.id(), null, product.name(), "", Map.of(), "PUBLISHED", product.version());
        UUID packId = draft(); var pack = packs.pack(packId, true);
        packs.saveVariant(null, packId, "base", "Base", "10.000", "PUBLISHED", List.of(new Component(sku.id(), 1)), null);
        pack = packs.savePack(packId, pack.code(), pack.name(), "", "", "PUBLISHED", pack.version());
        var image = media.upload(packId, List.of(new PackImageService.Upload(png(1600,1000), "image/png"))).getFirst();
        var response = mvc.perform(get("/api/v1/media/packs").param("ids", packId.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).contains(image.id().toString()).doesNotContain("original", "sourceBytes", "sourceSha256");
        byte[] detail = mvc.perform(get("/api/v1/media/packs/" + image.id() + "/detail"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(detail)).getWidth()).isEqualTo(1200);
        mvc.perform(get("/api/v1/media/packs/" + image.id() + "/original")).andExpect(status().isUnauthorized());
        catalog.saveVariant(sku.id(), product.id(), sku.sku(), sku.label(), sku.unit(), sku.options(), "2.375", "DRAFT", sku.version());
        mvc.perform(get("/api/v1/media/packs/" + image.id() + "/card")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/media/packs").param("ids", packId.toString())).andExpect(content().json("{}"));
    }
    @Test void storageFailureRollsBackAllMetadataAndCleansWrittenObjects() throws Exception {
        UUID id = draft();
        AtomicInteger count = new AtomicInteger();
        doAnswer(call -> {
            if (count.incrementAndGet() == 4) throw new IllegalStateException("Storage unavailable");
            stored.put(call.getArgument(0), call.getArgument(1)); return null;
        }).when(objects).put(anyString(), any(byte[].class), anyString());
        var upload = new PackImageService.Upload(png(640,480), "image/png");
        assertThatThrownBy(() -> media.upload(id, List.of(upload, upload))).isInstanceOf(IllegalStateException.class);
        assertThat(media.adminImages(id)).isEmpty(); assertThat(stored).isEmpty();
        verify(objects, times(3)).delete(anyString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_pack_image WHERE pack_id=?", Integer.class, id)).isZero();
    }
    private UUID draft() { return packs.savePack(null, "test-" + UUID.randomUUID(), "Test kit", "", "", "DRAFT", null).id(); }
    private static String endpoint(UUID id) { return "/api/v1/admin/packs/" + id + "/images"; }
    private static MockMultipartFile file(byte[] bytes, String type) { return new MockMultipartFile("files", "test.png", type, bytes); }
    private static byte[] png(int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}
