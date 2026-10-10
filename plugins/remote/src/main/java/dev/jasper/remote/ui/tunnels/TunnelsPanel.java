package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PanelHeader;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
 * visible and the live counters are in the row tooltip. One per window.
 *
 * <p>Rows are keyed by the tunnel's id. The switch shows only the confirmed state the plugin passes in (on while Up,
 * pending while Starting, an error while Failed) and never flips by itself; operating it, Enter or Space stops a running
 * tunnel and starts any other. A double click edits a saved tunnel or offers Save as Tunnel for a quick forward; Delete
 * and Backspace delete a saved one. Each row has its own context menu, whose actions name that row. The count and New
 * Tunnel action are the window's: {@link #onHeaderChanged} feeds {@code PanelHost.setHeader}. UI thread only; after
 * {@link #close()} nothing touches the view.
 */
public final class TunnelsPanel implements AutoCloseable {
    /** What the panel asks for; the plugin decides how. */
    public record Actions(Consumer<Tunnel> start, Consumer<Tunnel> stop, Consumer<Tunnel> edit, Consumer<Tunnel> duplicate,
                          Consumer<Tunnel> delete, Consumer<Tunnel> save, Consumer<Tunnel> browse, Consumer<Tunnel> copyAddress,
                          Runnable add) { }

    public static final String ROOT = "tunnels", HEADER = "tunnels.header", FILTER = "tunnels.filter", ROWS = "tunnels.rows",
        NO_MATCH = "tunnels.no-match", EMPTY = "tunnels.empty", EMPTY_NEW = "tunnels.empty.new", PROBLEM = "tunnels.problem",
        QUICK_HEADING = "heading.quick", PROBLEM_ROW = "problem", ACTIVATE = "activate", TOGGLE = "toggle", DELETE = "delete";
    /** A row's switch is this prefix and the tunnel's id; menu actions are {@code menu:<verb>:<row id>}. */
    static final String SWITCH = "switch:", MENU = "menu:", ROW = "tunnel:";

    private final Actions actions;
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
    private boolean closed;

    /** {@code icons} supplies the header action's artwork, which the window draws. */
    public TunnelsPanel(UiKit kit, Actions actions) {
        this(kit, actions, System::nanoTime, multiClickInterval());
    }

    /**
     * {@code nanos} and {@code multiClickNanos} (the desktop's double-click interval) tell a double click on a switch from
     * two separate operations: the old switch acted once per gesture.
     */
    TunnelsPanel(UiKit kit, Actions actions, LongSupplier nanos, long multiClickNanos) {
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

    /** The rows shown now, in order: tunnels by row id, the quick forwards' heading and the broken-file row. */
    List<String> rowIds() { return shown().stream().map(Row::id).toList(); }

    /** The row id of a tunnel. */
    static String rowId(UUID tunnel) { return ROW + tunnel; }

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
        var header = new Nodes.Header(HEADER, ControlState.READY, "Tunnels", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        if (saved.isEmpty() && quick.isEmpty()) {
            var body = new ArrayList<Nodes.Node>();
            // A broken file shows above the empty state; its message wraps at the panel's width.
            if (error.isPresent()) {
                body.add(new Nodes.Stack(PROBLEM + ".line", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                    severity(PROBLEM + ".icon", "error", "Error", Nodes.Severity.DANGER, Nodes.IconSize.CAPTION),
                    new Nodes.Text(PROBLEM + ".title", ControlState.READY, "tunnels.toml has errors", Nodes.TextRole.BODY_STRONG))));
                body.add(new Nodes.Text(PROBLEM, ControlState.READY, problemText(), Nodes.TextRole.BODY));
            }
            body.add(new Nodes.Empty(EMPTY, ControlState.READY,
                new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "tunnel", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Tunnels"), "No Tunnels",
                "Forward a local port to a remote host, or use Forward Port from an SSH pane.",
                List.of(new Nodes.Button(EMPTY_NEW, ControlState.READY, "New Tunnel", "", Nodes.ButtonKind.SECONDARY, EMPTY_NEW, ""))));
            return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.empty(),
                new Nodes.Stack(ROOT + ".body", ControlState.READY, Nodes.LayoutRole.VERTICAL, body), Optional.empty());
        }
        List<Row> rows = shown();
        if (selectedRow != null && rows.stream().noneMatch(row -> row.id().equals(selectedRow) && row.tunnel() != null)) selectedRow = null;
        var filter = new Nodes.Field(FILTER, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Filter tunnels", "Filter tunnels", "");
        var list = new Nodes.ItemList(ROWS, ControlState.READY, Nodes.ListKind.DOUBLE, rows.stream().map(this::item).toList(),
            selectedRow == null ? Set.of() : Set.of(selectedRow), ACTIVATE, List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(),
            TOGGLE, DELETE);
        Optional<Nodes.Node> footer = rows.stream().noneMatch(row -> row.tunnel() != null)
            ? Optional.of(new Nodes.Text(NO_MATCH, ControlState.READY, "No tunnels match", Nodes.TextRole.DESCRIPTION)) : Optional.empty();
        return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.of(filter), list, footer);
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
        // One subtitle line: the mapping, then what is happening or wrong (the old third line).
        var subtitle = new ArrayList<Nodes.Node>();
        subtitle.add(new Nodes.Text("row." + id + ".mapping", ControlState.READY, shortMapping, Nodes.TextRole.MONO_SMALL));
        String problem = "row." + id + ".problem";
        switch (state) {
            case TunnelState.Failed failed -> {
                subtitle.add(severity(problem + ".icon", "error", "Failed", Nodes.Severity.DANGER, Nodes.IconSize.CAPTION));
                subtitle.add(new Nodes.Text(problem, ControlState.READY, failed.reason(), Nodes.TextRole.DESCRIPTION));
            }
            case TunnelState.Starting starting -> subtitle.add(new Nodes.Text(problem, ControlState.READY, starting.stage(), Nodes.TextRole.DESCRIPTION));
            default -> {
                if (hostName.isEmpty()) subtitle.add(new Nodes.Text(problem, ControlState.READY, "Edit to choose a host", Nodes.TextRole.DESCRIPTION));
                else if (state instanceof TunnelState.Up up && up.counters().warning().isPresent()) {
                    subtitle.add(severity(problem + ".icon", "warning", "Warning", Nodes.Severity.WARNING, Nodes.IconSize.CAPTION));
                    subtitle.add(new Nodes.Text(problem, ControlState.READY, up.counters().warning().orElseThrow(), Nodes.TextRole.DESCRIPTION));
                }
            }
        }
        // Confirmed state only: on while Up, pending (operable) while Starting, the reason while Failed.
        var toggle = new Nodes.Toggle(SWITCH + tunnel.id(),
            new ControlState(true, true, false, state instanceof TunnelState.Failed failed ? failed.reason() : ""),
            state.running() ? "Stop tunnel" : "Start tunnel", state instanceof TunnelState.Up, SWITCH + tunnel.id(), true, "",
            state instanceof TunnelState.Starting);
        String detail = TunnelRows.detail(tunnel, state, hostName, clock.get());
        return new ListItem(id,
            Optional.of(new Nodes.StatusDot("row." + id + ".dot", ControlState.READY, dot(state))),
            List.of(new Nodes.Text("row." + id + ".name", ControlState.READY, title, Nodes.TextRole.BODY),
                new Nodes.Text("row." + id + ".via", ControlState.READY, hostName.map(host -> "via " + host).orElse("Host missing"), Nodes.TextRole.DESCRIPTION)),
            subtitle, List.of(toggle), false, Optional.of(menu(id, tunnel, state)), title + ", " + mapping + ", " + detail);
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
        menu.add(item("browse", rowId, "Open in Browser", TunnelRows.browseUri(tunnel, state).isPresent()));
        menu.add(item("copy", rowId, "Copy Address", state instanceof TunnelState.Up || tunnel.bindPort() != 0));
        menu.add(separator(2));
        if (tunnel.quick()) menu.add(item("save", rowId, "Save as Tunnel…", true));
        else {
            menu.add(item("edit", rowId, "Edit…", true));
            menu.add(item("duplicate", rowId, "Duplicate", true));
            menu.add(item("delete", rowId, "Delete…", true));
        }
        return menu;
    }

    private static MenuItem item(String verb, String rowId, String label, boolean enabled) {
        return new MenuItem("menu." + verb, label, "", MENU + verb + ":" + rowId, enabled, false);
    }

    private static MenuItem separator(int index) { return new MenuItem("menu.separator." + index, "", "", "", false, true); }
}
