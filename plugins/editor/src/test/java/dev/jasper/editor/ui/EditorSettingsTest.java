package dev.jasper.editor.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
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
                .isEqualTo(new EditorSettings("JetBrains Mono", 13, java.util.List.of(), false, 1.0, false, 4, false, 10, false));
            assertThat(host.declared(ID)).containsExactly("font_family", "font_size", "font_fallback", "font_ligatures", "font_line_height", "follow_terminal_font", "tab_size", "soft_wrap", "vim", "max_file_mib", "recent_files_days",
                "default_editor", "custom_command", "custom_in_terminal", "keep_environment_editor");
            assertThat(host.settingsReports(ID)).isEmpty();
        }
        try (var host = new FakePluginHost(home.resolve("other"))) {
            assertThat(start(host, Set.of()).current().fontFamily()).isEqualTo("Monospaced");
        }
    }

    /** Plan 15a: a look's font family (Enterprise's IBM Plex Mono) is the default only when it is installed. */
    @Test void aLooksFontFamilyIsTheDefaultOnlyWhenItIsInstalled() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setKitDefault(ID, "font_family", "IBM Plex Mono");
            assertThat(start(host, Set.of("IBM Plex Mono", "Menlo")).current().fontFamily()).isEqualTo("IBM Plex Mono");
            assertThat(host.lookDefaultProblems(ID)).isEmpty();
        }
        try (var host = new FakePluginHost(home.resolve("other"))) {
            host.setKitDefault(ID, "font_family", "IBM Plex Mono");
            assertThat(start(host, Set.of("Menlo")).current().fontFamily()).isEqualTo("Menlo");
            assertThat(host.lookDefaultProblems(ID)).singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .startsWith("dev.jasper.editor: the look's default for font_family is not used: ");
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
            assertThat(settings).isEqualTo(new EditorSettings("Menlo", 13, java.util.List.of(), false, 1.0, false, 4, true, 20, false));
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
     * Plan 3 Task 8 and the default editor plan: the declarations group the settings as Font, Editing, Files and Default
     * editor, as the Editor's own page shows them, with the on/off settings as switches.
     */
    @Test void declaresTheSettingsAsFontEditingFilesAndDefaultEditorGroups() {
        var specs = new java.util.ArrayList<dev.jasper.sdk.settings.SettingSpec<?>>();
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), context -> new EditorSettings.Declared(recording(context.settings(), specs), Set.of()));
            assertThat(specs).extracting(spec -> spec.section() + "/" + spec.key()).containsExactly(
                "Font/font_family", "Font/font_size", "Font/font_fallback", "Font/font_ligatures", "Font/font_line_height", "Font/follow_terminal_font", "Editing/tab_size", "Editing/soft_wrap", "Editing/vim", "Files/max_file_mib", "Files/recent_files_days",
                "Default editor/default_editor", "Default editor/custom_command", "Default editor/custom_in_terminal",
                "Default editor/keep_environment_editor");
            assertThat(specs).filteredOn(spec -> spec.kind() instanceof dev.jasper.sdk.settings.SettingKind.Bool)
                .extracting(dev.jasper.sdk.settings.SettingSpec::key).containsExactly("font_ligatures", "follow_terminal_font", "soft_wrap", "vim", "custom_in_terminal", "keep_environment_editor");
            assertThat(specs).allSatisfy(spec -> {
                assertThat(spec.label()).as("sentence-case labels without a colon").doesNotEndWith(":").isNotBlank();
                assertThat(spec.description()).isNotBlank();
            });
            assertThat(specs.getFirst().editor()).as("the font family is the settings font picker").isPresent();
        }
    }

    @Test void fractionalTypographyAndOldIntegerSizesAreReadWithoutRounding() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setSetting(ID, "font_size", 13L);
            var declared = start(host, Set.of());
            assertThat(declared.current().fontSize()).isEqualTo(13.0);
            host.setSetting(ID, "font_size", 13.5);
            host.setSetting(ID, "font_fallback", List.of("Dialog", "DialogInput"));
            host.setSetting(ID, "font_ligatures", true);
            host.setSetting(ID, "font_line_height", 1.25);
            host.setSetting(ID, "follow_terminal_font", true);
            var settings = declared.current();
            assertThat(settings.font().getSize2D()).isEqualTo(13.5f);
            assertThat(settings.fontFallback()).containsExactly("Dialog", "DialogInput");
            assertThat(settings.fontLigatures()).isTrue();
            assertThat(settings.fontLineHeight()).isEqualTo(1.25);
            assertThat(settings.followTerminalFont()).isTrue();
            assertThat(host.settingsReports(ID)).isEmpty();
        }
    }

    @Test void typographyBoundariesAndInvalidValuesUseTheDeclaredDefaults() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var declared = start(host, Set.of());
            assertThat(declared.current().fontFallback()).isEmpty();
            assertThat(declared.current().fontLigatures()).isFalse();
            assertThat(declared.current().fontLineHeight()).isEqualTo(1.0);
            assertThat(declared.current().followTerminalFont()).isFalse();
            for (double size : List.of(6.0, 72.0, 13.5)) {
                host.setSetting(ID, "font_size", size);
                assertThat(declared.current().fontSize()).isEqualTo(size);
            }
            for (double size : List.of(5.99, 72.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                host.setSetting(ID, "font_size", size);
                assertThat(declared.current().fontSize()).isEqualTo(13.0);
            }
            for (double height : List.of(.5, 3.0, 1.25)) {
                host.setSetting(ID, "font_line_height", height);
                assertThat(declared.current().fontLineHeight()).isEqualTo(height);
            }
            for (double height : List.of(.49, 3.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                host.setSetting(ID, "font_line_height", height);
                assertThat(declared.current().fontLineHeight()).isEqualTo(1.0);
            }
            for (List<String> fallback : List.of(List.of(""), List.of(" "), List.of("bad\0family"))) {
                host.setSetting(ID, "font_fallback", fallback);
                assertThat(declared.current().fontFallback()).isEmpty();
            }
            for (String key : List.of("font_ligatures", "follow_terminal_font")) host.setSetting(ID, key, "yes");
            assertThat(declared.current().fontLigatures()).isFalse();
            assertThat(declared.current().followTerminalFont()).isFalse();
            assertThat(host.settingsReports(ID)).isNotEmpty();
        }
    }

    @Test void kitTypographyDefaultsRemainIndependentWhenFollowing() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setKitDefault(ID, "font_family", "IBM Plex Mono");
            host.setKitDefault(ID, "font_size", 14.5);
            host.setKitDefault(ID, "font_fallback", List.of("Dialog"));
            host.setKitDefault(ID, "font_line_height", 1.2);
            host.setKitDefault(ID, "font_ligatures", true);
            host.setSetting(ID, "follow_terminal_font", true);
            var own = start(host, Set.of("IBM Plex Mono")).current();
            assertThat(own.fontFamily()).isEqualTo("IBM Plex Mono");
            assertThat(own.fontSize()).isEqualTo(14.5);
            assertThat(own.fontFallback()).containsExactly("Dialog");
            assertThat(own.fontLineHeight()).isEqualTo(1.2);
            assertThat(own.fontLigatures()).isTrue();
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

    @Test void theDefaultEditorIsTheJasperEditorAndOnlyKnownChoicesAreAccepted() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var declared = start(host, Set.of());
            assertThat(declared.defaultEditor()).isEqualTo("jasper");
            assertThat(declared.customCommand()).isEmpty();
            assertThat(declared.customInTerminal()).isTrue();
            assertThat(declared.keepEnvironmentEditor()).isTrue();
            for (String value : List.of("system", "custom", "nvim", "code", "notepadpp")) {
                host.setSetting(ID, "default_editor", value);
                assertThat(declared.defaultEditor()).isEqualTo(value);
            }
            host.setSetting(ID, "default_editor", "emacsclient");
            assertThat(declared.defaultEditor()).isEqualTo("jasper");
            assertThat(host.settingsReports(ID)).containsExactly("default_editor: Use jasper, system, custom or an editor id such as nvim or code.");
            host.setSetting(ID, "default_editor", "custom");
            host.setSetting(ID, "custom_command", "subl -w");
            host.setSetting(ID, "custom_in_terminal", false);
            host.setSetting(ID, "keep_environment_editor", false);
            assertThat(declared.customCommand()).isEqualTo("subl -w");
            assertThat(declared.customInTerminal()).isFalse();
            assertThat(declared.keepEnvironmentEditor()).isFalse();
        }
    }
}
