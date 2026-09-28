package tn.bricocomptoir.identity.application.port.in;
import java.util.Set;
import java.util.UUID;

public interface PersonalIdentity {
    record Profile(UUID id,String email,Set<String> roles,boolean active) { }
    Profile lock(UUID id);
    Profile profile(UUID id);
    void rectifyEmail(UUID id,String verifiedEmail);
    void anonymize(UUID id);
    int purgeExpiredTokens();
}
