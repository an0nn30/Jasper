package dev.jasper.ui.gallery;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import java.util.*;

/**
 * The Enterprise mocks' compositions as descriptors (plan 4c): each fixture is the size of its mock crop at 2x, halved
 * (the classic-* pairs in tools/visual/pairs.json), and names its mock under docs/design/enterprise/reference/. Only the
 * Enterprise gallery and the classic kit matrix render them; {@link KitGallery#fixtures()} does not list them.
 */
public final class EnterpriseFixtures {
    private EnterpriseFixtures() {}

    static final ControlState READY = ControlState.READY;
    static final ControlState DISABLED = new ControlState(true, false, false, "");

    /** The seven fixtures, in pairs.json order. */
    public static List<KitGallery.Fixture> create() {
        return List.of(
            new KitGallery.Fixture("classic-new-tunnel", "04 New Tunnel/New Tunnel - Local.png", "normal", 520, 449, new ViewSpec(tunnel())),
            new KitGallery.Fixture("classic-add-host", "03 Add Host/Add Host - Default.png", "normal", 520, 521, new ViewSpec(host())),
            new KitGallery.Fixture("classic-new-file-server-ftp", "02 New File Server/New File Server - FTP.png", "normal", 520, 605, new ViewSpec(server())),
            new KitGallery.Fixture("classic-notifications-groups", "05 Settings/Settings 03 - Appearance & Behavior - Notifications.png", "normal",
                751, 529, new ViewSpec(groups())),
            new KitGallery.Fixture("classic-project-markers", "05 Settings/Settings 15 - Tools - Project Index.png", "normal", 751, 92, new ViewSpec(markers())),
            new KitGallery.Fixture("classic-keymap-tree", "05 Settings/Settings 07 - Keymap.png", "normal", 777, 489, new ViewSpec(keymap())),
            // The panel draws the table unboxed; standing alone it is boxed, so the render is 1 px larger on each side
            // than the crop and pairs.json's render_crop cuts the box away.
            new KitGallery.Fixture("classic-file-servers", "01 Workstation/Workstation - Connected (plugins in use).png", "normal", 302, 45,
                new ViewSpec(servers())));
    }

    static Text text(String id, String value, TextRole role) { return new Text(id, READY, value, role); }
    static Field field(String id, String value, String hint, FieldKind kind) { return new Field(id, READY, kind, value, hint, id, ""); }
    static Field technical(String id, String hint) {
        return new Field(id, READY, FieldKind.TEXT, "", hint, id, "", Optional.empty(), FieldPresentation.TECHNICAL);
    }
    static FormRow row(String id, String label, Node control) { return new FormRow(id, READY, label, "", control, Optional.empty()); }
    static FormRow row(String id, String label, String caption, Node control, Node trailing) {
        return new FormRow(id, READY, label, caption, control, Optional.ofNullable(trailing));
    }
    static Option option(String id, String label) { return new Option(id, label, Optional.empty()); }
    static Option option(String id, String label, Hue hue) { return new Option(id, label, Optional.of(hue)); }
    static Choice exclusive(String id, String label, String selected, Option... options) {
        return new Choice(id, READY, ChoiceKind.EXCLUSIVE, List.of(options), selected, label, id);
    }
    static Choice dropdown(String id, String label, String selected, String... options) {
        return new Choice(id, READY, ChoiceKind.DROPDOWN, Arrays.stream(options).map(o -> new Option(o, o, Optional.empty())).toList(), selected, label, id);
    }
    static Choice picker(String id, String label) { return new Choice(id, READY, ChoiceKind.PICKER, List.of(), "", label, id, ""); }
    static Toggle toggle(String id, boolean checked) { return new Toggle(id, READY, id, checked, id, false); }
    static SettingRow setting(String id, String title, String description, Node control) { return new SettingRow(id, READY, title, description, control, false); }
    static Section section(String id, String title, Node body) { return new Section(id, READY, title, body); }
    static Card card(String id, CardKind kind, Node... children) { return new Card(id, READY, kind, List.of(children)); }
    static Nodes.Button button(String id, String label, ButtonKind kind, ControlState state) { return new Nodes.Button(id, state, label, "", kind, id, ""); }
    static Form full(String id, String title, List<Node> sections, String preview, List<Nodes.Button> actions) {
        return new Form(id, READY, FormKind.FULL, title, Optional.empty(), sections, Optional.of(text(id + "-preview", preview, TextRole.MONO_CAPTION)),
            actions, "cancel", FormSubmission.PRIMARY);
    }

    /** New Tunnel – Local. */
    static Node tunnel() {
        var via = new Choice("via", READY, ChoiceKind.PICKER, List.of(), "", "Via host", "via", "", Optional.empty(),
            Optional.of(new PickerFace("Mac", "dustin@192.168.1.119:22", "terminal", Hue.BLUE, PickerDensity.DEFAULT, true)));
        var route = new Stack("route", READY, LayoutRole.ROUTE, List.of(step("this-computer", Hue.NEUTRAL, "This computer", "localhost:8080"),
            step("via-step", Hue.BLUE, "Via", "Mac"), step("target", Hue.GREEN, "Target on Mac", "localhost:5432")));
        return full("new-tunnel", "New Tunnel", List.of(
            section("tunnel", "Tunnel", card("tunnel-card", CardKind.FORM,
                row("name-row", "Name", field("name", "", "e.g. Postgres", FieldKind.TEXT)),
                row("via-row", "Via host", via),
                row("type-row", "Type", exclusive("type", "Type", "local", option("local", "Local"), option("remote", "Remote"), option("dynamic", "Dynamic (SOCKS)"))),
                row("type-help-row", "", text("type-help", "Connections to a port on this computer go through Mac to the target.", TextRole.CAPTION)))),
            section("ports", "Ports", card("ports-card", CardKind.MIXED,
                row("listen-row", "Listen on", "this computer", field("listen", "localhost", "", FieldKind.TEXT), field("listen-port", "8080", "", FieldKind.PORT)),
                row("forward-row", "Forward to", "as seen from Mac", field("forward", "localhost", "", FieldKind.TEXT), field("forward-port", "5432", "", FieldKind.PORT)),
                route,
                setting("allow-row", "Allow other computers to connect", "Listens on all network interfaces, not just localhost", toggle("allow", false))))),
            "ssh -L 8080:localhost:5432 dustin@192.168.1.119",
            List.of(button("cancel", "Cancel", ButtonKind.SECONDARY, READY), button("submit", "Add Tunnel", ButtonKind.PRIMARY, DISABLED)));
    }

    static Stack step(String id, Hue hue, String caption, String value) {
        return new Stack(id, READY, LayoutRole.VERTICAL, List.of(new Icon(id + "-icon", READY, "terminal", hue, IconSize.CONTROL, caption),
            text(id + "-caption", caption, TextRole.CAPTION), text(id + "-value", value, TextRole.MONO)));
    }

    /** Add Host – Default. */
    static Node host() {
        var keys = new ItemList("keys", READY, ListKind.ORDERED, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE,
            Optional.of(button("key-add", "Add Key…", ButtonKind.SECONDARY, READY)), OptionalInt.empty(), "", "", "", "", "", "No keys");
        return full("add-host", "Add Host", List.of(
            section("connection", "Connection", card("connection-card", CardKind.FORM,
                row("name-row", "Name", field("name", "", "e.g. homelab", FieldKind.TEXT)),
                row("host-row", "Host", "", technical("host", "hostname or IP address"), field("port", "22", "", FieldKind.PORT)),
                row("username-row", "Username", technical("username", "root")),
                row("jump-row", "Jump host", dropdown("jump", "Jump host", "None", "None")))),
            section("authentication", "Authentication", card("authentication-card", CardKind.FORM,
                exclusive("mode", "Authentication", "vault", option("vault", "Vault Credential"), option("agent", "SSH Agent")),
                row("credential-row", "Credential", picker("credential", "Credential")),
                row("keys-row", "Keys", "Tried in order", keys, null))),
            section("options", "Options", card("options-card", CardKind.SETTINGS,
                setting("group-row", "Group", "", dropdown("group", "Group", "Other", "Other")),
                setting("favorite-row", "Add to Favorites", "", toggle("favorite", false)),
                setting("track-row", "Track shell folder", "SFTP follows the terminal's current directory", toggle("track", true))))),
            "ssh user@host", List.of(button("cancel", "Cancel", ButtonKind.SECONDARY, READY), button("connect", "Add and Connect", ButtonKind.SECONDARY, DISABLED),
                button("submit", "Add Host", ButtonKind.PRIMARY, DISABLED)));
    }

    /** New File Server – FTP (the mock's warning strip is a residual until a composition places a Notice there). */
    static Node server() {
        return full("new-file-server", "New File Server", List.of(
            section("server", "Server", card("server-card", CardKind.FORM,
                row("name-row", "Name", field("name", "", "e.g. Downloads", FieldKind.TEXT)),
                row("protocol-row", "Protocol", exclusive("protocol", "Protocol", "ftp", option("http", "HTTP", Hue.BLUE), option("ftp", "FTP", Hue.AMBER),
                    option("sftp", "SFTP", Hue.GREEN), option("tftp", "TFTP", Hue.PURPLE))),
                row("folder-row", "Folder", new Field("folder", READY, FieldKind.PATH, "", "No folder selected", "Folder", "choose-folder")),
                row("reachable-row", "Reachable from", "", dropdown("reachable", "Reachable from", "This computer only", "This computer only", "Local network"),
                    field("port", "2121", "", FieldKind.PORT)),
                row("passive-row", "Passive ports", technical("passive", "Empty uses the range in Settings")))),
            section("access", "Access", card("access-card", CardKind.MIXED,
                exclusive("login", "Login", "vault", option("anonymous", "Anonymous"), option("vault", "Vault Account"), option("session", "Session Password")),
                row("account-row", "Account", picker("account", "Account")),
                row("account-help-row", "", text("account-help", "Clients sign in with the username and password stored in this Vault account.", TextRole.CAPTION)),
                setting("allow-row", "Allow changes", "Clients can upload, rename and delete files", toggle("allow", false)))),
            section("security", "Security & Startup", card("security-card", CardKind.SETTINGS,
                setting("tls-row", "Use TLS", "", toggle("tls", false)),
                new SettingRow("require-row", DISABLED, "Require TLS", "Refuse logins and transfers without it",
                    new Toggle("require", DISABLED, "Require TLS", false, "require", false), true),
                setting("startup-row", "Start when Jasper starts", "", toggle("startup", false))))),
            "ftp://127.0.0.1:2121", List.of(button("cancel", "Cancel", ButtonKind.SECONDARY, READY), button("submit", "Add Server", ButtonKind.PRIMARY, DISABLED)));
    }

    /** Settings 03 Notifications: the Groups table, grouped by plugin, with bare Display dropdowns. */
    static Node groups() {
        var displays = List.of(option("card", "Card"), option("sticky", "Sticky card"), option("off", "Off"));
        var rows = new ArrayList<TableRow>();
        group(rows, "jasper", "Jasper", List.of("Jasper", "Configuration", "Plugin problems", "Internal errors", "Long-running commands", "Memory & VM options"), displays);
        group(rows, "thd", "THD Sourcing Crypto", List.of("THD Sourcing Crypto", "THD Sourcing Crypto activities"), displays);
        group(rows, "editor", "Editor", List.of("Editor", "Editor activities"), displays);
        group(rows, "vault", "Credential Vault", List.of("Credential Vault", "Credential Vault activities", "Vault copies"), displays);
        group(rows, "fileserver", "File Server", List.of("File Server", "File Server activities"), displays);
        group(rows, "snippets", "Snippets", List.of("Snippets", "Snippets activities"), displays);
        return new Table("groups", READY, List.of(new Column("group", "Group", TextRole.BODY, false, 4.6, Optional.empty()),
            new Column("display", "Display", TextRole.BODY, false, 1.36, Optional.empty()),
            new Column("source", "Source", TextRole.DESCRIPTION, false, 1, Optional.empty())), rows, Set.of(), "", List.of());
    }

    static void group(List<TableRow> rows, String id, String title, List<String> names, List<Option> displays) {
        rows.add(TableRow.heading(id, title, OptionalInt.of(names.size()), "group"));
        for (int i = 0; i < names.size(); i++) {
            String row = id + "-" + i;
            rows.add(new TableRow(row, Map.of("group", names.get(i), "source", "Default"), Optional.empty(), false, Optional.empty(), "",
                Map.of("display", new Choice(row + "-display", READY, ChoiceKind.DROPDOWN, displays, id.equals("jasper") && i == 1 ? "sticky" : "card", "Display", "")),
                Optional.empty()));
        }
    }

    /** Settings 15 Project Index: the project markers list and its entry row. */
    static Node markers() {
        var list = new ItemList("markers", READY, ListKind.ORDERED, List.of(marker("csproj", "*.csproj"), marker("xcodeproj", "*.xcodeproj"), marker("flake", "flake.nix")),
            Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty());
        // The entry field's label is its accessible name only (EMBEDDED), as the mock draws none.
        var entry = new Stack("marker-entry", READY, LayoutRole.INLINE, List.of(
            new Field("marker-new", READY, FieldKind.TEXT, "", "Add…", "Project marker", "", Optional.empty(), FieldPresentation.EMBEDDED),
            button("marker-add", "Add", ButtonKind.SECONDARY, DISABLED)));
        return new Stack("project-markers", READY, LayoutRole.VERTICAL, List.of(list, entry));
    }

    static ListItem marker(String id, String value) { return new ListItem(id, Optional.empty(), List.of(text(id + "-value", value, TextRole.MONO)), List.of(), List.of()); }

    /** Settings 07 Keymap: the action tree with its Action, Scope and Shortcut columns (keycaps are the app's, plan 9). */
    static Node keymap() {
        var entries = List.of("Appearance:3", "Application:14", "Clipboard:2", "Command Palette:9", "commandline:2", "config:1", "Credential Vault:12",
            "Editor:4", "File Server:16", "notifications:3", "Panes:8", "Project Index:5", "Remote:24", "Search:6", "Shell History:1", "Snippets:2",
            "Tabs:14", "Terminal:4", "THD Sourcing Crypto:1");
        var roots = new ArrayList<TreeItem>();
        for (var entry : entries) {
            String title = entry.substring(0, entry.indexOf(':')), id = title.toLowerCase(Locale.ROOT).replace(' ', '-');
            int count = Integer.parseInt(entry.substring(entry.indexOf(':') + 1));
            var children = id.equals("clipboard") ? List.of(action("copy", "Copy", "⌘C"), action("paste", "Paste", "⌘V")) : List.<TreeItem>of();
            roots.add(new TreeItem(id, title, Optional.empty(), false, children, OptionalInt.empty(), false, "", Optional.empty(),
                List.of("", count + " shortcuts"), Optional.empty()));
        }
        return new Tree("keymap", READY, roots, Set.of("clipboard"), Set.of(), "", List.of(new Column("action", "Action", TextRole.BODY, false, 5, Optional.empty()),
            new Column("scope", "Scope", TextRole.DESCRIPTION, false), new Column("shortcut", "Shortcut", TextRole.DESCRIPTION, false)));
    }

    /** Workstation – Connected: the File Servers table, its one server with the boxed HTTP tag. */
    static Node servers() {
        var row = new TableRow("test", Map.of("address", "127.0.0.1:8080", "status", "Stopped"), Optional.empty(), false, Optional.empty(), "", Map.of(
            "folder", new Stack("test-folder", READY, LayoutRole.INLINE, List.of(text("test-name", "test", TextRole.BODY), new Chip("test-chip", READY, "HTTP", Hue.BLUE)))),
            Optional.empty());
        return new Table("servers", READY, List.of(new Column("folder", "Folder", TextRole.BODY, false, 1.5, Optional.empty()),
            new Column("address", "Address", TextRole.MONO_SMALL, false, 1.6, Optional.empty()), new Column("status", "Status", TextRole.BODY, false, 1, Optional.empty())),
            List.of(row), Set.of(), "toggle", List.of());
    }

    static TreeItem action(String id, String title, String keys) {
        return new TreeItem(id, title, Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(), List.of("Global", keys), Optional.empty());
    }
}
