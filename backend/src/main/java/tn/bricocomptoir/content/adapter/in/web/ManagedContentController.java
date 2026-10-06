package tn.bricocomptoir.content.adapter.in.web;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tn.bricocomptoir.content.adapter.transaction.ManagedContentTransactions;
import tn.bricocomptoir.content.domain.ManagedContent.*;

@RestController
@RequestMapping("/api/v1")
public class ManagedContentController {
    private final ManagedContentTransactions content;
    private final Map<String, ArrayDeque<Instant>> submissions = new HashMap<>();
    public ManagedContentController(ManagedContentTransactions content) { this.content = content; }

    @GetMapping("/content/hero")
    public ResponseEntity<Hero> publicHero() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.hero(true)); }
    @GetMapping("/admin/content/hero")
    public ResponseEntity<Hero> adminHero() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.hero(false)); }
    @PutMapping("/admin/content/hero")
    public Hero saveHero(@RequestBody Hero hero, @org.springframework.security.core.annotation.AuthenticationPrincipal(expression="id") UUID actor) {
        return content.saveHero(hero, actor);
    }

    @GetMapping("/contact")
    public ResponseEntity<Contact> publicContact() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.contact()); }
    @GetMapping("/admin/contact")
    public ResponseEntity<Contact> adminContact() { return publicContact(); }
    @PutMapping("/admin/contact")
    public Contact saveContact(@RequestBody Contact details,
            @org.springframework.security.core.annotation.AuthenticationPrincipal(expression="id") UUID actor) {
        return content.saveContact(details, actor);
    }

    @PostMapping("/contact/messages")
    public ResponseEntity<Void> send(@RequestBody MessageInput input, HttpServletRequest request) {
        input.checked();
        if (!allow(request.getRemoteAddr())) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
        UUID id = content.addMessage(input);
        return ResponseEntity.created(URI.create("/api/v1/contact/messages/" + id)).build();
    }
    @GetMapping("/admin/contact/messages")
    public ResponseEntity<MessagePage> messages(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="NEW") String status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.messages(page, size, status));
    }
    @PostMapping("/admin/contact/messages/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable UUID id) { content.resolveMessage(id); }

    private synchronized boolean allow(String source) {
        Instant now = Instant.now();
        submissions.values().forEach(queue -> {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) queue.removeFirst();
        });
        submissions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (submissions.size() > 10_000) return false;
        var queue = submissions.computeIfAbsent(source, ignored -> new ArrayDeque<>());
        if (queue.size() >= 5) return false;
        queue.addLast(now);
        return true;
    }
}
