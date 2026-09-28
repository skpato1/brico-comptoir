package tn.bricocomptoir.notifications.application.port.out;

import tn.bricocomptoir.notifications.domain.Mail;

/** Provider may use Mail.id as an idempotency key; SMTP only offers a stable Message-ID. */
public interface EmailProvider {
    void send(Mail mail);
}
