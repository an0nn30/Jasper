package dev.jasper.ui.model;

import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.ui.model.Nodes.*;
import static org.assertj.core.api.Assertions.*;

class ViewSpecTest {
    private Text text(String id) { return new Text(id, ControlState.READY, "Text", TextRole.BODY); }
    @Test void duplicateNodeIdsAndBlankLabelsAreRejected() {
        assertThatThrownBy(() -> new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL,
            List.of(text("same"), text("same"))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Button("id", ControlState.READY, " ", "", ButtonKind.PRIMARY, "go", ""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> text(" ")).isInstanceOf(IllegalArgumentException.class);
    }
    /** Plan 3b: fields are editable unless they say so, and a stepper or slider is never read-only. */
    @Test void readOnlyFieldsAreValidatedAndDefaultToEditable() {
        assertThat(new Field("name", ControlState.READY, FieldKind.TEXT, "", "", "Name", "").readOnly()).isFalse();
        assertThat(new Field("find", ControlState.READY, FieldKind.SEARCH, "", "", "Find", "", Optional.empty(), FieldPresentation.STANDARD, "", "", "").readOnly()).isFalse();
        assertThat(new Field("count", ControlState.READY, FieldKind.NUMBER, "1", "", "Count", "", Optional.of(new Numeric(0, 9, 1, 0)),
            FieldPresentation.STANDARD, "", "", "", true).readOnly()).isTrue();
        for (var presentation : List.of(FieldPresentation.STEPPER, FieldPresentation.SLIDER))
            assertThatIllegalArgumentException().isThrownBy(() -> new Field("n", ControlState.READY, FieldKind.NUMBER, "1", "", "N", "",
                Optional.of(new Numeric(0, 9, 1, 0)), presentation, "", "", "", true)).withMessageContaining("never read-only");
    }
    /** Plan 3b (spec 8.2): EXCLUSIVE shows two to four options and TABS two or more, wherever the choice sits. */
    @Test void exclusiveChoicesShowTwoToFourOptionsAndTabsTwoOrMore() {
        for (int count = 0; count <= 6; count++) {
            var options = java.util.stream.IntStream.range(0, count).mapToObj(i -> new Option("o" + i, "Option " + i, Optional.empty())).toList();
            var exclusive = new Choice("pick", ControlState.READY, ChoiceKind.EXCLUSIVE, options, "", "Pick", "");
            var tabs = new Choice("tabs", ControlState.READY, ChoiceKind.TABS, options, "", "Tabs", "");
            if (count >= 2 && count <= 4) assertThatCode(() -> new ViewSpec(exclusive)).as("%d options", count).doesNotThrowAnyException();
            else assertThatIllegalArgumentException().as("%d options", count).isThrownBy(() -> new ViewSpec(exclusive)).withMessageContaining("2 to 4 options");
            if (count >= 2) assertThatCode(() -> new ViewSpec(tabs)).as("%d tabs", count).doesNotThrowAnyException();
            else assertThatIllegalArgumentException().as("%d tabs", count).isThrownBy(() -> new ViewSpec(tabs)).withMessageContaining("two or more options");
        }
        var one = new Choice("one", ControlState.READY, ChoiceKind.EXCLUSIVE, List.of(new Option("a", "A", Optional.empty())), "a", "One", "");
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new ItemList("list", ControlState.READY, ListKind.SINGLE,
            List.of(new ListItem("row", Optional.empty(), List.of(text("title")), List.of(), List.of(one))), Set.of(), "", List.of())))
            .withMessageContaining("2 to 4 options");
    }
    /** Plan 3b: a swatch is a square of a hue, so it needs one and takes no severity. */
    @Test void swatchesNeedAHueAndTakeNoSeverity() {
        assertThat(new Icon("ftp", ControlState.READY, "file", Hue.AMBER, IconSize.SWATCH, "FTP").size()).isEqualTo(IconSize.SWATCH);
        assertThatIllegalArgumentException().isThrownBy(() -> new Icon("none", ControlState.READY, "file", Hue.NONE, IconSize.SWATCH, "None"))
            .withMessageContaining("NONE");
        assertThatIllegalArgumentException().isThrownBy(() -> new Icon("warn", ControlState.READY, "file", Hue.AMBER, IconSize.SWATCH, "Warn", Optional.of(Severity.WARNING)))
            .withMessageContaining("severity");
        assertThat(new Icon("glyph", ControlState.READY, "file", Hue.NONE, IconSize.CAPTION, "File").hue()).isEqualTo(Hue.NONE);
    }
    /** Plan 3b (spec 8.2): a Separator stands between two items of an INLINE or ACTIONS stack, and nowhere else. */
    @Test void separatorsStandBetweenTwoItemsOfAnInlineOrActionsStack() {
        var rule = new Separator("rule", ControlState.READY);
        for (var role : List.of(LayoutRole.INLINE, LayoutRole.ACTIONS))
            assertThatCode(() -> new ViewSpec(new Nodes.Stack("bar", ControlState.READY, role, List.of(text("a"), rule, text("b"))))).doesNotThrowAnyException();
        for (var children : List.of(List.<Node>of(rule, text("a")), List.<Node>of(text("a"), rule),
                List.<Node>of(text("a"), rule, new Separator("again", ControlState.READY), text("b"))))
            assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new Nodes.Stack("bar", ControlState.READY, LayoutRole.INLINE, children)))
                .withMessageContaining("between two items");
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new Nodes.Stack("bar", ControlState.READY, LayoutRole.VERTICAL,
            List.of(text("a"), rule, text("b"))))).withMessageContaining("INLINE or ACTIONS");
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(rule)).withMessageContaining("INLINE or ACTIONS");
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new Card("card", ControlState.READY, CardKind.FORM,
            List.of(text("a"), rule, text("b"))))).withMessageContaining("INLINE or ACTIONS");
        // List rows hold presentation nodes only; a separator is not one, even inside a row's INLINE stack.
        assertThatIllegalArgumentException().isThrownBy(() -> new ListItem("row", Optional.empty(), List.of(new Nodes.Stack("line", ControlState.READY,
            LayoutRole.INLINE, List.of(text("a"), rule, text("b")))), List.of(), List.of())).withMessageContaining("Unsupported inline row node: Separator");
    }
    /** Plan 3b (spec 8.2): a Notice is one sentence with at most one SECONDARY or LINK action, in a form's sections or a VERTICAL stack. */
    @Test void noticesAreValidatedAndPlacedInFormsOrVerticalStacks() {
        var link = new Button("details", ControlState.READY, "Details", "", ButtonKind.LINK, "details", "");
        var notice = new Notice("notice", ControlState.READY, Severity.WARNING, "Plain FTP sends the password unencrypted", List.of(link));
        assertThat(new Notice("plain", ControlState.READY, Severity.INFO, "Saved").actions()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new Notice("blank", ControlState.READY, Severity.INFO, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new Notice("two", ControlState.READY, Severity.INFO, "Saved", List.of(link,
            new Button("more", ControlState.READY, "More", "", ButtonKind.LINK, "more", "")))).withMessageContaining("at most one action");
        for (var kind : List.of(ButtonKind.PRIMARY, ButtonKind.GHOST, ButtonKind.DESTRUCTIVE_GHOST))
            assertThatIllegalArgumentException().isThrownBy(() -> new Notice("kind", ControlState.READY, Severity.INFO, "Saved",
                List.of(new Button("go", ControlState.READY, "Go", "", kind, "go", "")))).withMessageContaining("SECONDARY or LINK");
        assertThatCode(() -> new ViewSpec(new Nodes.Stack("body", ControlState.READY, LayoutRole.VERTICAL, List.of(notice)))).doesNotThrowAnyException();
        assertThatCode(() -> new ViewSpec(new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(notice),
            Optional.empty(), List.of()))).doesNotThrowAnyException();
        for (Node misplaced : List.<Node>of(notice, new Nodes.Stack("bar", ControlState.READY, LayoutRole.INLINE, List.of(notice)),
                new Card("card", ControlState.READY, CardKind.FORM, List.of(notice)),
                new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.of(notice), List.of(), Optional.empty(), List.of())))
            assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(misplaced)).withMessageContaining("form's sections or a VERTICAL stack");
        // The action is a node of the view: its id is checked with the others, and list rows take no notice.
        assertThatIllegalArgumentException().isThrownBy(() -> new ViewSpec(new Nodes.Stack("details", ControlState.READY, LayoutRole.VERTICAL, List.of(notice))))
            .withMessageContaining("Duplicate node: details");
        assertThatIllegalArgumentException().isThrownBy(() -> new ListItem("row", Optional.empty(), List.of(notice), List.of(), List.of()))
            .withMessageContaining("Unsupported inline row node: Notice");
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
    @Test void browserDescriptorsValidateWeightsCrumbsMenusAndEditingActions() {
        assertThatThrownBy(() -> new Column("name", "Name", TextRole.BODY, false, 0, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Column("name", "Name", TextRole.BODY, false, Double.NaN, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Column("name", "Name", TextRole.BODY, false).weight()).isEqualTo(1);
        var pin = new MenuItem("pin", "Pin", "", "pin", true, false);
        assertThatThrownBy(() -> new TableRow("r", Map.of(), Optional.empty(), false, Optional.of(List.of(pin, pin)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TreeItem("t", "T", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(), List.of(), Optional.of(List.of(pin, pin))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TreeItem("t", "T", Optional.empty(), true, List.of()).trailing()).isEmpty();
        var crumb = new Crumb("c", "Users", List.of());
        assertThatThrownBy(() -> new Breadcrumbs("path", ControlState.READY, List.of(crumb, crumb), "Location", false, "", "", "", ""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Breadcrumbs("path", ControlState.READY, List.of(crumb), "Location", true, "/", "edit", "", "done"))
            .as("editing needs submit and cancel").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Crumb("c", " ", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new ViewSpec(new Breadcrumbs("path", ControlState.READY, List.of(crumb), "Location", true, "/", "edit", "go", "done")))
            .doesNotThrowAnyException();
        assertThat(new Table("t", ControlState.READY, List.of(), List.of(), Set.of(), "", List.of()).scroll()).isEqualTo(ScrollPolicy.STANDARD);
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
