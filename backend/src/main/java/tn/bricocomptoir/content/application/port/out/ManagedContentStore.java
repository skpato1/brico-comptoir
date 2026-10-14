package tn.bricocomptoir.content.application.port.out;

import java.util.UUID;
import tn.bricocomptoir.content.domain.ManagedContent.*;

public interface ManagedContentStore {
    SocialLinks socialLinks();
    SocialLinks saveSocialLinks(SocialLinks value, UUID actor);
    BestSellers bestSellers();
    BestSellers saveBestSellers(BestSellers value, UUID actor);
    ProfessionalFavorites professionalFavorites();
    ProfessionalFavorites saveProfessionalFavorites(ProfessionalFavorites value, UUID actor);
    Hero hero(boolean publicOnly);
    Hero saveHero(Hero hero, UUID actor);
    Contact contact();
    Contact saveContact(Contact contact, UUID actor);
    UUID addMessage(MessageInput input);
    MessagePage messages(int page, int size, String status);
    void resolveMessage(UUID id);
}
