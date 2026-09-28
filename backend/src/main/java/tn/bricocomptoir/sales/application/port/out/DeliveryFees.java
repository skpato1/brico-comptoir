package tn.bricocomptoir.sales.application.port.out;

import java.math.BigDecimal;
import tn.bricocomptoir.sales.domain.OrderModels.Address;

public interface DeliveryFees {
    BigDecimal forAddress(Address address);
}
