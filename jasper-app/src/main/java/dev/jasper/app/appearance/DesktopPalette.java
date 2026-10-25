package dev.jasper.app.appearance;

import dev.jasper.app.platform.DesktopObservation;
import dev.jasper.app.platform.DesktopObservation.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import static dev.jasper.ui.theme.SemanticColor.*;

/** Pure interface palette resolution. Native content never replaces editor, terminal, syntax or glass values. */
public final class DesktopPalette {
    private DesktopPalette() { }
    public enum SourceMode { AUTO, GTK3, GTK4 }
    public record Result(GeneratedTheme theme, Optional<Source> source, String status) {
        public Result { Objects.requireNonNull(theme); Objects.requireNonNull(source); Objects.requireNonNull(status); }
    }

    /** Explicit modes never borrow another toolkit. Desktop names are colon-separated, case-insensitive tokens. */
    public static List<Source> preferredSources(SourceMode mode, String currentDesktop) {
        Objects.requireNonNull(mode);
        if (mode == SourceMode.GTK3) return List.of(Source.GTK3);
        if (mode == SourceMode.GTK4) return List.of(Source.GTK4);
        boolean gnome = currentDesktop != null && java.util.Arrays.stream(currentDesktop.split(":"))
            .anyMatch(token -> token.equalsIgnoreCase("GNOME"));
        return gnome ? List.of(Source.GTK4, Source.GTK3) : List.of(Source.GTK3, Source.GTK4);
    }

    /** Whether this observation supplies a readable opaque window for the requested appearance. */
    public static boolean usable(boolean dark, Palette palette) {
        return palette != null && palette.appearance() == (dark ? Polarity.DARK : Polarity.LIGHT)
            && pair(palette.colors().get(Pair.WINDOW), null)
                .filter(window -> ColorMath.contrast(window.background(), window.foreground()) >= 4.5).isPresent();
    }

    /** A complete selected-source WINDOW pair is the sole admission gate; optional pairs stay with that source. */
    public static Result resolve(boolean dark, SourceMode mode, String currentDesktop, DesktopObservation input) {
        Objects.requireNonNull(input);
        var base = JasperTheme.builtIn("slate-cobalt").orElseThrow().generated(dark);
        Palette selected = null;
        for (var source : preferredSources(mode, currentDesktop)) {
            Palette candidate = input.palettes().get(source);
            if (usable(dark, candidate)) {
                selected = candidate; break;
            }
        }
        var colors = copy(base);
        if (selected != null) map(colors, selected);
        boolean high = input.portal().highContrast().orElse(false);
        Color accent = input.portal().accent().filter(rgba -> rgba.alpha() == 1).map(DesktopPalette::color).orElse(colors.get(ACCENT));
        colors.put(ACCENT, accent);
        colors.put(ACCENT_HOVER, ColorMath.mix(colors.get(TEXT_PRIMARY), accent, .08));
        var overrides = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        boolean repaired = repair(base, colors, overrides, high);
        String status = selected == null ? "Desktop palette unavailable; using Slate Cobalt" : capability(selected) + " desktop palette";
        if (selected != null && !selected.reason().isBlank()) status += "; using last good palette; " + selected.reason();
        if (selected == null) for (Source source : preferredSources(mode, currentDesktop)) {
            Palette candidate = input.palettes().get(source);
            if (candidate != null) status += "; " + capability(candidate) + " unavailable"
                + (candidate.reason().isBlank() ? "" : ": " + candidate.reason());
        }
        if (!repaired) {
            // A native family that cannot share Jasper's inks is replaced together, never mixed with another toolkit.
            var bundled = copy(base);
            for (var role : SemanticColor.values()) if (neutral(role)) colors.put(role, bundled.get(role));
            overrides.clear();
            repaired = repair(base, colors, overrides, high);
            status += "; bundled neutral fallback";
        }
        if (!repaired && high) {
            Color background = dark ? Color.BLACK : Color.WHITE, ink = dark ? Color.WHITE : Color.BLACK;
            for (var role : SemanticColor.values()) {
                if (neutral(role)) colors.put(role, surface(role) ? background : ink);
            }
            // The scrim is not an enabled-text surface; retain the bundled overlay.
            colors.put(SURFACE_SCRIM, base.semantic().color(SURFACE_SCRIM));
            colors.put(HOVER_OVERLAY, background);
            for (var role : DerivedColor.values()) if (role.overridable()) overrides.put(role,
                role.key().startsWith("surface.") || role == DerivedColor.WARNING_GROUND ? background : ink);
            repaired = repair(base, colors, overrides, true);
            status += "; high contrast neutral fallback";
        }
        var theme = theme(base, colors, overrides);
        var failures = DesktopPaintPairs.validate(theme, high);
        if (!failures.isEmpty()) throw new IllegalStateException("Unrepresentable desktop paint pairs: " + failures);
        return new Result(theme, Optional.ofNullable(selected).map(Palette::source), status);
    }

    private static String capability(Palette palette) {
        String label = palette.source() == Source.GTK3 ? "GTK 3"
            : palette.implementation().equalsIgnoreCase("libadwaita") ? "libadwaita" : "GTK 4";
        return label + (palette.version().isBlank() ? "" : " " + palette.version());
    }

    private record OpaquePair(Color background, Color foreground) { }
    private static Optional<OpaquePair> pair(Colors value, Color parent) {
        if (value == null || value.background().alpha() < 1 && parent == null) return Optional.empty();
        Color background = color(value.background());
        if (background.getAlpha() < 255) background = DesktopPaintPairs.flat(background, parent);
        return Optional.of(new OpaquePair(background, DesktopPaintPairs.flat(color(value.foreground()), background)));
    }
    private static Color color(Rgba value) {
        return new Color((int) Math.round(value.red() * 255), (int) Math.round(value.green() * 255),
            (int) Math.round(value.blue() * 255), (int) Math.round(value.alpha() * 255));
    }

    private static void map(EnumMap<SemanticColor, Color> colors, Palette selected) {
        var window = pair(selected.colors().get(Pair.WINDOW), null).orElseThrow();
        Color background = window.background(), ink = window.foreground();
        for (var role : List.of(SURFACE_APP, SURFACE_STATUS, SURFACE_PANEL, SURFACE_TERMINAL, SURFACE_TABSTRIP)) colors.put(role, background);
        for (var role : List.of(TEXT_PRIMARY, TEXT_SECONDARY, TEXT_TERTIARY, TEXT_PLACEHOLDER, TEXT_STRONG_SECONDARY, TEXT_FIELD_RAISED)) colors.put(role, ink);
        colors.put(HOVER_OVERLAY, ink);
        // Related surfaces are the established light tint recipes of their accepted parent.
        for (var role : List.of(SURFACE_PANEL_RAISED, SURFACE_CARD, SURFACE_CONTROL, BUTTON_SECONDARY_BG, SURFACE_EMPTY_TILE, SEGMENT_TRACK))
            colors.put(role, ColorMath.mix(ink, background, .03));
        for (var role : List.of(SURFACE_CONTROL_HOVER, BUTTON_SECONDARY_BG_HOVER, SURFACE_CONTROL_SELECTED, SEGMENT_SELECTED_BG))
            colors.put(role, ColorMath.mix(ink, background, .08));
        accept(colors, selected, Pair.VIEW, background, ink, SURFACE_INPUT, SURFACE_FIELD_RAISED);
        accept(colors, selected, Pair.SIDEBAR, background, ink, SURFACE_SIDEBAR);
        accept(colors, selected, Pair.DIALOG, background, ink, SURFACE_DIALOG);
        accept(colors, selected, Pair.POPOVER, background, ink, SURFACE_MENU);
        // Each optional family's default is its accepted WINDOW parent, not a second native source.
        var header = pair(selected.colors().get(Pair.HEADER), background).filter(p -> ColorMath.contrast(p.background(), p.foreground()) >= 4.5)
            .orElse(window);
        colors.put(SURFACE_TITLEBAR, header.background()); colors.put(TEXT_TITLEBAR, header.foreground());
        pair(selected.colors().get(Pair.SELECTION), background)
            .filter(p -> ColorMath.contrast(p.background(), p.foreground()) >= 4.5)
            .ifPresent(p -> colors.put(ACCENT, p.background()));
        var selection = pair(selected.colors().get(Pair.SELECTION), background)
            .filter(p -> ColorMath.contrast(p.background(), p.foreground()) >= 4.5 && ColorMath.contrast(p.background(), ink) >= 4.5);
        if (selection.isPresent()) {
            colors.put(SELECTION_BG, selection.get().background());
            colors.put(TEXT_ONSELECTION, selection.get().foreground()); colors.put(TEXT_ONSELECTION_SECONDARY, selection.get().foreground());
        } else {
            colors.put(SELECTION_BG, ColorMath.mix(colors.get(ACCENT), background, .14));
            colors.put(TEXT_ONSELECTION, ink); colors.put(TEXT_ONSELECTION_SECONDARY, ink);
        }
    }

    private static void accept(EnumMap<SemanticColor, Color> colors, Palette palette, Pair pair, Color parent, Color ink, SemanticColor... roles) {
        Color accepted = pair(palette.colors().get(pair), parent).filter(p -> ColorMath.contrast(p.background(), ink) >= 4.5)
            .map(OpaquePair::background).orElse(parent);
        for (var role : roles) colors.put(role, accepted);
    }

    private static boolean repair(GeneratedTheme base, EnumMap<SemanticColor, Color> colors, EnumMap<DerivedColor, Color> overrides, boolean high) {
        double minimum = high ? 7 : 4.5;
        var requirements = DesktopPaintPairs.all();
        // TextFields paints selected text with text.primary; list selection uses the shared on-selection ink.
        if (ColorMath.contrast(colors.get(SELECTION_BG), colors.get(TEXT_PRIMARY)) < minimum) {
            Color fill = ColorMath.mix(colors.get(ACCENT), colors.get(SURFACE_APP), .14);
            colors.put(SELECTION_BG, DesktopContrast.repair(fill, colors.get(TEXT_PRIMARY), minimum).orElse(colors.get(SURFACE_APP)));
            colors.put(TEXT_ONSELECTION, colors.get(TEXT_PRIMARY));
            colors.put(TEXT_ONSELECTION_SECONDARY, colors.get(TEXT_PRIMARY));
        }
        // Searchable popup fields inherit FlatLaf's wildcard text selection. Give that UI paint its own fill;
        // the authored terminal/editor selections remain content values, independent of interface contrast.
        overrides.put(DerivedColor.SURFACE_TEXT_SELECTION, colors.get(SELECTION_BG));
        // Iteration accounts for backgrounds derived from inks (tinted tags, notice fills and the classic gradient).
        for (int pass = 0; pass < 5; pass++) {
            // The same switch knob stands on off, on and pending tracks. Repair the track family together;
            // repairing its ink against three incompatible authored tracks cannot find a shared candidate.
            Color knob = SurfaceContrast.blackOrWhite(colors.get(TEXT_PRIMARY));
            colors.put(TOGGLE_KNOB, knob);
            var off = DesktopContrast.repair(colors.get(TOGGLE_TRACK_OFF),
                List.of(knob, colors.get(SURFACE_APP), colors.get(SURFACE_DIALOG), colors.get(SURFACE_CARD)), 3);
            if (off.isEmpty()) return false;
            colors.put(TOGGLE_TRACK_OFF, off.get());
            var accent = DesktopContrast.repair(colors.get(ACCENT), candidate ->
                ColorMath.contrast(candidate, SurfaceContrast.blackOrWhite(candidate)) >= minimum && ColorMath.contrast(candidate, knob) >= 3);
            if (accent.isEmpty()) return false;
            Color onAccent = SurfaceContrast.blackOrWhite(accent.get());
            colors.put(ACCENT, accent.get());
            colors.put(ACCENT_HOVER, DesktopContrast.repair(colors.get(ACCENT_HOVER), onAccent, minimum).orElse(accent.get()));
            colors.put(TEXT_ONACCENT, onAccent); colors.put(TEXT_ONACCENT_SECONDARY, onAccent);
            GeneratedTheme current = theme(base, colors, overrides);
            for (var ink : SemanticColor.values()) {
                var uses = requirements.stream().filter(requirement -> requirement.ink() == ink
                    && requirement.needsRoleRepair(high)).toList();
                if (uses.isEmpty() || ink == TEXT_ONACCENT || ink == TEXT_ONACCENT_SECONDARY || ink == TOGGLE_KNOB) continue;
                var snapshot = current;
                var backgrounds = uses.stream().map(use -> use.background(snapshot)).toList();
                var repaired = DesktopContrast.repair(DesktopPaintPairs.flat(colors.get(ink), colors.get(SURFACE_APP)), candidate -> {
                    for (int i = 0; i < uses.size(); i++)
                        if (ColorMath.contrast(candidate, backgrounds.get(i)) < uses.get(i).minimum(high)) return false;
                    return true;
                });
                if (repaired.isEmpty()) return false;
                colors.put(ink, repaired.get());
            }
            for (var hue : List.of(HUE_BLUE, HUE_GREEN, HUE_AMBER, HUE_PURPLE, HUE_NEUTRAL))
                colors.put(SemanticColor.of(hue.key() + ".surface").orElseThrow(), colors.get(hue));
            // All rings use one final opaque paint; fail the family if those parents cannot share it.
            List<Color> ringParents = List.of(colors.get(SURFACE_APP), colors.get(SURFACE_DIALOG), colors.get(SURFACE_CARD),
                colors.get(SURFACE_INPUT), colors.get(SURFACE_PANEL));
            for (var role : List.of(DerivedColor.FOCUS_HALO, DerivedColor.ERROR_HALO)) {
                var repaired = DesktopContrast.repair(colors.get(role == DerivedColor.FOCUS_HALO ? BORDER_FOCUS : BORDER_ERROR), ringParents, 3);
                if (repaired.isEmpty()) return false;
                overrides.put(role, repaired.get());
            }
            current = theme(base, colors, overrides);
            // Derived surface overrides can retain the family's ink even when the classic recipe lowers its contrast.
            for (var role : DerivedColor.values()) if (role.overridable() && role.key().startsWith("surface.")) {
                var inks = new ArrayList<Color>();
                for (var requirement : requirements) if (requirement.surface() == role && requirement.text()
                        && requirement.ink() != DerivedColor.ACCENT_EDGE
                        && requirement.needsRoleRepair(high))
                    inks.add(requirement.ink().color(current));
                if (!inks.isEmpty()) {
                    var repaired = DesktopContrast.repair(role.color(current), inks, minimum);
                    if (repaired.isEmpty()) return false;
                    overrides.put(role, repaired.get());
                }
            }
            var safeAccent = DesktopContrast.repair(colors.get(ACCENT), candidate -> {
                var trial = new EnumMap<>(colors);
                trial.put(ACCENT, candidate);
                Color on = SurfaceContrast.blackOrWhite(candidate);
                if (ColorMath.contrast(on, candidate) < minimum || ColorMath.contrast(knob, candidate) < 3) return false;
                trial.put(TEXT_ONACCENT, on); trial.put(TEXT_ONACCENT_SECONDARY, on);
                var tested = theme(base, trial, overrides);
                return requirements.stream().filter(p -> p.ink() == ACCENT || p.ink() == DerivedColor.ACCENT_EDGE)
                    .allMatch(p -> ColorMath.contrast(p.foreground(tested), p.background(tested)) >= p.minimum(high));
            });
            if (safeAccent.isEmpty()) return false;
            colors.put(ACCENT, safeAccent.get());
            colors.put(TEXT_ONACCENT, SurfaceContrast.blackOrWhite(safeAccent.get()));
            colors.put(TEXT_ONACCENT_SECONDARY, colors.get(TEXT_ONACCENT));
            if (DesktopPaintPairs.validate(theme(base, colors, overrides), high).isEmpty()) return true;
        }
        return false;
    }

    private static EnumMap<SemanticColor, Color> copy(GeneratedTheme theme) {
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, theme.semantic().color(role));
        return colors;
    }
    private static GeneratedTheme theme(GeneratedTheme base, Map<SemanticColor, Color> colors, Map<DerivedColor, Color> overrides) {
        return new GeneratedTheme("Desktop colors", base.dark(), base.author(), new SemanticPalette(colors), base.terminal(),
            base.editor(), base.syntax(), base.glass(), overrides);
    }
    private static boolean surface(SemanticColor role) {
        return role.key().startsWith("surface.") || role == BUTTON_SECONDARY_BG || role == BUTTON_SECONDARY_BG_HOVER
            || role == SEGMENT_TRACK || role == SEGMENT_SELECTED_BG || role == TOGGLE_TRACK_OFF || role == SELECTION_BG;
    }
    private static boolean neutral(SemanticColor role) {
        return surface(role) || role.key().startsWith("border.") || role.key().startsWith("text.") && role != TEXT_ONACCENT && role != TEXT_ONACCENT_SECONDARY
            || role == HOVER_OVERLAY || role == TOGGLE_KNOB || role == BUTTON_SECONDARY_BORDER || role == TAB_INDICATOR || role == TAB_INDICATOR_INACTIVE;
    }
}
