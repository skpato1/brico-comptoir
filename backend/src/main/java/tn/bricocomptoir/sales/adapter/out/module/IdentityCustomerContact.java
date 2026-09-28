package tn.bricocomptoir.sales.adapter.out.module;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.identity.application.port.in.IdentityContacts;
import tn.bricocomptoir.sales.application.port.out.CustomerContact;

@Component
public class IdentityCustomerContact implements CustomerContact {
    private final IdentityContacts contacts;
    public IdentityCustomerContact(IdentityContacts contacts) { this.contacts=contacts; }
    @Override public Optional<String> email(UUID id) { return contacts.activeCustomerEmail(id); }
    @Override public boolean current(UUID id,long version) { return contacts.currentOrderManager(id,version); }
    @Override public void lockPurchase(UUID id) { contacts.lockCustomerPurchase(id); }
}
