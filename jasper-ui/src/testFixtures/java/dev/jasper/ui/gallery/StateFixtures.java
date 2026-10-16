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
        // SWATCH has its own fixture (KitGallery's "swatches"), so this sheet keeps its pixels.
        for (Hue hue : Hue.values()) for (IconSize size : IconSize.values()) if (size != IconSize.SWATCH) icons.add(icon("icon-" + hue + "-" + size, "key", hue, size));
        var iconRows = new ArrayList<Node>();
        for (int i = 0; i < icons.size(); i += 5) iconRows.add(new Stack("icons-" + i, READY, LayoutRole.INLINE, icons.subList(i, i + 5)));
        for (StatusKind kind : StatusKind.values()) iconRows.add(stack("status-" + kind, LayoutRole.INLINE, new StatusDot("dot-" + kind, READY, kind), text("status-title-" + kind, kind.name(), TextRole.BODY)));
        result.add(sheet("icons-status", new Stack("icons", READY, LayoutRole.VERTICAL, iconRows), 480, 440));
        var controls = new ArrayList<Node>();
        for (ButtonKind kind : ButtonKind.values()) controls.add(button("button-" + kind, kind.name(), kind));
        for (FieldKind kind : FieldKind.values()) controls.add(field("field-" + kind, "", kind.name(), kind));
        controls.add(new Secret("secret", READY, "Password", "Password", ""));
        // TABS has its own fixture (KitGallery's "tabs"), so this sheet keeps its pixels.
        for (ChoiceKind kind : ChoiceKind.values()) if (kind != ChoiceKind.TABS) controls.add(choice("choice-" + kind, kind, "Selected", "Selected", "Other"));
        controls.add(new Choice("choice-free-entry", READY, ChoiceKind.SEARCHABLE, List.of(new Option("menlo", "Menlo", Optional.empty()), new Option("sf-mono", "SF Mono", Optional.empty())),
            "", "Font family", "", "", Optional.of(new ChoiceSearch(0, "No installed font matches", Optional.of("Iosevka")))));
        controls.add(new Toggle("switch", READY, "Switch", true, "switch", false));
        controls.add(new Chip("chip", READY, "HTTP", Hue.BLUE));
        for (FieldPresentation presentation : FieldPresentation.values())
            controls.add(new Field("field-" + presentation, READY, presentation == FieldPresentation.PLAIN_PATH ? FieldKind.PATH : presentation == FieldPresentation.STEPPER || presentation == FieldPresentation.SLIDER ? FieldKind.NUMBER : FieldKind.TEXT, "42", "", "Presentation " + presentation, "", presentation == FieldPresentation.STEPPER || presentation == FieldPresentation.SLIDER ? Optional.of(new Numeric(0,100,1,0)) : Optional.empty(), presentation));
        controls.add(new Nodes.Button("circle-help",READY,"","question",ButtonKind.GHOST,"help","Help",ButtonPresentation.HELP));
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
        var pin = new dev.jasper.ui.model.MenuItem("pin", "Pin", "bookmark", "pin", true, false);
        var browser = new ArrayList<Node>();
        browser.add(new Breadcrumbs("browser-path", READY, List.of(new Crumb("home", "Home", List.of()), new Crumb("projects", "projects", List.of(pin)),
            new Crumb("jasper", "jasper", List.of(pin))), "Location", false, "", "edit-path", "go", "done"));
        browser.add(new Tree("browser-places", READY, List.of(new TreeItem("home", "Home", Optional.of("home"), true, List.of(), OptionalInt.empty(), false, "/Users/me", Optional.empty(), List.of(), Optional.empty()),
            new TreeItem("remote", "Remote", Optional.empty(), false, List.of(new TreeItem("box", "Box", Optional.of("server"), true, List.of(), OptionalInt.empty(), false,
                "me@box:22", Optional.of(StatusKind.SUCCESS), List.of("SSH", "connected"), Optional.of(List.of(pin)))), OptionalInt.of(1), false, "", Optional.empty(), List.of(), Optional.empty())),
            Set.of("remote"), Set.of("home"), "open"));
        browser.add(new Table("browser-files", READY, List.of(new Column("name", "Name", TextRole.BODY, false, 4.5, Optional.of(true)),
                new Column("modified", "Modified", TextRole.DESCRIPTION, false, 1.75, Optional.empty()), new Column("size", "Size", TextRole.DESCRIPTION, true, 1, Optional.empty())),
            List.of(new TableRow("docs", Map.of("name", "docs", "modified", "2026-10-05 09:12", "size", ""), Optional.of("folder"), false, Optional.of(List.of(pin))),
                new TableRow("notes", Map.of("name", "notes.txt", "modified", "2026-10-04 17:40", "size", "6 B"), Optional.of("file"), true, Optional.of(List.of()))),
            Set.of("docs"), "open", List.of(), SelectionMode.SINGLE, ScrollPolicy.OVERLAY));
        browser.add(new Table("browser-standard", READY, List.of(new Column("name", "Name", TextRole.BODY, false)),
            List.of(new TableRow("one", Map.of("name", "Standard scroll"), Optional.empty())), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD));
        result.add(sheet("browser", new Stack("browser", READY, LayoutRole.VERTICAL, browser), 640, 560));
        // A command palette: a search field over a RESULTS list with headings, one-line rows and five visible rows.
        var results = new ArrayList<ListItem>();
        results.add(new ListItem("results-commands", Optional.empty(), List.of(text("results-commands-title", "Commands", TextRole.CAPTION)), List.of(), List.of(), true));
        for (String name : List.of("New Tab", "Split Right", "Close Pane")) {
            String id = "results-" + name.toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
            results.add(new ListItem(id, Optional.of(new Icon(id + "-icon", READY, "play", Hue.NONE, IconSize.RAIL, "Command")),
                List.of(text(id + "-title", name, TextRole.BODY)), List.of(text(id + "-detail", "Panes", TextRole.DESCRIPTION)), List.of(text(id + "-tag", "Cmd+T", TextRole.CAPTION))));
        }
        results.add(new ListItem("results-history", Optional.empty(), List.of(text("results-history-title", "History", TextRole.CAPTION)), List.of(), List.of(), true));
        results.add(new ListItem("results-ls", Optional.empty(), List.of(text("results-ls-title", "ls -la", TextRole.MONO)), List.of(), List.of()));
        result.add(sheet("results", new Stack("results", READY, LayoutRole.VERTICAL, List.of(
            new Field("results-query", READY, FieldKind.SEARCH, "", "Search everything", "Search all", ""),
            new ItemList("results-list", READY, ListKind.RESULTS, results, Set.of("results-new-tab"), "run", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(5)))), 680, 240));
        // Plan 2 Task 6c: plugin rows with their own menus and switch tooltips, severity glyphs and flowing body text.
        var notices = new ArrayList<Node>();
        for (Severity severity : Severity.values())
            notices.add(new Icon("notice-" + severity, READY, switch (severity) { case INFO -> "info"; case WARNING -> "warning"; case DANGER -> "error"; },
                Hue.NONE, IconSize.RAIL, severity.name(), Optional.of(severity)));
        notices.add(text("notice-body", "Fixture plugin 1.0\nFrom Fixture vendor\n\nA plugin is unrestricted code running inside Jasper, "
            + "with everything your account can reach. Allow only plugins you trust.", TextRole.BODY));
        var menu = List.of(new MenuItem("plugin-settings", "Open Settings", "", "settings", true, false), new MenuItem("plugin-remove", "Remove…", "", "remove", true, false));
        notices.add(new ItemList("notice-plugins", READY, ListKind.DOUBLE, List.of(
            new ListItem("notice-bundled", Optional.empty(), List.of(text("notice-bundled-title", "Bundled (1 of 1 enabled)", TextRole.CAPTION)), List.of(), List.of(), true),
            new ListItem("notice-plugin", Optional.of(new Icon("notice-plugin-icon", READY, "plugin", Hue.NEUTRAL, IconSize.RAIL, "Plugin")),
                List.of(text("notice-plugin-name", "Fixture plugin", TextRole.BODY_STRONG)), List.of(text("notice-plugin-meta", "1.0  Fixture vendor", TextRole.CAPTION)),
                List.of(new Toggle("notice-plugin-enabled", READY, "Enable Fixture plugin", true, "toggle", true, "Restart to apply")), false, Optional.of(menu))),
            Set.of("notice-plugin"), "", List.of(), SelectionMode.SINGLE, Optional.empty()));
        notices.add(new Nodes.Stack("notice-links", READY, LayoutRole.INLINE, List.of(
            new Nodes.Button("notice-open", READY, "Open", "", ButtonKind.LINK, "open", "", ButtonPresentation.EMBEDDED),
            new MenuButton("notice-more", READY, "More ▾", "", ButtonKind.LINK, "", List.of(new MenuItem("notice-four", "Four", "", "four", true, false)), "", ButtonPresentation.EMBEDDED))));
        result.add(sheet("notices", new Stack("notices", READY, LayoutRole.VERTICAL, notices), 420, 320));
        // Plan 2 Task 8-kit: Remote rows with tooltips, star buttons (plain and favourite) and a pending switch.
        var remote = new ArrayList<ListItem>();
        for (String id : List.of("web", "db", "socks")) {
            boolean favourite = id.equals("db"), pending = id.equals("socks");
            remote.add(new ListItem("remote-" + id, Optional.of(new StatusDot("remote-" + id + "-dot", READY, pending ? StatusKind.WARNING : favourite ? StatusKind.SUCCESS : StatusKind.IDLE)),
                List.of(text("remote-" + id + "-name", id, TextRole.BODY), text("remote-" + id + "-via", "via build-box", TextRole.DESCRIPTION)),
                List.of(text("remote-" + id + "-map", "8080 → localhost:80", TextRole.MONO_SMALL)),
                List.of(new Nodes.Button("remote-" + id + "-star", READY, "", favourite ? "star.filled" : "star", ButtonKind.GHOST, "favorite", favourite ? "Remove from favorites" : "Add to favorites",
                        favourite ? ButtonPresentation.EMBEDDED : ButtonPresentation.CONTEXTUAL_EMBEDDED, favourite ? Optional.of(Severity.WARNING) : Optional.empty()),
                    new Toggle("remote-" + id + "-run", READY, pending ? "Stop tunnel" : "Start tunnel", favourite || pending, "toggle", true, "", pending)),
                false, Optional.empty(), id + ", 8080 → localhost:80, via build-box"));
        }
        result.add(sheet("remote-rows", new ItemList("remote-tunnels", READY, ListKind.DOUBLE, remote, Set.of("remote-web"), "edit", List.of(), SelectionMode.SINGLE,
            Optional.empty(), OptionalInt.empty(), "toggle", "delete"), 360, 200));
        // Plan 2 Task 9-kit: Editor chrome. A DOCUMENT split with the document filling the height, an inline find bar
        // (Shift+Enter and Escape on the field, Escape over the whole bar, pressed and unpressed option buttons), a Vim
        // command line with its caret and an error message, and a language filter driving its list.
        var hiddenState = new ControlState(false, true, false, "");
        var findField = new Field("editor-find", READY, FieldKind.SEARCH, "needle", "Find", "Find", "next", Optional.empty(), FieldPresentation.EMBEDDED, "previous", "close", "");
        var findBar = new Stack("editor-find-bar", READY, LayoutRole.INLINE, List.of(findField,
            new Button("editor-previous", READY, "Previous", "", ButtonKind.SECONDARY, "previous", ""),
            new Button("editor-next", READY, "Next", "", ButtonKind.SECONDARY, "next", ""),
            new Button("editor-case", READY, "Aa", "", ButtonKind.GHOST, "case", "Match case", ButtonPresentation.STANDARD, Optional.empty(), Optional.of(true)),
            new Button("editor-word", READY, "W", "", ButtonKind.GHOST, "word", "Whole word", ButtonPresentation.STANDARD, Optional.empty(), Optional.of(false)),
            text("editor-matches", "2 matches", TextRole.CAPTION)), "close");
        var vimLine = new Stack("editor-vim", READY, LayoutRole.INLINE, List.of(
            new Text("editor-vim-line", READY, ":%s/old/new/g", TextRole.MONO, Optional.empty(), OptionalInt.of(6)),
            new Text("editor-vim-error", READY, "E486: Pattern not found", TextRole.MONO, Optional.of(Severity.DANGER), OptionalInt.empty())));
        result.add(sheet("editor-document", new Split("editor-tab", READY, SplitKind.DOCUMENT, List.of(
            new Custom("editor-text", READY, "editor-text", CustomKind.EDITOR, "Document"),
            new Empty("editor-loading", hiddenState, new Icon("editor-loading-icon", READY, "file", Hue.NEUTRAL, IconSize.EMPTY, "File"), "Loading notes.txt…", "", List.of()),
            findBar, vimLine)), 780, 440));
        var output = new ArrayList<ListItem>();
        for (int i=0;i<20;i++) output.add(new ListItem("vim-output-row-"+i,Optional.empty(),List.of(new Text("vim-output-text-"+i,READY,
            "Output line "+i,TextRole.MONO,i==19 ? Optional.of(Severity.DANGER) : Optional.empty(),OptionalInt.empty())),List.of(),List.of()));
        result.add(sheet("editor-vim-output", new ItemList("vim-output",READY,ListKind.COMPACT,output,Set.of(),"",List.of(),
            SelectionMode.NONE,Optional.empty(),OptionalInt.of(12)),680,400));
        var languages = new ArrayList<ListItem>();
        for (String name : List.of("Java", "JavaScript", "JSON")) languages.add(new ListItem("editor-language-" + name, Optional.empty(), List.of(text("editor-language-" + name + "-name", name, TextRole.BODY)), List.of(), List.of()));
        result.add(sheet("editor-language", new Stack("editor-language-picker", READY, LayoutRole.VERTICAL, List.of(
            new Field("editor-language-filter", READY, FieldKind.SEARCH, "ja", "Filter languages", "Filter languages", "choose", Optional.empty(), FieldPresentation.STANDARD, "", "", "editor-languages"),
            new ItemList("editor-languages", READY, ListKind.SINGLE, languages, Set.of("editor-language-Java"), "choose", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(14)))), 360, 360));
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
