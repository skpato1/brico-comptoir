package tn.bricocomptoir.administration;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.content.domain.ManagedContent.*;
import static org.assertj.core.api.Assertions.*;

class ManagedContentRulesTest {
    @Test void socialLinksRequireSecureMatchingProfileHostsOrRemainEmpty() {
        assertThat(new SocialLinks(" https://www.facebook.com/brico.comptoir.tunisie ", "", 0)
                .checked().facebookUrl()).isEqualTo("https://www.facebook.com/brico.comptoir.tunisie");
        assertThat(new SocialLinks("", "https://instagram.com/bricocomptoir/", 0).checked().instagramUrl())
                .isEqualTo("https://instagram.com/bricocomptoir/");
        for (String url : List.of("http://facebook.com/shop", "https://facebook.com.evil.test/shop",
                "javascript:alert(1)", "https://user@facebook.com/shop", "https://facebook.com/"))
            assertThatThrownBy(() -> new SocialLinks(url, "", 0).checked())
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SocialLinks("", "https://facebook.com/shop", 0).checked())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SocialLinks("", "", -1).checked())
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bestSellersRequireThreeDistinctIdsOrEmptySelection() {
        var first=UUID.randomUUID();var second=UUID.randomUUID();var third=UUID.randomUUID();
        assertThat(new BestSellers(0,List.of(first,second,third)).checked().productIds())
                .containsExactly(first,second,third);
        assertThat(new BestSellers(0,List.of()).checked().productIds()).isEmpty();
        assertThatThrownBy(()->new BestSellers(0,List.of(first)).checked()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BestSellers(0,List.of(first,second,first)).checked()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BestSellers(-1,List.of()).checked()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void professionalFavoritesRequireFourDistinctIdsOrEmptySelection() {
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThat(new ProfessionalFavorites(0, ids).checked().productIds()).containsExactlyElementsOf(ids);
        assertThat(new ProfessionalFavorites(0, List.of()).checked().productIds()).isEmpty();
        assertThatThrownBy(() -> new ProfessionalFavorites(0, ids.subList(0, 3)).checked())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfessionalFavorites(0, List.of(ids.get(0), ids.get(1), ids.get(2), ids.get(0))).checked())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfessionalFavorites(-1, List.of()).checked())
                .isInstanceOf(IllegalArgumentException.class);
    }
    private Slide slide() {
        return new Slide(UUID.randomUUID(), true, "kits", "Kits", "Un projet", "Description",
                "Illustration de démonstration", "/catalogue", "Voir", "Note");
    }
    @Test void heroRequiresVisibleUniqueSlidesAndInternalLinks() {
        Slide valid = slide();
        assertThat(new Hero(0, List.of(valid)).checked().slides()).containsExactly(valid);
        assertThatThrownBy(() -> new Hero(0, List.of(valid, valid)).checked()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Hero(0, List.of(new Slide(valid.id(), false, valid.image(), valid.label(),
                valid.title(), valid.description(), valid.alt(), valid.link(), valid.action(), valid.detail()))).checked())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Hero(0, List.of(new Slide(valid.id(), true, valid.image(), valid.label(),
                valid.title(), valid.description(), valid.alt(), "https://example.invalid", valid.action(), valid.detail()))).checked())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Hero(0, List.of(new Slide(valid.id(), true, "original", valid.label(),
                valid.title(), valid.description(), valid.alt(), valid.link(), valid.action(), valid.detail()))).checked())
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void suppliedPromotionalImagesAreAllowedButArbitraryPathsAreRejected() {
        Slide valid = slide();
        for (String image : List.of("brico-online", "brico-projects", "brico-workshop")) {
            assertThat(new Slide(valid.id(), true, image, valid.label(), valid.title(),
                    valid.description(), valid.alt(), valid.link(), valid.action(), valid.detail()).checked().image())
                    .isEqualTo(image);
        }
        assertThatThrownBy(() -> new Slide(valid.id(), true, "../../private", valid.label(), valid.title(),
                valid.description(), valid.alt(), valid.link(), valid.action(), valid.detail()).checked())
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void contactRejectsMarkupAndHoneypot() {
        assertThat(new Contact("", "", "", 0).checked().email()).isEmpty();
        assertThatThrownBy(() -> new Contact("<script>", "", "", 0).checked()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessageInput("Client", "client@example.invalid", "Sujet", "Bonjour", "bot").checked())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessageInput("Client", "client@example.invalid", "Sujet", "<script>", "").checked())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
