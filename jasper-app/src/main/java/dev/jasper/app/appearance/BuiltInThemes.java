package dev.jasper.app.appearance;
import dev.jasper.ui.themeio.ThemeTomlWriter;
import dev.jasper.ui.themeio.ThemeToml;
import dev.jasper.ui.theme.ThemeResolver;
import dev.jasper.ui.theme.ThemeGenerator;
import dev.jasper.ui.theme.GeneratedTheme;

/** The themes Jasper ships: Jasper Light/Dark, Nord and Clear Dark/Light. */
final class BuiltInThemes {
    private BuiltInThemes() {}

    private static final java.util.regex.Pattern UI_OR_ICONS = java.util.regex.Pattern.compile("(?m)^\\[(?:ui|icons)[\\].]");

    /**
     * A Jasper theme file Jasper ships; it must stand alone. Its name, darkness and palette come from its head, so the
     * catalog does not parse every [ui] table at start-up; the whole file is parsed when the document is first read.
     */
    static Theme toml(String id, String file) {
        GeneratedTheme summary = ThemeGenerator.generate(ThemeResolver.resolve(ThemeToml.parse(head(read(file)), file)));
        return new Theme(id, summary.name(), summary.dark(), TerminalPaletteAdapter.toTerminal(summary.terminal()),
            new Theme.Bundled(file, () -> ThemeResolver.resolve(ThemeToml.parse(read(file), file))));
    }

    /** {@code text} up to its first [ui] or [icons] table; {@link ThemeTomlWriter} writes them last. */
    static String head(String text) {
        var table = UI_OR_ICONS.matcher(text);
        return table.find() ? text.substring(0, table.start()) : text;
    }

    private static String read(String file) {
        String text = ThemeManager.read(file);
        if (text == null) throw new IllegalStateException("Missing bundled theme " + file);
        return text;
    }

    static java.util.List<Theme> all() { return Indexed.THEMES; }
    private static final class Indexed {
        static final java.util.List<Theme> THEMES = load();
        private static java.util.List<Theme> load() {
            String index = ThemeManager.read("index.txt");
            if (index == null) throw new IllegalStateException("Missing bundled theme index");
            var classics = java.util.Map.of("jasper-light", Theme.JASPER_LIGHT, "jasper-dark", Theme.DARK);
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
