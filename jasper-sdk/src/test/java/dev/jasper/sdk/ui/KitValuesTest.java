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
    @Test void rowMenusSwitchTooltipsAndSeveritiesAreValidated() {
        var title = List.<Node>of(text("t"));
        var items = new ArrayList<MenuItem>(List.of(new MenuItem("m", "Open", "", "open", true, false)));
        var row = new ListItem("r", Optional.empty(), title, List.of(), List.of(), false, Optional.of(items)); items.clear();
        assertThat(row.contextActions()).hasValueSatisfying(menu -> assertThat(menu).hasSize(1));
        assertThat(new ListItem("plain", Optional.empty(), title, List.of(), List.of()).contextActions()).isEmpty();
        assertThatThrownBy(() -> new ListItem("h", Optional.empty(), title, List.of(), List.of(), true, Optional.of(List.of())))
            .isInstanceOf(IllegalArgumentException.class);
        var twice = List.of(new MenuItem("m", "Open", "", "open", true, false), new MenuItem("m", "Again", "", "again", true, false));
        assertThatThrownBy(() -> new ListItem("r", Optional.empty(), title, List.of(), List.of(), false, Optional.of(twice)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Toggle("t", ControlState.READY, "On", true, "on", true).tooltip()).isEmpty();
        assertThatThrownBy(() -> new Toggle("t", ControlState.READY, "On", true, "on", true, null)).isInstanceOf(NullPointerException.class);
        assertThat(new Icon("i", ControlState.READY, "info", Hue.NONE, IconSize.RAIL, "Info").severity()).isEmpty();
        assertThatThrownBy(() -> new Icon("i", ControlState.READY, "info", Hue.NONE, IconSize.RAIL, "Info", null)).isInstanceOf(NullPointerException.class);
    }
    @Test void buttonsDefaultToTheStandardPresentationAndRejectNone() {
        assertThat(new Button("b", ControlState.READY, "Open", "", ButtonKind.LINK, "open", "").presentation()).isEqualTo(ButtonPresentation.STANDARD);
        assertThat(new MenuButton("m", ControlState.READY, "More", "", ButtonKind.LINK, "", List.of(new MenuItem("i", "I", "", "i", true, false)), "").presentation())
            .isEqualTo(ButtonPresentation.STANDARD);
        assertThatThrownBy(() -> new Button("b", ControlState.READY, "Open", "", ButtonKind.LINK, "open", "", null)).isInstanceOf(NullPointerException.class);
    }
    /** Plan 2 Task 8-kit: row tooltips, list Enter and Delete actions and pending switches, with their defaults. */
    @Test void remoteRowCapabilitiesAreValidatedAndDefaultToNone() {
        var title = List.<Node>of(text("t"));
        assertThat(new ListItem("r", Optional.empty(), title, List.of(), List.of()).tooltip()).isEmpty();
        assertThat(new ListItem("r", Optional.empty(), title, List.of(), List.of(), false, Optional.empty(), "Up 3 min").tooltip()).isEqualTo("Up 3 min");
        assertThatThrownBy(() -> new ListItem("h", Optional.empty(), title, List.of(), List.of(), true, Optional.empty(), "tip")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListItem("r", Optional.empty(), title, List.of(), List.of(), false, Optional.empty(), null)).isInstanceOf(NullPointerException.class);
        assertThat(new TableRow("r", Map.of(), Optional.empty()).tooltip()).isEmpty();
        assertThat(new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "notes.txt").tooltip()).isEqualTo("notes.txt");
        assertThatThrownBy(() -> new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), null)).isInstanceOf(NullPointerException.class);
        var item = new ListItem("a", Optional.empty(), title, List.of(), List.of());
        var list = new ItemList("l", ControlState.READY, ListKind.DOUBLE, List.of(item), Set.of(), "edit", List.of());
        assertThat(list.enterAction()).isEmpty(); assertThat(list.deleteAction()).isEmpty();
        var keyed = new ItemList("l", ControlState.READY, ListKind.DOUBLE, List.of(item), Set.of(), "edit", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "toggle", "delete");
        assertThat(keyed.enterAction()).isEqualTo("toggle"); assertThat(keyed.deleteAction()).isEqualTo("delete");
        for (var keys : List.of(List.of("go", ""), List.of("", "delete")))
            assertThatThrownBy(() -> new ItemList("l", ControlState.READY, ListKind.RESULTS, List.of(item), Set.of(), "run", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), keys.get(0), keys.get(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemList("l", ControlState.READY, ListKind.DOUBLE, List.of(item), Set.of(), "edit", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), null, "")).isInstanceOf(NullPointerException.class);
        assertThat(new Toggle("t", ControlState.READY, "On", true, "on", true, "").pending()).isFalse();
        assertThat(new Toggle("t", ControlState.READY, "On", true, "on", true, "", true).pending()).isTrue();
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
    @Test void browserDescriptorsValidateWeightsCrumbsMenusAndEditingActions() {
        assertThatThrownBy(() -> new Column("name", "Name", TextRole.BODY, false, 0, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Column("name", "Name", TextRole.BODY, false).weight()).isEqualTo(1);
        assertThat(new Column("name", "Name", TextRole.BODY, false).sorted()).isEmpty();
        var pin = new MenuItem("pin", "Pin", "", "pin", true, false);
        assertThatThrownBy(() -> new TableRow("r", Map.of(), Optional.empty(), false, Optional.of(List.of(pin, pin)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new TableRow("r", Map.of(), Optional.empty()).muted()).isFalse();
        var item = new TreeItem("t", "T", Optional.empty(), true, List.of());
        assertThat(item.tooltip()).isEmpty(); assertThat(item.trailing()).isEmpty(); assertThat(item.status()).isEmpty(); assertThat(item.contextActions()).isEmpty();
        var trailing = new java.util.ArrayList<>(List.of("Global"));
        var copied = new TreeItem("t", "T", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(), trailing, Optional.empty());
        trailing.clear(); assertThat(copied.trailing()).containsExactly("Global");
        var crumb = new Crumb("c", "Users", List.of());
        assertThatThrownBy(() -> new Breadcrumbs("path", ControlState.READY, List.of(crumb, crumb), "Location", false, "", "", "", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Breadcrumbs("path", ControlState.READY, List.of(crumb), "Location", true, "/", "edit", "go", "")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Table("t", ControlState.READY, List.of(), List.of(), Set.of(), "", List.of()).scroll()).isEqualTo(ScrollPolicy.STANDARD);
        assertThatThrownBy(() -> new UiEvent.Sorted("files", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UiEvent.Clicked("tree", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new UiEvent.Clicked("tree", "leaf").itemId()).isEqualTo("leaf");
    }
}
