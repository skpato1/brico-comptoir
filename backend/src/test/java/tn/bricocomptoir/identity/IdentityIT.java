package tn.bricocomptoir.identity;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tn.bricocomptoir.notifications.domain.Mail;
import tn.bricocomptoir.notifications.application.port.out.EmailProvider;
import tn.bricocomptoir.notifications.application.service.MailDispatcher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.identity.application.port.out.AccountStore;
import tn.bricocomptoir.identity.domain.Role;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = BricoComptoirApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityIT {
    private static final String USER = "identity_test_app";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final Pattern ID = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");
    private static final String GOOD_PASSWORD = "Correct-Horse-2026!";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("bricocomptoir_identity_test")
            .withUsername("migrator")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
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
    @Autowired AccountStore accounts;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailProvider mail;
    @Autowired MailDispatcher dispatcher;

    @Test
    void registrationCannotAssignAdminAndSessionRotatesAndLogsOut() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.post("/auth/register", "{}", false).statusCode()).isEqualTo(403);
        browser.csrf();
        String before = browser.cookie("JSESSIONID");
        String oldCsrf = browser.cookie("XSRF-TOKEN");
        String email = freshEmail();
        var registered = browser.post("/auth/register", "{\"email\":\"" + email + "\",\"password\":\""
                + GOOD_PASSWORD + "\",\"roles\":[\"ADMIN\"]}", true);
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(registered.body()).contains("CUSTOMER").doesNotContain("ADMIN", "passwordHash");
        assertThat(browser.post("/auth/register", credentials(email, GOOD_PASSWORD), true).statusCode())
                .isEqualTo(409);
        assertThat(browser.get("/admin/internal-accounts").statusCode()).isEqualTo(401);
        assertThat(browser.post("/auth/login", credentials(email, "wrong-password-2026"), true).statusCode())
                .isEqualTo(401);
        assertThat(browser.post("/auth/login", credentials(email, GOOD_PASSWORD), true).statusCode())
                .isEqualTo(200);
        assertThat(browser.cookie("JSESSIONID")).isNotEqualTo(before);
        assertThat(browser.get("/auth/me").body()).contains(email, "CUSTOMER");
        assertThat(browser.get("/admin/internal-accounts").statusCode()).isEqualTo(403);
        assertThat(browser.postWithToken("/auth/logout", "{}", oldCsrf).statusCode()).isEqualTo(403);
        browser.csrf();
        assertThat(browser.post("/auth/logout", "{}", true).statusCode()).isEqualTo(204);
        assertThat(browser.get("/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void backendEnforcesOwnershipAdminRolesAndImmediateRevocation() throws Exception {
        String adminEmail = freshEmail();
        var admin = identity.bootstrapAdmin(adminEmail, GOOD_PASSWORD);
        assertThatThrownBy(() -> identity.bootstrapAdmin(freshEmail(), GOOD_PASSWORD))
                .isInstanceOf(IllegalStateException.class);
        Browser root = new Browser();
        root.csrf();
        root.post("/auth/login", credentials(adminEmail, GOOD_PASSWORD), true);
        root.csrf();

        Browser customer = new Browser();
        customer.csrf();
        String customerEmail = freshEmail();
        var registered = customer.post("/auth/register", credentials(customerEmail, GOOD_PASSWORD), true);
        UUID customerId = id(registered.body());
        customer.post("/auth/login", credentials(customerEmail, GOOD_PASSWORD), true);

        assertThat(customer.get("/accounts/" + admin.id()).statusCode()).isEqualTo(403);
        assertThat(root.get("/accounts/" + customerId).statusCode()).isEqualTo(200);
        assertThat(customer.post("/admin/internal-accounts", credentials(freshEmail(), GOOD_PASSWORD), true)
                .statusCode()).isEqualTo(403);

        String managerEmail = freshEmail();
        var internal = root.post("/admin/internal-accounts", "{\"email\":\"" + managerEmail
                + "\",\"password\":\"" + GOOD_PASSWORD + "\",\"roles\":[\"CATALOG_MANAGER\"]}", true);
        assertThat(internal.statusCode()).isEqualTo(201);
        assertThat(internal.body()).contains("CATALOG_MANAGER").doesNotContain("CUSTOMER");
        UUID managerId = id(internal.body());
        Browser manager = new Browser();
        manager.csrf();
        assertThat(manager.post("/auth/login", credentials(managerEmail, GOOD_PASSWORD), true).statusCode())
                .isEqualTo(200);
        assertThat(manager.get("/accounts/" + customerId).statusCode()).isEqualTo(403);

        assertThat(root.put("/admin/accounts/" + admin.id() + "/roles", "{\"roles\":[\"ORDER_MANAGER\"]}")
                .statusCode()).isEqualTo(409);
        assertThat(root.patch("/admin/accounts/" + admin.id() + "/active", "{\"active\":false}")
                .statusCode()).isEqualTo(409);
        assertThat(root.patch("/admin/accounts/" + customerId + "/active", "{\"active\":false}")
                .statusCode()).isEqualTo(200);
        assertThat(customer.get("/auth/me").statusCode()).isEqualTo(401);
        assertThat(root.get("/accounts/" + customerId).body()).contains("\"active\":false");
        assertThat(root.put("/admin/accounts/" + managerId + "/roles", "{\"roles\":[\"ADMIN\"]}")
                .statusCode()).isEqualTo(200);
        assertThat(manager.get("/auth/me").statusCode()).isEqualTo(401);
        manager.csrf();
        manager.post("/auth/login", credentials(managerEmail, GOOD_PASSWORD), true);
        assertThat(manager.get("/accounts/" + customerId).statusCode()).isEqualTo(200);
        assertThat(root.put("/admin/accounts/" + admin.id() + "/roles", "{\"roles\":[\"ORDER_MANAGER\"]}")
                .statusCode()).isEqualTo(200);
        assertThat(root.get("/auth/me").statusCode()).isEqualTo(401);

        var secondAdmin = identity.createInternal(managerId, freshEmail(), GOOD_PASSWORD, Set.of(Role.ADMIN));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> deactivateAfter(start, managerId));
            var second = pool.submit(() -> deactivateAfter(start, secondAdmin.id()));
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(accounts.activeAdminCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean deactivateAfter(CountDownLatch start, UUID administrator) throws InterruptedException {
        start.await();
        try {
            identity.changeActive(administrator, administrator, false);
            return true;
        } catch (IllegalStateException | SecurityException refused) {
            return false;
        }
    }

    @Test
    void resetEmailIsGenericAndTokenIsSingleUseAndRevokesExistingSession() throws Exception {
        Browser browser = new Browser();
        browser.csrf();
        String email = freshEmail();
        browser.post("/auth/register", credentials(email, GOOD_PASSWORD), true);
        browser.post("/auth/login", credentials(email, GOOD_PASSWORD), true);
        browser.csrf();
        clearInvocations(mail);
        assertThat(browser.post("/auth/password-reset/request", "{\"email\":\"nobody@example.tn\"}", true)
                .statusCode()).isEqualTo(202);
        assertThat(browser.post("/auth/password-reset/request", "{\"email\":\"" + email + "\"}", true)
                .statusCode()).isEqualTo(202);
        assertThat(dispatcher.dispatchOne()).isTrue();
        var captured = org.mockito.ArgumentCaptor.forClass(Mail.class);
        verify(mail).send(captured.capture());
        String text = captured.getValue().text();
        assertThat(text).contains("/compte#reset=");
        String token = text.substring(text.indexOf("/compte#reset=") + 14).split("\\s")[0];
        assertThat(browser.post("/auth/password-reset/complete", "{\"token\":\"" + token
                + "\",\"password\":\"Replacement-2026!\"}", true).statusCode()).isEqualTo(204);
        assertThat(browser.get("/auth/me").statusCode()).isEqualTo(401);
        assertThat(browser.post("/auth/password-reset/complete", "{\"token\":\"" + token
                + "\",\"password\":\"Another-2026!\"}", true).statusCode()).isEqualTo(400);
        assertThat(browser.post("/auth/login", credentials(email, GOOD_PASSWORD), true).statusCode()).isEqualTo(401);
        assertThat(browser.post("/auth/login", credentials(email, "Replacement-2026!"), true).statusCode())
                .isEqualTo(200);

        browser.csrf();
        clearInvocations(mail);
        assertThat(browser.post("/auth/password-reset/request", "{\"email\":\"" + email + "\"}", true)
                .statusCode()).isEqualTo(202);
        assertThat(dispatcher.dispatchOne()).isTrue();
        var expiredMail = org.mockito.ArgumentCaptor.forClass(Mail.class);
        verify(mail).send(expiredMail.capture());
        String expiredText = expiredMail.getValue().text();
        String expiredToken = expiredText.substring(expiredText.indexOf("/compte#reset=") + 14).split("\\s")[0];
        jdbc.update("UPDATE identity_password_reset SET expires_at = now() - interval '1 second' "
                + "WHERE account_id = (SELECT id FROM identity_account WHERE email = ?)", email);
        assertThat(browser.post("/auth/password-reset/complete", "{\"token\":\"" + expiredToken
                + "\",\"password\":\"Too-Late-2026!\"}", true).statusCode()).isEqualTo(400);
    }

    private String freshEmail() { return UUID.randomUUID() + "@example.tn"; }
    private static UUID id(String body) {
        var matcher = ID.matcher(body);
        assertThat(matcher.find()).isTrue();
        return UUID.fromString(matcher.group(1));
    }
    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private final class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

        void csrf() throws Exception {
            assertThat(get("/auth/csrf").statusCode()).isEqualTo(204);
            assertThat(cookie("XSRF-TOKEN")).isNotBlank();
        }

        String cookie(String name) {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals(name))
                    .map(HttpCookie::getValue).findFirst().orElse("");
        }

        HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET());
        }

        HttpResponse<String> post(String path, String json, boolean token) throws Exception {
            return body("POST", path, json, token);
        }

        HttpResponse<String> postWithToken(String path, String json, String token) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", token).POST(HttpRequest.BodyPublishers.ofString(json));
            return send(builder);
        }

        HttpResponse<String> put(String path, String json) throws Exception {
            return body("PUT", path, json, true);
        }

        HttpResponse<String> patch(String path, String json) throws Exception {
            return body("PATCH", path, json, true);
        }

        private HttpResponse<String> body(String method, String path, String json, boolean token) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
            if (token) builder.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
            return send(builder);
        }

        private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private URI uri(String path) { return URI.create("http://localhost:" + port + "/api/v1" + path); }
    }
}
