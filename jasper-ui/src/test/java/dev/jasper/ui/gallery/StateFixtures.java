package dev.jasper.ui.gallery;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import java.util.*;
import dev.jasper.ui.model.Nodes.Stack;
import static dev.jasper.ui.gallery.FormFixtures.*;

/** Every vocabulary variant plus explicit headless interaction and domain presentation states. */
public final class StateFixtures {
    private StateFixtures() {}
    static KitGallery.Fixture sheet(String name, Node root, int width, int height) {
        return new KitGallery.Fixture(name, "", "normal", width, height, new ViewSpec(root));
    }
    public static List<KitGallery.Fixture> create() {
        var result = new ArrayList<KitGallery.Fixture>();
        var texts = Arrays.stream(TextRole.values()).map(r -> (Node)text("text-" + r, r + " · Jasper", r)).toList();
        result.add(sheet("typography", new Stack("typography", READY, LayoutRole.VERTICAL, texts), 480, 460));
        var icons = new ArrayList<Node>();
        for (Hue hue : Hue.values()) for (IconSize size : IconSize.values()) icons.add(icon("icon-" + hue + "-" + size, "key", hue, size));
        var iconRows = new ArrayList<Node>();
        for (int i = 0; i < icons.size(); i += 5) iconRows.add(new Stack("icons-" + i, READY, LayoutRole.INLINE, icons.subList(i, i + 5)));
        for (StatusKind kind : StatusKind.values()) iconRows.add(stack("status-" + kind, LayoutRole.INLINE, new StatusDot("dot-" + kind, READY, kind), text("status-title-" + kind, kind.name(), TextRole.BODY)));
        result.add(sheet("icons-status", new Stack("icons", READY, LayoutRole.VERTICAL, iconRows), 480, 440));
        var controls = new ArrayList<Node>();
        for (ButtonKind kind : ButtonKind.values()) controls.add(button("button-" + kind, kind.name(), kind));
        for (FieldKind kind : FieldKind.values()) controls.add(field("field-" + kind, "", kind.name(), kind));
        controls.add(new Secret("secret", READY, "Password", "Password", ""));
        for (ChoiceKind kind : ChoiceKind.values()) controls.add(choice("choice-" + kind, kind, "Selected", "Selected", "Other"));
        controls.add(new Choice("choice-free-entry", READY, ChoiceKind.SEARCHABLE, List.of(new Option("menlo", "Menlo", Optional.empty()), new Option("sf-mono", "SF Mono", Optional.empty())),
            "", "Font family", "", "", Optional.of(new ChoiceSearch(0, "No installed font matches", Optional.of("Iosevka")))));
        controls.add(new Toggle("switch", READY, "Switch", true, "switch", false));
        controls.add(new Chip("chip", READY, "HTTP", Hue.BLUE));
        controls.add(new Progress("progress", READY, .65, "Progress"));
        controls.add(new Progress("indeterminate", READY, -1, "Working"));
        controls.add(new Strength("strength", READY, 3, "Strong"));
        result.add(sheet("controls", new Stack("controls", READY, LayoutRole.VERTICAL, controls), 480, 1000));
        var layouts = new ArrayList<Node>();
        for (LayoutRole role : LayoutRole.values()) layouts.add(stack("stack-" + role, role, text("layout-label-" + role, role.name(), TextRole.BODY)));
        for (CardKind kind : CardKind.values()) layouts.add(card("card-" + kind, kind, setting("setting-" + kind, kind.name(), "Description", toggle("toggle-" + kind, true))));
        layouts.add(card("card-highlighted", CardKind.SETTINGS, new SettingRow("setting-highlighted", READY, "Matches search", "A row the search highlights", toggle("toggle-highlighted", true), false, true)));
        result.add(sheet("composition", new Stack("composition", READY, LayoutRole.VERTICAL, layouts), 600, 700));
        var data = new ArrayList<Node>();
        for (ListKind kind : ListKind.values()) data.add(new ItemList("list-" + kind, READY, kind, List.of(new ListItem("one", Optional.empty(), List.of(text("list-title-" + kind, kind.name(), TextRole.BODY)), List.of(text("list-subtitle-" + kind, "Secondary text", TextRole.DESCRIPTION)), List.of())), Set.of("one"), "activate", List.of(), kind == ListKind.COMPACT ? SelectionMode.MULTIPLE : SelectionMode.SINGLE, Optional.empty()));
        data.add(new Table("table", READY, List.of(new Column("name", "Name", TextRole.BODY, false), new Column("bytes", "Size", TextRole.MONO_SMALL, true)), List.of(new TableRow("one", Map.of("name", "Documents", "bytes", "2048"), Optional.of("folder"))), Set.of("one"), "open", List.of(), SelectionMode.MULTIPLE));
        data.add(new Tree("tree", READY, List.of(new TreeItem("root", "Home", Optional.of("folder"), false, List.of(new TreeItem("child", "Documents", Optional.of("folder"), true, List.of()),
            new TreeItem("matched", "Downloads", Optional.of("folder"), true, List.of(), OptionalInt.of(3), true)))), Set.of("root"), Set.of("child"), "open"));
        result.add(sheet("data", new Stack("data", READY, LayoutRole.VERTICAL, data), 600, 760));
        for (SplitKind kind : SplitKind.values()) result.add(sheet("split-" + kind, new Split("split", READY, kind, List.of(text("left", "Sidebar", TextRole.BODY), text("right", "Detail", TextRole.BODY))), 600, 100));
        for (FormKind kind : FormKind.values()) result.add(sheet("form-" + kind, new Form("form", READY, kind, kind.name(), Optional.empty(), List.of(row("row", "Label", field("input", "", "Value", FieldKind.TEXT))), Optional.empty(), List.of(button("save", "Save", ButtonKind.PRIMARY)), "cancel"), 480, 200));
        for (CustomKind kind : CustomKind.values()) result.add(sheet("custom-" + kind, new Custom("custom", READY, "preview", kind, kind.name()), 480, 180));
        for (String state : List.of("normal", "hover", "focus", "pressed", "selected", "disabled", "error", "busy", "conditional", "motion-disabled")) {
            ControlState cs = switch (state) {
                case "disabled" -> DISABLED;
                case "busy" -> new ControlState(true, true, true, "");
                case "error" -> new ControlState(true, true, false, "A value is required");
                default -> READY;
            };
            var nodes = new ArrayList<Node>(List.of(new Button("state-button", cs, "Continue", "", ButtonKind.PRIMARY, "continue", ""), new Field("state-field", cs, FieldKind.TEXT, "", "Value", "Value", ""), new Toggle("state-toggle", cs, "Confirmed switch", state.equals("selected"), "toggle", false)));
            nodes.add(new Field("conditional-field", new ControlState(state.equals("conditional"), true, false, ""), FieldKind.TEXT, "", "Session password hint", "Conditional value", ""));
            result.add(new KitGallery.Fixture("state-" + state, "", state, 400, 210, new ViewSpec(new Stack("state", READY, LayoutRole.VERTICAL, nodes))));
        }
        return List.copyOf(result);
    }
}
