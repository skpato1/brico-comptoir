package tn.bricocomptoir.identity.application.port.out;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tn.bricocomptoir.identity.domain.Account;
import tn.bricocomptoir.identity.domain.Role;

public interface AccountStore {
    Optional<Account> byEmail(String email);
    Optional<Account> byId(UUID id);
    void insert(Account account);
    void update(Account account);
    void lockAdministration();
    void lockAccount(UUID id);
    long activeAdminCount();
    void audit(UUID actor, UUID target, String action);
}
