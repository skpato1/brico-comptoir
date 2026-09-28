package tn.bricocomptoir.sales.application.port.out;

import java.util.UUID;

public interface StockLookup {
    long available(UUID skuId);
}
