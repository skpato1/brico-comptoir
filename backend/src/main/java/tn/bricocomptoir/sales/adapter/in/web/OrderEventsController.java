package tn.bricocomptoir.sales.adapter.in.web;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.bricocomptoir.sales.application.port.out.OrderEventFeed;
import tn.bricocomptoir.sales.application.port.out.CustomerContact;

@RestController
@RequestMapping("/api/v1/admin")
public class OrderEventsController {
    private final OrderEventFeed store;
    private final CustomerContact access;
    private final ScheduledExecutorService executor=Executors.newScheduledThreadPool(4,Thread.ofPlatform().daemon().name("order-sse-",0).factory());
    private final AtomicInteger connections=new AtomicInteger();
    private final ConcurrentMap<UUID,AtomicInteger> perAccount=new ConcurrentHashMap<>();
    public OrderEventsController(OrderEventFeed store,CustomerContact access) { this.store=store;this.access=access; }
    @GetMapping("/order-events")
    public ResponseEntity<List<OrderEventFeed.Event>> events(@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="100") int size) {
        try { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.events(after,size)); }
        catch(IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }
    @GetMapping(value="/order-events/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestParam(required=false) Long after,
        @RequestHeader(value="Last-Event-ID",required=false) String last,
        @AuthenticationPrincipal(expression="#this.id") UUID id,
        @AuthenticationPrincipal(expression="#this.version") long version,HttpServletRequest request) {
        long cursor;
        try { cursor=last!=null?Long.parseLong(last):after==null?store.latest():after;store.events(cursor,1); }
        catch(IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        if(!access.current(id,version)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(connections.incrementAndGet()>64) { connections.decrementAndGet();throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS); }
        var count=perAccount.compute(id,(k,v)-> {var value=v==null?new AtomicInteger():v;value.incrementAndGet();return value;});
        if(count.get()>2) { releaseAccount(id);connections.decrementAndGet();throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS); }
        var emitter=new SseEmitter(60000L);
        var closed=new AtomicBoolean();var position=new AtomicLong(cursor);
        var task=new AtomicReference<ScheduledFuture<?>>();
        HttpSession session=request.getSession(false);
        Runnable cleanup=()-> { if(closed.compareAndSet(false,true)) { connections.decrementAndGet();releaseAccount(id);
            var future=task.get();if(future!=null)future.cancel(false); } };
        emitter.onCompletion(cleanup);emitter.onTimeout(()->{cleanup.run();emitter.complete();});emitter.onError(e->cleanup.run());
        try { emitter.send(SseEmitter.event().name("ready").id(Long.toString(cursor)).reconnectTime(3000).data("{}")); }
        catch(Exception disconnected) { cleanup.run(); }
        if(!closed.get()) {
            task.set(executor.scheduleWithFixedDelay(()-> {
                if(closed.get())return;
                try {
                    // Session invalidation/logout and role revocation must also stop an already open stream.
                    if(session==null || session.getAttribute("SPRING_SECURITY_CONTEXT")==null || !access.current(id,version)) {
                        cleanup.run();emitter.complete();return;
                    }
                    var events=store.events(position.get(),100);
                    for(var event:events) {
                        emitter.send(SseEmitter.event().name("order-created").id(Long.toString(event.id())).data(event));
                        position.set(event.id());
                    }
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch(Exception disconnected) { cleanup.run();emitter.complete(); }
            },0,2,TimeUnit.SECONDS));
            if(closed.get())task.get().cancel(false);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(emitter);
    }
    private void releaseAccount(UUID id) { perAccount.computeIfPresent(id,(k,count)->count.decrementAndGet()==0?null:count); }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
