package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;

/** The themes Jasper ships: Jasper's own theme files and IntelliJ's classic themes, converted. */
final class BuiltInThemes {
    /** Jasper Dark's terminal palette until Task 6 moves it into {@code jasper-dark.toml}. */
    static final Palette GRAPHITE = new Palette(new Color(0xd5dee5), new Color(0x191c20), new Color(0xd5dee5),
        new Color(0x35434e), Palette.jasperDark().ansi());

    private BuiltInThemes() {}

    /** An IntelliJ theme file, converted, with the terminal palette of the editor scheme it names. */
    static Theme intellij(String id, String file, String scheme) {
        ThemeLoader.Resolved resolved = ThemeManager.loadBuiltIn(file);
        return intellij(id, file, resolved, EditorScheme.named(scheme, scheme, resolved.dark()).palette());
    }

    /** An IntelliJ theme file, converted, with {@code palette} as its terminal palette. */
    static Theme intellij(String id, String file, Palette palette) {
        return intellij(id, file, ThemeManager.loadBuiltIn(file), palette);
    }

    private static Theme intellij(String id, String file, ThemeLoader.Resolved resolved, Palette palette) {
        return Theme.of(id, new Theme.Bundled(file, IntellijConversion.convert(resolved, palette, file)));
    }
}
