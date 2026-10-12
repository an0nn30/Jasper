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
        HERO_TITLE(15, 600), DETAIL_TITLE(16, 600), WINDOW_TITLE(13, 600), PANEL_TITLE(13, 600), PANEL_DETAIL_TITLE(12.5f, 600), PANEL_DETAIL_LABEL(12, 400), PANEL_HEADER_TITLE(12.5f, 600), PANEL_HEADER_COUNT(11.5f, 400), PANEL_SEARCH(12,400), PANEL_ROW_SELECTED(12,500), PANEL_ROW_SECONDARY(13.25f,400), PANEL_DETAIL_MONO(11.65f,400),
        PICKER_COMPACT(10, 400), PICKER_CAPTION(9, 400),
        TITLE_CONTEXT(12, 400), TOOLBAR_LABEL(11.5f, 400), STATUS_TEXT(10, 400), STATUS_PATH(12, 400), RESULTS_HEADING(11, 400), PALETTE_HINT(11, 400),
        BODY(13, 400), BODY_STRONG(13, 500), LABEL(12.5f, 400), DESCRIPTION(12.5f, 400),
        SUPPORTING(11.5f, 400), CAPTION(11.5f, 400), SETTING_DESCRIPTION(11, 400), SECTION_LABEL(11, 600), COUNT(11, 400), CHIP(9.5f, 400), MONO(12, 400), MONO_SMALL(11.5f, 400), MONO_CAPTION(11, 400),
        ORDERED_ACTION(12, 400);
        private final float size;
        private final int weight;
        Role(float size, int weight) { this.size = size; this.weight = weight; }
    }
    private final String family;
    private final float bodySize;
    private final boolean platformDefault;
    public Typography() { this(13); }
    /** A size-only setting retains the platform role-family policy. */
    public Typography(float bodySize) { this(PlatformFamily.NAME, bodySize, true); }
    public Typography(String family, float bodySize) { this(family, bodySize, false); }
    private Typography(String family, float bodySize, boolean platformDefault) {
        Objects.requireNonNull(family);
        if (family.isBlank() || !Float.isFinite(bodySize) || bodySize < 6 || bodySize > 96)
            throw new IllegalArgumentException("Invalid font family or body size");
        this.family = family; this.bodySize = bodySize; this.platformDefault = platformDefault;
    }
    public String family() { return family; }
    public float bodySize() { return bodySize; }
    public Font font(Role role) { return font(role, false); }
    /** Selected labels use medium weight. Size settings enlarge every role proportionally. */
    public Font font(Role role, boolean selected) {
        Objects.requireNonNull(role);
        String resolved = role == Role.MONO || role == Role.MONO_SMALL || role == Role.MONO_CAPTION || role == Role.PANEL_DETAIL_MONO ? Font.MONOSPACED : family;
        int weight = selected && role == Role.LABEL ? 500 : role.weight;
        // The native macOS text face best matches regular desktop roles. Explicit user families bypass this policy.
        if (platformDefault && role != Role.BODY && weight < 600 && resolved.equals("Helvetica Neue") && AppleText.AVAILABLE)
            resolved = ".AppleSystemUIFont";
        // JBR maps Helvetica Neue's numeric semibold to regular. Use its installed medium face;
        // the small family-wide tracking adjustment is calibrated from original desktop title ink.
        if (weight == 600 && resolved.equals("Helvetica Neue") && HelveticaMedium.AVAILABLE)
            return HelveticaMedium.FONT.deriveFont(Map.of(TextAttribute.SIZE, role.size * bodySize / 13f,
                TextAttribute.TRACKING, platformDefault && role == Role.SECTION_LABEL ? .060f : .018f));
        return new Font(resolved, Font.PLAIN, 1).deriveFont(Map.of(
            TextAttribute.SIZE, role.size * bodySize / 13f,
            TextAttribute.WEIGHT, weight == 600 ? TextAttribute.WEIGHT_SEMIBOLD
                : weight == 500 ? TextAttribute.WEIGHT_MEDIUM : TextAttribute.WEIGHT_REGULAR));
    }
    /** Result selection changes receiving ink; every declared role keeps its family, size and weight. */
    public Font resultFont(Role role, boolean selected) {
        return font(role);
    }
    private static final class AppleText {
        static final boolean AVAILABLE = !new Font(".AppleSystemUIFont", Font.PLAIN, 1).getFamily(Locale.ROOT).equals(Font.DIALOG);
    }
    private static final class HelveticaMedium {
        static final Font FONT = new Font("HelveticaNeue-Medium", Font.PLAIN, 1);
        static final boolean AVAILABLE = FONT.getFamily(Locale.ROOT).equals("Helvetica Neue");
    }
    static int platformLookups() { return LOOKUPS.get(); }
    private static final java.util.concurrent.atomic.AtomicInteger LOOKUPS = new java.util.concurrent.atomic.AtomicInteger();
    /** Installed families are a process constant; each lookup creates native font objects macOS never releases. */
    private static final class PlatformFamily { static final String NAME = platformFamily(); }
    private static String platformFamily() {
        LOOKUPS.incrementAndGet();
        Set<String> installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> families = os.contains("mac") ? List.of("Helvetica Neue", "Helvetica", "Noto Sans", "DejaVu Sans")
            : os.contains("win") ? List.of("Segoe UI", "Tahoma", "Noto Sans", "DejaVu Sans")
            : List.of("Noto Sans", "Liberation Sans", "DejaVu Sans");
        return families.stream().filter(installed::contains).findFirst().orElse(Font.SANS_SERIF);
    }
}
