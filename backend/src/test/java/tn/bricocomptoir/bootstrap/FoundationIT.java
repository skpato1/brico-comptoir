package tn.bricocomptoir.bootstrap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationIT {
    private static final String APPLICATION_USER = "brico_test_app";
    private static final String APPLICATION_PASSWORD = UUID.randomUUID().toString();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_test")
            .withUsername("migrator")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            // Only a generated UUID is interpolated, never external input.
            statement.execute("CREATE ROLE " + APPLICATION_USER + " LOGIN PASSWORD '"
                    + APPLICATION_PASSWORD + "'");
        }
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", () -> APPLICATION_USER);
        registry.add("DB_PASSWORD", () -> APPLICATION_PASSWORD);
        registry.add("DB_MIGRATION_USERNAME", POSTGRES::getUsername);
        registry.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    Flyway flyway;

    @Autowired
    DataSource dataSource;

    @Autowired org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void healthAndReadinessCheckRealPostgresWithoutDisclosingDetails() throws Exception {
        for (var path : new String[]{"/api/v1/health", "/api/v1/health/readiness", "/api/v1/health/liveness"}) {
            var response = get(path);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"")
                    .doesNotContain("components", "jdbc:", "password");
        }
    }

    @Test
    void unlistedRoutesAndActuatorDetailsAreClosedWithoutLoginRedirect() throws Exception {
        for (var path : new String[]{"/api/v1/orders", "/api/v1/env", "/actuator", "/api/v1/health/db"}) {
            var response = get(path);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("location")).isEmpty();
        }
    }

    @Test
    void unsafeRequestsStillRequireCsrf() throws Exception {
        var request = HttpRequest.newBuilder(uri("/api/v1/health"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void spaUsesConfiguredSecureCsrfCookieEvenBehindHttpTlsTermination() throws Exception {
        // Same repository configuration as production, over the HTTP leg behind TLS ingress.
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(true));
        try {
            var response = get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(204);
            String cookie = response.headers().allValues("set-cookie").stream()
                    .filter(value -> value.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
            assertThat(cookie).contains("Secure", "SameSite=Lax").doesNotContain("HttpOnly");
        } finally {
            csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(false));
        }
    }

    @Test
    void trustedGatewayPreservesSeparateClientRateLimits() throws Exception {
        var cookies = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
        var browser = HttpClient.newBuilder().cookieHandler(cookies).build();
        browser.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.discarding());
        String token = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .findFirst().orElseThrow().getValue();
        // Loopback is a trusted gateway in this test; the production peer allowlist is explicit.
        for (int i = 0; i < 7; i++) {
            var request = HttpRequest.newBuilder(uri("/api/v1/auth/password-reset/request"))
                    .header("X-Forwarded-For", i == 6 ? "198.51.100.12" : "198.51.100.11")
                    .header("X-XSRF-TOKEN", token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"absent@test.invalid\"}")).build();
            assertThat(browser.send(request, HttpResponse.BodyHandlers.discarding()).statusCode())
                    .as("request %s from a distinct gateway client", i).isEqualTo(i == 5 ? 429 : 202);
        }
    }

    @Test
    void baselineMigrationIsAppliedOnceAndCanBeValidatedAgain() {
        assertThat(flyway.info().applied()).filteredOn(info -> info.getVersion() != null).hasSize(13);
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("13");
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void runtimeRoleCanUseSchemaButCannotCreateTables() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_user, "
                     + "has_schema_privilege(current_user, 'bricocomptoir', 'USAGE'), "
                     + "has_schema_privilege(current_user, 'bricocomptoir', 'CREATE')")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(APPLICATION_USER);
            assertThat(result.getBoolean(2)).isTrue();
            assertThat(result.getBoolean(3)).isFalse();
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
