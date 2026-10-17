package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TableRow;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): Notifications' groups as one grouped table. */
class NotificationsTableTest {
    static final String ID = "notifications.groups";

    static Nodes.Table table(NotificationGroupsEditor editor) {
        return (Nodes.Table) ResetMenuTest.find(editor.node(ID, ControlState.READY), ID + ":table");
    }

    @Test void everyOwnerHeadsItsGroupsWithTheirDisplayAndSource() throws Exception {
        edt(() -> {
            var editor = new NotificationGroupsEditor(new NotificationGroupChoices(EnterpriseSettings::groups), null, false, true);
            editor.show(Map.of("jasper.config", "card"));
            var table = table(editor);
            assertThat(table.columns()).extracting(dev.jasper.ui.model.Column::title).containsExactly("Group", "Display", "Source");
            assertThat(table.columns().getLast().role()).as("Source reads muted, as the mock's \"Default\"").isEqualTo(Nodes.TextRole.DESCRIPTION);
            var headings = table.rows().stream().filter(row -> row.heading().isPresent()).map(row -> row.heading().orElseThrow()).toList();
            assertThat(headings).extracting(TableRow.Heading::title).containsExactly("Jasper", "THD Sourcing Crypto", "Editor", "Credential Vault", "File Server", "Snippets");
            assertThat(headings.getFirst().count()).hasValue(6);
            assertThat(headings.getFirst().countNoun()).isEqualTo("group");
            var configuration = table.rows().stream().filter(row -> "Configuration".equals(row.cells().get("group"))).findFirst().orElseThrow();
            assertThat(configuration.cells().get("source")).as("saved as card, its default is sticky").isEqualTo("Custom");
            var display = (Nodes.Choice) configuration.cellNodes().get("display");
            assertThat(display.kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
            assertThat(display.selectedId()).isEqualTo("card");
            assertThat(display.options()).allMatch(option -> option.icon().isEmpty());
            assertThat(configuration.contextActions().orElseThrow()).extracting(MenuItem::label).containsExactly("Reset to Default");
            assertThat(ResetMenuTest.find(editor.node(ID, ControlState.READY), ID + ":description")).as("the description stays").isNotNull();
        });
    }

    @Test void resetToDefaultReturnsACustomRowToDefault() throws Exception {
        edt(() -> {
            var editor = new NotificationGroupsEditor(new NotificationGroupChoices(EnterpriseSettings::groups), null, false, true);
            editor.show(Map.of("jasper.config", "card"));
            int index = EnterpriseSettings.groups().stream().map(NotificationGroupChoices.Group::id).toList().indexOf("jasper.config");
            assertThat(editor.event(ID, new UiEvent.Action(NotificationGroupsEditor.resetId(ID, index)))).isTrue();
            assertThat(editor.value()).doesNotContainKey("jasper.config");
            var row = table(editor).rows().stream().filter(candidate -> "Configuration".equals(candidate.cells().get("group"))).findFirst().orElseThrow();
            assertThat(row.cells().get("source")).isEqualTo("Default");
            assertThat(((Nodes.Choice) row.cellNodes().get("display")).selectedId()).isEqualTo("sticky");
            assertThat(row.contextActions().orElseThrow().getFirst().enabled()).isFalse();
        });
    }

    @Test void aGroupThatArrivesLaterJoinsItsOwnersHeadingAndAnUnknownModeShowsTheDefault() throws Exception {
        edt(() -> {
            var groups = new ArrayList<>(EnterpriseSettings.groups().subList(0, 2));
            var editor = new NotificationGroupsEditor(new NotificationGroupChoices(() -> List.copyOf(groups)), null, false, true);
            editor.show(Map.of("jasper.general", "retired-mode"));
            var general = table(editor).rows().stream().filter(row -> "Jasper".equals(row.cells().get("group"))).findFirst().orElseThrow();
            assertThat(((Nodes.Choice) general.cellNodes().get("display")).selectedId()).isEqualTo("card");
            assertThat(general.cells().get("source")).as("the default shown, so Source reads Default").isEqualTo("Default");
            assertThat(general.contextActions().orElseThrow().getFirst().enabled()).as("the stale entry can still be reset").isTrue();
            groups.add(EnterpriseSettings.groups().get(2));
            editor.refresh();
            var heading = table(editor).rows().getFirst().heading().orElseThrow();
            assertThat(heading.count()).hasValue(3);
            assertThat(editor.value()).as("an unknown saved mode is kept as written").containsEntry("jasper.general", "retired-mode");
        });
    }
}
