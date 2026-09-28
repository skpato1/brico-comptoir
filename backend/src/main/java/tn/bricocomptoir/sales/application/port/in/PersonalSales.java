package tn.bricocomptoir.sales.application.port.in;
import java.time.Instant;
import java.util.*;

public interface PersonalSales {
    record Data(String ordersJson,String cartJson,boolean hasMore) { }
    Data export(String ownerScope,int page);
    void anonymize(String ownerScope,int legalDays);
    void rectify(String ownerScope,UUID orderId,String addressJson);
    void hold(UUID orderId,boolean held);
    String archive(UUID orderId);
    int retain(Instant now,int contactDays,int cartDays,int legalDays);
}
