package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ThemeGeneratorTest {
    /** Catppuccin Mocha's seeds: a typical terminal-first theme with no tokens. */
    static final String SEEDS = """
        name = "Mocha"
        [colors]
        background = "#1e1e2e"
        foreground = "#cdd6f4"
        accent = "#89b4fa"
        [colors.normal]
        black = "#45475a"
        red = "#f38ba8"
        green = "#a6e3a1"
        yellow = "#f9e2af"
        blue = "#89b4fa"
        magenta = "#f5c2e7"
        cyan = "#94e2d5"
        white = "#bac2de"
        """;

    static GeneratedTheme generate(String toml) {
        return ThemeGenerator.generate(ThemeResolver.resolve(ThemeToml.parse(toml, "test.toml")));
    }

    @Test void seedsAloneMakeACompleteTheme() {
        GeneratedTheme theme = generate(SEEDS);
        assertThat(theme.name()).isEqualTo("Mocha");
        assertThat(theme.dark()).as("inferred from the background").isTrue();
        assertThat(theme.tokens()).containsOnlyKeys(Token.values());
        assertThat(theme.syntax()).containsOnlyKeys(SyntaxRole.values());
        assertThat(theme.terminal().background()).isEqualTo(new Color(0x1e1e2e));
        assertThat(theme.terminal().foreground()).isEqualTo(new Color(0xcdd6f4));
        assertThat(theme.terminal().cursor()).as("cursor defaults to the foreground").isEqualTo(new Color(0xcdd6f4));
        assertThat(theme.terminal().ansi().get(1)).isEqualTo(new Color(0xf38ba8));
        assertThat(theme.terminal().ansi().get(9)).as("bright red: 20% toward white")
            .isEqualTo(ChromeKeys.mix(Color.WHITE, new Color(0xf38ba8), .20));
        assertThat(ChromeKeys.contrast(theme.terminal().selection(), new Color(0x1e1e2e))).isGreaterThanOrEqualTo(1.5);
        assertThat(theme.tokens().get(Token.SURFACE)).isEqualTo(new Color(0x1e1e2e));
        assertThat(theme.tokens().get(Token.TEXT)).isEqualTo(new Color(0xcdd6f4));
        assertThat(theme.tokens().get(Token.ACCENT)).isEqualTo(new Color(0x89b4fa));
        assertThat(theme.tokens().get(Token.TAB_UNDERLINE)).isEqualTo(new Color(0x89b4fa));
        assertThat(theme.tokens().get(Token.EDITOR_BACKGROUND)).isEqualTo(new Color(0x1e1e2e));
        assertThat(theme.icons()).isEmpty();
    }

    @Test void derivedTextAndStrokesMeetTheirContrastFloors() {
        GeneratedTheme theme = generate(SEEDS.replace("#a6e3a1", "#203320").replace("#f38ba8", "#3a1f24")
            .replace("#f9e2af", "#33301f").replace("#45475a", "#26263a"));
        var tokens = theme.tokens();
        Color surface = tokens.get(Token.SURFACE), statusBar = tokens.get(Token.STATUS_BAR);
        assertThat(ChromeKeys.contrast(tokens.get(Token.TEXT_MUTED), surface)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(tokens.get(Token.TEXT_DISABLED), surface)).isGreaterThanOrEqualTo(2);
        assertThat(ChromeKeys.contrast(tokens.get(Token.LINK), surface)).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(tokens.get(Token.DIVIDER), new Color(0x1e1e2e))).isGreaterThanOrEqualTo(3);
        for (Token status : List.of(Token.SUCCESS, Token.WARNING, Token.ERROR))
            assertThat(ChromeKeys.contrast(tokens.get(status), statusBar)).as(status.key()).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(tokens.get(Token.SELECTION_TEXT), tokens.get(Token.SELECTION))).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(tokens.get(Token.TEXT_ON_ACCENT), tokens.get(Token.BUTTON_DEFAULT))).isGreaterThanOrEqualTo(3);
        Color editor = tokens.get(Token.EDITOR_BACKGROUND);
        assertThat(ChromeKeys.contrast(theme.syntax().get(SyntaxRole.STRING).color(), editor)).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(theme.syntax().get(SyntaxRole.COMMENT).color(), editor)).isGreaterThanOrEqualTo(3);
        assertThat(theme.syntax().get(SyntaxRole.COMMENT).italic()).isTrue();
        assertThat(theme.syntax().get(SyntaxRole.KEYWORD).italic()).isFalse();
    }

    @Test void derivedTextContrastsWithAnExplicitBackground() {
        GeneratedTheme theme = generate(SEEDS + """
            [tokens]
            surface = "#cdd6f4"
            editor_background = "#cdd6f4"
            status_bar = "#cdd6f4"
            button_background = "#cdd6f4"
            tab_active = "#cdd6f4"
            text_selection = "#cdd6f4"
            """);
        var tokens = theme.tokens();
        for (var pair : List.of(List.of(Token.TEXT, Token.SURFACE),
                List.of(Token.EDITOR_FOREGROUND, Token.EDITOR_BACKGROUND), List.of(Token.BUTTON_TEXT, Token.BUTTON_BACKGROUND),
                List.of(Token.TAB_ACTIVE_TEXT, Token.TAB_ACTIVE), List.of(Token.TEXT_SELECTION_TEXT, Token.TEXT_SELECTION)))
            assertThat(ChromeKeys.contrast(tokens.get(pair.getFirst()), tokens.get(pair.getLast())))
                .as(pair.getFirst().key()).isGreaterThanOrEqualTo(4.5);
    }

    @Test void contrastFloorsUseTheActualFillWhenItDiffersFromTheSurface() {
        GeneratedTheme theme = generate(SEEDS + """
            [tokens]
            status_bar = "#cdd6f4"
            """);
        for (Token status : List.of(Token.SUCCESS, Token.WARNING, Token.ERROR))
            assertThat(ChromeKeys.contrast(theme.tokens().get(status), new Color(0xcdd6f4)))
                .as(status.key()).isGreaterThanOrEqualTo(4.5);
        GeneratedTheme lightChrome = generate(SEEDS + """
            [tokens]
            surface = "#fafafa"
            text = "#202020"
            """);
        assertThat(ChromeKeys.contrast(lightChrome.tokens().get(Token.DIVIDER), new Color(0x1e1e2e)))
            .isGreaterThanOrEqualTo(3);
    }

    @Test void explicitValuesAreKeptAsWrittenAndFeedTheDerivations() {
        GeneratedTheme theme = generate(SEEDS + """
            [tokens]
            surface = "#101018"
            text_muted = "#2a2a3a"
            [syntax]
            string = { color = "#000001", bold = true }
            """);
        assertThat(theme.tokens().get(Token.SURFACE)).isEqualTo(new Color(0x101018));
        assertThat(theme.tokens().get(Token.TEXT_MUTED)).as("below the floor, but written by the author").isEqualTo(new Color(0x2a2a3a));
        assertThat(theme.tokens().get(Token.HOVER)).isEqualTo(ChromeKeys.mix(new Color(0xcdd6f4), new Color(0x101018), .08));
        assertThat(theme.syntax().get(SyntaxRole.STRING)).isEqualTo(new SyntaxStyle(new Color(0x000001), true, false));
    }

    @Test void darknessIsInferredFromTheBackgroundUnlessSet() {
        String light = SEEDS.replace("#1e1e2e", "#fafafa").replace("#cdd6f4", "#383a42");
        GeneratedTheme theme = generate(light);
        assertThat(theme.dark()).isFalse();
        assertThat(theme.terminal().ansi().get(9)).as("bright red: 20% toward black on a light theme")
            .isEqualTo(ChromeKeys.mix(Color.BLACK, new Color(0xf38ba8), .20));
        assertThat(generate(light.replace("name = \"Mocha\"", "name = \"Mocha\"\ndark = true")).dark()).isTrue();
    }

    @Test void uiKeysAreTheTemplateThenTheThemesOwnKeys() {
        GeneratedTheme theme = generate(SEEDS + """
            [ui]
            "Panel.background" = "#010203"
            "Button.arc" = 6
            """);
        var keys = theme.uiKeys();
        var tokens = theme.tokens();
        assertThat(List.copyOf(keys.keySet()).getFirst()).isEqualTo("*.background");
        assertThat(keys.get("*.background")).isEqualTo(UiTemplate.hex(tokens.get(Token.SURFACE)));
        assertThat(keys.get("@background")).isEqualTo(UiTemplate.hex(tokens.get(Token.SURFACE)));
        assertThat(keys.get("@foreground")).isEqualTo(UiTemplate.hex(tokens.get(Token.TEXT)));
        assertThat(keys.get("List.background")).isEqualTo(UiTemplate.hex(tokens.get(Token.SURFACE_SUNKEN)));
        assertThat(keys.get("Jasper.tabSelectedBackground")).isEqualTo(UiTemplate.hex(tokens.get(Token.TAB_ACTIVE)));
        assertThat(keys.get("Jasper.splitDivider")).isEqualTo(UiTemplate.hex(tokens.get(Token.DIVIDER)));
        assertThat(keys.get("Component.arc")).isEqualTo(4);
        assertThat(keys.get("Panel.background")).isEqualTo("#010203");
        assertThat(keys.get("Button.arc")).isEqualTo(6L);
        assertThat(List.copyOf(keys.keySet())).as("the theme's keys come last").endsWith("Panel.background", "Button.arc");
    }

    @Test void aCompleteThemeKeepsItsOwnKeysAndOnlyItsExplicitTokens() {
        GeneratedTheme theme = generate(SEEDS.replace("name = \"Mocha\"", "name = \"Mocha\"\nui_complete = true") + """
            [tokens]
            tab_active = "#123456"
            [ui]
            "Panel.background" = "#010203"
            """);
        assertThat(theme.uiKeys()).containsOnlyKeys("Jasper.tabSelectedBackground", "Panel.background");
        assertThat(theme.uiKeys().get("Jasper.tabSelectedBackground")).isEqualTo("#123456");
    }

    @Test void theTemplateHoldsOnlyTokensAndPlainValues() {
        assertThat(UiTemplate.FLATLAF.values()).allSatisfy(value -> assertThat(value)
            .isInstanceOfAny(Token.class, String.class, Integer.class, Double.class, Boolean.class));
        assertThat(UiTemplate.FLATLAF.keySet()).noneMatch(key -> key.startsWith("Jasper."));
        assertThat(UiTemplate.JASPER.keySet()).allMatch(key -> key.startsWith("Jasper."));
        assertThat(UiTemplate.hex(new Color(1, 2, 3))).isEqualTo("#010203");
        assertThat(UiTemplate.hex(new Color(1, 2, 3, 4))).isEqualTo("#01020304");
    }

    @Test void chromeDerivesFromTheResolvedTokensNotTheTerminalSeeds() {
        GeneratedTheme theme = generate(SEEDS + """
            [tokens]
            surface = "#f4f4f4"
            text = "#202020"
            editor_background = "#ffffff"
            editor_foreground = "#000000"
            """);
        var tokens = theme.tokens();
        Color surface = new Color(0xf4f4f4);
        assertThat(tokens.get(Token.HOVER)).isEqualTo(ChromeKeys.mix(new Color(0x202020), surface, .08));
        assertThat(ChromeKeys.contrast(tokens.get(Token.TEXT_MUTED), surface)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(tokens.get(Token.LINK), surface)).isGreaterThanOrEqualTo(4.5);
        assertThat(tokens.get(Token.EDITOR_CURRENT_LINE)).isEqualTo(ChromeKeys.mix(Color.BLACK, Color.WHITE, .05));
    }
}
