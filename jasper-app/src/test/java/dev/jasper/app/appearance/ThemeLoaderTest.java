package dev.jasper.app.appearance;

import com.formdev.flatlaf.json.Json;
import java.awt.Color;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

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
}
