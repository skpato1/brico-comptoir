package tn.bricocomptoir.sales.adapter.out.module;

import java.util.List;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.sales.application.port.out.OrderEventFeed;
import tn.bricocomptoir.notifications.application.port.in.OrderEventQueries;

@Component
public class NotificationEventFeed implements OrderEventFeed {
    private final OrderEventQueries events;
    public NotificationEventFeed(OrderEventQueries events) { this.events=events; }
    @Override public long latest() { return events.latest(); }
    @Override public List<Event> events(long after,int size) {
        return events.events(after,size).stream().map(e->new Event(e.id(),e.orderId(),e.type(),e.createdAt())).toList();
    }
}
