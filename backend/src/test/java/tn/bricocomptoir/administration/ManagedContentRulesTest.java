package tn.bricocomptoir.administration;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.content.domain.ManagedContent.*;
import static org.assertj.core.api.Assertions.*;

class ManagedContentRulesTest {
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
    @Test void contactRejectsMarkupAndHoneypot() {
        assertThat(new Contact("", "", "", 0).checked().email()).isEmpty();
        assertThatThrownBy(() -> new Contact("<script>", "", "", 0).checked()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessageInput("Client", "client@example.invalid", "Sujet", "Bonjour", "bot").checked())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessageInput("Client", "client@example.invalid", "Sujet", "<script>", "").checked())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
