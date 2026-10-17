package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): the environment table, Add Variable…, Edit… and Remove. */
class EnvironmentTableTest {
    static final String ID = CoreSettings.TERMINAL_ENV.name();

    static Editors.Table editor(ArrayDeque<Optional<List<String>>> answers) {
        var parsed = ConfigLoader.parse(Path.of("/fixture/config.toml"), "", true);
        var context = new PageContext(new SettingsDraft(parsed.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { },
            () -> null, KitTestSupport::context, SettingsChromeTest.CLASSIC, Composition.CLASSIC, new Sheets(request -> answers.isEmpty() ? Optional.empty() : answers.poll()));
        return new Editors.Table(CoreSettings.TERMINAL_ENV, context, true);
    }

    @Test void withNoVariablesOnlyTheRowAndAddVariableShow() {
        var editor = editor(new ArrayDeque<>());
        editor.show(Map.of());
        var node = (Nodes.Stack) editor.node(ID, ControlState.READY);
        assertThat(node.children()).singleElement().isInstanceOf(Nodes.SettingRow.class);
        var row = (Nodes.SettingRow) node.children().getFirst();
        assertThat(row.title()).isEqualTo(CoreSettings.TERMINAL_ENV.label());
        assertThat(row.description()).isEqualTo("Applies to new panes");
        assertThat(((Nodes.Button) row.control()).label()).isEqualTo("Add Variable…");
        assertThat(row.contextActions()).as("Reset to Default in the row's menu (spec 12.1)").extracting(MenuItem::action)
            .containsExactly(ID + ":reset");
        assertThat(row.contextActions().getFirst().enabled()).as("at its default").isFalse();
        var highlighted = (Nodes.SettingRow) ((Nodes.Stack) editor.node(ID, ControlState.READY, true)).children().getFirst();
        assertThat(highlighted.highlighted()).as("the search highlight reaches the row").isTrue();
    }

    @Test void addingAnEmptyValueAndAValueWithEqualsShowsThemAsWritten() {
        var answers = new ArrayDeque<Optional<List<String>>>(List.of(Optional.of(List.of("EMPTY", "")), Optional.of(List.of("OPTS", "a=b=c"))));
        var editor = editor(answers);
        editor.show(Map.of());
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":add"))).isTrue();
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":add"))).isTrue();
        assertThat(editor.value()).containsExactly(Map.entry("EMPTY", ""), Map.entry("OPTS", "a=b=c"));
        var table = (Nodes.Table) ((Nodes.Stack) editor.node(ID, ControlState.READY)).children().get(1);
        assertThat(table.columns()).extracting(dev.jasper.ui.model.Column::title).containsExactly("Name", "Value");
        assertThat(table.rows()).extracting(row -> row.cells().get("value")).containsExactly("", "a=b=c");
        assertThat(table.rows().getFirst().contextActions().orElseThrow()).extracting(MenuItem::label).containsExactly("Edit…", "Remove");
        var row = (Nodes.SettingRow) ((Nodes.Stack) editor.node(ID, ControlState.READY)).children().getFirst();
        assertThat(row.contextActions().getFirst().enabled()).as("modified: Reset to Default is enabled").isTrue();
    }

    @Test void aRenameReplacesTheOtherEntryAndCancelChangesNothing() {
        var answers = new ArrayDeque<Optional<List<String>>>(List.of(Optional.of(List.of("B", "2")), Optional.empty()));
        var editor = editor(answers);
        editor.show(Map.of("A", "1", "B", "x"));
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":edit-0"))).as("rename A to B").isTrue();
        assertThat(editor.value()).containsExactly(Map.entry("B", "2"));
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":edit-0"))).as("cancelled").isFalse();
        assertThat(editor.value()).containsExactly(Map.entry("B", "2"));
        assertThat(editor.event(ID, new UiEvent.Action(ID + ":remove-0"))).isTrue();
        assertThat(editor.value()).isEmpty();
    }
}
