package dev.jasper.app.appearance;
import dev.jasper.ui.themeio.ThemeTomlWriter;
import dev.jasper.ui.themeio.ThemeToml;
import dev.jasper.ui.theme.ThemeResolver;
import dev.jasper.ui.theme.ThemeGenerator;

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
 * A converted IntelliJ theme preserves every installed color across native publication/reload,
 * including authored named colors, independent of vendor-name postprocessing.
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
            var converted = dev.jasper.ui.themeimport.ThemeConverter.convert(home.resolve("themes/vampire.jar")).getFirst();
            assertThat(converted.warnings().stream().filter(w -> !w.contains("discarded non-color override") && !w.contains("no longer controls Jasper native UI"))).singleElement().asString().contains("no console colours", "Jasper Dark");
            Theme theme = Theme.of("converted", new Theme.UserFile(home.resolve("vampire.toml"), converted.document()));
            String label = converted.sourceEntry();

            var resolved = dev.jasper.ui.themeimport.LegacyThemeTestSupport.load(label, json);
            assertThat(ThemeManager.install(ThemeGenerator.generate(converted.document()))).isTrue();
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
            var workspace = ThemeGenerator.generate(reloaded);
            assertThat(UIManager.getLookAndFeel()).isSameAs(installed);
            assertThat(workspace.icons()).isEqualTo(theme.source().document().icons());
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
