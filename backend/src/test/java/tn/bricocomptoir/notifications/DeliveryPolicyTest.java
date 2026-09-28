package tn.bricocomptoir.notifications;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.notifications.application.port.out.*;
import tn.bricocomptoir.notifications.application.service.MailDispatcher;
import tn.bricocomptoir.notifications.domain.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryPolicyTest {
    @Test void retriesAreBoundedAndHeadersCannotBeInjected() {
        assertThat(DeliveryPolicy.retryDelay(1)).isEqualTo(Duration.ofSeconds(15));
        assertThat(DeliveryPolicy.retryDelay(5)).isEqualTo(Duration.ofSeconds(240));
        assertThatThrownBy(()->DeliveryPolicy.retryDelay(6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->Mail.validate("key","victim@example.invalid\r\nBcc: other@example.invalid","Hello","Body",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->Mail.validate("key","valid@example.invalid","Hello\r\nX: injected","Body",null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void smtpFailureIsRecordedAndNeverEscapesIntoTheProducer() {
        var store=mock(OutboxStore.class);var provider=mock(EmailProvider.class);
        var now=Instant.parse("2026-09-28T10:00:00Z");
        var mail=new Mail(UUID.randomUUID(),UUID.randomUUID(),1,"test@example.invalid","Subject","Text",null);
        when(store.claim(now)).thenReturn(Optional.of(mail));when(store.current(mail,now)).thenReturn(true);
        doThrow(new IllegalStateException("SMTP down")).when(provider).send(mail);
        assertThat(new MailDispatcher(store,provider,Clock.fixed(now,ZoneOffset.UTC)).dispatchOne()).isTrue();
        verify(store).failed(mail,now);verify(store,never()).sent(any(),any());
    }
    @Test void cancelledClaimNeverReachesProvider() {
        var store=mock(OutboxStore.class);var provider=mock(EmailProvider.class);
        var mail=new Mail(UUID.randomUUID(),UUID.randomUUID(),1,"test@example.invalid","Subject","Text",null);
        when(store.claim(any())).thenReturn(Optional.of(mail));
        assertThat(new MailDispatcher(store,provider,Clock.systemUTC()).dispatchOne()).isTrue();
        verifyNoInteractions(provider);
    }
}
