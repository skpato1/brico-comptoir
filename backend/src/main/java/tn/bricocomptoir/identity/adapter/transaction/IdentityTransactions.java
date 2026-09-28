package tn.bricocomptoir.identity.adapter.transaction;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.identity.application.service.IdentityService;
import tn.bricocomptoir.identity.domain.Account;
import tn.bricocomptoir.identity.domain.Role;

@Service
public class IdentityTransactions {
    private final IdentityService service;

    public IdentityTransactions(IdentityService service) { this.service = service; }

    @Transactional
    public Account register(String email, String password) { return service.register(email, password); }

    @Transactional
    public Account createInternal(UUID actor, String email, String password, Set<Role> roles) {
        return service.createInternal(actor, email, password, roles);
    }

    @Transactional
    public Account bootstrapAdmin(String email, String password) { return service.bootstrapAdmin(email, password); }

    @Transactional(readOnly = true)
    public Account findVisible(UUID actor, UUID target) { return service.findVisible(actor, target); }

    @Transactional
    public Account changeRoles(UUID actor, UUID target, Set<Role> roles) {
        return service.changeRoles(actor, target, roles);
    }

    @Transactional
    public Account changeActive(UUID actor, UUID target, boolean active) {
        return service.changeActive(actor, target, active);
    }

    @Transactional
    public void requestReset(String email) { service.requestReset(email); }

    @Transactional
    public void completeReset(String token, String password) { service.completeReset(token, password); }
}
