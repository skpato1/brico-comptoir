package tn.bricocomptoir.privacy;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.sales.adapter.out.persistence.ArchiveCipher;
import static org.assertj.core.api.Assertions.*;
class ArchiveCipherTest {
    @Test void archivesAreRandomizedAuthenticatedAndBoundToAnOrder() {
        var cipher=new ArchiveCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        var first=cipher.encrypt("order-a","private-address");var second=cipher.encrypt("order-a","private-address");
        assertThat(first).isNotEqualTo(second);assertThat(cipher.decrypt("order-a",first)).isEqualTo("private-address");
        assertThatThrownBy(()->cipher.decrypt("order-b",first)).hasMessage("ARCHIVE_UNREADABLE");
        first[15]^=1;assertThatThrownBy(()->cipher.decrypt("order-a",first)).hasMessage("ARCHIVE_UNREADABLE");
    }
}
