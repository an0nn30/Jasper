package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;

/** The themes Jasper ships: Jasper's own theme files and IntelliJ's classic themes, converted. */
final class BuiltInThemes {
    private BuiltInThemes() {}

    /** An IntelliJ theme file, converted, with the terminal palette of the editor scheme it names. */
    static Theme intellij(String id, String file, String scheme) {
        ThemeLoader.Resolved resolved = ThemeManager.loadBuiltIn(file);
        return intellij(id, file, resolved, EditorScheme.named(scheme, scheme, resolved.dark()).palette());
    }

    /** A Jasper theme file Jasper ships; it must stand alone. */
    static Theme toml(String id, String file) {
        String text = ThemeManager.read(file);
        if (text == null) throw new IllegalStateException("Missing bundled theme " + file);
        return Theme.of(id, new Theme.Bundled(file, ThemeResolver.resolve(ThemeToml.parse(text, file))));
    }

    private static Theme intellij(String id, String file, ThemeLoader.Resolved resolved, Palette palette) {
        return Theme.of(id, new Theme.Bundled(file, IntellijConversion.convert(resolved, palette, file)));
    }
}
