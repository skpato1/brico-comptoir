package tn.bricocomptoir.sales.application.port.out;

import java.util.Optional;
import java.util.UUID;
public interface CustomerContact {
    Optional<String> email(UUID id);
    boolean current(UUID id,long version);
    default void lockPurchase(UUID id) { }
}
