package tn.bricocomptoir.content.adapter.transaction;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.bricocomptoir.content.application.port.out.ManagedContentStore;
import tn.bricocomptoir.content.application.port.out.BestSellerProductLookup;
import tn.bricocomptoir.content.application.service.ManagedContentService;
import tn.bricocomptoir.content.domain.ManagedContent.*;

@Service
public class ManagedContentTransactions {
    private final ManagedContentService service;
    public ManagedContentTransactions(ManagedContentStore store, BestSellerProductLookup products) {
        service = new ManagedContentService(store, products);
    }
    @Transactional(readOnly = true) public SocialLinks socialLinks() { return service.socialLinks(); }
    @Transactional public SocialLinks saveSocialLinks(SocialLinks value, UUID actor) { return service.saveSocialLinks(value, actor); }
    @Transactional(readOnly = true) public BestSellers bestSellers(boolean publicOnly) { return service.bestSellers(publicOnly); }
    @Transactional public BestSellers saveBestSellers(BestSellers value, UUID actor) { return service.saveBestSellers(value, actor); }
    @Transactional(readOnly = true) public ProfessionalFavorites professionalFavorites(boolean publicOnly) { return service.professionalFavorites(publicOnly); }
    @Transactional public ProfessionalFavorites saveProfessionalFavorites(ProfessionalFavorites value, UUID actor) { return service.saveProfessionalFavorites(value, actor); }
    @Transactional(readOnly = true) public Hero hero(boolean publicOnly) { return service.hero(publicOnly); }
    @Transactional public Hero saveHero(Hero hero, UUID actor) { return service.saveHero(hero, actor); }
    @Transactional(readOnly = true) public Contact contact() { return service.contact(); }
    @Transactional public Contact saveContact(Contact contact, UUID actor) { return service.saveContact(contact, actor); }
    @Transactional public UUID addMessage(MessageInput input) { return service.addMessage(input); }
    @Transactional(readOnly = true) public MessagePage messages(int page, int size, String status) { return service.messages(page, size, status); }
    @Transactional public void resolveMessage(UUID id) { service.resolveMessage(id); }
}
