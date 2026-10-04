package dev.jasper.app.settingsui;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class NotificationGroupsEditorTest {
    private static final NotificationGroupChoices.Group COMMANDS =
        new NotificationGroupChoices.Group("jasper.commands", "Long-running commands", "Jasper", "card");
    private static final NotificationGroupChoices.Group CONFIG =
        new NotificationGroupChoices.Group("jasper.config", "Configuration", "Jasper", "sticky");
    private static final NotificationGroupChoices.Group REMOTE =
        new NotificationGroupChoices.Group("dev.jasper.remote.activities", "Remote activities", "Remote", "card");

    private List<NotificationGroupChoices.Group> groups = List.of(COMMANDS, CONFIG);
    private int edits;

    private NotificationGroupsEditor editor() {
        var editor = new NotificationGroupsEditor(new NotificationGroupChoices(() -> groups));
        editor.onEdit(() -> edits++);
        return editor;
    }

    private static String rendered(NotificationGroupsEditor editor, String id, String token) {
        var combo = editor.combo(id);
        var label = (JLabel) combo.getRenderer().getListCellRendererComponent(new JList<>(), token, 0, false, false);
        return label.getText();
    }

    private static List<String> labels(Container container) {
        var texts = new ArrayList<String>();
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label) texts.add(label.getText());
            if (child instanceof Container nested) texts.addAll(labels(nested));
        }
        return texts;
    }

    @Test void everyGroupShowsItsModeWithTheDefaultMarked() {
        var editor = editor();
        editor.show(Map.of());
        assertThat(labels(editor.component())).contains("Long-running commands", "Configuration", "Jasper");
        assertThat(editor.combo(COMMANDS.id()).getSelectedItem()).isEqualTo("card");
        assertThat(editor.combo(CONFIG.id()).getSelectedItem()).isEqualTo("sticky");
        assertThat(rendered(editor, COMMANDS.id(), "card")).isEqualTo("Card (default)");
        assertThat(rendered(editor, COMMANDS.id(), "history")).isEqualTo("History only");
        assertThat(rendered(editor, CONFIG.id(), "sticky")).isEqualTo("Sticky card (default)");
        assertThat(editor.reset(COMMANDS.id()).isEnabled()).isFalse();
        assertThat(edits).isZero();
    }

    @Test void choosingAModeAddsAnEntryAndChoosingTheDefaultRemovesIt() {
        var editor = editor();
        editor.show(Map.of());
        editor.combo(COMMANDS.id()).setSelectedItem("history");
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "history"));
        assertThat(editor.reset(COMMANDS.id()).isEnabled()).isTrue();
        editor.combo(COMMANDS.id()).setSelectedItem("card");
        assertThat(editor.value()).isEmpty();
        assertThat(edits).isEqualTo(2);
    }

    @Test void resetRemovesTheGroupsEntry() {
        var editor = editor();
        editor.show(Map.of(COMMANDS.id(), "off"));
        assertThat(editor.combo(COMMANDS.id()).getSelectedItem()).isEqualTo("off");
        editor.reset(COMMANDS.id()).doClick();
        assertThat(editor.value()).isEmpty();
        assertThat(editor.combo(COMMANDS.id()).getSelectedItem()).isEqualTo("card");
        assertThat(edits).isEqualTo(1);
    }

    /** A plugin that is not running now keeps its entries through any edit of the others. */
    @Test void entriesForGroupsNotKnownNowAreKept() {
        var editor = editor();
        editor.show(Map.of(REMOTE.id(), "off", CONFIG.id(), "history"));
        editor.combo(COMMANDS.id()).setSelectedItem("off");
        assertThat(editor.value()).isEqualTo(Map.of(REMOTE.id(), "off", CONFIG.id(), "history", COMMANDS.id(), "off"));
    }

    /** What the dialog re-shows after config.toml changes on disk is not an edit, and an untouched entry stays as written. */
    @Test void showingIsNotAnEditAndAnUntouchedEntryStaysExactlyAsWritten() {
        var editor = editor();
        editor.show(Map.of(COMMANDS.id(), "card"));
        assertThat(edits).isZero();
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "card"));
        assertThat(editor.reset(COMMANDS.id()).isEnabled()).as("an entry is there to reset").isTrue();
        editor.combo(CONFIG.id()).setSelectedItem("off");
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "card", CONFIG.id(), "off"));
        editor.show(Map.of(CONFIG.id(), "history"));
        assertThat(editor.value()).as("showing replaces the user's choices").isEqualTo(Map.of(CONFIG.id(), "history"));
        assertThat(edits).isEqualTo(1);
    }

    @Test void refreshListsGroupsDeclaredSinceAndKeepsTheChoices() {
        var editor = editor();
        editor.show(Map.of());
        editor.combo(COMMANDS.id()).setSelectedItem("history");
        groups = List.of(COMMANDS, CONFIG, REMOTE);
        editor.refresh();
        assertThat(editor.combo(REMOTE.id()).getSelectedItem()).isEqualTo("card");
        assertThat(editor.combo(COMMANDS.id()).getSelectedItem()).isEqualTo("history");
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "history"));
        assertThat(labels(editor.component())).contains("Remote activities", "Remote");
    }

    @Test void withNoGroupsYetTheEditorSaysSo() {
        groups = List.of();
        var editor = editor();
        editor.show(Map.of(REMOTE.id(), "off"));
        assertThat(labels(editor.component())).contains("No notification groups yet.");
        assertThat(editor.value()).isEqualTo(Map.of(REMOTE.id(), "off"));
    }

    @Test void theFactoryBuildsAFullWidthEditorThatRemovesTheTableAtItsDefault() {
        var context = new PageContext(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
            id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { });
        var factory = Editors.notificationGroups(new NotificationGroupChoices(() -> groups));
        var editor = factory.build().apply(CoreSettings.NOTIFICATIONS_GROUPS, context);
        assertThat(editor).isInstanceOf(NotificationGroupsEditor.class);
        assertThat(editor.labelled()).isTrue();
        assertThat(editor.removesAtDefault()).isTrue();
        assertThat(factory.terms()).contains("card", "sticky", "history only", "off");
    }
}
