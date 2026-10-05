package dev.jasper.ui.theme;

import dev.jasper.ui.themeio.ThemeToml;

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

    @Test void authoredUiColorsReachSemanticSnapshotWithCanonicalAndNativePrecedence() {
        String source=SEEDS+"\n[ui]\n\"Panel.background\" = \"#123456\"\n";
        assertThat(generate(source).semantic().color(SemanticColor.SURFACE_PANEL)).isEqualTo(new Color(0x123456));
        assertThat(generate(source+"\n[tokens]\nsurface=\"#abcdef\"\n").semantic().color(SemanticColor.SURFACE_PANEL)).isEqualTo(new Color(0xabcdef));
        assertThat(generate(source+"\n[semantic]\n\"surface.panel\"=\"#fedcba\"\n").semantic().color(SemanticColor.SURFACE_PANEL)).isEqualTo(new Color(0xfedcba));
    }
    /**
     * The native-consumer vendor keys with a dedicated canonical role translate into that role in one module table:
     * an authored [ui] value beats the legacy token (as the raw key did for the native consumer), and an explicit
     * canonical [semantic] value still wins.
     */
    @Test void supportedVendorRolesTranslateIntoTheirCanonicalRoleBelowExplicitCanonicalValues() {
        String tokens = "\n[tokens]\nsidebar=\"#3c3f41\"\ninput_background=\"#45494a\"\nseparator=\"#515151\"\n";
        String ui = "\n[ui]\n\"ToolWindow.background\" = \"#314159\"\n\"TextField.background\" = { \"os.mac\" = \"#26a69a\", \"os.default\" = \"#26a69a\" }\n"
            + "\"Separator.foreground\" = \"$Panel.background\"\n\"Panel.background\" = \"#5a5b5c\"\n";
        var semantic = generate(SEEDS + tokens + ui).semantic();
        assertThat(semantic.color(SemanticColor.SURFACE_SIDEBAR)).isEqualTo(new Color(0x314159));
        assertThat(semantic.color(SemanticColor.SURFACE_INPUT)).isEqualTo(new Color(0x26a69a));
        assertThat(semantic.color(SemanticColor.BORDER_SUBTLE)).as("references resolve as for tokens").isEqualTo(new Color(0x5a5b5c));
        assertThat(generate(SEEDS + tokens).semantic().color(SemanticColor.SURFACE_SIDEBAR)).isEqualTo(new Color(0x3c3f41));
        var explicit = generate(SEEDS + tokens + ui + "\n[semantic]\n\"surface.sidebar\"=\"#010203\"\n").semantic();
        assertThat(explicit.color(SemanticColor.SURFACE_SIDEBAR)).isEqualTo(new Color(0x010203));
        assertThat(UiColorKeys.semanticRoles()).containsOnlyKeys("ToolWindow.background", "TextField.background", "Separator.foreground");
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
            .isEqualTo(ColorMath.mix(Color.WHITE, new Color(0xf38ba8), .20));
        assertThat(ColorMath.contrast(theme.terminal().selection(), new Color(0x1e1e2e))).isGreaterThanOrEqualTo(1.5);
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
        assertThat(ColorMath.contrast(tokens.get(Token.TEXT_MUTED), surface)).isGreaterThanOrEqualTo(3);
        assertThat(ColorMath.contrast(tokens.get(Token.TEXT_DISABLED), surface)).isGreaterThanOrEqualTo(2);
        assertThat(ColorMath.contrast(tokens.get(Token.LINK), surface)).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorMath.contrast(tokens.get(Token.DIVIDER), new Color(0x1e1e2e))).isGreaterThanOrEqualTo(3);
        for (Token status : List.of(Token.SUCCESS, Token.WARNING, Token.ERROR))
            assertThat(ColorMath.contrast(tokens.get(status), statusBar)).as(status.key()).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorMath.contrast(tokens.get(Token.SELECTION_TEXT), tokens.get(Token.SELECTION))).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorMath.contrast(tokens.get(Token.TEXT_ON_ACCENT), tokens.get(Token.BUTTON_DEFAULT))).isGreaterThanOrEqualTo(3);
        Color editor = tokens.get(Token.EDITOR_BACKGROUND);
        assertThat(ColorMath.contrast(theme.syntax().get(SyntaxRole.STRING).color(), editor)).isGreaterThanOrEqualTo(4.5);
        assertThat(ColorMath.contrast(theme.syntax().get(SyntaxRole.COMMENT).color(), editor)).isGreaterThanOrEqualTo(3);
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
            assertThat(ColorMath.contrast(tokens.get(pair.getFirst()), tokens.get(pair.getLast())))
                .as(pair.getFirst().key()).isGreaterThanOrEqualTo(4.5);
    }

    @Test void contrastFloorsUseTheActualFillWhenItDiffersFromTheSurface() {
        GeneratedTheme theme = generate(SEEDS + """
            [tokens]
            status_bar = "#cdd6f4"
            """);
        for (Token status : List.of(Token.SUCCESS, Token.WARNING, Token.ERROR))
            assertThat(ColorMath.contrast(theme.tokens().get(status), new Color(0xcdd6f4)))
                .as(status.key()).isGreaterThanOrEqualTo(4.5);
        GeneratedTheme lightChrome = generate(SEEDS + """
            [tokens]
            surface = "#fafafa"
            text = "#202020"
            """);
        assertThat(ColorMath.contrast(lightChrome.tokens().get(Token.DIVIDER), new Color(0x1e1e2e)))
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
        assertThat(theme.tokens().get(Token.HOVER)).isEqualTo(ColorMath.mix(new Color(0xcdd6f4), new Color(0x101018), .08));
        assertThat(theme.syntax().get(SyntaxRole.STRING)).isEqualTo(new SyntaxStyle(new Color(0x000001), true, false));
    }

    @Test void darknessIsInferredFromTheBackgroundUnlessSet() {
        String light = SEEDS.replace("#1e1e2e", "#fafafa").replace("#cdd6f4", "#383a42");
        GeneratedTheme theme = generate(light);
        assertThat(theme.dark()).isFalse();
        assertThat(theme.terminal().ansi().get(9)).as("bright red: 20% toward black on a light theme")
            .isEqualTo(ColorMath.mix(Color.BLACK, new Color(0xf38ba8), .20));
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
        assertThat(keys).doesNotContainKeys("Component.arc", "Button.arc");
        assertThat(keys.get("Panel.background")).isEqualTo("#010203");
        assertThat(List.copyOf(keys.keySet())).as("the theme's keys come last").endsWith("Panel.background");
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
        assertThat(tokens.get(Token.HOVER)).isEqualTo(ColorMath.mix(new Color(0x202020), surface, .08));
        assertThat(ColorMath.contrast(tokens.get(Token.TEXT_MUTED), surface)).isGreaterThanOrEqualTo(3);
        assertThat(ColorMath.contrast(tokens.get(Token.LINK), surface)).isGreaterThanOrEqualTo(4.5);
        assertThat(tokens.get(Token.EDITOR_CURRENT_LINE)).isEqualTo(ColorMath.mix(Color.BLACK, Color.WHITE, .05));
    }
    @Test void generatedUiDataExcludesGeometryAndClassesFromDirectDocuments() {
        var base = ThemeResolver.resolve(ThemeToml.parse(SEEDS, "direct.toml"));
        var ui = new java.util.LinkedHashMap<String, Object>();
        ui.put("Button.arc", "@arc");
        ui.put("*.font", "@font");
        ui.put("Panel.background", "#12345680");
        ui.put("Button.border", "com.formdev.flatlaf.ui.FlatButtonBorder");
        ui.put("@evil", "com.example.Evil");
        ui.put("Component.focusWidth", 99);
        ui.put("Tree.rowHeight", java.util.Map.of("os.mac", 900));
        var document = new ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), base.parentLine(),
            base.uiComplete(), base.metadata(), base.seeds(), base.ansi(), base.tokens(), base.syntax(), ui, base.icons());
        assertThat(ThemeGenerator.generate(document).uiKeys()).doesNotContainKeys("Button.arc", "*.font", "Button.border",
            "@evil", "Component.focusWidth", "Tree.rowHeight").containsEntry("Panel.background", "#12345680");
    }

    @Test void generatedSnapshotsFreezeCallerOwnedNestedData() {
        var base = generate(SEEDS);
        var colors = new java.util.LinkedHashMap<String, Object>();
        colors.put("os.mac", "#123456");
        var paths = new java.util.ArrayList<>(List.of("icon.svg"));
        var snapshot = new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(),
            base.terminal(), java.util.Map.of("Panel.background", colors),
            java.util.Map.of("ColorPalette", colors, "paths", paths), base.glass(), base.semantic());
        colors.put("os.mac", "#ffffff");
        paths.add("other.svg");
        assertThat(snapshot.uiKeys()).containsEntry("Panel.background", java.util.Map.of("os.mac", "#123456"));
        assertThat(snapshot.icons()).containsEntry("ColorPalette", java.util.Map.of("os.mac", "#123456"))
            .containsEntry("paths", List.of("icon.svg"));
        assertThatThrownBy(() -> ((java.util.Map<?, ?>) snapshot.uiKeys().get("Panel.background")).clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<?>) snapshot.icons().get("paths")).clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void generatedUiDataRejectsLiteralVariableAndPlatformBorderGeometry() {
        var theme = generate(SEEDS + """
            [ui]
            "Button.border" = "20,20,20,20,#ff0000,4,30"
            "*.border" = "20,20,20,20,#ff0000,4,30"
            "Button.selectedBorder" = "#ff0000"
            "@geometry" = "20,20,20,20,#ff0000,4,30"
            "@geometryAlias" = "$Button.border"
            "Button.toolbar.border" = "@geometry"
            "Panel.border" = { "os.mac" = "20,20,20,20,#ff0000,4,30", "os.default" = "@geometry" }
            "@platformGeometry" = { "os.mac" = "20,20,20,20,#ff0000,4,30", "os.default" = "#ff0000" }
            "@typedBorder" = "{border}20,20,20,20,#ff0000,4,30"
            "@nestedGeometry" = "lighten(20,20,20,20,#ff0000,4,30)"
            "@font" = "16 Arial"
            "Button.borderColor" = "#ff0000"
            "*.borderColor" = "#00ff00"
            """
        );
        assertThat(theme.uiKeys()).doesNotContainKeys("Button.border", "*.border", "Button.selectedBorder",
            "@geometry", "@geometryAlias", "Button.toolbar.border", "Panel.border", "@platformGeometry",
            "@typedBorder", "@nestedGeometry", "@font")
            .containsEntry("Button.borderColor", "#ff0000").containsEntry("*.borderColor", "#00ff00");
    }

    @Test void generatedUiDataPreservesValidatedColorReferencesAndFunctions() {
        var theme = generate(SEEDS + """
            [ui]
            "@base" = "#1234"
            "@alias" = "@base"
            "@hover" = "lighten(@base, 8%, relative derived)"
            "@platformColor" = { "os.mac" = "rgba(1, 2, 3, 50%)", "os.default" = "mix(#12345680, darken(@base, 10%), 25%)" }
            "Button.borderColor" = "@hover"
            "Panel.background" = "$Button.borderColor"
            "Label.foreground" = "contrast(@base, #000, #fff, 43%)"
            "List.background" = "hsl(200, 80%, 40%)"
            "List.selectionBackground" = "over(#1234, #fff)"
            "*.disabledForeground" = "fade($Label.foreground, 42%)"
            """
        );
        assertThat(theme.uiKeys()).containsEntry("@base", "#1234").containsEntry("@alias", "@base")
            .containsEntry("@hover", "lighten(@base, 8%, relative derived)")
            .containsEntry("Button.borderColor", "@hover").containsEntry("Panel.background", "$Button.borderColor")
            .containsEntry("Label.foreground", "contrast(@base, #000, #fff, 43%)")
            .containsEntry("List.background", "hsl(200, 80%, 40%)")
            .containsEntry("List.selectionBackground", "over(#1234, #fff)")
            .containsEntry("*.disabledForeground", "fade($Label.foreground, 42%)");
        assertThat(theme.uiKeys().get("@platformColor")).isEqualTo(java.util.Map.of(
            "os.mac", "rgba(1, 2, 3, 50%)", "os.default", "mix(#12345680, darken(@base, 10%), 25%)"));
    }

    @Test void generatedUiDataRetainsBorderNamedColorVariablesWithoutBorderObjects() {
        var theme = generate(SEEDS + """
            [ui]
            "@border" = "#ff0000"
            "@hoverBorder" = "lighten(@border, 8%)"
            "Button.borderColor" = "@border"
            "Button.hoverBorderColor" = "@hoverBorder"
            "@unsafeBorder" = "20,20,20,20,#ff0000,4,30"
            "Button.border" = "@border"
            """
        );
        assertThat(theme.uiKeys()).containsEntry("@border", "#ff0000")
            .containsEntry("@hoverBorder", "lighten(@border, 8%)")
            .containsEntry("Button.borderColor", "@border")
            .containsEntry("Button.hoverBorderColor", "@hoverBorder")
            .doesNotContainKeys("@unsafeBorder", "Button.border");
    }

    @Test void generatedUiDataRetainsOptionalColorReferencesWithoutOptionalBorderReferences() {
        var theme = generate(SEEDS + """
            [ui]
            "HelpButton.hoverBorderColor" = "$?Button.hoverBorderColor"
            "@optionalColor" = "$?Button.borderColor"
            "Panel.background" = "mix($?Button.hoverBorderColor, #fff, 25%)"
            "@unsafeBorderReference" = "$?Button.border"
            "@unsafeWildcardBorder" = "$?border"
            "@malformedOptional" = "$??Button.borderColor"
            "Button.border" = "$?Button.borderColor"
            """
        );
        assertThat(theme.uiKeys()).containsEntry("HelpButton.hoverBorderColor", "$?Button.hoverBorderColor")
            .containsEntry("@optionalColor", "$?Button.borderColor")
            .containsEntry("Panel.background", "mix($?Button.hoverBorderColor, #fff, 25%)")
            .doesNotContainKeys("@unsafeBorderReference", "@unsafeWildcardBorder", "@malformedOptional", "Button.border");
    }

    @Test void generatedUiRejectsBooleanColorsButKeepsColorIdentifiersAndBooleanConditions() {
        var theme = generate(SEEDS + """
            [ui]
            "Tree.forceFocusedSelectionForeground" = "true"
            "Probe.foreground" = "false"
            "Probe.background" = { "os.mac" = "true", "os.default" = "#123456" }
            "Probe.borderColor" = "mix(false, #fff, 25%)"
            "Probe.caretColor" = "if(true, true, #123456)"
            "ColorPalette.Gray2" = "#223344"
            "Label.foreground" = "Gray2"
            "Button.background" = "if(true, #123456, #abcdef)"
            "Button.foreground" = "if(false, Gray2, #abcdef)"
            """
        );
        assertThat(theme.uiKeys()).doesNotContainKeys("Tree.forceFocusedSelectionForeground", "Probe.foreground",
            "Probe.background", "Probe.borderColor", "Probe.caretColor")
            .containsEntry("Label.foreground", "Gray2")
            .containsEntry("Button.background", "if(true, #123456, #abcdef)")
            .containsEntry("Button.foreground", "if(false, Gray2, #abcdef)");
    }

}
