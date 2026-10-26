package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.workstation.Layout;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The "Tunnels" rail panel on one host-rendered kit view: a filter, saved tunnels by name, then quick forwards under
 * their own heading. Each row shows a state dot, the name and host, the mapping and a start/stop switch; problems stay
 * visible and the live counters are in the row tooltip. One, in the main window.
 *
 * <p>Rows are keyed by the tunnel's id. The switch shows only the confirmed state the plugin passes in (on while Up,
 * pending while Starting, an error while Failed) and never flips by itself; operating it, Enter or Space stops a running
 * tunnel and starts any other. A double click edits a saved tunnel or offers Save as Tunnel for a quick forward; Delete
 * and Backspace delete a saved one. Each row has its own context menu, whose actions name that row. The count and New
 * Tunnel action are the window's: {@link #onHeaderChanged} feeds {@code PanelHost.setHeader}. UI thread only; after
 * {@link #close()} nothing touches the view.
 *
 * <p>Under {@link Layout#CLASSIC} (Enterprise presentation spec 14.4) the same rows are a Type, Local, Remote and Via
 * table over the selected (else the first) tunnel's status line; Enter or a double click starts or stops a row, in
 * place of the switch, and the row menu holds the other gestures.
 *
 * <p>In a window of its own or docked at the bottom ({@link #wide(boolean)}) the panel lays its list beside the
 * selected (else the first) tunnel's detail: under {@link Layout#ROWS} the state, the route strip the tunnel editor
 * draws, the fields and the Start or Stop, Edit, Open in Browser and Copy Address buttons, under {@link Layout#CLASSIC}
 * the table beside the tunnel's Properties and status line. With nothing listed the wide layout is the docked one.
 */
public final class TunnelsPanel implements AutoCloseable {
    /** What the panel asks for; the plugin decides how. */
    public record Actions(Consumer<Tunnel> start, Consumer<Tunnel> stop, Consumer<Tunnel> edit, Consumer<Tunnel> duplicate,
                          Consumer<Tunnel> delete, Consumer<Tunnel> save, Consumer<Tunnel> browse, Consumer<Tunnel> copyAddress,
                          Runnable add) { }

    public static final String ROOT = "tunnels", HEADER = "tunnels.header", FILTER = "tunnels.filter", ROWS = "tunnels.rows",
        NO_MATCH = "tunnels.no-match", EMPTY = "tunnels.empty", EMPTY_NEW = "tunnels.empty.new", PROBLEM = "tunnels.problem",
        QUICK_HEADING = "heading.quick", PROBLEM_ROW = "problem", ACTIVATE = "activate", TOGGLE = "toggle", DELETE = "delete";
    /** The wide layout's nodes: the split, its list and detail columns, and the detail's heading, route, fields and actions. */
    public static final String COLUMNS = "tunnels.columns", LIST_COLUMN = "tunnels.list", DETAIL_COLUMN = "tunnels.detail",
        DETAIL_CONTENT = "tunnels.detail.content", DETAIL_NONE = "tunnels.detail.none", DETAIL_TITLE = "tunnels.detail.title",
        DETAIL_STATE = "tunnels.detail.state", DETAIL_ROUTE = "tunnels.detail.route", DETAIL_FIELDS = "tunnels.detail.fields",
        DETAIL_ACTIONS = "tunnels.detail.actions", DETAIL_TOGGLE = "tunnels.detail.toggle", DETAIL_EDIT = "tunnels.detail.edit",
        DETAIL_BROWSE = "tunnels.detail.browse", DETAIL_COPY = "tunnels.detail.copy", PROPERTIES = "tunnels.properties";
    /** A row's switch is this prefix and the tunnel's id; menu actions are {@code menu:<verb>:<row id>}. */
    static final String SWITCH = "switch:", MENU = "menu:", ROW = "tunnel:";

    private final Actions actions;
    /** Which composition this panel takes, fixed at construction (spec 3.6). */
    private final Layout layout;
    private final LongSupplier nanos;
    private final long multiClickNanos;
    private final UiView view;
    private List<Tunnel> saved = List.of(), quick = List.of();
    private Optional<String> error = Optional.empty();
    private Function<UUID, TunnelState> states = id -> TunnelState.STOPPED;
    private Function<UUID, Optional<String>> hostNames = id -> Optional.empty();
    private Supplier<Instant> clock = Instant::now;
    private Consumer<PanelHeader> headerChanged;
    private String query = "", selectedRow, lastSwitch;
    private long lastSwitchAt;
    /** Whether the panel is asked for its wide layout (a window of its own, or docked at the bottom). */
    private boolean wide;
    private boolean closed;

    /** {@code icons} supplies the header action's artwork, which the window draws. */
    public TunnelsPanel(UiKit kit, Actions actions) {
        this(kit, actions, System::nanoTime, multiClickInterval());
    }

    /** The panel in {@code layout}'s composition. */
    public TunnelsPanel(UiKit kit, Actions actions, Layout layout) {
        this(kit, actions, System::nanoTime, multiClickInterval(), layout);
    }

    /**
     * {@code nanos} and {@code multiClickNanos} (the desktop's double-click interval) tell a double click on a switch from
     * two separate operations: the old switch acted once per gesture.
     */
    TunnelsPanel(UiKit kit, Actions actions, LongSupplier nanos, long multiClickNanos) { this(kit, actions, nanos, multiClickNanos, Layout.ROWS); }

    TunnelsPanel(UiKit kit, Actions actions, LongSupplier nanos, long multiClickNanos, Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        this.actions = Objects.requireNonNull(actions); this.nanos = Objects.requireNonNull(nanos);
        if (multiClickNanos < 0) throw new IllegalArgumentException("multiClickNanos must not be negative");
        this.multiClickNanos = multiClickNanos;
        view = kit.create(new ViewSpec(node()), this::handle);
    }

    public UiView view() { return view; }

    /** Supplies the window-owned count and add action, now and whenever the tunnels change. Called on the UI thread. */
    public void onHeaderChanged(Consumer<PanelHeader> listener) { headerChanged = Objects.requireNonNull(listener); publishHeader(); }

    /** Replaces what the panel lists: saved tunnels by name, then quick forwards under their own heading. */
    public void setTunnels(List<Tunnel> saved, List<Tunnel> quick, Optional<String> error, Function<UUID, TunnelState> states,
                           Function<UUID, Optional<String>> hostNames) {
        if (closed) return;
        this.saved = saved.stream().sorted(Comparator.comparing((Tunnel tunnel) -> tunnel.name().toLowerCase(Locale.ROOT))).toList();
        this.quick = List.copyOf(quick);
        this.error = error;
        this.states = states;
        this.hostNames = hostNames;
        publishHeader();
        render();
    }

    /** Shows the counters and uptimes as they are now; the plugin calls this once a second while a tunnel is up. */
    public void refresh() { render(); }

    void setClock(Supplier<Instant> value) { clock = value; render(); }

    /**
     * Asks for the wide layout, the list beside the selected (else the first) tunnel's detail, or the docked one. Nothing
     * changes while the value is the same or after {@link #close()}. With no tunnel listed the panel keeps its docked
     * composition, whose empty state fills the window.
     */
    public void wide(boolean value) {
        if (closed || value == wide) return;
        wide = value;
        render();
    }

    /** Whether the wide layout is asked for, as {@link #wide(boolean)} last set it. */
    public boolean wide() { return wide; }

    /** The rows shown now, in order: tunnels by row id, the quick forwards' heading and the broken-file row. */
    List<String> rowIds() { return shown().stream().map(Row::id).toList(); }

    /** The row id of a tunnel. */
    static String rowId(UUID tunnel) { return ROW + tunnel; }

    // ---- the classic composition (Enterprise presentation spec 14.4)

    /** The classic table's columns, in the Workstation mock's proportions. */
    static final List<Column> TABLE_COLUMNS = List.of(new Column("type", "Type", Nodes.TextRole.BODY, false, 76, Optional.empty()),
        new Column("local", "Local", Nodes.TextRole.MONO, false, 87, Optional.empty()), new Column("remote", "Remote", Nodes.TextRole.MONO, false, 110, Optional.empty()),
        new Column("via", "Via", Nodes.TextRole.BODY, false, 77, Optional.empty()));
    public static final String FOOTER = "tunnels.footer", TOP = "tunnels.top";

    private Nodes.Panel classicNode(boolean columns) {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Tunnels", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        List<Row> rows = shown();
        if (selectedRow != null && rows.stream().noneMatch(row -> row.id().equals(selectedRow) && row.tunnel() != null)) selectedRow = null;
        boolean none = saved.isEmpty() && quick.isEmpty();
        var tableRows = new ArrayList<TableRow>();
        for (Row row : rows) {
            if (row.id().equals(QUICK_HEADING)) tableRows.add(TableRow.heading(QUICK_HEADING, "Quick forwards", OptionalInt.empty(), ""));
            else if (row.tunnel() != null) tableRows.add(tableRow(row));
        }
        Optional<Nodes.Empty> empty = none ? Optional.of(new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "tunnel", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Tunnels"), "No Tunnels",
            "Forward a local port to a remote host, or use Forward Port from an SSH pane.",
            List.of(new Nodes.Button(EMPTY_NEW, ControlState.READY, "New Tunnel…", "", Nodes.ButtonKind.SECONDARY, EMPTY_NEW, "")))) : Optional.empty();
        boolean filtered = !none && tableRows.stream().noneMatch(row -> row.heading().isEmpty());
        var table = new Nodes.Table(ROWS, ControlState.READY, TABLE_COLUMNS, tableRows, selectedRow == null ? Set.of() : Set.of(selectedRow), TOGGLE, List.of(),
            Nodes.SelectionMode.SINGLE, Nodes.ScrollPolicy.STANDARD, empty, filtered ? "No tunnels match" : "");
        var top = new ArrayList<Nodes.Node>();
        error.ifPresent(message -> top.add(new Nodes.Notice(PROBLEM, ControlState.READY, Nodes.Severity.DANGER, problemText(), List.of())));
        if (!none) top.add(new Nodes.Field(FILTER, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Filter tunnels", "Filter tunnels", ""));
        Optional<Nodes.Node> search = top.isEmpty() ? Optional.empty()
            : top.size() == 1 && top.getFirst() instanceof Nodes.Field ? Optional.of(top.getFirst())
            : Optional.of(new Nodes.Stack(TOP, ControlState.READY, Nodes.LayoutRole.VERTICAL, top));
        Optional<Tunnel> detailed = detailed(rows);
        if (!columns) return new Nodes.Panel(ROOT, ControlState.READY, header, search, table, detailed.<Nodes.Node>map(this::statusLine));
        // The table in a list column beside the tunnel's Properties and status line, which the docked table holds under it.
        var listColumn = new Nodes.Panel(LIST_COLUMN, ControlState.READY, hiddenHeader(LIST_COLUMN + ".header", "Tunnels"), search, table, Optional.empty());
        var details = new ArrayList<Nodes.Node>();
        detailed.ifPresentOrElse(tunnel -> { details.add(properties(tunnel)); details.add(statusLine(tunnel)); },
            () -> details.add(new Nodes.Text(DETAIL_NONE, ControlState.READY, "Select a tunnel to see its properties.", Nodes.TextRole.DESCRIPTION)));
        return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.empty(),
            split(listColumn, new Nodes.Stack(DETAIL_CONTENT, ControlState.READY, Nodes.LayoutRole.DETAIL, details)), Optional.empty());
    }

    private TableRow tableRow(Row row) {
        Tunnel tunnel = row.tunnel(); String id = row.id();
        TunnelState state = states.apply(tunnel.id());
        Optional<String> hostName = hostNames.apply(tunnel.hostId());
        var type = new Nodes.Stack("row." + id + ".type", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.StatusDot("row." + id + ".dot", ControlState.READY, dot(state)),
            new Nodes.Text("row." + id + ".kind", ControlState.READY, kindWord(tunnel.kind()), Nodes.TextRole.BODY)));
        Ends ends = ends(tunnel, state);
        String detail = TunnelRows.detail(tunnel, state, hostName, clock.get());
        return new TableRow(id, Map.of("local", ends.local(), "remote", ends.remote(), "via", hostName.orElse("—")), Optional.empty(), false,
            Optional.of(menu(id, tunnel, state)), TunnelRows.title(tunnel, state) + ", " + TunnelRows.mapping(tunnel, state) + ", " + detail,
            Map.of("type", type), Optional.empty());
    }

    private static String kindWord(TunnelKind kind) { return switch (kind) { case LOCAL -> "Local"; case REMOTE -> "Remote"; case DYNAMIC -> "Dynamic"; }; }

    /** The Local and Remote values of the classic table and of its Properties. */
    private record Ends(String local, String remote) { }

    private static Ends ends(Tunnel tunnel, TunnelState state) {
        String listen = (tunnel.bindAddress().equals(Tunnel.LOCALHOST) ? "" : bracketed(tunnel.bindAddress())) + ":" + TunnelRows.port(tunnel, state);
        String target = bracketed(tunnel.targetHost()) + ":" + tunnel.targetPort();
        String remote = switch (tunnel.kind()) { case LOCAL -> target; case REMOTE -> listen; case DYNAMIC -> "SOCKS5"; };
        return new Ends(tunnel.kind() == TunnelKind.REMOTE ? target : listen, remote);
    }

    private static String bracketed(String host) { return host.contains(":") ? "[" + host + "]" : host; }

    /** The classic look's word for a confirmed state, as its status line and Properties both say it. */
    private static String stateWord(TunnelState state) {
        return switch (state) {
            case TunnelState.Up up -> "active";
            case TunnelState.Starting starting -> "starting";
            case TunnelState.Failed failed -> "failed: " + failed.reason();
            case TunnelState.Stopped stopped -> "inactive";
        };
    }

    /** The selected tunnel's, else the first one's, line: its name, its confirmed state and the gesture. */
    private String footerText(Tunnel tunnel) {
        TunnelState state = states.apply(tunnel.id());
        return TunnelRows.title(tunnel, state) + " — " + stateWord(state) + ". Click to toggle.";
    }

    private Nodes.Text statusLine(Tunnel tunnel) { return new Nodes.Text(FOOTER, ControlState.READY, footerText(tunnel), Nodes.TextRole.CAPTION); }

    /** The tunnel's Properties beside the classic table: its type, ends, host and state, and while up what it counted. */
    private Nodes.Properties properties(Tunnel tunnel) {
        TunnelState state = states.apply(tunnel.id());
        Ends ends = ends(tunnel, state);
        var rows = new ArrayList<Nodes.PropertyRow>(List.of(new Nodes.PropertyRow("Type", kindWord(tunnel.kind()), false),
            new Nodes.PropertyRow("Local", ends.local(), true), new Nodes.PropertyRow("Remote", ends.remote(), true),
            new Nodes.PropertyRow("Via", hostNames.apply(tunnel.hostId()).orElse("—"), false),
            new Nodes.PropertyRow("State", stateWord(state), false)));
        if (state instanceof TunnelState.Up up) counts(up).forEach(fact -> rows.add(new Nodes.PropertyRow(fact.caption(), fact.value(), false)));
        return new Nodes.Properties(PROPERTIES, ControlState.READY, "Properties — " + TunnelRows.title(tunnel, state), rows);
    }

    /** A captioned value of the wide detail: what a tunnel that is up counted, and how long it has been up. */
    private record Fact(String key, String caption, String value) { }

    private List<Fact> counts(TunnelState.Up up) {
        var counters = up.counters();
        return List.of(new Fact("connections", "Connections", String.valueOf(counters.open())),
            new Fact("traffic", "Traffic", "↓" + TunnelRows.bytes(counters.received()) + " ↑" + TunnelRows.bytes(counters.sent())),
            new Fact("uptime", "Uptime", TunnelRows.duration(Duration.between(up.since(), clock.get()))));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        headerChanged = null;
        view.close();
    }

    // ---- events

    private void handle(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(FILTER) -> { query = edited.text(); render(); }
            case UiEvent.Selected selected when selected.id().equals(ROWS) -> {
                selectedRow = selected.ids().stream().findFirst().filter(id -> tunnel(id).isPresent()).orElse(null);
                render();
            }
            case UiEvent.Toggled toggled when toggled.id().startsWith(SWITCH) -> switched(toggled.id().substring(SWITCH.length()));
            case UiEvent.Action action -> act(action.id());
            default -> { }
        }
    }

    /**
     * The row's switch. The second click of a double click is not a second operation, as the old switch acted once per
     * gesture. The value it reports is ignored: what happens follows the confirmed state.
     */
    private void switched(String id) {
        long now = nanos.getAsLong();
        boolean second = id.equals(lastSwitch) && now - lastSwitchAt < multiClickNanos;
        lastSwitch = id; lastSwitchAt = now;
        if (!second) tunnel(ROW + id).ifPresent(this::toggle);
    }

    private void act(String id) {
        switch (id) {
            case ACTIVATE -> selected().ifPresent(tunnel -> (tunnel.quick() ? actions.save() : actions.edit()).accept(tunnel));
            case TOGGLE -> selected().ifPresent(this::toggle);
            // Only saved tunnels; a quick forward goes away when stopped.
            case DELETE -> selected().filter(tunnel -> !tunnel.quick()).ifPresent(actions.delete());
            case EMPTY_NEW -> actions.add().run();
            default -> menu(id);
        }
    }

    private void menu(String id) {
        if (!id.startsWith(MENU)) return;
        String rest = id.substring(MENU.length());
        int colon = rest.indexOf(':');
        if (colon < 0) return;
        String verb = rest.substring(0, colon);
        tunnel(rest.substring(colon + 1)).ifPresent(tunnel -> {
            switch (verb) {
                case "start" -> actions.start().accept(tunnel);
                case "stop" -> actions.stop().accept(tunnel);
                case "browse" -> actions.browse().accept(tunnel);
                case "copy" -> actions.copyAddress().accept(tunnel);
                case "save" -> actions.save().accept(tunnel);
                case "edit" -> actions.edit().accept(tunnel);
                case "duplicate" -> actions.duplicate().accept(tunnel);
                case "delete" -> { if (!tunnel.quick()) actions.delete().accept(tunnel); }
                default -> { }
            }
        });
    }

    /** Stops a running tunnel (Starting or Up) and starts any other, by the confirmed state. */
    private void toggle(Tunnel tunnel) {
        (states.apply(tunnel.id()).running() ? actions.stop() : actions.start()).accept(tunnel);
    }

    private Optional<Tunnel> selected() { return selectedRow == null ? Optional.empty() : tunnel(selectedRow); }

    /** The tunnel listed now under this row id. */
    private Optional<Tunnel> tunnel(String rowId) {
        return shown().stream().filter(row -> row.id().equals(rowId) && row.tunnel() != null).map(Row::tunnel).findFirst();
    }

    /** The desktop's double-click interval, 500 ms where it has none. */
    private static long multiClickInterval() {
        Object interval = null;
        try { interval = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval"); }
        catch (RuntimeException | java.awt.AWTError headless) { }
        return TimeUnit.MILLISECONDS.toNanos(interval instanceof Integer millis ? millis : 500);
    }

    // ---- descriptors

    /** A line of the list: a tunnel, or (with no tunnel) the quick forwards' heading or the broken-file row. */
    private record Row(String id, Tunnel tunnel) { }

    private List<Row> shown() {
        var rows = new ArrayList<Row>();
        if (error.isPresent()) rows.add(new Row(PROBLEM_ROW, null));
        saved.stream().filter(this::matches).forEach(tunnel -> rows.add(new Row(rowId(tunnel.id()), tunnel)));
        List<Tunnel> quickShown = quick.stream().filter(this::matches).toList();
        if (!quickShown.isEmpty()) {
            rows.add(new Row(QUICK_HEADING, null));
            quickShown.forEach(tunnel -> rows.add(new Row(rowId(tunnel.id()), tunnel)));
        }
        return rows;
    }

    private boolean matches(Tunnel tunnel) { return TunnelRows.matches(tunnel, hostNames.apply(tunnel.hostId()).orElse(""), query); }

    private void publishHeader() {
        if (headerChanged == null || closed) return;
        headerChanged.accept(new PanelHeader(saved.isEmpty() && quick.isEmpty() ? "" : String.valueOf(saved.size() + quick.size()), false,
            List.of(new PanelHeader.Action("New Tunnel…", "add", () -> { if (!closed) actions.add().run(); }))));
    }

    private void render() { if (!closed && view != null) view.update(new ViewSpec(node())); }

    private String problemText() { return error.map(message -> "tunnels.toml has errors: " + message).orElse(""); }

    private static Nodes.Icon severity(String id, String glyph, String name, Nodes.Severity severity, Nodes.IconSize size) {
        return new Nodes.Icon(id, ControlState.READY, glyph, Nodes.Hue.NONE, size, name, Optional.of(severity));
    }

    private Nodes.Panel node() {
        // With nothing listed the wide layout is the docked one, whose empty state fills the window.
        boolean columns = wide && !(saved.isEmpty() && quick.isEmpty());
        return layout == Layout.CLASSIC ? classicNode(columns) : rowsNode(columns);
    }

    private Nodes.Panel rowsNode(boolean columns) {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Tunnels", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        if (saved.isEmpty() && quick.isEmpty()) {
            Nodes.Node empty = new Nodes.Empty(EMPTY, ControlState.READY,
                new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "tunnel", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Tunnels"), "No Tunnels",
                "Forward a local port to a remote host, or use Forward Port from an SSH pane.",
                List.of(new Nodes.Button(EMPTY_NEW, ControlState.READY, "New Tunnel", "", Nodes.ButtonKind.SECONDARY, EMPTY_NEW, "")));
            // The empty state fills the body and centers there. A broken file shows above it, its message wrapping at the
            // panel's width; the empty state then fills what the note leaves (a document split's filling child).
            if (error.isPresent()) empty = new Nodes.Split(ROOT + ".body", ControlState.READY, Nodes.SplitKind.DOCUMENT, List.of(
                new Nodes.Stack(PROBLEM + ".note", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                    new Nodes.Stack(PROBLEM + ".line", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                        severity(PROBLEM + ".icon", "error", "Error", Nodes.Severity.DANGER, Nodes.IconSize.CAPTION),
                        new Nodes.Text(PROBLEM + ".title", ControlState.READY, "tunnels.toml has errors", Nodes.TextRole.BODY_STRONG))),
                    new Nodes.Text(PROBLEM, ControlState.READY, problemText(), Nodes.TextRole.BODY))),
                empty));
            return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.empty(), empty, Optional.empty());
        }
        List<Row> rows = shown();
        if (selectedRow != null && rows.stream().noneMatch(row -> row.id().equals(selectedRow) && row.tunnel() != null)) selectedRow = null;
        var filter = new Nodes.Field(FILTER, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Filter tunnels", "Filter tunnels", "");
        var list = new Nodes.ItemList(ROWS, ControlState.READY, Nodes.ListKind.DOUBLE, rows.stream().map(this::item).toList(),
            selectedRow == null ? Set.of() : Set.of(selectedRow), ACTIVATE, List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(),
            TOGGLE, DELETE);
        Optional<Nodes.Node> footer = rows.stream().noneMatch(row -> row.tunnel() != null)
            ? Optional.of(new Nodes.Text(NO_MATCH, ControlState.READY, "No tunnels match", Nodes.TextRole.DESCRIPTION)) : Optional.empty();
        if (!columns) return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.of(filter), list, footer);
        var listColumn = new Nodes.Panel(LIST_COLUMN, ControlState.READY, hiddenHeader(LIST_COLUMN + ".header", "Tunnels"), Optional.of(filter), list, footer);
        return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.empty(), split(listColumn, detail(detailed(rows))), Optional.empty());
    }

    // ---- the wide composition: the list beside the selected tunnel's detail

    /** The tunnel the wide detail describes: the selected one, else the first listed. */
    private Optional<Tunnel> detailed(List<Row> rows) {
        return selected().or(() -> rows.stream().map(Row::tunnel).filter(Objects::nonNull).findFirst());
    }

    /** A column's header, which the column does not draw. */
    private static Nodes.Header hiddenHeader(String id, String title) {
        return new Nodes.Header(id, new ControlState(false, true, false, ""), title, OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Nodes.Split split(Nodes.Panel listColumn, Nodes.Node detail) {
        var detailColumn = new Nodes.Panel(DETAIL_COLUMN, ControlState.READY, hiddenHeader(DETAIL_COLUMN + ".header", "Details"), Optional.empty(), detail, Optional.empty());
        return new Nodes.Split(COLUMNS, ControlState.READY, Nodes.SplitKind.SIDEBAR_DETAIL, List.of(listColumn, detailColumn));
    }

    /** The tunnel's heading, route, fields and actions; the empty state while no tunnel is listed. */
    private Nodes.Node detail(Optional<Tunnel> shown) {
        if (shown.isEmpty()) return new Nodes.Empty(DETAIL_NONE, ControlState.READY,
            new Nodes.Icon(DETAIL_NONE + ".icon", ControlState.READY, "tunnel", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Tunnels"),
            "No Tunnel Selected", "", List.of());
        Tunnel tunnel = shown.get();
        String id = rowId(tunnel.id());
        TunnelState state = states.apply(tunnel.id());
        Optional<String> hostName = hostNames.apply(tunnel.hostId());
        boolean running = state.running();
        var heading = new Nodes.Stack(DETAIL_COLUMN + ".heading", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Icon(DETAIL_COLUMN + ".icon", ControlState.READY, "tunnel", Nodes.Hue.NEUTRAL, Nodes.IconSize.PROMPT, "Tunnel"),
            new Nodes.Stack(DETAIL_COLUMN + ".identity", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                new Nodes.Text(DETAIL_TITLE, ControlState.READY, TunnelRows.title(tunnel, state), Nodes.TextRole.DETAIL_TITLE),
                stateLine(state, hostName.isEmpty()))),
            button(DETAIL_EDIT, tunnel.quick() ? "Save as Tunnel…" : "Edit…", Nodes.ButtonKind.SECONDARY, tunnel.quick() ? "save" : "edit", id, true),
            button(DETAIL_TOGGLE, running ? "Stop" : "Start", Nodes.ButtonKind.PRIMARY, running ? "stop" : "start", id, true)));
        String listen = bracketed(tunnel.bindAddress()) + ":" + TunnelRows.port(tunnel, state);
        String target = bracketed(tunnel.targetHost()) + ":" + tunnel.targetPort();
        var route = TunnelEditor.route(DETAIL_ROUTE, tunnel.kind(), listen, hostName.orElse("Host missing"), target);
        var fields = new ArrayList<Nodes.Node>();
        fields.add(field("type", "Type", kindWord(tunnel.kind())));
        fields.add(hostName.map(name -> field("via", "Via", name)).orElseGet(() -> field("via", "Via", "Host missing", Optional.of(Nodes.Severity.WARNING))));
        if (state instanceof TunnelState.Up up) counts(up).forEach(fact -> fields.add(field(fact.key(), fact.caption(), fact.value())));
        return new Nodes.Stack(DETAIL_CONTENT, ControlState.READY, Nodes.LayoutRole.DETAIL, List.of(heading, route,
            new Nodes.Card(DETAIL_FIELDS, ControlState.READY, Nodes.CardKind.FORM, fields),
            new Nodes.Stack(DETAIL_ACTIONS, ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                button(DETAIL_BROWSE, "Open in Browser", Nodes.ButtonKind.SECONDARY, "browse", id, browsable(tunnel, state)),
                button(DETAIL_COPY, "Copy Address", Nodes.ButtonKind.SECONDARY, "copy", id, copyable(tunnel, state))))));
    }

    /**
     * What the tunnel is doing, as the docked row says it: its failure or its stage first, then a missing host, then an up
     * tunnel's warning or how long it has been up, else Stopped.
     */
    private Nodes.Text stateLine(TunnelState state, boolean hostMissing) {
        if (state instanceof TunnelState.Failed failed) return new Nodes.Text(DETAIL_STATE, ControlState.READY, failed.reason(), Nodes.TextRole.DESCRIPTION,
            Optional.of(Nodes.Severity.DANGER), OptionalInt.empty());
        if (state instanceof TunnelState.Starting starting) return new Nodes.Text(DETAIL_STATE, ControlState.READY, starting.stage(), Nodes.TextRole.DESCRIPTION);
        if (hostMissing) return new Nodes.Text(DETAIL_STATE, ControlState.READY, "Host missing · Edit to choose a host", Nodes.TextRole.DESCRIPTION,
            Optional.of(Nodes.Severity.WARNING), OptionalInt.empty());
        if (state instanceof TunnelState.Up up) return up.counters().warning()
            .map(warning -> new Nodes.Text(DETAIL_STATE, ControlState.READY, warning, Nodes.TextRole.DESCRIPTION, Optional.of(Nodes.Severity.WARNING), OptionalInt.empty()))
            .orElseGet(() -> new Nodes.Text(DETAIL_STATE, ControlState.READY, "Up for " + TunnelRows.duration(Duration.between(up.since(), clock.get())),
                Nodes.TextRole.DESCRIPTION));
        return new Nodes.Text(DETAIL_STATE, ControlState.READY, "Stopped", Nodes.TextRole.DESCRIPTION);
    }

    /** A button that does what the row's menu item of the same verb does, to this row. */
    private static Nodes.Button button(String id, String label, Nodes.ButtonKind kind, String verb, String rowId, boolean enabled) {
        return new Nodes.Button(id, new ControlState(true, enabled, false, ""), label, "", kind, MENU + verb + ":" + rowId, "");
    }

    /** One captioned value of the detail card, as the Vault manager's detail rows. */
    private static Nodes.Node field(String key, String caption, String value) { return field(key, caption, value, Optional.empty()); }

    private static Nodes.Node field(String key, String caption, String value, Optional<Nodes.Severity> severity) {
        String id = DETAIL_FIELDS + "." + key;
        return new Nodes.Stack(id + ".row", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text(id + ".label", ControlState.READY, caption, Nodes.TextRole.CAPTION),
            new Nodes.Text(id, ControlState.READY, value, Nodes.TextRole.BODY, severity, OptionalInt.empty())));
    }

    private ListItem item(Row row) {
        if (row.id().equals(QUICK_HEADING))
            return new ListItem(QUICK_HEADING, Optional.empty(),
                List.of(new Nodes.Text(QUICK_HEADING + ".text", ControlState.READY, "Quick forwards", Nodes.TextRole.SECTION_LABEL)), List.of(), List.of(), true);
        if (row.tunnel() == null) {
            // The broken file: a row of its own with no switch and no menu, as before; the tooltip has the whole message.
            return new ListItem(PROBLEM_ROW, Optional.of(severity("row.problem.icon", "error", "Error", Nodes.Severity.DANGER, Nodes.IconSize.CONTROL)),
                List.of(new Nodes.Text("row.problem.text", ControlState.READY, problemText(), Nodes.TextRole.BODY_STRONG)),
                List.of(), List.of(), false, Optional.empty(), problemText());
        }
        Tunnel tunnel = row.tunnel();
        String id = row.id();
        TunnelState state = states.apply(tunnel.id());
        Optional<String> hostName = hostNames.apply(tunnel.hostId());
        String title = TunnelRows.title(tunnel, state);
        String mapping = TunnelRows.mapping(tunnel, state);
        String shortMapping = tunnel.bindAddress().equals(Tunnel.LOCALHOST) && tunnel.kind() != TunnelKind.REMOTE
            ? mapping.substring(tunnel.bindAddress().length()) : mapping;
        // The compact row's first line is the name and the host, or in its place what is happening or wrong (the old third
        // line), which the panel's width would otherwise squeeze beside the route; the second line is the route alone.
        var titleLine = new ArrayList<Nodes.Node>();
        titleLine.add(new Nodes.Text("row." + id + ".name", ControlState.READY, title, Nodes.TextRole.BODY));
        String problem = "row." + id + ".problem";
        switch (state) {
            case TunnelState.Failed failed -> {
                titleLine.add(severity(problem + ".icon", "error", "Failed", Nodes.Severity.DANGER, Nodes.IconSize.CAPTION));
                titleLine.add(new Nodes.Text(problem, ControlState.READY, failed.reason(), Nodes.TextRole.DESCRIPTION));
            }
            case TunnelState.Starting starting -> titleLine.add(new Nodes.Text(problem, ControlState.READY, starting.stage(), Nodes.TextRole.DESCRIPTION));
            default -> {
                if (hostName.isEmpty()) titleLine.add(new Nodes.Text(problem, ControlState.READY, "Host missing · Edit to choose a host", Nodes.TextRole.DESCRIPTION,
                    Optional.of(Nodes.Severity.WARNING), OptionalInt.empty()));
                else if (state instanceof TunnelState.Up up && up.counters().warning().isPresent()) {
                    titleLine.add(severity(problem + ".icon", "warning", "Warning", Nodes.Severity.WARNING, Nodes.IconSize.CAPTION));
                    titleLine.add(new Nodes.Text(problem, ControlState.READY, up.counters().warning().orElseThrow(), Nodes.TextRole.DESCRIPTION));
                } else titleLine.add(new Nodes.Text("row." + id + ".via", ControlState.READY, "via " + hostName.orElseThrow(), Nodes.TextRole.DESCRIPTION));
            }
        }
        var subtitle = List.<Nodes.Node>of(new Nodes.Text("row." + id + ".mapping", ControlState.READY, shortMapping, Nodes.TextRole.MONO_SMALL));
        // Confirmed state only: on while Up, pending (operable) while Starting, the reason while Failed.
        var toggle = new Nodes.Toggle(SWITCH + tunnel.id(),
            new ControlState(true, true, false, state instanceof TunnelState.Failed failed ? failed.reason() : ""),
            state.running() ? "Stop tunnel" : "Start tunnel", state instanceof TunnelState.Up, SWITCH + tunnel.id(), true, "",
            state instanceof TunnelState.Starting);
        String detail = TunnelRows.detail(tunnel, state, hostName, clock.get());
        return new ListItem(id,
            Optional.of(new Nodes.StatusDot("row." + id + ".dot", ControlState.READY, dot(state))),
            titleLine, subtitle, List.of(toggle), false, Optional.of(menu(id, tunnel, state)), title + ", " + mapping + ", " + detail);
    }

    /** Up is success (a warning while up is a warning), Starting a warning, Failed danger, Stopped idle. */
    static Nodes.StatusKind dot(TunnelState state) {
        return switch (state) {
            case TunnelState.Up up -> up.counters().warning().isPresent() ? Nodes.StatusKind.WARNING : Nodes.StatusKind.SUCCESS;
            case TunnelState.Starting starting -> Nodes.StatusKind.WARNING;
            case TunnelState.Failed failed -> Nodes.StatusKind.DANGER;
            case TunnelState.Stopped stopped -> Nodes.StatusKind.IDLE;
        };
    }

    private static List<MenuItem> menu(String rowId, Tunnel tunnel, TunnelState state) {
        var menu = new ArrayList<MenuItem>();
        menu.add(state.running() ? item("stop", rowId, "Stop", true) : item("start", rowId, "Start", true));
        if (state instanceof TunnelState.Failed) menu.add(item("stop", rowId, "Stop", true));
        menu.add(separator(1));
        menu.add(item("browse", rowId, "Open in Browser", browsable(tunnel, state)));
        menu.add(item("copy", rowId, "Copy Address", copyable(tunnel, state)));
        menu.add(separator(2));
        if (tunnel.quick()) menu.add(item("save", rowId, "Save as Tunnel…", true));
        else {
            menu.add(item("edit", rowId, "Edit…", true));
            menu.add(item("duplicate", rowId, "Duplicate", true));
            menu.add(item("delete", rowId, "Delete…", true));
        }
        return menu;
    }

    /** Whether Open in Browser has a page to open: a running local tunnel. */
    private static boolean browsable(Tunnel tunnel, TunnelState state) { return TunnelRows.browseUri(tunnel, state).isPresent(); }

    /** Whether Copy Address has an address: the port is bound, or saved. */
    private static boolean copyable(Tunnel tunnel, TunnelState state) { return state instanceof TunnelState.Up || tunnel.bindPort() != 0; }

    private static MenuItem item(String verb, String rowId, String label, boolean enabled) {
        return new MenuItem("menu." + verb, label, "", MENU + verb + ":" + rowId, enabled, false);
    }

    private static MenuItem separator(int index) { return new MenuItem("menu.separator." + index, "", "", "", false, true); }
}
