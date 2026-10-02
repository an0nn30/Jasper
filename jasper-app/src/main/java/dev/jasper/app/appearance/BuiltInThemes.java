package dev.jasper.app.appearance;

/** The themes Jasper ships: Jasper's own theme files and IntelliJ's classic themes, converted. */
final class BuiltInThemes {
    private BuiltInThemes() {}

    /** A Jasper theme file Jasper ships; it must stand alone. */
    static Theme toml(String id, String file) {
        String text = ThemeManager.read(file);
        if (text == null) throw new IllegalStateException("Missing bundled theme " + file);
        return Theme.of(id, new Theme.Bundled(file, ThemeResolver.resolve(ThemeToml.parse(text, file))));
    }

}
