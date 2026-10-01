package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.appearance.ThemeFixtures.*;
import static org.assertj.core.api.Assertions.*;

/**
 * A converted IntelliJ plugin theme installs exactly as the plugin's resolved {@code .theme.json} did before the theme
 * engine: every colour FlatLaf ends up with is the same, named colours ({@code ColorPalette.*}) and the accent included.
 */
@ExtendWith(EdtTestExtension.class)
class PluginThemeParityTest {
    private static final String ENTRY = "themes/Vampire.theme.json";
    /** A Dracula-style theme: its accent is a named colour, its parent Darcula. */
    private static final String THEME = """
        {"name": "Vampire", "dark": true, "author": "Fixture", "parentTheme": "Darcula",
         "colors": {"accentColor": "#ff79c6", "background": "#282a36", "foreground": "#f8f8f2", "selection": "#44475a",
           "comment": "accentColor"},
         "ui": {"*": {"background": "background", "foreground": "foreground", "selectionBackground": "selection"},
           "Panel.background": "background", "Link.activeForeground": "comment"}}""";

    @Test void aConvertedPluginThemeInstallsTheSameColoursAsItsThemeFile(@TempDir Path home) throws Exception {
        assertParity(home, THEME);
    }

    @Test void draculaKeepsItsAuthorSpecificAdjustments(@TempDir Path home) throws Exception {
        assertParity(home, THEME.replace("Fixture", "Zihan Ma").replace("Vampire", "Dracula"));
    }

    @Test void materialKeepsItsAuthorSpecificAdjustments(@TempDir Path home) throws Exception {
        assertParity(home, THEME.replace("Fixture", "Mallowigi"));
    }

    @Test void namedColoursWithUiSuffixesKeepTheirOriginalHandling(@TempDir Path home) throws Exception {
        assertParity(home, THEME.replace("\"comment\": \"accentColor\"", """
            "comment": "accentColor", "sampleIcon": "#123456", "sample.border": "#234567",
            "sample.rowHeight": "#345678"
            """));
    }

    private static void assertParity(Path home, String json) throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            jar(home.resolve("themes/vampire.jar"), entries(
                "META-INF/plugin.xml", pluginXml("Vampire", "/" + ENTRY), ENTRY, json));
            var snapshot = ThemeCatalog.beside(home.resolve("config.toml")).scan();
            assertThat(snapshot.problems()).isEmpty();
            Theme theme = snapshot.themes().stream().filter(candidate -> candidate.source() instanceof Theme.Archive)
                .findFirst().orElseThrow();
            String label = ((Theme.Archive) theme.source()).entry();

            var resolved = ThemeManager.loadArchive(label, json);
            assertThat(ThemeManager.install(resolved.json(), resolved.colors(), theme.palette().background())).isTrue();
            Map<String, String> before = colours();
            assertThat(ThemeManager.install(theme)).isTrue();
            Map<String, String> after = colours();

            assertThat(before).as("the theme file's own install").containsEntry("Component.accentColor", "#ff79c6")
                .containsEntry("ColorPalette.accentColor", "#ff79c6");
            assertThat(differences(before, after)).as("colours that differ (theme file -> converted)").isEmpty();
            var reloaded = ThemeResolver.resolve(ThemeToml.parse(ThemeTomlWriter.write(theme.source().document()), "export.toml"));
            assertThat(ThemeManager.install(Theme.of("export", new Theme.UserFile(home.resolve("export.toml"), reloaded)))).isTrue();
            assertThat(differences(before, colours())).as("native disk round-trip install").isEmpty();
            var installed = UIManager.getLookAndFeel();
            var workspace = WorkspaceValues.resolve(reloaded, ThemeGenerator.generate(reloaded));
            assertThat(UIManager.getLookAndFeel()).isSameAs(installed);
            assertThat(workspace.icons()).isEqualTo(theme.source().document().icons());
            assertThat(theme.source().document().seeds()).containsEntry(Seed.ACCENT, new Color(0xff79c6));
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** Each key whose colour differs, or that only one side has, mapped to its two colours. */
    private static Map<String, String> differences(Map<String, String> before, Map<String, String> after) {
        var keys = new TreeSet<String>(before.keySet());
        keys.addAll(after.keySet());
        var differences = new TreeMap<String, String>();
        for (String key : keys)
            if (!Objects.equals(before.get(key), after.get(key))) differences.put(key, before.get(key) + " -> " + after.get(key));
        return differences;
    }

    /** Every colour in the look and feel defaults as hex (alpha appended when not opaque), sorted by key. */
    private static Map<String, String> colours() {
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        var colours = new TreeMap<String, String>();
        for (Object key : defaults.keySet().toArray()) {
            if (defaults.get(key) instanceof Color color)
                colours.put(String.valueOf(key), String.format("#%06x", color.getRGB() & 0xffffff)
                    + (color.getAlpha() == 255 ? "" : String.format("%02x", color.getAlpha())));
        }
        return colours;
    }
}
