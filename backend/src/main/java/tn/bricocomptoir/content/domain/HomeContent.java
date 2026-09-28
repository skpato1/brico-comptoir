package tn.bricocomptoir.content.domain;

public record HomeContent(String title, String accent, String description, String solutionTitle,
                          String solutionDescription, String productTitle, String productDescription, long version) {
    public HomeContent checked() {
        if (version < 0) throw new IllegalArgumentException("Invalid version");
        return new HomeContent(text(title,100),text(accent,100),text(description,500),
                text(solutionTitle,120),text(solutionDescription,500),text(productTitle,120),
                text(productDescription,500),version);
    }
    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.indexOf('<') >= 0
                || value.indexOf('>') >= 0) throw new IllegalArgumentException("Plain text required within field limits");
        return value.trim();
    }
}
