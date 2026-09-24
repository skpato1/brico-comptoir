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
    void baselineMigrationIsAppliedOnceAndCanBeValidatedAgain() {
        assertThat(flyway.info().applied()).filteredOn(info -> info.getVersion() != null).hasSize(1);
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
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
