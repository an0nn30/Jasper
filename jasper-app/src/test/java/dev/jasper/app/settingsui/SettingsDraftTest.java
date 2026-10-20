package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.settings.SettingValues;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsDraftTest {
    private static SettingValues values(String text) { return ConfigLoader.parse(Path.of("config.toml"), text, true).values(); }

    @Test void stagingTracksPendingValuesAndDropsEditsThatMatchTheBaseline() {
        var draft = new SettingsDraft(values("[window]\ntab_height = 44\n"));
        var changes = new int[1];
        draft.onChanged(() -> changes[0]++);
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
        assertThat(draft.modified()).isFalse();
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(50);
        assertThat(draft.modified(CoreSettings.WINDOW_TAB_HEIGHT)).isTrue();
        assertThat(draft.size()).isEqualTo(1);
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44));
        assertThat(draft.modified()).as("back to the saved value").isFalse();
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty());
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).as("reset shows the default").isEqualTo(30);
        assertThat(draft.modified(CoreSettings.WINDOW_TAB_HEIGHT)).isTrue();
        draft.stage(CoreSettings.TERMINAL_FONT_SIZE, Optional.empty());
        assertThat(draft.modified(CoreSettings.TERMINAL_FONT_SIZE)).as("already unset").isFalse();
        assertThat(changes[0]).isEqualTo(4);
    }

    @Test void theEditCarriesPendingChangesAndTheBaselineTheyReplace() {
        var draft = new SettingsDraft(values("[window]\ntab_height = 44\n"));
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty());
        draft.stage(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f));
        var edit = draft.edit();
        assertThat(edit.changes()).containsOnly(Map.entry(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty()),
            Map.entry(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f)));
        assertThat(edit.expected()).containsOnly(Map.entry(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44)),
            Map.entry(CoreSettings.TERMINAL_FONT_SIZE, Optional.empty()));
    }

    @Test void rebasingKeepsOnlyEditsTheNewBaselineDoesNotAlreadyHold() {
        var draft = new SettingsDraft(values(""));
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
        draft.stage(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f));
        draft.rebase(values("[window]\ntab_height = 50\n"));
        assertThat(draft.modified(CoreSettings.WINDOW_TAB_HEIGHT)).isFalse();
        assertThat(draft.modified(CoreSettings.TERMINAL_FONT_SIZE)).isTrue();
        assertThat(draft.baseline().get(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(50);
        draft.reset(List.of(CoreSettings.TERMINAL_FONT_SIZE));
        assertThat(draft.modified()).isFalse();
    }

    @Test void searchNeedsEveryWordInAnyOrderAndCase() {
        assertThat(SearchText.words("  Font   SIZE ")).containsExactly("font", "size");
        assertThat(SearchText.matches("Terminal font size", "size font")).isTrue();
        assertThat(SearchText.matches("Terminal font size", "font colour")).isFalse();
        assertThat(SearchText.matches("anything", "   ")).isFalse();
    }

    /** The template's [notifications.groups] header is uncommented: an empty table holds nothing, so it is no change. */
    @Test void anEmptyTableIsTheSameAsNoTable() {
        var draft = new SettingsDraft(values("[notifications.groups]\n"));
        draft.stage(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(Map.of("jasper.commands", "history")));
        assertThat(draft.modified()).isTrue();
        draft.stage(CoreSettings.NOTIFICATIONS_GROUPS, Optional.empty());
        assertThat(draft.modified()).as("back to no entries").isFalse();

        var reset = new SettingsDraft(values("[notifications.groups]\n\"jasper.commands\" = \"off\"\n"));
        reset.stage(CoreSettings.NOTIFICATIONS_GROUPS, Optional.empty());
        assertThat(reset.modified()).isTrue();
        reset.rebase(values("[notifications.groups]\n"));
        assertThat(reset.modified()).as("the file now holds what the reset would write").isFalse();
    }
}
