package tn.bricocomptoir.sales.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HexFormat;
import tn.bricocomptoir.sales.domain.CartModels.*;

public final class CartRules {
    private CartRules() { }
    private static final Comparator<Line> ORDER = Comparator.comparing(Line::kind)
            .thenComparing(line -> line.offerId().toString());

    public static List<Line> normalize(List<Line> input) {
        return normalize(input, 100);
    }

    private static List<Line> normalize(List<Line> input, int maxRawLines) {
        if (input == null || input.size() > maxRawLines) throw new IllegalArgumentException("Cart has too many lines");
        Map<Key, Long> quantities = new LinkedHashMap<>();
        for (Line line : input) {
            if (line == null || line.kind() == null || line.offerId() == null
                    || line.quantity() < 1 || line.quantity() > 999)
                throw new IllegalArgumentException("Invalid cart line");
            Key key = new Key(line.kind(), line.offerId());
            long total;
            try { total = Math.addExact(quantities.getOrDefault(key, 0L), line.quantity()); }
            catch (ArithmeticException overflow) { throw new IllegalArgumentException("Cart quantity overflow"); }
            if (total > 999) throw new IllegalArgumentException("Cart quantity exceeds 999");
            quantities.put(key, total);
        }
        if (quantities.size() > 100) throw new IllegalArgumentException("Cart has too many lines");
        return quantities.entrySet().stream()
                .map(entry -> new Line(entry.getKey().kind(), entry.getKey().offerId(), entry.getValue()))
                .sorted(ORDER).toList();
    }

    public static List<Line> merge(List<Line> existing, List<Line> incoming) {
        List<Line> combined = new ArrayList<>(normalize(existing));
        combined.addAll(normalize(incoming));
        return normalize(combined, 200);
    }

    public static Map<UUID, Long> demand(List<DemandLine> lines) {
        Map<UUID, Long> total = new LinkedHashMap<>();
        for (DemandLine line : lines) {
            if (line == null || line.quantity() < 1 || line.components() == null)
                throw new IllegalArgumentException("Invalid demand line");
            for (SkuRequirement component : line.components()) {
                if (component == null || component.skuId() == null || component.quantity() < 1)
                    throw new IllegalArgumentException("Invalid SKU requirement");
                try {
                    long required = Math.multiplyExact(line.quantity(), component.quantity());
                    total.merge(component.skuId(), required, Math::addExact);
                } catch (ArithmeticException overflow) {
                    throw new IllegalArgumentException("SKU demand overflow");
                }
            }
        }
        return Map.copyOf(total);
    }

    public static String fingerprint(List<Line> input) {
        StringBuilder canonical = new StringBuilder();
        for (Line line : normalize(input)) canonical.append(line.kind()).append(':')
                .append(line.offerId()).append(':').append(line.quantity()).append(';');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record Key(Kind kind, UUID offerId) { }
}
