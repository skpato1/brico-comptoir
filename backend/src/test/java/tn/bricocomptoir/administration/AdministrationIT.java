package tn.bricocomptoir.administration;

import java.net.*;
import java.net.http.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tn.bricocomptoir.bootstrap.BricoComptoirApplication;
import tn.bricocomptoir.catalog.adapter.transaction.CatalogTransactions;
import tn.bricocomptoir.packs.adapter.transaction.PackTransactions;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.identity.domain.Role;
import tn.bricocomptoir.inventory.adapter.transaction.InventoryTransactions;
import tn.bricocomptoir.sales.adapter.transaction.OrderTransactions;
import tn.bricocomptoir.sales.domain.*;
import tn.bricocomptoir.sales.domain.OrderModels.*;
import tn.bricocomptoir.sales.domain.CartModels.Kind;
import tn.bricocomptoir.content.domain.HomeContent;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes=BricoComptoirApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdministrationIT {
    private static final String USER="administration_test_app", PASSWORD=UUID.randomUUID().toString();
    private static final String LOGIN_PASSWORD="Correct-Horse-2026!";
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.6-alpine")
        .withDatabaseName("administration_test").withUsername("migrator").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) throws Exception {
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
            var s=c.createStatement()){s.execute("CREATE ROLE "+USER+" LOGIN PASSWORD '"+PASSWORD+"'");}
        r.add("DB_URL",POSTGRES::getJdbcUrl);r.add("DB_USERNAME",()->USER);r.add("DB_PASSWORD",()->PASSWORD);
        r.add("DB_MIGRATION_USERNAME",POSTGRES::getUsername);r.add("DB_MIGRATION_PASSWORD",POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired IdentityTransactions identity;
    @Autowired CatalogTransactions catalog;
    @Autowired PackTransactions packs;
    @Autowired InventoryTransactions inventory;
    @Autowired OrderTransactions orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender mail;
    private static UUID adminId;
    private static String adminEmail;
    @BeforeEach void administrator(){
        if(adminId==null){adminEmail=UUID.randomUUID()+"@example.invalid";adminId=identity.bootstrapAdmin(adminEmail,LOGIN_PASSWORD).id();}
    }
    private Browser browser(Role role)throws Exception{
        String email=adminEmail;
        if(role==Role.CUSTOMER)email=identity.register(UUID.randomUUID()+"@example.invalid",LOGIN_PASSWORD).email();
        else if(role!=Role.ADMIN)email=identity.createInternal(adminId,UUID.randomUUID()+"@example.invalid",LOGIN_PASSWORD,Set.of(role)).email();
        Browser browser=new Browser();browser.login(email);return browser;
    }
    @Test void everyAdministrativeScopeIsEnforcedWithSessionAndCsrf()throws Exception{
        Browser root=browser(Role.ADMIN), cm=browser(Role.CATALOG_MANAGER),om=browser(Role.ORDER_MANAGER),customer=browser(Role.CUSTOMER),guest=new Browser();
        List<String> catalogue=List.of("/admin/catalog/categories/page","/admin/catalog/brands/page","/admin/catalog/products","/admin/packs/page","/admin/content/home");
        for(String path:catalogue){
            assertThat(root.get(path).statusCode()).as(path).isEqualTo(200);
            assertThat(cm.get(path).statusCode()).as(path).isEqualTo(200);
            assertThat(om.get(path).statusCode()).as(path).isEqualTo(403);
            assertThat(customer.get(path).statusCode()).as(path).isEqualTo(403);
            assertThat(guest.get(path).statusCode()).as(path).isEqualTo(401);
        }
        assertThat(om.get("/admin/orders?page=0&size=1").statusCode()).isEqualTo(200);
        assertThat(cm.get("/admin/orders").statusCode()).isEqualTo(403);
        assertThat(customer.get("/admin/orders").statusCode()).isEqualTo(403);
        assertThat(root.get("/admin/delivery").statusCode()).isEqualTo(200);
        for(Browser denied:List.of(cm,om,customer)){
            assertThat(denied.get("/admin/delivery").statusCode()).isEqualTo(403);
            assertThat(denied.request("PUT","/admin/delivery","{}",true).statusCode()).isEqualTo(403);
            assertThat(denied.request("POST","/admin/stock/adjustments","{}",true).statusCode()).isEqualTo(403);
            assertThat(denied.get("/admin/stock/"+UUID.randomUUID()).statusCode()).isEqualTo(403);
        }
        for(Browser denied:List.of(om,customer)){
            for(String path:List.of("/admin/catalog/categories","/admin/catalog/brands","/admin/catalog/products","/admin/packs"))
                assertThat(denied.request("POST",path,"{}",true).statusCode()).as(path).isEqualTo(403);
            assertThat(denied.request("POST","/admin/catalog/products/"+UUID.randomUUID()+"/images","{}",true).statusCode()).isEqualTo(403);
            assertThat(denied.request("PUT","/admin/content/home","{}",true).statusCode()).isEqualTo(403);
        }
        assertThat(cm.request("POST","/admin/orders/"+UUID.randomUUID()+"/prepare","{}",true).statusCode()).isEqualTo(403);
        assertThat(root.request("PUT","/admin/delivery",root.get("/admin/delivery").body(),false).statusCode()).isEqualTo(403);
        assertThat(cm.request("PUT","/admin/content/home",cm.get("/admin/content/home").body(),false).statusCode()).isEqualTo(403);
        HomeContent before=json.readValue(cm.get("/admin/content/home").body(),HomeContent.class);
        HomeContent changed=new HomeContent("Titre de test",before.accent(),before.description(),before.solutionTitle(),before.solutionDescription(),before.productTitle(),before.productDescription(),before.version());
        assertThat(cm.request("PUT","/admin/content/home",json.writeValueAsString(changed),true).statusCode()).isEqualTo(200);
        assertThat(guest.get("/content/home").body()).contains("Titre de test");
        assertThat(cm.request("PUT","/admin/content/home",json.writeValueAsString(changed),true).statusCode()).isEqualTo(409);
        assertThat(cm.request("PUT","/admin/content/home",json.writeValueAsString(changed).replace("Titre de test","<script>"),true).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM content_home WHERE id=1",String.class)).isNotBlank();
    }
    @Test void administrativePagesAreBoundedStableAndSearchable()throws Exception{
        Browser cm=browser(Role.CATALOG_MANAGER);
        String token="page-"+UUID.randomUUID();
        for(int i=0;i<3;i++){
            catalog.saveCategory(null,null,token+"-"+i,token+" "+i,true,null);
            catalog.saveBrand(null,token+"-"+i,token+" "+i,true,null);
            packs.savePack(null,token+"-"+i,token+" "+i,"","","DRAFT",null);
        }
        for(String path:List.of("/admin/catalog/categories/page","/admin/catalog/brands/page","/admin/packs/page")){
            var a=json.readTree(cm.get(path+"?q="+token+"&page=0&size=2").body());
            var b=json.readTree(cm.get(path+"?q="+token+"&page=1&size=2").body());
            assertThat(a.path("totalElements").asLong()).isEqualTo(3);
            assertThat(a.path("items").size()).isEqualTo(2);assertThat(b.path("items").size()).isEqualTo(1);
            assertThat(a.path("items").get(0).path("id").asString()).isNotEqualTo(b.path("items").get(0).path("id").asString());
            assertThat(cm.get(path+"?page=-1").statusCode()).isEqualTo(400);
            assertThat(cm.get(path+"?size=101").statusCode()).isEqualTo(400);
        }
    }
    @Test void deliveryUpdatesRecalculateQuotesAndNeverRewriteOrders()throws Exception{
        Browser root=browser(Role.ADMIN),om=browser(Role.ORDER_MANAGER),cm=browser(Role.CATALOG_MANAGER);
        DeliverySettings initial=json.readValue(root.get("/admin/delivery").body(),DeliverySettings.class);
        var first=new DeliverySettings(true,"7.125",Set.of("TUNIS"),initial.version());
        var saved=root.request("PUT","/admin/delivery",json.writeValueAsString(first),true);
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(root.request("PUT","/admin/delivery",json.writeValueAsString(first),true).statusCode()).isEqualTo(409);
        DeliverySettings current=json.readValue(saved.body(),DeliverySettings.class);
        assertThat(root.request("PUT","/admin/delivery",json.writeValueAsString(new DeliverySettings(true,"1.0001",Set.of("TUNIS"),current.version())),true).statusCode()).isEqualTo(400);
        assertThat(root.request("PUT","/admin/delivery",json.writeValueAsString(new DeliverySettings(true,"1.000",Set.of("UNKNOWN"),current.version())),true).statusCode()).isEqualTo(400);
        String token=UUID.randomUUID().toString();
        var category=catalog.saveCategory(null,null,"fee-"+token,"Test livraison",true,null);
        var product=catalog.saveProduct(null,category.id(),null,"Test livraison","",Map.of(),"PUBLISHED",null);
        var sku=catalog.saveVariant(null,product.id(),"FEE-"+token.toUpperCase(),"Unité","pièce",Map.of(),"2.375","PUBLISHED",null);
        inventory.adjust(UUID.randomUUID(),sku.id(),5,"Stock test","test");
        var actor=new Actor("G:"+UUID.randomUUID(),null,false);
        var address=new Address("Test","20123456","Adresse test","Tunis","1000","TUNIS","TN");
        var items=List.of(new Item(Kind.PRODUCT,sku.id(),1,sku.version(),catalog.product(product.id(),true).version()));
        var oldQuote=orders.preview(actor,items,address);
        assertThat(oldQuote.deliveryTnd()).isEqualByComparingTo("7.125");
        current=json.readValue(root.request("PUT","/admin/delivery",json.writeValueAsString(new DeliverySettings(true,"9.500",Set.of("TUNIS"),current.version())),true).body(),DeliverySettings.class);
        assertThatThrownBy(()->orders.place(actor,UUID.randomUUID(),items,address,oldQuote.quoteHash())).isInstanceOf(CheckoutFailure.class).hasMessage("OFFER_CHANGED");
        var quote=orders.preview(actor,items,address);
        var order=orders.place(actor,UUID.randomUUID(),items,address,quote.quoteHash());
        assertThat(cm.request("POST","/admin/orders/"+order.id()+"/prepare","{}",true).statusCode()).isEqualTo(403);
        assertThat(om.request("POST","/admin/orders/"+order.id()+"/ship","{}",true).statusCode()).isEqualTo(409);
        assertThat(om.request("POST","/admin/orders/"+order.id()+"/prepare","{}",true).statusCode()).isEqualTo(200);
        assertThat(om.request("POST","/admin/orders/"+order.id()+"/cancel","{}",true).statusCode()).isEqualTo(200);
        assertThat(inventory.stock(sku.id()).reserved()).isZero();
        assertThat(root.request("PUT","/admin/delivery",json.writeValueAsString(new DeliverySettings(false,"0.000",Set.of(),current.version())),true).statusCode()).isEqualTo(200);
        assertThatThrownBy(()->orders.preview(actor,items,address)).isInstanceOf(CheckoutFailure.class).hasMessage("CHECKOUT_UNAVAILABLE");
        assertThat(orders.get(actor,order.id()).snapshot().deliveryTnd()).isEqualByComparingTo("9.500");
    }
    private final class Browser{
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path)throws Exception{return request("GET",path,null,false);}
        void login(String email)throws Exception{
            get("/auth/csrf");
            assertThat(request("POST","/auth/login",json.writeValueAsString(Map.of("email",email,"password",LOGIN_PASSWORD)),true).statusCode()).isEqualTo(200);
            get("/auth/csrf");
        }
        HttpResponse<String> request(String method,String path,String body,boolean csrf)throws Exception{
            var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path));
            if(body!=null)b.header("Content-Type","application/json");
            if(csrf)cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("XSRF-TOKEN")).findFirst().ifPresent(c->b.header("X-XSRF-TOKEN",c.getValue()));
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
