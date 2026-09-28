package tn.bricocomptoir.identity.adapter.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;

@Component
@Profile("local & !prod")
@ConditionalOnProperty(name = "brico.bootstrap-admin", havingValue = "true")
public class LocalAdminBootstrap implements ApplicationRunner {
    private final IdentityTransactions identity;
    private final ConfigurableApplicationContext context;
    private final String email;
    private final String password;

    public LocalAdminBootstrap(IdentityTransactions identity, ConfigurableApplicationContext context,
                               @Value("${BRICO_BOOTSTRAP_ADMIN_EMAIL:}") String email,
                               @Value("${BRICO_BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.identity = identity;
        this.context = context;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        identity.bootstrapAdmin(email, password);
        System.out.println("Local administrator created. Bootstrap process is stopping.");
        SpringApplication.exit(context);
    }
}
