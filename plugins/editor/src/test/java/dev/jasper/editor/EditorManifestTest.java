package dev.jasper.editor;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import static org.assertj.core.api.Assertions.assertThat;

/** The Editor's shipped manifest, as Jasper reads it from the plugin's jar. */
class EditorManifestTest {
    /**
     * The Editor's right-click Move Pane to Tab names the window's other tabs, as Jasper's own Tab menu does. A tab's
     * title is a terminal's or another plugin's, which a plugin reads only with {@code terminal.observe}
     * ({@code ContentTab.tabChoices()}); without it the menu would show "Tab 3".
     */
    @Test void theEditorDeclaresTerminalObserveSoItsMoveMenuNamesTheTabs() throws IOException {
        assertThat(manifest().getArrayOrEmpty("capabilities").toList()).contains("terminal.observe");
    }

    /** The File associations section reaches {@code Platform.fileAssociations()}, which needs {@code files.associate}. */
    @Test void theEditorDeclaresFilesAssociateForItsFileAssociationsSection() throws IOException {
        assertThat(manifest().getArrayOrEmpty("capabilities").toList()).contains("files.associate");
    }

    /**
     * The Chromium Markdown preview draws with the Browser's exported API, which the Editor reaches only when it requires
     * the Browser; optionally, so the Editor runs, with the Basic preview, while the Browser is off or not installed.
     */
    @Test void theEditorRequiresTheBrowserOptionallyForItsMarkdownPreview() throws IOException {
        var requires = manifest().getArrayOrEmpty("requires");
        assertThat(requires.size()).isEqualTo(1);
        var browser = requires.getTable(0);
        assertThat(browser.getString("id")).isEqualTo("dev.jasper.browser");
        assertThat(browser.getString("version")).isEqualTo(">=0.2.0");
        assertThat(browser.getBoolean("optional")).isTrue();
    }

    /** The Editor's own {@code plugin.toml}: the tests also see the Browser's, whose API they compile against. */
    private static TomlParseResult manifest() throws IOException {
        for (URL url : Collections.list(EditorPlugin.class.getClassLoader().getResources("plugin.toml"))) {
            String text;
            try (var in = url.openStream()) { text = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            var parsed = Toml.parse(text);
            assertThat(parsed.errors()).as(url.toString()).isEmpty();
            if ("dev.jasper.editor".equals(parsed.getString("id"))) return parsed;
        }
        throw new AssertionError("The Editor's plugin.toml is not on the class path");
    }
}
