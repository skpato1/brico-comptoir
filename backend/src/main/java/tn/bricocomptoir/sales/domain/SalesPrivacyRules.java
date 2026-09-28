package tn.bricocomptoir.sales.domain;
import java.time.*;
import java.util.*;
import tn.bricocomptoir.sales.domain.OrderModels.*;

public final class SalesPrivacyRules {
    private SalesPrivacyRules() { }
    public static void requireFinished(Collection<Status> statuses) {
        if(statuses.stream().anyMatch(s->s!=Status.CANCELLED && s!=Status.DELIVERED))throw new IllegalStateException("ACTIVE_ORDERS");
    }
    public static Instant retainUntil(Instant created,int days) {
        if(days<1)throw new IllegalStateException("LEGAL_RETENTION_NOT_CONFIGURED");
        return created.plus(Duration.ofDays(days));
    }
    public static Address rectify(String scope,Order order,Address input) {
        if(!order.ownerScope().equals(scope))throw new IllegalArgumentException("ORDER_NOT_FOUND");
        if(order.status()!=Status.CONFIRMED)throw new IllegalStateException("ORDER_ALREADY_PROCESSING");
        Address old=order.snapshot().address(),valid;
        try { valid=OrderRules.address(new Address(input.recipient(),input.phone(),input.street(),input.city(),input.postalCode(),input.governorate(),input.country(),old.email())); }
        catch(CheckoutFailure invalid) { throw new IllegalArgumentException("INVALID_ADDRESS"); }
        if(!valid.governorate().equals(old.governorate()))throw new IllegalArgumentException("DELIVERY_ZONE_CHANGED");
        return valid;
    }
}
