package tn.bricocomptoir.catalog.domain;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogRulesTest {
    @Test void tndHasExactThreeDecimalsAndPositiveBoundedRange() {
        assertThat(CatalogRules.price("12.3").toPlainString()).isEqualTo("12.300");
        assertThat(CatalogRules.price("0.001").toPlainString()).isEqualTo("0.001");
        for (String invalid : new String[]{"0", "-1.000", "1.0001", "1.0000", "1e2", "1000000000.000", "NaN"})
            assertThatThrownBy(() -> CatalogRules.price(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void publicationAndProductFactsAreBounded() {
        assertThat(CatalogRules.status("DRAFT")).isEqualTo("DRAFT");
        assertThatThrownBy(() -> CatalogRules.status("ACTIVE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogRules.attributes(Map.of("dimension", " ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogRules.sku("demo sku"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
