package tn.bricocomptoir.content.application.service;

import java.util.UUID;
import tn.bricocomptoir.content.application.port.out.ManagedContentStore;
import tn.bricocomptoir.content.domain.ManagedContent.*;

public final class ManagedContentService {
    private final ManagedContentStore store;
    public ManagedContentService(ManagedContentStore store) { this.store = store; }
    public Hero hero(boolean publicOnly) { return store.hero(publicOnly); }
    public Hero saveHero(Hero hero, UUID actor) { return store.saveHero(hero.checked(), actor); }
    public Contact contact() { return store.contact(); }
    public Contact saveContact(Contact contact, UUID actor) { return store.saveContact(contact.checked(), actor); }
    public UUID addMessage(MessageInput input) { return store.addMessage(input.checked()); }
    public MessagePage messages(int page, int size, String status) {
        if (page < 0 || page > 100_000 || size < 1 || size > 50 || status == null
                || !java.util.Set.of("NEW", "RESOLVED").contains(status))
            throw new IllegalArgumentException("Invalid message filter");
        return store.messages(page, size, status);
    }
    public void resolveMessage(UUID id) {
        if (id == null) throw new IllegalArgumentException("Message required");
        store.resolveMessage(id);
    }
}
