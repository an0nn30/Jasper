package dev.jasper.app.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class KeybindingSaveTest {
    @TempDir Path directory;

    @Test void savesAndPublishesWhilePreservingCommentsAndLatestUnrelatedSettings() throws Exception {
        Path file = directory.resolve("config.toml");
        String original = "# mine\r\n[font]\r\nsize = 18 # keep\r\n[keybindings] # keys\r\ncopy = 'ctrl+c' # copy note\r\n";
        Files.writeString(file, original);
        try (var service = new ConfigService(file, true)) {
            Files.writeString(file, original.replace("18", "20"));
            var result = service.saveKeybindings(Map.of("copy", "ctrl+c"),
                Map.of("copy", Optional.of("ctrl+shift+c"), "app.shortcuts", Optional.of("ctrl+k")), List.of()).get(5, TimeUnit.SECONDS);
            assertThat(result.snapshot().keybindings()).containsEntry("copy", "ctrl+shift+c").containsEntry("app.shortcuts", "ctrl+k");
            assertThat(Files.readString(file)).contains("# mine\r\n[font]\r\nsize = 20 # keep\r\n", "# keys", "# copy note");
            try (var reopened = new ConfigService(file, true)) {
                assertThat(reopened.initialState().snapshot().bindings(true).strokeFor("copy"))
                    .contains(KeyStroke.getKeyStroke("ctrl shift C"));
            }
        }
    }

    @Test void createsTemplateAndRestoresDefaultByRemovingOverride() throws Exception {
        Path file = directory.resolve("new/config.toml");
        try (var service = new ConfigService(file, true)) {
            service.saveKeybindings(Map.of(), Map.of("copy", Optional.of("none")), List.of()).get(5, TimeUnit.SECONDS);
            assertThat(Files.readString(file)).contains("# Jasper settings");
            var reset = service.saveKeybindings(Map.of("copy", "none"), Map.of("copy", Optional.empty()), List.of()).get(5, TimeUnit.SECONDS);
            assertThat(reset.snapshot().keybindings()).doesNotContainKey("copy");
            assertThat(reset.snapshot().bindings(true).strokeFor("copy")).contains(KeyStroke.getKeyStroke("meta C"));
        }
    }

    @Test void rejectsStaleEditsInvalidFilesAndConflictsWithoutWriting() throws Exception {
        Path file = directory.resolve("config.toml");
        for (String text : List.of("[keybindings]\ncopy='ctrl+c'\n", "[broken", "[keybindings]\ncopy='nonsense'\n")) {
            Files.writeString(file, text);
            try (var service = new ConfigService(file, true)) {
                assertThatThrownBy(() -> service.saveKeybindings(Map.of(), Map.of("copy", Optional.of("ctrl+k")), List.of()).get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(Exception.class);
                assertThat(Files.readString(file)).isEqualTo(text);
            }
        }
        Files.writeString(file, "# unchanged\n");
        try (var service = new ConfigService(file, true)) {
            assertThatThrownBy(() -> service.saveKeybindings(Map.of(), Map.of("copy", Optional.of("cmd+v")), List.of()).get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(Files.readString(file)).isEqualTo("# unchanged\n");
            var saved = service.saveKeybindings(Map.of(), Map.of("copy", Optional.of("cmd+v"), "paste", Optional.of("none")), List.of()).get(5, TimeUnit.SECONDS);
            assertThat(saved.snapshot().bindings(true).strokeFor("paste")).isEmpty();
        }
    }

    @Test void pluginDefaultCannotBeSilentlyDisplaced() throws Exception {
        Path file = directory.resolve("config.toml");
        var extensions = List.of(new KeyBindings.Extension("test.plugin", Optional.of("ctrl+k")));
        try (var service = new ConfigService(file, true)) {
            assertThatThrownBy(() -> service.saveKeybindings(Map.of(), Map.of("copy", Optional.of("ctrl+k")), extensions).get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(file).doesNotExist();
            var saved = service.saveKeybindings(Map.of(), Map.of("copy", Optional.of("ctrl+k"), "test.plugin", Optional.of("none")), extensions).get(5, TimeUnit.SECONDS);
            assertThat(saved.snapshot().keybindings()).containsEntry("test.plugin", "none");
        }
    }

    @Test void patchesQuotedDottedInlineAndMultilineBindingsWithoutChangingOtherValues() {
        for (String text : List.of(
                "keybindings.copy = 'ctrl+c' # keep\n[font]\nsize=20\n",
                "keybindings = { copy = 'ctrl+c' } # keep\n[font]\nsize=20\n",
                "['keybindings'] # keep\ncopy = '''ctrl+c'''\n[font]\nsize=20\n",
                "[keybindings]\ncopy = \"\"\"\nctrl+c\"\"\" # keep\n[font]\nsize=20\n")) {
            String edited = KeybindingText.update(text, Map.of("copy", Optional.empty(), "test.plugin", Optional.of("ctrl+k")));
            var parsed = ConfigLoader.parse(Path.of("config.toml"), edited, true);
            assertThat(parsed.rejected()).as(edited).isFalse();
            assertThat(parsed.snapshot().keybindings()).containsExactlyEntriesOf(Map.of("test.plugin", "ctrl+k"));
            assertThat(edited).contains("# keep", "[font]\nsize=20\n");
        }
    }

    @Test void savingThroughASymlinkKeepsTheLinkAndUpdatesItsTarget() throws Exception {
        Path target = directory.resolve("real.toml"), link = directory.resolve("config.toml");
        Files.writeString(target, "# real settings\n");
        try { Files.createSymbolicLink(link, target); }
        catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + unavailable.getMessage());
        }
        try (var service = new ConfigService(link, true)) {
            service.saveKeybindings(Map.of(), Map.of("copy", Optional.of("ctrl+k")), List.of()).get(5, TimeUnit.SECONDS);
            assertThat(Files.isSymbolicLink(link)).isTrue();
            assertThat(Files.readString(target)).contains("ctrl+k");
        }
    }

    @Test void restoringAPluginDefaultCannotReportSuccessIfAnotherDefaultTakesIt() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[keybindings]\n\"test.second\"='ctrl+k'\n");
        var extensions = List.of(new KeyBindings.Extension("test.first", Optional.of("ctrl+k")),
            new KeyBindings.Extension("test.second", Optional.of("ctrl+k")));
        try (var service = new ConfigService(file, true)) {
            assertThatThrownBy(() -> service.saveKeybindings(Map.of("test.second", "ctrl+k"),
                Map.of("test.second", Optional.empty()), extensions).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(Files.readString(file)).contains("'ctrl+k'");
        }
    }

    @Test void physicalShortcutSerializationRoundTripsAcrossPlatforms() {
        for (boolean mac : new boolean[]{true, false}) for (String key : List.of("meta shift H", "ctrl alt PAGE_UP", "altGraph A", "F24", "ctrl BACK_SLASH", "shift SEMICOLON")) {
            var stroke = KeyStroke.getKeyStroke(key);
            assertThat(KeyBindings.parse(KeyBindings.format(stroke, mac), mac)).as(key).contains(stroke);
        }
    }
}
