package dev.jasper.ui.metrics;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A type scale's roles drawn in a face set with the user's font settings, applied consistently to named text roles: the
 * platform family (resolved once per process) unless the user names one, at the scale's body size unless the user sets
 * one.
 */
public final class Typography {
    public enum Role {
        PROMPT_TITLE(15, 600), DETAIL_TITLE(16, 600), WINDOW_TITLE(13, 600), PANEL_TITLE(13, 600), PANEL_DETAIL_TITLE(12.5f, 600), PANEL_DETAIL_LABEL(12, 400), PANEL_HEADER_TITLE(12.5f, 600), PANEL_HEADER_COUNT(11.5f, 400), PANEL_SEARCH(12,400), PANEL_ROW_SELECTED(12,500), PANEL_ROW_SECONDARY(13.25f,400), PANEL_DETAIL_MONO(11.65f,400),
        PANEL_EMPTY_DESCRIPTION(12, 400), PANEL_ROW_CHIP(10, 400),
        PICKER_COMPACT(10, 400), PICKER_CAPTION(9, 400),
        TITLE_CONTEXT(12, 400), TOOLBAR_LABEL(11.5f, 400), STATUS_TEXT(10, 400), STATUS_PATH(12, 400), RESULTS_HEADING(11, 400), PALETTE_HINT(11, 400),
        BODY(13, 400), BODY_STRONG(13, 500), LABEL(12.5f, 400), DESCRIPTION(12.5f, 400),
        SETTINGS_BODY(13, 400), SETTINGS_PREVIEW(12, 400),
        SUPPORTING(11.5f, 400), CAPTION(11.5f, 400), SETTING_DESCRIPTION(11, 400), SECTION_LABEL(11, 600), COUNT(11, 400), CHIP(9.5f, 400), MONO(12, 400), MONO_SMALL(11.5f, 400), MONO_CAPTION(11, 400),
        ORDERED_ACTION(12, 400),
        // The Run window's headings and data text (plan 8a): the mocks' 11 px semibold and 0.87 em data face.
        RUN_HEADING(12, 600), RUN_DETAIL(11.5f, 400);
        /** The role's size and weight in {@link TypeScale#DEFAULT}; every reading goes through a scale. */
        final float declaredSize;
        final int declaredWeight;
        Role(float size, int weight) { declaredSize = size; declaredWeight = weight; }
    }

    /**
     * The user's font settings as the host applied them.
     *
     * @param family a named UI family, or empty for the faces' own
     * @param size a body size in points, or empty for the scale's
     */
    public record UserFont(Optional<String> family, Optional<Float> size) {
        /** Neither setting. */
        public static final UserFont NONE = new UserFont(Optional.empty(), Optional.empty());
        /** Both values are required; an empty one means the setting is unset. */
        public UserFont { Objects.requireNonNull(family, "family"); Objects.requireNonNull(size, "size"); }
    }

    private final TypeScale scale;
    private final FaceSet faces;
    private final String family;
    private final float bodySize;
    private final boolean platformDefault;

    /** {@code scale}'s roles in {@code faces}, with {@code user}'s family and size where set. */
    public static Typography of(TypeScale scale, FaceSet faces, UserFont user) {
        Objects.requireNonNull(scale, "scale"); Objects.requireNonNull(faces, "faces"); Objects.requireNonNull(user, "user");
        // A named family never runs the installed-family lookup (platformLookups counts it).
        return new Typography(scale, faces, user.family().orElseGet(() -> PlatformFamily.NAME), user.size().orElse(scale.body()),
            user.family().isEmpty());
    }

    private Typography(TypeScale scale, FaceSet faces, String family, float bodySize, boolean platformDefault) {
        Objects.requireNonNull(family);
        if (family.isBlank() || !Float.isFinite(bodySize) || bodySize < 6 || bodySize > 96)
            throw new IllegalArgumentException("Invalid font family or body size");
        this.scale = scale; this.faces = faces; this.family = family; this.bodySize = bodySize; this.platformDefault = platformDefault;
    }
    public String family() { return family; }
    public float bodySize() { return bodySize; }
    public Font font(Role role) { return font(role, false); }
    /** Selected labels use medium weight. Size settings enlarge every role proportionally. */
    public Font font(Role role, boolean selected) { return font(role, selected, null); }
    /**
     * {@code role}'s font for {@code text}: {@link #font(Role)}, unless that is a kit's own face with no glyph for a
     * character of {@code text} (IBM Plex has no macOS modifier symbols and no CJK); the whole text then switches to the
     * role's platform face, then a logical family if needed ({@link Font#MONOSPACED} for a data role),
     * which uses the platform's installed fallback fonts glyph by glyph.
     */
    public Font font(Role role, String text) { return font(role, false, Objects.requireNonNull(text, "text")); }

    private Font font(Role role, boolean selected, String text) {
        Objects.requireNonNull(role);
        int weight = selected && role == Role.LABEL ? 500 : scale.weight(role);
        boolean data = role == Role.MONO || role == Role.MONO_SMALL || role == Role.MONO_CAPTION || role == Role.PANEL_DETAIL_MONO
            || role == Role.RUN_DETAIL;
        String face = data ? weight >= 500 ? faces.dataMedium() : faces.dataRegular() : platformDefault ? faces.uiFace(weight) : "";
        if (!face.isEmpty() && !face.equals(Font.MONOSPACED)) {
            // A kit's own face is a whole weight: drawn as it is, at the role's size. One that is not registered draws in
            // the platform face for this role (spec 5.9); nothing is cached, so registration order cannot matter.
            Font named = new Font(face, Font.PLAIN, 1);
            if (named.getFontName(Locale.ROOT).equals(face) && (text == null || named.canDisplayUpTo(text) < 0))
                return named.deriveFont(scale.size(role) * bodySize / scale.base());
        }
        String resolved = data ? Font.MONOSPACED : family;
        // The native macOS text face best matches regular desktop roles. Explicit user families bypass this policy.
        if (platformDefault && role != Role.BODY && weight < 600 && resolved.equals("Helvetica Neue") && AppleText.AVAILABLE)
            resolved = ".AppleSystemUIFont";
        // A physical platform family (for example Liberation Sans on Linux) can itself lack the glyph.
        // Logical families use Java's installed fallback fonts for symbols and CJK.
        if (text != null && new Font(resolved, Font.PLAIN, 1).canDisplayUpTo(text) >= 0)
            resolved = data ? Font.MONOSPACED : Font.SANS_SERIF;
        float size = scale.size(role) * bodySize / scale.base();
        // JBR maps Helvetica Neue's numeric semibold to regular. Use its installed medium face;
        // the small family-wide tracking adjustment is calibrated from original desktop title ink.
        if (weight == 600 && resolved.equals("Helvetica Neue") && HelveticaMedium.AVAILABLE)
            return HelveticaMedium.FONT.deriveFont(Map.of(TextAttribute.SIZE, size,
                TextAttribute.TRACKING, platformDefault && role == Role.SECTION_LABEL && scale.labelTracking() ? .060f : .018f));
        return new Font(resolved, Font.PLAIN, 1).deriveFont(Map.of(
            TextAttribute.SIZE, size,
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
