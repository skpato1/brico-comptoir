package tn.bricocomptoir.sales.adapter.in.module;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tn.bricocomptoir.sales.application.port.in.PersonalSales;
import tn.bricocomptoir.sales.adapter.out.persistence.*;
import tn.bricocomptoir.sales.domain.*;
import tn.bricocomptoir.sales.domain.OrderModels.*;

@Service @Transactional
public class PersonalSalesAdapter implements PersonalSales {
    private final JdbcTemplate jdbc;
    private final JdbcOrderStore orders;
    private final ObjectMapper json;
    private final ArchiveCipher cipher;
    public PersonalSalesAdapter(JdbcTemplate jdbc,JdbcOrderStore orders,ObjectMapper json,ArchiveCipher cipher) {
        this.jdbc=jdbc;this.orders=orders;this.json=json;this.cipher=cipher;
    }
    public Data export(String scope,int page) {
        if(page<0 || page>100000)throw new IllegalArgumentException("INVALID_PAGE");
        var list=orders.list(scope,null,page*100,101);
        List<Map<String,Object>> exported=list.stream().limit(100).map(o->Map.<String,Object>of(
            "id",o.id(),"status",o.status(),"createdAt",o.createdAt(),"snapshot",o.snapshot())).toList();
        List<Map<String,Object>> cart=scope.startsWith("C:")?jdbc.query("SELECT kind,offer_id,quantity FROM customer_cart_line WHERE customer_id=? ORDER BY kind,offer_id",
            (r,n)->Map.<String,Object>of("kind",r.getString(1),"offerId",r.getObject(2,UUID.class),"quantity",r.getLong(3)),UUID.fromString(scope.substring(2))):List.of();
        return new Data(json.writeValueAsString(exported),json.writeValueAsString(cart),list.size()>100);
    }
    public void anonymize(String scope,int legalDays) {
        if(scope.startsWith("G:")) jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,92718))",Object.class,scope);
        var ids=jdbc.query("SELECT id,status FROM sales_order WHERE owner_scope=? ORDER BY id FOR UPDATE",
            (r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),scope);
        SalesPrivacyRules.requireFinished(ids.stream().map(e->Status.valueOf(e.getValue())).toList());
        for(var entry:ids) archive(entry.getKey(),legalDays);
        if(scope.startsWith("C:")) jdbc.update("DELETE FROM customer_cart WHERE customer_id=?",UUID.fromString(scope.substring(2)));
        else jdbc.update("INSERT INTO sales_privacy_closed_scope(owner_scope) VALUES (?) ON CONFLICT DO NOTHING",scope);
    }
    private void archive(UUID id,int legalDays) {
        var contacts=jdbc.query("SELECT c.address,o.created_at FROM sales_order_contact c JOIN sales_order o ON o.id=c.order_id WHERE c.order_id=? FOR UPDATE OF c",
            (r,n)->Map.entry(r.getString(1),r.getTimestamp(2).toInstant()),id);
        if(contacts.isEmpty())return;
        var contact=contacts.getFirst();
        jdbc.update("INSERT INTO sales_order_private_archive(order_id,ciphertext,retain_until) VALUES (?,?,?) ON CONFLICT DO NOTHING",
            id,cipher.encrypt(id.toString(),contact.getKey()),java.sql.Timestamp.from(SalesPrivacyRules.retainUntil(contact.getValue(),legalDays)));
        jdbc.update("DELETE FROM sales_order_contact WHERE order_id=?",id);
    }
    public void rectify(String scope,UUID id,String addressJson) {
        var order=orders.find(id,true).orElseThrow(()->new IllegalArgumentException("ORDER_NOT_FOUND"));
        var input=json.readValue(addressJson,Address.class);
        var valid=SalesPrivacyRules.rectify(scope,order,input);
        jdbc.update("UPDATE sales_order_contact SET address=?::jsonb,updated_at=now() WHERE order_id=?",json.writeValueAsString(valid),id);
    }
    public void hold(UUID id,boolean held) {
        if(jdbc.update("UPDATE sales_order SET legal_hold=? WHERE id=?",held,id)!=1)throw new IllegalArgumentException("ORDER_NOT_FOUND");
    }
    public String archive(UUID id) {
        byte[] ciphertext=jdbc.queryForObject("SELECT privacy_read_archive(?)",byte[].class,id);
        if(ciphertext==null)throw new IllegalArgumentException("ARCHIVE_NOT_FOUND");
        return cipher.decrypt(id.toString(),ciphertext);
    }
    public int retain(Instant now,int contactDays,int cartDays,int legalDays) {
        var ids=jdbc.query("SELECT o.id FROM sales_order o JOIN sales_order_contact c ON c.order_id=o.id WHERE o.status IN ('CANCELLED','DELIVERED') AND o.updated_at<? ORDER BY o.id LIMIT 100 FOR UPDATE OF o SKIP LOCKED",
            (r,n)->r.getObject(1,UUID.class),java.sql.Timestamp.from(now.minus(Duration.ofDays(contactDays))));
        for(var id:ids)archive(id,legalDays);
        jdbc.update("DELETE FROM customer_cart WHERE updated_at<?",java.sql.Timestamp.from(now.minus(Duration.ofDays(cartDays))));
        return ids.size()+jdbc.queryForObject("SELECT privacy_purge_archives(?)",Integer.class,java.sql.Timestamp.from(now));
    }
}
