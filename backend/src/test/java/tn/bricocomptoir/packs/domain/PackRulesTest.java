package tn.bricocomptoir.packs.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.packs.domain.PackModels.Component;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackRulesTest {
    @Test void duplicatesAreAggregatedBeforeAvailabilityAndMultipleOccurrences() {
        UUID fix = UUID.randomUUID();
        UUID tool = UUID.randomUUID();
        List<Component> components = PackRules.normalize(List.of(
                new Component(fix, 2), new Component(tool, 1), new Component(fix, 1)));
        assertThat(components).containsExactlyInAnyOrder(new Component(fix, 3), new Component(tool, 1));
        assertThat(components.stream().map(Component::variantId).toList()).isSorted();
        assertThat(PackRules.availability(components, Map.of(fix, 8L, tool, 5L))).isEqualTo(2);
        assertThat(PackRules.availability(components, Map.of(fix, 8L, tool, 0L))).isZero();
        assertThat(PackRules.requirements(components, 2))
                .containsExactlyInAnyOrder(new Component(fix, 6), new Component(tool, 2));
    }

    @Test void invalidCompositionAndPriceAreRejected() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> PackRules.normalize(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackRules.normalize(List.of(new Component(id, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackRules.normalize(List.of(
                new Component(id, Long.MAX_VALUE), new Component(id, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackRules.requirements(List.of(new Component(id, Long.MAX_VALUE)), 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PackRules.price("12.3").toPlainString()).isEqualTo("12.300");
        for (String invalid : List.of("0", "-1", "1.0001", "1000000000.000"))
            assertThatThrownBy(() -> PackRules.price(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
}
