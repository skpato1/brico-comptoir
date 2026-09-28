package tn.bricocomptoir.identity.domain;

import java.util.Set;
import java.util.UUID;

public record Account(UUID id, String email, String passwordHash, Set<Role> roles, boolean active, long version) {
    public Account {
        if (id == null || email == null || passwordHash == null || roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Invalid account");
        }
        roles = Set.copyOf(roles);
    }

    public Account withRoles(Set<Role> next) {
        return new Account(id, email, passwordHash, next, active, version + 1);
    }

    public Account withActive(boolean next) {
        return new Account(id, email, passwordHash, roles, next, version + 1);
    }

    public Account withPasswordHash(String next) {
        return new Account(id, email, next, roles, active, version + 1);
    }
}
