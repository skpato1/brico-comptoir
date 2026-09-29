package tn.bricocomptoir.media;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tn.bricocomptoir.media.adapter.out.storage.MinioObjectStorage;
import tn.bricocomptoir.media.adapter.out.storage.S3ObjectStorage;
import tn.bricocomptoir.media.application.port.out.ObjectStorage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ObjectStorageTest {
    @Test
    void signedBinaryRoundTripPreservesSupabaseEndpointPrefixAndUsesConfiguredRegion() throws Exception {
        byte[] photo = {(byte) 0xff, (byte) 0xd8, 0, 42, (byte) 0xff, (byte) 0xd9};
        List<String> methods = new ArrayList<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            assertThat(exchange.getRequestURI().getPath())
                    .isEqualTo("/storage/v1/s3/bricocomptoir-media/packs/example/card.jpg");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .contains("AWS4-HMAC-SHA256", "/eu-west-3/s3/aws4_request");
            methods.add(exchange.getRequestMethod());
            switch (exchange.getRequestMethod()) {
                case "PUT" -> {
                    assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("image/jpeg");
                    assertThat(exchange.getRequestHeaders().getFirst("Cache-Control")).isEqualTo("private, no-store");
                    assertThat(exchange.getRequestHeaders().getFirst("x-amz-sdk-checksum-algorithm")).isNull();
                    assertThat(exchange.getRequestHeaders().getFirst("Content-Encoding")).isNull();
                    assertThat(exchange.getRequestBody().readAllBytes()).isEqualTo(photo);
                    exchange.getResponseHeaders().set("ETag", "\"test-etag\"");
                    exchange.sendResponseHeaders(200, -1);
                }
                case "GET" -> {
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    exchange.sendResponseHeaders(200, photo.length);
                    exchange.getResponseBody().write(photo);
                }
                case "DELETE" -> exchange.sendResponseHeaders(204, -1);
                default -> exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        });
        server.start();
        try (var storage = storage(server)) {
            storage.put("packs/example/card.jpg", photo, "image/jpeg");
            assertThat(storage.get("packs/example/card.jpg")).isEqualTo(photo);
            storage.delete("packs/example/card.jpg");
            assertThat(methods).containsExactly("PUT", "GET", "DELETE");
        } finally { server.stop(0); }
    }

    @Test
    void providerErrorsAreWrappedAndTransientRetriesAreBounded() throws Exception {
        var attempts = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            attempts.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] error = "<Error><Code>InternalError</Code><Message>private provider diagnostic</Message></Error>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            exchange.sendResponseHeaders(500, error.length);
            exchange.getResponseBody().write(error);
            exchange.close();
        });
        server.start();
        try (var storage = storage(server)) {
            assertThatThrownBy(() -> storage.put("photo.jpg", new byte[]{1}, "image/jpeg"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Object storage write failed");
            assertThat(attempts.get()).isEqualTo(2);
            attempts.set(0);
            assertThatThrownBy(() -> storage.get("photo.jpg"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Object storage read failed");
            assertThat(attempts.get()).isEqualTo(2);
            attempts.set(0);
            assertThatThrownBy(() -> storage.delete("photo.jpg"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Object storage cleanup failed");
            assertThat(attempts.get()).isEqualTo(2);
        } finally { server.stop(0); }
    }

    @Test
    void storageProviderSelectionRetainsLocalMinioAndSelectsS3Explicitly() {
        var context = new ApplicationContextRunner().withUserConfiguration(MinioObjectStorage.class, S3ObjectStorage.class)
                .withPropertyValues("brico.media.endpoint=http://127.0.0.1:9000", "brico.media.access-key=test-access",
                        "brico.media.secret-key=test-secret", "brico.media.bucket=bricocomptoir-media",
                        "brico.media.region=eu-west-3");
        context.run(c -> {
            assertThat(c).hasSingleBean(ObjectStorage.class);
            assertThat(c.getBean(ObjectStorage.class)).isInstanceOf(MinioObjectStorage.class);
        });
        context.withPropertyValues("brico.media.provider=s3",
                "brico.media.endpoint=http://127.0.0.1:9000/storage/v1/s3").run(c -> {
            assertThat(c).hasSingleBean(ObjectStorage.class);
            assertThat(c.getBean(ObjectStorage.class)).isInstanceOf(S3ObjectStorage.class);
        });
    }

    private S3ObjectStorage storage(HttpServer server) {
        return new S3ObjectStorage("http://127.0.0.1:" + server.getAddress().getPort() + "/storage/v1/s3",
                "test-access", "test-secret", "bricocomptoir-media", "eu-west-3");
    }
}
