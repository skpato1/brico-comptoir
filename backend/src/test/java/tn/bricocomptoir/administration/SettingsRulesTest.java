package tn.bricocomptoir.administration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.content.domain.HomeContent;
import tn.bricocomptoir.sales.domain.DeliverySettings;
import static org.assertj.core.api.Assertions.*;
class SettingsRulesTest {
    @Test void homeRequiresBoundedPlainTextAndNonnegativeVersion(){
        assertThat(new HomeContent(" titre ","suite","description","solution","description","produit","description",0).checked().title()).isEqualTo("titre");
        assertThatThrownBy(()->new HomeContent("<script>","suite","description","s","d","p","d",0).checked()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new HomeContent("x".repeat(101),"suite","description","s","d","p","d",0).checked()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void deliveryUsesExactTndAndRequiresKnownZonesWhenEnabled(){
        assertThat(new DeliverySettings(true,"7.125",Set.of("TUNIS"),0).checked().amountTnd()).isEqualTo("7.125");
        for(String amount: new String[]{"-1","0.0001","1000000","NaN"})
            assertThatThrownBy(()->new DeliverySettings(true,amount,Set.of("TUNIS"),0).checked()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DeliverySettings(true,"0",Set.of(),0).checked()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DeliverySettings(false,"0",Set.of("UNKNOWN"),0).checked()).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DeliverySettings(false,"0",Set.of(),0).checked().enabled()).isFalse();
    }
}
