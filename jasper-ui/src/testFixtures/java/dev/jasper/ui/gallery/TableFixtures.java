package dev.jasper.ui.gallery;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import java.util.*;
import java.util.List;

/**
 * SDK 0.18's table vocabulary (plan 3c), as the Workstation and Settings mocks use it: a column tree, a grouped table
 * with cell dropdowns on a selected row, cell stacks with a chip and a status dot, an empty table's state, and the empty
 * lines of a table and an ordered list.
 */
public final class TableFixtures {
    private TableFixtures() {}
    static final ControlState READY = ControlState.READY;

    public static ViewSpec create() {
        var sessions = new Tree("t18-sessions", READY, List.of(new TreeItem("lan", "Local Network", Optional.empty(), false, List.of(
                new TreeItem("mac", "Mac", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.of(StatusKind.SUCCESS),
                    List.of("192.168.1.119", "22"), Optional.empty()),
                new TreeItem("minecraft", "minecraft-local", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.of(StatusKind.IDLE),
                    List.of("192.168.1.124", "22"), Optional.empty())))),
            Set.of("lan"), Set.of("mac"), "connect", List.of(new Column("name", "Name", TextRole.BODY, false),
                new Column("host", "Host", TextRole.MONO_SMALL, false, 1, Optional.of(true)), new Column("port", "Port", TextRole.MONO_SMALL, true)));
        var displays = List.of(new Option("card", "Card", Optional.empty()), new Option("sticky", "Sticky card", Optional.empty()));
        var groups = new Table("t18-groups", READY, List.of(new Column("group", "Group", TextRole.BODY, false, 3, Optional.empty()),
                new Column("display", "Display", TextRole.BODY, false, 1.5, Optional.empty()), new Column("source", "Source", TextRole.DESCRIPTION, false)),
            List.of(TableRow.heading("t18-jasper", "Jasper", OptionalInt.of(2), "group"), group("t18-config", "Configuration", "sticky", displays),
                group("t18-errors", "Internal errors", "card", displays), TableRow.heading("t18-editor", "Editor", OptionalInt.of(1), "group"),
                group("t18-editor-main", "Editor", "card", displays)),
            Set.of("t18-errors"), "edit", List.of(), SelectionMode.SINGLE);
        var servers = new Table("t18-servers", READY, List.of(new Column("folder", "Folder", TextRole.BODY, false, 2, Optional.empty()),
                new Column("address", "Address", TextRole.MONO_SMALL, false, 2, Optional.empty()), new Column("status", "Status", TextRole.BODY, false)),
            List.of(new TableRow("t18-test", Map.of("address", "http://192.168.1.119:8080"), Optional.empty(), false, Optional.empty(), "", Map.of(
                "folder", new Stack("t18-test-folder", READY, LayoutRole.INLINE, List.of(new Text("t18-test-name", READY, "test", TextRole.BODY),
                    new Chip("t18-test-chip", READY, "HTTP", Hue.BLUE))),
                "status", new Stack("t18-test-status", READY, LayoutRole.INLINE, List.of(new StatusDot("t18-test-dot", READY, StatusKind.SUCCESS),
                    new Text("t18-test-word", READY, "Running", TextRole.BODY)))), Optional.empty())),
            Set.of(), "toggle", List.of());
        var tunnels = new Table("t18-tunnels", READY, List.of(new Column("type", "Type", TextRole.BODY, false), new Column("local", "Local", TextRole.MONO_SMALL, false),
                new Column("remote", "Remote", TextRole.MONO_SMALL, false)),
            List.of(), Set.of(), "toggle", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD,
            Optional.of(new Empty("t18-tunnels-empty", READY, new Icon("t18-tunnels-empty-icon", READY, "tunnel", Hue.NEUTRAL, IconSize.EMPTY, "Tunnels"),
                "No Tunnels", "Forward a local port to a remote host.",
                List.of(new Nodes.Button("t18-tunnels-new", READY, "New Tunnel…", "", ButtonKind.SECONDARY, "new-tunnel", "")))), "");
        var files = new Table("t18-files", READY, List.of(new Column("name", "Name", TextRole.BODY, false, 3, Optional.empty()),
                new Column("size", "Size", TextRole.DESCRIPTION, false), new Column("modified", "Modified", TextRole.DESCRIPTION, false, 2, Optional.empty())),
            List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty(), "Not Connected");
        var keys = new ItemList("t18-keys", READY, ListKind.ORDERED, List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty(),
            OptionalInt.empty(), "", "", "", "", "", "No keys");
        return new ViewSpec(new Stack("t18", READY, LayoutRole.VERTICAL, List.of(sessions, groups, servers, tunnels, files, keys)));
    }

    private static TableRow group(String id, String name, String display, List<Option> displays) {
        return new TableRow(id, Map.of("group", name, "source", "Default"), Optional.empty(), false, Optional.empty(), "",
            Map.of("display", new Choice(id + "-display", READY, ChoiceKind.DROPDOWN, displays, display, "Display", "")), Optional.empty());
    }
}
