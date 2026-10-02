package dev.jasper.app.config;

import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ThemeSelectionMigrationTest {
    @TempDir Path home;
    @Test void rewritesOnlySelectedNamesAndPreservesUnrelatedTextAndBackups() throws Exception {
        Path config = home.resolve("config.toml");
        String original = "# keep\n[ui.theme]\nname = \"Example\" # chosen\nworkspace = \"match\"\n[plugins.custom]\nx = 12 # untouched\n";
        Files.writeString(config, original); Files.writeString(home.resolve("config.toml.v1.bak"), "previous");
        var edit = ThemeSelectionMigration.plan(config, false, Map.of("example", "Example 2"), Map.of()).orElseThrow();
        ThemeSelectionMigration.apply(config, false, edit);
        assertThat(Files.readString(config)).contains("# keep", "name = \"Example 2\" # chosen", "workspace = \"match\"", "[plugins.custom]\nx = 12 # untouched\n");
        assertThat(Files.readString(home.resolve("config.toml.v1.bak"))).isEqualTo("previous");
        assertThat(Files.readString(home.resolve("config.toml.v1.2.bak"))).isEqualTo(original);
        assertThat(ThemeSelectionMigration.plan(config, false, Map.of("example", "Example 2"), Map.of())).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"light", "dark", "Light", "match", "Unknown"})
    void canonicalNamesStayLiteralWhileAliasesUseExistingRules(String name) throws Exception {
        Path config = home.resolve("config.toml");
        String canonical = "ui.theme.name='" + name + "'\nui.theme.workspace='" + name + "'\n";
        Files.writeString(config, canonical + "ui.theme.variant='dark'\nui.theme.terminal='light'\nterminal.colors='dark'\n");
        var edit = ThemeSelectionMigration.plan(config, false, Map.of(), Map.of()).orElseThrow();
        ThemeSelectionMigration.apply(config, false, edit);
        assertThat(edit.after()).containsEntry("ui.theme.name", name).containsEntry("ui.theme.workspace", name).hasSize(2);
        Files.writeString(config, "ui.theme.terminal='" + name + "'\n");
        edit = ThemeSelectionMigration.plan(config, false, Map.of(), Map.of()).orElseThrow();
        String expected = name.equalsIgnoreCase("light") ? "Jasper Light" : name.equals("dark") ? "Jasper Dark" : name;
        assertThat(edit.after()).containsEntry("ui.theme.workspace", expected);
    }
    @Test void staleRejectedMissingAndFailedBackupLeaveConfigUntouched() throws Exception {
        Path config = home.resolve("config.toml");
        assertThat(ThemeSelectionMigration.plan(config, false, Map.of(), Map.of())).isEmpty(); assertThat(config).doesNotExist();
        Files.writeString(config, "ui.theme.name='Example'\n");
        var edit = ThemeSelectionMigration.plan(config, false, Map.of("example", "Example 2"), Map.of()).orElseThrow();
        Files.writeString(config, "# changed\nui.theme.name='Other'\n");
        assertThatThrownBy(() -> ThemeSelectionMigration.apply(config, false, edit)).isInstanceOf(java.io.IOException.class);
        assertThat(Files.readString(config)).contains("Other");
        Files.writeString(config, "invalid = [");
        assertThatThrownBy(() -> ThemeSelectionMigration.plan(config, false, Map.of(), Map.of())).isInstanceOf(java.io.IOException.class);
        Files.writeString(config, "ui.theme.name='Example'\n");
        // A failed destination write cannot destroy the original or replace an existing backup.
        Path wrong = home.resolve("other.toml"); Files.writeString(wrong, "ui.theme.name='Example'\n");
        assertThatThrownBy(() -> ThemeSelectionMigration.apply(wrong, false, edit)).isInstanceOf(java.io.IOException.class);
    }
    @Test void backupFailureLeavesOriginalUnchanged() throws Exception {
        // The source is a valid filename, but appending a backup suffix exceeds the filesystem component limit.
        Path config = home.resolve("c".repeat(250)); String original = "ui.theme.name='Example'\n";
        Files.writeString(config, original);
        var edit = ThemeSelectionMigration.plan(config, false, Map.of("example", "Example 2"), Map.of()).orElseThrow();
        assertThatThrownBy(() -> ThemeSelectionMigration.apply(config, false, edit)).isInstanceOf(java.io.IOException.class);
        assertThat(Files.readString(config)).isEqualTo(original);
    }
}
