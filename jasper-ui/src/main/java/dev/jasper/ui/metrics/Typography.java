package dev.jasper.ui.metrics;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Platform family and user size resolved once, then applied consistently to named text roles. */
public final class Typography {
    public enum Role {
        HERO_TITLE(15, 600), DETAIL_TITLE(16, 600), WINDOW_TITLE(13, 600), PANEL_TITLE(13, 600),
        BODY(13, 400), BODY_STRONG(13, 500), LABEL(12.5f, 400), DESCRIPTION(12.5f, 400),
        CAPTION(11.5f, 400), SECTION_LABEL(11, 600), COUNT(11, 400), CHIP(9.5f, 400), MONO(12, 400), MONO_SMALL(11.5f, 400);
        private final float size;
        private final int weight;
        Role(float size, int weight) { this.size = size; this.weight = weight; }
    }
    private final String family;
    private final float bodySize;
    public Typography() { this(platformFamily(), 13); }
    public Typography(String family, float bodySize) {
        Objects.requireNonNull(family);
        if (family.isBlank() || !Float.isFinite(bodySize) || bodySize < 6 || bodySize > 96)
            throw new IllegalArgumentException("Invalid font family or body size");
        this.family = family; this.bodySize = bodySize;
    }
    public String family() { return family; }
    public float bodySize() { return bodySize; }
    public Font font(Role role) { return font(role, false); }
    /** Selected labels use medium weight. Size settings enlarge every role proportionally. */
    public Font font(Role role, boolean selected) {
        Objects.requireNonNull(role);
        String resolved = role == Role.MONO || role == Role.MONO_SMALL ? Font.MONOSPACED : family;
        int weight = selected && role == Role.LABEL ? 500 : role.weight;
        return new Font(resolved, Font.PLAIN, 1).deriveFont(Map.of(
            TextAttribute.SIZE, role.size * bodySize / 13f,
            TextAttribute.WEIGHT, weight == 600 ? TextAttribute.WEIGHT_SEMIBOLD : weight == 500 ? TextAttribute.WEIGHT_MEDIUM : TextAttribute.WEIGHT_REGULAR));
    }
    private static String platformFamily() {
        Set<String> installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> families = os.contains("mac") ? List.of("Helvetica Neue", "Helvetica", "Noto Sans", "DejaVu Sans")
            : os.contains("win") ? List.of("Segoe UI", "Tahoma", "Noto Sans", "DejaVu Sans")
            : List.of("Noto Sans", "Liberation Sans", "DejaVu Sans");
        return families.stream().filter(installed::contains).findFirst().orElse(Font.SANS_SERIF);
    }
}
