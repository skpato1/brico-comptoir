package tn.bricocomptoir.sales.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface OrderStock {
    void reserve(UUID orderId, Map<UUID, Long> demand);
    void release(UUID orderId);
    void consume(UUID orderId);
}
