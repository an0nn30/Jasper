package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ActivityLog;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.fileserver.workstation.Layout;
import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PanelHeader;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The per-window File Servers rail panel. Rows show confirmed state; the borrowed header owns count and New. The row the
 * details and activity follow is the user's selection, or the first server while nothing is selected; only a selection
 * the user (or {@link #select}) made is shown as selected.
 *
 * <p>Under {@link Layout#CLASSIC} (Enterprise presentation spec 14.5) the rows are a Folder, Address and Status table;
 * the chosen server's Properties and status line follow it, the row menu reads Copy URI and Show Activity.
 */
public final class FileServersPanel implements AutoCloseable {
    public record Actions(Consumer<UUID> toggle, Consumer<Optional<UUID>> edit, Consumer<UUID> duplicate, Consumer<UUID> delete,
                          Consumer<UUID> reveal, Consumer<String> copy, Consumer<UUID> copyPassword, Consumer<UUID> clearActivity,
                          Consumer<UUID> fingerprints) { }

    public static final String ROOT = "fileserver.panel", HEADER = "fileserver.header", ROWS = "fileserver.rows",
        EMPTY = "fileserver.empty", DETAILS = "fileserver.details", ACTIVATE = "fileserver.details.open",
        ENTER_TOGGLE = "fileserver.toggle.selected", KEY_DETAILS = "fileserver.details.key",
        KEY_COPY = "fileserver.copy.key", KEY_CANCEL = "fileserver.details.close.key";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final UiView view;
    private final Actions actions;
    private final Function<UUID, List<ActivityLog.Entry>> log;
    private final Path home;
    private final String firewall;
    private final ZoneId zone;
    private final String headerAddIcon;
    /** Which composition this panel takes, fixed at construction (spec 3.6). */
    private final Layout layout;
    private Consumer<PanelHeader> headerChanged = header -> { };
    private List<ServerRegistry.View> servers = List.of();
    private Optional<String> problem = Optional.empty(), tlsFingerprint = Optional.empty(), hostKeyFingerprint = Optional.empty();
    private String activity = "";
    private List<String> activityLines = List.of();
    private UUID selected;
    /** The row the user (or {@link #select}) selected, shown as selected; {@code selected} falls back to the first row. */
    private UUID chosen;
    private boolean detailsOpen, closed;

    public FileServersPanel(UiKit kit, Actions actions, Function<UUID, List<ActivityLog.Entry>> log,
                            Path home, String firewallHint, ZoneId zone, String headerAddIcon) {
        this(kit, actions, log, home, firewallHint, zone, headerAddIcon, Layout.ROWS);
    }

    /** The panel in {@code layout}'s composition. */
    public FileServersPanel(UiKit kit, Actions actions, Function<UUID, List<ActivityLog.Entry>> log,
                            Path home, String firewallHint, ZoneId zone, String headerAddIcon, Layout layout) {
        this.layout = java.util.Objects.requireNonNull(layout);
        this.actions = actions; this.log = log; this.home = home; this.firewall = firewallHint; this.zone = zone;
        this.headerAddIcon = headerAddIcon;
        view = kit.create(new ViewSpec(node()), this::event);
    }

    public UiView view() { return view; }
    @Override public void close() { if (closed) return; closed = true; view.close(); headerChanged = header -> { }; }

    /** Supplies the window-owned count and New action; never adds another title row. */
    public void onHeaderChanged(Consumer<PanelHeader> listener) { headerChanged = listener; publishHeader(); }

    private void publishHeader() {
        if (closed) return;
        headerChanged.accept(new PanelHeader(servers.isEmpty() ? "" : String.valueOf(servers.size()), false,
            List.of(new PanelHeader.Action("New File Server…", headerAddIcon,
                () -> { if (!closed) actions.edit().accept(Optional.empty()); }))));
    }

    public void setServers(List<ServerRegistry.View> next, Optional<String> problem) {
        if (closed) return;
        servers = List.copyOf(next); this.problem = problem;
        if (chosen != null && server(chosen).isEmpty()) chosen = null;
        if (selectedView().isEmpty()) selected = chosen != null ? chosen : servers.isEmpty() ? null : servers.getFirst().definition().id();
        publishHeader();
        refreshActivity();
        render();
    }

    public void setTlsFingerprint(Optional<String> fingerprint) {
        if (!closed && !fingerprint.equals(tlsFingerprint)) { tlsFingerprint = fingerprint; render(); }
    }

    public void setHostKeyFingerprint(Optional<String> fingerprint) {
        if (!closed && !fingerprint.equals(hostKeyFingerprint)) { hostKeyFingerprint = fingerprint; render(); }
    }

    /** Re-reads the selected server's bounded activity log. */
    public void refreshActivity() {
        if (closed) return;
        List<String> nextLines = selectedView().map(server -> log.apply(server.definition().id()).stream()
            .map(entry -> TIME.format(entry.at().atZone(zone)) + " " + entry.text()).toList()).orElse(List.of());
        String next = String.join("\n", nextLines);
        if (!next.equals(activity)) { activity = next; activityLines = nextLines; render(); revealLatestActivity(); }
    }

    public Optional<UUID> selected() { return Optional.ofNullable(selected); }
    public void select(UUID id) {
        if (closed || servers.stream().noneMatch(server -> server.definition().id().equals(id))) return;
        selected = id; chosen = id; refreshActivity(); render(); view.reveal("row." + id + ".name");
    }

    Optional<ServerRegistry.View> selectedView() {
        return servers.stream().filter(server -> server.definition().id().equals(selected)).findFirst();
    }

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Selected selection when selection.id().equals(ROWS) -> {
                selection.ids().stream().findFirst().flatMap(FileServersPanel::rowUuid).ifPresent(id -> { selected = id; chosen = id; refreshActivity(); render(); });
            }
            case UiEvent.Toggled toggle when toggle.id().startsWith("switch:") ->
                rowUuid(toggle.id().substring("switch:".length())).ifPresent(id -> server(id)
                    .filter(server -> server.state().phase() != Phase.STOPPING).ifPresent(server -> actions.toggle().accept(id)));
            case UiEvent.Action action -> action(action.id());
            default -> { }
        }
    }

    private void action(String id) {
        switch (id) {
            case "fileserver.empty.new" -> actions.edit().accept(Optional.empty());
            case ACTIVATE, KEY_DETAILS -> { if (selectedView().isPresent()) { detailsOpen = id.equals(ACTIVATE) || !detailsOpen; render(); revealLatestActivity(); } }
            case KEY_CANCEL -> { detailsOpen = false; render(); }
            case ENTER_TOGGLE -> selectedView().filter(server -> server.state().phase() != Phase.STOPPING)
                .ifPresent(server -> actions.toggle().accept(server.definition().id()));
            case KEY_COPY -> selectedView().ifPresent(this::copyAddress);
            case "fileserver.details.toggle" -> selectedView().filter(server -> server.state().phase() != Phase.STOPPING)
                .ifPresent(server -> actions.toggle().accept(server.definition().id()));
            case "fileserver.details.edit" -> selectedView().ifPresent(server -> actions.edit().accept(Optional.of(server.definition().id())));
            case "fileserver.activity.clear" -> selectedView().ifPresent(server -> actions.clearActivity().accept(server.definition().id()));
            case "fileserver.activity.copy" -> actions.copy().accept(activity);
            default -> {
                if (id.startsWith("uri:")) copyUri(id);
                else if (id.startsWith("menu:")) menuAction(id);
            }
        }
    }

    private void copyAddress(ServerRegistry.View server) {
        server.addresses().stream().findFirst().ifPresent(uri -> actions.copy().accept(uri.toString()));
    }

    private void copyUri(String action) {
        int colon = action.indexOf(':', 4);
        if (colon < 0) return;
        rowUuid(action.substring(4, colon)).flatMap(this::server).ifPresent(server -> {
            try { int index = Integer.parseInt(action.substring(colon + 1));
                if (index >= 0 && index < server.addresses().size()) actions.copy().accept(server.addresses().get(index).toString());
            } catch (NumberFormatException ignored) { }
        });
    }

    private void menuAction(String action) {
        int colon = action.lastIndexOf(':');
        if (colon < 0) return;
        String verb = action.substring(5, colon);
        rowUuid(action.substring(colon + 1)).flatMap(this::server).ifPresent(server -> {
            UUID id = server.definition().id();
            switch (verb) {
                case "toggle" -> { if (server.state().phase() != Phase.STOPPING) actions.toggle().accept(id); }
                case "edit" -> actions.edit().accept(Optional.of(id));
                case "duplicate" -> actions.duplicate().accept(id);
                case "delete" -> actions.delete().accept(id);
                case "reveal" -> actions.reveal().accept(id);
                case "copy" -> copyAddress(server);
                case "password" -> actions.copyPassword().accept(id);
                case "fingerprints" -> actions.fingerprints().accept(id);
                case "details" -> { selected = id; chosen = id; detailsOpen = !detailsOpen; refreshActivity(); render(); revealLatestActivity(); }
                default -> { }
            }
        });
    }

    private Optional<ServerRegistry.View> server(UUID id) {
        return servers.stream().filter(server -> server.definition().id().equals(id)).findFirst();
    }
    private static Optional<UUID> rowUuid(String row) {
        try { return Optional.of(UUID.fromString(row.startsWith("server:") ? row.substring(7) : row)); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    private static String rowId(UUID id) { return "server:" + id; }

    private void render() { if (!closed) view.update(new ViewSpec(node())); }
    private void revealLatestActivity() {
        if (!closed && detailsOpen && !activityLines.isEmpty()) view.reveal("fileserver.activity.text." + (activityLines.size() - 1));
    }
    private static ControlState state(boolean visible, boolean enabled) { return new ControlState(visible, enabled, false, ""); }
    private static Nodes.Text text(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, ControlState.READY, value, role); }

    private Nodes.Panel node() { return layout == Layout.CLASSIC ? classicNode() : rowsNode(); }

    private Nodes.Panel rowsNode() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "File Servers", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        if (servers.isEmpty()) {
            Nodes.Node empty = new Nodes.Empty(EMPTY, ControlState.READY,
                new Nodes.Icon("fileserver.empty.icon", ControlState.READY, "server", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "File Servers"),
                "No File Servers", "Share a local folder over HTTP with devices on your network.",
                List.of(new Nodes.Button("fileserver.empty.new", ControlState.READY, "Share Folder…", "", Nodes.ButtonKind.SECONDARY,
                    "fileserver.empty.new", "")));
            // The empty state fills the body and centers there; a broken file's note sits above it (a document split).
            if (problem.isPresent()) empty = new Nodes.Split("fileserver.empty.body", ControlState.READY, Nodes.SplitKind.DOCUMENT, List.of(
                new Nodes.Text("fileserver.problem", ControlState.READY, "servers.toml: " + problem.get(),
                    Nodes.TextRole.BODY, Optional.of(Nodes.Severity.DANGER), OptionalInt.empty()), empty));
            return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.empty(), empty, Optional.empty());
        }
        var rows = new ArrayList<ListItem>();
        problem.ifPresent(error -> rows.add(new ListItem("fileserver.problem.row", Optional.of(new Nodes.Icon("fileserver.problem.icon", ControlState.READY,
            "error", Nodes.Hue.NONE, Nodes.IconSize.CONTROL, "Error", Optional.of(Nodes.Severity.DANGER))),
            List.of(text("fileserver.problem.text", "servers.toml: " + error, Nodes.TextRole.BODY)), List.of(), List.of())));
        for (ServerRows.Row row : ServerRows.rows(servers, Optional.empty())) {
            switch (row) {
                case ServerRows.Header heading -> rows.add(new ListItem("fileserver.temporary", Optional.empty(),
                    List.of(text("fileserver.temporary.title", heading.title(), Nodes.TextRole.SECTION_LABEL)), List.of(), List.of(), true));
                case ServerRows.Server server -> rows.add(item(server.view()));
                case ServerRows.Problem ignored -> { }
            }
        }
        var list = new Nodes.ItemList(ROWS, ControlState.READY, Nodes.ListKind.DOUBLE, rows,
            chosen == null ? Set.of() : Set.of(rowId(chosen)), ACTIVATE, List.of(), Nodes.SelectionMode.SINGLE,
            Optional.empty(), OptionalInt.empty(), ENTER_TOGGLE, "", KEY_DETAILS, KEY_COPY, KEY_CANCEL);
        var body = new ArrayList<Nodes.Node>();
        body.add(list);
        selectedView().ifPresent(server -> body.add(details(server)));
        return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.empty(),
            new Nodes.Stack("fileserver.body", ControlState.READY, Nodes.LayoutRole.VERTICAL, body), Optional.empty());
    }

    private ListItem item(ServerRegistry.View server) {
        ServerDefinition definition = server.definition();
        UUID id = definition.id(); String prefix = "row." + id;
        Phase phase = server.state().phase();
        var subtitle = new ArrayList<Nodes.Node>();
        subtitle.add(new Nodes.Chip(prefix + ".protocol", ControlState.READY, definition.protocol().label(), protocolHue(definition.protocol())));
        subtitle.add(text(prefix + ".address", ServerRows.address(server), Nodes.TextRole.MONO_SMALL));
        String warning = ServerRows.warnings(definition).stream().findFirst().orElse("");
        if (phase != Phase.RUNNING && phase != Phase.STOPPED)
            subtitle.add(new Nodes.Text(prefix + ".state", ControlState.READY, ServerRows.state(server.state()), Nodes.TextRole.DESCRIPTION,
                Optional.of(phase == Phase.FAILED ? Nodes.Severity.DANGER : Nodes.Severity.WARNING), OptionalInt.empty()));
        else if (!warning.isEmpty())
            subtitle.add(new Nodes.Text(prefix + ".warning", ControlState.READY, "Warning · " + warning, Nodes.TextRole.DESCRIPTION,
                Optional.of(Nodes.Severity.WARNING), OptionalInt.empty()));
        var toggle = new Nodes.Toggle("switch:" + id, state(true, phase != Phase.STOPPING),
            phase == Phase.STARTING ? "Cancel start" : phase == Phase.STOPPING ? "Stopping" : phase == Phase.RUNNING ? "Stop server" : "Start server",
            phase == Phase.RUNNING, "switch:" + id, true, "", phase == Phase.STARTING || phase == Phase.STOPPING);
        String description = definition.name() + ", " + definition.protocol().label() + ", " + ServerRows.address(server) + ", " + tooltip(server);
        // The shared folder's filled glyph in the type hue; Copy Address is the row menu's and the platform Copy key's.
        return new ListItem(rowId(id), Optional.of(new Nodes.Icon(prefix + ".folder", ControlState.READY, "folder.filled", Nodes.Hue.BLUE,
            Nodes.IconSize.CONTROL, "Folder")),
            List.of(text(prefix + ".name", definition.name(), Nodes.TextRole.BODY)), subtitle, List.of(toggle), false,
            Optional.of(menu(server)), description);
    }

    private static Nodes.Hue protocolHue(Protocol protocol) {
        return switch (protocol) { case HTTP -> Nodes.Hue.BLUE; case SFTP -> Nodes.Hue.GREEN; case FTP -> Nodes.Hue.AMBER; case TFTP -> Nodes.Hue.PURPLE; };
    }

    private String tooltip(ServerRegistry.View server) {
        var parts = new ArrayList<String>();
        parts.add(ServerRows.state(server.state())); parts.addAll(ServerRows.warnings(server.definition()));
        if (server.state().phase() == Phase.RUNNING && !(server.definition().bind() instanceof Bind.Loopback)) parts.add(firewall);
        return String.join(" · ", parts);
    }

    private List<MenuItem> menu(ServerRegistry.View server) {
        UUID id = server.definition().id(); Phase phase = server.state().phase();
        var result = new ArrayList<MenuItem>();
        result.add(menuItem("toggle", id, phase.startable() ? "Start" : phase == Phase.STARTING ? "Cancel Start" : "Stop", phase != Phase.STOPPING));
        result.add(separator("first", id));
        result.add(menuItem("edit", id, server.temporary() ? "Save as a Server…" : "Edit…", true));
        if (!server.temporary()) {
            result.add(menuItem("duplicate", id, "Duplicate", true));
            result.add(menuItem("delete", id, "Delete…", true));
        }
        result.add(menuItem("reveal", id, "Reveal Folder", true));
        result.add(menuItem("copy", id, "Copy Address", !server.addresses().isEmpty()));
        result.add(menuItem("password", id, "Copy Password", phase == Phase.RUNNING && server.user().isPresent()));
        result.add(menuItem("fingerprints", id, "Show Fingerprints…", server.definition().tls() || server.definition().protocol() == Protocol.SFTP));
        result.add(separator("second", id));
        result.add(menuItem("details", id, detailsOpen ? "Hide Details" : "Show Details", true));
        return List.copyOf(result);
    }

    private static MenuItem menuItem(String verb, UUID id, String label, boolean enabled) {
        return new MenuItem("menu." + verb + ":" + id, label, "", "menu:" + verb + ":" + id, enabled, false);
    }
    private static MenuItem separator(String part, UUID id) { return new MenuItem("separator." + part + ":" + id, "", "", "", false, true); }

    // ---- the classic composition (Enterprise presentation spec 14.5)

    /** The classic table's columns, in the Workstation mock's proportions. */
    static final List<Column> COLUMNS = List.of(new Column("folder", "Folder", Nodes.TextRole.BODY, false, 110, Optional.empty()),
        new Column("address", "Address", Nodes.TextRole.MONO, true, 117, Optional.empty()), new Column("status", "Status", Nodes.TextRole.BODY, false, 73, Optional.empty()));
    public static final String PROPERTIES = "fileserver.properties", FOOTER = "fileserver.footer", NOTICE = "fileserver.notice";

    private Nodes.Panel classicNode() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "File Servers", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        var rows = new ArrayList<TableRow>();
        for (ServerRows.Row row : ServerRows.rows(servers, Optional.empty())) {
            switch (row) {
                case ServerRows.Header heading -> rows.add(TableRow.heading("fileserver.temporary", heading.title(), OptionalInt.empty(), ""));
                case ServerRows.Server server -> rows.add(tableRow(server.view()));
                case ServerRows.Problem ignored -> { }
            }
        }
        Optional<Nodes.Empty> empty = servers.isEmpty() ? Optional.of(new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon("fileserver.empty.icon", ControlState.READY, "server", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "File Servers"),
            "No File Servers", "Share a local folder over HTTP with devices on your network.",
            List.of(new Nodes.Button("fileserver.empty.new", ControlState.READY, "Share Folder…", "", Nodes.ButtonKind.SECONDARY, "fileserver.empty.new", ""))))
            : Optional.empty();
        var table = new Nodes.Table(ROWS, ControlState.READY, COLUMNS, rows, chosen == null ? Set.of() : Set.of(rowId(chosen)), ENTER_TOGGLE, List.of(),
            Nodes.SelectionMode.SINGLE, Nodes.ScrollPolicy.STANDARD, empty, "");
        Optional<Nodes.Node> top = problem.map(error -> new Nodes.Stack(NOTICE + ".stack", ControlState.READY, Nodes.LayoutRole.VERTICAL,
            List.of(new Nodes.Notice(NOTICE, ControlState.READY, Nodes.Severity.DANGER, "servers.toml: " + error, List.of()))));
        // The chosen server's Properties (leading, so the classic face stands them flush) and its status line; with
        // nothing chosen the body stays the table alone, as the Workstation mock draws it.
        var below = new ArrayList<Nodes.Node>();
        if (chosen != null) selectedView().ifPresent(server -> {
            below.add(properties(server));
            below.add(new Nodes.Text(FOOTER, ControlState.READY,
                server.definition().name() + " — " + ServerRows.state(server.state()).toLowerCase(Locale.ROOT) + ". Click to toggle.", Nodes.TextRole.CAPTION));
            if (detailsOpen) below.addAll(activity());
        });
        return new Nodes.Panel(ROOT, ControlState.READY, header, top, table,
            below.isEmpty() ? Optional.empty() : Optional.of(new Nodes.Stack("fileserver.below", ControlState.READY, Nodes.LayoutRole.VERTICAL, below)));
    }

    private TableRow tableRow(ServerRegistry.View server) {
        ServerDefinition definition = server.definition();
        UUID id = definition.id(); String prefix = "row." + id;
        var folder = new Nodes.Stack(prefix + ".folder", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(text(prefix + ".name", definition.name(), Nodes.TextRole.BODY),
            new Nodes.Chip(prefix + ".protocol", ControlState.READY, definition.protocol().label(), protocolHue(definition.protocol()))));
        var status = new Nodes.Stack(prefix + ".status", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.StatusDot(prefix + ".dot", ControlState.READY, dot(server.state().phase())), text(prefix + ".state", ServerRows.state(server.state()), Nodes.TextRole.BODY)));
        return new TableRow(rowId(id), Map.of("address", ServerRows.address(server)), Optional.empty(), false, Optional.of(classicMenu(server)),
            definition.name() + ", " + definition.protocol().label() + ", " + ServerRows.address(server) + ", " + tooltip(server),
            Map.of("folder", folder, "status", status), Optional.empty());
    }

    /** Running is success, a transition or a wait for the Vault a warning, a failure danger, stopped idle. */
    static Nodes.StatusKind dot(Phase phase) {
        return switch (phase) {
            case RUNNING -> Nodes.StatusKind.SUCCESS;
            case STARTING, STOPPING, WAITING_FOR_VAULT -> Nodes.StatusKind.WARNING;
            case FAILED -> Nodes.StatusKind.DANGER;
            case STOPPED -> Nodes.StatusKind.IDLE;
        };
    }

    /**
     * The row menu of the default look, with Copy URI for Copy Address and Show Activity for Show Details (Rulings 10); a
     * server with more than one address adds "Copy" and the address for each later one, as the details card's Copy buttons do.
     */
    private List<MenuItem> classicMenu(ServerRegistry.View server) {
        UUID id = server.definition().id();
        var items = new ArrayList<MenuItem>();
        for (MenuItem item : menu(server)) {
            if (item.action().equals("menu:copy:" + id)) {
                items.add(menuItem("copy", id, "Copy URI", item.enabled()));
                for (int i = 1; i < server.addresses().size(); i++)
                    items.add(new MenuItem("menu.uri." + i + ":" + id, "Copy " + server.addresses().get(i), "", "uri:" + id + ":" + i, true, false));
            }
            else if (item.action().equals("menu:details:" + id)) items.add(menuItem("details", id, detailsOpen ? "Hide Activity" : "Show Activity", true));
            else items.add(item);
        }
        return List.copyOf(items);
    }

    private Nodes.Node properties(ServerRegistry.View server) {
        ServerDefinition definition = server.definition();
        var rows = new ArrayList<Nodes.PropertyRow>();
        rows.add(new Nodes.PropertyRow("Folder", ServerRows.folder(definition.folder(), home), true));
        rows.add(new Nodes.PropertyRow("Protocol", definition.protocol().label(), false));
        rows.add(new Nodes.PropertyRow("Port", String.valueOf(definition.port()), true));
        rows.add(new Nodes.PropertyRow("Access", ServerRows.access(definition), false));
        rows.add(new Nodes.PropertyRow("Reachable from", definition.bind().label(), false));
        server.user().ifPresent(user -> rows.add(new Nodes.PropertyRow("User", user, true)));
        if (definition.tls()) rows.add(new Nodes.PropertyRow("TLS certificate", tlsFingerprint.orElse("Created when the server first starts"), tlsFingerprint.isPresent()));
        if (definition.protocol() == Protocol.SFTP)
            rows.add(new Nodes.PropertyRow("SSH host key", hostKeyFingerprint.orElse("Created when the server first starts"), hostKeyFingerprint.isPresent()));
        for (int i = 0; i < server.addresses().size(); i++)
            rows.add(new Nodes.PropertyRow(server.addresses().size() == 1 ? "Address" : "Address " + (i + 1), server.addresses().get(i).toString(), true));
        List<String> warnings = ServerRows.warnings(definition);
        if (!warnings.isEmpty()) rows.add(new Nodes.PropertyRow("Warning", String.join(" · ", warnings), false, Optional.of(Nodes.Severity.WARNING)));
        return new Nodes.Properties(PROPERTIES, ControlState.READY, "Properties — " + definition.name(), rows);
    }

    /** The default look's activity log and its Clear and Copy, shown by Show Activity. */
    private List<Nodes.Node> activity() {
        var activityRows = new ArrayList<ListItem>();
        for (int i = 0; i < activityLines.size(); i++) activityRows.add(new ListItem("fileserver.activity.line." + i,
            Optional.empty(), List.of(text("fileserver.activity.text." + i, activityLines.get(i), Nodes.TextRole.MONO_SMALL)), List.of(), List.of()));
        return List.of(text("fileserver.activity.title", "Activity", Nodes.TextRole.SECTION_LABEL),
            new Nodes.ItemList("fileserver.activity.log", ControlState.READY, Nodes.ListKind.COMPACT, activityRows, Set.of(), "", List.of(),
                Nodes.SelectionMode.NONE, Optional.empty(), OptionalInt.of(8), "", ""),
            new Nodes.Stack("fileserver.activity.actions", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                new Nodes.Button("fileserver.activity.clear", ControlState.READY, "Clear", "remove", Nodes.ButtonKind.GHOST, "fileserver.activity.clear", ""),
                new Nodes.Button("fileserver.activity.copy", ControlState.READY, "Copy", "copy", Nodes.ButtonKind.GHOST, "fileserver.activity.copy", ""))));
    }

    private Nodes.Node details(ServerRegistry.View server) {
        ServerDefinition definition = server.definition();
        Phase phase = server.state().phase();
        var details = new ArrayList<Nodes.Node>();
        details.add(text("fileserver.card.name", definition.name(), Nodes.TextRole.DETAIL_TITLE));
        details.add(text("fileserver.card.state", ServerRows.state(server.state()), Nodes.TextRole.DESCRIPTION));
        details.add(text("fileserver.card.folder", ServerRows.folder(definition.folder(), home), Nodes.TextRole.MONO_SMALL));
        details.add(text("fileserver.card.access", definition.protocol().label() + " :" + definition.port() + " · " + ServerRows.access(definition)
            + " · " + definition.bind().label() + server.user().map(user -> " · user " + user).orElse(""), Nodes.TextRole.DESCRIPTION));
        List<String> warnings = ServerRows.warnings(definition);
        if (!warnings.isEmpty()) details.add(new Nodes.Text("fileserver.card.warnings", ControlState.READY,
            "Warning · " + String.join(" · ", warnings), Nodes.TextRole.DESCRIPTION, Optional.of(Nodes.Severity.WARNING), OptionalInt.empty()));
        if (definition.tls()) details.add(text("fileserver.card.tls", tlsFingerprint.map(fingerprint -> "TLS certificate SHA-256 " + fingerprint)
            .orElse("TLS certificate: created when the server first starts"), Nodes.TextRole.DESCRIPTION));
        if (definition.protocol() == Protocol.SFTP) details.add(text("fileserver.card.hostkey", hostKeyFingerprint.map(fingerprint -> "SSH host key " + fingerprint)
            .orElse("SSH host key: created when the server first starts"), Nodes.TextRole.DESCRIPTION));
        for (int i = 0; i < server.addresses().size(); i++) {
            URI uri = server.addresses().get(i);
            details.add(new Nodes.Stack("fileserver.card.uri." + i, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                text("fileserver.card.uri.text." + i, uri.toString(), Nodes.TextRole.MONO_SMALL),
                new Nodes.Button("uri:" + definition.id() + ":" + i, ControlState.READY, "Copy", "copy", Nodes.ButtonKind.GHOST,
                    "uri:" + definition.id() + ":" + i, ""))));
        }
        details.add(new Nodes.Stack("fileserver.card.actions", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
            new Nodes.Button("fileserver.details.toggle", state(true, phase != Phase.STOPPING),
                switch (phase) { case STOPPED, WAITING_FOR_VAULT -> "Start"; case FAILED -> "Retry"; case STARTING -> "Cancel";
                    case RUNNING -> "Stop"; case STOPPING -> "Stopping…"; }, "", Nodes.ButtonKind.SECONDARY, "fileserver.details.toggle", ""),
            new Nodes.Button("fileserver.details.edit", ControlState.READY, server.temporary() ? "Save as a Server…" : "Edit…", "edit",
                Nodes.ButtonKind.SECONDARY, "fileserver.details.edit", ""))));
        details.add(text("fileserver.activity.title", "Activity", Nodes.TextRole.SECTION_LABEL));
        var activityRows = new ArrayList<ListItem>();
        for (int i = 0; i < activityLines.size(); i++) activityRows.add(new ListItem("fileserver.activity.line." + i,
            Optional.empty(), List.of(text("fileserver.activity.text." + i, activityLines.get(i), Nodes.TextRole.MONO_SMALL)),
            List.of(), List.of()));
        details.add(new Nodes.ItemList("fileserver.activity.log", ControlState.READY, Nodes.ListKind.COMPACT,
            activityRows, Set.of(), "", List.of(), Nodes.SelectionMode.NONE, Optional.empty(), OptionalInt.of(8), "", ""));
        details.add(new Nodes.Stack("fileserver.activity.actions", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
            new Nodes.Button("fileserver.activity.clear", ControlState.READY, "Clear", "remove", Nodes.ButtonKind.GHOST, "fileserver.activity.clear", ""),
            new Nodes.Button("fileserver.activity.copy", ControlState.READY, "Copy", "copy", Nodes.ButtonKind.GHOST, "fileserver.activity.copy", ""))));
        return new Nodes.Card(DETAILS, state(detailsOpen, true), Nodes.CardKind.MIXED, details);
    }
}
