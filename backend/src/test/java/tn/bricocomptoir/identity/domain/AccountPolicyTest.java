package tn.bricocomptoir.identity.domain;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountPolicyTest {
    @Test
    void normalizesEmailAndRejectsInvalidInput() {
        assertThat(AccountPolicy.normalizeEmail("  Alice@Example.TN  ")).isEqualTo("alice@example.tn");
        assertThatThrownBy(() -> AccountPolicy.normalizeEmail("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCustomerCannotClaimAnInternalRoleThroughInternalCreation() {
        assertThatThrownBy(() -> AccountPolicy.requireInternalRoles(Set.of(Role.CUSTOMER, Role.ADMIN)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountPolicy.requireInternalRoles(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preventsRemovingOrDisablingTheLastActiveAdministrator() {
        Account admin = new Account(UUID.randomUUID(), "admin@example.tn", "hash", Set.of(Role.ADMIN), true, 0);
        assertThat(AccountPolicy.removesLastAdmin(admin, Set.of(Role.CATALOG_MANAGER), true, 1)).isTrue();
        assertThat(AccountPolicy.removesLastAdmin(admin, admin.roles(), false, 1)).isTrue();
        assertThat(AccountPolicy.removesLastAdmin(admin, Set.of(Role.CATALOG_MANAGER), true, 2)).isFalse();
    }

    @Test
    void passwordIsBounded() {
        AccountPolicy.requirePassword("a-valid-password");
        assertThatThrownBy(() -> AccountPolicy.requirePassword("short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountPolicy.requirePassword("x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
