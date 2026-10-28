package dev.jasper.sdk.ui;

import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.ui.Nodes.*;
import static org.assertj.core.api.Assertions.*;

class KitValuesTest {
    private Text text(String id) { return new Text(id, ControlState.READY, "Text", TextRole.BODY); }
    private static List<String> names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(constant -> constant.name()).toList();
    }
    /** Spec 8.1: the constants SDK 0.18 renames; every other constant keeps its name and place. */
    @Test void zeroEighteenNamesTheExclusiveHelpAndPromptConstants() {
        assertThat(names(ChoiceKind.class)).containsExactly("DROPDOWN", "PICKER", "EXCLUSIVE", "SEARCHABLE", "PREVIEWS", "TABS");
        assertThat(names(ButtonPresentation.class)).containsExactly("STANDARD", "EMBEDDED", "CONTEXTUAL_EMBEDDED", "HELP");
        assertThat(names(FormKind.class)).containsExactly("PROMPT", "PROMPT_COMPACT", "SHEET", "FULL", "SETTINGS", "WINDOW");
        assertThat(names(LayoutRole.class)).startsWith("VERTICAL", "INLINE", "ACTIONS", "PROMPT_HEADER", "DETAIL");
        assertThat(names(TextRole.class)).startsWith("PROMPT_TITLE", "DETAIL_TITLE").contains("MONO", "MONO_SMALL", "MONO_CAPTION");
        assertThat(names(IconSize.class)).containsExactly("CAPTION", "CONTROL", "RAIL", "EMPTY", "PROMPT", "SWATCH");
        assertThat(names(ButtonKind.class)).contains("GHOST", "DESTRUCTIVE_GHOST");
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
    /** Plan 3b: a rich face's caption is plain text unless it says it is a data value. */
    @Test void pickerFacesDefaultToAPlainCaption() {
        assertThat(new PickerFace("Mac", "dustin@host:22", "server", Hue.BLUE, PickerDensity.DEFAULT).technicalCaption()).isFalse();
        assertThat(new PickerFace("Mac", "dustin@host:22", "server", Hue.BLUE, PickerDensity.DEFAULT, true).technicalCaption()).isTrue();
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
    /** Plan 3b (spec 8.2): properties hold one or more labelled values, their labels without a closing colon. */
    @Test void propertiesNeedRowsAndLabelsWithoutAColon() {
        var host = new PropertyRow("Host", "192.168.1.124", true);
        assertThat(host.severity()).isEmpty();
        assertThat(new Nodes.Properties("properties", ControlState.READY, "", List.of(host)).title()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new Nodes.Properties("properties", ControlState.READY, "Properties", List.of()))
            .withMessageContaining("at least one row");
        assertThatIllegalArgumentException().isThrownBy(() -> new PropertyRow(" ", "x", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new PropertyRow("Host:", "x", false)).withMessageContaining("colon");
        assertThat(new PropertyRow("Time (UTC:local)", "12:30", false).label()).isEqualTo("Time (UTC:local)");
        assertThatNullPointerException().isThrownBy(() -> new PropertyRow("Host", null, false));
        var properties = new Nodes.Properties("properties", ControlState.READY, "Properties — minecraft-local", List.of(host,
            new PropertyRow("Credentials", "Vault locked", false, Optional.of(Severity.WARNING))));
        assertThatCode(() -> new ViewSpec(new Nodes.Stack("detail", ControlState.READY, LayoutRole.VERTICAL, List.of(properties)))).doesNotThrowAnyException();
        assertThatIllegalArgumentException().isThrownBy(() -> new ListItem("row", Optional.empty(), List.of(properties), List.of(), List.of()))
            .withMessageContaining("Unsupported inline row node: Properties");
    }
    /** Plan 3b (spec 8.2): a footnote is one line beside the actions, and it shares the footer's leading edge with no preview. */
    @Test void footnotesAreOneLineAndExcludeAPreview() {
        var unlock = new Button("unlock", ControlState.READY, "Unlock", "", ButtonKind.PRIMARY, "unlock", "");
        assertThat(new Form("form", ControlState.READY, FormKind.PROMPT_COMPACT, "Unlock", Optional.empty(), List.of(text("body")), Optional.empty(), List.of(unlock))
            .footnote()).isEmpty();
        var noted = new Form("form", ControlState.READY, FormKind.PROMPT_COMPACT, "Unlock", Optional.empty(), List.of(text("body")), Optional.empty(), List.of(unlock),
            "cancel", FormSubmission.PRIMARY, "Auto-locks after 15 min idle");
        assertThat(noted.footnote()).isEqualTo("Auto-locks after 15 min idle");
        assertThatIllegalArgumentException().isThrownBy(() -> new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(text("body")),
            Optional.of(text("preview")), List.of(unlock), "cancel", FormSubmission.PRIMARY, "Saved")).withMessageContaining("preview");
        for (String broken : List.of("One" + (char) 10 + "Two", "One" + (char) 13 + "Two"))
            assertThatIllegalArgumentException().isThrownBy(() -> new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(text("body")),
                Optional.empty(), List.of(unlock), "cancel", FormSubmission.PRIMARY, broken)).withMessageContaining("one line");
        assertThat(new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(text("body")), Optional.of(text("preview")),
            List.of(unlock), "cancel", FormSubmission.PRIMARY, " ").footnote()).isBlank();
    }
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
        assertThat(new Button("b", ControlState.READY, "", "star", ButtonKind.GHOST, "fav", "Favourite", ButtonPresentation.EMBEDDED).iconSeverity()).isEmpty();
        assertThat(new Button("b", ControlState.READY, "", "star.filled", ButtonKind.GHOST, "fav", "Favourite", ButtonPresentation.EMBEDDED, Optional.of(Severity.WARNING)).iconSeverity())
            .hasValue(Severity.WARNING);
        assertThatThrownBy(() -> new Button("b", ControlState.READY, "", "star", ButtonKind.GHOST, "fav", "Favourite", ButtonPresentation.EMBEDDED, null)).isInstanceOf(NullPointerException.class);
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
        assertThat(new Column("name", "Name", TextRole.BODY, false).width()).isEmpty();
        assertThat(new Column("name", "Name", TextRole.BODY, false, 1, Optional.empty(), OptionalInt.of(180)).width()).hasValue(180);
        assertThatThrownBy(() -> new Column("name", "Name", TextRole.BODY, false, 1, Optional.empty(), OptionalInt.of(0))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new UiEvent.ColumnsResized("files", Map.of("name", 180)).widths()).containsEntry("name", 180);
        assertThatThrownBy(() -> new UiEvent.ColumnsResized("files", Map.of("name", 0))).isInstanceOf(IllegalArgumentException.class);
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

    /** Plan 2 Task 9-kit: the Editor's text caret and severity, field keys and list, stack Escape and toggle buttons, with their defaults. */
    @Test void editorCapabilitiesAreValidatedAndDefaultToNone() {
        var plain = new Text("t", ControlState.READY, "abc", TextRole.MONO);
        assertThat(plain.severity()).isEmpty(); assertThat(plain.caret()).isEmpty();
        assertThat(new Text("t", ControlState.READY, "abc", TextRole.MONO, Optional.of(Severity.DANGER), OptionalInt.of(3)).caret()).hasValue(3);
        assertThat(new Text("t", ControlState.READY, "", TextRole.MONO_SMALL, Optional.empty(), OptionalInt.of(0)).caret()).hasValue(0);
        assertThatThrownBy(() -> new Text("t", ControlState.READY, "abc", TextRole.BODY, Optional.empty(), OptionalInt.of(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Text("t", ControlState.READY, "abc", TextRole.MONO, Optional.empty(), OptionalInt.of(4))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Text("t", ControlState.READY, "abc", TextRole.MONO, Optional.empty(), OptionalInt.of(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Text("t", ControlState.READY, "abc", TextRole.MONO, null, OptionalInt.empty())).isInstanceOf(NullPointerException.class);

        var field = new Field("f", ControlState.READY, FieldKind.SEARCH, "", "Find", "Find", "next");
        assertThat(List.of(field.alternateAction(), field.cancelAction(), field.listId())).containsOnly("");
        var find = new Field("f", ControlState.READY, FieldKind.SEARCH, "", "Find", "Find", "next", Optional.empty(), FieldPresentation.EMBEDDED, "previous", "close", "");
        assertThat(find.alternateAction()).isEqualTo("previous"); assertThat(find.cancelAction()).isEqualTo("close");
        assertThatThrownBy(() -> new Field("f", ControlState.READY, FieldKind.TEXTAREA, "", "", "Notes", "", Optional.empty(), FieldPresentation.STANDARD, "x", "", ""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Field("f", ControlState.READY, FieldKind.TEXTAREA, "", "", "Notes", "", Optional.empty(), FieldPresentation.STANDARD, "", "", "list"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Field("f", ControlState.READY, FieldKind.TEXTAREA, "", "", "Notes", "", Optional.empty(), FieldPresentation.STANDARD, "", "close", "").cancelAction()).isEqualTo("close");
        assertThatThrownBy(() -> new Field("f", ControlState.READY, FieldKind.NUMBER, "1", "", "Size", "", Optional.of(new Numeric(0, 9, 1, 0)), FieldPresentation.STANDARD, "", "", "list"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Field("f", ControlState.READY, FieldKind.TEXT, "", "", "Name", "", Optional.empty(), FieldPresentation.STANDARD, null, "", "")).isInstanceOf(NullPointerException.class);
        var filter = new Field("filter", ControlState.READY, FieldKind.SEARCH, "", "Filter", "Filter", "choose", Optional.empty(), FieldPresentation.STANDARD, "", "", "languages");
        var languages = new ItemList("languages", ControlState.READY, ListKind.SINGLE, List.of(new ListItem("java", Optional.empty(), List.of(text("java.name")), List.of(), List.of())), Set.of(), "choose", List.of());
        assertThat(new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(filter, languages))).root()).isNotNull();
        assertThatThrownBy(() -> new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(filter))))
            .as("a field's list must be in the view").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(filter, text("languages")))))
            .as("and must be an item list").isInstanceOf(IllegalArgumentException.class);

        assertThat(new Nodes.Stack("s", ControlState.READY, LayoutRole.INLINE, List.of()).cancelAction()).isEmpty();
        assertThat(new Nodes.Stack("s", ControlState.READY, LayoutRole.INLINE, List.of(), "close").cancelAction()).isEqualTo("close");
        assertThatThrownBy(() -> new Nodes.Stack("s", ControlState.READY, LayoutRole.INLINE, List.of(), null)).isInstanceOf(NullPointerException.class);

        assertThat(new Button("b", ControlState.READY, "Aa", "", ButtonKind.GHOST, "case", "Match case").selected()).isEmpty();
        assertThat(new Button("b", ControlState.READY, "Aa", "", ButtonKind.GHOST, "case", "Match case", ButtonPresentation.STANDARD, Optional.empty(), Optional.of(true)).selected()).hasValue(true);
        assertThat(new Button("b", ControlState.READY, "Aa", "", ButtonKind.SECONDARY, "case", "Match case", ButtonPresentation.STANDARD, Optional.empty(), Optional.of(false)).selected()).hasValue(false);
        assertThatThrownBy(() -> new Button("b", ControlState.READY, "Go", "", ButtonKind.PRIMARY, "go", "", ButtonPresentation.STANDARD, Optional.empty(), Optional.of(true)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Button("b", ControlState.READY, "Go", "", ButtonKind.GHOST, "go", "", ButtonPresentation.STANDARD, Optional.empty(), null)).isInstanceOf(NullPointerException.class);
    }

    /** Plan 2 Task 9-kit fix round 1 (M4): a field's list is checked wherever either sits, in the same one traversal. */
    @Test void aFieldsListIsResolvedAcrossEveryContainer() {
        var filter = new Field("filter", ControlState.READY, FieldKind.SEARCH, "", "Filter", "Filter", "choose", Optional.empty(), FieldPresentation.STANDARD, "", "", "languages");
        var languages = new ItemList("languages", ControlState.READY, ListKind.SINGLE, List.of(new ListItem("java", Optional.empty(), List.of(text("java.name")), List.of(), List.of())), Set.of(), "choose", List.of());
        var header = new Header("header", ControlState.READY, "Languages", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        var inForm = new Form("form", ControlState.READY, FormKind.SHEET, "Language", Optional.empty(),
            List.of(new Section("section", ControlState.READY, "Filter", new Card("card", ControlState.READY, CardKind.FORM, List.of(new FormRow("row", ControlState.READY, "Filter", "", filter, Optional.empty()))))),
            Optional.of(new Panel("panel", ControlState.READY, header, Optional.empty(), new Split("split", ControlState.READY, SplitKind.DOCUMENT, List.of(text("a"), languages)), Optional.empty())),
            List.of(), "cancel");
        assertThat(new ViewSpec(inForm).root()).isSameAs(inForm);
        var dangling = new Form("form", ControlState.READY, FormKind.SHEET, "Language", Optional.empty(),
            List.of(new Section("section", ControlState.READY, "Filter", new SettingRow("row", ControlState.READY, "Filter", "", filter, false))), Optional.empty(), List.of(), "cancel");
        assertThatThrownBy(() -> new ViewSpec(dangling)).as("no list anywhere").isInstanceOf(IllegalArgumentException.class);
        var inEmpty = new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(
            new Empty("empty", ControlState.READY, text("icon"), "Nothing", "", List.of(new Panel("p", ControlState.READY, header, Optional.of(filter), languages, Optional.empty())))));
        assertThat(new ViewSpec(inEmpty).root()).isSameAs(inEmpty);
    }

    @Test void aMenuItemNamesItsShortcutSourceOrNone() {
        var plain = new MenuItem("open", "Open", "", "open", true, false);
        assertThat(plain.shortcut()).isEmpty();
        assertThat(plain.withShortcut("dev.jasper.sample.open").shortcut()).isEqualTo("dev.jasper.sample.open");
        assertThat(new MenuItem("open", "Open", "", "open", true, false, Hue.BLUE).shortcut()).isEmpty();
        assertThatThrownBy(() -> plain.withShortcut(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatNullPointerException().isThrownBy(() -> new MenuItem("open", "Open", "", "open", true, false, Hue.BLUE, null));
    }
    private static TableRow cellRow(Node node) {
        return new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "", Map.of("col", node), Optional.empty());
    }
    private static Button emptyAction(String id) {
        return new Button(id, ControlState.READY, "New Tunnel…", "", ButtonKind.SECONDARY, id, "");
    }
    /** Plan 3c (spec 8.2): the new components default to nothing through every existing constructor. */
    @Test void zeroEighteenTableFieldsDefaultToNothing() {
        var row = new TableRow("r", Map.of("name", "a"), Optional.empty());
        assertThat(row.cellNodes()).isEmpty();
        assertThat(row.heading()).isEmpty();
        assertThat(new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "tip").cellNodes()).isEmpty();
        var table = new Table("t", ControlState.READY, List.of(), List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD);
        assertThat(table.empty()).isEmpty();
        assertThat(table.emptyText()).isEmpty();
        assertThat(new Tree("tree", ControlState.READY, List.of(), Set.of(), Set.of(), "").columns()).isEmpty();
        var list = new ItemList("l", ControlState.READY, ListKind.SINGLE, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty(),
            OptionalInt.empty(), "", "", "", "", "");
        assertThat(list.emptyText()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> new ItemList("l", ControlState.READY, ListKind.SINGLE, List.of(), Set.of(), "", List.of(),
            SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "", "", "", "", "", null));
    }
    /** Plan 3c (spec 8.2): a cell node is presentation or a DROPDOWN, in a column of its own, and a view node. */
    @Test void cellNodesArePresentationOrADropdownInAColumnOfTheirOwn() {
        var dot = new StatusDot("d", ControlState.READY, StatusKind.SUCCESS);
        var options = List.of(new Option("card", "Card", Optional.empty()), new Option("sticky", "Sticky card", Optional.empty()));
        List<Node> accepted = List.of(text("t"), dot, new Icon("i", ControlState.READY, "server", Hue.NONE, IconSize.CAPTION, "Server"),
            new Chip("c", ControlState.READY, "HTTP", Hue.BLUE), new Nodes.Stack("s", ControlState.READY, LayoutRole.INLINE, List.of(dot, text("word"))),
            new Choice("choice", ControlState.READY, ChoiceKind.DROPDOWN, options, "card", "Display", ""));
        for (var node : accepted) assertThat(cellRow(node).cellNodes()).containsEntry("col", node);
        List<Node> refused = List.of(new Button("b", ControlState.READY, "Go", "", ButtonKind.SECONDARY, "go", ""),
            new Nodes.Stack("v", ControlState.READY, LayoutRole.VERTICAL, List.of(text("x"))),
            new Nodes.Stack("deep", ControlState.READY, LayoutRole.INLINE, List.of(new Nodes.Stack("inner", ControlState.READY, LayoutRole.INLINE, List.of(text("y"))))),
            new Nodes.Stack("act", ControlState.READY, LayoutRole.INLINE, List.of(new Button("b2", ControlState.READY, "Go", "", ButtonKind.SECONDARY, "go", ""))),
            new Choice("picker", ControlState.READY, ChoiceKind.PICKER, List.of(), "", "Pick", "pick"),
            new Choice("rich", ControlState.READY, ChoiceKind.DROPDOWN, options, "card", "Display", "", "", Optional.empty(),
                Optional.of(new PickerFace("Card", "", "", Hue.NONE, PickerDensity.DEFAULT))),
            new Toggle("on", ControlState.READY, "On", true, "on", true));
        for (var node : refused) assertThatIllegalArgumentException().as(node.id()).isThrownBy(() -> cellRow(node));
        assertThatIllegalArgumentException().as("one column, two contents").isThrownBy(() ->
            new TableRow("r", Map.of("col", "text"), Optional.empty(), false, Optional.empty(), "", Map.of("col", text("t")), Optional.empty()));
        var columns = List.of(new Column("col", "Column", TextRole.BODY, false));
        assertThatIllegalArgumentException().as("unknown column").isThrownBy(() -> new ViewSpec(new Table("t", ControlState.READY, columns,
            List.of(new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "", Map.of("other", text("t")), Optional.empty())), Set.of(), "", List.of())));
        assertThatIllegalArgumentException().as("cell node ids are view ids").isThrownBy(() -> new ViewSpec(new Nodes.Stack("root", ControlState.READY,
            LayoutRole.VERTICAL, List.of(text("same"), new Table("t", ControlState.READY, columns, List.of(cellRow(text("same"))), Set.of(), "", List.of())))));
        assertThatCode(() -> new ViewSpec(new Table("t", ControlState.READY, columns, List.of(cellRow(text("cell"))), Set.of(), "", List.of())))
            .doesNotThrowAnyException();
        var many = new LinkedHashMap<String, Node>();
        for (int i = 11; i >= 0; i--) many.put("c" + (char) ('a' + i), text("t" + i));
        var row = new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "", many, Optional.empty());
        assertThat(row.cellNodes().keySet()).as("column-id order")
            .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(0, 11).mapToObj(i -> "c" + (char) ('a' + i)).toList());
        assertThat(row.cellNodes()).isEqualTo(Map.copyOf(many));
        assertThatThrownBy(() -> row.cellNodes().put("x", text("x"))).isInstanceOf(UnsupportedOperationException.class);
    }
    /** Plan 3c (spec 8.2): a heading row is a title and a count, and a table never selects it. */
    @Test void headingRowsCarryATitleAndACountAndAreNeverSelected() {
        var heading = TableRow.heading("jasper", "Jasper", OptionalInt.of(6), "group");
        assertThat(heading.heading()).hasValue(new TableRow.Heading("Jasper", OptionalInt.of(6), "group"));
        assertThat(heading.cells()).isEmpty();
        assertThat(heading.cellNodes()).isEmpty();
        assertThat(TableRow.heading("bare", "Bare", OptionalInt.of(4), "").heading().orElseThrow().countNoun()).isEmpty();
        assertThat(TableRow.heading("none", "None", OptionalInt.empty(), "").heading().orElseThrow().count()).isEmpty();
        assertThat(TableRow.heading("two", "Two", OptionalInt.of(2), "file server").heading().orElseThrow().countNoun()).isEqualTo("file server");
        assertThatIllegalArgumentException().as("blank title").isThrownBy(() -> TableRow.heading("h", " ", OptionalInt.empty(), ""));
        assertThatIllegalArgumentException().as("negative count").isThrownBy(() -> TableRow.heading("h", "H", OptionalInt.of(-1), ""));
        assertThatIllegalArgumentException().as("noun without count").isThrownBy(() -> TableRow.heading("h", "H", OptionalInt.empty(), "group"));
        assertThatIllegalArgumentException().as("capitalised noun").isThrownBy(() -> TableRow.heading("h", "H", OptionalInt.of(2), "Group"));
        assertThatIllegalArgumentException().as("noun with spaces around it").isThrownBy(() -> TableRow.heading("h", "H", OptionalInt.of(2), " group"));
        var title = Optional.of(new TableRow.Heading("H", OptionalInt.empty(), ""));
        assertThatIllegalArgumentException().as("cells").isThrownBy(() -> new TableRow("h", Map.of("c", "x"), Optional.empty(), false, Optional.empty(), "", Map.of(), title));
        assertThatIllegalArgumentException().as("cell nodes").isThrownBy(() -> new TableRow("h", Map.of(), Optional.empty(), false, Optional.empty(), "", Map.of("c", text("x")), title));
        assertThatIllegalArgumentException().as("icon").isThrownBy(() -> new TableRow("h", Map.of(), Optional.of("file"), false, Optional.empty(), "", Map.of(), title));
        assertThatIllegalArgumentException().as("muted").isThrownBy(() -> new TableRow("h", Map.of(), Optional.empty(), true, Optional.empty(), "", Map.of(), title));
        assertThatIllegalArgumentException().as("menu").isThrownBy(() -> new TableRow("h", Map.of(), Optional.empty(), false, Optional.of(List.of()), "", Map.of(), title));
        assertThatIllegalArgumentException().as("tooltip").isThrownBy(() -> new TableRow("h", Map.of(), Optional.empty(), false, Optional.empty(), "Tip", Map.of(), title));
        var columns = List.of(new Column("c", "C", TextRole.BODY, false));
        assertThatIllegalArgumentException().as("selected heading").isThrownBy(() -> new Table("t", ControlState.READY, columns, List.of(heading), Set.of("jasper"), "", List.of()));
        assertThatCode(() -> new ViewSpec(new Table("t", ControlState.READY, columns, List.of(heading, new TableRow("r", Map.of("c", "x"), Optional.empty())),
            Set.of("r"), "", List.of()))).doesNotThrowAnyException();
    }
    /** Plan 3c (spec 8.2): an empty table shows a state with at most one action or a line, not both. */
    @Test void emptyStatesAreExclusiveAndATablesStateHasOneAction() {
        var columns = List.of(new Column("c", "C", TextRole.BODY, false));
        var icon = new Icon("e.icon", ControlState.READY, "server", Hue.NEUTRAL, IconSize.EMPTY, "Servers");
        var one = new Empty("e", ControlState.READY, icon, "No Tunnels", "", List.of(emptyAction("new")));
        var table = new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(one), "");
        assertThat(table.empty()).hasValue(one);
        assertThat(new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD,
            Optional.empty(), "Not Connected").emptyText()).isEqualTo("Not Connected");
        assertThatIllegalArgumentException().as("state and line").isThrownBy(() -> new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(),
            SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(one), "Not Connected"));
        assertThatCode(() -> new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD,
            Optional.of(one), " ")).as("a blank line is no line").doesNotThrowAnyException();
        var two = new Empty("e", ControlState.READY, icon, "No Tunnels", "", List.of(emptyAction("a"), emptyAction("b")));
        assertThatIllegalArgumentException().as("two actions").isThrownBy(() -> new Table("t", ControlState.READY, columns, List.of(), Set.of(), "", List.of(),
            SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(two), ""));
        assertThatIllegalArgumentException().as("the state's ids are view ids").isThrownBy(() ->
            new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(text("e"), table))));
        assertThatCode(() -> new ViewSpec(table)).doesNotThrowAnyException();
        var list = new ItemList("l", ControlState.READY, ListKind.ORDERED, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty(),
            OptionalInt.empty(), "", "", "", "", "", "No keys");
        assertThat(new ViewSpec(list).root()).isEqualTo(list);
    }
    /** Plan 3c (spec 8.2): tree columns title the items and their trailing values. */
    @Test void treeColumnsTitleTheItemsAndTheirTrailingValues() {
        var columns = List.of(new Column("name", "Name", TextRole.BODY, false), new Column("host", "Host", TextRole.MONO_SMALL, false),
            new Column("port", "Port", TextRole.MONO_SMALL, true));
        var mac = new TreeItem("mac", "Mac", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(), List.of("192.168.1.119", "22"), Optional.empty());
        var tree = new Tree("sessions", ControlState.READY, List.of(new TreeItem("lan", "Local Network", Optional.empty(), false, List.of(mac))),
            Set.of("lan"), Set.of("mac"), "connect", columns);
        assertThat(new ViewSpec(tree).root()).isEqualTo(tree);
        var extra = new TreeItem("extra", "Extra", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(), List.of("a", "b", "c"), Optional.empty());
        assertThatIllegalArgumentException().as("a nested item with three values under two value columns").isThrownBy(() -> new ViewSpec(new Tree("t",
            ControlState.READY, List.of(new TreeItem("b", "B", Optional.empty(), false, List.of(extra))), Set.of(), Set.of(), "", columns)));
        assertThatCode(() -> new ViewSpec(new Tree("t", ControlState.READY, List.of(extra), Set.of(), Set.of(), "")))
            .as("without columns, values are free").doesNotThrowAnyException();
        assertThatCode(() -> new ViewSpec(new Tree("t", ControlState.READY, List.of(new TreeItem("few", "Few", Optional.empty(), true, List.of())),
            Set.of(), Set.of(), "", columns))).as("fewer values than columns").doesNotThrowAnyException();
        assertThatIllegalArgumentException().as("duplicate column").isThrownBy(() -> new Tree("t", ControlState.READY, List.of(), Set.of(), Set.of(), "",
            List.of(columns.get(0), columns.get(0))));
    }
}
