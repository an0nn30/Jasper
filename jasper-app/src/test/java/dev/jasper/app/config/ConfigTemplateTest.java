package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.config.ToolbarMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.tomlj.Toml;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.settings.TomlLiterals;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ConfigTemplateTest {
    @TempDir Path directory;

    @Test void paletteAndClearCommentsShowTheirLiteralPlatformDefaults() {
        assertThat(ConfigTemplate.text(true)).contains("# command_palette = \"cmd+k\"", "# clear_scrollback = \"cmd+shift+k\"");
        assertThat(ConfigTemplate.text(true)).doesNotContain("history_palette", "snippets_palette");
        assertThat(ConfigTemplate.text(false)).contains("# command_palette = \"ctrl+k\"", "# clear_scrollback = \"ctrl+shift+k\"",
            "Command Palette uses plain Ctrl+K");
    }

    @Test void commentedTemplatesParseCleanlyWithBuiltInDefaultsOnBothPlatforms() {
        for (boolean macOs : new boolean[]{true, false}) {
            var result = ConfigLoader.parse(directory.resolve("config.toml"), ConfigTemplate.text(macOs), macOs);
            assertThat(result.rejected()).isFalse();
            assertThat(result.diagnostics()).isEmpty();

            assertThat(result.snapshot()).isEqualTo(ConfigSnapshot.defaults());
        }
    }

    @Test void repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms() throws Exception {
        Path example = Path.of(System.getProperty("jasper.projectDir")).resolve("config.example.toml");
        assertThat(example).isRegularFile();
        String text = Files.readString(example, StandardCharsets.UTF_8);
        var toml = Toml.parse(text);
        assertThat(toml.errors()).isEmpty();
        assertThat(toml.getLong("version")).isEqualTo(2L);
        var expected = new ArrayList<List<String>>();
        expected.add(List.of("version"));
        for (Setting<?> setting : CoreSettings.SCHEMA.settings())
            if (!(setting.kind() instanceof SettingKind.StringMap) && setting != CoreSettings.UI_FONT_SIZE) expected.add(setting.key());
        assertThat(TomlDocument.leaves(text).keySet()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(toml.getTable("terminal.env").keySet()).isEmpty();
        assertThat(toml.getTable("keybindings").keySet()).isEmpty();

        for (boolean macOs : new boolean[]{true, false}) {
            var result = ConfigLoader.parse(example, text, macOs);
            assertThat(result.rejected()).isFalse();
            assertThat(result.diagnostics()).isEmpty();
            assertThat(result.snapshot()).isEqualTo(ConfigSnapshot.defaults());
        }
    }

    @Test void uncommentedDefaultsAreValidAndPreserveEveryEffectiveShortcut() {
        for (boolean macOs : new boolean[]{true, false}) {
            String uncommented = ConfigTemplate.text(macOs).lines()
                .map(line -> line.matches("# ([a-z_0-9]+ = .*|\\[.*])") ? line.substring(2) : line)
                .collect(java.util.stream.Collectors.joining("\n"));
            var all = ConfigLoader.parse(directory.resolve("config.toml"), uncommented, macOs);
            assertThat(all.rejected()).isFalse();
            assertThat(all.diagnostics()).isEmpty();
            assertThat(all.snapshot().tabHeight()).isEqualTo(30);
            assertThat(all.snapshot().toolbar()).isEqualTo(ToolbarMode.ICONS_AND_LABELS);
            assertThat(all.snapshot().statusBar()).isTrue();
            assertThat(all.snapshot().buddyEnabled()).isFalse();
            assertThat(all.snapshot().maxResults()).isEqualTo(5);
            assertThat(all.snapshot().fontSize()).isEqualTo(16f);
            assertThat(all.snapshot().columns()).isEqualTo(100);
            assertThat(all.snapshot().lines()).isEqualTo(35);
            assertThat(all.snapshot().railWidth()).isEqualTo(28);
            assertThat(all.snapshot().toolbarHeight()).isEqualTo(30);
            assertThat(all.snapshot().statusBarHeight()).isEqualTo(24);
            assertThat(all.snapshot().font()).isEqualTo(FontConfig.defaults());
            assertThat(all.snapshot().terminal()).isEqualTo(TerminalConfig.defaults());
            assertThat(all.snapshot().theme()).isEqualTo("Jasper Dark");
            assertThat(all.snapshot().keybindings()).hasSize(ActionId.values().length);
            var defaults = KeyBindings.defaults(macOs);
            for (ActionId action : ActionId.values()) {
                String binding = all.snapshot().keybindings().get(action.id());
                var individual = ConfigLoader.parse(directory.resolve("config.toml"),
                    "[keybindings]\n" + action.id() + " = '" + binding + "'", macOs);
                assertThat(individual.diagnostics()).as(action.id() + " on macOS=" + macOs).isEmpty();
                assertThat(individual.snapshot().bindings(macOs).strokeFor(action)).isEqualTo(defaults.strokeFor(action));
                assertThat(all.snapshot().bindings(macOs).strokeFor(action)).isEqualTo(defaults.strokeFor(action));
            }
        }
    }

    @Test void theTemplateIsVersionTwoAndListsEverySettingUnderItsTableOnce() {
        for (boolean macOs : new boolean[]{true, false}) {
            String template = ConfigTemplate.text(macOs);
            assertThat(template).contains("\nversion = 2\n").doesNotContain("[font]", "[background]");
            String uncommented = template.lines()
                .map(line -> line.matches("# ([a-z_0-9]+ = .*|\\[.*])") ? line.substring(2) : line)
                .collect(java.util.stream.Collectors.joining("\n"));
            var parsed = ConfigLoader.parse(directory.resolve("config.toml"), uncommented, macOs);
            assertThat(parsed.diagnostics()).isEmpty();
            for (Setting<?> setting : CoreSettings.SCHEMA.settings()) {
                if (setting.kind() instanceof SettingKind.StringMap) continue;
                assertThat(parsed.values().present(setting)).as(setting.name()).isTrue();
                assertThat(parsed.values().get(setting)).as(setting.name()).isEqualTo(templateValue(setting));
            }
            var headers = template.lines().filter(line -> line.startsWith("[")).toList();
            assertThat(headers).doesNotHaveDuplicates();
        }
    }

    private static <T> T templateValue(Setting<T> setting) { return setting.example().orElse(setting.defaultValue()); }

    @Test void theConfigurationGuideTableNamesEveryCurrentKeyAndNoOldOne() throws Exception {
        Path guide = Path.of(System.getProperty("jasper.projectDir")).resolve("docs/configuration.md");
        String text = Files.readString(guide, StandardCharsets.UTF_8);
        for (Setting<?> setting : CoreSettings.SCHEMA.settings()) {
            assertThat(text).as(setting.name()).contains("| `" + setting.name() + "` |");
            for (List<String> alias : setting.aliases()) assertThat(text).doesNotContain("| `" + TomlLiterals.path(alias) + "` |");
        }
    }

    @Test void explicitCreationMakesOnlyConfigParentsAndNeverOverwritesEdits() throws Exception {
        Path file = directory.resolve("nested/jasper/config.toml");
        ConfigTemplate.ensureExists(file, true);
        assertThat(Files.readString(file)).isEqualTo(ConfigTemplate.text(true));
        byte[] edited = "# user bytes\r\nwindow.tab_height=44\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file, edited);
        ConfigTemplate.ensureExists(file, false);
        ConfigTemplate.ensureExists(file, true);
        assertThat(Files.readAllBytes(file)).isEqualTo(edited);
        try (var files = Files.list(file.getParent())) {
            assertThat(files.toList()).containsExactly(file);
        }
    }

    @Test void concurrentCreationPreservesOneCompleteTemplateAndExistingEdits() throws Exception {
        Path file = directory.resolve("nested/config.toml");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var first = executor.submit(() -> { ready.countDown(); go.await(); ConfigTemplate.ensureExists(file, true); return null; });
            var second = executor.submit(() -> { ready.countDown(); go.await(); ConfigTemplate.ensureExists(file, false); return null; });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(Files.readString(file)).isIn(ConfigTemplate.text(true), ConfigTemplate.text(false));
            Files.writeString(file, "# retained user edit");
            var third = executor.submit(() -> { ConfigTemplate.ensureExists(file, true); return null; });
            var fourth = executor.submit(() -> { ConfigTemplate.ensureExists(file, false); return null; });
            third.get(5, TimeUnit.SECONDS);
            fourth.get(5, TimeUnit.SECONDS);
            assertThat(Files.readString(file)).isEqualTo("# retained user edit");
        }
    }

    @Test void unusableParentIsReportedWithoutChangingItsBytes() throws Exception {
        Path parent = directory.resolve("not-a-directory");
        Files.writeString(parent, "user data");
        assertThatExceptionOfType(IOException.class).isThrownBy(() -> ConfigTemplate.ensureExists(parent.resolve("config.toml"), true));
        assertThat(Files.readString(parent)).isEqualTo("user data");
    }
}
