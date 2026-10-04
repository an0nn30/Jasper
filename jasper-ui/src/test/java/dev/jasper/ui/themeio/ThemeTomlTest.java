package dev.jasper.ui.themeio;

import dev.jasper.ui.theme.*;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ThemeTomlTest {
    private static final String FULL = """
        name = "  Nord  "
        dark = true
        extends = "Jasper Dark"
        author = "Arctic Ice Studio"
        license = "MIT"
        ui_complete = false

        [colors]
        background = "#2e3440"
        foreground = "#D8DEE9"
        accent = "#88c0d0"
        selection = "#434c5e80"
        [colors.normal]
        black = "#3b4252"
        red = "#bf616a"
        [colors.bright]
        white = "#eceff4"

        [tokens]
        tab_active = "#3b4252"

        [syntax]
        keyword = "#81a1c1"
        comment = { color = "#616e88", italic = true }

        [ui]
        "*.background" = "#2e3440"
        Button.arc = 6
        "Component.innerFocusWidth" = 0.5
        "Button.default.boldText" = true
        "Tree.rowHeight" = { "os.mac" = 22, "os.default" = 20 }
        """;

    @Test void aFullThemeFileReadsEverySection() {
        ThemeDocument theme = ThemeToml.parse(FULL, "nord.toml");
        assertThat(theme.origin()).isEqualTo("nord.toml");
        assertThat(theme.name()).isEqualTo("Nord");
        assertThat(theme.dark()).contains(true);
        assertThat(theme.parent()).contains("Jasper Dark");
        assertThat(theme.parentLine()).isEqualTo(3);
        assertThat(theme.uiComplete()).contains(false);
        assertThat(theme.metadata()).containsExactly(Map.entry("author", "Arctic Ice Studio"), Map.entry("license", "MIT"));
        assertThat(theme.seeds()).containsOnly(Map.entry(Seed.BACKGROUND, new Color(0x2e3440)),
            Map.entry(Seed.FOREGROUND, new Color(0xd8dee9)), Map.entry(Seed.ACCENT, new Color(0x88c0d0)),
            Map.entry(Seed.SELECTION, new Color(0x43, 0x4c, 0x5e, 0x80)));
        assertThat(theme.ansi()).containsOnly(Map.entry(0, new Color(0x3b4252)), Map.entry(1, new Color(0xbf616a)),
            Map.entry(15, new Color(0xeceff4)));
        assertThat(theme.tokens()).containsOnly(Map.entry(Token.TAB_ACTIVE, new Color(0x3b4252)));
        assertThat(theme.syntax()).containsOnly(
            Map.entry(SyntaxRole.KEYWORD, new SyntaxStyle(new Color(0x81a1c1), false, false)),
            Map.entry(SyntaxRole.COMMENT, new SyntaxStyle(new Color(0x616e88), false, true)));
        assertThat(List.copyOf(theme.ui().keySet())).containsExactly("*.background", "Button.arc",
            "Component.innerFocusWidth", "Button.default.boldText", "Tree.rowHeight");
        assertThat(theme.ui().get("Button.arc")).isEqualTo(6L);
        assertThat(theme.ui().get("Component.innerFocusWidth")).isEqualTo(0.5);
        assertThat(theme.ui().get("Button.default.boldText")).isEqualTo(true);
        assertThat(theme.ui().get("Tree.rowHeight")).isEqualTo(Map.of("os.mac", 22L, "os.default", 20L));
        assertThat(theme.icons()).isEmpty();
    }

    @Test void aMinimalThemeHasEmptySectionsAndNoOptionalValues() {
        ThemeDocument theme = ThemeToml.parse("name = \"Bare\"", "bare.toml");
        assertThat(theme.dark()).isEmpty();
        assertThat(theme.parent()).isEmpty();
        assertThat(theme.parentLine()).isZero();
        assertThat(theme.uiComplete()).isEmpty();
        assertThat(theme.seeds()).isEmpty();
        assertThat(theme.ui()).isEmpty();
    }

    @Test void everyProblemNamesTheFileAndLine() {
        assertProblem("name = \"x\"\n[colors\n", 2, "");
        assertProblem("name='x'\nflavour=3", 2, "unknown key");
        assertProblem("dark = true\n", 0, "needs a \"name\"");
        assertProblem("name = \"  \"\n", 1, "must not be blank");
        assertProblem("name = \"x\"\nextends = \"\"\n", 2, "must not be blank");
        assertProblem("name = 3\n", 1, "\"name\" must be a string");
        assertProblem("name = \"x\"\ndark = \"yes\"\n", 2, "\"dark\" must be true or false");
        assertProblem("name = \"x\"\n[colors]\nbackgound = \"#000000\"\n", 3, "unknown colour \"backgound\"");
        assertProblem("name = \"x\"\n[colors]\nbackground = \"#00000\"\n", 3, "must be a colour like #1e1e2e");
        assertProblem("name = \"x\"\n[colors]\nbackground = \"000000\"\n", 3, "must be a colour like #1e1e2e");
        assertProblem("name = \"x\"\n[colors.normal]\npurple = \"#000000\"\n", 3, "unknown ANSI colour \"purple\"");
        assertProblem("name = \"x\"\n[tokens]\ntab = \"#000000\"\n", 3, "unknown token \"tab\"");
        assertProblem("name = \"x\"\n[syntax]\nmacro = \"#000000\"\n", 3, "unknown syntax role \"macro\"");
        assertProblem("name = \"x\"\n[syntax]\nkeyword = { colour = \"#000000\" }\n", 3, "unknown field \"colour\"");
        assertProblem("name = \"x\"\n[syntax]\nkeyword = { bold = true }\n", 3, "needs a \"color\"");
        assertProblem("name = \"x\"\n[syntax]\nkeyword = 3\n", 3, "must be a colour or { color, bold, italic }");
        assertProblem("name = \"x\"\n[ui]\n\"ButtonUI\" = \"x\"\n", 3, "Swing UI delegate");
        assertProblem("name = \"x\"\n[ui]\n\"Button.border\" = \"com.example.EvilBorder\"\n", 3, "names a Java class");
        assertProblem("name = \"x\"\n[ui]\n\"Button.margin\" = [1, 2]\n", 3, "must be a string, number or boolean");
        assertProblem("name = \"x\"\ncolors = \"#000000\"\n", 2, "\"colors\" must be a table");
    }

    @Test void colourTextParsesWithAndWithoutAlpha() {
        assertThat(ThemeToml.color("#0A0b0C")).isEqualTo(new Color(0x0a0b0c));
        assertThat(ThemeToml.color("#0a0b0c7f")).isEqualTo(new Color(0x0a, 0x0b, 0x0c, 0x7f));
    }

    private static void assertProblem(String text, int line, String message) {
        assertThatThrownBy(() -> ThemeToml.parse(text, "bad.toml"))
            .isInstanceOfSatisfying(ThemeFileException.class, failure -> {
                assertThat(failure.origin()).isEqualTo("bad.toml");
                assertThat(failure.line()).as(text).isEqualTo(line);
                assertThat(failure.getMessage()).as(text).startsWith(line > 0 ? "bad.toml:" + line + ": " : "bad.toml: ").contains(message);
            });
    }
}
