package tn.bricocomptoir.notifications.adapter.in.web;

import java.time.Clock;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tn.bricocomptoir.notifications.adapter.out.persistence.JdbcNotifications;

@RestController
@RequestMapping("/api/v1/admin/mail-outbox")
public class OutboxController {
    private final JdbcNotifications store;
    private final Clock clock;
    public OutboxController(JdbcNotifications store,Clock clock) { this.store=store;this.clock=clock; }
    @GetMapping
    public ResponseEntity<JdbcNotifications.Page> outbox(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        try { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.page(page,size)); }
        catch(IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }
    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable UUID id) {
        if(!store.retry(id,clock.instant())) throw new ResponseStatusException(HttpStatus.CONFLICT);
    }
}
