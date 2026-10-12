package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.terminal.config.Palette;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.GlassLevels;
import dev.jasper.ui.theme.SchemeColors;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SemanticPalette;
import dev.jasper.ui.theme.SlateSteel;
import dev.jasper.ui.theme.TerminalScheme;
import java.awt.Color;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Package-local test access, excluded from production artifacts. */
public final class ThemeTestSupport {
    /** Slate Steel's variant on both routes. */
    public static dev.jasper.ui.theme.ThemePair pair(boolean dark) {
        var theme = generated(dark);
        return new dev.jasper.ui.theme.ThemePair(theme, theme);
    }
    public static boolean install(Theme theme) { return ThemeManager.install(theme); }
    /** Installs a generated theme, as ThemeController's installer does. */
    public static boolean install(GeneratedTheme theme) { return ThemeManager.install(theme); }

    /** Slate Steel's Dark or Light variant. */
    public static GeneratedTheme generated(boolean dark) { return SlateSteel.bundled().generated(dark); }
    /** The variant with its matching terminal scheme, as a window shows it. */
    public static ResolvedTheme resolved(boolean dark) { return resolved(dark, TerminalScheme.matching(dark)); }
    /** The variant with {@code scheme} in its terminal panes. */
    public static ResolvedTheme resolved(boolean dark, TerminalScheme scheme) { return ResolvedTheme.slateSteel(dark, scheme); }
    /** Chrome and workspace that differ, for hosting code that accepts any pair; panes take the workspace's terminal colours. */
    public static ResolvedTheme mixed(GeneratedTheme chrome, GeneratedTheme workspace) {
        return new ResolvedTheme(chrome, workspace,
            TerminalPaletteAdapter.toTerminal(new SchemeColors(workspace.terminal(), workspace.terminal().foreground())));
    }
    /** The variant's matching terminal scheme as a terminal palette. */
    public static Palette terminal(boolean dark) { return terminal(TerminalScheme.matching(dark)); }
    /** {@code scheme} as a terminal palette. */
    public static Palette terminal(TerminalScheme scheme) { return TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme)); }
    /** {@code role} in the variant. */
    public static Color color(boolean dark, SemanticColor role) { return SlateSteel.bundled().palette(dark).color(role); }
    /** A controller showing the variant with the matching scheme; constructing one also resets the installed look and fonts. */
    public static ThemeController controller(boolean dark) { return new ThemeController(dark, TerminalSchemeChoice.MATCH); }
    /** As {@link #controller(boolean)}, installing through {@code installer}. */
    public static ThemeController controller(boolean dark, Predicate<GeneratedTheme> installer) {
        return new ThemeController(dark, TerminalSchemeChoice.MATCH, installer);
    }
    /** Installs the variant's look and feel. */
    public static boolean install(boolean dark) { return ThemeManager.install(generated(dark)); }
    /** {@code base} with {@code roles} replaced: a theme unlike either variant for tests that need another one. */
    public static GeneratedTheme recolored(GeneratedTheme base, Map<SemanticColor, Color> roles) {
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, roles.getOrDefault(role, base.semantic().color(role)));
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(), base.terminal(),
            base.uiKeys(), base.icons(), base.glass(), new SemanticPalette(colors));
    }
    /** {@code base} with these FlatLaf UI keys added or replaced (values {@code #rrggbb} or FlatLaf values). */
    public static GeneratedTheme withUiKeys(GeneratedTheme base, Map<String, Object> keys) {
        var merged = new LinkedHashMap<>(base.uiKeys()); merged.putAll(keys);
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(), base.terminal(),
            merged, base.icons(), base.glass(), base.semantic());
    }
    /** {@code base} with its workspace glass replaced. */
    public static GeneratedTheme withGlass(GeneratedTheme base, GlassLevels glass) {
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(), base.terminal(),
            base.uiKeys(), base.icons(), glass, base.semantic());
    }
    /** {@code base} reporting {@code dark}. */
    public static GeneratedTheme withDark(GeneratedTheme base, boolean dark) {
        return new GeneratedTheme(base.name(), dark, base.author(), base.tokens(), base.syntax(), base.terminal(),
            base.uiKeys(), base.icons(), base.glass(), base.semantic());
    }

    /** {@code top} painted over opaque {@code bottom} as Java2D composites it: what an alpha-carrying role shows on a surface. */
    public static Color over(Color top, Color bottom) {
        var image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(bottom); g.fillRect(0, 0, 1, 1); g.setColor(top); g.fillRect(0, 0, 1, 1); } finally { g.dispose(); }
        return new Color(image.getRGB(0, 0), true);
    }

    /** A built-in theme by name, or Jasper Dark: what start-up picks for a name no theme has. */
    public static Theme named(String name) { return ThemeCatalog.builtIn().latest().find(name).orElse(Theme.DARK); }
}
