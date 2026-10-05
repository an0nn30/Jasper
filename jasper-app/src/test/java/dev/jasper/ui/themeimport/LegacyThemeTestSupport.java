package dev.jasper.ui.themeimport;
import dev.jasper.app.themeimport.*;
import dev.jasper.app.appearance.TerminalPaletteAdapter;
import dev.jasper.ui.themeimport.ThemeParents;
import dev.jasper.ui.themeimport.EditorScheme;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.List;
import java.util.Map;

/** Owner-package bridge for runtime parity tests. */
public final class LegacyThemeTestSupport {
    private LegacyThemeTestSupport() {}
    public record Resolved(String json, Map<String, Color> colors, List<String> warnings) {}
    public static Resolved load(String label, String json) {
        var value = ThemeParents.load(label, json);
        return new Resolved(value.json(), value.colors(), value.warnings());
    }
    public static Resolved bundled(String file) {
        var value = ThemeParents.load(file.replaceFirst("^intellij/", ""));
        return new Resolved(value.json(), value.colors(), value.warnings());
    }
    public record Scheme(dev.jasper.ui.theme.TerminalColors palette, java.util.Map<dev.jasper.ui.theme.SyntaxRole,dev.jasper.ui.theme.SyntaxStyle> syntax) {}
    public static Scheme scheme(String name, boolean dark) { var value = EditorScheme.named(name, name, dark); return new Scheme(value.palette(), value.syntax()); }
    public static Palette palette(String name, boolean dark) { return TerminalPaletteAdapter.toTerminal(EditorScheme.named(name, name, dark).palette()); }
}
