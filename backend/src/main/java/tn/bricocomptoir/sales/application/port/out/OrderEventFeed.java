package tn.bricocomptoir.sales.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
public interface OrderEventFeed {
    record Event(long id,UUID orderId,String type,Instant createdAt) { }
    long latest();
    List<Event> events(long after,int size);
}
