package tn.bricocomptoir.bootstrap;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Fail before opening connections; diagnostics contain fixed variable names, never values. */
public final class StartupConfigurationCheck
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String[][] REQUIRED = {
        {"DB_URL", "spring.datasource.url"},
        {"DB_USERNAME", "spring.datasource.username"},
        {"DB_PASSWORD", "spring.datasource.password"},
        {"DB_MIGRATION_USERNAME", "spring.flyway.user"},
        {"DB_MIGRATION_PASSWORD", "spring.flyway.password"},
        {"MEDIA_ENDPOINT", "brico.media.endpoint"},
        {"MEDIA_ACCESS_KEY", "brico.media.access-key"},
        {"MEDIA_SECRET_KEY", "brico.media.secret-key"},
        {"SMTP_HOST", "spring.mail.host"},
        {"MAIL_FROM", "brico.mail.from"},
        {"APP_PUBLIC_URL", "brico.public-url"},
        {"SERVER_PORT", "server.port"},
        {"MAIL_OUTBOX_KEY", "brico.mail.outbox-key"},
        {"DATA_ARCHIVE_KEY", "brico.privacy.archive-key"}
    };

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        List<String> invalid = invalidVariables(context.getEnvironment());
        if (!invalid.isEmpty()) {
            var logger = LoggerFactory.getLogger(StartupConfigurationCheck.class);
            invalid.forEach(name -> logger.error("CONFIG_INVALID_" + name));
            throw new IllegalStateException("STARTUP_CONFIGURATION_INVALID");
        }
    }

    static List<String> invalidVariables(Environment environment) {
        List<String> invalid = new ArrayList<>();
        for (String[] setting : REQUIRED) {
            String value;
            try {
                value = environment.getProperty(setting[1]);
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                value = null;
            }
            boolean valid = value != null && !value.isBlank()
                    && !value.contains("A_REMPLACER") && !value.contains("${");
            if (valid && setting[0].equals("DB_URL")) valid = value.startsWith("jdbc:postgresql://");
            if (valid && setting[0].equals("SERVER_PORT")) {
                try {
                    int port = Integer.parseInt(value);
                    valid = port > 0 && port <= 65535;
                } catch (NumberFormatException invalidPort) {
                    valid = false;
                }
            }
            if (valid && (setting[0].equals("MAIL_OUTBOX_KEY") || setting[0].equals("DATA_ARCHIVE_KEY"))) {
                try {
                    valid = Base64.getDecoder().decode(value).length == 32;
                } catch (IllegalArgumentException invalidKey) {
                    valid = false;
                }
            }
            if (!valid) invalid.add(setting[0]);
        }
        return List.copyOf(invalid);
    }
}
