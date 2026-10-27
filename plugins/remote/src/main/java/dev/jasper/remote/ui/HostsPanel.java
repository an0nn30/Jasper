package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PanelHeader;
import dev.jasper.sdk.ui.TreeItem;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/**
 * The "Sessions" rail panel on one host-rendered kit view: search, the WSL distributions the application detected,
 * grouped hosts, and the selected row's card with Connect (a host) or Open (a distribution). One for each window.
 *
 * <p>Rows are keyed by stable ids (a host's UUID, a distribution's profile id, a group's name). A single click toggles
 * a group; Enter or a double click connects to a host, opens a distribution or toggles a group. A host's star reports
 * only its own action (favourite on or off), never selecting or activating the row. Each row has its own context menu,
 * whose actions name that row, so a menu acts on the row it opened on whatever is selected. The count and the New and
 * Import actions are the window's: {@link #onHeaderChanged} feeds {@code PanelHost.setHeader}. UI thread only; after
 * {@link #close()} nothing touches the view.
 *
 * <p>Under {@link Layout#CLASSIC} (Enterprise presentation spec 14.4) the same rows are a Name, Host and Port column tree,
 * whose disclosure collapses a group, over the selected row's Properties and Edit… and Connect; the header detail is the
 * count.
 *
 * <p>In a window of its own or docked at the bottom ({@link #wide(boolean)}) the panel lays its list beside the selected
 * row's detail: under {@link Layout#ROWS} the host's or distribution's fields and its actions, under
 * {@link Layout#CLASSIC} the tree beside its Properties. With nothing listed the wide layout is the docked one.
 */
public final class HostsPanel implements AutoCloseable {
    public record Actions(Consumer<RemoteHost> connect, Consumer<RemoteHost> connectSplit, Consumer<Optional<RemoteHost>> edit, Consumer<RemoteHost> duplicate,
                          Consumer<RemoteHost> delete, BiConsumer<RemoteHost, Boolean> favorite, Runnable importConfig) { }

    public static final String ROOT = "sessions", HEADER = "sessions.header", SEARCH = "sessions.search", ROWS = "sessions.rows",
        FOOTER = "sessions.footer", HINT = "sessions.hint", CARD = "card", CARD_TILE = "card.tile", CARD_NAME = "card.name",
        CARD_ADDRESS = "card.address", CARD_INFO = "card.info", CARD_JUMP = "card.jump", CARD_CREDENTIAL = "card.credential",
        CARD_CONNECTED = "card.connected", CARD_LOCKED = "card.locked", EDIT = "card.edit", CONNECT = "card.connect", ACTIVATE = "activate";
    /** The wide layout's nodes: the split, its list and detail columns, and the detail's content, fields and actions. */
    public static final String COLUMNS = "sessions.columns", LIST_COLUMN = "sessions.list", DETAIL_COLUMN = "sessions.detail",
        DETAIL_CONTENT = "sessions.detail.content", DETAIL_EMPTY = "sessions.detail.empty", DETAIL_FIELDS = "sessions.detail.fields",
        DETAIL_ACTIONS = "sessions.detail.actions", SPLIT_BUTTON = "sessions.detail.split", BROWSE_BUTTON = "sessions.detail.browse";
    /** Star actions are this prefix and the host's id; menu actions are {@code menu:<verb>:<row id>}. */
    static final String FAVORITE = "favorite:", MENU = "menu:";
    private static final String WSL_GROUP = "group.wsl", ERROR = "error";

    private final Actions actions;
    /** Which composition this panel takes, fixed at construction (spec 3.6). */
    private final Layout layout;
    private Consumer<Optional<String>> selectionChanged = row -> { };
    private final UiView view;
    private final Set<String> collapsed = new HashSet<>();
    private boolean wslCollapsed;
    private final List<Runnable> collapseListeners = new ArrayList<>();
    private List<RemoteHost> hosts = List.of();
    private Optional<String> error = Optional.empty();
    private Function<RemoteHost, String> credentialLabels = host -> "";
    private List<HostRows.Row> rows = List.of();
    private String query = "", selectedRow, defaultGroup = HostRows.OTHER;
    private Runnable renameDefaultGroup = () -> {};
    private Consumer<RemoteHost> activate;
    private Consumer<RemoteHost> browseFiles;
    private Function<RemoteHost, String> metadata = host -> "";
    private ToIntFunction<RemoteHost> sessionCount = host -> 0;
    private List<ShellProfileInfo> distros = List.of();
    private ToIntFunction<ShellProfileInfo> distroSessions = profile -> 0;
    private Consumer<ShellProfileInfo> openDistro = profile -> { };
    private Consumer<ShellProfileInfo> splitDistro = profile -> { };
    private Consumer<PanelHeader> headerChanged;
    private int headerCount = -1;
    private boolean closed;
    /** Whether the panel is asked for its wide layout (a window of its own, or docked at the bottom). */
    private boolean wide;
    private final LongSupplier clock;
    private final long multiClickNanos;
    private String lastStar;
    private long lastStarAt;

    /** {@code icons} supplies the header actions' artwork, which the window draws. */
    public HostsPanel(UiKit kit, Actions actions) {
        this(kit, actions, System::nanoTime, multiClickNanos());
    }

    /** The panel in {@code layout}'s composition. */
    public HostsPanel(UiKit kit, Actions actions, Layout layout) {
        this(kit, actions, System::nanoTime, multiClickNanos(), layout);
    }

    /**
     * {@code clock} (nanoseconds) and {@code multiClickNanos} (the desktop's double-click interval) tell a double click on a
     * star from two separate clicks.
     */
    HostsPanel(UiKit kit, Actions actions, LongSupplier clock, long multiClickNanos) { this(kit, actions, clock, multiClickNanos, Layout.ROWS); }

    HostsPanel(UiKit kit, Actions actions, LongSupplier clock, long multiClickNanos, Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        this.clock = Objects.requireNonNull(clock);
        if (multiClickNanos < 0) throw new IllegalArgumentException("multiClickNanos must not be negative");
        this.multiClickNanos = multiClickNanos;
        this.actions = Objects.requireNonNull(actions);
        this.activate = actions.connect();
        rebuildRows();
        view = kit.create(new ViewSpec(node()), this::handle);
    }

    public UiView view() { return view; }

    /** The rows shown now, in order (the panel's model). */
    public List<HostRows.Row> rows() { return rows; }

    /** The selected row, if any. */
    public Optional<HostRows.Row> selectedRow() { return rows.stream().filter(row -> rowId(row).equals(selectedRow)).findFirst(); }

    /** The row's stable id in the list. */
    public static String rowId(HostRows.Row row) {
        return switch (row) {
            case HostRows.Host host -> "host:" + host.host().id();
            case HostRows.Distro distro -> "distro:" + distro.profile().id();
            case HostRows.Group group -> group.wsl() ? WSL_GROUP : "group:" + group.name();
            case HostRows.Error ignored -> ERROR;
        };
    }

    /** The panel became visible: as when the old list was shown, the first host (or distribution) is selected. */
    public void shown() { if (closed) return; selectFirstHost(); render(); }

    /** Focus an explicit opening in a visible window; a panel that is not showing yet keeps focus where it is. */
    public void focusWhenOpened() { if (!closed) view.focus(ROWS); }

    /**
     * Asks for the wide layout, the list beside the selected row's detail, or the docked one. Nothing changes while the
     * value is the same or after {@link #close()}. With no host and no distribution listed the panel keeps its docked
     * composition, whose empty state fills the window.
     */
    public void wide(boolean value) {
        if (closed || value == wide) return;
        wide = value;
        render();
    }

    /** Whether the wide layout is asked for, as {@link #wide(boolean)} last set it. */
    public boolean wide() { return wide; }

    public void setDefaultGroup(String name) { defaultGroup = name; rebuild(); }
    public void onRenameDefaultGroup(Runnable rename) { renameDefaultGroup = rename; }
    public void onBrowseFiles(Consumer<RemoteHost> action) { browseFiles = action; rebuild(); }
    public void onActivate(Consumer<RemoteHost> action) { activate = action; }
    /** Supplies this window's app-owned header immediately and whenever the saved-session count changes. */
    public void onHeaderChanged(Consumer<PanelHeader> listener) {
        headerChanged = Objects.requireNonNull(listener); headerCount = -1; refreshHeader();
    }
    public void setHostDetails(Function<RemoteHost, String> details, ToIntFunction<RemoteHost> sessions) {
        metadata = details; sessionCount = sessions; rebuild();
    }

    /** What Open (double-click, Enter, the card, the first menu item) and Open in split do for a WSL distribution. */
    public void onDistros(Consumer<ShellProfileInfo> open, Consumer<ShellProfileInfo> openSplit) { openDistro = open; splitDistro = openSplit; }

    /** The WSL group's rows and their running-pane counts; other kinds of profile are ignored, and none hides the group. */
    public void setDistros(List<ShellProfileInfo> profiles, ToIntFunction<ShellProfileInfo> sessions) {
        distros = profiles.stream().filter(profile -> profile.kind() == ShellProfileInfo.Kind.WSL).toList();
        distroSessions = sessions;
        rebuild();
    }

    public void setHosts(List<RemoteHost> hosts, Optional<String> error) { this.hosts = List.copyOf(hosts); this.error = error; rebuild(); }
    public void setCredentialLabels(Function<RemoteHost, String> labels) { credentialLabels = labels; render(); }
    /** The collapsed host groups, by name; the WSL group is never among them (see {@link #wslCollapsed()}). */
    public Set<String> collapsed() { return Set.copyOf(collapsed); }
    public void setCollapsed(Set<String> groups) { collapsed.clear(); collapsed.addAll(groups); rebuild(); }
    /** Whether the WSL group is collapsed, kept apart from the host groups so no host group name can share it. */
    public boolean wslCollapsed() { return wslCollapsed; }
    public void setWslCollapsed(boolean value) { wslCollapsed = value; rebuild(); }
    public Subscription onCollapsedChanged(Runnable listener) { collapseListeners.add(listener); return () -> collapseListeners.remove(listener); }

    public void select(UUID hostId) {
        rows.stream().filter(row -> row instanceof HostRows.Host host && host.host().id().equals(hostId)).findFirst()
            .ifPresent(row -> { selectedRow = rowId(row); render(); });
    }

    void selectDistro(String profileId) {
        rows.stream().filter(row -> row instanceof HostRows.Distro distro && distro.profile().id().equals(profileId)).findFirst()
            .ifPresent(row -> { selectedRow = rowId(row); render(); });
    }

    Optional<RemoteHost> selectedHost() { return selectedRow().filter(HostRows.Host.class::isInstance).map(row -> ((HostRows.Host) row).host()); }

    Optional<ShellProfileInfo> selectedDistro() { return selectedRow().filter(HostRows.Distro.class::isInstance).map(row -> ((HostRows.Distro) row).profile()); }

    private void selectFirstHost() {
        Optional<HostRows.Row> first = rows.stream().filter(row -> row instanceof HostRows.Host || row instanceof HostRows.Distro).findFirst();
        // When all folders are collapsed, Enter can expand the first folder. Never select an error.
        if (first.isEmpty()) first = rows.stream().filter(HostRows.Group.class::isInstance).findFirst();
        selectedRow = first.map(HostsPanel::rowId).orElse(null);
    }

    private void handle(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(SEARCH) -> {
                String before = selectedRow;
                query = edited.text(); rebuild();
                // A filter that hides the selected row selects another: that is the user's selection too.
                if (!Objects.equals(before, selectedRow)) selectionChanged.accept(Optional.ofNullable(selectedRow));
            }
            case UiEvent.Selected selected when selected.id().equals(ROWS) -> {
                selectedRow = selected.ids().stream().findFirst().filter(id -> rows.stream().anyMatch(row -> rowId(row).equals(id))).orElse(null);
                render();
                selectionChanged.accept(Optional.ofNullable(selectedRow));
            }
            case UiEvent.Clicked clicked when clicked.id().equals(ROWS) -> rowById(clicked.itemId()).ifPresent(row -> {
                if (layout == Layout.ROWS && row instanceof HostRows.Group group) toggle(group);
            });
            case UiEvent.Expanded expanded when expanded.id().equals(ROWS) -> rowById(expanded.itemId()).ifPresent(row -> {
                if (row instanceof HostRows.Group group && expanded.expanded() == collapsedGroup(group)) toggle(group);
            });
            case UiEvent.Action action -> act(action.id());
            default -> { }
        }
    }

    /** The desktop's double-click interval, 500 ms where it has none. */
    private static long multiClickNanos() {
        Object interval = null;
        try { interval = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval"); } catch (RuntimeException | java.awt.AWTError headless) { }
        return java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(interval instanceof Integer millis ? millis : 500);
    }

    private Optional<HostRows.Row> rowById(String id) { return rows.stream().filter(row -> rowId(row).equals(id)).findFirst(); }

    private Optional<RemoteHost> host(String rowId) { return rowById(rowId).filter(HostRows.Host.class::isInstance).map(row -> ((HostRows.Host) row).host()); }

    private Optional<ShellProfileInfo> distro(String rowId) { return rowById(rowId).filter(HostRows.Distro.class::isInstance).map(row -> ((HostRows.Distro) row).profile()); }

    private void act(String id) {
        if (id.equals(ACTIVATE)) { selectedRow().ifPresent(this::activate); return; }
        if (id.equals(CONNECT)) { connectSelected(); return; }
        if (id.equals(EDIT)) { selectedHost().ifPresent(host -> actions.edit().accept(Optional.of(host))); return; }
        if (id.startsWith(FAVORITE)) {
            String hostId = id.substring(FAVORITE.length());
            // The star reports each click; the second click of a double click is not a second toggle, as the old list
            // acted only on a single click.
            long now = clock.getAsLong();
            boolean second = hostId.equals(lastStar) && now - lastStarAt < multiClickNanos;
            lastStar = hostId; lastStarAt = now;
            if (second) return;
            hosts.stream().filter(host -> host.id().toString().equals(hostId)).findFirst().ifPresent(host -> actions.favorite().accept(host, !host.favorite()));
            return;
        }
        if (!id.startsWith(MENU)) return;
        String rest = id.substring(MENU.length());
        int colon = rest.indexOf(':');
        if (colon < 0) return;
        String verb = rest.substring(0, colon), row = rest.substring(colon + 1);
        switch (verb) {
            case "connect" -> host(row).ifPresent(actions.connect());
            case "split" -> host(row).ifPresent(actions.connectSplit());
            case "browse" -> host(row).ifPresent(host -> { if (browseFiles != null) browseFiles.accept(host); });
            case "edit" -> host(row).ifPresent(host -> actions.edit().accept(Optional.of(host)));
            case "duplicate" -> host(row).ifPresent(actions.duplicate());
            case "delete" -> host(row).ifPresent(actions.delete());
            case "favorite" -> host(row).ifPresent(host -> actions.favorite().accept(host, !host.favorite()));
            case "rename" -> renameDefaultGroup.run();
            case "open" -> distro(row).ifPresent(openDistro);
            case "open-split" -> distro(row).ifPresent(splitDistro);
            default -> { }
        }
    }

    private void connectSelected() {
        Optional<ShellProfileInfo> distro = selectedDistro();
        if (distro.isPresent()) { openDistro.accept(distro.get()); return; }
        selectedHost().ifPresent(host -> {
            if (sessionCount.applyAsInt(host) > 0) activate.accept(host);
            else actions.connect().accept(host);
        });
    }

    /** Double-click or Enter on a row: connect to a host, open a distribution, toggle a group. */
    private void activate(HostRows.Row row) {
        switch (row) {
            case HostRows.Host host -> activate.accept(host.host());
            case HostRows.Group group -> toggle(group);
            case HostRows.Distro distro -> openDistro.accept(distro.profile());
            case HostRows.Error ignored -> { }
        }
    }

    private void toggle(HostRows.Group group) {
        if (group.wsl()) wslCollapsed = !wslCollapsed;
        else if (!collapsed.remove(group.name())) collapsed.add(group.name());
        collapseListeners.forEach(Runnable::run);
        rebuild();
    }

    private void rebuildRows() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        List<RemoteHost> matching = hosts.stream().filter(host -> HostRows.matches(host, needle)
            || (host.group().isEmpty() && defaultGroup.toLowerCase(Locale.ROOT).contains(needle))
            || metadata.apply(host).toLowerCase(Locale.ROOT).contains(needle)).toList();
        List<HostRows.Distro> distroRows = distros.stream().filter(profile -> HostRows.matches(profile, needle))
            .map(profile -> new HostRows.Distro(profile, distroSessions.applyAsInt(profile))).toList();
        var next = new ArrayList<HostRows.Row>();
        error.ifPresent(message -> next.add(new HostRows.Error(message)));
        // The classic tree keeps every row and shows the collapsed groups through its expanded ids.
        boolean flat = layout == Layout.CLASSIC;
        next.addAll(HostRows.rows(matching, "", flat || !needle.isEmpty() ? Set.of() : collapsed, defaultGroup, distroRows, !flat && needle.isEmpty() && wslCollapsed));
        rows = List.copyOf(next);
        // A refresh keeps the selected row (a host, distribution or group by identity); otherwise the first host.
        if (selectedRow == null || rows.stream().noneMatch(row -> rowId(row).equals(selectedRow))) selectFirstHost();
    }

    private void rebuild() {
        rebuildRows();
        render();
        refreshHeader();
    }

    private void render() { if (!closed && view != null) view.update(new ViewSpec(node())); }

    private void refreshHeader() {
        if (headerChanged == null || closed) return;
        int count = hosts.size() + distros.size();
        if (count == headerCount) return;
        headerCount = count;
        headerChanged.accept(new PanelHeader(layout == Layout.CLASSIC ? String.valueOf(count) : "", false, List.of(
            new PanelHeader.Action("New session", "add", () -> { if (!closed) actions.edit().accept(Optional.empty()); }),
            new PanelHeader.Action("Import sessions", "import", () -> { if (!closed) actions.importConfig().run(); }))));
    }

    private static ControlState state(boolean visible) { return new ControlState(visible, true, false, ""); }

    private Nodes.Panel node() {
        // With nothing listed the wide layout is the docked one, whose empty state fills the window.
        boolean columns = wide && !(hosts.isEmpty() && distros.isEmpty());
        if (layout == Layout.CLASSIC) return columns ? classicWideNode() : classicNode();
        return columns ? rowsWideNode() : rowsNode();
    }

    private Nodes.Header rowsHeader() {
        return new Nodes.Header(HEADER, ControlState.READY, "Sessions", OptionalInt.of(hosts.size() + distros.size()), Optional.empty(), Optional.empty(), List.of());
    }

    private Nodes.Field rowsSearch() {
        return new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Search sessions", "Search sessions", "");
    }

    private Nodes.ItemList rowsList() {
        var items = rows.stream().map(this::item).toList();
        return new Nodes.ItemList(ROWS, ControlState.READY, Nodes.ListKind.SINGLE, items,
            selectedRow == null ? Set.of() : Set.of(selectedRow), ACTIVATE, List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "", "");
    }

    /** What the list says when it has no row to show, the same in every composition. */
    private Optional<Nodes.Text> hint() {
        boolean searching = !query.strip().isEmpty();
        // With a WSL group shown, the hint sits under it and speaks of SSH hosts only; without WSL the panel holds hosts alone.
        if (!(hosts.isEmpty() || (searching && rows.size() == (error.isPresent() ? 1 : 0)))) return Optional.empty();
        String hint = hosts.isEmpty() ? (distros.isEmpty() ? "No hosts yet" : "No SSH hosts yet") + " — Add or Import from ~/.ssh/config"
            : distros.isEmpty() ? "No hosts match" : "No sessions match";
        return Optional.of(new Nodes.Text(HINT, ControlState.READY, hint, Nodes.TextRole.DESCRIPTION));
    }

    private Nodes.Panel rowsNode() {
        var footer = new ArrayList<Nodes.Node>();
        hint().ifPresent(footer::add);
        card().ifPresent(footer::add);
        Optional<Nodes.Node> bottom = footer.isEmpty() ? Optional.empty()
            : Optional.of(new Nodes.Stack(FOOTER, ControlState.READY, Nodes.LayoutRole.DETAIL, footer));
        return new Nodes.Panel(ROOT, ControlState.READY, rowsHeader(), Optional.of(rowsSearch()), rowsList(), bottom);
    }

    private ListItem item(HostRows.Row row) {
        String id = rowId(row);
        return switch (row) {
            case HostRows.Error failure -> new ListItem(id, Optional.empty(),
                List.of(new Nodes.Text("row." + id + ".message", ControlState.READY, failure.message(), Nodes.TextRole.BODY_STRONG)), List.of(), List.of(), true);
            case HostRows.Group group -> {
                boolean renamable = !group.wsl() && group.name().equals(defaultGroup);
                yield new ListItem(id,
                    Optional.of(new Nodes.Icon("row." + id + ".state", ControlState.READY, group.collapsed() ? "chevron.right" : "chevron.down",
                        Nodes.Hue.NONE, Nodes.IconSize.CONTROL, group.collapsed() ? "Collapsed group" : "Expanded group")),
                    List.of(new Nodes.Text("row." + id + ".name", ControlState.READY, group.name(), Nodes.TextRole.BODY_STRONG),
                        new Nodes.Text("row." + id + ".count", ControlState.READY, String.valueOf(group.count()), Nodes.TextRole.COUNT)),
                    List.of(), List.of(), false,
                    renamable ? Optional.of(List.of(menu("rename", id, "Rename group…"))) : Optional.empty(),
                    renamable ? "Right-click to rename this group" : "");
            }
            case HostRows.Distro distro -> {
                var trailing = new ArrayList<Nodes.Node>();
                String badge = distro.profile().detail().orElse("");
                if (!badge.isEmpty()) trailing.add(new Nodes.Chip("row." + id + ".badge", ControlState.READY, badge, Nodes.Hue.NEUTRAL));
                int sessions = distro.sessions();
                if (sessions > 0) trailing.add(new Nodes.StatusDot("row." + id + ".dot", ControlState.READY, Nodes.StatusKind.SUCCESS));
                if (sessions > 1) trailing.add(new Nodes.Text("row." + id + ".sessions", ControlState.READY, String.valueOf(sessions), Nodes.TextRole.COUNT));
                yield new ListItem(id,
                    Optional.of(new Nodes.Icon("row." + id + ".icon", ControlState.READY, "terminal", Nodes.Hue.NONE, Nodes.IconSize.CONTROL, "WSL distribution")),
                    List.of(new Nodes.Text("row." + id + ".name", ControlState.READY, distro.profile().name(), Nodes.TextRole.BODY)),
                    List.of(), trailing, false,
                    Optional.of(List.of(menu("open", id, "Open in new tab"), menu("open-split", id, "Open in split"))),
                    distro.profile().name() + ", " + (badge.isEmpty() ? HostRows.WSL : badge) + ", " + sessions + (sessions == 1 ? " session" : " sessions"));
            }
            case HostRows.Host entry -> {
                RemoteHost host = entry.host();
                int sessions = sessionCount.applyAsInt(host);
                String facts = metadata.apply(host);
                String via = via(host);
                var menu = hostMenu(id, host);
                var star = new Nodes.Button("row." + id + ".star", ControlState.READY, "", host.favorite() ? "star.filled" : "star", Nodes.ButtonKind.GHOST,
                    FAVORITE + host.id(), host.favorite() ? "Remove from favorites" : "Add to favorites", host.favorite() ? Nodes.ButtonPresentation.EMBEDDED : Nodes.ButtonPresentation.CONTEXTUAL_EMBEDDED,
                    host.favorite() ? Optional.of(Nodes.Severity.WARNING) : Optional.empty());
                yield new ListItem(id,
                    Optional.of(new Nodes.StatusDot("row." + id + ".dot", ControlState.READY, sessions > 0 ? Nodes.StatusKind.SUCCESS : Nodes.StatusKind.IDLE)),
                    List.of(new Nodes.Text("row." + id + ".name", ControlState.READY, host.name(), Nodes.TextRole.BODY),
                        new Nodes.Text("row." + id + ".address", ControlState.READY, host.hostname(), Nodes.TextRole.DESCRIPTION)),
                    List.of(), List.of(star), false, Optional.of(List.copyOf(menu)),
                    host.name() + ", " + host.label() + (facts.isBlank() ? "" : ", " + facts) + (via == null ? "" : ", via " + via)
                        + ", " + sessions + (sessions == 1 ? " session" : " sessions"));
            }
        };
    }

    /** A host row's menu, the same in both compositions. */
    private List<MenuItem> hostMenu(String id, RemoteHost host) {
        var menu = new ArrayList<MenuItem>(List.of(menu("connect", id, "Connect in new tab"), menu("split", id, "Connect in split")));
        if (browseFiles != null) menu.add(menu("browse", id, "Browse files"));
        menu.addAll(List.of(menu("edit", id, "Edit…"), menu("duplicate", id, "Duplicate"), menu("delete", id, "Delete…"),
            menu("favorite", id, host.favorite() ? "Remove from favorites" : "Add to favorites")));
        if (host.group().isEmpty() && defaultGroup.equals(HostRows.OTHER) && hosts.stream().allMatch(h -> h.group().isEmpty()))
            menu.add(menu("rename", id, "Rename group…"));
        return menu;
    }

    private static MenuItem menu(String verb, String rowId, String label) {
        return new MenuItem("menu." + verb, label, "", MENU + verb + ":" + rowId, true, false);
    }

    private String via(RemoteHost host) {
        return host.jump().flatMap(id -> hosts.stream().filter(other -> other.id().equals(id)).findFirst()).map(RemoteHost::name).orElse(null);
    }

    /** The selected host's or distribution's persistent detail, keyed by identity. */
    private Optional<Nodes.Node> card() {
        Optional<ShellProfileInfo> distro = selectedDistro();
        Optional<RemoteHost> host = selectedHost();
        if (distro.isEmpty() && host.isEmpty()) return Optional.empty();
        String name, address, info, jump = "", credential = "", button;
        boolean connected = false, terminal;
        if (distro.isPresent()) {
            var profile = distro.get();
            name = profile.name(); address = profile.detail().orElse(HostRows.WSL); info = "wsl.exe -d " + profile.name();
            button = "Open"; terminal = true;
        } else {
            var h = host.get();
            connected = sessionCount.applyAsInt(h) > 0;
            name = h.name(); address = h.label(); info = metadata.apply(h);
            String via = via(h);
            jump = via == null ? "" : "via " + via;
            credential = connected ? "Connected" : credentialLabels.apply(h);
            button = connected ? "Open Shell" : "Connect"; terminal = connected;
        }
        boolean isHost = host.isPresent();
        var details = new Nodes.Stack("card.details", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text(CARD_NAME, ControlState.READY, name, Nodes.TextRole.PANEL_TITLE),
            new Nodes.Text(CARD_ADDRESS, ControlState.READY, address, Nodes.TextRole.MONO_CAPTION),
            // A host's OS and address facts stay in its row tooltip, as the old card kept them in a tooltip.
            new Nodes.Text(CARD_INFO, state(!isHost), info, Nodes.TextRole.DESCRIPTION),
            new Nodes.Text(CARD_JUMP, state(false), jump, Nodes.TextRole.DESCRIPTION)));
        var identity = new Nodes.Stack("card.identity", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Icon(CARD_TILE, ControlState.READY, "server", Nodes.Hue.NEUTRAL, Nodes.IconSize.PROMPT, isHost ? "SSH host" : "WSL distribution"), details));
        var status = new ArrayList<Nodes.Node>();
        if (isHost && connected) status.add(new Nodes.StatusDot(CARD_CONNECTED, ControlState.READY, Nodes.StatusKind.SUCCESS));
        else if (isHost && credential.equals("Vault locked")) status.add(new Nodes.Icon(CARD_LOCKED, ControlState.READY, "lock", Nodes.Hue.NONE, Nodes.IconSize.CONTROL, "Vault locked"));
        status.add(new Nodes.Text(CARD_CREDENTIAL, state(isHost), credential, Nodes.TextRole.CAPTION));
        status.add(new Nodes.Button(EDIT, state(isHost), "", "edit", Nodes.ButtonKind.GHOST, EDIT, "Edit session"));
        status.add(new Nodes.Button(CONNECT, ControlState.READY, button, terminal ? "terminal" : "", Nodes.ButtonKind.PRIMARY, CONNECT, ""));
        // NAVIGATION reserves fixed actions, lets the credential caption elide and wraps at genuinely narrow widths.
        var actionsRow = new Nodes.Stack("card.actions", ControlState.READY, Nodes.LayoutRole.NAVIGATION, status);
        return Optional.of(new Nodes.Stack(CARD, ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(identity, actionsRow)));
    }

    // ---- the wide composition: the list beside the selected row's detail

    /** A column's header, which the column does not draw; its count still hides the search while nothing is listed. */
    private static Nodes.Header hiddenHeader(String id, String title, OptionalInt count) {
        return new Nodes.Header(id, state(false), title, count, Optional.empty(), Optional.empty(), List.of());
    }

    /** The hint under a list column's rows, in the same footer stack the docked layout puts it in. */
    private Optional<Nodes.Node> hintFooter(Nodes.LayoutRole role) {
        return hint().map(text -> (Nodes.Node) new Nodes.Stack(FOOTER, ControlState.READY, role, List.of(text)));
    }

    private Nodes.Panel rowsWideNode() {
        var listColumn = new Nodes.Panel(LIST_COLUMN, ControlState.READY, hiddenHeader(LIST_COLUMN + ".header", "Sessions", OptionalInt.of(hosts.size() + distros.size())),
            Optional.of(rowsSearch()), rowsList(), hintFooter(Nodes.LayoutRole.DETAIL));
        var detailColumn = new Nodes.Panel(DETAIL_COLUMN, ControlState.READY, hiddenHeader(DETAIL_COLUMN + ".header", "Details", OptionalInt.empty()),
            Optional.empty(), detail(), Optional.empty());
        return new Nodes.Panel(ROOT, ControlState.READY, rowsHeader(), Optional.empty(),
            new Nodes.Split(COLUMNS, ControlState.READY, Nodes.SplitKind.SIDEBAR_DETAIL, List.of(listColumn, detailColumn)), Optional.empty());
    }

    /** The selected host's or distribution's heading, fields and actions; the empty state while no session is selected. */
    private Nodes.Node detail() {
        Optional<RemoteHost> host = selectedHost();
        if (host.isPresent()) return hostDetail(host.get());
        Optional<ShellProfileInfo> distro = selectedDistro();
        if (distro.isPresent()) return distroDetail(distro.get());
        return new Nodes.Empty(DETAIL_EMPTY, ControlState.READY,
            new Nodes.Icon(DETAIL_EMPTY + ".icon", ControlState.READY, "server", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Sessions"),
            "No Session Selected", "Select a host or a WSL distribution to see its details.", List.of());
    }

    private Nodes.Node hostDetail(RemoteHost host) {
        int sessions = sessionCount.applyAsInt(host);
        boolean connected = sessions > 0;
        String credential = credentialLabels.apply(host), via = via(host), system = metadata.apply(host);
        var fields = new ArrayList<Nodes.Node>();
        fields.add(field("host", "Host", host.hostname(), Nodes.TextRole.MONO_SMALL, Optional.empty()));
        fields.add(field("user", "User", host.username(), Nodes.TextRole.MONO_SMALL, Optional.empty()));
        fields.add(field("port", "Port", String.valueOf(host.port()), Nodes.TextRole.MONO_SMALL, Optional.empty()));
        fields.add(field("credentials", "Credentials", credential, Nodes.TextRole.BODY,
            credential.equals("Vault locked") ? Optional.of(Nodes.Severity.WARNING) : Optional.empty()));
        if (via != null) fields.add(field("jump", "Jump host", via, Nodes.TextRole.BODY, Optional.empty()));
        fields.add(field("group", "Group", host.group().isEmpty() ? defaultGroup : host.group(), Nodes.TextRole.BODY, Optional.empty()));
        if (!system.isBlank()) fields.add(field("system", "System", system, Nodes.TextRole.BODY, Optional.empty()));
        fields.add(field("sessions", "Sessions", sessionsText(sessions, "Not connected"), Nodes.TextRole.BODY, Optional.empty()));
        var buttons = new ArrayList<Nodes.Node>();
        buttons.add(new Nodes.Button(SPLIT_BUTTON, ControlState.READY, "Connect in Split", "", Nodes.ButtonKind.SECONDARY, MENU + "split:" + selectedRow, ""));
        if (browseFiles != null)
            buttons.add(new Nodes.Button(BROWSE_BUTTON, ControlState.READY, "Browse Files", "", Nodes.ButtonKind.SECONDARY, MENU + "browse:" + selectedRow, ""));
        return detailContent(heading("server", "SSH host", host.name(), host.label(), true, connected ? "Open Shell" : "Connect", connected), fields, buttons);
    }

    private Nodes.Node distroDetail(ShellProfileInfo profile) {
        var fields = List.<Nodes.Node>of(
            field("distribution", "Distribution", profile.name(), Nodes.TextRole.BODY, Optional.empty()),
            field("version", "Version", profile.detail().orElse(HostRows.WSL), Nodes.TextRole.BODY, Optional.empty()),
            field("command", "Command", "wsl.exe -d " + profile.name(), Nodes.TextRole.MONO_SMALL, Optional.empty()),
            field("sessions", "Sessions", sessionsText(distroSessions.applyAsInt(profile), "Not open"), Nodes.TextRole.BODY, Optional.empty()));
        var buttons = List.<Nodes.Node>of(
            new Nodes.Button(SPLIT_BUTTON, ControlState.READY, "Open in Split", "", Nodes.ButtonKind.SECONDARY, MENU + "open-split:" + selectedRow, ""));
        return detailContent(heading("terminal", "WSL distribution", profile.name(), profile.detail().orElse(HostRows.WSL), false, "Open", true), fields, buttons);
    }

    private static Nodes.Node detailContent(Nodes.Node heading, List<Nodes.Node> fields, List<Nodes.Node> buttons) {
        return new Nodes.Stack(DETAIL_CONTENT, ControlState.READY, Nodes.LayoutRole.DETAIL, List.of(heading,
            new Nodes.Card(DETAIL_FIELDS, ControlState.READY, Nodes.CardKind.FORM, fields),
            new Nodes.Stack(DETAIL_ACTIONS, ControlState.READY, Nodes.LayoutRole.ACTIONS, buttons)));
    }

    /** The row's tile, its name and address, Edit… for a host and the primary action: the card's controls, with the card's ids. */
    private static Nodes.Node heading(String icon, String iconName, String name, String address, boolean editable, String connect, boolean terminal) {
        var children = new ArrayList<Nodes.Node>();
        children.add(new Nodes.Icon(CARD_TILE, ControlState.READY, icon, Nodes.Hue.NEUTRAL, Nodes.IconSize.PROMPT, iconName));
        children.add(new Nodes.Stack("sessions.detail.identity", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text(CARD_NAME, ControlState.READY, name, Nodes.TextRole.DETAIL_TITLE),
            new Nodes.Text(CARD_ADDRESS, ControlState.READY, address, Nodes.TextRole.MONO_CAPTION))));
        if (editable) children.add(new Nodes.Button(EDIT, ControlState.READY, "Edit…", "", Nodes.ButtonKind.SECONDARY, EDIT, "Edit session"));
        children.add(new Nodes.Button(CONNECT, ControlState.READY, connect, terminal ? "terminal" : "", Nodes.ButtonKind.PRIMARY, CONNECT, ""));
        return new Nodes.Stack("sessions.detail.heading", ControlState.READY, Nodes.LayoutRole.NAVIGATION, children);
    }

    /** One captioned value of the detail card, as the Vault manager's detail rows. */
    private static Nodes.Node field(String key, String caption, String value, Nodes.TextRole role, Optional<Nodes.Severity> severity) {
        String id = DETAIL_COLUMN + "." + key;
        return new Nodes.Stack(id + ".row", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text(id + ".label", ControlState.READY, caption, Nodes.TextRole.CAPTION),
            new Nodes.Text(id, ControlState.READY, value, role, severity, OptionalInt.empty())));
    }

    private static String sessionsText(int sessions, String none) { return sessions == 0 ? none : sessions == 1 ? "1 session" : sessions + " sessions"; }

    // ---- the classic composition (Enterprise presentation spec 14.4)

    /** The classic tree's columns: the title, then a host's address and port (its trailing values). */
    static final List<Column> TREE_COLUMNS = List.of(new Column("name", "Name", Nodes.TextRole.BODY, false, 3, Optional.empty()),
        new Column("host", "Host", Nodes.TextRole.MONO, false, 2.3, Optional.empty()), new Column("port", "Port", Nodes.TextRole.MONO, true, 1, Optional.empty()));
    public static final String PROPERTIES = "sessions.properties", PROPERTY_ACTIONS = "sessions.properties.actions";

    /** Selects a row by its id ({@link #rowId}), as the window's toolbar view does; an unknown row changes nothing. */
    public void selectRow(String rowId) {
        if (closed || rowId.equals(selectedRow) || rows.stream().noneMatch(row -> rowId(row).equals(rowId))) return;
        selectedRow = rowId; render();
    }

    /** Hears every selection the user makes in the panel, including one a filter makes, as the row id, or empty for none. */
    public void onSelectionChanged(Consumer<Optional<String>> listener) { selectionChanged = Objects.requireNonNull(listener); }

    private boolean collapsedGroup(HostRows.Group group) { return group.wsl() ? wslCollapsed : collapsed.contains(group.name()); }

    private Nodes.Header classicHeader() {
        // The count is the window header's detail (refreshHeader), which the strip draws as "(n)": not a second count here.
        return new Nodes.Header(HEADER, ControlState.READY, "Sessions", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private Nodes.Field classicFilter() {
        return new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Filter sessions", "Filter sessions", "");
    }

    private Nodes.Tree classicTree() {
        boolean searching = !query.strip().isEmpty();
        var roots = new ArrayList<TreeItem>();
        var expanded = new HashSet<String>();
        HostRows.Group group = null;
        var children = new ArrayList<TreeItem>();
        for (HostRows.Row row : rows) {
            if (row instanceof HostRows.Group next) {
                if (group != null) roots.add(groupItem(group, children));
                group = next; children = new ArrayList<>();
                if (searching || !collapsedGroup(next)) expanded.add(rowId(next));
            } else if (group != null && !(row instanceof HostRows.Error)) children.add(treeItem(row));
            else roots.add(treeItem(row));
        }
        if (group != null) roots.add(groupItem(group, children));
        return new Nodes.Tree(ROWS, ControlState.READY, roots, expanded, selectedRow == null ? Set.of() : Set.of(selectedRow), ACTIVATE, TREE_COLUMNS);
    }

    private Nodes.Panel classicNode() {
        var footer = new ArrayList<Nodes.Node>();
        hint().ifPresent(footer::add);
        footer.addAll(classicDetails());
        Optional<Nodes.Node> bottom = footer.isEmpty() ? Optional.empty()
            : Optional.of(new Nodes.Stack(FOOTER, ControlState.READY, Nodes.LayoutRole.VERTICAL, footer));
        return new Nodes.Panel(ROOT, ControlState.READY, classicHeader(), Optional.of(classicFilter()), classicTree(), bottom);
    }

    /** The tree in a list column beside the selected row's Properties and actions, which the docked tree holds under it. */
    private Nodes.Panel classicWideNode() {
        var listColumn = new Nodes.Panel(LIST_COLUMN, ControlState.READY, hiddenHeader(LIST_COLUMN + ".header", "Sessions", OptionalInt.empty()),
            Optional.of(classicFilter()), classicTree(), hintFooter(Nodes.LayoutRole.VERTICAL));
        List<Nodes.Node> details = classicDetails();
        if (details.isEmpty()) details = List.of(new Nodes.Text(DETAIL_EMPTY, ControlState.READY, "Select a session to see its properties.", Nodes.TextRole.DESCRIPTION));
        var detailColumn = new Nodes.Panel(DETAIL_COLUMN, ControlState.READY, hiddenHeader(DETAIL_COLUMN + ".header", "Details", OptionalInt.empty()),
            Optional.empty(), new Nodes.Stack(DETAIL_CONTENT, ControlState.READY, Nodes.LayoutRole.DETAIL, details), Optional.empty());
        return new Nodes.Panel(ROOT, ControlState.READY, classicHeader(), Optional.empty(),
            new Nodes.Split(COLUMNS, ControlState.READY, Nodes.SplitKind.SIDEBAR_DETAIL, List.of(listColumn, detailColumn)), Optional.empty());
    }

    private TreeItem groupItem(HostRows.Group group, List<TreeItem> children) {
        String id = rowId(group);
        boolean renamable = !group.wsl() && group.name().equals(defaultGroup);
        return new TreeItem(id, group.name(), Optional.empty(), false, children, OptionalInt.empty(), false,
            renamable ? "Right-click to rename this group" : "", Optional.empty(), List.of(),
            renamable ? Optional.of(List.of(menu("rename", id, "Rename group…"))) : Optional.empty());
    }

    private TreeItem treeItem(HostRows.Row row) {
        String id = rowId(row);
        return switch (row) {
            case HostRows.Error failure -> new TreeItem(id, failure.message(), Optional.empty(), true, List.of(), OptionalInt.empty(), true,
                failure.message(), Optional.of(Nodes.StatusKind.DANGER), List.of(), Optional.of(List.of()));
            case HostRows.Group group -> groupItem(group, List.of());
            case HostRows.Distro distro -> {
                int sessions = distro.sessions();
                String badge = distro.profile().detail().orElse(HostRows.WSL);
                yield new TreeItem(id, distro.profile().name(), Optional.empty(), true, List.of(), OptionalInt.empty(), false,
                    distro.profile().name() + ", " + badge + ", " + sessions + (sessions == 1 ? " session" : " sessions"),
                    Optional.of(sessions > 0 ? Nodes.StatusKind.SUCCESS : Nodes.StatusKind.IDLE), List.of(badge),
                    Optional.of(List.of(menu("open", id, "Open in new tab"), menu("open-split", id, "Open in split"))));
            }
            case HostRows.Host entry -> {
                RemoteHost host = entry.host();
                int sessions = sessionCount.applyAsInt(host);
                String facts = metadata.apply(host), via = via(host);
                yield new TreeItem(id, host.favorite() ? host.name() + " ★" : host.name(), Optional.empty(), true, List.of(), OptionalInt.empty(), false,
                    host.name() + ", " + host.label() + (facts.isBlank() ? "" : ", " + facts) + (via == null ? "" : ", via " + via)
                        + ", " + sessions + (sessions == 1 ? " session" : " sessions"),
                    Optional.of(sessions > 0 ? Nodes.StatusKind.SUCCESS : Nodes.StatusKind.IDLE), List.of(host.hostname(), String.valueOf(host.port())),
                    Optional.of(hostMenu(id, host)));
            }
        };
    }

    /** The selected host's or distribution's Properties and actions; nothing while no session is selected. */
    private List<Nodes.Node> classicDetails() {
        Optional<RemoteHost> host = selectedHost();
        if (host.isPresent()) {
            RemoteHost h = host.get();
            String credential = credentialLabels.apply(h);
            boolean locked = credential.equals("Vault locked");
            boolean named = h.auth() instanceof Auth.Vault && !locked && !credential.equals("needs Credential Vault") && !credential.equals("credential missing");
            boolean connected = sessionCount.applyAsInt(h) > 0;
            return List.of(new Nodes.Properties(PROPERTIES, ControlState.READY, "Properties — " + h.name(), List.of(
                    new Nodes.PropertyRow("Host", h.hostname(), true), new Nodes.PropertyRow("User", h.username(), true),
                    new Nodes.PropertyRow("Port", String.valueOf(h.port()), true),
                    new Nodes.PropertyRow("Credentials", named ? "Vault: " + credential : credential, false,
                        locked ? Optional.of(Nodes.Severity.WARNING) : Optional.empty()))),
                new Nodes.Stack(PROPERTY_ACTIONS, ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                    new Nodes.Button(EDIT, ControlState.READY, "Edit…", "", Nodes.ButtonKind.SECONDARY, EDIT, "Edit session"),
                    new Nodes.Button(CONNECT, ControlState.READY, connected ? "Open Shell" : "Connect", "", Nodes.ButtonKind.PRIMARY, CONNECT, ""))));
        }
        Optional<ShellProfileInfo> distro = selectedDistro();
        if (distro.isEmpty()) return List.of();
        var profile = distro.get();
        return List.of(new Nodes.Properties(PROPERTIES, ControlState.READY, "Properties — " + profile.name(), List.of(
                new Nodes.PropertyRow("Distribution", profile.name(), false), new Nodes.PropertyRow("Version", profile.detail().orElse(HostRows.WSL), false),
                new Nodes.PropertyRow("Command", "wsl.exe -d " + profile.name(), true))),
            new Nodes.Stack(PROPERTY_ACTIONS, ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
                new Nodes.Button(CONNECT, ControlState.READY, "Open", "", Nodes.ButtonKind.PRIMARY, CONNECT, ""))));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        headerChanged = null;
        view.close();
    }
}
