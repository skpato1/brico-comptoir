package tn.bricocomptoir.privacy.adapter.out.module;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import tn.bricocomptoir.privacy.application.port.out.PersonalData;
import tn.bricocomptoir.identity.application.port.in.PersonalIdentity;
import tn.bricocomptoir.sales.application.port.in.PersonalSales;
import tn.bricocomptoir.notifications.application.port.in.*;
@Component
public class ModulePersonalData implements PersonalData {
    private final PersonalIdentity identity;
    private final PersonalSales sales;
    private final PersonalNotifications mail;
    private final NotificationOperations queue;
    private final String url;
    public ModulePersonalData(PersonalIdentity identity,PersonalSales sales,PersonalNotifications mail,NotificationOperations queue,@Value("${brico.public-url}") String url) {
        this.identity=identity;this.sales=sales;this.mail=mail;this.queue=queue;this.url=url;
    }
    private Profile map(PersonalIdentity.Profile a) { return new Profile(a.id(),a.email(),a.roles(),a.active()); }
    public Profile profile(UUID id) { return map(identity.profile(id)); }
    public Profile lock(UUID id) { return map(identity.lock(id)); }
    public void email(UUID id,String value) { identity.rectifyEmail(id,value); }
    public void identityRemove(UUID id) { identity.anonymize(id); }
    public Sales export(String scope,int page) { var d=sales.export(scope,page);return new Sales(d.ordersJson(),d.cartJson(),d.hasMore()); }
    public void salesRemove(String scope,int days) { sales.anonymize(scope,days); }
    public void rectify(String scope,UUID orderId,String address) { sales.rectify(scope,orderId,address); }
    public void hold(UUID id,boolean held) { sales.hold(id,held); }
    public String archive(UUID id) { return sales.archive(id); }
    public void cancelMail(String scope) { mail.cancelOrders(scope); }
    public void cancelVerification(UUID id) { mail.cancelEmailChange(id); }
    public void verification(UUID id,String email,String token,Instant expiresAt) {
        String key="EMAIL_CHANGE:"+id+":"+UUID.randomUUID();
        queue.enqueue(new NotificationOperations.Email(key,email,"BricoComptoir — vérification de votre adresse",
            "Pour confirmer votre nouvelle adresse, connectez-vous à votre compte puis ouvrez ce lien (30 minutes) :\n"+url+"/mes-donnees#email="+token,null,expiresAt));
        queue.associate(key,"C:"+id);
    }
    public int retain(Instant now,int contact,int cart,int mailDays,int legal) {
        return sales.retain(now,contact,cart,legal)+mail.retain(now,mailDays)+identity.purgeExpiredTokens();
    }
    public int purgeTokens() { return identity.purgeExpiredTokens(); }
}
