package tn.bricocomptoir.inventory.domain;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.inventory.domain.InventoryModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryRulesTest {
    @Test void physicalReservedAndAvailableRemainConsistent() {
        UUID id = UUID.randomUUID();
        Stock initial = new Stock(id, 5, 0);
        Stock reserved = InventoryRules.change(initial, 0, 3);
        assertThat(reserved).isEqualTo(new Stock(id, 5, 3));
        assertThat(reserved.available()).isEqualTo(2);
        assertThat(InventoryRules.change(reserved, -3, -3)).isEqualTo(new Stock(id, 2, 0));
        assertThat(InventoryRules.change(reserved, 0, -3)).isEqualTo(initial);
        assertThatThrownBy(() -> InventoryRules.change(reserved, -3, 0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InventoryRules.change(reserved, 0, 3))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InventoryRules.change(initial, -6, 0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InventoryRules.change(new Stock(id, Long.MAX_VALUE, 0), 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void duplicateSkusAggregateAndOverflowIsRejected() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<Line> normalized = InventoryRules.normalize(List.of(
                new Line(second, 1), new Line(first, 2), new Line(first, 3)));
        assertThat(normalized).containsExactlyInAnyOrder(new Line(first, 5), new Line(second, 1));
        assertThat(normalized.stream().map(Line::variantId).toList()).isSorted();
        assertThatThrownBy(() -> InventoryRules.normalize(List.of(new Line(first, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryRules.normalize(List.of(
                new Line(first, Long.MAX_VALUE), new Line(first, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void terminalTransitionsCannotReverseOrConsumeTwice() {
        assertThat(InventoryRules.transition(Status.ACTIVE, Status.RELEASED)).isEqualTo(Status.RELEASED);
        assertThat(InventoryRules.transition(Status.ACTIVE, Status.CONSUMED)).isEqualTo(Status.CONSUMED);
        assertThat(InventoryRules.transition(Status.CONSUMED, Status.CONSUMED)).isEqualTo(Status.CONSUMED);
        assertThatThrownBy(() -> InventoryRules.transition(Status.CONSUMED, Status.RELEASED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InventoryRules.transition(Status.RELEASED, Status.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
    }
}
