package tn.bricocomptoir.sales;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.sales.domain.CartModels.*;
import tn.bricocomptoir.sales.domain.CartRules;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartRulesTest {
    @Test void normalizesDuplicatesAndLimitsQuantities() {
        UUID sku = UUID.randomUUID();
        UUID pack = UUID.randomUUID();
        assertThat(CartRules.normalize(List.of(new Line(Kind.PRODUCT, sku, 2),
                new Line(Kind.PACK, pack, 1), new Line(Kind.PRODUCT, sku, 3))))
                .containsExactly(new Line(Kind.PRODUCT, sku, 5), new Line(Kind.PACK, pack, 1));
        assertThatThrownBy(() -> CartRules.normalize(List.of(new Line(Kind.PRODUCT, sku, 999),
                new Line(Kind.PRODUCT, sku, 1)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CartRules.normalize(List.of(new Line(Kind.PACK, pack, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        List<Line> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) tooMany.add(new Line(Kind.PRODUCT, UUID.randomUUID(), 1));
        assertThatThrownBy(() -> CartRules.normalize(tooMany)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void demandAddsSharedSkuAcrossIndividualAndMultiplePacks() {
        UUID shared = UUID.randomUUID();
        UUID tool = UUID.randomUUID();
        assertThat(CartRules.demand(List.of(
                new DemandLine(2, List.of(new SkuRequirement(shared, 1))),
                new DemandLine(3, List.of(new SkuRequirement(shared, 2), new SkuRequirement(tool, 1),
                        new SkuRequirement(shared, 1))),
                new DemandLine(1, List.of(new SkuRequirement(shared, 4))))))
                .containsEntry(shared, 15L).containsEntry(tool, 3L);
        assertThatThrownBy(() -> CartRules.demand(List.of(new DemandLine(Long.MAX_VALUE,
                List.of(new SkuRequirement(shared, 2)))))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void mergeIsCanonicalAndFingerprintDoesNotDependOnInputOrder() {
        UUID sku = UUID.randomUUID();
        UUID pack = UUID.randomUUID();
        List<Line> first = List.of(new Line(Kind.PACK, pack, 1), new Line(Kind.PRODUCT, sku, 2));
        List<Line> reordered = List.of(new Line(Kind.PRODUCT, sku, 2), new Line(Kind.PACK, pack, 1));
        assertThat(CartRules.fingerprint(first)).isEqualTo(CartRules.fingerprint(reordered));
        assertThat(CartRules.merge(first, List.of(new Line(Kind.PRODUCT, sku, 3))))
                .containsExactly(new Line(Kind.PRODUCT, sku, 5), new Line(Kind.PACK, pack, 1));
        assertThat(CartRules.fingerprint(first)).isNotEqualTo(CartRules.fingerprint(
                List.of(new Line(Kind.PRODUCT, sku, 3), new Line(Kind.PACK, pack, 1))));
    }
}
