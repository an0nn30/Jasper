package dev.jasper.ui.gallery;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.swing.*;

import java.util.*;
import java.util.List;

/**
 * Four module-only gallery panels; every action changes local state without domain/network work.
 */
public final class PanelFixtures {
    private static final ControlState READY = ControlState.READY;

    private PanelFixtures() {}

    public static ViewSpec create(String name) {
        return new State(name).spec();
    }

    public static Demo mount(String name, KitContext context) {
        return new Demo(new State(name), context);
    }

    public static final class Demo implements AutoCloseable {
        private final State state;
        private final KitView view;
        private boolean closed;

        private Demo(State state, KitContext context) {
            this.state = state;
            view = new KitView(context, state.spec(), this::event);
            view.setPanelHostActions(
                    state.name,
                    List.of(
                            new Nodes.Button(
                                    state.name + "-collapse",
                                    READY,
                                    "",
                                    "collapse",
                                    ButtonKind.GHOST,
                                    "collapse",
                                    "Collapse or restore panel")),
                    e -> {
                        if (e instanceof UiEvent.Action) {
                            state.collapsed = !state.collapsed;
                            refresh();
                        }
                    });
        }

        public KitView view() {
            return view;
        }

        public boolean pending() {
            return !state.pendingServers.isEmpty();
        }

        /**
         * Deterministic completion seam for gallery/tests; false demonstrates confirmed-state
         * failure. Completes the oldest pending row intent; other rows keep their own pending
         * state.
         */
        public void completePending(boolean success) {
            if (closed) throw new IllegalStateException("Closed fixture");
            if (state.pendingServers.isEmpty())
                throw new IllegalStateException("No pending toggle");
            var server = state.servers.get(state.pendingServers.removeFirst());
            server.pending = false;
            if (success) {
                server.confirmed = server.requested;
                server.error = "";
            } else
                server.error =
                        "Could not " + (server.requested ? "start" : "stop") + " file server";
            refresh();
        }

        /** Dispatch a local fixture intent; used to record deterministic gallery transitions. */
        public void dispatch(UiEvent e) {
            if (closed) throw new IllegalStateException("Closed fixture");
            event(e);
        }

        private void event(UiEvent e) {
            if (e instanceof UiEvent.Edited edit && edit.id().equals("sessions-query"))
                state.query = edit.text();
            else if (e instanceof UiEvent.Toggled toggle) {
                if (toggle.id().equals("sftp-follow")) state.follow = toggle.checked();
                else {
                    var server = state.servers.get(toggle.id());
                    if (server == null || server.pending) return;
                    server.pending = true;
                    server.requested = toggle.checked();
                    server.error = "";
                    state.pendingServers.addLast(toggle.id());
                }
            } else if (e instanceof UiEvent.Selected selected
                    && selected.id().equals("sessions-body")) {
                if (!selected.ids().isEmpty()) state.selected = selected.ids().iterator().next();
            } else if (e instanceof UiEvent.Action action) {
                if(action.id().startsWith("session-favorite:")) {
                    String id=action.id().substring("session-favorite:".length());
                    if(!state.favorites.add(id))state.favorites.remove(id);
                    refresh();return;
                }
                switch (action.id()) {
                    case "sessions-add" ->
                            state.sessions.add(
                                    new Session(
                                            "added-" + state.sessions.size(),
                                            "new-session",
                                            "127.0.0.1"));
                    case "tunnels-add", "tunnels-new" -> state.tunnels++;
                    case "choose-amp" -> state.sftpSession = "amp";
                    case "choose-pve" -> state.sftpSession = "pve";
                    case "sftp-disconnect" -> state.sftpSession = "";
                    case "fileservers-add" ->
                            state.servers.put(
                                    "server-" + state.servers.size() + "-toggle",
                                    new ServerState());
                    case "session-connect" -> state.connected = !state.connected;
                    default -> {
                        return;
                    }
                }
            } else return;
            refresh();
        }

        private void refresh() {
            view.update(state.spec());
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                view.close();
            }
        }
    }

    private record Session(String id, String title, String address) {}

    private static final class ServerState {
        boolean confirmed, pending, requested;
        String error = "";
    }

    private static final class State {
        final String name;
        final List<Session> sessions =
                new ArrayList<>(
                        List.of(
                                new Session("amp", "amp", "100.110.65.45"),
                                new Session("amp-amp", "amp-amp", "100.110.65.45"),
                                new Session("dustin-vm", "dustin-vm", "100.83.72.100"),
                                new Session("minecraft", "minecraft-local", "192.168.1.124"),
                                new Session("pve", "pve", "100.73.226.91"),
                                new Session("pve-root", "pve-root", "100.73.226.91")));
        String query = "", selected = "amp", sftpSession = "";
        final Set<String> favorites = new HashSet<>();
        int tunnels;
        final Map<String, ServerState> servers = new LinkedHashMap<>();
        final Deque<String> pendingServers = new ArrayDeque<>();
        boolean follow = true, collapsed, connected;

        State(String name) {
            if (!Set.of("sessions", "tunnels", "sftp", "fileservers").contains(name))
                throw new IllegalArgumentException("Unknown panel fixture: " + name);
            this.name = name;
            servers.put("server-toggle", new ServerState());
        }

        ControlState bodyState() {
            return new ControlState(!collapsed, true, false, "");
        }

        ViewSpec spec() {
            return switch (name) {
                case "sessions" -> sessions();
                case "tunnels" -> tunnels();
                case "sftp" -> sftp();
                case "fileservers" -> fileServers();
                default -> throw new AssertionError(name);
            };
        }

        Header header(String title, OptionalInt count, boolean add) {
            return new Header(
                    name + "-header",
                    READY,
                    title,
                    count,
                    Optional.empty(),
                    add
                            ? Optional.of(
                                    new Nodes.Button(
                                            name + "-add",
                                            READY,
                                            "",
                                            "add",
                                            ButtonKind.GHOST,
                                            name + "-add",
                                            "Add " + title))
                            : Optional.empty(),
                    name.equals("sftp")
                            ? List.of(
                                    new MenuItem(
                                            "disconnect",
                                            "Disconnect",
                                            "",
                                            "sftp-disconnect",
                                            !sftpSession.isEmpty(),
                                            false))
                            : List.of(
                                    new MenuItem(
                                            "add-more",
                                            "Add " + title,
                                            "add",
                                            name + "-add",
                                            true,
                                            false)));
        }

        ViewSpec panel(Header h, Optional<Node> nav, Node body, Optional<Node> footer) {
            return new ViewSpec(new Nodes.Panel(name, READY, h, nav, body, footer));
        }

        ViewSpec sessions() {
            var filtered =
                    sessions.stream()
                            .filter(
                                    s ->
                                            s.title()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains(
                                                                    query.toLowerCase(Locale.ROOT))
                                                    || s.address().contains(query))
                            .toList();
            Node body =
                    filtered.isEmpty()
                            ? empty(
                                    name,
                                    "search",
                                    "No Results",
                                    "Try a different session name.",
                                    List.of())
                            : new ItemList(
                                    "sessions-body",
                                    bodyState(),
                                    ListKind.SINGLE,
                                    filtered.stream()
                                            .map(
                                                    s ->
                                                            new ListItem(
                                                                    s.id(),
                                                                    Optional.of(
                                                                            new StatusDot(
                                                                                    "session-dot-"
                                                                                            + s
                                                                                                    .id(),
                                                                                    READY,
                                                                                    StatusKind
                                                                                            .IDLE)),
                                                                    List.of(
                                                                            text(
                                                                                    "session-title-"
                                                                                            + s
                                                                                                    .id(),
                                                                                    s.title(),
                                                                                    TextRole
                                                                                            .BODY),
                                                                            text(
                                                                                    "session-address-"
                                                                                            + s
                                                                                                    .id(),
                                                                                    s.address(),
                                                                                    TextRole.BODY)),
                                                                    List.of(),
                                                                    List.of(new Nodes.Button("session-star-"+s.id(),READY,"",favorites.contains(s.id())?"star.filled":"star",ButtonKind.GHOST,"session-favorite:"+s.id(),favorites.contains(s.id())?"Remove from favorites":"Add to favorites",favorites.contains(s.id())?ButtonPresentation.EMBEDDED:ButtonPresentation.CONTEXTUAL_EMBEDDED,favorites.contains(s.id())?Optional.of(Severity.WARNING):Optional.empty()))))
                                            .toList(),
                                    filtered.stream().anyMatch(s -> s.id().equals(selected))
                                            ? Set.of(selected)
                                            : Set.of(),
                                    "session-connect",
                                    List.of());
            Session detail =
                    sessions.stream()
                            .filter(s -> s.id().equals(selected))
                            .findFirst()
                            .orElse(sessions.getFirst());
            var footer =
                    new Nodes.Stack(
                            "sessions-footer",
                            READY,
                            LayoutRole.DETAIL,
                            List.of(
                                    new Nodes.Stack(
                                            "session-detail",
                                            READY,
                                            LayoutRole.NAVIGATION,
                                            List.of(
                                                    new Nodes.Icon("session-detail-icon",READY,"server",Hue.NEUTRAL,IconSize.HERO,"SSH host"),
                                                    new Nodes.Stack(
                                                            "session-detail-text",
                                                            READY,
                                                            LayoutRole.VERTICAL,
                                                            List.of(
                                                                    text(
                                                                            "session-detail-title",
                                                                            detail.title(),
                                                                            TextRole.PANEL_TITLE),
                                                                    text(
                                                                            "session-detail-address",
                                                                            "ubuntu@"
                                                                                    + detail
                                                                                            .address()
                                                                                    + ":22",
                                                                            TextRole
                                                                                    .MONO_CAPTION))))),
                                    new Nodes.Stack(
                                            "session-footer-actions",
                                            READY,
                                            LayoutRole.NAVIGATION,
                                            List.of(
                                                    new Nodes.Icon("session-lock",READY,"lock",Hue.NONE,IconSize.CONTROL,"Vault locked"),
                                                    text(
                                                            "session-vault",
                                                            "Vault locked",
                                                            TextRole.CAPTION),
                                                    new Nodes.Button("session-edit",READY,"","edit",ButtonKind.GHOST,"session-edit","Edit session"),
                                                    new Nodes.Button(
                                                            "session-connect",
                                                            READY,
                                                            connected ? "Connected" : "Connect",
                                                            "",
                                                            ButtonKind.PRIMARY,
                                                            "session-connect",
                                                            "Connect locally in fixture")))));
            return panel(
                    header("Sessions", OptionalInt.of(sessions.size()), true),
                    Optional.of(
                            new Field(
                                    "sessions-query",
                                    new ControlState(!collapsed, true, false, ""),
                                    FieldKind.SEARCH,
                                    query,
                                    "Search sessions",
                                    "Search sessions",
                                    "")),
                    body,
                    Optional.of(footer));
        }

        ViewSpec tunnels() {
            Node body =
                    tunnels == 0
                            ? empty(
                                    name,
                                    "tunnel",
                                    "No Tunnels",
                                    "Forward a local port to a remote host, or use Forward Port"
                                        + " from an SSH pane.",
                                    List.of(button("tunnels-new", "New Tunnel", "tunnels-new")))
                            : new ItemList(
                                    "tunnels-body",
                                    bodyState(),
                                    ListKind.DOUBLE,
                                    java.util.stream.IntStream.range(0, tunnels)
                                            .mapToObj(
                                                    i ->
                                                            new ListItem(
                                                                    "tunnel-" + i,
                                                                    Optional.of(
                                                                            icon(
                                                                                    "tunnel-icon-"
                                                                                            + i,
                                                                                    "tunnel")),
                                                                    List.of(
                                                                            text(
                                                                                    "tunnel-title-"
                                                                                            + i,
                                                                                    "Local forward",
                                                                                    TextRole
                                                                                            .BODY_STRONG)),
                                                                    List.of(
                                                                            text(
                                                                                    "tunnel-address-"
                                                                                            + i,
                                                                                    "127.0.0.1:8080",
                                                                                    TextRole
                                                                                            .MONO_SMALL)),
                                                                    List.of(
                                                                            new StatusDot(
                                                                                    "tunnel-status-"
                                                                                            + i,
                                                                                    READY,
                                                                                    StatusKind
                                                                                            .SUCCESS))))
                                            .toList(),
                                    Set.of(),
                                    "",
                                    List.of());
            return panel(
                    header("Tunnels", OptionalInt.of(tunnels), true),
                    Optional.empty(),
                    body,
                    Optional.empty());
        }

        ViewSpec sftp() {
            Node body =
                    sftpSession.isEmpty()
                            ? empty(
                                    name,
                                    "folder",
                                    "Not Connected",
                                    "Connect to an SSH session to browse its files.",
                                    List.of(
                                            new MenuButton(
                                                    "sftp-choose",
                                                    READY,
                                                    "Choose Session",
                                                    "",
                                                    ButtonKind.SECONDARY,
                                                    "",
                                                    List.of(
                                                            new MenuItem(
                                                                    "amp-choice",
                                                                    "amp",
                                                                    "server",
                                                                    "choose-amp",
                                                                    true,
                                                                    false),
                                                            new MenuItem(
                                                                    "pve-choice",
                                                                    "pve",
                                                                    "server",
                                                                    "choose-pve",
                                                                    true,
                                                                    false)),
                                                    "Choose an SSH session")))
                            : new Nodes.Table(
                                    "sftp-body",
                                    bodyState(),
                                    List.of(
                                            new Column("name", "Name", TextRole.BODY, false),
                                            new Column("size", "Size", TextRole.MONO_SMALL, true)),
                                    List.of(
                                            new TableRow(
                                                    "documents",
                                                    Map.of("name", "Documents", "size", "\u2014"),
                                                    Optional.of("folder")),
                                            new TableRow(
                                                    "readme",
                                                    Map.of("name", "README.txt", "size", "1.2 KB"),
                                                    Optional.of("file"))),
                                    Set.of(),
                                    "",
                                    List.of());
            var nav =
                    new Nodes.Stack(
                            "sftp-navigation",
                            new ControlState(!collapsed && !sftpSession.isEmpty(), true, false, ""),
                            LayoutRole.NAVIGATION,
                            List.of(
                                    icon("sftp-folder", "folder"),
                                    text(
                                            "sftp-path",
                                            "/home/"
                                                    + (sftpSession.isEmpty() ? "amp" : sftpSession),
                                            TextRole.MONO_SMALL)));
            return panel(
                    header("SFTP", OptionalInt.empty(), false),
                    Optional.of(nav),
                    body,
                    Optional.of(
                            new SettingRow(
                                    "sftp-footer",
                                    READY,
                                    "Follow terminal folder",
                                    "",
                                    new Toggle(
                                            "sftp-follow",
                                            READY,
                                            "Follow terminal folder",
                                            follow,
                                            "follow",
                                            true),
                                    false)));
        }

        ViewSpec fileServers() {
            var rows = new ArrayList<ListItem>();
            int i = 0;
            for (var entry : servers.entrySet()) {
                String prefix = i == 0 ? "server" : "server-" + i;
                var server = entry.getValue();
                var toggleState = new ControlState(true, true, server.pending, server.error);
                rows.add(
                        new ListItem(
                                prefix,
                                Optional.of(
                                        new Nodes.Icon(
                                                prefix + "-icon",
                                                READY,
                                                "folder",
                                                Hue.BLUE,
                                                IconSize.CONTROL,
                                                "File server")),
                                List.of(
                                        text(
                                                prefix + "-title",
                                                i == 0 ? "tester" : "server " + (i + 1),
                                                TextRole.BODY)),
                                List.of(
                                        new Chip(prefix + "-protocol", READY, "HTTP", Hue.BLUE),
                                        text(
                                                prefix + "-address",
                                                "127.0.0.1:" + (8080 + i),
                                                TextRole.MONO_SMALL),
                                        text(
                                                prefix + "-state",
                                                server.pending
                                                        ? server.requested
                                                                ? "Starting..."
                                                                : "Stopping..."
                                                        : server.error.isEmpty() ? "" : "Failed",
                                                TextRole.CAPTION)),
                                List.of(
                                        new Toggle(
                                                prefix + "-toggle",
                                                toggleState,
                                                "Enable file server",
                                                server.confirmed,
                                                "toggle-server",
                                                true))));
                i++;
            }
            return panel(
                    header("File Servers", OptionalInt.of(servers.size()), true),
                    Optional.empty(),
                    new ItemList(
                            "fileservers-body",
                            bodyState(),
                            ListKind.DOUBLE,
                            rows,
                            Set.of(),
                            "",
                            List.of()),
                    Optional.empty());
        }

        Empty empty(
                String prefix, String icon, String title, String description, List<Node> actions) {
            return new Empty(
                    prefix + "-body",
                    bodyState(),
                    new Nodes.Icon(
                            prefix + "-empty-icon",
                            READY,
                            icon,
                            Hue.NEUTRAL,
                            IconSize.EMPTY,
                            title),
                    title,
                    description,
                    actions);
        }
    }

    private static Text text(String id, String text, TextRole role) {
        return new Text(id, READY, text, role);
    }

    private static Nodes.Icon icon(String id, String name) {
        return new Nodes.Icon(id, READY, name, Hue.NEUTRAL, IconSize.CONTROL, name);
    }

    private static Nodes.Button button(String id, String label, String action) {
        return new Nodes.Button(id, READY, label, "", ButtonKind.SECONDARY, action, "");
    }
}
