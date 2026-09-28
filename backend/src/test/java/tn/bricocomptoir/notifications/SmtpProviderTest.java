package tn.bricocomptoir.notifications;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import tn.bricocomptoir.notifications.adapter.out.mail.SmtpEmailProvider;
import tn.bricocomptoir.notifications.domain.Mail;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SmtpProviderTest {
    @Test void repeatedDeliveryUsesTheSameMessageIdAndPlainUtf8Content() throws Exception {
        var sender=mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(i->new MimeMessage((Session)null));
        var provider=new SmtpEmailProvider(sender,"noreply@test.invalid");
        var mail=new Mail(UUID.randomUUID(),UUID.randomUUID(),1,"client@test.invalid","Commande confirmée","Résumé en TND",null);
        provider.send(mail);provider.send(mail);
        var captured=ArgumentCaptor.forClass(MimeMessage.class);verify(sender,times(2)).send(captured.capture());
        for(var message:captured.getAllValues()) {
            message.saveChanges();assertThat(message.getMessageID()).isEqualTo("<"+mail.id()+"@outbox.bricocomptoir>");
            assertThat(message.getAllRecipients()).hasSize(1);assertThat(message.getContent().toString()).contains("Résumé en TND");
        }
    }
}
