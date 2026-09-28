package tn.bricocomptoir.privacy;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.sales.domain.SalesPrivacyRules;
import tn.bricocomptoir.sales.domain.OrderModels.Status;
import static org.assertj.core.api.Assertions.*;
class SalesPrivacyRulesTest {
    @Test void aDeliveryStillInProgressPreventsRemovalEvenAlongsideFinishedOrders() {
        assertThatThrownBy(()->SalesPrivacyRules.requireFinished(List.of(Status.DELIVERED,Status.SHIPPED,Status.CANCELLED))).hasMessage("ACTIVE_ORDERS");
        assertThatCode(()->SalesPrivacyRules.requireFinished(List.of(Status.CANCELLED,Status.DELIVERED))).doesNotThrowAnyException();
    }
    @Test void anUndecidedLegalDurationMustNotSilentlyDeleteAContact() {
        Instant created=Instant.parse("2026-09-28T12:00:00Z");
        assertThatThrownBy(()->SalesPrivacyRules.retainUntil(created,0)).hasMessage("LEGAL_RETENTION_NOT_CONFIGURED");
        assertThat(SalesPrivacyRules.retainUntil(created,30)).isEqualTo(Instant.parse("2026-10-28T12:00:00Z"));
    }
}
