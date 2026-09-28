package tn.bricocomptoir.sales.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CartModels {
    private CartModels() { }

    public enum Kind { PRODUCT, PACK }
    public record Line(Kind kind, UUID offerId, long quantity) { }
    public record SkuRequirement(UUID skuId, long quantity) { }
    public record DemandLine(long quantity, List<SkuRequirement> components) { }
    public record Cart(UUID customerId, long version, List<Line> lines) { }
    public record DisplayLine(Line line, String label, BigDecimal unitPriceTnd,
                              BigDecimal lineEstimateTnd, Long offerVersion, Long parentVersion) { }
    public record Shortage(UUID skuId, long required, long available) { }
    public record Quote(List<DisplayLine> items, BigDecimal subtotalEstimateTnd,
                        List<Shortage> shortages) { }
    public record View(Cart cart, Quote quote) { }
}
