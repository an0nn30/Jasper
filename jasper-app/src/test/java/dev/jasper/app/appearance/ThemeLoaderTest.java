package dev.jasper.app.appearance;

import com.formdev.flatlaf.json.Json;
import java.awt.Color;
import java.io.StringReader;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ThemeLoaderTest {
    private static ThemeLoader loader(Map<String, String> files) {
        return new ThemeLoader(files::get,
            Map.of("Parent", "parent.json", "Child", "child.json", "Loop A", "a.json", "Loop B", "b.json"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(ThemeLoader.Resolved resolved) throws Exception {
        return (Map<String, Object>) Json.parse(new StringReader(resolved.json()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ui(ThemeLoader.Resolved resolved) throws Exception {
        return (Map<String, Object>) json(resolved).get("ui");
    }

    /** A map in the order of its alternating keys and values. */
    private static Map<String, Object> ordered(Object... keysAndValues) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keysAndValues.length; i += 2) map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return map;
    }

    @Test void childEntriesReplaceTheParentsAndFollowTheChildsOrder() throws Exception {
        var resolved = loader(Map.of(
            "parent.json", """
                {"name": "Parent", "ui": {"A.x": "#111111", "*.background": "#222222", "B.y": "#333333"}}""",
            "child.json", """
                {"name": "Child", "parentTheme": "Parent", "ui": {"*.background": "#444444", "A.x": "#555555"}}"""))
            .load("child.json");
        var ui = ui(resolved);
        assertThat(List.copyOf(ui.keySet())).containsExactly("B.y", "*.background", "A.x");
        assertThat(ui).containsEntry("*.background", "#444444").containsEntry("A.x", "#555555").containsEntry("B.y", "#333333");
        assertThat(resolved.name()).isEqualTo("Child");
    }

    @Test void nestedObjectsAndDottedKeysNameTheSameKey() throws Exception {
        var ui = ui(loader(Map.of(
            "parent.json", """
                {"name": "Parent", "ui": {"Button": {"arc": 3, "default": {"foreground": "#ffffff"}}}}""",
            "child.json", """
                {"name": "Child", "parentTheme": "Parent", "ui": {"Button.arc": 5}}"""))
            .load("child.json"));
        assertThat(ui).containsOnlyKeys("Button.default.foreground", "Button.arc").containsEntry("Button.arc", "5");
    }

    @Test @SuppressWarnings("unchecked")
    void namedColoursResolveThroughOtherNamesIncludingPerOsValues() throws Exception {
        var resolved = loader(Map.of("child.json", """
            {"name": "Child", "colors": {"grey15": "#F2F2F2", "panel": "grey15"},
             "ui": {"Panel.background": "panel", "Label.foreground": {"os.mac": "panel", "os.default": "#000000"}}}"""))
            .load("child.json");
        var ui = ui(resolved);
        assertThat(ui).containsEntry("Panel.background", "#f2f2f2");
        assertThat((Map<String, Object>) ui.get("Label.foreground")).containsEntry("os.mac", "#f2f2f2").containsEntry("os.default", "#000000");
        assertThat(resolved.colors()).containsEntry("Panel.background", new Color(0xf2f2f2));
    }

    @Test void resolvingA20000LinkChainOfNamedColoursIsNotCubic() throws Exception {
        // "link" (not hex digits) keeps a name like "link10000" from ever accidentally matching the hex pattern.
        var json = new StringBuilder("{\"name\": \"Child\", \"colors\": {");
        for (int i = 0; i < 20_000; i++) {
            if (i > 0) json.append(',');
            json.append("\"link").append(i).append("\":\"").append(i == 19_999 ? "#123456" : "link" + (i + 1)).append('"');
        }
        json.append("}, \"ui\": {");
        for (int i = 0; i < 20_000; i++) {
            if (i > 0) json.append(',');
            json.append("\"k").append(i).append("\":\"link").append(i).append('"');
        }
        json.append("}}");
        var resolved = assertTimeoutPreemptively(Duration.ofSeconds(5),
            () -> loader(Map.of("child.json", json.toString())).load("child.json"));
        var ui = ui(resolved);
        for (int i = 0; i < 20_000; i++) assertThat(ui).as("k" + i).containsEntry("k" + i, "#123456");
    }

    @Test void valuesThatNameNoThemeColourPassThroughUnchanged() throws Exception {
        var resolved = loader(Map.of("child.json", """
            {"name": "Child", "colors": {"panel": "#f2f2f2"},
             "ui": {"WelcomeScreen.defaultBackground": "Gray2", "TabbedPane.tabFillStyle": "underline"}}"""))
            .load("child.json");
        assertThat(ui(resolved)).containsEntry("WelcomeScreen.defaultBackground", "Gray2").containsEntry("TabbedPane.tabFillStyle", "underline");
        assertThat(resolved.colors()).isEmpty();
    }

    @Test void intellijImplementationClassesAreDropped() throws Exception {
        var ui = ui(loader(Map.of("child.json", """
            {"name": "Child", "ui": {"Button.UI": "com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI",
              "ScrollPane.border": "com.intellij.ide.ui.laf.darcula.ui.DarculaScrollPaneBorder",
              "InternalFrame.border": {"os.windows": "com.intellij.ide.ui.laf.darcula.ui.DarculaInternalBorder"},
              "Panel.background": "#f2f2f2"}}"""))
            .load("child.json"));
        assertThat(ui).containsOnlyKeys("Panel.background");
    }

    @Test void blankOrNullUiValuesAreDroppedWithAWarningNamingEachKey() throws Exception {
        var resolved = loader(Map.of("child.json", """
            {"name": "Child", "ui": {"Label.foreground": "", "Panel.background": "null", "A.x": "#010101"}}"""))
            .load("child.json");
        assertThat(ui(resolved)).containsOnlyKeys("A.x");
        assertThat(resolved.warnings()).hasSize(2)
            .anySatisfy(warning -> assertThat(warning).contains("child.json").contains("\"Label.foreground\""))
            .anySatisfy(warning -> assertThat(warning).contains("child.json").contains("\"Panel.background\""));
    }

    @Test void anyKeyEndingInUiIsDroppedRegardlessOfValue() throws Exception {
        var ui = ui(loader(Map.of("child.json", """
            {"name": "Child", "ui": {"PanelUI": "#f2f2f2", "Button.UI": "#f2f2f2", "MenuBar.UI": "#f2f2f2",
              "Panel.background": "#f2f2f2"}}"""))
            .load("child.json"));
        assertThat(ui).containsOnlyKeys("Panel.background");
    }

    @Test void classNameLookingValuesAreDroppedUnlessTheyAreFlatLaf() throws Exception {
        var ui = ui(loader(Map.of("child.json", """
            {"name": "Child", "ui": {"CheckBox.icon": "probe.Other", "CheckBox.icon2": "com.formdev.flatlaf.icons.FlatCheckBoxIcon",
              "A.a": "#123456", "A.b": "1,2,3,4", "A.c": "true", "A.d": "Arial"}}"""))
            .load("child.json"));
        assertThat(ui).containsOnlyKeys("CheckBox.icon2", "A.a", "A.b", "A.c", "A.d")
            .containsEntry("CheckBox.icon2", "com.formdev.flatlaf.icons.FlatCheckBoxIcon")
            .containsEntry("A.a", "#123456").containsEntry("A.b", "1,2,3,4")
            .containsEntry("A.c", "true").containsEntry("A.d", "Arial");
    }

    @Test void aClassNameWithSurroundingSpacesIsStillDropped() throws Exception {
        var ui = ui(loader(Map.of("child.json", """
            {"name": "Child", "ui": {"CheckBox.icon": "  probe.Other ", "CheckBox.icon2": " com.formdev.flatlaf.icons.FlatCheckBoxIcon",
              "A.a": "#123456"}}"""))
            .load("child.json"));
        assertThat(ui).containsOnlyKeys("CheckBox.icon2", "A.a");
    }

    @Test @SuppressWarnings("unchecked")
    void loadResolvesNamedColoursInIconsAndKeepsTheResolvedTables() throws Exception {
        var resolved = loader(Map.of(
            "parent.json", """
                {"name": "Parent", "colors": {"red": "#C75450"}, "ui": {"A.x": "red"}}""",
            "child.json", """
                {"name": "Child", "parentTheme": "Parent", "colors": {"warn": "red", "half": "#11223344"},
                 "ui": {"B.y": "warn"}, "icons": {"ColorPalette": {"Actions.Red": "warn", "Actions.Grey": "#afb1b3"}}}"""))
            .load("child.json");
        assertThat(resolved.ui()).containsExactlyEntriesOf(ordered("A.x", "#c75450", "B.y", "#c75450"));
        assertThat(resolved.icons()).containsOnlyKeys("ColorPalette");
        assertThat((Map<String, Object>) resolved.icons().get("ColorPalette"))
            .containsEntry("Actions.Red", "#c75450").containsEntry("Actions.Grey", "#afb1b3");
        assertThat(resolved.namedColors()).as("parents' names first, each resolved to a literal")
            .containsExactly(entry("red", "#c75450"), entry("warn", "#c75450"), entry("half", "#11223344"));
        assertThat((Map<String, Object>) json(resolved).get("icons")).isEqualTo(resolved.icons());
    }

    @Test @SuppressWarnings("unchecked")
    void jsonWritesFlatLafInputForResolvedEntries() throws Exception {
        var ui = ordered("Panel.background", "#010203", "Label.foreground", Map.of("os.default", "#040506"));
        var written = (Map<String, Object>) Json.parse(new StringReader(
            ThemeLoader.json("Name \"quoted\"", true, "Jasper", ui, Map.of())));
        assertThat(List.copyOf(written.keySet())).as("no icons when there are none")
            .containsExactly("name", "dark", "author", "colors", "ui");
        assertThat(written).containsEntry("name", "Name \"quoted\"").containsEntry("dark", "true").containsEntry("author", "Jasper")
            .containsEntry("colors", Map.of());
        assertThat((Map<String, Object>) written.get("ui")).containsExactlyEntriesOf(ui);
        var withIcons = (Map<String, Object>) Json.parse(new StringReader(ThemeLoader.json("Light", false, "", Map.of(),
            Map.of("ColorPalette", Map.of("Actions.Red", "#c75450")))));
        assertThat(withIcons).containsEntry("dark", "false").containsEntry("ui", Map.of())
            .containsEntry("icons", Map.of("ColorPalette", Map.of("Actions.Red", "#c75450")));
    }

    @Test void explicitColoursApplyWildcardsInOrderAndKeepAlpha() {
        var resolved = loader(Map.of("child.json", """
            {"name": "Child", "ui": {"SearchEverywhere.Header.background": "#111111", "*.background": "#222222",
              "ToolWindow.background": "#333333", "EditorTabs.hoverBackground": "#00000019", "@accentBaseColor": "#61afef"}}"""))
            .load("child.json");
        assertThat(resolved.colors())
            .containsEntry("SearchEverywhere.Header.background", new Color(0x222222))
            .containsEntry("ToolWindow.background", new Color(0x333333))
            .containsEntry("EditorTabs.hoverBackground", new Color(0, 0, 0, 0x19))
            .doesNotContainKeys("*.background", "@accentBaseColor");
    }

    @Test void aThemeWithoutAuthorOrDarkFlagIsStillAValidFlatLafInput() throws Exception {
        var resolved = loader(Map.of("child.json", """
            {"name": "Child", "ui": {}}""")).load("child.json");
        // FlatLaf 3.7 fails on a theme without an author; its parser returns every scalar as a String.
        assertThat(json(resolved)).containsEntry("author", "").containsEntry("dark", "false");
        assertThat(resolved.dark()).isFalse();
        assertThat(loader(Map.of("child.json", """
            {"name": "Child", "dark": true}""")).load("child.json").dark()).isTrue();
    }

    @Test void brokenThemesFailNamingTheFileAndTheKey() {
        assertThatThrownBy(() -> loader(Map.of()).load("missing.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("missing.json");
        assertThatThrownBy(() -> loader(Map.of("child.json", """
            {"name": "Child", "parentTheme": "Nobody"}""")).load("child.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("child.json").hasMessageContaining("Nobody");
        assertThatThrownBy(() -> loader(Map.of(
            "a.json", """
                {"name": "Loop A", "parentTheme": "Loop B"}""",
            "b.json", """
                {"name": "Loop B", "parentTheme": "Loop A"}""")).load("a.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("a.json -> b.json -> a.json");
        assertThatThrownBy(() -> loader(Map.of("child.json", """
            {"name": "Child", "colors": {"panel": "grey99"}}""")).load("child.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("child.json")
            .hasMessageContaining("\"panel\"").hasMessageContaining("\"grey99\"");
        assertThatThrownBy(() -> loader(Map.of("child.json", """
            {"name": "Child", "colors": {"a": "b", "b": "a"}}""")).load("child.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("cycle");
        assertThatThrownBy(() -> loader(Map.of("child.json", "{\"name\": ")).load("child.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("child.json");
    }

    @Test void anUnknownParentFallsBackToTheBaseForTheThemesDarknessWithAWarning() throws Exception {
        var files = Map.of(
            "darcula.json", """
                {"name": "Darcula", "dark": true, "ui": {"A.x": "#101010"}}""",
            "light.json", """
                {"name": "IntelliJ", "ui": {"A.x": "#f0f0f0"}}""",
            "child.json", """
                {"name": "Child", "dark": true, "parentTheme": "ExperimentalDark", "ui": {"B.y": "#202020"}}""",
            "bright.json", """
                {"name": "Bright", "parentTheme": "Islands Light"}""");
        var loader = new ThemeLoader(files::get, Map.of("Darcula", "darcula.json", "IntelliJ", "light.json"));
        var dark = loader.load("child.json");
        assertThat(ui(dark)).containsEntry("A.x", "#101010").containsEntry("B.y", "#202020");
        assertThat(dark.warnings()).singleElement().asString()
            .contains("child.json").contains("ExperimentalDark").contains("Darcula");
        var light = loader.load("bright.json");
        assertThat(ui(light)).containsEntry("A.x", "#f0f0f0");
        assertThat(light.warnings()).singleElement().asString().contains("Islands Light").contains("IntelliJ");
        assertThat(loader.load("darcula.json").warnings()).isEmpty();
    }

    @Test void aParentThemeThatIsNotAStringIsAnError() {
        for (String parent : List.of("{}", "[\"Parent\"]"))
            assertThatThrownBy(() -> loader(Map.of("child.json", "{\"name\": \"Child\", \"parentTheme\": " + parent + "}"))
                .load("child.json"))
                .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("child.json").hasMessageContaining("parentTheme");
    }

    @Test void aPerOsValueDropsOnlyItsIntellijClassBranches() throws Exception {
        var ui = ui(loader(Map.of("child.json", """
            {"name": "Child", "ui": {"Menu.border": {"os.mac": "com.intellij.ide.ui.laf.darcula.ui.DarculaMenuBorder",
              "os.default": "1,1,1,1"}}}""")).load("child.json"));
        assertThat(ui).containsEntry("Menu.border", Map.of("os.default", "1,1,1,1"));
    }
}
