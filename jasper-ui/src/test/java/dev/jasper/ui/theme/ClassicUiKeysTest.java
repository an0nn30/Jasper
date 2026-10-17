package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 6.5: the classic key set is the default one with the frozen classic overlay. */
class ClassicUiKeysTest {
    static LinkedHashMap<String, String> overlay() throws IOException {
        var table = new LinkedHashMap<String, String>();
        try (var in = ClassicUiKeysTest.class.getResourceAsStream("ui-color-keys-classic.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                table.put(cells[0], cells[1]);
            }
        }
        return table;
    }

    /** A source name: a SemanticColor constant, or a derived role's key. */
    static String colour(GeneratedTheme theme, String source) {
        for (var role : DerivedColor.values()) if (role.key().equals(source)) return ColorMath.hex(role.color(theme));
        return ColorMath.hex(SemanticColor.valueOf(source).color(theme));
    }

    @Test void theClassicKeysAreTheDefaultKeysWithTheOverlay() throws IOException {
        for (var theme : dev.jasper.ui.testsupport.KitFixtures.themes()) {
            var base = UiColorKeys.flatLaf(theme, UiKeySet.DEFAULT);
            var classic = UiColorKeys.flatLaf(theme, UiKeySet.CLASSIC);
            var expected = new LinkedHashMap<String, Object>(base);
            for (var entry : overlay().entrySet()) expected.put(entry.getKey(), colour(theme, entry.getValue()));
            assertThat(classic).as(theme.name()).containsExactlyEntriesOf(expected);
        }
    }

    /** Overlay keys FlatLaf already had keep their place; new keys follow every wildcard, so they win over it. */
    @Test void newKeysComeAfterTheWildcards() throws IOException {
        var keys = new ArrayList<>(UiColorKeys.flatLaf(dev.jasper.ui.testsupport.KitFixtures.light(), UiKeySet.CLASSIC).keySet());
        int lastWildcard = 0;
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).startsWith("*.")) lastWildcard = i;
        for (var key : overlay().keySet()) assertThat(keys.indexOf(key)).as(key).isGreaterThan(lastWildcard);
        assertThat(UiColorKeys.flatLaf(dev.jasper.ui.testsupport.KitFixtures.light(), UiKeySet.CLASSIC).get("Table.selectionBackground"))
            .isEqualTo(ColorMath.hex(dev.jasper.ui.testsupport.KitFixtures.light().semantic().color(SemanticColor.SELECTION_BG)));
    }
}
