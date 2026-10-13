package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The Jasper theme format (spec section 2): every rule of the file, through the bundled file and small variations. */
class JasperThemeTest {
    static String bundledJson() throws Exception {
        try (var in = JasperThemeTest.class.getResourceAsStream(JasperTheme.RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** {@code ui.dark}, {@code editor.dark} and the dark and high-contrast schemes removed: a Light-only theme. */
    static String lightOnly() throws Exception {
        String json = bundledJson();
        json = json.replaceFirst("(?s)\"ui\": \\{\\s*\"dark\": \\{.*?},\\s*\"light\"", "\"ui\": {\n    \"light\"");
        json = json.replaceFirst("(?s)\"editor\": \\{\\s*\"dark\": \\{.*?},\\s*\"light\"", "\"editor\": {\n    \"light\"");
        json = json.replaceFirst("(?s)\"terminal\": \\{\\s*\"dark\": \\{.*?},\\s*\"light\"", "\"terminal\": {\n    \"light\"");
        return json.replaceFirst("(?s),\\s*\"high-contrast\": \\{.*?}", "");
    }

    /** A terminal-only theme with one scheme. */
    static final String GRUVBOX = """
        { "jasper-theme": 1, "name": "Gruvbox Dark",
          "terminal": { "dark": { "name": "Gruvbox Dark", "bg": "#282828", "fg": "#EBDBB2", "fg.bold": "#FBF1C7",
            "cursor": "#D5C4A1", "selection": "#504945", "black": "#282828", "red": "#CC241D", "green": "#98971A",
            "yellow": "#D79921", "blue": "#458588", "magenta": "#B16286", "cyan": "#689D6A", "white": "#A89984",
            "bright.black": "#928374", "bright.red": "#FB4934", "bright.green": "#B8BB26", "bright.yellow": "#FABD2F",
            "bright.blue": "#83A598", "bright.magenta": "#D3869B", "bright.cyan": "#8EC07C", "bright.white": "#EBDBB2" } } }
        """;

    static ThemeFileException failure(String json) {
        return catchThrowableOfType(ThemeFileException.class, () -> JasperTheme.parse(json, "t"));
    }

    @Test void aTerminalOnlyThemeHasSchemesAndNoVariant() {
        var theme = JasperTheme.parse(GRUVBOX, "gruvbox-dark");
        assertThat(theme.name()).isEqualTo("Gruvbox Dark");
        assertThat(theme.author()).isEmpty();
        assertThat(theme.has(true)).isFalse();
        assertThat(theme.has(false)).isFalse();
        var scheme = theme.schemes().getFirst();
        assertThat(scheme.id()).isEqualTo(new SchemeId("gruvbox-dark", "dark"));
        assertThat(scheme.name()).isEqualTo("Gruvbox Dark");
        assertThat(scheme.colors().background()).isEqualTo(new Color(0x282828));
        assertThat(scheme.boldForeground()).isEqualTo(new Color(0xFBF1C7));
        assertThatThrownBy(() -> theme.generated(true)).isInstanceOf(IllegalStateException.class).hasMessage("Gruvbox Dark has no Dark variant");
        assertThatThrownBy(() -> theme.matching(false)).isInstanceOf(IllegalStateException.class);
    }

    @Test void aLightOnlyThemeOffersOnlyLight() throws Exception {
        var theme = JasperTheme.parse(lightOnly(), "dawn");
        assertThat(theme.has(true)).isFalse();
        assertThat(theme.has(false)).isTrue();
        assertThat(theme.schemes()).extracting(scheme -> scheme.id().key()).containsExactly("light");
        assertThat(theme.generated(false).terminal()).isEqualTo(theme.matching(false).colors());
        assertThat(theme.generated(false).name()).isEqualTo("Slate Steel");
    }

    @Test void anEditorEntryMayBeALiteralColourWithStyles() throws Exception {
        String json = bundledJson().replaceFirst("\"keyword\": \"terminal.ansi.magenta\"", "\"keyword\": \"#FF79C6, italic, bold\"")
            .replaceFirst("\"gutter\": \"terminal.bg\"", "\"gutter\": \"#101010\"");
        var theme = JasperTheme.parse(json, "t");
        assertThat(theme.syntax(true).get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xFF79C6), true, true));
        assertThat(theme.editor(true).gutter()).isEqualTo(new Color(0x101010));
        assertThat(theme.syntax(false).get(SyntaxRole.KEYWORD)).as("only the Dark entry changed").isEqualTo(JasperTheme.bundled().syntax(false).get(SyntaxRole.KEYWORD));
        assertThat(failure(bundledJson().replaceFirst("\"keyword\": \"terminal.ansi.magenta\"", "\"keyword\": \"#FF79C680\"")))
            .hasMessageContaining("editor.dark.keyword: Expected #RRGGBB or a reference, got \"#FF79C680\"");
        assertThat(failure(bundledJson().replaceFirst("\"gutter\": \"terminal.bg\"", "\"gutter\": \"#101010, bold\"")))
            .hasMessageContaining("editor.dark.gutter: A surface takes no style");
    }

    @Test void theMarkerNameAndAuthorAreChecked() throws Exception {
        String json = bundledJson();
        assertThat(failure(json.replace("\"jasper-theme\": 1, ", ""))).hasMessage("t.json: Not a Jasper theme: \"jasper-theme\": 1 is missing");
        assertThat(failure(json.replace("\"jasper-theme\": 1,", "\"jasper-theme\": 2,")))
            .hasMessageContaining("jasper-theme: Format 2 is not one this Jasper reads; expected 1");
        assertThat(failure(json.replace("\"jasper-theme\": 1,", "\"jasper-theme\": \"1\","))).hasMessageContaining("jasper-theme: Expected the number 1");
        assertThat(failure(json.replace("\"name\": \"Slate Steel\", ", ""))).hasMessageContaining("Missing entry \"name\"");
        assertThat(failure(json.replace("\"name\": \"Slate Steel\"", "\"name\": \" \""))).hasMessageContaining("name: Expected a non-empty string");
        assertThat(failure(json.replace("\"author\": \"Jasper\"", "\"author\": {}"))).hasMessageContaining("author: Expected a string");
        assertThat(failure(json.replace("\"author\": \"Jasper\",", "\"author\": \"Jasper\", \"extra\": \"x\","))).hasMessageContaining("Unknown entry \"extra\"");
    }

    @Test void aVariantNeedsItsMatchingSchemeAndEditorAndNothingElse() throws Exception {
        String json = bundledJson();
        assertThat(failure(json.replace("\"light\": {\n      \"surface.app\"", "\"pale\": {\n      \"surface.app\"")))
            .hasMessageContaining("ui: Unknown variant \"pale\"");
        assertThat(failure(json.replace("\"light\": {\n      \"name\": \"Slate Steel Light\"", "\"pale\": {\n      \"name\": \"Slate Steel Light\"")))
            .hasMessageContaining("ui.light: Missing its matching scheme \"terminal.light\"");
        assertThat(failure(json.replaceFirst("(?s)\"editor\": \\{.*}\\s*}\\s*$", "\"x\": \"y\" }"))).hasMessageContaining("Unknown entry \"x\"");
        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\",\n", "\"name\": \"Gruvbox Dark\", \"ui\": {},\n")))
            .hasMessageContaining("ui: Expected \"dark\", \"light\" or both");
        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\",\n", "\"name\": \"Gruvbox Dark\", \"editor\": {},\n")))
            .hasMessageContaining("editor: A theme without ui has no editor schemes");
        assertThat(failure(lightOnly().replace("\"editor\": {\n    \"light\"", "\"editor\": {\n    \"dark\": {}, \"light\"")))
            .hasMessageContaining("editor: Unknown variant \"dark\"");
    }

    @Test void schemesNeedAKeyANameAndExactlyTheirColours() throws Exception {
        assertThat(failure(GRUVBOX.replace("\"dark\": {", "\"Dark_1\": {"))).hasMessageContaining(
            "terminal: Scheme key \"Dark_1\" must be lowercase letters, digits and dashes, at most 32 characters");
        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\", \"bg\"", "\"bg\""))).hasMessageContaining("terminal.dark: Missing entry \"name\"");
        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\", \"bg\"", "\"name\": \"\", \"bg\""))).hasMessageContaining("terminal.dark.name: Expected a non-empty string");
        assertThat(failure(GRUVBOX.replace("\"fg.bold\": \"#FBF1C7\",", ""))).hasMessageContaining("terminal.dark: Missing colour \"fg.bold\"");
        assertThat(failure(GRUVBOX.replace("\"bg\":", "\"bgx\": \"#000000\", \"bg\":"))).hasMessageContaining("terminal.dark: Unknown colour \"bgx\"");
        assertThat(failure(GRUVBOX.replace("\"terminal\": { \"dark\"", "\"terminal\": {}, \"x\": { \"dark\""))).hasMessageContaining("Unknown entry \"x\"");
        assertThat(failure("{ \"jasper-theme\": 1, \"name\": \"Empty\", \"terminal\": {} }")).hasMessageContaining("terminal: Expected at least one scheme");
        assertThat(failure("{ \"jasper-theme\": 1, \"name\": \"None\" }")).hasMessageContaining("Missing object \"terminal\"");
    }

    @Test void aFileTheEngineCannotUseFailsNamingTheKey() throws Exception {
        String json = bundledJson();
        assertThat(failure(json.replace("\"surface.app\": \"#1E2127\",", "\"surface.app\": \"#1E2127\", \"surface.nope\": \"#000000\",")))
            .hasMessageContaining("ui.dark: Unknown colour \"surface.nope\"");
        assertThat(failure(json.replace("\"surface.panel\": \"#272A31\", ", ""))).hasMessageContaining("ui.dark: Missing colour \"surface.panel\"");
        assertThat(failure(json.replace("\"#1E2127\"", "\"#1E21\""))).hasMessageContaining("surface.app").hasMessageContaining("#RRGGBB");
        assertThat(failure(json.replaceFirst("\"caret\": \"[^\"]+\",\\s*", ""))).hasMessageContaining("editor.dark: Missing entry \"caret\"");
        assertThat(failure(json.replaceFirst("\"keyword\": \"terminal.ansi.magenta\"", "\"keyword\": \"terminal.ansi.pink\"")))
            .hasMessageContaining("editor.dark.keyword").hasMessageContaining("terminal.ansi.pink");
        assertThat(failure(json.replaceFirst("\"currentLine\": \"hover.overlay @ 5%\"", "\"currentLine\": \"hover.overlay @ 105%\"")))
            .hasMessageContaining("currentLine");
        assertThat(failure(json.replaceFirst("\"background\": \"terminal.bg\"", "\"background\": \"terminal.bg @ 50%\""))).hasMessageContaining("background");
        assertThat(failure(json.replaceFirst("terminal.ansi.bright.black, italic", "terminal.ansi.bright.black, wavy"))).hasMessageContaining("wavy");
        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\",\n", "\"name\": \"Gruvbox Dark\", \"ui\": \"x\",\n")))
            .hasMessageContaining(": ui: Expected an object");
    }

    @Test void theDetailOmitsTheFileForCallersThatShowItSeparately() throws Exception {
        var failure = failure(bundledJson().replace("\"surface.panel\": \"#272A31\", ", ""));
        assertThat(failure.origin()).isEqualTo("t.json");
        assertThat(failure.detail()).isEqualTo("ui.dark: Missing colour \"surface.panel\"");
        assertThat(failure.getMessage()).isEqualTo("t.json: " + failure.detail());
    }

    @Test void idsAreLowercaseWordsOfAtMost64Characters() {
        assertThat(JasperTheme.validId("ocean-dawn-2")).isTrue();
        assertThat(JasperTheme.validId("x".repeat(64))).isTrue();
        assertThat(JasperTheme.validId("x".repeat(65))).isFalse();
        for (String bad : new String[] {"", "Ocean", "ocean_dawn", "-ocean", "ocean-", "ocean--dawn", "oc ean", null})
            assertThat(JasperTheme.validId(bad)).as(bad).isFalse();
        assertThatThrownBy(() -> JasperTheme.parse(GRUVBOX, "Gruvbox")).isInstanceOf(IllegalArgumentException.class);
    }
}
