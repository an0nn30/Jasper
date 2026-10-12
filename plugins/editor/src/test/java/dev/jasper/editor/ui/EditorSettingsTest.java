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
            assertThat(host.declared(ID)).containsExactly("font_family", "font_size", "tab_size", "soft_wrap", "vim", "max_file_mib", "recent_files_days");
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

    @Test void recentFilesAreKeptThirtyDaysByDefaultAndZeroTo365Days() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var declared = start(host, Set.of());
            assertThat(declared.recentFilesDays()).isEqualTo(30);
            host.setSetting(ID, "recent_files_days", 0L);
            assertThat(declared.recentFilesDays()).isZero();
            host.setSetting(ID, "recent_files_days", 365L);
            assertThat(declared.recentFilesDays()).isEqualTo(365);
            host.setSetting(ID, "recent_files_days", 400L);
            assertThat(declared.recentFilesDays()).isEqualTo(30);
            assertThat(host.settingsReports(ID)).isNotEmpty();
        }
    }

    /**
     * Plan 3 Task 8: the generated Settings page shows a section label above a settings card wherever the section
     * changes, so the declarations group the page as Font, Editing and Files, with the two on/off settings as switches.
     */
    @Test void declaresTheSettingsPageAsFontEditingAndFilesGroups() {
        var specs = new java.util.ArrayList<dev.jasper.sdk.settings.SettingSpec<?>>();
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), context -> new EditorSettings.Declared(recording(context.settings(), specs), Set.of()));
            assertThat(specs).extracting(spec -> spec.section() + "/" + spec.key()).containsExactly(
                "Font/font_family", "Font/font_size", "Editing/tab_size", "Editing/soft_wrap", "Editing/vim", "Files/max_file_mib", "Files/recent_files_days");
            assertThat(specs).filteredOn(spec -> spec.kind() instanceof dev.jasper.sdk.settings.SettingKind.Bool)
                .extracting(dev.jasper.sdk.settings.SettingSpec::key).containsExactly("soft_wrap", "vim");
            assertThat(specs).allSatisfy(spec -> {
                assertThat(spec.label()).as("sentence-case labels without a colon").doesNotEndWith(":").isNotBlank();
                assertThat(spec.description()).isNotBlank();
            });
            assertThat(specs.getFirst().editor()).as("the font family is the settings font picker").isPresent();
        }
    }

    private static dev.jasper.sdk.settings.PluginSettings recording(dev.jasper.sdk.settings.PluginSettings delegate, java.util.List<dev.jasper.sdk.settings.SettingSpec<?>> specs) {
        return (dev.jasper.sdk.settings.PluginSettings) java.lang.reflect.Proxy.newProxyInstance(EditorSettingsTest.class.getClassLoader(),
            new Class<?>[]{dev.jasper.sdk.settings.PluginSettings.class}, (proxy, method, args) -> {
                if (method.getName().equals("declare") && args != null && args.length == 1 && args[0] instanceof dev.jasper.sdk.settings.SettingSpec<?> spec) specs.add(spec);
                try { return method.invoke(delegate, args); } catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
            });
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
