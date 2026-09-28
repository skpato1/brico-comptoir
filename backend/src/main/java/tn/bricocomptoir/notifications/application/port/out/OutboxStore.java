package tn.bricocomptoir.notifications.application.port.out;

import java.time.Instant;
import java.util.Optional;
import tn.bricocomptoir.notifications.domain.Mail;

public interface OutboxStore {
    Optional<Mail> claim(Instant now);
    boolean current(Mail mail, Instant now);
    void sent(Mail mail, Instant now);
    void failed(Mail mail, Instant now);
}
