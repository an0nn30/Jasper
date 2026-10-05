package dev.jasper.app.settingsui;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.model.UiEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Main's notification-groups editor behaviour (merged 2026-10-05), on the branch's kit {@link ValueEditor}: the
 * dropdown, Reset and labels are kit nodes and the user's choices arrive as kit events.
 */
class NotificationGroupsEditorTest {
    private static final String ID = CoreSettings.NOTIFICATIONS_GROUPS.name();
    private static final NotificationGroupChoices.Group COMMANDS =
        new NotificationGroupChoices.Group("jasper.commands", "Long-running commands", "Jasper", "card");
    private static final NotificationGroupChoices.Group CONFIG =
        new NotificationGroupChoices.Group("jasper.config", "Configuration", "Jasper", "sticky");
    private static final NotificationGroupChoices.Group REMOTE =
        new NotificationGroupChoices.Group("dev.jasper.remote.activities", "Remote activities", "Remote", "card");

    private List<NotificationGroupChoices.Group> groups = List.of(COMMANDS, CONFIG);
    private int edits;

    private NotificationGroupsEditor editor() {
        return new NotificationGroupsEditor(new NotificationGroupChoices(() -> groups));
    }

    private int index(String groupId) {
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).id().equals(groupId)) return i;
        throw new AssertionError(groupId);
    }

    private void choose(NotificationGroupsEditor editor, String groupId, String token) {
        if (editor.event(ID, new UiEvent.Selected(NotificationGroupsEditor.modeId(ID, index(groupId)), Set.of(token)))) edits++;
    }

    private void reset(NotificationGroupsEditor editor, String groupId) {
        if (editor.event(ID, new UiEvent.Action(NotificationGroupsEditor.resetId(ID, index(groupId))))) edits++;
    }

    private Nodes.FormRow row(NotificationGroupsEditor editor, String groupId) {
        var stack = (Nodes.Stack) editor.node(ID, ControlState.READY);
        return (Nodes.FormRow) stack.children().get(index(groupId));
    }

    private Nodes.Choice combo(NotificationGroupsEditor editor, String groupId) { return (Nodes.Choice) row(editor, groupId).control(); }

    private Nodes.Button resetButton(NotificationGroupsEditor editor, String groupId) {
        return (Nodes.Button) row(editor, groupId).trailing().orElseThrow();
    }

    private static String rendered(Nodes.Choice choice, String token) {
        return choice.options().stream().filter(option -> option.id().equals(token)).map(Option::label).findFirst().orElseThrow();
    }

    private static List<String> texts(Nodes.Node node) {
        var texts = new ArrayList<String>();
        switch (node) {
            case Nodes.Stack stack -> stack.children().forEach(child -> texts.addAll(texts(child)));
            case Nodes.FormRow row -> { texts.add(row.label()); texts.add(row.caption()); }
            case Nodes.Text text -> texts.add(text.text());
            default -> { }
        }
        return texts;
    }

    @Test void everyGroupShowsItsModeWithTheDefaultMarked() {
        var editor = editor();
        editor.show(Map.of());
        assertThat(texts(editor.node(ID, ControlState.READY))).contains("Long-running commands", "Configuration")
            .anySatisfy(text -> assertThat(text).contains("Jasper"));
        assertThat(combo(editor, COMMANDS.id()).selectedId()).isEqualTo("card");
        assertThat(combo(editor, CONFIG.id()).selectedId()).isEqualTo("sticky");
        assertThat(combo(editor, COMMANDS.id()).kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
        assertThat(combo(editor, COMMANDS.id()).label()).isEqualTo("Long-running commands: how its notifications appear");
        assertThat(rendered(combo(editor, COMMANDS.id()), "card")).isEqualTo("Card (default)");
        assertThat(rendered(combo(editor, COMMANDS.id()), "history")).isEqualTo("History only");
        assertThat(rendered(combo(editor, CONFIG.id()), "sticky")).isEqualTo("Sticky card (default)");
        assertThat(combo(editor, COMMANDS.id()).options()).extracting(Option::id).containsExactly("card", "sticky", "history", "off");
        assertThat(resetButton(editor, COMMANDS.id()).state().enabled()).isFalse();
        assertThat(resetButton(editor, COMMANDS.id()).tooltip()).isEqualTo("Reset Long-running commands to its default");
        assertThat(edits).isZero();
    }

    @Test void choosingAModeAddsAnEntryAndChoosingTheDefaultRemovesIt() {
        var editor = editor();
        editor.show(Map.of());
        choose(editor, COMMANDS.id(), "history");
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "history"));
        assertThat(resetButton(editor, COMMANDS.id()).state().enabled()).isTrue();
        choose(editor, COMMANDS.id(), "card");
        assertThat(editor.value()).isEmpty();
        assertThat(edits).isEqualTo(2);
    }

    @Test void resetRemovesTheGroupsEntry() {
        var editor = editor();
        editor.show(Map.of(COMMANDS.id(), "off"));
        assertThat(combo(editor, COMMANDS.id()).selectedId()).isEqualTo("off");
        reset(editor, COMMANDS.id());
        assertThat(editor.value()).isEmpty();
        assertThat(combo(editor, COMMANDS.id()).selectedId()).isEqualTo("card");
        assertThat(edits).isEqualTo(1);
    }

    /** A plugin that is not running now keeps its entries through any edit of the others. */
    @Test void entriesForGroupsNotKnownNowAreKept() {
        var editor = editor();
        editor.show(Map.of(REMOTE.id(), "off", CONFIG.id(), "history"));
        choose(editor, COMMANDS.id(), "off");
        assertThat(editor.value()).isEqualTo(Map.of(REMOTE.id(), "off", CONFIG.id(), "history", COMMANDS.id(), "off"));
    }

    /** What the dialog re-shows after config.toml changes on disk is not an edit, and an untouched entry stays as written. */
    @Test void showingIsNotAnEditAndAnUntouchedEntryStaysExactlyAsWritten() {
        var editor = editor();
        editor.show(Map.of(COMMANDS.id(), "card"));
        assertThat(edits).isZero();
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "card"));
        assertThat(resetButton(editor, COMMANDS.id()).state().enabled()).as("an entry is there to reset").isTrue();
        choose(editor, CONFIG.id(), "off");
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "card", CONFIG.id(), "off"));
        editor.show(Map.of(CONFIG.id(), "history"));
        assertThat(editor.value()).as("showing replaces the user's choices").isEqualTo(Map.of(CONFIG.id(), "history"));
        assertThat(edits).isEqualTo(1);
    }

    @Test void refreshListsGroupsDeclaredSinceAndKeepsTheChoices() {
        var editor = editor();
        editor.show(Map.of());
        choose(editor, COMMANDS.id(), "history");
        groups = List.of(COMMANDS, CONFIG, REMOTE);
        editor.refresh();
        assertThat(combo(editor, REMOTE.id()).selectedId()).isEqualTo("card");
        assertThat(combo(editor, COMMANDS.id()).selectedId()).isEqualTo("history");
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "history"));
        assertThat(texts(editor.node(ID, ControlState.READY))).contains("Remote activities")
            .anySatisfy(text -> assertThat(text).startsWith("Remote"));
    }

    @Test void withNoGroupsYetTheEditorSaysSo() {
        groups = List.of();
        var editor = editor();
        editor.show(Map.of(REMOTE.id(), "off"));
        assertThat(texts(editor.node(ID, ControlState.READY))).contains("No notification groups yet.");
        assertThat(editor.value()).isEqualTo(Map.of(REMOTE.id(), "off"));
    }

    @Test void theFactoryBuildsAFullWidthEditorThatRemovesTheTableAtItsDefault() {
        var context = PageKit.context(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()), id -> { }, () -> { });
        var factory = Editors.notificationGroups(new NotificationGroupChoices(() -> groups));
        var editor = factory.build().apply(CoreSettings.NOTIFICATIONS_GROUPS, context);
        assertThat(editor).isInstanceOf(NotificationGroupsEditor.class);
        assertThat(editor.labelled()).isTrue();
        assertThat(editor.removesAtDefault()).isTrue();
        assertThat(factory.terms()).contains("card", "sticky", "history only", "off");
    }

    /** config.toml names groups by id, so each group's row shows its id (main showed it on hover). */
    @Test void eachGroupsRowShowsItsId() {
        var editor = editor();
        editor.show(Map.of());
        assertThat(row(editor, COMMANDS.id()).label()).isEqualTo("Long-running commands");
        assertThat(row(editor, COMMANDS.id()).caption()).contains("Jasper").contains(COMMANDS.id());
        assertThat(row(editor, CONFIG.id()).caption()).contains(CONFIG.id());
    }

    @Test void eventsForOtherNodesEditNothing() {
        var editor = editor();
        editor.show(Map.of());
        assertThat(editor.event(ID, new UiEvent.Selected("other", Set.of("off")))).isFalse();
        assertThat(editor.event(ID, new UiEvent.Selected(NotificationGroupsEditor.modeId(ID, 0), Set.of("bogus")))).isFalse();
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":reset"))).as("the row's own Reset is the page's").isFalse();
        assertThat(editor.value()).isEmpty();
    }
}
