package tn.bricocomptoir.sales.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import tn.bricocomptoir.sales.domain.CartModels.Line;
import tn.bricocomptoir.sales.domain.OrderModels.*;

public final class OrderRules {
    private OrderRules() { }
    public static final Set<String> GOVERNORATES = Set.of("ARIANA", "BEJA", "BEN_AROUS", "BIZERTE",
            "GABES", "GAFSA", "JENDOUBA", "KAIROUAN", "KASSERINE", "KEBILI", "KEF", "MAHDIA",
            "MANOUBA", "MEDENINE", "MONASTIR", "NABEUL", "SFAX", "SIDI_BOUZID", "SILIANA",
            "SOUSSE", "TATAOUINE", "TOZEUR", "TUNIS", "ZAGHOUAN");

    public static Address address(Address input) {
        if (input == null) throw new CheckoutFailure("INVALID_CHECKOUT");
        String phone = text(input.phone(), 20).replace(" ", "");
        if (phone.startsWith("+216")) phone = phone.substring(4);
        String postal = text(input.postalCode(), 4);
        if (!phone.matches("[0-9]{8}") || !postal.matches("[0-9]{4}")
                || !GOVERNORATES.contains(input.governorate()) || !"TN".equals(input.country()))
            throw new CheckoutFailure("INVALID_CHECKOUT");
        return new Address(text(input.recipient(), 120), phone, text(input.street(), 300),
                text(input.city(), 100), postal, input.governorate(), "TN", email(input.email()));
    }

    public static List<Item> items(List<Item> input) {
        if (input == null || input.isEmpty() || input.size() > 100)
            throw new CheckoutFailure("INVALID_CHECKOUT");
        try {
            CartRules.normalize(input.stream().map(i -> new Line(i.kind(), i.offerId(), i.quantity())).toList());
            if (input.stream().anyMatch(i -> i.offerVersion() < 0 || i.parentVersion() < 0)
                    || input.stream().map(i -> i.kind() + ":" + i.offerId()).distinct().count() != input.size())
                throw new CheckoutFailure("INVALID_CHECKOUT");
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CheckoutFailure("INVALID_CHECKOUT");
        }
        return input.stream().sorted(Comparator.comparing(Item::kind)
                .thenComparing(i -> i.offerId().toString())).toList();
    }

    public static BigDecimal fee(String value) {
        try {
            BigDecimal result = new BigDecimal(value).setScale(3, java.math.RoundingMode.UNNECESSARY);
            if (result.signum() < 0 || result.compareTo(new BigDecimal("999999.999")) > 0)
                throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException invalid) { throw new CheckoutFailure("CHECKOUT_UNAVAILABLE"); }
    }

    public static Status transition(Status current, Status target, boolean manager) {
        if (current == target) {
            if (target != Status.CANCELLED && !manager) throw new CheckoutFailure("INVALID_TRANSITION");
            return current;
        }
        boolean valid = target == Status.CANCELLED
                ? current == Status.CONFIRMED || manager && current == Status.PREPARING
                : manager && (current == Status.CONFIRMED && target == Status.PREPARING
                    || current == Status.PREPARING && target == Status.SHIPPED
                    || current == Status.SHIPPED && target == Status.DELIVERED);
        if (!valid) throw new CheckoutFailure("INVALID_TRANSITION");
        return target;
    }

    /** Length-prefixed tokens avoid ambiguous delimiters in user-entered addresses/labels. */
    public static String hash(List<String> tokens) {
        StringBuilder canonical = new StringBuilder();
        for (String token : tokens) canonical.append(token.length()).append(':').append(token);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static List<String> addressTokens(Address a) {
        return List.of(a.recipient(), a.phone(), a.street(), a.city(), a.postalCode(), a.governorate(), a.country(), a.email()==null?"":a.email());
    }
    public static String email(String value) {
        if(value==null || value.isBlank()) return null;
        String email=value.trim().toLowerCase(java.util.Locale.ROOT);
        if(email.length()>254 || email.chars().anyMatch(Character::isISOControl) || !email.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+")) throw new CheckoutFailure("INVALID_CHECKOUT");
        return email;
    }
    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max
                || value.chars().anyMatch(Character::isISOControl)) throw new CheckoutFailure("INVALID_CHECKOUT");
        return value.trim();
    }
}
