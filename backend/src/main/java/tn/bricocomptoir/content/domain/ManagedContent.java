package tn.bricocomptoir.content.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ManagedContent {
    private ManagedContent() { }

    public record Hero(long version, List<Slide> slides) {
        public Hero checked() {
            if (version < 0 || slides == null || slides.isEmpty() || slides.size() > 10)
                throw new IllegalArgumentException("Hero must have 1 to 10 slides");
            Set<UUID> ids = new HashSet<>();
            int visible = 0;
            for (Slide slide : slides) {
                slide.checked();
                if (!ids.add(slide.id())) throw new IllegalArgumentException("Duplicate slide");
                if (slide.visible()) visible++;
            }
            if (visible == 0) throw new IllegalArgumentException("At least one visible slide required");
            return new Hero(version, List.copyOf(slides));
        }
    }

    public record Slide(UUID id, boolean visible, String image, String label, String title,
                        String description, String alt, String link, String action, String detail) {
        private static final Pattern LINK = Pattern.compile("/(solutions|packs|catalogue|produits/[0-9a-fA-F-]{36}|packs/[0-9a-fA-F-]{36})");
        public Slide checked() {
            if (id == null || image == null || !Set.of("kits", "hardware", "tools").contains(image))
                throw new IllegalArgumentException("Unknown banner image");
            if (link == null || !LINK.matcher(link).matches())
                throw new IllegalArgumentException("Invalid internal banner link");
            plain(label, 80); plain(title, 140); plain(description, 400);
            plain(alt, 180); plain(action, 80); plain(detail, 160);
            return this;
        }
    }

    public record Contact(String email, String phone, String address, long version) {
        public Contact checked() {
            if (version < 0 || email == null || (!email.isBlank() && !EMAIL.matcher(email).matches())
                    || phone == null || phone.length() > 40 || address == null || address.length() > 240)
                throw new IllegalArgumentException("Invalid contact details");
            optionalPlain(email, 254); optionalPlain(phone, 40); optionalPlain(address, 240);
            return this;
        }
    }

    public record MessageInput(String name, String email, String subject, String body, String website) {
        public MessageInput checked() {
            if (website != null && !website.isBlank()) throw new IllegalArgumentException("Invalid form");
            if (email == null || !EMAIL.matcher(email).matches()) throw new IllegalArgumentException("Invalid email");
            plain(name, 120); plain(email, 254); plain(subject, 160); plain(body, 3000);
            return this;
        }
    }

    public record Message(UUID id, String name, String email, String subject, String body,
                          String status, java.time.OffsetDateTime createdAt) { }
    public record MessagePage(List<Message> items, int page, int size, long totalElements) { }

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$");
    private static void plain(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.indexOf('<') >= 0
                || value.indexOf('>') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Plain text required within field limits");
    }
    private static void optionalPlain(String value, int max) {
        if (!value.isEmpty()) plain(value, max);
    }
}
