package dev.jasper.app.appearance;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.KitFaces;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.RunWindowPresentation;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import static dev.jasper.ui.theme.SemanticColor.*;

/**
 * Actual shared-role uses plus both shipped kits' pairs, resolved through their paint guards.
 * Read-only validation enumerates both kit definitions at call time; it never selects or caches the process kit.
 */
public final class DesktopPaintPairs {
    private DesktopPaintPairs() { }

    record Requirement(String name, ColorRole ink, ColorRole surface, PaintPair guard, boolean text, double tint) {
        Color background(GeneratedTheme theme) {
            if (surface == DerivedColor.SURFACE_TEXT_SELECTION) return UiColorKeys.textSelection(theme);
            Color under = flat(surface == DerivedColor.SURFACE_HEADER_ACTIVE && name.startsWith("dock/")
                ? SurfaceContrast.painted(theme, ChromePainter.FOCUSED_HEADER) : surface.color(theme), SURFACE_APP.color(theme));
            if (name.startsWith("tab-inactive/") || name.startsWith("tab-hover/")) {
                under = SurfaceContrast.apart(SURFACE_TABSTRIP.color(theme), SURFACE_TERMINAL.color(theme), SurfaceContrast.TAB_LIGHTNESS);
                if (name.startsWith("tab-hover/")) under = paintedComposite(ColorMath.opacity(HOVER_OVERLAY.color(theme), .08), under);
            }
            // LINK hover paints an 8% accent fill over its owner, including a Notice severity ground.
            if (ink == TEXT_LINK_HOVER && (name.startsWith("form/") || name.startsWith("notice/")))
                under = paintedComposite(ColorMath.opacity(ACCENT.color(theme), .08), under);
            if (tint > 0) {
                Color fill = SemanticColor.of(ink.key() + ".surface").map(role -> role.color(theme)).orElse(ink.color(theme));
                double share = (theme.dark() ? 17 : 13) / 100.0;
                under = paintedComposite(ColorMath.opacity(fill, share), under);
            }
            return under;
        }
        Color foreground(GeneratedTheme theme) {
            Color paint = ink == DerivedColor.FOCUS_HALO || ink == DerivedColor.ERROR_HALO
                ? halo(theme, ink == DerivedColor.ERROR_HALO, name.startsWith("classic."))
                : guard == null ? ink.color(theme)
                // Notices.link guards against the severity ground before Buttons paints its hover tint.
                : name.startsWith("notice/") && ink == TEXT_LINK_HOVER ? SurfaceContrast.painted(theme, guard)
                : SurfaceContrast.painted(theme, guard, background(theme));
            return flat(paint, background(theme));
        }
        double minimum(boolean highContrast) { return text ? highContrast ? 7 : 4.5 : 3; }
        /** A guard cannot replace role repair when it runs before the owner composites a different backdrop. */
        boolean needsRoleRepair(boolean highContrast) {
            return guard == null || minimum(highContrast) > guard.guard().floor()
                || name.startsWith("notice/") && ink == TEXT_LINK_HOVER;
        }
    }

    static List<Requirement> all() { return requirements(); }

    /** Named pairs failing the requested thresholds after derived colors, tinting and paint guards. */
    public static List<String> validate(GeneratedTheme theme, boolean highContrast) {
        var failures = new ArrayList<String>();
        for (var pair : all())
            if (ColorMath.contrast(pair.foreground(theme), pair.background(theme)) < pair.minimum(highContrast)) failures.add(pair.name());
        return List.copyOf(failures);
    }

    private static Color halo(GeneratedTheme theme, boolean error, boolean classic) {
        var kit = classic ? Kit.CLASSIC : Kit.DEFAULT;
        double share = kit.metrics().value(error ? DesignMetrics.Role.ERROR_HALO_PERCENT : theme.dark()
            ? DesignMetrics.Role.FOCUS_HALO_PERCENT_DARK : DesignMetrics.Role.FOCUS_HALO_PERCENT_LIGHT) / 100.0;
        return DerivedPalette.halo(theme, error, share);
    }

    static Color flat(Color top, Color parent) {
        return top.getAlpha() == 255 ? top : ColorMath.mix(top, parent, top.getAlpha() / 255.0);
    }

    /** Java2D's 8-bit SrcOver rounds each premultiplied contribution before adding them. */
    private static Color paintedComposite(Color top, Color parent) {
        int alpha = top.getAlpha();
        return new Color(blend(top.getRed(), parent.getRed(), alpha), blend(top.getGreen(), parent.getGreen(), alpha),
            blend(top.getBlue(), parent.getBlue(), alpha));
    }
    private static int blend(int top, int parent, int alpha) {
        return Math.min(255, (top * alpha + 127) / 255 + (parent * (255 - alpha) + 127) / 255);
    }

    private static List<Requirement> requirements() {
        var pairs = new ArrayList<Requirement>();
        // Labels, supporting captions and links stand on these concrete form/row containers (Labels, Buttons, Lists).
        texts(pairs, "form", List.of(SURFACE_APP, SURFACE_PANEL, SURFACE_DIALOG, SURFACE_CARD, SURFACE_SIDEBAR),
            TEXT_PRIMARY, TEXT_SECONDARY, TEXT_TERTIARY, TEXT_LINK, TEXT_LINK_HOVER, TEXT_ERROR, TEXT_SUCCESS);
        texts(pairs, "menu", List.of(SURFACE_MENU), TEXT_PRIMARY, TEXT_TERTIARY);
        texts(pairs, "status", List.of(SURFACE_STATUS), TEXT_PRIMARY, TEXT_SECONDARY, STATUS_SUCCESS, STATUS_WARNING, STATUS_DANGER, STATUS_IDLE);
        texts(pairs, "field", List.of(SURFACE_INPUT, SURFACE_FIELD_RAISED), TEXT_PRIMARY, TEXT_PLACEHOLDER, TEXT_FIELD_RAISED);
        texts(pairs, "button", List.of(BUTTON_SECONDARY_BG, BUTTON_SECONDARY_BG_HOVER, SURFACE_CONTROL_HOVER, SURFACE_CONTROL_SELECTED),
            TEXT_PRIMARY, TEXT_STRONG_SECONDARY, TEXT_SECONDARY);
        texts(pairs, "tab", List.of(SURFACE_TABSTRIP, SURFACE_TERMINAL), TEXT_PRIMARY, TEXT_SECONDARY);
        texts(pairs, "tab-inactive", List.of(SURFACE_TABSTRIP), TEXT_PRIMARY, TEXT_SECONDARY, TEXT_TERTIARY);
        texts(pairs, "tab-hover", List.of(SURFACE_TABSTRIP), TEXT_PRIMARY, TEXT_SECONDARY, TEXT_TERTIARY);
        texts(pairs, "classic-button-gradient", List.of(DerivedColor.BUTTON_END), TEXT_PRIMARY, TEXT_SECONDARY, TEXT_STRONG_SECONDARY);
        texts(pairs, "classic-keycap", List.of(DerivedColor.SURFACE_TABLE_HEADER), TEXT_PLACEHOLDER);
        texts(pairs, "classic-status-cell", List.of(SURFACE_APP), TEXT_STRONG_SECONDARY);
        addGuard(pairs, "classic-warning-cell", new PaintPair(STATUS_WARNING, DerivedColor.WARNING_GROUND, SurfaceContrast.Guard.TEXT));
        for (var surface : List.of(SURFACE_APP, SURFACE_STATUS, SURFACE_CONTROL_SELECTED, DerivedColor.SURFACE_HEADER_ACTIVE))
            addGuard(pairs, "classic-run", new PaintPair(DerivedColor.ACCENT_EDGE, surface, SurfaceContrast.Guard.TEXT));
        texts(pairs, "title", List.of(SURFACE_TITLEBAR), TEXT_TITLEBAR);
        texts(pairs, "action", List.of(ACCENT, ACCENT_HOVER), TEXT_ONACCENT, TEXT_ONACCENT_SECONDARY);
        texts(pairs, "inherited-text-selection", List.of(DerivedColor.SURFACE_TEXT_SELECTION), TEXT_PRIMARY);
        texts(pairs, "selection", List.of(SELECTION_BG), TEXT_ONSELECTION, TEXT_ONSELECTION_SECONDARY, TEXT_PRIMARY);
        texts(pairs, "inactive-list-selection", List.of(SURFACE_CONTROL_SELECTED), TEXT_ONSELECTION);
        texts(pairs, "segment", List.of(SEGMENT_SELECTED_BG), TEXT_PRIMARY);
        texts(pairs, "classic-gradient", List.of(DerivedColor.BUTTON_END), TEXT_PRIMARY, TEXT_STRONG_SECONDARY, TEXT_SECONDARY);
        texts(pairs, "classic-footer", List.of(DerivedColor.SURFACE_FOOTER), TEXT_PRIMARY, TEXT_SECONDARY);
        texts(pairs, "classic-row", List.of(DerivedColor.SURFACE_ROW_HOVER, DerivedColor.SURFACE_ZEBRA), TEXT_PRIMARY, TEXT_SECONDARY, TEXT_TERTIARY);
        texts(pairs, "classic-header", List.of(DerivedColor.SURFACE_HEADER, DerivedColor.SURFACE_HEADER_ACTIVE, DerivedColor.SURFACE_TABLE_HEADER),
            TEXT_PRIMARY, TEXT_SECONDARY, TEXT_STRONG_SECONDARY);
        for (var ground : List.of(DerivedColor.INFO_GROUND, DerivedColor.WARNING_GROUND, DerivedColor.DANGER_GROUND))
            for (var ink : List.of(TEXT_PRIMARY, TEXT_LINK, TEXT_LINK_HOVER))
                pairs.add(new Requirement("notice/" + ink.key() + "/" + ground.key(), ink, ground,
                    new PaintPair(ink, ground, SurfaceContrast.Guard.TEXT), true, 0));
        for (var pair : ChromePainter.DOCK_PAIRS) addGuard(pairs, "dock", pair);
        for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC)) {
            for (var pair : RunWindowPresentation.pairs(kit)) addGuard(pairs, "run-" + kit.id(), pair);
            for (var pair : RunWindowPresentation.edges(kit)) addGuard(pairs, "run-edge-" + kit.id(), pair);
        }
        for (var hue : List.of(HUE_BLUE, HUE_GREEN, HUE_AMBER, HUE_PURPLE, HUE_NEUTRAL)) {
            // Tags paint their surface hue at the kit tile opacity, including integer alpha rounding.
            pairs.add(new Requirement("tag/" + hue.key(), hue, SURFACE_CARD, null, true, .17));
            // InteractiveRows and its viewport are transparent. Chips inherit the mounted panel, app or
            // sidebar surface; their translucent tile must retain text contrast on those parents too.
            for (var surface : List.of(SURFACE_APP, SURFACE_PANEL, SURFACE_SIDEBAR))
                pairs.add(new Requirement("tag/" + hue.key() + "/" + surface.key(), hue, surface, null, true, .17));
        }
        for (var surface : List.of(ACCENT, TOGGLE_TRACK_OFF, STATUS_WARNING))
            pairs.add(new Requirement("switch/knob/" + surface.key(), TOGGLE_KNOB, surface, null, false, 0));
        pairs.add(new Requirement("switch/pending/knob", STATUS_WARNING, TOGGLE_KNOB, null, false, 0));
        for (var surface : List.of(SURFACE_DIALOG, SURFACE_APP, SURFACE_CARD))
            pairs.add(new Requirement("switch/track/" + surface.key(), TOGGLE_TRACK_OFF, surface, null, false, 0));
        for (var surface : List.of(SURFACE_TERMINAL, SURFACE_TABSTRIP))
            pairs.add(new Requirement("tab/indicator/" + surface.key(), TAB_INDICATOR, surface, null, false, 0));
        for (var boundary : List.of(BORDER_INPUT, BORDER_CONTROL, BUTTON_SECONDARY_BORDER, BORDER_FIELD_RAISED, BORDER_FIELD_RAISED_HOVER,
                BORDER_FOCUS, BORDER_ERROR))
            for (var surface : List.of(SURFACE_INPUT, SURFACE_CARD, SURFACE_DIALOG, SURFACE_APP, SURFACE_PANEL))
                pairs.add(new Requirement("control/" + boundary.key() + "/" + surface.key(), boundary, surface, null, false, 0));
        for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC)) {
            int index = 0;
            for (var pair : KitFaces.declared(kit)) {
                int number = index++;
                if (pair.guard().kind() != SurfaceContrast.Guard.Kind.CONTRAST || pair.guard().equals(SurfaceContrast.Guard.STRUCTURAL)) continue;
                pairs.add(new Requirement(kit.id() + "/" + number + "/" + pair.slot().key() + "/" + pair.against().key(),
                    pair.slot(), pair.against(), pair, pair.guard().equals(SurfaceContrast.Guard.TEXT), 0));
            }
        }
        for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC)) for (boolean error : new boolean[] {false, true})
            for (var surface : List.of(kit.colors().dialogSurface(), SURFACE_CARD, SURFACE_INPUT, SURFACE_PANEL))
                pairs.add(new Requirement(kit.id() + "." + (error ? "error" : "focus") + ".halo/" + surface.key(),
                    error ? DerivedColor.ERROR_HALO : DerivedColor.FOCUS_HALO, surface, null, false, 0));
        return List.copyOf(pairs);
    }

    private static void addGuard(List<Requirement> pairs, String name, PaintPair pair) {
        if (pair.guard().kind() != SurfaceContrast.Guard.Kind.CONTRAST || pair.guard().equals(SurfaceContrast.Guard.STRUCTURAL)) return;
        String key = name + "/" + pair.slot().key() + "/" + pair.against().key();
        if (pairs.stream().noneMatch(p -> p.name().equals(key)))
            pairs.add(new Requirement(key, pair.slot(), pair.against(), pair, pair.guard().equals(SurfaceContrast.Guard.TEXT), 0));
    }

    private static void texts(List<Requirement> pairs, String use, List<? extends ColorRole> surfaces, SemanticColor... inks) {
        for (var surface : surfaces) for (var ink : inks)
            pairs.add(new Requirement(use + "/" + ink.key() + "/" + surface.key(), ink, surface, null, true, 0));
    }
}
