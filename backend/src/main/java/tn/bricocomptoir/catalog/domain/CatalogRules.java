package tn.bricocomptoir.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.regex.Pattern;

public final class CatalogRules {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SKU = Pattern.compile("[A-Z0-9][A-Z0-9_-]*");
    private static final Pattern TND = Pattern.compile("[0-9]+(?:\\.[0-9]{1,3})?");

    private CatalogRules() { }

    public static String text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim()))
            throw new IllegalArgumentException("Invalid " + field);
        return value;
    }

    public static String optionalText(String value, int max, String field) {
        if (value == null) return "";
        if (value.length() > max || !value.equals(value.trim())) throw new IllegalArgumentException("Invalid " + field);
        return value;
    }

    public static String slug(String value) {
        if (value == null || value.length() > 80 || !SLUG.matcher(value).matches())
            throw new IllegalArgumentException("Invalid slug");
        return value;
    }

    public static String sku(String value) {
        if (value == null || value.length() > 64 || !SKU.matcher(value).matches())
            throw new IllegalArgumentException("Invalid SKU");
        return value;
    }

    public static BigDecimal price(String value) {
        if (value == null || !TND.matcher(value).matches())
            throw new IllegalArgumentException("Invalid TND price");
        try {
            BigDecimal amount = new BigDecimal(value).setScale(3, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0 || amount.precision() > 12) throw new IllegalArgumentException("Invalid TND price");
            return amount;
        } catch (NullPointerException | NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("Invalid TND price", invalid);
        }
    }

    public static Map<String, String> attributes(Map<String, String> values) {
        if (values == null) return Map.of();
        if (values.size() > 20) throw new IllegalArgumentException("Too many characteristics");
        values.forEach((key, value) -> {
            text(key, 60, "characteristic name");
            text(value, 250, "characteristic value");
        });
        return Map.copyOf(values);
    }

    public static String status(String value) {
        if (!"DRAFT".equals(value) && !"PUBLISHED".equals(value))
            throw new IllegalArgumentException("Invalid publication status");
        return value;
    }

    public static void version(long version) {
        if (version < 0) throw new IllegalArgumentException("Invalid version");
    }
}
