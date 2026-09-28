package tn.bricocomptoir.notifications;

import java.io.*;
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
import org.springframework.test.context.bean.override.mockito.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.identity.domain.Role;
import tn.bricocomptoir.catalog.adapter.transaction.CatalogTransactions;
import tn.bricocomptoir.inventory.adapter.transaction.InventoryTransactions;
import tn.bricocomptoir.sales.adapter.transaction.OrderTransactions;
import tn.bricocomptoir.sales.domain.CartModels.Kind;
import tn.bricocomptoir.sales.domain.OrderModels.*;
import tn.bricocomptoir.sales.domain.OrderModels.Order;
import tn.bricocomptoir.notifications.adapter.out.persistence.JdbcNotifications;
import tn.bricocomptoir.notifications.application.port.out.EmailProvider;
import tn.bricocomptoir.notifications.application.service.MailDispatcher;
import tn.bricocomptoir.notifications.domain.Mail;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers @ActiveProfiles("test")
@SpringBootTest(classes=BricoComptoirApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationsIT {
    private static final String USER="notification_test_app",PASSWORD=UUID.randomUUID().toString(),LOGIN="Correct-Horse-2026!";
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6-alpine")
        .withDatabaseName("notifications_test").withUsername("migrator").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void database(DynamicPropertyRegistry r)throws Exception {
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var s=c.createStatement()) {
            s.execute("CREATE ROLE "+USER+" LOGIN PASSWORD '"+PASSWORD+"'");
        }
        r.add("DB_URL",POSTGRES::getJdbcUrl);r.add("DB_USERNAME",()->USER);r.add("DB_PASSWORD",()->PASSWORD);
        r.add("DB_MIGRATION_USERNAME",POSTGRES::getUsername);r.add("DB_MIGRATION_PASSWORD",POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired IdentityTransactions identity;
    @Autowired CatalogTransactions catalog;
    @Autowired InventoryTransactions inventory;
    @Autowired OrderTransactions orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MailDispatcher dispatcher;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean EmailProvider provider;
    @MockitoSpyBean JdbcNotifications store;
    private static UUID admin;
    private static String adminEmail;
    @BeforeEach void setup() {
        if(admin==null) { adminEmail=UUID.randomUUID()+"@example.invalid";admin=identity.bootstrapAdmin(adminEmail,LOGIN).id(); }
        jdbc.update("UPDATE notification_mail_outbox SET state='CANCELLED',payload=NULL,lease_token=NULL,lease_until=NULL WHERE state<>'SENT'");
        reset(provider);
    }
    record Purchase(Actor actor,UUID key,List<Item> items,Address address,String quote,UUID sku) { }
    private Purchase purchase(UUID customer,String email) {
        String token=UUID.randomUUID().toString();
        var category=catalog.saveCategory(null,null,"mail-"+token,"Test email",true,null);
        var product=catalog.saveProduct(null,category.id(),null,"TEST email","",Map.of(),"PUBLISHED",null);
        var sku=catalog.saveVariant(null,product.id(),"MAIL-"+token.toUpperCase(),"Unité","pièce",Map.of(),"2.375","PUBLISHED",null);
        inventory.adjust(UUID.randomUUID(),sku.id(),10,"Test email","test");
        var actor=new Actor(customer==null?"G:"+token:"C:"+customer,customer,false);
        var address=new Address("TEST NE PAS LIVRER","20123456","Rue fictive","Tunis","1000","TUNIS","TN",email);
        var items=List.of(new Item(Kind.PRODUCT,sku.id(),1,sku.version(),catalog.product(product.id(),false).version()));
        return new Purchase(actor,UUID.randomUUID(),items,address,orders.preview(actor,items,address).quoteHash(),sku.id());
    }
    private Order place(Purchase p) { return orders.place(p.actor(),p.key(),p.items(),p.address(),p.quote()); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class); }
    private void due() { jdbc.update("UPDATE notification_mail_outbox SET next_attempt_at=now()-interval '1 second' WHERE state='PENDING'"); }

    @Test void snapshotsRecipientsAndEveryRelevantStatusAreQueuedOnlyOnce() {
        var customer=identity.register(UUID.randomUUID()+"@example.invalid",LOGIN);
        Purchase p=purchase(customer.id(),"attacker@example.invalid");
        Order order=place(p);assertThat(place(p).id()).isEqualTo(order.id());
        var manager=new Actor("C:"+admin,admin,true);
        for(var status:List.of(Status.PREPARING,Status.SHIPPED,Status.DELIVERED)) {
            orders.transition(manager,order.id(),status);orders.transition(manager,order.id(),status);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_mail_outbox WHERE dedup_key LIKE ?",Long.class,"ORDER:"+order.id()+":%")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_order_event WHERE order_id=?",Long.class,order.id())).isEqualTo(1);
        var bytes=jdbc.queryForObject("SELECT payload FROM notification_mail_outbox WHERE dedup_key=?",byte[].class,"ORDER:"+order.id()+":CONFIRMED");
        assertThat(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(customer.email(),"TEST email");
        for(int i=0;i<4;i++)assertThat(dispatcher.dispatchOne()).isTrue();
        assertThat(dispatcher.dispatchOne()).isFalse();
        var captured=org.mockito.ArgumentCaptor.forClass(Mail.class);verify(provider,times(4)).send(captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(m-> {assertThat(m.recipient()).isEqualTo(customer.email());assertThat(m.text()).contains("9.375 TND",order.id().toString());});
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_mail_outbox WHERE state='SENT' AND payload IS NOT NULL",Long.class)).isZero();
    }
    @Test void smtpOutageLeavesOrderCommittedAndRetryIsBoundedAndControlled()throws Exception {
        var p=purchase(null,"guest@example.invalid");var order=place(p);
        doThrow(new IllegalStateException("SMTP down secret")).when(provider).send(any());
        for(int i=0;i<5;i++) { due();assertThat(dispatcher.dispatchOne()).isTrue();assertThat(dispatcher.dispatchOne()).isFalse(); }
        assertThat(dispatcher.dispatchOne()).isFalse();
        assertThat(orders.get(p.actor(),order.id()).status()).isEqualTo(Status.CONFIRMED);
        assertThat(inventory.stock(p.sku()).reserved()).isEqualTo(1);
        assertThat(place(p).id()).isEqualTo(order.id());
        var id=jdbc.queryForObject("SELECT id FROM notification_mail_outbox WHERE dedup_key=?",UUID.class,"ORDER:"+order.id()+":CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT state FROM notification_mail_outbox WHERE id=?",String.class,id)).isEqualTo("FAILED");
        Browser root=new Browser();root.login(adminEmail);
        assertThat(root.get("/admin/mail-outbox?size=1").body()).doesNotContain("guest@example.invalid","SMTP down secret","Rue fictive");
        assertThat(root.post("/admin/mail-outbox/"+id+"/retry","{}",false).statusCode()).isEqualTo(403);
        assertThat(root.post("/admin/mail-outbox/"+id+"/retry","{}",true).statusCode()).isEqualTo(204);
        reset(provider);assertThat(dispatcher.dispatchOne()).isTrue();verify(provider).send(any());
        assertThat(root.post("/admin/mail-outbox/"+id+"/retry","{}",true).statusCode()).isEqualTo(409);
        orders.transition(new Actor("C:"+admin,admin,true),order.id(),Status.CANCELLED);
        assertThat(dispatcher.dispatchOne()).isTrue();
        assertThat(inventory.stock(p.sku()).reserved()).isZero();
    }
    @Test void concurrentWorkersAndAbandonedLeasesKeepAStableIdentity()throws Exception {
        var order=place(purchase(null,"guest@example.invalid"));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(i->{entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return null;}).when(provider).send(any());
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(dispatcher::dispatchOne);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(executor.submit(dispatcher::dispatchOne).get(5,TimeUnit.SECONDS)).isFalse();
            release.countDown();assertThat(first.get(5,TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); }
        verify(provider).send(any());reset(provider);
        orders.transition(new Actor("C:"+admin,admin,true),order.id(),Status.PREPARING);
        var abandoned=store.claim(Instant.now()).orElseThrow();
        assertThat(store.claim(Instant.now())).isEmpty();
        jdbc.update("UPDATE notification_mail_outbox SET lease_until=now()-interval '1 second' WHERE id=?",abandoned.id());
        assertThat(dispatcher.dispatchOne()).isTrue();
        var captured=org.mockito.ArgumentCaptor.forClass(Mail.class);verify(provider).send(captured.capture());
        assertThat(captured.getValue().id()).isEqualTo(abandoned.id());assertThat(captured.getValue().attempt()).isEqualTo(2);
        store.failed(abandoned,Instant.now());
        assertThat(jdbc.queryForObject("SELECT state FROM notification_mail_outbox WHERE id=?",String.class,abandoned.id())).isEqualTo("SENT");
    }
    @Test void rollbackAlsoRemovesQueuedEmailEventAndReservation() {
        var p=purchase(null,"guest@example.invalid");long before=count("notification_mail_outbox"),events=count("notification_order_event");
        doAnswer(i->{i.callRealMethod();throw new IllegalStateException("Injected after notification insert");}).when(store).created(any());
        try { assertThatThrownBy(()->place(p)).hasMessageContaining("Injected"); }
        finally { reset(store); }
        assertThat(count("notification_mail_outbox")).isEqualTo(before);assertThat(count("notification_order_event")).isEqualTo(events);
        assertThat(inventory.stock(p.sku()).reserved()).isZero();
        assertThat(place(p).status()).isEqualTo(Status.CONFIRMED);
    }
    @Test void resetMessagesAreGenericEncryptedCancelledAndExpire() {
        var customer=identity.register(UUID.randomUUID()+"@example.invalid",LOGIN);
        long before=count("notification_mail_outbox");identity.requestReset("unknown@example.invalid");assertThat(count("notification_mail_outbox")).isEqualTo(before);
        identity.requestReset(customer.email());identity.requestReset(customer.email());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_mail_outbox WHERE state='PENDING'",Long.class)).isEqualTo(1);
        assertThat(dispatcher.dispatchOne()).isTrue();assertThat(dispatcher.dispatchOne()).isFalse();
        var captured=org.mockito.ArgumentCaptor.forClass(Mail.class);verify(provider).send(captured.capture());
        String token=captured.getValue().text().split("#reset=")[1].split("\\s")[0];
        identity.completeReset(token,"Replacement-2026!");
        assertThatThrownBy(()->identity.completeReset(token,"Replacement-2026!")).isInstanceOf(IllegalArgumentException.class);
        reset(provider);identity.requestReset(customer.email());
        jdbc.update("UPDATE notification_mail_outbox SET expires_at=now()-interval '1 second' WHERE state='PENDING'");
        assertThat(dispatcher.dispatchOne()).isFalse();verifyNoInteractions(provider);
    }
    @Test void concurrentResetRequestsLeaveOnlyOneUsableQueuedLink()throws Exception {
        var customer=identity.register(UUID.randomUUID()+"@example.invalid",LOGIN);
        var start=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->{start.await();identity.requestReset(customer.email());return true;});
            var second=executor.submit(()->{start.await();identity.requestReset(customer.email());return true;});
            start.countDown();assertThat(first.get(5,TimeUnit.SECONDS)).isTrue();assertThat(second.get(5,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_password_reset WHERE account_id=?",Long.class,customer.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_mail_outbox WHERE state='PENDING'",Long.class)).isEqualTo(1);
        assertThat(dispatcher.dispatchOne()).isTrue();assertThat(dispatcher.dispatchOne()).isFalse();verify(provider).send(any());
    }
    @Test void eventCursorCannotSkipATransactionThatCommitsLater()throws Exception {
        long before=store.latest();UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var secondStarted=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=executor.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(s->{
                store.created(first);locked.countDown();try {if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Timeout");}
                catch(InterruptedException e){throw new IllegalStateException(e);}return true;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var b=executor.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(s->{secondStarted.countDown();store.created(second);return true;}));
            assertThat(secondStarted.await(5,TimeUnit.SECONDS)).isTrue();assertThat(b.isDone()).isFalse();assertThat(store.latest()).isEqualTo(before);
            release.countDown();assertThat(a.get(5,TimeUnit.SECONDS)).isTrue();assertThat(b.get(5,TimeUnit.SECONDS)).isTrue();
        } finally {release.countDown();}
        assertThat(store.events(before,100)).extracting(e->e.orderId()).containsExactly(first,second);
    }
    @Test void eventHistoryStreamReconnectAndRoleRevocationAreProtected()throws Exception {
        Browser root=new Browser();root.login(adminEmail);
        var manager=identity.createInternal(admin,UUID.randomUUID()+"@example.invalid",LOGIN,Set.of(Role.ORDER_MANAGER));
        Browser om=new Browser();om.login(manager.email());
        var cm=identity.createInternal(admin,UUID.randomUUID()+"@example.invalid",LOGIN,Set.of(Role.CATALOG_MANAGER));
        Browser catalogue=new Browser();catalogue.login(cm.email());
        var c=identity.register(UUID.randomUUID()+"@example.invalid",LOGIN);Browser customer=new Browser();customer.login(c.email());
        for(Browser denied:List.of(catalogue,customer,new Browser())) {
            int status=denied==catalogue||denied==customer?403:401;
            assertThat(denied.get("/admin/order-events").statusCode()).isEqualTo(status);
            assertThat(denied.get("/admin/order-events/stream").statusCode()).isEqualTo(status);
            assertThat(denied.get("/admin/mail-outbox").statusCode()).isEqualTo(status);
            assertThat(denied.post("/admin/mail-outbox/"+UUID.randomUUID()+"/retry","{}",true).statusCode()).isEqualTo(status);
        }
        assertThat(om.get("/admin/mail-outbox").statusCode()).isEqualTo(403);
        assertThat(om.get("/admin/order-events?after=-1").statusCode()).isEqualTo(400);
        long cursor=store.latest();Order order=place(purchase(null,null));
        var replay=om.get("/admin/order-events?after="+cursor+"&size=1");assertThat(replay.body()).contains(order.id().toString()).doesNotContain("Rue fictive","20123456");
        var response=om.stream(cursor,false);assertThat(response.statusCode()).isEqualTo(200);
        try(var reader=new BufferedReader(new InputStreamReader(response.body()))) {
            assertThat(frame(reader)).contains("event:ready");assertThat(frame(reader)).contains("event:order-created",order.id().toString());
        }
        // An order committed while disconnected is recovered with Last-Event-ID.
        long secondCursor=store.latest();Order second=place(purchase(null,null));
        var resumed=om.stream(secondCursor,true);
        try(var reader=new BufferedReader(new InputStreamReader(resumed.body()))) {
            assertThat(frame(reader)).contains("event:ready");assertThat(frame(reader)).contains(second.id().toString());
            identity.changeRoles(admin,manager.id(),Set.of(Role.CATALOG_MANAGER));
            try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
                assertThat(executor.submit(()->{String line;StringBuilder remaining=new StringBuilder();while((line=reader.readLine())!=null)remaining.append(line);return remaining.toString();}).get(8,TimeUnit.SECONDS)).doesNotContain("order-created");
            }
        }
        assertThat(om.get("/admin/order-events").statusCode()).isEqualTo(401);
    }
    private String frame(BufferedReader reader)throws Exception {
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            return executor.submit(()->{StringBuilder result=new StringBuilder();String line;while((line=reader.readLine())!=null){if(line.isEmpty()&&result.length()>0)return result.toString();result.append(line).append('\n');}return result.toString();}).get(8,TimeUnit.SECONDS);
        }
    }
    private final class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build();
        URI uri(String path){return URI.create("http://localhost:"+port+"/api/v1"+path);}
        HttpResponse<String> get(String path)throws Exception {return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        HttpResponse<String> post(String path,String body,boolean csrf)throws Exception {
            var builder=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            if(csrf){get("/auth/csrf");builder.header("X-XSRF-TOKEN",cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue());}
            return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
        void login(String email)throws Exception {assertThat(post("/auth/login",json.writeValueAsString(Map.of("email",email,"password",LOGIN)),true).statusCode()).isEqualTo(200);}
        HttpResponse<InputStream> stream(long cursor,boolean header)throws Exception {
            var builder=HttpRequest.newBuilder(uri("/admin/order-events/stream"+(header?"":"?after="+cursor))).header("Accept","text/event-stream").GET();
            if(header)builder.header("Last-Event-ID",Long.toString(cursor));
            return client.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
        }
    }
}
