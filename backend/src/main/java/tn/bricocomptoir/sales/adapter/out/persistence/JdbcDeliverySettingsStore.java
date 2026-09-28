package tn.bricocomptoir.sales.adapter.out.persistence;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.sales.domain.DeliverySettings;
import tn.bricocomptoir.sales.application.port.out.DeliverySettingsStore;
@Repository
public class JdbcDeliverySettingsStore implements DeliverySettingsStore {
    private final JdbcTemplate jdbc;
    private final String defaultAmount;
    private final Set<String> defaultZones;
    public JdbcDeliverySettingsStore(JdbcTemplate jdbc,@Value("${brico.checkout.delivery-fee-tnd:}") String amount,
                                    @Value("${brico.checkout.governorates:}") String zones) {
        this.jdbc=jdbc; defaultAmount=amount;
        defaultZones=Arrays.stream(zones.split(",")).map(String::trim).filter(s->!s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
    public DeliverySettings get() {
        return jdbc.queryForObject("SELECT * FROM sales_delivery_settings WHERE id=1",(r,n)->{
            var fee=r.getBigDecimal("fee_tnd");
            return new DeliverySettings(r.getBoolean("enabled"), fee==null ? defaultAmount : fee.toPlainString(),
                fee==null ? defaultZones : Set.copyOf(Arrays.asList((String[])r.getArray("governorates").getArray())),r.getLong("version"));
        });
    }
    public DeliverySettings save(DeliverySettings value,String actor) {
        if(jdbc.update("UPDATE sales_delivery_settings SET enabled=?,fee_tnd=?::numeric,governorates=string_to_array(?,','),version=version+1,updated_at=now(),updated_by=? WHERE id=1 AND version=?",
            value.enabled(),value.amountTnd(),String.join(",",value.governorates().stream().sorted().toList()),actor,value.version())!=1)
            throw new IllegalStateException("Version conflict");
        return get();
    }
}
