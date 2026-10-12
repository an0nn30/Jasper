package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.settings.Setting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsSaveTest {
    @TempDir Path directory;

    private static SettingsEdit edit(Map<Setting<?>, Optional<?>> changes, Map<Setting<?>, Optional<?>> expected) {
        return new SettingsEdit(changes, expected);
    }

    private ConfigService.State save(ConfigService service, SettingsEdit edit) throws Exception {
        return service.save(edit).get(5, TimeUnit.SECONDS);
    }

    @Test void writesNewAndReplacedValuesKeepingComments() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "# mine\n[window]\ntab_height = 30 # px\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(
                Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44), CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f)),
                Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(30), CoreSettings.TERMINAL_FONT_SIZE, Optional.empty())));
            assertThat(Files.readString(file)).isEqualTo("# mine\n[window]\ntab_height = 44 # px\n\n[terminal.font]\nsize = 18\n");
            assertThat(state.snapshot().tabHeight()).isEqualTo(44);
            assertThat(state.snapshot().fontSize()).isEqualTo(18f);
        }
    }

    @Test void anEmptyValueRestoresTheDefaultByRemovingTheKey() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ntab_height = 44\nstatus_bar = false\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty()),
                Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44))));
            assertThat(Files.readString(file)).isEqualTo("[window]\nstatus_bar = false\n");
            assertThat(state.snapshot().tabHeight()).isEqualTo(30);
        }
    }

    @Test void aValueChangedOnDiskIsRefusedAndRepublished() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ntab_height = 44\n");
        try (var service = new ConfigService(file, true)) {
            assertThatThrownBy(() -> save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50)),
                    Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(30)))))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("window.tab_height changed on disk. Review the new value and try again.");
            assertThat(Files.readString(file)).isEqualTo("[window]\ntab_height = 44\n");
        }
    }

    @Test void invalidValuesAndRejectedFilesAreRefusedButUntouchedValueErrorsAreNot() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ncolumns = 1\n");
        try (var service = new ConfigService(file, true)) {
            assertThatThrownBy(() -> save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(99)),
                    Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty()))))
                .hasCauseInstanceOf(IllegalArgumentException.class).hasRootCauseMessage("window.tab_height: Use an integer from 20–72; using 72.");
            save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(40)), Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty())));
            assertThat(Files.readString(file)).isEqualTo("[window]\ncolumns = 1\ntab_height = 40\n");
        }
        Files.writeString(file, "[window\n");
        try (var service = new ConfigService(file, true)) {
            assertThatThrownBy(() -> save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(40)),
                    Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty()))))
                .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(Files.readString(file)).isEqualTo("[window\n");
        }
    }

    @Test void savingASettingHeldUnderAnOldKeyMovesItToTheCurrentKey() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[font]\nsize = 15 # old\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f)),
                Map.of(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(15f))));
            assertThat(Files.readString(file)).isEqualTo("[font]\n# old\n\n[terminal.font]\nsize = 18\n");
            assertThat(state.diagnostics()).isEmpty();
        }
    }

    @Test void savingAMovedSettingRemovesItsSupersededAliasAndKeepsOtherContent() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "# mine\nfont.family = 'Menlo' # old\nterminal.font.family = 'Monaco'\nwindow.tab_height = 40\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, SettingsEdit.of(CoreSettings.TERMINAL_FONT_FAMILY, "Iosevka", service.initialState().values()));
            String text = Files.readString(file);
            assertThat(text).contains("# mine", "# old", "window.tab_height = 40", "terminal.font.family = \"Iosevka\"");
            assertThat(text).doesNotContain("Menlo");
            assertThat(state.snapshot().font().family()).isEqualTo("Iosevka");
        }
    }

    @Test void aMissingFileStartsFromTheTemplate() throws Exception {
        Path file = directory.resolve("new/config.toml");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44)), Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty())));
            assertThat(Files.readString(file)).startsWith("# Jasper settings");
            assertThat(state.snapshot().tabHeight()).isEqualTo(44);
            assertThat(state.diagnostics()).isEmpty();
        }
    }

    @Test void environmentTablesAreWrittenEntryByEntry() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[terminal.env]\nA = \"1\"\nB = \"2\"\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.TERMINAL_ENV, Optional.of(Map.of("A", "9", "C", "3"))),
                Map.of(CoreSettings.TERMINAL_ENV, Optional.of(Map.of("A", "1", "B", "2")))));
            assertThat(state.snapshot().terminal().env()).containsOnly(Map.entry("A", "9"), Map.entry("C", "3"));
            assertThat(Files.readString(file)).containsOnlyOnce("[terminal.env]");
        }
    }

    @Test void anEditMustNameTheSameSettingsTwice() {
        assertThatIllegalArgumentException().isThrownBy(() -> edit(Map.of(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(40)), Map.of()));
    }

    @Test void resettingATableRemovesItsEntriesAndTheTableLeftEmpty() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[terminal.env]\nA = \"1\"\n\n[window]\ntab_height = 44\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.TERMINAL_ENV, Optional.empty()),
                Map.of(CoreSettings.TERMINAL_ENV, Optional.of(Map.of("A", "1")))));
            assertThat(Files.readString(file)).doesNotContain("[terminal.env]").contains("[window]\ntab_height = 44\n");
            assertThat(state.values().saved(CoreSettings.TERMINAL_ENV)).isEmpty();
            assertThat(state.snapshot().tabHeight()).isEqualTo(44);
        }
    }

    /** The template's table headers are uncommented, so a new file already holds an empty [terminal.env]. */
    @Test void aTableStartsFromTheTemplatesEmptyHeader() throws Exception {
        Path file = directory.resolve("new/config.toml");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.TERMINAL_ENV, Optional.of(Map.of("A", "1"))),
                Map.of(CoreSettings.TERMINAL_ENV, Optional.empty())));
            assertThat(state.snapshot().terminal().env()).containsExactly(Map.entry("A", "1"));
            assertThat(Files.readString(file)).containsOnlyOnce("[terminal.env]");
            assertThat(state.diagnostics()).isEmpty();
        }
    }

    @Test void notificationGroupsKeepOtherGroupsAndAnEmptiedTableLeavesNoHeader() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[notifications.groups]\n\"dev.jasper.remote.activities\" = \"off\"\n");
        try (var service = new ConfigService(file, true)) {
            var both = Map.of("dev.jasper.remote.activities", "off", "jasper.commands", "history");
            var state = save(service, edit(Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(both)),
                Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(Map.of("dev.jasper.remote.activities", "off")))));
            assertThat(state.values().get(CoreSettings.NOTIFICATIONS_GROUPS)).isEqualTo(both);
            assertThat(Files.readString(file)).containsOnlyOnce("[notifications.groups]");
            state = save(service, edit(Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.empty()),
                Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(both))));
            assertThat(state.values().saved(CoreSettings.NOTIFICATIONS_GROUPS)).isEmpty();
            assertThat(Files.readString(file)).doesNotContain("[notifications.groups]");
        }
    }

    @Test void aFirstGroupModeIsSavedIntoTheTemplate() throws Exception {
        Path file = directory.resolve("new/config.toml");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(Map.of("jasper.commands", "off"))),
                Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.empty())));
            assertThat(state.values().get(CoreSettings.NOTIFICATIONS_GROUPS)).isEqualTo(Map.of("jasper.commands", "off"));
            assertThat(Files.readString(file)).containsOnlyOnce("[notifications.groups]");
            assertThat(state.diagnostics()).isEmpty();
        }
    }
}
