package tn.bricocomptoir.content.application.service;

import java.util.UUID;
import tn.bricocomptoir.content.application.port.out.ManagedContentStore;
import tn.bricocomptoir.content.application.port.out.BestSellerProductLookup;
import tn.bricocomptoir.content.domain.ManagedContent.*;

public final class ManagedContentService {
    private final ManagedContentStore store;
    private final BestSellerProductLookup products;
    public ManagedContentService(ManagedContentStore store, BestSellerProductLookup products) {
        this.store = store; this.products = products;
    }
    public SocialLinks socialLinks() { return store.socialLinks(); }
    public SocialLinks saveSocialLinks(SocialLinks value, UUID actor) {
        return store.saveSocialLinks(value.checked(), actor);
    }
    public BestSellers bestSellers(boolean publicOnly) {
        BestSellers selection = store.bestSellers();
        if (publicOnly && (selection.productIds().size() != 3
                || products.visibleIds(selection.productIds()).size() != 3))
            return new BestSellers(selection.version(), java.util.List.of());
        return selection;
    }
    public BestSellers saveBestSellers(BestSellers selection, UUID actor) {
        BestSellers checked = selection.checked();
        if (!checked.productIds().isEmpty() && products.visibleIds(checked.productIds()).size() != 3)
            throw new IllegalArgumentException("Only three published products can be selected");
        return store.saveBestSellers(checked, actor);
    }
    public ProfessionalFavorites professionalFavorites(boolean publicOnly) {
        ProfessionalFavorites selection = store.professionalFavorites();
        if (publicOnly && (selection.productIds().size() != 4
                || products.visibleIds(selection.productIds()).size() != 4))
            return new ProfessionalFavorites(selection.version(), java.util.List.of());
        return selection;
    }
    public ProfessionalFavorites saveProfessionalFavorites(ProfessionalFavorites selection, UUID actor) {
        ProfessionalFavorites checked = selection.checked();
        if (!checked.productIds().isEmpty() && products.visibleIds(checked.productIds()).size() != 4)
            throw new IllegalArgumentException("Only four published products can be selected");
        return store.saveProfessionalFavorites(checked, actor);
    }
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
