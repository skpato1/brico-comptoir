package tn.bricocomptoir.privacy;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.privacy.application.port.out.*;
import tn.bricocomptoir.privacy.application.service.PrivacyService;
import tn.bricocomptoir.privacy.domain.PrivacyPolicy;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivacyServiceTest {
    private final PersonalData data=mock(PersonalData.class);
    private final PrivacyStore store=mock(PrivacyStore.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"),ZoneOffset.UTC);
    private PrivacyService service(boolean enabled) { return new PrivacyService(data,store,new PrivacyPolicy(90,30,30,365,enabled?3650:0,enabled),clock); }
    @Test void changedNoticeDoesNotRecordConsentAndWithdrawalDoesNotTouchOrders() {
        UUID id=UUID.randomUUID();
        assertThatThrownBy(()->service(false).consent(id,true,"old")).hasMessage("NOTICE_CHANGED");
        verifyNoInteractions(store);
        service(false).consent(id,false,PrivacyService.NOTICE);
        verify(store).consent(id,false,PrivacyService.NOTICE);
        verify(data,never()).salesRemove(any(),anyInt());
        verify(data,never()).cancelMail(any());
    }
    @Test void activeOrderFailurePreventsAccountRemovalAndConsentChanges() {
        UUID id=UUID.randomUUID();doThrow(new IllegalStateException("ACTIVE_ORDERS")).when(data).salesRemove("C:"+id,3650);
        assertThatThrownBy(()->service(true).anonymize(id)).hasMessage("ACTIVE_ORDERS");
        verify(data,never()).identityRemove(any());verifyNoInteractions(store);
    }
    @Test void retentionCannotRunWithoutExplicitConfiguration() {
        assertThatThrownBy(()->service(false).retain()).hasMessage("RETENTION_DISABLED");verifyNoInteractions(data,store);
        assertThatThrownBy(()->new PrivacyPolicy(90,30,30,365,0,true)).hasMessage("INVALID_RETENTION_POLICY");
        assertThat(new PrivacyPolicy(90,30,30,365,3650,true).legalUntil(clock.instant())).isAfter(clock.instant());
    }
    @Test void guestCannotChooseCustomerScopeAndExportsStayPaged() {
        assertThatThrownBy(()->service(false).guestExport("C:"+UUID.randomUUID(),0)).hasMessage("GUEST_SESSION_REQUIRED");
        assertThatThrownBy(()->service(false).export(UUID.randomUUID(),-1)).hasMessage("INVALID_PAGE");verifyNoInteractions(data,store);
    }
}
