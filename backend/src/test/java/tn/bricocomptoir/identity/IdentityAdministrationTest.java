package tn.bricocomptoir.identity;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tn.bricocomptoir.identity.application.port.out.*;
import tn.bricocomptoir.identity.application.service.IdentityService;
import tn.bricocomptoir.identity.domain.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IdentityAdministrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"roles", "active", "create"})
    void revocationWhileWaitingForAdministrationLockPreventsMutation(String action) {
        var accounts = mock(AccountStore.class);
        UUID actor = UUID.randomUUID(), target = UUID.randomUUID();
        var revoked = new AtomicBoolean();
        doAnswer(call -> { revoked.set(true); return null; }).when(accounts).lockAdministration();
        when(accounts.byId(actor)).thenAnswer(call -> java.util.Optional.of(new Account(actor,
                "admin@test.invalid", "hash", Set.of(revoked.get() ? Role.CATALOG_MANAGER : Role.ADMIN), true, 0)));
        when(accounts.byId(target)).thenReturn(java.util.Optional.of(new Account(target,
                "customer@test.invalid", "hash", Set.of(Role.CUSTOMER), true, 0)));
        var passwords = mock(PasswordHasher.class);
        when(passwords.hash(any())).thenReturn("hash");
        var service = new IdentityService(accounts, passwords, mock(ResetStore.class),
                mock(ResetDelivery.class), Clock.systemUTC());
        assertThatThrownBy(() -> {
            switch (action) {
                case "roles" -> service.changeRoles(actor, target, Set.of(Role.ADMIN));
                case "active" -> service.changeActive(actor, target, false);
                default -> service.createInternal(actor, "new@test.invalid", "Long-Password-2026!", Set.of(Role.ADMIN));
            }
        }).isInstanceOf(SecurityException.class);
        verify(accounts, never()).insert(any());
        verify(accounts, never()).update(any());
    }
}
