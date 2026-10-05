package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.themeimport.ThemeConverter;
import dev.jasper.ui.themeio.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.*;
import java.util.*;

import javax.swing.UIManager;

class AuthoredUiColorsTest {
    @TempDir Path directory;

    static GeneratedTheme generate(String ui, RuntimePlatform platform) {
        return ThemeGenerator.generate(
                ThemeResolver.resolve(
                        ThemeToml.parse(
                                ThemeGeneratorTest.SEEDS + "\n[ui]\n" + ui, "authored.toml")),
                platform);
    }

    @Test
    void platformAliasWildcardReferenceAndCanonicalOrder() {
        var platform =
                "\"Panel.background\"={\"os.mac\"=\"#01020380\",\"os.default\"=\"#112233\"}\n";
        assertThat(
                        generate(platform, RuntimePlatform.MAC)
                                .semantic()
                                .color(SemanticColor.SURFACE_PANEL))
                .isEqualTo(new Color(1, 2, 3, 128));
        for (var os :
                List.of(RuntimePlatform.WINDOWS, RuntimePlatform.LINUX, RuntimePlatform.OTHER))
            assertThat(generate(platform, os).semantic().color(SemanticColor.SURFACE_PANEL))
                    .isEqualTo(new Color(0x112233));
        var alias =
                generate(
                        "\"Button.startBackground\"=\"#111111\"\n"
                                + "\"Button.background\"=\"#222222\"\n",
                        RuntimePlatform.MAC);
        assertThat(alias.semantic().color(SemanticColor.SURFACE_CONTROL))
                .isEqualTo(new Color(0x222222));
        assertThat(
                        generate(
                                        "\"Button.background\"=\"#222222\"\n"
                                                + "\"Button.startBackground\"=\"#111111\"\n",
                                        RuntimePlatform.MAC)
                                .semantic()
                                .color(SemanticColor.SURFACE_CONTROL))
                .isEqualTo(new Color(0x111111));
        assertThat(
                        generate(
                                        "\"Panel.background\"=\"#111111\"\n"
                                                + "\"*.background\"=\"#334455\"\n",
                                        RuntimePlatform.MAC)
                                .semantic()
                                .color(SemanticColor.SURFACE_PANEL))
                .isEqualTo(new Color(0x334455));
        assertThat(
                        generate(
                                        "\"*.background\"=\"#334455\"\n"
                                                + "\"Panel.background\"=\"#111111\"\n",
                                        RuntimePlatform.MAC)
                                .semantic()
                                .color(SemanticColor.SURFACE_PANEL))
                .isEqualTo(new Color(0x111111));
        assertThat(
                        generate(
                                        "\"@base\"=\"#12345680\"\n"
                                                + "\"@alias\"=\"@base\"\n"
                                                + "\"Panel.background\"=\"@alias\"\n",
                                        RuntimePlatform.MAC)
                                .semantic()
                                .color(SemanticColor.SURFACE_PANEL))
                .isEqualTo(new Color(0x12, 0x34, 0x56, 0x80));
    }

    @Test
    void authoredFocusAndInputBordersSurviveSemanticInstallation() {
        var theme =
                generate(
                        "\"Component.focusColor\"=\"#123456\"\n"
                                + "\"Component.borderColor\"=\"#345678\"",
                        RuntimePlatform.MAC);
        assertThat(theme.semantic().color(SemanticColor.BORDER_FOCUS))
                .isEqualTo(new Color(0x123456));
        Edt.run(
                () -> {
                    var old = UIManager.getLookAndFeel();
                    try {
                        FlatLafInstaller.install(theme);
                        assertThat(UIManager.getColor("Component.borderColor"))
                                .isEqualTo(new Color(0x345678));
                    } finally {
                        try {
                            UIManager.setLookAndFeel(old);
                        } catch (Exception e) {
                            throw new AssertionError(e);
                        }
                    }
                });
    }

    @Test
    void deterministicColorFunctionsPreserveChannelsAndAlpha() {
        var examples =
                Map.ofEntries(
                        Map.entry("rgb(1,2,3)", new Color(1, 2, 3)),
                                Map.entry("rgba(#123456,50%)", new Color(0x12, 0x34, 0x56, 128)),
                        Map.entry("hsl(120,100%,50%)", Color.GREEN),
                                Map.entry("hsla(240,100%,50%,50%)", new Color(0, 0, 255, 128)),
                        Map.entry("lighten(#000,20%)", new Color(51, 51, 51)),
                                Map.entry("darken(#fff,20%)", new Color(204, 204, 204)),
                        Map.entry("mix(#f008,#00f8,50%)", new Color(128, 0, 128, 136)),
                                Map.entry("tint(#000,20%)", new Color(51, 51, 51)),
                        Map.entry("shade(#fff,20%)", new Color(204, 204, 204)),
                                Map.entry("fade(#f00,20%)", new Color(255, 0, 0, 51)),
                        Map.entry("spin(#f00,120)", Color.GREEN),
                                Map.entry("changeHue(#f00,240)", Color.BLUE),
                        Map.entry("changeSaturation(#f00,0%)", new Color(128, 128, 128)),
                                Map.entry("desaturate(#f00,100%)", new Color(128, 128, 128)),
                        Map.entry("changeLightness(#f00,100%)", Color.WHITE),
                                Map.entry("fadein(#f000,100%)", Color.RED),
                        Map.entry("fadeout(#f00,100%)", new Color(255, 0, 0, 0)),
                                Map.entry("changeAlpha(#f00,0%)", new Color(255, 0, 0, 0)),
                        Map.entry("over(#f008,#00f)", new Color(136, 0, 119)),
                                Map.entry("contrast(#000,#111,#eee)", new Color(238, 238, 238)),
                        Map.entry("if(false,#111,#222)", new Color(34, 34, 34)),
                                Map.entry("systemColor(window,#123456)", new Color(0x123456)));
        examples.forEach(
                (expression, expected) ->
                        assertThat(
                                        generate(
                                                        "\"Panel.background\"=\""
                                                                + expression
                                                                + "\"",
                                                        RuntimePlatform.MAC)
                                                .semantic()
                                                .color(SemanticColor.SURFACE_PANEL))
                                .as(expression)
                                .isEqualTo(expected));
    }

    @Test
    void deterministicFunctionsMatchPinnedFlatLafMaterialization() {
        for (String expression :
                List.of(
                        "contrast(#808080,#111,#eee)",
                        "contrast(#ff0000,#111,#eee,30%)",
                        "lighten(#999,10%,autoInverse)",
                        "lighten(#ddd,10%,derived)",
                        "darken(#333,10%,relative derived)",
                        "saturate(#998877,10%,autoInverse)",
                        "fadein(#12345688,10%,relative)",
                        "over(#12345688,#aabbcc44)",
                        "mix(#12345688,#aabbcc44,27%)",
                        "hsla(207,63%,42%,57%)",
                        "spin(#123456,47)",
                        "desaturate(#345678,17%)",
                        "changeLightness(#123456,46%)",
                        "lighten(#ddeeff,10%,noAutoInverse derived)",
                        "tint(#12345688,20%)",
                        "shade(#abcdef99,12%)")) {
            var expected =
                    (Color)
                            com.formdev.flatlaf.FlatLaf.parseDefaultsValue(
                                    "Probe.background", expression, Color.class);
            assertThat(
                            generate(
                                            "\"Panel.background\"=\"" + expression + "\"",
                                            RuntimePlatform.MAC)
                                    .semantic()
                                    .color(SemanticColor.SURFACE_PANEL))
                    .as(expression)
                    .isEqualTo(expected);
        }
    }

    @Test
    void propertyReferencesUseEffectiveWildcardAndAliasOrder() {
        assertThat(
                        generate(
                                        "\"Button.background\"=\"#111111\"\n"
                                                + "\"*.background\"=\"#334455\"\n"
                                                + "\"Panel.background\"=\"$Button.background\"",
                                        RuntimePlatform.MAC)
                                .semantic()
                                .color(SemanticColor.SURFACE_PANEL))
                .isEqualTo(new Color(0x334455));
        assertThat(
                        generate(
                                        "\"Button.background\"=\"#111111\"\n"
                                                + "\"Button.startBackground\"=\"#334455\"\n"
                                                + "\"Panel.background\"=\"$Button.background\"",
                                        RuntimePlatform.MAC)
                                .semantic()
                                .color(SemanticColor.SURFACE_PANEL))
                .isEqualTo(new Color(0x334455));
    }

    @Test
    void invalidReferencesAndSystemFallbackDiagnoseOriginAndTerminate() {
        for (var ui :
                List.of(
                        "\"Panel.background\"=\"@missing\"",
                        "\"@a\"=\"@b\"\n\"@b\"=\"@a\"\n\"Panel.background\"=\"@a\"",
                        "\"Panel.background\"=\"systemColor(window)\"")) {
            assertThatThrownBy(() -> generate(ui, RuntimePlatform.MAC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("authored.toml")
                    .hasMessageContaining("Panel.background")
                    .hasMessageContaining("fallback");
        }
    }

    @Test
    void carbonCanonicalRolesPreserveAuthoredIntentOverVendorPostJsonHeuristics() throws Exception {
        Path imported = directory.resolve("carbon.theme.json");
        Files.writeString(
                imported,
                "{\"name\":\"Carbon\",\"dark\":true,\"ui\":{\"Panel.background\":\"#202020\",\"TextField.background\":\"#112233\"}}");
        var document = ThemeConverter.convert(imported).getFirst().document();
        assertThat(document.tokens()).doesNotContainKey(Token.INPUT_BACKGROUND);
        assertThat(document.ui()).containsEntry("TextField.background", "#112233");
        var theme = ThemeGenerator.generate(document, RuntimePlatform.MAC);
        assertThat(theme.semantic().color(SemanticColor.SURFACE_INPUT))
                .isEqualTo(new Color(0x112233));
        Edt.run(
                () -> {
                    var old = UIManager.getLookAndFeel();
                    try {
                        FlatLafInstaller.install(theme);
                        assertThat(UIManager.getColor("TextField.background"))
                                .isEqualTo(new Color(0x112233));
                    } finally {
                        try {
                            UIManager.setLookAndFeel(old);
                        } catch (Exception failure) {
                            throw new AssertionError(failure);
                        }
                    }
                });
    }

    @Test
    void nativeAndImportedCompleteThemesReachSemanticInstalledAndPaintedColors() throws Exception {
        Path imported = directory.resolve("colors.theme.json");
        Files.writeString(
                imported,
                """
                {"name":"Authored","dark":true,"colors":{"chosen":"#123456"},"ui":{"Panel.background":{"os.mac":"chosen","os.default":"#345678"}}}
                """);
        var document = ThemeConverter.convert(imported).getFirst().document();
        assertThat(document.uiComplete()).contains(true);
        var importedTheme = ThemeGenerator.generate(document, RuntimePlatform.MAC);
        var nativeTheme = generate("\"Panel.background\"=\"#123456\"", RuntimePlatform.MAC);
        Edt.run(
                () -> {
                    var old = UIManager.getLookAndFeel();
                    try {
                        for (var theme : List.of(nativeTheme, importedTheme)) {
                            assertThat(theme.semantic().color(SemanticColor.SURFACE_PANEL))
                                    .isEqualTo(new Color(0x123456));
                            FlatLafInstaller.install(theme);
                            assertThat(UIManager.getColor("Panel.background"))
                                    .isEqualTo(new Color(0x123456));
                            var context = KitFixtures.context();
                            context.binding().publish(new ThemePair(theme, theme));
                            var panel =
                                    new Nodes.Panel(
                                            "panel",
                                            ControlState.READY,
                                            new Nodes.Header(
                                                    "header",
                                                    ControlState.READY,
                                                    "Panel",
                                                    OptionalInt.empty(),
                                                    Optional.empty(),
                                                    Optional.empty(),
                                                    List.of()),
                                            Optional.empty(),
                                            new Nodes.Stack(
                                                    "body",
                                                    ControlState.READY,
                                                    Nodes.LayoutRole.VERTICAL,
                                                    List.of()),
                                            Optional.empty());
                            try (var view = new KitView(context, new ViewSpec(panel), e -> {})) {
                                assertThat(KitFixtures.render(view, 240, 180, 1).getRGB(120, 100))
                                        .isEqualTo(new Color(0x123456).getRGB());
                            } finally {
                                context.binding().close();
                                context.icons().close();
                            }
                        }
                    } finally {
                        try {
                            UIManager.setLookAndFeel(old);
                        } catch (Exception e) {
                            throw new AssertionError(e);
                        }
                    }
                });
    }
}
