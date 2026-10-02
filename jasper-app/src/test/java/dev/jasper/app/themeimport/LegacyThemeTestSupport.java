package dev.jasper.app.themeimport;

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
    public static Palette palette(String name, boolean dark) { return EditorScheme.named(name, name, dark).palette(); }
}
