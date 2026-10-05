package dev.jasper.sdk.ui;

import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.ui.Nodes.*;
import static org.assertj.core.api.Assertions.*;

class KitValuesTest {
    private Text text(String id) { return new Text(id, ControlState.READY, "Text", TextRole.BODY); }
    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {
        assertThatThrownBy(() -> new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL,
            List.of(text("same"), text("same"))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Button("id", ControlState.READY, " ", "", ButtonKind.PRIMARY, "go", ""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> text(" ")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void choiceTableListAndTreeValidateReferencesAndUniqueIds() {
        assertThatThrownBy(() -> new ViewSpec(new Choice("pick", ControlState.READY, ChoiceKind.DROPDOWN,
            List.of(new Option("a", "A", Optional.empty())), "missing", "Choice", "choose")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewSpec(new Table("table", ControlState.READY,
            List.of(new Column("name", "Name", TextRole.BODY, false)),
            List.of(new TableRow("r", Map.of("unknown", "x"), Optional.empty())), Set.of(), "", List.of())))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewSpec(new Tree("tree", ControlState.READY,
            List.of(new TreeItem("a", "A", Optional.empty(), true, List.of())), Set.of("a"), Set.of(), "")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewSpec(new ItemList("list", ControlState.READY, ListKind.SINGLE,
            List.of(new ListItem("a", Optional.empty(), List.of(text("title")), List.of(), List.of())), Set.of("missing"), "", List.of())))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void optionIdsAreScopedToTheirOwningControls() {
        var options = List.of(new Option("same", "Same", Optional.empty()));
        assertThatCode(() -> new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(
            new Choice("first", ControlState.READY, ChoiceKind.DROPDOWN, options, "same", "First", ""),
            new Choice("second", ControlState.READY, ChoiceKind.DROPDOWN, options, "same", "Second", "")))))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> new Choice("duplicate", ControlState.READY, ChoiceKind.DROPDOWN,
            List.of(options.getFirst(), options.getFirst()), "same", "Duplicate", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void duplicateIdsInsideRowAndPanelChildrenAreFound() {
        var list = new ItemList("list", ControlState.READY, ListKind.DOUBLE,
            List.of(new ListItem("a", Optional.of(text("same")), List.of(text("same")), List.of(), List.of())), Set.of(), "", List.of());
        assertThatThrownBy(() -> new ViewSpec(list)).isInstanceOf(IllegalArgumentException.class);
        var header = new Header("same", ControlState.READY, "Header", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        assertThatThrownBy(() -> new ViewSpec(new Panel("panel", ControlState.READY, header, Optional.empty(), text("same"), Optional.empty())))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nestedCollectionsAreImmutableSnapshots() {
        var children = new ArrayList<Node>(); children.add(text("text"));
        var root = new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, children);
        children.clear();
        assertThat(root.children()).hasSize(1);
        assertThatThrownBy(() -> root.children().clear()).isInstanceOf(UnsupportedOperationException.class);
        var cells = new HashMap<String,String>(); cells.put("name", "A");
        var row = new TableRow("r", cells, Optional.empty()); cells.clear();
        assertThat(row.cells()).containsEntry("name", "A");
    }
    @Test void strengthProgressHeaderAndLeafShapeAreValidated() {
        assertThatThrownBy(() -> new Strength("s", ControlState.READY, 5, "Strength")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Progress("p", ControlState.READY, Double.NaN, "Progress")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Header("h", ControlState.READY, "Header", OptionalInt.of(-1), Optional.empty(), Optional.empty(), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Header("h", ControlState.READY, "Header", OptionalInt.empty(), Optional.empty(), Optional.of(text("wrong")), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TreeItem("leaf", "Leaf", Optional.empty(), true,
            List.of(new TreeItem("child", "Child", Optional.empty(), true, List.of())))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void secretsHaveNoValueProperty() {
        assertThat(Arrays.stream(Secret.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("value", "password", "text");
        assertThat(Arrays.stream(UiEvent.SecretChanged.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
            .containsExactly("id");
    }
    @Test void selectedEventsSnapshotTheirSetsAndReordersRejectNegativeDestinations() {
        var ids = new HashSet<>(Set.of("a")); var event = new UiEvent.Selected("list", ids); ids.clear();
        assertThat(event.ids()).containsExactly("a");
        assertThatThrownBy(() -> new UiEvent.Reordered("list", "a", -1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void customContentIdsAreUniqueEvenWhenNodeIdsDiffer() {
        assertThatThrownBy(() -> new ViewSpec(new Nodes.Stack("root",ControlState.READY,LayoutRole.VERTICAL,List.of(
            new Custom("one",ControlState.READY,"document",CustomKind.EDITOR,"Editor"),
            new Custom("two",ControlState.READY,"document",CustomKind.EDITOR,"Editor")))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void settingsDescriptorsCarryCountsEmphasisHighlightSearchAndNumberBounds() {
        var item = new TreeItem("page", "Page", Optional.empty(), true, List.of(), OptionalInt.of(3), true);
        assertThat(item.count()).hasValue(3); assertThat(item.emphasized()).isTrue();
        assertThat(new TreeItem("legacy", "Legacy", Optional.empty(), true, List.of()).count()).isEmpty();
        assertThatThrownBy(() -> new TreeItem("bad", "Bad", Optional.empty(), true, List.of(), OptionalInt.of(-1), false))
            .isInstanceOf(IllegalArgumentException.class);
        var toggle = new Toggle("t", ControlState.READY, "On", true, "t", false);
        assertThat(new SettingRow("row", ControlState.READY, "Title", "", toggle, false, true).highlighted()).isTrue();
        assertThat(new SettingRow("row", ControlState.READY, "Title", "", toggle, false).highlighted()).isFalse();
        assertThat(new FormRow("form", ControlState.READY, "Label", "", toggle, Optional.empty(), true).highlighted()).isTrue();
        assertThat(new FormRow("form", ControlState.READY, "Label", "", toggle, Optional.empty()).highlighted()).isFalse();
        var options = List.of(new Option("pinned", "Pinned", Optional.empty()), new Option("a", "A", Optional.empty()));
        var search = new ChoiceSearch(1, "No matches", Optional.empty());
        assertThatCode(() -> new ViewSpec(new Choice("s", ControlState.READY, ChoiceKind.SEARCHABLE, options, "a", "Theme", "", "", Optional.of(search))))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> new Choice("s", ControlState.READY, ChoiceKind.SEARCHABLE, options, "a", "Theme", ""))
            .as("searchable needs its search configuration").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Choice("d", ControlState.READY, ChoiceKind.DROPDOWN, options, "a", "Theme", "", "", Optional.of(search)))
            .as("only searchable choices search").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Choice("s", ControlState.READY, ChoiceKind.SEARCHABLE, options, "a", "Theme", "", "", Optional.of(new ChoiceSearch(3, "No matches", Optional.empty()))))
            .as("pinned entries exist").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Choice("s", ControlState.READY, ChoiceKind.SEARCHABLE, options, "a", "Font", "", "", Optional.of(new ChoiceSearch(0, "No matches", Optional.of("Menlo")))))
            .as("free entry owns the value").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChoiceSearch(0, " ", Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        var bounds = new Numeric(0, 100, 0.5, 1);
        assertThat(new Field("n", ControlState.READY, FieldKind.NUMBER, "1", "", "Size", "", Optional.of(bounds)).numeric()).contains(bounds);
        assertThatThrownBy(() -> new Field("n", ControlState.READY, FieldKind.NUMBER, "1", "", "Size", ""))
            .as("a number states its bounds").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Field("t", ControlState.READY, FieldKind.TEXT, "1", "", "Size", "", Optional.of(bounds)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Numeric(5, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Numeric(0, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Numeric(0, 1, 1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Numeric(Double.NaN, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
