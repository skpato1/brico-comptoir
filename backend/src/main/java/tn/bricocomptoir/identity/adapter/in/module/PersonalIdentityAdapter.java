package tn.bricocomptoir.identity.adapter.in.module;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.bricocomptoir.identity.application.port.in.PersonalIdentity;
import tn.bricocomptoir.identity.application.port.out.*;
import tn.bricocomptoir.identity.domain.*;

@Service
@Transactional
public class PersonalIdentityAdapter implements PersonalIdentity {
    private final AccountStore accounts;
    private final tn.bricocomptoir.identity.application.service.IdentityService service;
    private final JdbcTemplate jdbc;
    public PersonalIdentityAdapter(AccountStore accounts,tn.bricocomptoir.identity.application.service.IdentityService service,JdbcTemplate jdbc) {
        this.accounts=accounts;this.service=service;this.jdbc=jdbc;
    }
    public Profile lock(UUID id) {
        // Same order as administrative role mutations; last administrator remains protected.
        accounts.lockAdministration();accounts.lockAccount(id);return profile(id);
    }
    public Profile profile(UUID id) {
        var a=account(id);
        return new Profile(a.id(),a.email(),a.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),a.active());
    }
    private Account account(UUID id) {
        return accounts.byId(id).filter(Account::active).orElseThrow(()->new IllegalStateException("ACCOUNT_UNAVAILABLE"));
    }
    public void rectifyEmail(UUID id,String value) {
        service.rectifyVerifiedEmail(id,value);
    }
    public void anonymize(UUID id) {
        service.anonymizeAccount(id);
    }
    public int purgeExpiredTokens() { return jdbc.update("DELETE FROM identity_password_reset WHERE expires_at<=now()"); }
}
