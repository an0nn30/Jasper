package dev.jasper.remote;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.ui.HostRows;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.sftp.SftpPanel;
import dev.jasper.remote.ui.transfers.TransferRows;
import dev.jasper.remote.ui.transfers.TransferStrip;
import dev.jasper.remote.ui.transfers.TransfersPanel;
import dev.jasper.remote.ui.tunnels.TunnelsPanel;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.TreeItem;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spec 14.7: each default-look gesture of Remote has a classic path with the same effect. A gesture runs once on the
 * rows composition and once on the classic one, each on fresh panels recording what the plugin is asked to do: the
 * Sessions, Tunnels and SFTP panels, and the window's transfer rows, which the default look shows in the SFTP footer and
 * the classic one in the Transfers panel.
 */
class CompositionParityTest {
    @TempDir Path dir;
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final ShellProfileInfo ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
    final Tunnel db = Tunnel.create("db", mac.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    /** A quick forward (an empty name): its menu offers Save as Tunnel… in place of Edit…. */
    final Tunnel forward = Tunnel.create("", mac.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 8080, "localhost", 80);
    static final FileEntry FOLDER = new FileEntry("Applications", FileEntry.Kind.DIRECTORY, 0, 1000, 0755, ""),
        FILE = new FileEntry("notes.toml", FileEntry.Kind.FILE, 6, 1000, 0644, "");
    /** A running upload, and a finished download whose row offers Resume. */
    final UUID running = UUID.randomUUID(), finished = UUID.randomUUID();

    /** One composition under test: its panels and what they asked for. */
    final class Compositions {
        final List<String> events = new ArrayList<>();
        final Map<UUID, TunnelState> states = new java.util.HashMap<>();
        final HostsPanel sessions;
        final TunnelsPanel tunnels;
        final SftpPanel sftp;
        final TransferStrip strip;
        /** The classic Transfers panel over {@link #strip}; null under ROWS, where the SFTP footer shows the rows. */
        final TransfersPanel transfers;
        Compositions(Layout layout) {
            sessions = new HostsPanel(new FakeKit("dev.jasper.remote"), new HostsPanel.Actions(h -> events.add("connect " + h.name()),
                h -> events.add("split " + h.name()), h -> events.add("edit " + h.map(RemoteHost::name).orElse("new")), h -> events.add("duplicate " + h.name()),
                h -> events.add("delete " + h.name()), (h, favorite) -> events.add("favorite " + h.name() + " " + favorite), () -> events.add("import")), layout);
            sessions.onDistros(profile -> events.add("open " + profile.name()), profile -> events.add("open-split " + profile.name()));
            sessions.onBrowseFiles(h -> events.add("browse " + h.name()));
            sessions.setDistros(List.of(ubuntu), profile -> 0);
            sessions.setHosts(List.of(mac), Optional.empty());
            tunnels = new TunnelsPanel(new FakeKit("dev.jasper.remote"), new TunnelsPanel.Actions(t -> events.add("start " + t.name()),
                t -> events.add("stop " + t.name()), t -> events.add("edit " + t.name()), t -> events.add("duplicate " + t.name()),
                t -> events.add("delete " + t.name()), t -> events.add("save " + t.name()), t -> events.add("browse " + t.name()),
                t -> events.add("copy " + t.name()), () -> events.add("new tunnel")), layout);
            tunnels.setTunnels(List.of(db), List.of(forward), Optional.empty(), id -> states.getOrDefault(id, TunnelState.STOPPED), id -> Optional.of("Mac"));
            sftp = new SftpPanel(new FakeKit("dev.jasper.remote"), layout);
            sftp.actions(new SftpPanel.Actions(path -> events.add("navigate " + path), () -> events.add("up"), () -> events.add("refresh"),
                follow -> events.add("follow " + follow), () -> events.add("upload"), () -> events.add("upload folder"), () -> events.add("download"),
                () -> events.add("new folder"), () -> events.add("delete"), () -> events.add("copy paths"), () -> events.add("copy to host"),
                () -> events.add("cancel"), page -> events.add("page " + page), shown -> events.add("hidden " + shown)));
            sftp.navigation(() -> events.add("back"), () -> events.add("forward"));
            sftp.onChooseSession(h -> events.add("choose " + h.name()));
            sftp.sessions(List.of(mac));
            strip = new TransferStrip(new TransferStrip.Actions((id, action) -> events.add(action + " " + job(id)),
                id -> events.add("cancel " + job(id)), id -> events.add("dismiss " + job(id))));
            strip.rows(List.of(
                new TransferRows.Row(running, "↑", "report.pdf → Mac:/srv", "Local: /tmp/report.pdf → dustin@Mac: /srv", "Uploading",
                    OptionalDouble.of(0.4), false, Optional.empty(), false, false),
                new TransferRows.Row(finished, "↓", "notes.toml → Local", "dustin@Mac: /srv/notes.toml → Local: /tmp", "Interrupted",
                    OptionalDouble.empty(), false, Optional.of(TransferRows.Action.RESUME), true, false)));
            if (layout == Layout.ROWS) { sftp.transfers(strip); transfers = null; }
            else transfers = new TransfersPanel(new FakeKit("dev.jasper.remote"), strip);
        }
        String job(UUID id) { return id.equals(running) ? "running" : "finished"; }
        /** A listed folder, as a connected sidebar shows it: the first entry selected, history both ways. */
        void browse() { sftp.showPage("Mac", "/Users/dustin", List.of(FOLDER, FILE), 0, 2, false); sftp.navigationAvailable(true, true); }
        /** A folder longer than one page, shown from its second page. */
        void paged() { sftp.showPage("Mac", "/Users/dustin", List.of(FILE), 200, 450, false); }
        /** Where the window's transfer rows are: the SFTP sidebar's footer, or the classic Transfers panel. */
        dev.jasper.sdk.ui.UiView transferView() { return transfers == null ? sftp.view() : transfers.view(); }
        /** The SFTP item labelled {@code label} in the Upload menu, the first row's menu or the folder's own menu. */
        void sftpMenu(String where, String label) {
            var table = node(sftp.view(), SftpPanel.FILES, Nodes.Table.class);
            List<MenuItem> items = switch (where) {
                case "upload" -> node(sftp.view(), SftpPanel.UPLOAD, Nodes.MenuButton.class).items();
                case "row" -> table.rows().getFirst().contextActions().orElseThrow();
                default -> table.contextActions();
            };
            press(sftp.view(), items.stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action());
        }
        void selectHost(String row) { emit(sessions.view(), new UiEvent.Selected(HostsPanel.ROWS, Set.of(row))); }
        void selectTunnel() { emit(tunnels.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of("tunnel:" + db.id()))); }
        List<MenuItem> hostMenu(String row) {
            var node = node(sessions.view(), HostsPanel.ROWS, Nodes.Node.class);
            if (node instanceof Nodes.ItemList list) return list.items().stream().filter(item -> item.id().equals(row)).findFirst().flatMap(ListItem::contextActions).orElseThrow();
            return leaf(((Nodes.Tree) node).roots(), row).flatMap(TreeItem::contextActions).orElseThrow();
        }
        List<MenuItem> tunnelMenu() {
            var node = node(tunnels.view(), TunnelsPanel.ROWS, Nodes.Node.class);
            if (node instanceof Nodes.ItemList list) return list.items().stream().filter(item -> item.id().startsWith("tunnel:")).findFirst().flatMap(ListItem::contextActions).orElseThrow();
            return ((Nodes.Table) node).rows().stream().filter(row -> row.heading().isEmpty()).findFirst().flatMap(TableRow::contextActions).orElseThrow();
        }
        List<MenuItem> tunnelMenu(Tunnel tunnel) {
            String id = "tunnel:" + tunnel.id();
            var node = node(tunnels.view(), TunnelsPanel.ROWS, Nodes.Node.class);
            if (node instanceof Nodes.ItemList list) return list.items().stream().filter(item -> item.id().equals(id)).findFirst().flatMap(ListItem::contextActions).orElseThrow();
            return ((Nodes.Table) node).rows().stream().filter(row -> row.id().equals(id)).findFirst().flatMap(TableRow::contextActions).orElseThrow();
        }
        void menu(List<MenuItem> items, String label, Function<Compositions, dev.jasper.sdk.ui.UiView> view) {
            press(view.apply(this), items.stream().filter(item -> item.label().equals(label)).findFirst().orElseThrow().action());
        }
    }

    static Optional<TreeItem> leaf(List<TreeItem> items, String id) {
        for (var item : items) {
            if (item.id().equals(id)) return Optional.of(item);
            var found = leaf(item.children(), id);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    interface Path0 { void run(Compositions c) throws Exception; }
    record Gesture(String name, Path0 rows, Path0 classic) { }

    String host() { return HostsPanel.rowId(new HostRows.Host(mac)); }
    String distro() { return "distro:" + ubuntu.id(); }

    List<Gesture> gestures() {
        var gestures = new ArrayList<Gesture>();
        gestures.add(new Gesture("activate a host", c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.ACTIVATE); },
            c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.ACTIVATE); }));
        gestures.add(new Gesture("Connect on the card or Properties", c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.CONNECT); },
            c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.CONNECT); }));
        gestures.add(new Gesture("Edit on the card or Properties", c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.EDIT); },
            c -> { c.selectHost(host()); press(c.sessions.view(), HostsPanel.EDIT); }));
        gestures.add(new Gesture("the star", c -> press(c.sessions.view(), "favorite:" + mac.id()),
            c -> c.menu(c.hostMenu(host()), "Add to favorites", x -> x.sessions.view())));
        for (String label : List.of("Connect in new tab", "Connect in split", "Browse files", "Edit…", "Duplicate", "Delete…", "Add to favorites"))
            gestures.add(new Gesture("host menu " + label, c -> c.menu(c.hostMenu(host()), label, x -> x.sessions.view()),
                c -> c.menu(c.hostMenu(host()), label, x -> x.sessions.view())));
        gestures.add(new Gesture("open a distribution", c -> { c.selectHost(distro()); press(c.sessions.view(), HostsPanel.ACTIVATE); },
            c -> { c.selectHost(distro()); press(c.sessions.view(), HostsPanel.ACTIVATE); }));
        for (String label : List.of("Open in new tab", "Open in split"))
            gestures.add(new Gesture("distribution menu " + label, c -> c.menu(c.hostMenu(distro()), label, x -> x.sessions.view()),
                c -> c.menu(c.hostMenu(distro()), label, x -> x.sessions.view())));
        gestures.add(new Gesture("collapse a group", c -> { emit(c.sessions.view(), new UiEvent.Clicked(HostsPanel.ROWS, "group:Local Network")); c.events.add("collapsed " + c.sessions.collapsed()); },
            c -> { emit(c.sessions.view(), new UiEvent.Expanded(HostsPanel.ROWS, "group:Local Network", false)); c.events.add("collapsed " + c.sessions.collapsed()); }));
        gestures.add(new Gesture("search", c -> { type(c.sessions.view(), HostsPanel.SEARCH, "zzz"); c.events.add("rows " + c.sessions.rows().size()); },
            c -> { type(c.sessions.view(), HostsPanel.SEARCH, "zzz"); c.events.add("rows " + c.sessions.rows().size()); }));
        gestures.add(new Gesture("start a tunnel", c -> emit(c.tunnels.view(), new UiEvent.Toggled("switch:" + db.id(), true)),
            c -> { c.selectTunnel(); press(c.tunnels.view(), TunnelsPanel.TOGGLE); }));
        gestures.add(new Gesture("stop a running tunnel", c -> { c.states.put(db.id(), new TunnelState.Up(Instant.now(), 5432, new Counters())); c.tunnels.refresh(); emit(c.tunnels.view(), new UiEvent.Toggled("switch:" + db.id(), false)); },
            c -> { c.states.put(db.id(), new TunnelState.Up(Instant.now(), 5432, new Counters())); c.tunnels.refresh(); c.selectTunnel(); press(c.tunnels.view(), TunnelsPanel.TOGGLE); }));
        gestures.add(new Gesture("edit a tunnel", c -> { c.selectTunnel(); press(c.tunnels.view(), TunnelsPanel.ACTIVATE); },
            c -> c.menu(c.tunnelMenu(), "Edit…", x -> x.tunnels.view())));
        gestures.add(new Gesture("delete a tunnel", c -> { c.selectTunnel(); press(c.tunnels.view(), TunnelsPanel.DELETE); },
            c -> c.menu(c.tunnelMenu(), "Delete…", x -> x.tunnels.view())));
        for (String label : List.of("Duplicate", "Copy Address"))
            gestures.add(new Gesture("tunnel menu " + label, c -> c.menu(c.tunnelMenu(), label, x -> x.tunnels.view()),
                c -> c.menu(c.tunnelMenu(), label, x -> x.tunnels.view())));
        Path0 up = c -> { c.states.put(db.id(), new TunnelState.Up(Instant.now(), 5432, new Counters())); c.tunnels.refresh(); };
        gestures.add(new Gesture("tunnel menu Open in Browser", c -> { up.run(c); c.menu(c.tunnelMenu(), "Open in Browser", x -> x.tunnels.view()); },
            c -> { up.run(c); c.menu(c.tunnelMenu(), "Open in Browser", x -> x.tunnels.view()); }));
        gestures.add(new Gesture("quick forward menu Save as Tunnel…", c -> c.menu(c.tunnelMenu(forward), "Save as Tunnel…", x -> x.tunnels.view()),
            c -> c.menu(c.tunnelMenu(forward), "Save as Tunnel…", x -> x.tunnels.view())));
        // A filter that matches nothing: the default look's "No tunnels match" line, the classic table's empty text.
        gestures.add(new Gesture("a search that matches no tunnel",
            c -> { type(c.tunnels.view(), TunnelsPanel.FILTER, "zzz"); c.events.add("empty " + node(c.tunnels.view(), TunnelsPanel.NO_MATCH, Nodes.Text.class).text()); },
            c -> { type(c.tunnels.view(), TunnelsPanel.FILTER, "zzz"); c.events.add("empty " + node(c.tunnels.view(), TunnelsPanel.ROWS, Nodes.Table.class).emptyText()); }));

        // SFTP, connected: the navigation row keeps its ids, so each tool is the same press in both looks.
        for (String tool : List.of(SftpPanel.BACK, SftpPanel.FORWARD, SftpPanel.REFRESH, SftpPanel.HIDDEN, SftpPanel.UPLOAD))
            gestures.add(new Gesture("SFTP " + tool, c -> { c.browse(); press(c.sftp.view(), tool); }, c -> { c.browse(); press(c.sftp.view(), tool); }));
        for (String where : List.of("upload", "row", "folder"))
            for (String label : List.of("Up", "New folder…", "Upload folder…", "Download selected…", "Copy to host…", "Copy paths", "Delete selected…"))
                gestures.add(new Gesture("SFTP " + where + " menu " + label, c -> { c.browse(); c.sftpMenu(where, label); },
                    c -> { c.browse(); c.sftpMenu(where, label); }));
        Path0 crumb = c -> { c.browse(); emit(c.sftp.view(), new UiEvent.Selected(SftpPanel.PATH,
            Set.of(node(c.sftp.view(), SftpPanel.PATH, Nodes.Breadcrumbs.class).crumbs().getFirst().id()))); };
        gestures.add(new Gesture("SFTP breadcrumb", crumb, crumb));
        Path0 open = c -> { c.browse(); press(c.sftp.view(), node(c.sftp.view(), SftpPanel.FILES, Nodes.Table.class).activateAction()); };
        gestures.add(new Gesture("SFTP open a folder", open, open));
        Path0 follow = c -> { c.browse(); emit(c.sftp.view(), new UiEvent.Toggled(SftpPanel.FOLLOW, false)); };
        gestures.add(new Gesture("SFTP Follow terminal folder", follow, follow));
        for (String page : List.of(SftpPanel.PREVIOUS, SftpPanel.NEXT))
            gestures.add(new Gesture("SFTP page " + page, c -> { c.paged(); press(c.sftp.view(), page); }, c -> { c.paged(); press(c.sftp.view(), page); }));
        // SFTP, disconnected: the default look's Choose Session menu, the classic Choose Session… dropdown.
        gestures.add(new Gesture("SFTP Choose Session",
            c -> press(c.sftp.view(), node(c.sftp.view(), SftpPanel.CHOOSE, Nodes.MenuButton.class).items().stream()
                .filter(item -> item.label().equals("Mac")).findFirst().orElseThrow().action()),
            c -> emit(c.sftp.view(), new UiEvent.Selected(SftpPanel.CHOOSE_DROPDOWN, Set.of(node(c.sftp.view(), SftpPanel.CHOOSE_DROPDOWN, Nodes.Choice.class)
                .options().stream().filter(option -> option.label().equals("Mac")).findFirst().orElseThrow().id())))));

        // The window's transfer rows: the SFTP footer under ROWS, the Transfers panel under CLASSIC (Task 8).
        gestures.add(new Gesture("cancel a running transfer", c -> click(c.transferView(), "transfer." + running + ".close"),
            c -> click(c.transferView(), "transfer." + running + ".close")));
        gestures.add(new Gesture("dismiss a finished transfer", c -> click(c.transferView(), "transfer." + finished + ".close"),
            c -> click(c.transferView(), "transfer." + finished + ".close")));
        gestures.add(new Gesture("a transfer's own action", c -> click(c.transferView(), "transfer." + finished + ".action"),
            c -> click(c.transferView(), "transfer." + finished + ".action")));
        return gestures;
    }

    @Test void everySessionsTunnelsSftpAndTransfersGestureHasAClassicPathWithTheSameEffect() throws Exception {
        for (var gesture : gestures()) {
            var effects = new ArrayList<List<String>>();
            for (var layout : List.of(Layout.ROWS, Layout.CLASSIC)) {
                var c = value(() -> new Compositions(layout));
                try { edt(() -> (layout == Layout.ROWS ? gesture.rows() : gesture.classic()).run(c)); }
                catch (Exception | Error failure) { throw new AssertionError(gesture.name() + " (" + layout + ") has no path", failure); }
                effects.add(List.copyOf(c.events));
            }
            assertThat(effects.get(0)).as(gesture.name() + ": the default look's path does something").isNotEmpty();
            assertThat(effects.get(1)).as(gesture.name() + ": the classic path does the same").isEqualTo(effects.get(0));
        }
    }

    @Test void theToolbarsHostDistributionAndManageSessionsGesturesHaveClassicPaths() throws Exception {
        for (String what : List.of("host", "distro", "manage")) {
            var effects = new ArrayList<List<String>>();
            for (var layout : List.of(Layout.ROWS, Layout.CLASSIC)) try (var host = new FakePluginHost()) {
                var events = new ArrayList<String>();
                var context = new AtomicReference<PluginContext>();
                host.start(RemotePluginTest.INFO, Set.of(), Set.of(), context::set);
                var store = new HostStore(dir.resolve(layout + "-" + what + ".toml"), Runnable::run, Runnable::run);
                store.put(mac).join();
                var toolbar = value(() -> new SessionsToolbar(context.get(), store, "network", (window, chosen) -> events.add("activate " + chosen.name()),
                    window -> events.add("manage"), () -> List.of(ubuntu), (window, profile) -> events.add("open " + profile.name()),
                    layout, new SessionSelection(), window -> Optional.empty()));
                var window = host.addTerminalWindow();
                if (layout == Layout.ROWS) {
                    String action = switch (what) {
                        case "host" -> "dev.jasper.remote.session.h" + mac.id();
                        case "distro" -> SessionsToolbar.distroActionId(ubuntu.id());
                        default -> "dev.jasper.remote.sessions.manage";
                    };
                    assertThat(value(() -> host.invoke(action, window, null))).as(what + " runs").isTrue();
                } else {
                    var view = value(() -> host.toolbarViews(window).getFirst());
                    edt(() -> {
                        if (what.equals("manage")) emit(view, new UiEvent.Selected(SessionsToolbar.CHOICE, Set.of(SessionsToolbar.MANAGE_OPTION)));
                        else {
                            emit(view, new UiEvent.Selected(SessionsToolbar.CHOICE, Set.of(what.equals("host") ? "host:" + mac.id() : "distro:" + ubuntu.id())));
                            press(view, SessionsToolbar.CONNECT);
                        }
                    });
                }
                effects.add(List.copyOf(events));
                edt(toolbar::close);
            }
            assertThat(effects.get(0)).as(what).isNotEmpty();
            assertThat(effects.get(1)).as(what + ": the toolbar view does the same").isEqualTo(effects.get(0));
        }
    }

    @Test void transfersIsReachableInBothLooks() throws Exception {
        for (var layout : List.of(Layout.ROWS, Layout.CLASSIC)) try (var host = new FakePluginHost()) {
            if (layout == Layout.CLASSIC) dev.jasper.remote.workstation.FakeEnterprise.start(host);
            var plugin = new TestRemotePlugin(UI, context -> Optional.empty(), (delay, task) -> () -> { }, dir.resolve("ssh-" + layout));
            host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor", "dev.jasper.enterprise"), plugin);
            settle(host);
            var window = host.addTerminalWindow();
            assertThat(value(() -> host.invoke(dev.jasper.remote.ui.transfers.TransferUi.SHOW, window, null))).as(layout + ": Transfers runs").isTrue();
            settle(host);
            String shown = layout == Layout.ROWS ? dev.jasper.remote.ui.sftp.SftpUi.PANEL : dev.jasper.remote.ui.transfers.TransfersPanel.PANEL;
            assertThat(value(() -> host.panelView(shown))).as(layout + ": Transfers shows its panel").isPresent();
        }
    }
}
