package tn.bricocomptoir.privacy.application.service;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import tn.bricocomptoir.privacy.application.port.out.*;
import tn.bricocomptoir.privacy.domain.PrivacyPolicy;

public final class PrivacyService {
    public static final String NOTICE="2026-09-28";
    private final PersonalData data;
    private final PrivacyStore store;
    private final PrivacyPolicy policy;
    private final Clock clock;
    public PrivacyService(PersonalData data,PrivacyStore store,PrivacyPolicy policy,Clock clock) {
        this.data=data;this.store=store;this.policy=policy;this.clock=clock;
    }
    public record View(PersonalData.Profile account,PrivacyStore.Consent consent) { }
    public record Export(View profile,PersonalData.Sales sales,int page) { }
    public String history(UUID id,int page) { PrivacyPolicy.page(page);data.profile(id);return store.history(id,page); }
    public String archive(UUID actor,UUID orderId,String caseReference) {
        admin(actor);
        if(caseReference==null || !caseReference.matches("CASE-[A-Z0-9-]{3,32}"))throw new IllegalArgumentException("CASE_REFERENCE_REQUIRED");
        String result=data.archive(orderId);store.archiveAudit(actor,orderId,caseReference);return result;
    }
    public View view(UUID id) { return new View(data.profile(id),store.consent(id)); }
    public View consent(UUID id,boolean allowed,String notice) {
        data.lock(id);
        if(allowed && !NOTICE.equals(notice))throw new IllegalArgumentException("NOTICE_CHANGED");
        store.consent(id,allowed,NOTICE);store.audit(scope(id),id,allowed?"MARKETING_GRANTED":"MARKETING_WITHDRAWN",NOTICE);
        return view(id);
    }
    public Export export(UUID id,int page) {
        PrivacyPolicy.page(page);var view=view(id);
        store.audit(scope(id),id,"DATA_EXPORTED",null);
        return new Export(view,data.export(scope(id),page),page);
    }
    public PersonalData.Sales guestExport(String scope,int page) {
        guest(scope);PrivacyPolicy.page(page);store.audit(scope,null,"DATA_EXPORTED",null);return data.export(scope,page);
    }
    public void requestEmail(UUID id,String email) {
        data.lock(id);
        if(email==null || email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("INVALID_EMAIL");
        String normalized=email.strip().toLowerCase(Locale.ROOT);
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expiry=clock.instant().plus(Duration.ofMinutes(30));
        data.cancelVerification(id);store.pending(id,normalized,hash(token),expiry);
        data.verification(id,normalized,token,expiry);store.audit(scope(id),id,"EMAIL_CHANGE_REQUESTED",null);
    }
    public void confirmEmail(UUID id,String token) {
        data.lock(id);
        String email=store.consume(id,hash(token),clock.instant()).orElseThrow(()->new IllegalArgumentException("INVALID_EMAIL_TOKEN"));
        data.email(id,email);data.cancelVerification(id);store.audit(scope(id),id,"EMAIL_RECTIFIED",null);
    }
    public void rectify(UUID id,UUID orderId,String address) {
        data.lock(id);data.rectify(scope(id),orderId,address);store.audit(scope(id),id,"DELIVERY_RECTIFIED",null);
    }
    public void guestRectify(String scope,UUID id,String address) {
        guest(scope);data.rectify(scope,id,address);store.audit(scope,null,"DELIVERY_RECTIFIED",null);
    }
    public void anonymize(UUID id) {
        data.lock(id);data.salesRemove(scope(id),policy.legalDays());data.cancelMail(scope(id));
        data.cancelVerification(id);store.removePending(id);store.consent(id,false,NOTICE);data.identityRemove(id);
        store.audit(scope(id),id,"PERSONAL_DATA_REMOVED",null);
    }
    public void guestAnonymize(String scope) {
        guest(scope);data.salesRemove(scope,policy.legalDays());data.cancelMail(scope);store.audit(scope,null,"PERSONAL_DATA_REMOVED",null);
    }
    public void hold(UUID actor,UUID orderId,boolean held,String reason) {
        admin(actor);
        data.hold(orderId,held);store.holdAudit(actor,orderId,held,reason);
    }
    public int purgeTokens() { return data.purgeTokens()+store.purgeTokens(clock.instant()); }
    public int retainAs(UUID actor) { admin(actor);return retain(); }
    private void admin(UUID id) {
        if(!data.lock(id).roles().contains("ADMIN"))throw new SecurityException("FORBIDDEN");
    }
    public int retain() {
        if(!policy.retentionEnabled())throw new IllegalStateException("RETENTION_DISABLED");
        var now=clock.instant();
        return data.retain(now,policy.contactDays(),policy.cartDays(),policy.mailDays(),policy.legalDays())+store.retain(now,policy.auditDays());
    }
    private static String scope(UUID id) { return "C:"+id; }
    private static void guest(String scope) {
        if(scope==null || !scope.matches("G:[0-9a-f-]{36}"))throw new IllegalArgumentException("GUEST_SESSION_REQUIRED");
    }
    public static String hash(String token) {
        if(token==null || token.length()>128)throw new IllegalArgumentException("INVALID_EMAIL_TOKEN");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
