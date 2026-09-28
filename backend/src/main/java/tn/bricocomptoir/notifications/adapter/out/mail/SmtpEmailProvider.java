package tn.bricocomptoir.notifications.adapter.out.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.notifications.application.port.out.EmailProvider;
import tn.bricocomptoir.notifications.domain.Mail;

@Component
public class SmtpEmailProvider implements EmailProvider {
    private final JavaMailSender sender;
    private final String from;
    public SmtpEmailProvider(JavaMailSender sender,@Value("${brico.mail.from}") String from) { this.sender=sender;this.from=from; }
    @Override public void send(Mail mail) {
        try {
            MimeMessage message=new MimeMessage(sender.createMimeMessage().getSession()) {
                @Override protected void updateMessageID() throws MessagingException {
                    setHeader("Message-ID","<"+mail.id()+"@outbox.bricocomptoir>");
                }
            };
            var helper=new MimeMessageHelper(message,"UTF-8");
            helper.setFrom(from);helper.setTo(mail.recipient());helper.setSubject(mail.subject());helper.setText(mail.text(),false);
            sender.send(message);
        } catch(MessagingException failure) { throw new IllegalStateException("PROVIDER_UNAVAILABLE",failure); }
    }
}
