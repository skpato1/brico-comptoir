package tn.bricocomptoir.privacy.adapter.in.web;
import java.time.*;
import java.util.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tn.bricocomptoir.privacy.adapter.transaction.PrivacyTransactions;
import tn.bricocomptoir.privacy.application.service.PrivacyService;

@RestController @RequestMapping("/api/v1")
public class PrivacyController {
    private final PrivacyTransactions privacy;
    private final AuthenticationManager authentication;
    private final ObjectMapper json;
    private final Map<UUID,Attempts> failures=new HashMap<>();
    public PrivacyController(PrivacyTransactions privacy,AuthenticationManager authentication,ObjectMapper json) {
        this.privacy=privacy;this.authentication=authentication;this.json=json;
    }
    private static final String ID="#this instanceof T(java.lang.String) ? null : #this.id";
    @GetMapping("/privacy/me") public ResponseEntity<PrivacyService.View> view(@AuthenticationPrincipal(expression=ID) UUID id) { return ok(privacy.view(id)); }
    @PutMapping("/privacy/consent") public ResponseEntity<PrivacyService.View> consent(@AuthenticationPrincipal(expression=ID) UUID id,@Valid @RequestBody Consent input) {
        return ok(privacy.consent(id,input.marketing(),input.noticeVersion()));
    }
    @PostMapping("/privacy/export") public ResponseEntity<?> export(@AuthenticationPrincipal(expression=ID) UUID id,Authentication actor,@Valid @RequestBody Password input,@RequestParam(defaultValue="0") int page) {
        verify(id,actor,input.password());var result=privacy.export(id,page);
        var actions=json.readTree(privacy.history(id,page));
        return ok(Map.of("schemaVersion",1,"profile",result.profile(),"orders",json.readTree(result.sales().ordersJson()),
            "cart",json.readTree(result.sales().cartJson()),"actions",actions,"page",page,"hasMore",result.sales().hasMore() || actions.size()==100));
    }
    @PostMapping("/privacy/email/request") public ResponseEntity<Void> requestEmail(@AuthenticationPrincipal(expression=ID) UUID id,Authentication actor,@Valid @RequestBody EmailChange input) {
        verify(id,actor,input.password());privacy.requestEmail(id,input.email());return ok(null);
    }
    @PostMapping("/privacy/email/confirm") public ResponseEntity<Void> confirmEmail(@AuthenticationPrincipal(expression=ID) UUID id,@Valid @RequestBody Token input,HttpServletRequest request,HttpServletResponse response,Authentication actor) {
        privacy.confirmEmail(id,input.token());logout(request,response,actor);return ok(null);
    }
    @PatchMapping("/privacy/orders/{orderId}/address") public ResponseEntity<Void> rectify(@AuthenticationPrincipal(expression=ID) UUID id,@PathVariable UUID orderId,@RequestBody Map<String,Object> input) {
        privacy.rectify(id,orderId,json.writeValueAsString(input));return ok(null);
    }
    @PostMapping("/privacy/anonymize") public ResponseEntity<Void> anonymize(@AuthenticationPrincipal(expression=ID) UUID id,Authentication actor,@Valid @RequestBody Removal input,HttpServletRequest request,HttpServletResponse response) {
        if(!input.confirm())throw new IllegalArgumentException("CONFIRMATION_REQUIRED");
        verify(id,actor,input.password());privacy.anonymize(id);logout(request,response,actor);return ok(null);
    }
    @PostMapping("/privacy/guest/export") public ResponseEntity<?> guestExport(HttpServletRequest request,Authentication actor,@RequestParam(defaultValue="0") int page) {
        var result=privacy.guestExport(guest(request,actor),page);
        return ok(Map.of("schemaVersion",1,"orders",json.readTree(result.ordersJson()),"page",page,"hasMore",result.hasMore()));
    }
    @PatchMapping("/privacy/guest/orders/{id}/address") public ResponseEntity<Void> guestRectify(HttpServletRequest request,Authentication actor,@PathVariable UUID id,@RequestBody Map<String,Object> address) {
        privacy.guestRectify(guest(request,actor),id,json.writeValueAsString(address));return ok(null);
    }
    @PostMapping("/privacy/guest/anonymize") public ResponseEntity<Void> guestRemove(HttpServletRequest request,HttpServletResponse response,Authentication actor,@RequestBody GuestRemoval input) {
        if(!input.confirm())throw new IllegalArgumentException("CONFIRMATION_REQUIRED");
        privacy.guestAnonymize(guest(request,actor));logout(request,response,actor);return ok(null);
    }
    @PutMapping("/admin/privacy/orders/{id}/hold") public ResponseEntity<Void> hold(@AuthenticationPrincipal(expression=ID) UUID actor,@PathVariable UUID id,@Valid @RequestBody Hold input) {
        if(!input.reasonCode().matches("LEGAL_OBLIGATION|DISPUTE|HOLD_RELEASED"))throw new IllegalArgumentException("INVALID_HOLD_REASON");
        privacy.hold(actor,id,input.held(),input.reasonCode());return ok(null);
    }
    @PostMapping("/admin/privacy/retention") public ResponseEntity<?> retain(@AuthenticationPrincipal(expression=ID) UUID actor) { return ok(Map.of("processed",privacy.retainAs(actor))); }
    @PostMapping("/admin/privacy/orders/{id}/archive-export") public ResponseEntity<?> archive(@AuthenticationPrincipal(expression=ID) UUID actorId,Authentication actor,@PathVariable UUID id,@Valid @RequestBody Archive input) {
        verify(actorId,actor,input.password());
        return ok(Map.of("orderId",id,"address",json.readTree(privacy.archive(actorId,id,input.caseReference()))));
    }
    @GetMapping("/privacy/history") public ResponseEntity<?> history(@AuthenticationPrincipal(expression=ID) UUID id,@RequestParam(defaultValue="0") int page) { return ok(json.readTree(privacy.history(id,page))); }
    private String guest(HttpServletRequest request,Authentication actor) {
        if(actor!=null && !(actor instanceof AnonymousAuthenticationToken))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var session=request.getSession(false);return session==null?null:(String)session.getAttribute("sales.guest-scope");
    }
    private void verify(UUID id,Authentication actor,String password) {
        synchronized(failures) {
            Instant now=Instant.now();failures.entrySet().removeIf(e->e.getValue().until().isBefore(now));
            var entry=failures.get(id);
            if(entry!=null && entry.count()>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            if(failures.size()>10000)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            failures.put(id,new Attempts(entry==null?1:entry.count()+1,entry==null?now.plusSeconds(900):entry.until()));
        }
        try { authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(actor.getName(),password)); }
        catch(org.springframework.security.core.AuthenticationException invalid) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"REAUTHENTICATION_REQUIRED"); }
        synchronized(failures) { failures.remove(id); }
    }
    private void logout(HttpServletRequest request,HttpServletResponse response,Authentication actor) { new SecurityContextLogoutHandler().logout(request,response,actor); }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    record Attempts(int count,Instant until) { }
    public record Consent(@NotNull Boolean marketing,@NotBlank String noticeVersion) { }
    public record Password(@NotBlank @Size(max=128) String password) { }
    public record EmailChange(@NotBlank @Size(max=128) String password,@NotBlank @Email @Size(max=254) String email) { }
    public record Token(@NotBlank @Size(max=128) String token) { }
    public record Removal(@NotBlank @Size(max=128) String password,@NotNull Boolean confirm) { }
    public record GuestRemoval(boolean confirm) { }
    public record Hold(boolean held,@NotBlank String reasonCode) { }
    public record Archive(@NotBlank @Size(max=128) String password,@NotBlank @Size(max=40) String caseReference) { }
}
