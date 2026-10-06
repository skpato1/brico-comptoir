package tn.bricocomptoir.bootstrap;

import java.time.Clock;
import java.util.Map;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import tn.bricocomptoir.identity.adapter.security.SessionUser;
import tn.bricocomptoir.identity.adapter.security.SessionVersionFilter;
import tn.bricocomptoir.identity.application.port.out.AccountStore;
import tn.bricocomptoir.identity.application.port.out.PasswordHasher;
import tn.bricocomptoir.identity.application.port.out.ResetDelivery;
import tn.bricocomptoir.identity.application.port.out.ResetStore;
import tn.bricocomptoir.identity.application.service.IdentityService;
import tn.bricocomptoir.identity.domain.AccountPolicy;
import tn.bricocomptoir.catalog.application.port.out.CatalogStore;
import tn.bricocomptoir.catalog.application.service.CatalogService;
import tn.bricocomptoir.catalog.application.service.CatalogImportService;
import tn.bricocomptoir.media.application.service.MediaService;
import tn.bricocomptoir.media.application.service.PackImageService;
import tn.bricocomptoir.media.application.port.out.PackPhotoLookup;
import tn.bricocomptoir.media.application.port.out.PackImageStore;
import tn.bricocomptoir.media.application.port.out.CatalogProductLookup;
import tn.bricocomptoir.media.application.port.out.ImageProcessor;
import tn.bricocomptoir.media.application.port.out.MediaStore;
import tn.bricocomptoir.media.application.port.out.ObjectStorage;
import tn.bricocomptoir.inventory.application.service.InventoryService;
import tn.bricocomptoir.inventory.application.port.out.InventoryStore;
import tn.bricocomptoir.inventory.application.port.out.VariantReferencePort;
import tn.bricocomptoir.packs.application.service.PackService;
import tn.bricocomptoir.packs.application.port.out.CatalogSkuLookup;
import tn.bricocomptoir.packs.application.port.out.PackStore;
import tn.bricocomptoir.packs.application.port.out.StockAvailabilityLookup;
import tn.bricocomptoir.sales.application.port.out.CartStore;
import tn.bricocomptoir.sales.application.port.out.OfferLookup;
import tn.bricocomptoir.sales.application.port.out.StockLookup;
import tn.bricocomptoir.sales.application.service.CartService;
import tn.bricocomptoir.sales.application.service.OrderService;
import tn.bricocomptoir.sales.application.port.out.OrderStore;
import tn.bricocomptoir.sales.application.port.out.CheckoutOffers;
import tn.bricocomptoir.sales.application.port.out.OrderStock;
import tn.bricocomptoir.sales.application.port.out.DeliveryFees;
import tn.bricocomptoir.sales.application.port.out.OrderNotifications;
import tn.bricocomptoir.sales.application.port.out.CustomerContact;
import tn.bricocomptoir.notifications.application.port.out.OutboxStore;
import tn.bricocomptoir.notifications.application.port.out.EmailProvider;
import tn.bricocomptoir.notifications.application.service.MailDispatcher;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SecurityConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }
    @Bean
    tn.bricocomptoir.privacy.domain.PrivacyPolicy privacyPolicy(
        @Value("${brico.privacy.contact-days}") int contacts,@Value("${brico.privacy.cart-days}") int carts,
        @Value("${brico.privacy.mail-days}") int mails,@Value("${brico.privacy.audit-days}") int audits,
        @Value("${brico.privacy.legal-days}") int legal,@Value("${brico.privacy.retention-enabled}") boolean enabled) {
        return new tn.bricocomptoir.privacy.domain.PrivacyPolicy(contacts,carts,mails,audits,legal,enabled);
    }
    @Bean
    tn.bricocomptoir.privacy.application.service.PrivacyService privacyService(
        tn.bricocomptoir.privacy.application.port.out.PersonalData data,
        tn.bricocomptoir.privacy.application.port.out.PrivacyStore store,
        tn.bricocomptoir.privacy.domain.PrivacyPolicy policy,Clock clock) {
        return new tn.bricocomptoir.privacy.application.service.PrivacyService(data,store,policy,clock);
    }

    @Bean
    CatalogService catalogService(CatalogStore store) { return new CatalogService(store); }

    @Bean
    CatalogImportService catalogImportService(CatalogStore store, CatalogService catalog) {
        return new CatalogImportService(store, catalog);
    }

    @Bean
    MediaService mediaService(CatalogProductLookup catalog, ImageProcessor processor,
                              MediaStore store, ObjectStorage objects) {
        return new MediaService(catalog, processor, store, objects);
    }

    @Bean
    PackImageService packImageService(PackPhotoLookup packs, ImageProcessor processor,
                                      PackImageStore store, ObjectStorage objects) {
        return new PackImageService(packs, processor, store, objects);
    }

    @Bean
    InventoryService inventoryService(InventoryStore store, VariantReferencePort variants) {
        return new InventoryService(store, variants);
    }

    @Bean
    PackService packService(PackStore store, CatalogSkuLookup catalog, StockAvailabilityLookup stock) {
        return new PackService(store, catalog, stock);
    }

    @Bean
    CartService cartService(CartStore store, OfferLookup offers, StockLookup stock) {
        return new CartService(store, offers, stock);
    }

    @Bean
    OrderService orderService(OrderStore store, CheckoutOffers offers, StockLookup availability,
                              OrderStock stock, DeliveryFees delivery, Clock clock,OrderNotifications notifications,CustomerContact contact) {
        return new OrderService(store, offers, availability, stock, delivery, clock,notifications,contact);
    }
    @Bean
    MailDispatcher mailDispatcher(OutboxStore store,EmailProvider provider,Clock clock) {
        return new MailDispatcher(store,provider,clock);
    }

    @Bean
    IdentityService identityService(AccountStore accounts, PasswordHasher passwords,
                                    ResetStore resets, ResetDelivery delivery, Clock clock) {
        return new IdentityService(accounts, passwords, resets, delivery, clock);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("argon2id", Map.of("argon2id",
                Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    PasswordHasher passwordHasher(PasswordEncoder encoder) { return encoder::encode; }

    @Bean
    UserDetailsService userDetailsService(AccountStore accounts) {
        return username -> {
            String email;
            try { email = AccountPolicy.normalizeEmail(username); }
            catch (IllegalArgumentException invalid) {
                throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Invalid credentials");
            }
            return accounts.byEmail(email).map(SessionUser::new)
                    .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Invalid credentials"));
        };
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(@Value("${server.servlet.session.cookie.secure}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure));
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrf,
                                             AccountStore accounts,
                                             HttpSessionSecurityContextRepository context) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/health/liveness",
                                "/api/v1/health/readiness", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/complete",
                                "/api/v1/cart/estimate", "/api/v1/checkout/preview", "/api/v1/orders",
                                "/api/v1/orders/*/cancel", "/api/v1/contact/messages").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*").permitAll()
                        .requestMatchers("/api/v1/privacy/guest/**").permitAll()
                        .requestMatchers("/api/v1/privacy/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/content/home", "/api/v1/content/hero",
                                "/api/v1/contact").permitAll()
                        .requestMatchers("/api/v1/admin/content/**").hasAnyRole("CATALOG_MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/admin/contact/messages", "/api/v1/admin/contact/messages/**")
                                .hasAnyRole("ORDER_MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/brands",
                                "/api/v1/products", "/api/v1/products/*", "/api/v1/availability/*",
                                "/api/v1/packs", "/api/v1/packs/*",
                                "/api/v1/media/products", "/api/v1/media/*/card",
                                "/api/v1/media/*/detail", "/api/v1/media/packs",
                                "/api/v1/media/packs/*/card", "/api/v1/media/packs/*/detail").permitAll()
                        .requestMatchers("/api/v1/admin/catalog/**").hasAnyRole("CATALOG_MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/admin/packs", "/api/v1/admin/packs/**")
                                .hasAnyRole("CATALOG_MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/admin/orders", "/api/v1/admin/orders/**")
                                .hasAnyRole("ORDER_MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/admin/order-events", "/api/v1/admin/order-events/stream")
                                .hasAnyRole("ORDER_MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/cart", "/api/v1/cart/merge").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me", "/api/v1/accounts/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .anyRequest().denyAll())
                .csrf(config -> config.spa().csrfTokenRepository(csrf))
                .securityContext(config -> config.securityContextRepository(context))
                .sessionManagement(config -> config.sessionFixation(fixation -> fixation.changeSessionId()))
                .addFilterBefore(new SessionVersionFilter(accounts), AuthorizationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            Throwable cause = exception;
                            while (cause != null) {
                                if (cause instanceof CsrfException) {
                                    response.sendError(403);
                                    return;
                                }
                                cause = cause.getCause();
                            }
                            response.sendError(401);
                        })
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                .build();
    }
}
