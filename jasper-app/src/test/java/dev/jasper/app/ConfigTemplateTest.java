package dev.jasper.app;

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

import static org.assertj.core.api.Assertions.*;

class ConfigTemplateTest {
    @TempDir Path directory;

    @Test void paletteAndClearCommentsShowTheirLiteralPlatformDefaults() {
        assertThat(ConfigTemplate.text(true)).contains("# command_palette = \"cmd+k\"", "# clear_scrollback = \"cmd+shift+k\"");
        assertThat(ConfigTemplate.text(true)).contains("# history_palette = \"cmd+r\"");
        assertThat(ConfigTemplate.text(false)).contains("# command_palette = \"ctrl+k\"", "# clear_scrollback = \"ctrl+shift+k\"",
            "Command Palette uses plain Ctrl+K");
        assertThat(ConfigTemplate.text(false)).contains("# history_palette = \"ctrl+shift+r\"", "Search Shell History uses Ctrl+Shift+R");
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
        assertThat(toml.getTable("buddy").keySet()).containsExactly("enabled");
        assertThat(toml.getTable("history").keySet()).containsExactly("enabled");
        assertThat(toml.getTable("window").keySet())
            .containsExactlyInAnyOrder("tab_height", "toolbar", "status_bar", "columns", "lines");
        assertThat(toml.getTable("font").keySet())
            .containsExactlyInAnyOrder("family", "size", "fallback", "ligatures", "line_height");
        assertThat(toml.getTable("terminal").keySet())
            .containsExactlyInAnyOrder("scrollback", "option_as_meta", "dim_inactive_panes", "copy_on_select",
                "bell", "on_exit", "shell", "cursor", "env");
        assertThat(toml.getTable("terminal.shell").keySet()).containsExactlyInAnyOrder("program", "args");
        assertThat(toml.getTable("terminal.cursor").keySet()).containsExactlyInAnyOrder("shape", "blink");
        assertThat(toml.getTable("terminal.env").keySet()).isEmpty();
        assertThat(toml.getTable("ui.theme").keySet()).containsExactly("variant");
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
            assertThat(all.snapshot().tabHeight()).isEqualTo(38);
            assertThat(all.snapshot().toolbar()).isEqualTo(WindowContent.ToolbarMode.ICONS_AND_LABELS);
            assertThat(all.snapshot().statusBar()).isTrue();
            assertThat(all.snapshot().buddyEnabled()).isTrue();
            assertThat(all.snapshot().historyEnabled()).isTrue();
            assertThat(all.snapshot().fontSize()).isEqualTo(16f);
            assertThat(all.snapshot().columns()).isEqualTo(150);
            assertThat(all.snapshot().lines()).isEqualTo(45);
            assertThat(all.snapshot().font()).isEqualTo(FontConfig.defaults());
            assertThat(all.snapshot().terminal()).isEqualTo(TerminalConfig.defaults());
            assertThat(all.snapshot().variant()).isEqualTo(Appearance.DARK);
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
