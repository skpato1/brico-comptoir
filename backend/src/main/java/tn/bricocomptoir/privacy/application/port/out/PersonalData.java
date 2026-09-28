package tn.bricocomptoir.privacy.application.port.out;
import java.time.Instant;
import java.util.*;
public interface PersonalData {
    record Profile(UUID id,String email,Set<String> roles,boolean active) { }
    record Sales(String ordersJson,String cartJson,boolean hasMore) { }
    Profile profile(UUID id);
    Profile lock(UUID id);
    void email(UUID id,String email);
    void identityRemove(UUID id);
    Sales export(String scope,int page);
    void salesRemove(String scope,int legalDays);
    void rectify(String scope,UUID orderId,String addressJson);
    void hold(UUID orderId,boolean held);
    String archive(UUID orderId);
    void cancelMail(String scope);
    void verification(UUID id,String email,String token,Instant expiry);
    void cancelVerification(UUID id);
    int retain(Instant now,int contactDays,int cartDays,int mailDays,int legalDays);
    int purgeTokens();
}
