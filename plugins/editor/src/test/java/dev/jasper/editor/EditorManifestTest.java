package dev.jasper.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The Editor's shipped manifest, as Jasper reads it from the plugin's jar. */
class EditorManifestTest {
    /**
     * The Editor's right-click Move Pane to Tab names the window's other tabs, as Jasper's own Tab menu does. A tab's
     * title is a terminal's or another plugin's, which a plugin reads only with {@code terminal.observe}
     * ({@code ContentTab.tabChoices()}); without it the menu would show "Tab 3".
     */
    @Test void theEditorDeclaresTerminalObserveSoItsMoveMenuNamesTheTabs() throws IOException {
        assertThat(capabilities()).contains("terminal.observe");
    }

    private static List<String> capabilities() throws IOException {
        String manifest;
        try (var in = EditorPlugin.class.getResourceAsStream("/plugin.toml")) {
            assertThat(in).as("the Editor's plugin.toml").isNotNull();
            manifest = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var line = Pattern.compile("(?m)^capabilities\\s*=\\s*\\[(.*)]\\s*$").matcher(manifest);
        assertThat(line.find()).as("a capabilities line").isTrue();
        return Arrays.stream(line.group(1).split(",")).map(String::strip).map(value -> value.replace("\"", "")).toList();
    }
}
