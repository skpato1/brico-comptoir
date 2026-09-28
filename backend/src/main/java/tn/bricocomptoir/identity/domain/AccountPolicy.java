package tn.bricocomptoir.identity.domain;

import java.util.Locale;
import java.util.Set;

public final class AccountPolicy {
    private AccountPolicy() { }

    public static String normalizeEmail(String value) {
        if (value == null) throw new IllegalArgumentException("Invalid email");
        String email = value.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return email;
    }

    public static void requirePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128 || password.isBlank()) {
            throw new IllegalArgumentException("Password must have 12 to 128 characters");
        }
    }

    public static void requireInternalRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty() || roles.contains(Role.CUSTOMER)) {
            throw new IllegalArgumentException("Internal accounts need internal roles");
        }
    }

    public static boolean removesLastAdmin(Account before, Set<Role> after, boolean active, long activeAdmins) {
        return before.active() && before.roles().contains(Role.ADMIN)
                && (!active || !after.contains(Role.ADMIN)) && activeAdmins <= 1;
    }
}
