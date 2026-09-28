package tn.bricocomptoir.identity.adapter.in.module;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.identity.application.port.in.IdentityContacts;
import tn.bricocomptoir.identity.application.port.out.AccountStore;
import tn.bricocomptoir.identity.domain.*;

@Component
public class IdentityContactsAdapter implements IdentityContacts {
    private final AccountStore accounts;
    public IdentityContactsAdapter(AccountStore accounts) { this.accounts=accounts; }
    @Override public Optional<String> activeCustomerEmail(UUID id) {
        return accounts.byId(id).filter(a->a.active() && a.roles().contains(Role.CUSTOMER)).map(Account::email);
    }
    @Override public boolean currentOrderManager(UUID id,long version) {
        return accounts.byId(id).filter(a->a.active() && a.version()==version &&
            (a.roles().contains(Role.ADMIN)||a.roles().contains(Role.ORDER_MANAGER))).isPresent();
    }
    @Override public void lockCustomerPurchase(UUID id) {
        accounts.lockAccount(id);
        if(activeCustomerEmail(id).isEmpty())throw new IllegalStateException("ACCOUNT_UNAVAILABLE");
    }
}
