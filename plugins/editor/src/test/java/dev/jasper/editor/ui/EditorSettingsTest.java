package dev.jasper.editor.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EditorSettingsTest {
    @TempDir Path home;
    private static final String ID = "dev.jasper.editor";
    private static final PluginInfo INFO = new PluginInfo(ID, "Editor", "0.1.0", Set.of());

    private static EditorSettings.Declared start(FakePluginHost host, Set<String> installed) {
        var declared = new AtomicReference<EditorSettings.Declared>();
        host.start(INFO, Set.of(), Set.of(), context -> declared.set(new EditorSettings.Declared(context.settings(), installed)));
        return declared.get();
    }

    @Test void defaultsPickTheFirstInstalledPreferredFont() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            assertThat(start(host, Set.of("Menlo", "JetBrains Mono")).current())
                .isEqualTo(new EditorSettings("JetBrains Mono", 13, 4, false, 10, false));
            assertThat(host.declared(ID)).containsExactly("font_family", "font_size", "tab_size", "soft_wrap", "vim", "max_file_mib");
            assertThat(host.settingsReports(ID)).isEmpty();
        }
        try (var host = new FakePluginHost(home.resolve("other"))) {
            assertThat(start(host, Set.of()).current().fontFamily()).isEqualTo("Monospaced");
        }
    }

    @Test void badValuesAreReportedAndTheirDefaultsUsed() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setSetting(ID, "font_family", "Nope Mono");
            host.setSetting(ID, "font_size", 99L);
            host.setSetting(ID, "tab_size", "four");
            host.setSetting(ID, "soft_wrap", true);
            host.setSetting(ID, "max_file_mib", 20L);
            host.setSetting(ID, "vim", "yes");
            var settings = start(host, Set.of("Menlo", "Consolas")).current();
            assertThat(settings).isEqualTo(new EditorSettings("Menlo", 13, 4, true, 20, false));
            assertThat(settings.maxFileBytes()).isEqualTo(20L * 1024 * 1024);
            assertThat(settings.font()).isEqualTo(new Font("Menlo", Font.PLAIN, 13));
            assertThat(host.settingsReports(ID)).extracting(line -> line.substring(0, line.indexOf(':')))
                .containsExactly("font_family", "font_size", "tab_size", "vim");
        }
    }

    @Test void savingVimModeWritesTheFileAndTheValueFollows() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var declared = start(host, Set.of());
            declared.saveVim(true).join();
            assertThat(host.savedSettings(ID)).containsEntry("vim", true);
            assertThat(declared.current().vim()).isTrue();
        }
    }
}
