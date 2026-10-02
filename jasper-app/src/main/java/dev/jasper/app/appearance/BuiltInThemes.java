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

    static java.util.List<Theme> all() { return Indexed.THEMES; }
    private static final class Indexed {
        static final java.util.List<Theme> THEMES = load();
        private static java.util.List<Theme> load() {
            String index = ThemeManager.read("index.txt");
            if (index == null) throw new IllegalStateException("Missing bundled theme index");
            var classics = java.util.Map.of("jasper-light", Theme.JASPER_LIGHT, "jasper-dark", Theme.DARK,
                "intellij-light", Theme.INTELLIJ_LIGHT, "darcula", Theme.DARCULA);
            var themes = new java.util.ArrayList<Theme>(); var ids = new java.util.HashSet<String>();
            for (String file : index.lines().toList()) {
                if (!file.matches("[a-z0-9][a-z0-9-]*\\.toml") || !ids.add(file)) throw new IllegalStateException("Invalid theme index entry: " + file);
                String id = file.substring(0, file.length() - 5);
                themes.add(classics.containsKey(id) ? classics.get(id) : toml(id, file));
            }
            if (!themes.containsAll(classics.values())) throw new IllegalStateException("Theme index omits a classic theme");
            return java.util.List.copyOf(themes);
        }
    }
}
