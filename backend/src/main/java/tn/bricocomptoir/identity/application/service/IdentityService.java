package tn.bricocomptoir.identity.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tn.bricocomptoir.identity.application.port.out.AccountStore;
import tn.bricocomptoir.identity.application.port.out.PasswordHasher;
import tn.bricocomptoir.identity.application.port.out.ResetDelivery;
import tn.bricocomptoir.identity.application.port.out.ResetStore;
import tn.bricocomptoir.identity.domain.Account;
import tn.bricocomptoir.identity.domain.AccountPolicy;
import tn.bricocomptoir.identity.domain.Role;

public final class IdentityService {
    private final AccountStore accounts;
    private final PasswordHasher passwords;
    private final ResetStore resets;
    private final ResetDelivery delivery;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public IdentityService(AccountStore accounts, PasswordHasher passwords, ResetStore resets,
                           ResetDelivery delivery, Clock clock) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.resets = resets;
        this.delivery = delivery;
        this.clock = clock;
    }

    public Account register(String emailInput, String password) {
        String email = AccountPolicy.normalizeEmail(emailInput);
        AccountPolicy.requirePassword(password);
        if (accounts.byEmail(email).isPresent()) throw new IllegalStateException("Email already registered");
        Account account = new Account(UUID.randomUUID(), email, passwords.hash(password),
                Set.of(Role.CUSTOMER), true, 0);
        accounts.insert(account);
        return account;
    }

    public Account createInternal(UUID actor, String emailInput, String password, Set<Role> roles) {
        AccountPolicy.requireInternalRoles(roles);
        accounts.lockAdministration();
        requireAdmin(actor);
        String email = AccountPolicy.normalizeEmail(emailInput);
        AccountPolicy.requirePassword(password);
        if (accounts.byEmail(email).isPresent()) throw new IllegalStateException("Email already registered");
        Account account = new Account(UUID.randomUUID(), email, passwords.hash(password), roles, true, 0);
        accounts.insert(account);
        accounts.audit(actor, account.id(), "CREATE_INTERNAL");
        return account;
    }

    public Account bootstrapAdmin(String emailInput, String password) {
        String email = AccountPolicy.normalizeEmail(emailInput);
        AccountPolicy.requirePassword(password);
        accounts.lockAdministration();
        if (accounts.activeAdminCount() != 0 || accounts.byEmail(email).isPresent()) {
            throw new IllegalStateException("Admin bootstrap requires no existing admin or matching account");
        }
        Account account = new Account(UUID.randomUUID(), email, passwords.hash(password), Set.of(Role.ADMIN), true, 0);
        accounts.insert(account);
        accounts.audit(null, account.id(), "BOOTSTRAP_ADMIN");
        return account;
    }

    public Account findVisible(UUID actor, UUID target) {
        Account requester = requireActive(actor);
        if (!actor.equals(target) && !requester.roles().contains(Role.ADMIN)) {
            throw new SecurityException("Access denied");
        }
        return accounts.byId(target).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    public Account changeRoles(UUID actor, UUID target, Set<Role> roles) {
        if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("Roles required");
        accounts.lockAdministration();
        // Recheck after waiting: another administrator may have revoked this actor.
        requireAdmin(actor);
        Account before = accounts.byId(target).orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (AccountPolicy.removesLastAdmin(before, roles, before.active(), accounts.activeAdminCount())) {
            throw new IllegalStateException("Cannot remove last active admin");
        }
        Account changed = before.withRoles(roles);
        accounts.update(changed);
        accounts.audit(actor, target, "CHANGE_ROLES");
        return changed;
    }

    public Account changeActive(UUID actor, UUID target, boolean active) {
        accounts.lockAdministration();
        requireAdmin(actor);
        Account before = accounts.byId(target).orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (AccountPolicy.removesLastAdmin(before, before.roles(), active, accounts.activeAdminCount())) {
            throw new IllegalStateException("Cannot deactivate last active admin");
        }
        Account changed = before.withActive(active);
        accounts.update(changed);
        if(!active) { resets.deleteForAccount(target);delivery.cancel(before.email()); }
        accounts.audit(actor, target, active ? "ACTIVATE" : "DEACTIVATE");
        return changed;
    }

    public void requestReset(String emailInput) {
        String email;
        try { email = AccountPolicy.normalizeEmail(emailInput); }
        catch (IllegalArgumentException ignored) { return; }
        Optional<Account> existing = accounts.byEmail(email).filter(Account::active);
        if (existing.isEmpty()) return;
        accounts.lockAccount(existing.get().id());
        if(accounts.byId(existing.get().id()).filter(Account::active).isEmpty())return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        resets.deleteForAccount(existing.get().id());
        delivery.cancel(email);
        var expiry=clock.instant().plus(Duration.ofMinutes(30));
        resets.save(digest(token), existing.get().id(), expiry);
        delivery.send(email, token,expiry);
    }
    public void rectifyVerifiedEmail(UUID id,String value) {
        accounts.lockAccount(id);var a=requireActive(id);String email=AccountPolicy.normalizeEmail(value);
        if(accounts.byEmail(email).filter(other->!other.id().equals(id)).isPresent())throw new IllegalArgumentException("EMAIL_UNAVAILABLE");
        resets.deleteForAccount(id);delivery.cancel(a.email());delivery.cancel(email);
        accounts.update(new Account(id,email,a.passwordHash(),a.roles(),true,a.version()+1));
        accounts.audit(id,id,"EMAIL_RECTIFIED");
    }
    public void anonymizeAccount(UUID id) {
        accounts.lockAdministration();accounts.lockAccount(id);var a=requireActive(id);
        if(AccountPolicy.removesLastAdmin(a,Set.of(Role.CUSTOMER),false,accounts.activeAdminCount()))throw new IllegalStateException("LAST_ADMIN");
        resets.deleteForAccount(id);delivery.cancel(a.email());
        accounts.update(new Account(id,"deleted-"+id+"@example.invalid","!DISABLED",Set.of(Role.CUSTOMER),false,a.version()+1));
        accounts.audit(id,id,"PERSONAL_DATA_REMOVED");
    }

    public void completeReset(String token, String newPassword) {
        AccountPolicy.requirePassword(newPassword);
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        String hash=digest(token);
        UUID accountId=resets.accountFor(hash).orElseThrow(()->new IllegalArgumentException("Invalid or expired token"));
        accounts.lockAccount(accountId);
        resets.consume(hash, clock.instant())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));
        Account account = accounts.byId(accountId).orElseThrow();
        if (!account.active()) throw new IllegalArgumentException("Invalid or expired token");
        accounts.update(account.withPasswordHash(passwords.hash(newPassword)));
        resets.deleteForAccount(accountId);
        delivery.cancel(account.email());
    }

    private Account requireActive(UUID id) {
        return accounts.byId(id).filter(Account::active).orElseThrow(() -> new SecurityException("Access denied"));
    }

    private void requireAdmin(UUID actor) {
        if (!requireActive(actor).roles().contains(Role.ADMIN)) throw new SecurityException("Access denied");
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
