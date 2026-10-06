package tn.bricocomptoir.identity.adapter.in.web;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tn.bricocomptoir.identity.adapter.security.AttemptThrottle;
import tn.bricocomptoir.identity.adapter.security.SessionUser;
import tn.bricocomptoir.identity.adapter.transaction.IdentityTransactions;
import tn.bricocomptoir.identity.domain.Account;
import tn.bricocomptoir.identity.domain.Role;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final IdentityTransactions identity;
    private final AuthenticationManager authentication;
    private final HttpSessionSecurityContextRepository contexts;
    private final CookieCsrfTokenRepository csrf;
    private final AttemptThrottle throttle;

    public IdentityController(IdentityTransactions identity, AuthenticationManager authentication,
                              HttpSessionSecurityContextRepository contexts, CookieCsrfTokenRepository csrf,
                              AttemptThrottle throttle) {
        this.identity = identity;
        this.authentication = authentication;
        this.contexts = contexts;
        this.csrf = csrf;
        this.throttle = throttle;
    }

    @GetMapping("/auth/csrf")
    public ResponseEntity<Void> csrf(HttpServletRequest request) {
        request.getSession(true);
        ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AccountView> register(@Valid @RequestBody Credentials input, HttpServletRequest request) {
        limit("register", request, 5, Duration.ofHours(1));
        Account account = identity.register(input.email(), input.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountView.of(account));
    }

    @PostMapping("/auth/login")
    public AccountView login(@Valid @RequestBody Credentials input, HttpServletRequest request,
                             HttpServletResponse response) {
        limit("login", request, 10, Duration.ofMinutes(15));
        try {
            var result = authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
            boolean existingSession = request.getSession(false) != null;
            request.getSession(true);
            if (existingSession) request.changeSessionId();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(result);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            csrf.saveToken(null, request, response);
            csrf.saveToken(csrf.generateToken(request), request, response);
            SessionUser user = (SessionUser) result.getPrincipal();
            return new AccountView(user.id(), user.email(), user.roles(), true);
        } catch (AuthenticationException failure) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        csrf.saveToken(null, request, response);
        csrf.saveToken(csrf.generateToken(request), request, response);
    }

    @GetMapping("/auth/me")
    public AccountView me(@AuthenticationPrincipal SessionUser user) {
        return AccountView.of(identity.findVisible(user.id(), user.id()));
    }

    @GetMapping("/accounts/{id}")
    public AccountView account(@AuthenticationPrincipal SessionUser user, @PathVariable UUID id) {
        return AccountView.of(identity.findVisible(user.id(), id));
    }

    @PostMapping("/auth/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody ResetRequest input, HttpServletRequest request) {
        limit("reset", request, 5, Duration.ofHours(1));
        identity.requestReset(input.email());
    }

    @PostMapping("/auth/password-reset/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeReset(@Valid @RequestBody ResetComplete input) {
        identity.completeReset(input.token(), input.password());
    }

    @PostMapping("/admin/internal-accounts")
    public ResponseEntity<AccountView> createInternal(@AuthenticationPrincipal SessionUser user,
            @Valid @RequestBody InternalAccountRequest input) {
        Account created = identity.createInternal(user.id(), input.email(), input.password(), input.roles());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountView.of(created));
    }

    @GetMapping("/admin/accounts/lookup")
    public AccountView lookup(@AuthenticationPrincipal SessionUser user, @RequestParam String email) {
        return AccountView.of(identity.findByEmailVisible(user.id(), email));
    }

    @PutMapping("/admin/accounts/{id}/roles")
    public AccountView changeRoles(@AuthenticationPrincipal SessionUser user, @PathVariable UUID id,
                                   @Valid @RequestBody RolesRequest input) {
        return AccountView.of(identity.changeRoles(user.id(), id, input.roles()));
    }

    @PatchMapping("/admin/accounts/{id}/active")
    public AccountView changeActive(@AuthenticationPrincipal SessionUser user, @PathVariable UUID id,
                                    @RequestBody ActiveRequest input) {
        if (input.active() == null) throw new IllegalArgumentException("Active state required");
        return AccountView.of(identity.changeActive(user.id(), id, input.active()));
    }

    private void limit(String scope, HttpServletRequest request, int count, Duration window) {
        if (!throttle.allow(scope, request.getRemoteAddr(), count, window)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
        }
    }

    public record Credentials(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(min = 12, max = 128) String password) { }
    public record ResetRequest(@NotBlank @Size(max = 254) String email) { }
    public record ResetComplete(@NotBlank @Size(max = 128) String token,
                                @NotBlank @Size(min = 12, max = 128) String password) { }
    public record InternalAccountRequest(@NotBlank @Email @Size(max = 254) String email,
                                         @NotBlank @Size(min = 12, max = 128) String password,
                                         @NotEmpty Set<Role> roles) { }
    public record RolesRequest(@NotEmpty Set<Role> roles) { }
    public record ActiveRequest(Boolean active) { }
    public record AccountView(UUID id, String email, Set<Role> roles, boolean active) {
        static AccountView of(Account account) {
            return new AccountView(account.id(), account.email(), account.roles(), account.active());
        }
    }
}
