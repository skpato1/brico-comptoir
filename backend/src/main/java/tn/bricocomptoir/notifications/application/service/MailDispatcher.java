package tn.bricocomptoir.notifications.application.service;

import java.time.Clock;
import tn.bricocomptoir.notifications.application.port.out.EmailProvider;
import tn.bricocomptoir.notifications.application.port.out.OutboxStore;

public final class MailDispatcher {
    private final OutboxStore store;
    private final EmailProvider provider;
    private final Clock clock;
    public MailDispatcher(OutboxStore store, EmailProvider provider, Clock clock) {
        this.store=store; this.provider=provider; this.clock=clock;
    }
    public boolean dispatchOne() {
        var claimed=store.claim(clock.instant());
        if (claimed.isEmpty()) return false;
        var mail=claimed.get();
        if (!store.current(mail,clock.instant())) return true;
        try { provider.send(mail); }
        catch (RuntimeException unavailable) { store.failed(mail,clock.instant()); return true; }
        store.sent(mail,clock.instant());
        return true;
    }
}
