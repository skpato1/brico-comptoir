package tn.bricocomptoir.identity.adapter.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import tn.bricocomptoir.identity.domain.Account;
import tn.bricocomptoir.identity.domain.Role;

public record SessionUser(UUID id, String email, String passwordHash, Set<Role> roles,
                          boolean active, long version) implements UserDetails {
    public SessionUser(Account account) {
        this(account.id(), account.email(), account.passwordHash(), account.roles(), account.active(), account.version());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
}
