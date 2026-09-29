package tn.bricocomptoir.media;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.media.domain.ImageRules;
import tn.bricocomptoir.media.domain.ProductImage;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageRulesTest {
    @Test void validatesTypeSizeDimensionsCapacityAndCompleteOrder() {
        ImageRules.source(100_000, 800, 600, "image/png", "image/png");
        assertThatThrownBy(() -> ImageRules.source(100_000, 800, 600, "image/jpeg", "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageRules.source(100_000, 100, 600, "image/png", "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageRules.source(100_000, 6000, 6000, "image/png", "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageRules.capacity(11, 2)).isInstanceOf(IllegalArgumentException.class);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), product = UUID.randomUUID();
        var images = List.of(image(a, product), image(b, product));
        ImageRules.order(images, List.of(b, a), b);
        ImageRules.orderIds(List.of(a, b), List.of(b, a), b);
        assertThatThrownBy(() -> ImageRules.orderIds(List.of(a, b), List.of(a, a), a))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageRules.order(images, List.of(a, a), a))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageRules.order(images, List.of(a, b), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private static ProductImage image(UUID id, UUID product) {
        return new ProductImage(id, product, "", "", "", "image/png", 1, "", 800, 600, 0, false, null);
    }
}
