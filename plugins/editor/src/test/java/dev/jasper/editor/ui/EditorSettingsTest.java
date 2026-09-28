package dev.jasper.editor.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EditorSettingsTest {
    @TempDir Path home;
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of());

    @Test void defaultsPickTheFirstInstalledPreferredFont() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            assertThat(EditorSettings.read(context.config(), Set.of("Menlo", "JetBrains Mono")))
                .isEqualTo(new EditorSettings("JetBrains Mono", 13, 4, false, 10, false));
            assertThat(EditorSettings.read(context.config(), Set.of()).fontFamily()).isEqualTo("Monospaced");
            assertThat(host.reports()).isEmpty();
        }
    }

    @Test void badValuesAreReportedAndTheirDefaultsUsed() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setConfig("dev.jasper.editor", Map.of("font_family", "Nope Mono", "font_size", 99L, "tab_size", "four",
                "soft_wrap", true, "max_file_mib", 20L, "vim", "yes"));
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            var settings = EditorSettings.read(context.config(), Set.of("Menlo", "Consolas"));
            assertThat(settings).isEqualTo(new EditorSettings("Menlo", 13, 4, true, 20, false));
            assertThat(settings.maxFileBytes()).isEqualTo(20L * 1024 * 1024);
            assertThat(settings.font()).isEqualTo(new Font("Menlo", Font.PLAIN, 13));
            assertThat(host.reports()).containsExactly(
                "dev.jasper.editor: font_family: No installed font family is named \"Nope Mono\"; using Menlo.",
                "dev.jasper.editor: font_size: Use 8 to 48; using 13.",
                "dev.jasper.editor: tab_size: Use a whole number from 1 to 16; using 4.",
                "dev.jasper.editor: vim: Use true or false; using false.");
        }
    }
}
