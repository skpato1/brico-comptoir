package tn.bricocomptoir.bootstrap;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;

class StartupConfigurationCheckTest {
    private MockEnvironment validEnvironment() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://database:5432/brico")
                .withProperty("spring.datasource.username", "brico_app")
                .withProperty("spring.datasource.password", "test-only-password")
                .withProperty("spring.flyway.user", "brico_migrator")
                .withProperty("spring.flyway.password", "test-only-password")
                .withProperty("brico.media.endpoint", "https://objects.example.invalid")
                .withProperty("brico.media.access-key", "test-only-access")
                .withProperty("brico.media.secret-key", "test-only-secret")
                .withProperty("spring.mail.host", "smtp.example.invalid")
                .withProperty("brico.mail.from", "test@example.invalid")
                .withProperty("brico.public-url", "https://shop.example.invalid")
                .withProperty("server.port", "10000")
                .withProperty("brico.mail.outbox-key", key)
                .withProperty("brico.privacy.archive-key", key);
    }

    @Test void acceptsResolvedConfiguration() {
        assertThat(StartupConfigurationCheck.invalidVariables(validEnvironment())).isEmpty();
    }

    @Test void reportsBlankDatabaseAndProviderPlaceholdersByNameOnly() {
        var environment = validEnvironment().withProperty("spring.datasource.url", "")
                .withProperty("spring.datasource.password", " ")
                .withProperty("brico.media.endpoint", "https://A_REMPLACER.example.invalid")
                .withProperty("spring.mail.host", "A_REMPLACER");
        assertThat(StartupConfigurationCheck.invalidVariables(environment))
                .containsExactly("DB_URL", "DB_PASSWORD", "MEDIA_ENDPOINT", "SMTP_HOST");
    }

    @Test void unresolvedSecretPlaceholderNeverEscapesInError() {
        var environment = validEnvironment().withProperty("spring.datasource.password", "${MISSING_PASSWORD}");
        assertThat(StartupConfigurationCheck.invalidVariables(environment)).containsExactly("DB_PASSWORD");
    }

    @Test void rejectsNonJdbcDatabaseAndEmptyOrInvalidPort() {
        var environment = validEnvironment().withProperty("spring.datasource.url", "postgresql://database/brico")
                .withProperty("server.port", "");
        assertThat(StartupConfigurationCheck.invalidVariables(environment)).containsExactly("DB_URL", "SERVER_PORT");
        for (String port : new String[]{"0", "65536", "not-a-number"}) {
            assertThat(StartupConfigurationCheck.invalidVariables(validEnvironment().withProperty("server.port", port)))
                    .containsExactly("SERVER_PORT");
        }
    }

    @Test void rejectsMalformedOrWrongLengthEncryptionKeys() {
        var environment = validEnvironment().withProperty("brico.mail.outbox-key", "not-base64!")
                .withProperty("brico.privacy.archive-key", Base64.getEncoder().encodeToString(new byte[16]));
        assertThat(StartupConfigurationCheck.invalidVariables(environment))
                .containsExactly("MAIL_OUTBOX_KEY", "DATA_ARCHIVE_KEY");
    }
}
