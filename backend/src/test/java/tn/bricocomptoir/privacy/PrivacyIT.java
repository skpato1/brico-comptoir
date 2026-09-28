package tn.bricocomptoir.privacy;
import java.net.*;
import java.net.http.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.identity.domain.*;
import tn.bricocomptoir.catalog.adapter.transaction.CatalogTransactions;
import tn.bricocomptoir.inventory.adapter.transaction.InventoryTransactions;
import tn.bricocomptoir.sales.adapter.transaction.OrderTransactions;
import tn.bricocomptoir.sales.domain.CartModels.Kind;
import tn.bricocomptoir.sales.domain.OrderModels.*;
import tn.bricocomptoir.sales.domain.OrderModels.Order;
import tn.bricocomptoir.privacy.adapter.transaction.PrivacyTransactions;
import tn.bricocomptoir.privacy.application.service.PrivacyService;
import tn.bricocomptoir.notifications.application.port.out.EmailProvider;
import tn.bricocomptoir.notifications.application.service.MailDispatcher;
import tn.bricocomptoir.notifications.domain.Mail;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers @ActiveProfiles("test")
@SpringBootTest(classes=BricoComptoirApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrivacyIT {
    private static final String USER="privacy_test_app",PASSWORD=UUID.randomUUID().toString(),LOGIN="Correct-Horse-2026!";
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6-alpine")
        .withDatabaseName("privacy_test").withUsername("migrator").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void database(DynamicPropertyRegistry r)throws Exception {
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var s=c.createStatement()) { s.execute("CREATE ROLE "+USER+" LOGIN PASSWORD '"+PASSWORD+"'"); }
        r.add("DB_URL",POSTGRES::getJdbcUrl);r.add("DB_USERNAME",()->USER);r.add("DB_PASSWORD",()->PASSWORD);
        r.add("DB_MIGRATION_USERNAME",POSTGRES::getUsername);r.add("DB_MIGRATION_PASSWORD",POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired IdentityTransactions identity;
    @Autowired CatalogTransactions catalog;
    @Autowired InventoryTransactions inventory;
    @Autowired OrderTransactions orders;
    @Autowired PrivacyTransactions privacy;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MailDispatcher dispatcher;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @MockitoBean EmailProvider mail;
    @MockitoBean tn.bricocomptoir.identity.adapter.security.AttemptThrottle loginThrottle;
    private static Account admin;
    @BeforeEach void setup() {
        if(admin==null)admin=identity.bootstrapAdmin(fresh(),LOGIN);
        jdbc.update("UPDATE notification_mail_outbox SET state='CANCELLED',payload=NULL,lease_token=NULL,lease_until=NULL WHERE state<>'SENT'");
        reset(mail);
        when(loginThrottle.allow(anyString(),anyString(),anyInt(),any())).thenReturn(true);
    }
    private String fresh() { return UUID.randomUUID()+"@example.invalid"; }
    private Account customer() { return identity.register(fresh(),LOGIN); }
    record Purchase(Actor actor,List<Item> items,Address address,String hash,UUID sku) { }
    private Purchase purchase(UUID account) {
        UUID id=UUID.randomUUID();var category=catalog.saveCategory(null,null,"privacy-"+id,"TEST confidentialité",true,null);
        var product=catalog.saveProduct(null,category.id(),null,"TEST confidentialité","",Map.of(),"PUBLISHED",null);
        var sku=catalog.saveVariant(null,product.id(),"PRIVACY-"+id.toString().toUpperCase(),"Unité","pièce",Map.of(),"2.375","PUBLISHED",null);
        inventory.adjust(UUID.randomUUID(),sku.id(),5,"Test confidentialité","test");
        var actor=new Actor(account==null?"G:"+id:"C:"+account,account,false);
        var address=new Address("TEST NE PAS LIVRER","20123456","Rue privée fictive","Tunis","1000","TUNIS","TN","guest@example.invalid");
        var items=List.of(new Item(Kind.PRODUCT,sku.id(),1,sku.version(),catalog.product(product.id(),false).version()));
        return new Purchase(actor,items,address,orders.preview(actor,items,address).quoteHash(),sku.id());
    }
    private Order place(Purchase p) { return orders.place(p.actor(),UUID.randomUUID(),p.items(),p.address(),p.hash()); }
    private Actor root() { return new Actor("C:"+admin.id(),admin.id(),true); }
    @Test void consentDefaultsFalseIsIndependentAndWithdrawalDoesNotCancelAnOrder()throws Exception {
        var a=customer();var b=new Browser();b.login(a.email());
        assertThat(b.get("/privacy/me").body()).contains("\"marketing\":false").doesNotContain("passwordHash","tokenHash");
        assertThat(b.request("PUT","/privacy/consent",Map.of("marketing",true,"noticeVersion",PrivacyService.NOTICE),false).statusCode()).isEqualTo(403);
        assertThat(b.request("PUT","/privacy/consent",Map.of("marketing",true,"noticeVersion","old"),true).statusCode()).isEqualTo(400);
        assertThat(b.request("PUT","/privacy/consent",Map.of("noticeVersion",PrivacyService.NOTICE),true).statusCode()).isEqualTo(400);
        var p=purchase(a.id());var order=place(p);
        assertThat(privacy.view(a.id()).consent().marketing()).isFalse();
        for(boolean value:List.of(true,false)) assertThat(b.request("PUT","/privacy/consent",Map.of("marketing",value,"noticeVersion",PrivacyService.NOTICE,"accountId",admin.id()),true).statusCode()).isEqualTo(200);
        assertThat(orders.get(p.actor(),order.id()).status()).isEqualTo(Status.CONFIRMED);
        assertThat(privacy.view(admin.id()).consent().marketing()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM privacy_audit WHERE subject_scope=? AND action LIKE 'MARKETING_%'",Long.class,p.actor().scope())).isEqualTo(2);
    }
    @Test void exportsRequireReauthenticationNeverIncludeOtherPeopleOrSecretsAndAdminListIsMinimal()throws Exception {
        var a=customer();var other=customer();var own=place(purchase(a.id()));var theirs=place(purchase(other.id()));
        var b=new Browser();b.login(a.email());
        assertThat(b.request("POST","/privacy/export",Map.of("password","wrong"),true).statusCode()).isEqualTo(401);
        var result=b.request("POST","/privacy/export",Map.of("password",LOGIN,"accountId",other.id()),true);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains(a.email(),own.id().toString(),"Rue privée fictive").doesNotContain(other.email(),theirs.id().toString(),"password_hash","passwordHash","token_hash","tokenHash","ciphertext","ownerScope");
        assertThat(result.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var r=new Browser();r.login(admin.email());var list=r.get("/admin/orders");
        assertThat(list.body()).doesNotContain(a.email(),other.email(),"Rue privée fictive","20123456","TEST NE PAS LIVRER");
        assertThat(r.get("/admin/orders/"+own.id()).body()).contains(a.email(),"Rue privée fictive");
        assertThat(b.get("/privacy/accounts/"+other.id()).statusCode()).isEqualTo(404);
        assertThat(new Browser().get("/privacy/me").statusCode()).isEqualTo(401);
    }
    @Test void emailCorrectionIsVerifiedSingleUseAndRevokesAllPreviousSessions()throws Exception {
        var a=customer();var b=new Browser();b.login(a.email());var second=new Browser();second.login(a.email());String email=fresh();
        assertThat(b.request("POST","/privacy/email/request",Map.of("password",LOGIN,"email",email),true).statusCode()).isEqualTo(200);
        assertThat(identity.findVisible(a.id(),a.id()).email()).isEqualTo(a.email());
        assertThat(dispatcher.dispatchOne()).isTrue();var capture=org.mockito.ArgumentCaptor.forClass(Mail.class);verify(mail).send(capture.capture());
        String token=capture.getValue().text().split("#email=")[1];
        assertThat(jdbc.queryForObject("SELECT token_hash FROM privacy_email_change WHERE account_id=?",String.class,a.id())).doesNotContain(token);
        var other=new Browser();other.login(customer().email());
        assertThat(other.request("POST","/privacy/email/confirm",Map.of("token",token),true).statusCode()).isEqualTo(400);
        assertThat(b.request("POST","/privacy/email/confirm",Map.of("token",token),true).statusCode()).isEqualTo(200);
        assertThat(second.get("/auth/me").statusCode()).isEqualTo(401);
        assertThat(identity.findVisible(a.id(),a.id()).email()).isEqualTo(email);
        var renewed=new Browser();renewed.login(email);
        assertThat(renewed.request("POST","/privacy/email/confirm",Map.of("token",token),true).statusCode()).isEqualTo(400);
    }
    @Test void deliveryRectificationChecksOwnershipStatusZoneAndPreservesMoneyAndLines()throws Exception {
        var a=customer();var p=purchase(a.id());var o=place(p);var b=new Browser();b.login(a.email());
        var newAddress=new Address("Destinataire corrigé","20987654","Rue corrigée","Tunis","1001","TUNIS","TN","spoof@example.invalid");
        var other=new Browser();other.login(customer().email());
        assertThat(other.request("PATCH","/privacy/orders/"+o.id()+"/address",newAddress,true).statusCode()).isEqualTo(400);
        assertThat(b.request("PATCH","/privacy/orders/"+o.id()+"/address",newAddress,true).statusCode()).isEqualTo(200);
        var changed=orders.get(p.actor(),o.id());assertThat(changed.snapshot().address().street()).isEqualTo("Rue corrigée");
        assertThat(changed.snapshot().address().email()).isEqualTo(a.email());assertThat(changed.snapshot().items()).isEqualTo(o.snapshot().items());assertThat(changed.snapshot().totalTnd()).isEqualTo(o.snapshot().totalTnd());
        var wrongZone=new Address("TEST","20123456","Rue","Sfax","3000","SFAX","TN",null);
        assertThat(b.request("PATCH","/privacy/orders/"+o.id()+"/address",wrongZone,true).statusCode()).isEqualTo(400);
        var invalidPhone=new Address("TEST","not-a-phone","Rue","Tunis","1000","TUNIS","TN",null);
        assertThat(b.request("PATCH","/privacy/orders/"+o.id()+"/address",invalidPhone,true).statusCode()).isEqualTo(400);
        orders.transition(root(),o.id(),Status.PREPARING);
        assertThat(b.request("PATCH","/privacy/orders/"+o.id()+"/address",newAddress,true).statusCode()).isEqualTo(409);
    }
    @Test void removalRefusesActiveOrdersThenArchivesAndInvalidatesCredentialsAndCancelsMail()throws Exception {
        var a=customer();var p=purchase(a.id());var order=place(p);var b=new Browser();b.login(a.email());
        assertThat(b.request("POST","/privacy/anonymize",Map.of("password",LOGIN,"confirm",false),true).statusCode()).isEqualTo(400);
        assertThat(b.request("POST","/privacy/anonymize",Map.of("password",LOGIN,"confirm",true),true).statusCode()).isEqualTo(409);
        assertThat(identity.findVisible(a.id(),a.id()).active()).isTrue();assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order_contact WHERE order_id=?",Long.class,order.id())).isEqualTo(1);
        orders.transition(root(),order.id(),Status.CANCELLED);
        jdbc.update("INSERT INTO customer_cart(customer_id) VALUES (?)",a.id());
        assertThat(b.request("POST","/privacy/anonymize",Map.of("password",LOGIN,"confirm",true),true).statusCode()).isEqualTo(200);
        assertThat(b.get("/auth/me").statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT email FROM identity_account WHERE id=?",String.class,a.id())).startsWith("deleted-");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM identity_account WHERE id=?",String.class,a.id())).isEqualTo("!DISABLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_cart WHERE customer_id=?",Long.class,a.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_mail_outbox WHERE subject_scope=? AND payload IS NOT NULL",Long.class,p.actor().scope())).isZero();
        var snapshot=jdbc.queryForObject("SELECT snapshot::text FROM sales_order WHERE id=?",String.class,order.id());assertThat(snapshot).doesNotContain("Rue privée fictive",a.email(),"20123456").contains("2.375");
        assertThatThrownBy(()->jdbc.queryForObject("SELECT ciphertext FROM sales_order_private_archive WHERE order_id=?",byte[].class,order.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(archiveCount(order.id())).isEqualTo(1);
        var root=new Browser();root.login(admin.email());
        assertThat(root.request("POST","/admin/privacy/orders/"+order.id()+"/archive-export",Map.of("password",LOGIN,"caseReference","CASE-TEST-ACCESS"),true).body()).contains("Rue privée fictive",a.email());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM privacy_audit WHERE action='ARCHIVE_EXPORTED' AND target_order_id=?",Long.class,order.id())).isEqualTo(1);
    }
    @Test void legalHoldAndRetentionKeepFinancialDataAndPurgeOnlyEligibleArchives()throws Exception {
        var a=customer();var p=purchase(a.id());var order=place(p);orders.transition(root(),order.id(),Status.CANCELLED);
        privacy.anonymize(a.id());setArchiveExpired(order.id());
        var r=new Browser();r.login(admin.email());
        assertThat(r.request("PUT","/admin/privacy/orders/"+order.id()+"/hold",Map.of("held",true,"reasonCode","DISPUTE"),true).statusCode()).isEqualTo(200);
        assertThat(r.request("POST","/admin/privacy/retention",Map.of(),true).statusCode()).isEqualTo(200);assertThat(archiveCount(order.id())).isEqualTo(1);
        assertThat(r.request("PUT","/admin/privacy/orders/"+order.id()+"/hold",Map.of("held",false,"reasonCode","HOLD_RELEASED"),true).statusCode()).isEqualTo(200);
        privacy.retain();assertThat(archiveCount(order.id())).isZero();
        assertThat(orders.get(root(),order.id()).snapshot().totalTnd()).isEqualTo(order.snapshot().totalTnd());
        assertThatThrownBy(()->jdbc.update("UPDATE sales_order SET snapshot='{}'::jsonb WHERE id=?",order.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void catalogueAndOrderManagersCannotRunRetentionOrSetLegalHolds()throws Exception {
        for(Role role:List.of(Role.CATALOG_MANAGER,Role.ORDER_MANAGER)) {
            var a=identity.createInternal(admin.id(),fresh(),LOGIN,Set.of(role));var b=new Browser();b.login(a.email());
            assertThat(b.request("POST","/admin/privacy/retention",Map.of(),true).statusCode()).isEqualTo(403);
            assertThat(b.request("PUT","/admin/privacy/orders/"+UUID.randomUUID()+"/hold",Map.of("held",true,"reasonCode","DISPUTE"),true).statusCode()).isEqualTo(403);
            assertThat(b.get("/privacy/me").statusCode()).isEqualTo(200);
            assertThat(b.request("POST","/admin/privacy/orders/"+UUID.randomUUID()+"/archive-export",Map.of("password",LOGIN,"caseReference","CASE-TEST-ACCESS"),true).statusCode()).isEqualTo(403);
        }
        assertThatThrownBy(()->privacy.anonymize(admin.id())).hasMessage("LAST_ADMIN");assertThat(identity.findVisible(admin.id(),admin.id()).active()).isTrue();
    }
    @Test void transactionRollbackRestoresEveryModuleAndRacingCheckoutCannotResurrectAnAccount()throws Exception {
        var a=customer();var p=purchase(a.id());var order=place(p);orders.transition(root(),order.id(),Status.CANCELLED);
        assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->{privacy.anonymize(a.id());throw new IllegalStateException("ROLLBACK_TEST");})).hasMessage("ROLLBACK_TEST");
        assertThat(identity.findVisible(a.id(),a.id()).active()).isTrue();assertThat(archiveCount(order.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order_contact WHERE order_id=?",Long.class,order.id())).isEqualTo(1);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var remove=executor.submit(()->new TransactionTemplate(manager).execute(s->{privacy.anonymize(a.id());locked.countDown();try { if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("timeout"); }catch(InterruptedException e){throw new IllegalStateException(e);}return true;}));
            assertThat(locked.await(8,TimeUnit.SECONDS)).isTrue();
            var checkout=executor.submit(()->orders.place(p.actor(),UUID.randomUUID(),p.items(),p.address(),p.hash()));
            assertThat(checkout.isDone()).isFalse();release.countDown();assertThat(remove.get(8,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->checkout.get(8,TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order WHERE owner_scope=?",Long.class,p.actor().scope())).isEqualTo(1);
        }finally { release.countDown(); }
    }
    @Test void expiredVerificationTokensAreNotAcceptedAndRetentionPurgesExpiredData()throws Exception {
        var a=customer();privacy.requestEmail(a.id(),fresh());dispatcher.dispatchOne();var capture=org.mockito.ArgumentCaptor.forClass(Mail.class);verify(mail).send(capture.capture());String token=capture.getValue().text().split("#email=")[1];
        jdbc.update("UPDATE privacy_email_change SET expires_at=now()-interval '1 minute' WHERE account_id=?",a.id());
        assertThatThrownBy(()->privacy.confirmEmail(a.id(),token)).hasMessage("INVALID_EMAIL_TOKEN");
        jdbc.update("INSERT INTO customer_cart(customer_id,updated_at) VALUES (?,now()-interval '31 days')",a.id());
        privacy.retain();assertThat(jdbc.queryForObject("SELECT count(*) FROM privacy_email_change WHERE account_id=?",Long.class,a.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_cart WHERE customer_id=?",Long.class,a.id())).isZero();
    }
    @Test void reauthenticationIsThrottledAndScopeOverrideNeverGrantsAdministrativeAccess()throws Exception {
        var a=customer();var b=new Browser();b.login(a.email());
        for(int i=0;i<5;i++)assertThat(b.request("POST","/privacy/export",Map.of("password","wrong"),true).statusCode()).isEqualTo(401);
        assertThat(b.request("POST","/privacy/export",Map.of("password",LOGIN),true).statusCode()).isEqualTo(429);
        assertThat(b.request("POST","/admin/privacy/retention",Map.of("actorId",admin.id()),true).statusCode()).isEqualTo(403);
        assertThatThrownBy(()->privacy.retainAs(a.id())).isInstanceOf(SecurityException.class);
    }
    @Test void automaticRetentionArchivesOnlyFinishedContactsAndRemovesOldMessagesAndAudit()throws Exception {
        var a=customer();var finished=place(purchase(a.id()));var active=place(purchase(a.id()));
        orders.transition(root(),finished.id(),Status.CANCELLED);
        jdbc.update("UPDATE sales_order SET updated_at=now()-interval '91 days' WHERE id IN (?,?)",finished.id(),active.id());
        privacy.consent(a.id(),true,PrivacyService.NOTICE);
        jdbc.update("UPDATE privacy_audit SET created_at=now()-interval '366 days' WHERE subject_scope=?","C:"+a.id());
        jdbc.update("UPDATE notification_mail_outbox SET created_at=now()-interval '31 days' WHERE subject_scope=?","C:"+a.id());
        privacy.retain();
        assertThat(archiveCount(finished.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order_contact WHERE order_id=?",Long.class,finished.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_order_contact WHERE order_id=?",Long.class,active.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_mail_outbox WHERE subject_scope=?",Long.class,"C:"+a.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM privacy_audit WHERE subject_scope=?",Long.class,"C:"+a.id())).isZero();
        assertThat(privacy.view(a.id()).consent().marketing()).isTrue(); // choice remains independently stored
    }
    private long archiveCount(UUID id)throws Exception {
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var s=c.prepareStatement("SELECT count(*) FROM bricocomptoir.sales_order_private_archive WHERE order_id=?")) {
            s.setObject(1,id);try(var r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    @Test void guestSessionOwnsItsDataAndClosingItPreventsAnotherCheckout()throws Exception {
        var p=purchase(null);var own=new Browser();var outsider=new Browser();
        var preview=own.request("POST","/checkout/preview",Map.of("items",p.items(),"address",p.address(),"marketing",true),true);
        assertThat(preview.statusCode()).isEqualTo(200);
        String quote=json.readTree(preview.body()).get("quoteHash").asText();
        var result=own.place(Map.of("items",p.items(),"address",p.address(),"quoteHash",quote,"marketing",true));
        assertThat(result.statusCode()).isEqualTo(201);UUID id=UUID.fromString(json.readTree(result.body()).get("id").asText());
        String scope=jdbc.queryForObject("SELECT owner_scope FROM sales_order WHERE id=?",String.class,id);
        assertThat(own.request("POST","/privacy/guest/export",Map.of(),true).body()).contains(id.toString(),"Rue privée fictive");
        assertThat(outsider.request("POST","/privacy/guest/export",Map.of("ownerScope",scope),true).statusCode()).isEqualTo(400);
        assertThat(outsider.get("/orders/"+id).statusCode()).isEqualTo(404);
        var corrected=new Address("TEST corrigé","20987654","Rue corrigée","Tunis","1000","TUNIS","TN",null);
        assertThat(own.request("PATCH","/privacy/guest/orders/"+id+"/address",corrected,true).statusCode()).isEqualTo(200);
        assertThat(own.request("POST","/privacy/guest/anonymize",Map.of("confirm",true),true).statusCode()).isEqualTo(409);
        orders.transition(root(),id,Status.CANCELLED);
        assertThat(own.request("POST","/privacy/guest/anonymize",Map.of("confirm",true),true).statusCode()).isEqualTo(200);
        assertThatThrownBy(()->orders.place(new Actor(scope,null,false),UUID.randomUUID(),p.items(),p.address(),quote)).hasMessage("GUEST_SESSION_CLOSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM privacy_consent WHERE account_id IS NULL",Long.class)).isZero();
    }
    private void setArchiveExpired(UUID id)throws Exception {
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var s=c.prepareStatement("UPDATE bricocomptoir.sales_order_private_archive SET retain_until=now()-interval '1 minute' WHERE order_id=?")) {s.setObject(1,id);s.executeUpdate();}
    }
    private final class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build();
        URI uri(String path){return URI.create("http://localhost:"+port+"/api/v1"+path);}
        HttpResponse<String> get(String path)throws Exception {return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        HttpResponse<String> request(String method,String path,Object body,boolean csrf)throws Exception {
            var builder=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if(csrf){get("/auth/csrf");builder.header("X-XSRF-TOKEN",cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue());}
            return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
        void login(String email)throws Exception {assertThat(request("POST","/auth/login",Map.of("email",email,"password",LOGIN),true).statusCode()).isEqualTo(200);}
        HttpResponse<String> place(Object body)throws Exception {
            get("/auth/csrf");String token=cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
            return client.send(HttpRequest.newBuilder(uri("/orders")).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").header("X-XSRF-TOKEN",token).header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
