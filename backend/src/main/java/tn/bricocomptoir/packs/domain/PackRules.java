package tn.bricocomptoir.packs.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import tn.bricocomptoir.packs.domain.PackModels.Component;

public final class PackRules {
    private static final Pattern CODE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern TND = Pattern.compile("[0-9]+(?:\\.[0-9]{1,3})?");
    private PackRules() { }

    public static String code(String value) {
        if (value == null || value.length() > 80 || !CODE.matcher(value).matches())
            throw new IllegalArgumentException("Invalid pack code");
        return value;
    }
    public static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim()))
            throw new IllegalArgumentException("Invalid " + field);
        return value;
    }
    public static String optionalText(String value, int max, String field) {
        if (value == null) return "";
        if (value.length() > max || !value.equals(value.trim()))
            throw new IllegalArgumentException("Invalid " + field);
        return value;
    }
    public static String status(String value) {
        if (!"DRAFT".equals(value) && !"PUBLISHED".equals(value))
            throw new IllegalArgumentException("Invalid pack status");
        return value;
    }
    public static BigDecimal price(String value) {
        if (value == null || value.length() > 16 || !TND.matcher(value).matches())
            throw new IllegalArgumentException("Invalid TND price");
        BigDecimal amount = new BigDecimal(value).setScale(3, RoundingMode.UNNECESSARY);
        if (amount.signum() <= 0 || amount.precision() > 12)
            throw new IllegalArgumentException("Invalid TND price");
        return amount;
    }
    public static List<Component> normalize(List<Component> input) {
        if (input == null || input.isEmpty() || input.size() > 100)
            throw new IllegalArgumentException("Pack variant requires 1 to 100 component lines");
        Map<UUID, Long> bySku = new TreeMap<>(Comparator.naturalOrder());
        for (Component line : input) {
            if (line == null || line.variantId() == null || line.quantity() <= 0)
                throw new IllegalArgumentException("Each component needs a SKU and positive quantity");
            try { bySku.merge(line.variantId(), line.quantity(), Math::addExact); }
            catch (ArithmeticException overflow) { throw new IllegalArgumentException("Component quantity overflow", overflow); }
        }
        List<Component> components = new ArrayList<>();
        bySku.forEach((id, quantity) -> components.add(new Component(id, quantity)));
        return List.copyOf(components);
    }
    public static long availability(List<Component> components, Map<UUID, Long> availableBySku) {
        if (components == null || components.isEmpty()) throw new IllegalArgumentException("Empty composition");
        long available = Long.MAX_VALUE;
        for (Component component : normalize(components)) {
            long quantity = availableBySku.getOrDefault(component.variantId(), 0L);
            available = Math.min(available, Math.max(0, quantity) / component.quantity());
        }
        return available;
    }
    public static List<Component> requirements(List<Component> components, long packQuantity) {
        if (packQuantity <= 0) throw new IllegalArgumentException("Pack quantity must be positive");
        List<Component> result = new ArrayList<>();
        for (Component component : normalize(components)) {
            try { result.add(new Component(component.variantId(), Math.multiplyExact(component.quantity(), packQuantity))); }
            catch (ArithmeticException overflow) { throw new IllegalArgumentException("Component quantity overflow", overflow); }
        }
        return List.copyOf(result);
    }
}
