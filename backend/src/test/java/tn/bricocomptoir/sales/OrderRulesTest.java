package tn.bricocomptoir.sales;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.sales.domain.*;
import tn.bricocomptoir.sales.domain.CartModels.Kind;
import tn.bricocomptoir.sales.domain.OrderModels.*;
import static org.assertj.core.api.Assertions.*;

class OrderRulesTest {
    @Test void addressIsTunisianAndFeesAreExactAndExplicit() {
        Address a = new Address(" Test ", "+216 20123456", "Rue test", "Tunis", "1000", "TUNIS", "TN");
        assertThat(OrderRules.address(a).phone()).isEqualTo("20123456");
        assertThat(OrderRules.address(a).recipient()).isEqualTo("Test");
        assertThat(OrderRules.fee("7.125")).isEqualByComparingTo("7.125");
        for (String fee : List.of("", "-1", "7.1234", "NaN"))
            assertThatThrownBy(() -> OrderRules.fee(fee)).isInstanceOf(CheckoutFailure.class);
        for (Address invalid : List.of(new Address("Test", "123", "Rue", "Ville", "1000", "TUNIS", "TN"),
                new Address("Test", "20123456", "Rue", "Ville", "123", "TUNIS", "TN"),
                new Address("Test", "20123456", "Rue", "Ville", "1000", "PARIS", "FR")))
            assertThatThrownBy(() -> OrderRules.address(invalid)).isInstanceOf(CheckoutFailure.class);
    }
    @Test void onlyValidTransitionsCanAffectStock() {
        assertThat(OrderRules.transition(Status.CONFIRMED, Status.CANCELLED, false)).isEqualTo(Status.CANCELLED);
        assertThat(OrderRules.transition(Status.PREPARING, Status.SHIPPED, true)).isEqualTo(Status.SHIPPED);
        assertThat(OrderRules.transition(Status.SHIPPED, Status.SHIPPED, true)).isEqualTo(Status.SHIPPED);
        assertThatThrownBy(() -> OrderRules.transition(Status.PREPARING, Status.CANCELLED, false)).isInstanceOf(CheckoutFailure.class);
        assertThatThrownBy(() -> OrderRules.transition(Status.SHIPPED, Status.CANCELLED, true)).isInstanceOf(CheckoutFailure.class);
        assertThatThrownBy(() -> OrderRules.transition(Status.CANCELLED, Status.SHIPPED, true)).isInstanceOf(CheckoutFailure.class);
    }
    @Test void versionsDuplicatesAndEmptyCheckoutAreValidated() {
        Item i = new Item(Kind.PRODUCT, UUID.randomUUID(), 1, 0, 0);
        assertThat(OrderRules.items(List.of(i))).containsExactly(i);
        assertThatThrownBy(() -> OrderRules.items(List.of(i, i))).isInstanceOf(CheckoutFailure.class);
        assertThatThrownBy(() -> OrderRules.items(List.of())).isInstanceOf(CheckoutFailure.class);
        assertThatThrownBy(() -> OrderRules.items(List.of(new Item(i.kind(), i.offerId(), 1, -1, 0)))).isInstanceOf(CheckoutFailure.class);
    }
    @Test void hashIsUnambiguousForUserText() {
        assertThat(OrderRules.hash(List.of("a:b", "c"))).isNotEqualTo(OrderRules.hash(List.of("a", "b:c")));
        assertThat(OrderRules.hash(List.of("test"))).hasSize(64).isEqualTo(OrderRules.hash(List.of("test")));
    }
}
