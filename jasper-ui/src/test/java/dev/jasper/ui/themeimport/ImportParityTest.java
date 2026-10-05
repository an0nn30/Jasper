package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.*;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ImportParityTest {
    @TempDir Path home;

    @Test void stagedArchivePublicRouteAndPublicationUtilitiesKeepBounds() throws Exception {
        Path staged = ThemeFixtures.jar(home.resolve("download.part"), ThemeFixtures.entries(
            "META-INF/plugin.xml", ThemeFixtures.pluginXml("Staged", "theme.json"),
            "theme.json", "{\"name\":\"Staged\",\"dark\":true,\"ui\":{\"Panel.background\":\"#123456\"}}"));
        byte[] before = Files.readAllBytes(staged);
        var batch = ThemeConverter.archive(staged);
        assertThat(batch.failures()).isEmpty();
        assertThat(batch.results()).singleElement().satisfies(result -> assertThat(result.document().name()).isEqualTo("Staged"));
        assertThat(Files.readAllBytes(staged)).isEqualTo(before);
        assertThat(ThemeConverter.MAX_ARCHIVE).isEqualTo(64L << 20);
        assertThat(ThemeConverter.MAX_FILE).isEqualTo(4 << 20);
        assertThat(ThemeConverter.MAX_PROVIDERS).isEqualTo(64);
        assertThat(ThemeConverter.MAX_NESTED_JARS).isEqualTo(16);
        assertThat(ThemeSources.hash(new byte[0])).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThatThrownBy(() -> ThemeSources.safe(home, "../escape")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ThemeConverter.explicitTokens(Map.of("Panel.background", "#123456", "Button.arc", 99)))
            .containsEntry(Token.SURFACE, new Color(0x123456));
    }

    private Path file(String name, String text) throws Exception {
        Path path = home.resolve(name);
        Files.createDirectories(path.getParent()); Files.writeString(path, text); return path;
    }
    private ThemeDocument convert(String json) throws Exception {
        return ThemeConverter.convert(file("test.theme.json", json)).getFirst().document();
    }

    @Test void resolvesColorsBeforeDiscardingOverridesAndReportsEachKeyOnce() throws Exception {
        var result = ThemeConverter.convert(file("policy.theme.json", """
            {"name":"Policy","dark":true,"colors":{"accent":"#12345680","alias":"accent"},
             "ui":{"Panel.background":"alias","Button.arc":"alias","ButtonUI":"#123456",
             "Label.font":"#123456","Button.foreground":{"os.mac":"com.formdev.flatlaf.ui.FlatButtonUI","os.default":"accent"}}}
            """)).getFirst();
        assertThat(result.document().ui()).containsEntry("Panel.background", "#12345680")
            .containsEntry("ColorPalette.accent", "#12345680").containsEntry("ColorPalette.alias", "#12345680")
            .containsEntry("Button.foreground", Map.of("os.default", "#12345680"))
            .doesNotContainKeys("Button.arc", "ButtonUI", "Label.font");
        for (String key : List.of("Button.arc", "ButtonUI", "Label.font", "Button.foreground"))
            assertThat(result.warnings().stream().filter(warning -> warning.contains(key)).toList()).hasSize(1);
        assertThat(ThemeGenerator.generate(result.document()).uiKeys()).containsEntry("ColorPalette.accent", "#12345680");
    }

    @Test void nativeFilteringPreservesGlassSyntaxSemanticMetadataAndProvenance() throws Exception {
        var result = ThemeConverter.convert(file("native-policy.toml", """
            name='Native Policy'
            extends='Jasper Dark'
            dark=true
            license='Fixture'
            [ui]
            'Button.arc'=50
            'Panel.background'='#11223380'
            [semantic]
            'surface.card'='#123456'
            [workspace]
            opacity=0.8
            [syntax]
            keyword={color='#234567',bold=true,italic=true}
            """)).getFirst();
        assertThat(result.document().ui()).containsEntry("Panel.background", "#11223380")
            .containsEntry("Jasper.headerHighlight", "#343a42").doesNotContainKey("Button.arc");
        assertThat(result.document().workspace().levels().opacity()).isEqualTo(0.8);
        assertThat(result.document().semanticOverrides()).containsEntry(SemanticColor.SURFACE_CARD, new Color(0x123456));
        assertThat(result.document().syntax()).containsEntry(SyntaxRole.KEYWORD, new SyntaxStyle(new Color(0x234567), true, true));
        assertThat(result.document().metadata()).containsEntry("license", "Fixture");
        assertThat(result.sourceEntry()).isEqualTo("native-policy.toml");
        assertThat(result.document().origin()).isEqualTo("native-policy.toml");
        assertThat(result.warnings()).singleElement().asString().contains("Button.arc");
    }

    @Test void symlinkSourcesAndSidecarsAndEntitySidecarsFailConversion() throws Exception {
        Path theme = file("target.theme.json", "{\"name\":\"Target\"}");
        Path linked = home.resolve("linked.theme.json");
        Files.createSymbolicLink(linked, theme);
        assertThat(ThemeConverter.convertAll(linked).failures()).singleElement().satisfies(failure ->
            assertThat(failure.message()).contains("regular"));
        Path xml = file("target.xml", "<scheme name='Target'/>");
        Files.createSymbolicLink(home.resolve("linked.xml"), xml);
        assertThat(ThemeConverter.convertAll(file("sidecar.theme.json", "{\"name\":\"Sidecar\",\"editorScheme\":\"linked.xml\"}")).failures())
            .singleElement().satisfies(failure -> assertThat(failure.message()).contains("symbolic link"));
        file("entity.xml", "<!DOCTYPE scheme [<!ENTITY external SYSTEM 'file:///etc/hosts'>]><scheme name='&external;'/>");
        assertThat(ThemeConverter.convertAll(file("entity.theme.json", "{\"name\":\"Entity\",\"editorScheme\":\"entity.xml\"}")).failures())
            .singleElement().satisfies(failure -> assertThat(failure.message()).contains("DOCTYPE"));
    }

    @Test void duplicateNativeParentsKeepFilenamePrecedenceAndCanExtendLowerLayers() throws Exception {
        file("z-base.toml", "name='Duplicate'\nextends='Jasper Dark'\n[colors]\nbackground='#111111'\n");
        file("A-override.toml", "name='Duplicate'\nextends='Duplicate'\n[colors]\nbackground='#222222'\n");
        var result = ThemeConverter.convert(file("child.toml", "name='Child'\nextends='duplicate'\n")).getFirst();
        assertThat(result.document().seeds()).containsEntry(Seed.BACKGROUND, new Color(0x222222));
        assertThat(result.document().ansi()).hasSize(16);
    }

    @Test void layeredNestedAndDottedKeysKeepLastDefinitionAndChildOrder() {
        var files = Map.of("parent.json", """
            {"name":"Parent","ui":{"Panel.background":"#111111","*.background":"#222222","Label.foreground":"#333333"}}
            """, "child.json", """
            {"name":"Child","parentTheme":"Parent","ui":{"*.background":"#444444","Panel":{"background":"#000000"},"Panel.background":"#555555"}}
            """);
        var resolved = new ThemeLoader(files::get, Map.of("Parent", "parent.json")).load("child.json");
        assertThat(resolved.ui().keySet()).containsExactly("Label.foreground", "*.background", "Panel.background");
        assertThat(resolved.ui()).containsEntry("*.background", "#444444").containsEntry("Panel.background", "#555555");
    }

    @Test void explicitColoursApplyWildcardsInOrderAndKeepAlpha() {
        var resolved = new ThemeLoader(Map.of("child.json", """
            {"name":"Child","ui":{"SearchEverywhere.Header.background":"#111111","*.background":"#222222",
              "ToolWindow.background":"#333333","EditorTabs.hoverBackground":"#00000019","@accentBaseColor":"#61afef"}}
            """)::get, Map.of()).load("child.json");
        assertThat(resolved.colors()).containsEntry("SearchEverywhere.Header.background", new Color(0x222222))
            .containsEntry("ToolWindow.background", new Color(0x333333))
            .containsEntry("EditorTabs.hoverBackground", new Color(0, 0, 0, 0x19))
            .doesNotContainKeys("*.background", "@accentBaseColor");
    }

    @Test void nativeParentsUseDeclaredNamesAndLocalFilenamePrecedence() throws Exception {
        file("ocean-base.toml", "name='Ocean Base'\nextends='Jasper Dark'\n[colors]\nbackground='#123456'\n");
        var child = ThemeConverter.convert(file("child.toml", "name='Child'\nextends='oCEan bASE'\n")).getFirst().document();
        assertThat(child.seeds()).containsEntry(Seed.BACKGROUND, new Color(0x123456));
        file("a-override.toml", "name='Jasper Dark'\nextends='Jasper Dark'\n[colors]\nbackground='#654321'\n");
        var override = ThemeConverter.convert(home.resolve("a-override.toml")).getFirst().document();
        assertThat(override.seeds()).containsEntry(Seed.BACKGROUND, new Color(0x654321));
        var local = ThemeConverter.convert(file("local-child.toml", "name='Local Child'\nextends='Jasper Dark'\n")).getFirst().document();
        assertThat(local.seeds()).containsEntry(Seed.BACKGROUND, new Color(0x654321));
    }

    @Test void flattensParentsAndSyntaxWithoutLosingFlags() throws Exception {
        file("Parent.theme.json", """
            {"name":"Parent","parentTheme":"Darcula","colors":{"base":"#223344"},
             "ui":{"Panel.background":"base","Button.arc":7},"icons":{"ColorPalette":{"Actions.Red":"#ab1234"}}}
            """);
        file("child.xml", """
            <scheme name="Child" parent_scheme="Darcula"><attributes>
              <option name="CONSOLE_BLUE_BRIGHT_OUTPUT"><value><option name="FOREGROUND" value="010203"/></value></option>
              <option name="DEFAULT_KEYWORD"><value><option name="FOREGROUND" value="aabbcc"/><option name="FONT_TYPE" value="3"/></value></option>
            </attributes></scheme>
            """);
        var result = convert("""
            {"name":"Child","dark":true,"author":"Fixture Author","parentTheme":"Parent",
             "editorScheme":"child.xml","ui":{"Component.focusColor":"#987654"}}
            """);
        var saved = ThemeToml.parse(ThemeTomlWriter.write(result), "converted.toml");
        assertThat(saved.parent()).isEmpty();
        assertThat(saved.ansi().get(12)).isEqualTo(new Color(0x010203));
        assertThat(saved.syntax().get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xaabbcc), true, true));
        assertThat(saved.metadata()).containsEntry("author", "Fixture Author");
        assertThat(saved.ui()).doesNotContainKey("Button.arc").containsEntry("Panel.background", "#223344");
        assertThat(saved.icons()).containsKey("ColorPalette");
        assertThat(saved.tokens()).containsEntry(Token.SURFACE, new Color(0x223344));
    }

    @Test void conversionMatchesEveryColorInTheExistingGolden() throws Exception {
        Path source = Path.of(getClass().getResource("golden/child.theme.json").toURI());
        String actual = ThemeTomlWriter.write(ThemeConverter.convert(source).getFirst().document());
        var expected = ThemeToml.parse(Files.readString(source.resolveSibling("child.colors.expected.toml")), "golden");
        var document = ThemeToml.parse(actual, "converted");
        assertThat(document.seeds()).isEqualTo(expected.seeds());
        assertThat(document.ansi()).isEqualTo(expected.ansi());
        assertThat(document.syntax()).isEqualTo(expected.syntax());
        assertThat(document.tokens()).isEqualTo(expected.tokens());
        assertThat(document.icons()).isEqualTo(expected.icons());
        assertThat(document.ui()).containsExactlyEntriesOf(expected.ui());
    }

    @Test void reportsEveryConsoleFallback() throws Exception {
        var absent = ThemeConverter.convert(file("absent.theme.json", "{\"name\":\"No console\",\"dark\":true}")).getFirst();
        assertThat(absent.warnings()).anyMatch(s -> s.contains("Jasper Dark") && s.contains("console"));
        file("empty.xml", "<scheme name='Empty' parent_scheme='Default'><colors><option name='CONSOLE_BACKGROUND_KEY' value='123456'/></colors></scheme>");
        var partial = ThemeConverter.convert(file("partial.theme.json", "{\"name\":\"Partial\",\"editorScheme\":\"empty.xml\"}")).getFirst();
        assertThat(partial.document().seeds()).containsEntry(Seed.BACKGROUND, new Color(0x123456));
    }

    @Test void preservesExplicitIntellijAccent() throws Exception {
        var document = convert("""
            {"name":"Accent","dark":true,"colors":{"accent":"#010203"},
             "ui":{"Component.focusColor":"#111111","EditorTabs.underlineColor":"#222222","Button.default.startBackground":"#333333"}}
            """);
        assertThat(document.seeds()).containsEntry(Seed.ACCENT, new Color(0x010203));
    }

    @Test void inverseUsesSuppliedKeysOnly() {
        var ui = new LinkedHashMap<String, Object>();
        ui.put("*.background", "#111111"); ui.put("Panel.background", "#222222");
        ui.put("Button.startBackground", "#334455");
        assertThat(WorkspaceTokenKeys.explicitTokens(ui)).containsEntry(Token.SURFACE, new Color(0x222222))
            .containsEntry(Token.BUTTON_BACKGROUND, new Color(0x334455)).doesNotContainKey(Token.ERROR);
        ui.remove("*.background"); ui.put("*.background", "#556677");
        assertThat(WorkspaceTokenKeys.explicitTokens(ui)).containsEntry(Token.SURFACE, new Color(0x556677));
    }

    @Test void unrelatedControlColorsDoNotOverrideEffectiveDefaults() {
        assertThat(WorkspaceTokenKeys.explicitTokens(Map.of(
            "ToolTip.background", "#123456", "ProgressBar.foreground", "#234567",
            "ScrollPane.background", "#345678", "Button.toolbar.hoverBackground", "#456789")))
            .doesNotContainKeys(Token.SURFACE_RAISED, Token.ACCENT, Token.SCROLLBAR_TRACK, Token.HOVER);
    }

    @Test void conversionIsPlatformIndependent() throws Exception {
        var document = convert("""
            {"name":"Per OS","ui":{"Panel.background":{"os.mac":"#010203","os.default":"#112233"},
            "Component.focusColor":{"os.mac":"#223344","os.default":"#445566"}}}
            """);
        assertThat(document.tokens()).doesNotContainKeys(Token.SURFACE, Token.FOCUS_RING);
        assertThat(document.seeds()).containsEntry(Seed.ACCENT, new Color(0x445566));
        assertThat(document.ui().get("Panel.background")).isEqualTo(Map.of("os.mac", "#010203", "os.default", "#112233"));
    }

    @Test void allProvidersAndFailuresSurvive() throws Exception {
        Path archive = home.resolve("many.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : Map.of("META-INF/plugin.xml", "<idea-plugin><name>Many</name><extensions defaultExtensionNs='com.intellij'><themeProvider path='/one.theme.json'/><themeProvider path='/two.theme.json'/><themeProvider path='/bad.theme.json'/></extensions></idea-plugin>",
                    "one.theme.json", "{\"name\":\"One\"}", "two.theme.json", "{\"name\":\"Two\"}", "bad.theme.json", "broken").entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        var batch = ThemeConverter.convertAll(archive);
        assertThat(batch.results()).hasSize(2); assertThat(batch.failures()).hasSize(1);
        assertThat(batch.failures().getFirst().sourceEntry()).contains("bad.theme.json");
        assertThatThrownBy(() -> ThemeConverter.convert(archive)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void archiveParentsAndSchemeSidecarsResolveInsideTheArchive() throws Exception {
        Path archive = home.resolve("parents.jar");
        dev.jasper.ui.themeimport.ThemeFixtures.jar(archive, dev.jasper.ui.themeimport.ThemeFixtures.entries(
            "META-INF/plugin.xml", dev.jasper.ui.themeimport.ThemeFixtures.pluginXml("Parents", "themes/child.theme.json"),
            "themes/child.theme.json", "{\"name\":\"Child\",\"parentTheme\":\"Local\",\"editorScheme\":\"child.xml\"}",
            "themes/Local.theme.json", "{\"name\":\"Local\",\"ui\":{\"Panel.background\":\"#123456\"}}",
            "themes/child.xml", "<scheme name='Child' parent_scheme='Local'/>",
            "themes/Local.xml", "<scheme name='Local' parent_scheme='Default'><attributes><option name='DEFAULT_KEYWORD'><value><option name='FOREGROUND' value='abcdef'/></value></option></attributes></scheme>"));
        var result = ThemeConverter.convert(archive).getFirst();
        assertThat(result.document().ui()).containsEntry("Panel.background", "#123456");
        assertThat(result.document().syntax().get(SyntaxRole.KEYWORD).color()).isEqualTo(new Color(0xabcdef));
        assertThat(result.sourceEntry()).isEqualTo("themes/child.theme.json");
    }

    @Test void archiveSchemesMayUseRootRelativePathsWithoutALeadingSlash() {
        Path archive = home.resolve("root-relative.jar");
        dev.jasper.ui.themeimport.ThemeFixtures.jar(archive, dev.jasper.ui.themeimport.ThemeFixtures.entries(
            "META-INF/plugin.xml", dev.jasper.ui.themeimport.ThemeFixtures.pluginXml("Root", "themes/test.theme.json"),
            "themes/test.theme.json", "{\"name\":\"Root\",\"editorScheme\":\"colors/test.xml\"}",
            "colors/test.xml", dev.jasper.ui.themeimport.ThemeFixtures.scheme("Root", "Darcula", "123456", "654321")));
        var result = ThemeConverter.convert(archive).getFirst();
        assertThat(result.document().seeds()).containsEntry(Seed.BACKGROUND, new Color(0x123456));
    }

    @Test void rejectsInvalidParentsAndBoundedSidecars() throws Exception {
        for (String json : List.of("{\"name\":\"Bad\",\"editorScheme\":\"../secret.xml\"}",
                "{\"name\":\"Bad\",\"colors\":{\"a\":\"b\",\"b\":\"a\"}}")) {
            Path source = file("bad.theme.json", json);
            assertThat(ThemeConverter.convertAll(source).failures()).isNotEmpty();
        }
        var missing = ThemeConverter.convert(file("orphan.theme.json", "{\"name\":\"Missing parent\",\"parentTheme\":\"Missing\"}")).getFirst();
        assertThat(missing.warnings()).anyMatch(warning -> warning.contains("parentTheme") && warning.contains("instead"));
        file("A.theme.json", "{\"name\":\"A\",\"parentTheme\":\"B\"}");
        Path cycle = file("B.theme.json", "{\"name\":\"B\",\"parentTheme\":\"A\"}");
        assertThat(ThemeConverter.convertAll(cycle).failures()).isNotEmpty();
        Path huge = file("huge.theme.json", " ".repeat((4 << 20) + 1));
        assertThat(ThemeConverter.convertAll(huge).failures()).isNotEmpty();
    }

    @Test void alacrittyCopiesTerminalColorsAndDerivesAccent() throws Exception {
        String text = "[colors.primary]\nbackground='#101010'\nforeground='0xabcdef'\n[colors.cursor]\ncursor='#123456'\n[colors.selection]\nbackground='#234567'\n";
        for (String section : List.of("normal", "bright")) {
            text += "[colors." + section + "]\n";
            for (String name : ThemeDocument.ANSI_NAMES) text += name + "='#101010'\n";
        }
        var converted = ThemeConverter.convert(file("my_theme.toml", text + "[colors.dim]\nblue='#987654'\n")).getFirst().document();
        assertThat(converted.name()).isEqualTo("My Theme");
        assertThat(converted.ansi().values()).containsOnly(new Color(0x101010));
        assertThat(converted.seeds()).containsEntry(Seed.CURSOR, new Color(0x123456)).containsEntry(Seed.SELECTION, new Color(0x234567));
        assertThat(converted.seeds().get(Seed.ACCENT)).isEqualTo(new Color(0x646464));
        assertThat(ThemeTomlWriter.write(converted)).doesNotContain("dim");
    }

    @Test void resolvesLocalSchemeParentsAndRejectsTheirCycles() throws Exception {
        file("Local.xml", "<scheme name='Local' parent_scheme='Default'><attributes><option name='BASE_KEY'><value><option name='FOREGROUND' value='654321'/><option name='FONT_TYPE' value='3'/></value></option></attributes></scheme>");
        file("child.xml", "<scheme name='Child' parent_scheme='Local'><attributes><option name='DEFAULT_KEYWORD' baseAttributes='BASE_KEY'/></attributes></scheme>");
        var document = convert("{\"name\":\"Scheme child\",\"editorScheme\":\"child.xml\"}");
        assertThat(document.syntax().get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0x654321), true, true));
        file("Local.xml", "<scheme name='Local' parent_scheme='Child'/>");
        Path source = file("cycle.theme.json", "{\"name\":\"Cycle\",\"editorScheme\":\"child.xml\"}");
        assertThat(ThemeConverter.convertAll(source).failures()).anyMatch(failure -> failure.message().contains("cycle"));
    }

    @Test void nativeImportsFlattenAndMalformedNativeIsNotAlacritty() throws Exception {
        var document = ThemeConverter.convert(file("native.toml", "name='Native'\nextends='Jasper Dark'\n[tokens]\nborder='#123456'\n")).getFirst().document();
        assertThat(document.parent()).isEmpty();
        assertThat(document.tokens()).containsEntry(Token.BORDER, new Color(0x123456));
        var bad = ThemeConverter.convertAll(file("bad.toml", "name='Bad'\n[colors]\nbackground=1\n"));
        assertThat(bad.failures()).singleElement().satisfies(failure -> assertThat(failure.message()).contains("background"));
    }
}
