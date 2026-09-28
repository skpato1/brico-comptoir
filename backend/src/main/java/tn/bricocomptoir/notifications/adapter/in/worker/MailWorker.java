package tn.bricocomptoir.notifications.adapter.in.worker;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.notifications.application.service.MailDispatcher;

@Component
@ConditionalOnProperty(name="brico.mail.worker-enabled",havingValue="true",matchIfMissing=true)
public class MailWorker {
    private final MailDispatcher dispatcher;
    public MailWorker(MailDispatcher dispatcher) { this.dispatcher=dispatcher; }
    @Scheduled(fixedDelayString="${brico.mail.poll-ms:2000}",initialDelay=3000)
    public void poll() {
        try { for(int i=0;i<10 && dispatcher.dispatchOne();i++) { } }
        catch(RuntimeException failure) { LoggerFactory.getLogger(MailWorker.class).warn("OUTBOX_WORKER_UNAVAILABLE"); }
    }
}
