package tn.bricocomptoir.identity.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Internal queries; never exposed as an email directory over HTTP. */
public interface IdentityContacts {
    Optional<String> activeCustomerEmail(UUID accountId);
    boolean currentOrderManager(UUID accountId,long sessionVersion);
    void lockCustomerPurchase(UUID accountId);
}
