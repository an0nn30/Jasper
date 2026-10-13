package dev.jasper.remote;

import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.HostRows;
import dev.jasper.remote.ui.RemoteScope;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import dev.jasper.remote.ui.sftp.SftpPanel;
import dev.jasper.remote.ui.sftp.SftpTestAccess;
import dev.jasper.remote.ui.sftp.SftpUi;
import dev.jasper.remote.ui.transfers.ExistingItemsPanel;
import dev.jasper.remote.testsupport.Kits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.ui.UiTestAccess.*;
import static org.assertj.core.api.Assertions.*;

class RemotePluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.2.0",
        Set.of(Capabilities.TERMINAL_OPEN, Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE, Capabilities.FILES_SOURCE));

    final List<Runnable> scheduled = new ArrayList<>();

    RemotePlugin plugin(Path sshDir) {
        // Completions reach the plugin's kit views on the EDT (Task 8a): inline there, queued from MINA and worker threads.
        return new TestRemotePlugin(Kits.UI, context -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
    }

    @Test void hostContributesSemanticNetworkNavigationIcons(@TempDir Path dir) {
        try(var host=new FakePluginHost()) {
            host.start(INFO,Set.of(),Set.of(),plugin(dir.resolve("ssh")));settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(host.actionSpec("dev.jasper.remote.connect").orElseThrow().icon()).contains("network");
            assertThat(host.toolbar()).anyMatch(item -> item.contains("Sessions"));
            assertThat(host.panels()).containsExactly("dev.jasper.remote.sftp.panel|SFTP|LEFT+split|dev.jasper.remote.sftp.toggle", "dev.jasper.remote.tunnels.panel|Tunnels|LEFT|dev.jasper.remote.tunnels", "dev.jasper.remote.panel|Sessions|LEFT|dev.jasper.remote.hosts");
        }
    }

    @Test void navigationActionsCarryTheirDefaultsAndAnOldShortcutsTableIsUnknown(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.remote", "shortcuts.toggle_panel", "cmd+alt+p");
            host.start(INFO, Set.of(), Set.of(), plugin(dir.resolve("ssh")));
            settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(defaults(host, RemotePlugin.CONNECT)).isEqualTo("cmd+shift+h|ctrl+shift+h|GLOBAL");
            assertThat(defaults(host, RemotePlugin.HOSTS)).isEqualTo("cmd+alt+1|alt+1|GLOBAL");
            assertThat(defaults(host, RemotePlugin.SFTP_TOGGLE)).isEqualTo("cmd+alt+2|alt+2|GLOBAL");
            assertThat(defaults(host, dev.jasper.remote.ui.transfers.TransferUi.SHOW)).isEqualTo("cmd+alt+3|alt+3|GLOBAL");
            assertThat(defaults(host, RemotePlugin.SFTP)).as("Alt rule off macOS").isEqualTo("cmd+shift+o|derived|TERMINAL");
            assertThat(defaults(host, RemotePlugin.SPLIT)).isEqualTo("none|derived|TERMINAL");
            // Ruling G6: the SFTP panel's Alt+Left/Alt+Right/Alt+Up are panel shortcuts with those defaults.
            assertThat(defaults(host, SftpUi.BACK)).isEqualTo("alt+left|derived|PANEL");
            assertThat(defaults(host, SftpUi.FORWARD)).isEqualTo("alt+right|derived|PANEL");
            assertThat(defaults(host, SftpUi.PARENT)).isEqualTo("alt+up|derived|PANEL");
            for (String id : List.of(SftpUi.BACK, SftpUi.FORWARD, SftpUi.PARENT))
                assertThat(host.actionSpec(id).orElseThrow().context().id()).as("in the SFTP panel only").contains(SftpUi.PANEL);
            assertThat(host.settingsReports("dev.jasper.remote")).containsExactly("shortcuts.toggle_panel: Unknown setting or table; ignored.");
        }
    }

    @Test void newHostOpensAddHostWithoutTheSessionsPanelAndSitsInTheSshMenu(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                RemotePlugin plugin = plugin(dir.resolve("ssh"));
                host.start(INFO, Set.of(), Set.of(), plugin);
                settle(host);
                UUID window = host.addTerminalWindow();
                assertThat(defaults(host, RemotePlugin.NEW_HOST)).as("unbound until the user binds it").isEqualTo("none|derived|GLOBAL");
                assertThat(host.menu("top:" + RemotePlugin.MENU)).containsSubsequence("item:" + RemotePlugin.NEW_HOST, "item:" + RemotePlugin.IMPORT);
                assertThat(host.invoke(RemotePlugin.NEW_HOST, window, null)).isTrue();
                assertThat(host.failures()).isEmpty();
                assertThat(host.windows()).containsExactly("dialog|Add Host|true");
                assertThat(plugin.hostsPanel(window)).as("the Sessions panel stays closed").isNull();
            }
        });
    }

    private static String defaults(FakePluginHost host, String actionId) {
        var spec = host.actionSpec(actionId).orElseThrow();
        return spec.defaultBinding().orElse("none") + "|" + spec.otherDefaultBinding().orElse("derived") + "|" + spec.context().kind();
    }

    static javax.swing.JMenuItem menuItem(javax.swing.JPopupMenu menu,String text) {
        return java.util.Arrays.stream(menu.getComponents()).filter(javax.swing.JMenuItem.class::isInstance).map(javax.swing.JMenuItem.class::cast).filter(item->item.getText().equals(text)).findFirst().orElseThrow();
    }
    @Test void endingAPanesSessionClearsTheSftpView(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            Path files = java.nio.file.Files.createDirectories(dir.resolve("files"));
            java.nio.file.Files.writeString(files.resolve("readme.txt"), "x");
            server.server.setFileSystemFactory(new org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory(files.toRealPath()));
            server.server.setSubsystemFactories(List.of(new org.apache.sshd.sftp.server.SftpSubsystemFactory()));
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            plugin.store().put(prod);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var handle = context.terminals().window(window).orElseThrow();
            Kits.edt(() -> host.openPanel(SftpUi.PANEL, window));
            var panel = SftpTestAccess.panel(plugin.sftp(), window);
            Kits.edt(() -> plugin.openHost(handle, prod)); settle(host);
            Kits.edt(() -> plugin.openHost(handle, prod)); settle(host);
            UUID first = host.terminalPanes().getFirst(), second = host.terminalPanes().getLast();

            // The SFTP panel is a kit view: host events that reach it are delivered on the EDT, as the app delivers them.
            Kits.edt(() -> { host.focusTerminalPane(first); host.flush(); });
            awaitRowCount(host, panel, 1);
            Kits.edt(() -> host.typeIntoSession(first, "q"));
            awaitRowCount(host, panel, 0);
            assertThat(SftpTestAccess.directory(panel)).as("the shell exited").isEmpty();

            Kits.edt(() -> { host.focusTerminalPane(second); host.flush(); });
            awaitRowCount(host, panel, 1);
            Kits.edt(() -> { host.closeTerminalPane(second); host.flush(); });
            awaitRowCount(host, panel, 0);
            assertThat(SftpTestAccess.directory(panel)).as("the pane closed").isEmpty();
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    static void awaitRowCount(FakePluginHost host, SftpPanel panel, int count) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (SftpTestAccess.rows(panel).size() != count && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
        assertThat(SftpTestAccess.rows(panel)).hasSize(count);
    }

    /**
     * Drains the fake host's background work and events. The plugin's views are kit views, which live on the EDT: on the
     * EDT this runs inline; from the test thread each round hops to the EDT (running queued completions first), so the
     * caller can wait off the EDT between rounds and no worker/EDT hand-off can deadlock.
     */
    static void settle(FakePluginHost host) {
        if (java.awt.EventQueue.isDispatchThread()) { for (int i = 0; i < 20; i++) { host.runBackground(); host.flush(); } return; }
        try { Kits.settle(host); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    @Test void uploadingOntoExistingFilesAsksOnceAndReplaceCopies(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            Path files = java.nio.file.Files.createDirectories(dir.resolve("files"));
            java.nio.file.Files.writeString(files.resolve("readme.txt"), "old");
            server.server.setFileSystemFactory(new org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory(files.toRealPath()));
            server.server.setSubsystemFactories(List.of(new org.apache.sshd.sftp.server.SftpSubsystemFactory()));
            Path local = java.nio.file.Files.createDirectories(dir.resolve("local"));
            Path readme = java.nio.file.Files.writeString(local.resolve("readme.txt"), "new"), fresh = java.nio.file.Files.writeString(local.resolve("fresh.txt"), "fresh");
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            plugin.store().put(prod);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            Kits.edt(() -> host.openPanel(SftpUi.PANEL, window));
            var panel = SftpTestAccess.panel(plugin.sftp(), window);
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), prod)); settle(host);
            Kits.edt(() -> { host.focusTerminalPane(host.terminalPanes().getFirst()); host.flush(); });
            awaitRowCount(host, panel, 1);

            host.queuePathSelection(List.of(fresh));
            SftpTestAccess.upload(panel);
            awaitRemote(host, files.resolve("fresh.txt"), "fresh");
            assertThat(host.windowContent("dev.jasper.remote", "Items already exist")).as("nothing existed, nothing asked").isEmpty();

            java.nio.file.Files.writeString(fresh, "fresher");
            var before = jobs(plugin);
            host.queuePathSelection(List.of(readme, fresh));
            SftpTestAccess.upload(panel);
            var ask = awaitAsk(host);
            String where = SftpTestAccess.directory(panel);
            assertThat(Kits.<String>value(() -> Kits.node(ask, ExistingItemsPanel.TEXT, dev.jasper.sdk.ui.Nodes.Text.class).text())).isEqualTo("2 of 2 items already exist in prod:" + where + ".");
            assertThat(Kits.<dev.jasper.sdk.ui.Nodes.FormSubmission>value(() -> Kits.form(ask).submission())).as("Enter copies nothing").isEqualTo(dev.jasper.sdk.ui.Nodes.FormSubmission.NONE);
            Kits.edt(() -> Kits.click(ask, ExistingItemsPanel.CANCEL));
            settle(host);
            assertThat(host.windowContent("dev.jasper.remote", "Items already exist")).isEmpty();
            assertThat(jobs(plugin)).as("Cancel queues nothing").isSubsetOf(before);

            host.queuePathSelection(List.of(readme, fresh));
            SftpTestAccess.upload(panel);
            var skip = awaitAsk(host);
            Kits.edt(() -> Kits.click(skip, ExistingItemsPanel.SKIP));
            var skipped = awaitNewJob(host, plugin, before);
            assertThat(skipped.skippedEntries()).isEqualTo(2);
            assertThat(java.nio.file.Files.readString(files.resolve("readme.txt"))).as("Skip existing keeps the old file").isEqualTo("old");
            assertThat(java.nio.file.Files.readString(files.resolve("fresh.txt"))).isEqualTo("fresh");

            host.queuePathSelection(List.of(readme, fresh));
            SftpTestAccess.upload(panel);
            var replace = awaitAsk(host);
            Kits.edt(() -> Kits.click(replace, ExistingItemsPanel.REPLACE));
            awaitRemote(host, files.resolve("readme.txt"), "new");
            awaitRemote(host, files.resolve("fresh.txt"), "fresher");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** The open "Items already exist" question's kit view, once it shows. */
    static dev.jasper.sdk.testing.FakeKitView awaitAsk(FakePluginHost host) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (Kits.value(() -> host.windowView("dev.jasper.remote", "Items already exist")).isEmpty() && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
        return Kits.value(() -> host.windowView("dev.jasper.remote", "Items already exist").orElseThrow());
    }

    static Set<UUID> jobs(RemotePlugin plugin) throws Exception {
        var ids = new java.util.HashSet<UUID>();
        for (var job : plugin.transfers().snapshot(0, 50).get(5, java.util.concurrent.TimeUnit.SECONDS).jobs()) ids.add(job.id());
        return ids;
    }

    /** The one transfer queued since {@code before}, once it has finished. */
    static dev.jasper.remote.transfer.TransferJob awaitNewJob(FakePluginHost host, RemotePlugin plugin, Set<UUID> before) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            settle(host);
            for (var job : plugin.transfers().snapshot(0, 50).get(5, java.util.concurrent.TimeUnit.SECONDS).jobs())
                if (!before.contains(job.id()) && job.state().terminal()) return job;
            Thread.sleep(20);
        }
        throw new AssertionError("No new transfer finished");
    }

    static javax.swing.JButton button(java.awt.Container root, String name) {
        return find(root, javax.swing.JButton.class, candidate -> name.equals(candidate.getText()) || name.equals(candidate.getToolTipText())).orElseThrow();
    }

    static <T> Optional<T> find(java.awt.Component component, Class<T> type) { return find(component, type, candidate -> true); }

    static <T> Optional<T> find(java.awt.Component component, Class<T> type, java.util.function.Predicate<T> match) {
        if (type.isInstance(component) && match.test(type.cast(component))) return Optional.of(type.cast(component));
        if (component instanceof java.awt.Container container)
            for (var child : container.getComponents()) { var found = find(child, type, match); if (found.isPresent()) return found; }
        return Optional.empty();
    }

    static void awaitRemote(FakePluginHost host, Path file, String content) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            settle(host);
            if (java.nio.file.Files.isRegularFile(file) && java.nio.file.Files.readString(file).equals(content)) return;
            Thread.sleep(20);
        }
        throw new AssertionError(file + " never held " + content);
    }

    @Test void registersItsSurfaceAndConnectsThroughSessionsToolbar(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains("dev.jasper.remote.connect|Connect to SSH Host...|true", "dev.jasper.remote.hosts|Toggle SSH hosts|true", "dev.jasper.remote.sftp.toggle|Toggle SFTP|true",
                "dev.jasper.remote.split|Split with Same Host|false", "dev.jasper.remote.import|Import from ~/.ssh/config...|true");
            assertThat(host.panels()).containsExactly("dev.jasper.remote.sftp.panel|SFTP|LEFT+split|dev.jasper.remote.sftp.toggle",
                "dev.jasper.remote.tunnels.panel|Tunnels|LEFT|dev.jasper.remote.tunnels",
                "dev.jasper.remote.panel|Sessions|LEFT|dev.jasper.remote.hosts");
            assertThat(host.scopes()).containsExactly("dev.jasper.remote.tunnels.scope|Tunnels|toggle,browse,edit",
                "dev.jasper.remote.scope|SSH|connect,split,edit");
            assertThat(host.menu("top:dev.jasper.remote.menu")).isNotEmpty();
            assertThat(host.menu("VIEW")).as("View > Tool Windows already lists the three panel toggles").isEmpty();
            assertThat(host.status()).as("hidden at zero").isEmpty();
            assertThat(vault.consumers).containsExactly("dev.jasper.remote");

            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "Production", Optional.empty());
            plugin.store().put(prod);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            List<String> rows = host.searchScope(RemoteScope.ID, "", window, null);
            assertThat(rows).containsExactly("host." + prod.id() + "|prod|true");
            // The connecting overlay is a kit view (Task 8a): actions that open it run on the EDT.
            assertThat(Kits.value(() -> host.invoke("dev.jasper.remote.session.h" + prod.id(), window, null))).isTrue();
            assertThat(host.openRequests()).isEmpty();
            assertThat(host.windows()).containsExactly("overlay|Connecting to prod|true");
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), prod));
            assertThat(host.windows()).hasSize(1);
            settle(host);
            assertThat(host.openRequests()).containsExactly("session-tab|" + window + "|prod");
            assertThat(host.windows()).isEmpty();
            UUID pane = host.terminalPanes().getFirst();
            assertThat(host.sessionState(pane)).isEqualTo("RUNNING|");
            assertThat(host.status()).containsExactly("dev.jasper.remote.status|RIGHT|1 SSH session||dev.jasper.remote.hosts");
            Thread.sleep(300);
            assertThat(host.sessionOutput(pane)).startsWith("READY xterm-256color 80x24");
            assertThat(host.failures()).isEmpty();
            host.focusTerminalPane(pane);
            host.flush();
            assertThat(host.actions()).contains("dev.jasper.remote.split|Split with Same Host|true");
            assertThat(Kits.value(() -> host.invoke(RemotePlugin.SPLIT, window, pane))).isTrue();
            settle(host);
            assertThat(host.openRequests()).hasSize(2).last().asString().startsWith("session-split");
            assertThat(host.terminalPanes()).hasSize(2);
            assertThat(host.status().getFirst()).contains("2 SSH sessions");
            UUID second = host.terminalPanes().stream().filter(id -> !id.equals(pane)).findFirst().orElseThrow();
            host.focusTerminalPane(pane); host.flush();
            assertThat(Kits.value(() -> host.invoke("dev.jasper.remote.session.h" + prod.id(), window, null))).isTrue();
            assertThat(context.terminals().activePane()).get().extracting(dev.jasper.sdk.terminal.PaneHandle::id).isEqualTo(pane);
            host.focusTerminalPane(second); host.flush();
            assertThat(Kits.value(() -> host.invoke("dev.jasper.remote.session.h" + prod.id(), window, null))).isTrue();
            assertThat(context.terminals().activePane()).get().extracting(dev.jasper.sdk.terminal.PaneHandle::id).isEqualTo(second);
            assertThat(host.openRequests()).hasSize(2);
            host.closeTerminalPane(pane);
            host.flush();
            // The closed session's channel ends on its own thread; the count follows once that close is reported.
            long counted = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!host.status().getFirst().contains("1 SSH session") && System.nanoTime() < counted) { Thread.sleep(10); host.flush(); }
            assertThat(host.status().getFirst()).contains("1 SSH session");
            host.typeIntoSession(second, "q");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (!host.sessionState(second).startsWith("EXITED") && System.nanoTime() < deadline) { Thread.sleep(10); host.flush(); }
            assertThat(host.sessionState(second)).startsWith("EXITED");
            Kits.edt(() -> host.reconnectSession(second));
            assertThat(host.windows()).containsExactly("overlay|Connecting to prod|true");
            Kits.edt(() -> cancel(plugin.currentConnectionPanel())); settle(host);
            assertThat(host.sessionState(second)).startsWith("EXITED");
            assertThat(plugin.connections().channelCount()).isZero();
            Kits.edt(() -> host.reconnectSession(second)); settle(host);
            assertThat(host.sessionState(second)).isEqualTo("RUNNING|");
            assertThat(host.openRequests()).hasSize(2);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void followedPanesGetTheirOwnConnectionAndProbeTheirFolder(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            server.execReplies(Map.of("uname -s", "Linux\n", "cat /etc/os-release", "", "sh -s", "jasper-cwd\0/srv/app\0"));
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost shared = RemoteHost.create("shared", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            RemoteHost followed = RemoteHost.create("followed", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty());
            plugin.store().put(shared); plugin.store().put(followed);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var handle = context.terminals().window(window).orElseThrow();
            Kits.edt(() -> plugin.openHost(handle, shared)); settle(host);
            Kits.edt(() -> plugin.openHost(handle, shared)); settle(host);
            assertThat(server.server.getActiveSessions()).as("unfollowed panes share a connection").hasSize(1);
            Kits.edt(() -> plugin.openHost(handle, followed)); settle(host);
            Kits.edt(() -> plugin.openHost(handle, followed)); settle(host);
            assertThat(server.server.getActiveSessions()).as("each followed pane has its own").hasSize(3);

            UUID followedPane = host.terminalPanes().getLast();
            host.focusTerminalPane(followedPane); host.flush();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (context.terminals().pane(followedPane).orElseThrow().info().remoteDirectory().isEmpty() && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
            assertThat(server.execCommands).as("focus probes the followed pane with SFTP closed").contains("sh -s");
            assertThat(context.terminals().pane(followedPane).orElseThrow().info().remoteDirectory())
                .contains(new dev.jasper.sdk.terminal.RemoteDirectory("127.0.0.1", "/srv/app"));
            server.execReplies(Map.of("uname -s", "Linux\n", "cat /etc/os-release", "", "sh -s", "jasper-cwd\0/srv/changed\0"));

            long before = server.execCommands.stream().filter("sh -s"::equals).count();
            host.typeIntoSession(followedPane, "\r");
            deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!context.terminals().pane(followedPane).orElseThrow().info().remoteDirectory().map(directory -> directory.path().equals("/srv/changed")).orElse(false) && System.nanoTime() < deadline) {
                var tasks = List.copyOf(scheduled); scheduled.clear(); tasks.forEach(Runnable::run);
                settle(host); Thread.sleep(20);
            }
            assertThat(server.execCommands.stream().filter("sh -s"::equals).count()).as("Enter probes again").isGreaterThan(before);
            assertThat(context.terminals().pane(followedPane).orElseThrow().info().remoteDirectory())
                .contains(new dev.jasper.sdk.terminal.RemoteDirectory("127.0.0.1", "/srv/changed"));
            host.remoteCwdChanged(followedPane, "reported-host", "/shell/path"); host.flush();
            long reportedProbes = server.execCommands.stream().filter("sh -s"::equals).count();
            host.typeIntoSession(followedPane, "\r");
            var pendingTasks = List.copyOf(scheduled); scheduled.clear(); pendingTasks.forEach(Runnable::run);
            host.focusTerminalPane(followedPane); settle(host);
            assertThat(server.execCommands.stream().filter("sh -s"::equals).count()).isEqualTo(reportedProbes);
            assertThat(context.terminals().pane(followedPane).orElseThrow().info().remoteDirectory())
                .contains(new dev.jasper.sdk.terminal.RemoteDirectory("reported-host", "/shell/path"));

            long probes = server.execCommands.stream().filter("sh -s"::equals).count();
            host.focusTerminalPane(host.terminalPanes().getFirst()); host.flush(); settle(host);
            assertThat(server.execCommands.stream().filter("sh -s"::equals).count()).as("unfollowed panes are never probed").isEqualTo(probes);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void sessionsToolbarTracksSavedHostsAndCleansUp(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                RemotePlugin plugin = plugin(dir);
                host.start(INFO, Set.of(), Set.of(), plugin);
                settle(host);
                String manage = "dev.jasper.remote.sessions.manage";
                assertThat(host.toolbar()).containsExactly("menu:Sessions:" + manage);
                assertThat(host.actions()).contains(manage + "|Manage Sessions...|true");
                UUID window = host.addTerminalWindow();
                assertThat(host.invoke(manage, window, null)).isTrue();
                assertThat(host.openPanel(RemotePlugin.PANEL, window)).as("a kit view, no raw component").isNotNull();
                assertThat(plugin.hostsPanel(window)).isNotNull();

                var zebra = RemoteHost.create("Zebra", "z.example", 22, "me", new Auth.Agent(), "", Optional.empty());
                var alpha = RemoteHost.create("alpha", "a.example", 2222, "root", new Auth.Agent(), "", Optional.empty());
                plugin.store().put(zebra); plugin.store().put(alpha); settle(host);
                String zebraAction = "dev.jasper.remote.session.h" + zebra.id();
                String alphaAction = "dev.jasper.remote.session.h" + alpha.id();
                assertThat(host.toolbar()).containsExactly("menu:Sessions:" + alphaAction + "," + zebraAction + "," + manage);
                assertThat(host.actions()).contains(alphaAction + "|alpha (root@a.example:2222)|true");

                var renamed = zebra.withEdited("Aardvark", "new.example", 2200, "deploy", zebra.auth(), "", Optional.empty());
                plugin.store().put(renamed); settle(host);
                assertThat(host.toolbar()).containsExactly("menu:Sessions:" + zebraAction + "," + alphaAction + "," + manage);
                assertThat(host.actions()).contains(zebraAction + "|Aardvark (deploy@new.example:2200)|true");
                assertThat(host.invoke(zebraAction, window, null)).isTrue();
                assertThat(host.windows()).containsExactly("overlay|Connecting to Aardvark|true");
                cancel(plugin.currentConnectionPanel()); settle(host);

                plugin.store().remove(zebra.id()); settle(host);
                assertThat(host.toolbar()).containsExactly("menu:Sessions:" + alphaAction + "," + manage);
                assertThat(host.invoke(zebraAction, window, null)).isFalse();
                plugin.store().remove(alpha.id()); settle(host);
                assertThat(host.toolbar()).containsExactly("menu:Sessions:" + manage);
                host.stopAll();
                assertThat(host.toolbar()).isEmpty();
                assertThat(host.actions()).isEmpty();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    @Test void hostKeyPromptIsAWindowModalDialog(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                RemotePlugin plugin = plugin(dir);
                var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
                host.addTerminalWindow();
                CompletableFuture<HostKeyVerifier.Decision> decision = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-ed25519", "SHA256:x"));
                assertThat(host.windows()).containsExactly("dialog|Verify host key h|true");
                trust(plugin.currentHostKeyPanel());
                assertThat(decision).isCompletedWithValue(HostKeyVerifier.Decision.TRUST);
                assertThat(host.windows()).isEmpty();
                CompletableFuture<HostKeyVerifier.Decision> closed = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-ed25519", "SHA256:x"));
                host.requestClose("dialog");
                assertThat(closed).isCompletedWithValue(HostKeyVerifier.Decision.CANCEL);
            }
        });
    }

    @Test void thePanelAddsEditsDeletesAndImports(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            Path sshDir = Files.createDirectories(dir.resolve("ssh"));
            Files.writeString(sshDir.resolve("config"), "Host imported\n  HostName imported.example\n  User me\n");
            try (var host = new FakePluginHost()) {
                var vault = new FakeVault(); vault.password("Imported login", "me", "secret");
                host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
                RemotePlugin plugin = plugin(sshDir);
                var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
                UUID window = host.addTerminalWindow();
                host.activateTerminalWindow(window);
                host.openPanel(RemotePlugin.PANEL, window);
                var panel = plugin.hostsPanel(window);
                assertThat(panel).isNotNull();
                headerAction(host, window, "New session");
                assertThat(host.windows()).containsExactly("dialog|Add Host|true");
                name(plugin.currentEditor(), "new"); hostname(plugin.currentEditor(), "new.example"); agentAuth(plugin.currentEditor());
                username(plugin.currentEditor(), "root");
                save(plugin.currentEditor());
                settle(host);
                assertThat(host.windows()).isEmpty();
                assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
                assertThat(rows(panel)).as("ungrouped host without a redundant header").hasSize(1);
                assertThat(host.panelHeader(RemotePlugin.PANEL, window).detail()).isEmpty();
                assertThat(((dev.jasper.sdk.ui.Nodes.Panel)panel.view().spec().root()).header().count()).hasValue(1);
                panel.select(plugin.store().hosts().getFirst().id());
                assertThat(cardCredential(panel)).isEqualTo("SSH agent");
                assertThat(host.invoke(RemotePlugin.IMPORT, window, null)).isTrue();
                settle(host);
                assertThat(host.windows()).containsExactly("dialog|Import from ~/.ssh/config|true");
                chooseCredential(plugin.currentImport(), 0);
                importButton(plugin.currentImport());
                settle(host);
                assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new", "imported");
                RemoteHost imported = plugin.store().hosts().get(1);
                cancelImport(plugin.currentImport());
                menu(panel, indexOf(panel, imported), "Delete…");
                assertThat(host.windows()).containsExactly("dialog|Delete imported?|true");
                Kits.click(plugin.currentConfirm().view(), dev.jasper.remote.ui.ConfirmPanel.CONFIRM);
                settle(host);
                assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
                menu(panel, 0, "Rename group\u2026");
                groupName(plugin.currentGroupEditor(), "Servers");
                saveGroup(plugin.currentGroupEditor()); settle(host);
                toggle(panel, 0);
                settle(host);
                var savedPanelState = new PanelState(context.dataDirectory().resolve("panel-state.toml"));
                assertThat(savedPanelState.defaultGroup()).isEqualTo("Servers");
                assertThat(savedPanelState.collapsed()).containsExactly("Servers");
            }
        });
    }

    /** Runs a Sessions header action through the testkit's published header, as the window's header button would. */
    static void headerAction(FakePluginHost host, UUID window, String label) {
        host.panelHeader(RemotePlugin.PANEL, window).actions().stream().filter(action -> action.label().equals(label)).findFirst()
            .orElseThrow(() -> new AssertionError("No header action " + label)).invoke().run();
    }

    static int indexOf(HostsPanel panel, RemoteHost host) {
        for (int i = 0; i < rows(panel).size(); i++)
            if (rows(panel).get(i) instanceof dev.jasper.remote.ui.HostRows.Host row && row.host().id().equals(host.id())) return i;
        throw new AssertionError("host row not shown");
    }
    @Test void cancellationClosesTheHostKeyDialog(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                RemotePlugin plugin = plugin(dir);
                host.start(INFO, Set.of(), Set.of(), plugin);
                host.addTerminalWindow();
                var pending = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-rsa", "SHA256:x"));
                pending.cancel(true);
                assertThat(host.windows()).isEmpty();
            }
        });
    }

    @Test void hostsActionCreatesThePanelWithoutOpeningThePalette(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                var plugin = plugin(dir);
                host.start(INFO, Set.of(), Set.of(), plugin);
                UUID window = host.addTerminalWindow();
                host.invoke(RemotePlugin.HOSTS, window, null);
                assertThat(host.paletteOpens()).isEmpty();
                assertThat(host.openPanel(RemotePlugin.PANEL, window)).isNotNull();
            }
        });
    }

    @Test void failedMutationsReportAfterTheEditorCloses(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                var plugin = plugin(dir);
                var context = host.start(INFO, Set.of(), Set.of(), plugin);
                UUID window = host.addTerminalWindow();
                host.openPanel(RemotePlugin.PANEL, window);
                var panel = plugin.hostsPanel(window);
                var saved = RemoteHost.create("saved", "example", 22, "me", Auth.AGENT, "", Optional.empty());
                plugin.store().put(saved); settle(host);
                Files.writeString(context.dataDirectory().resolve("hosts.toml"), "broken = [");
                int row = indexOf(panel, saved);
                menu(panel, row, "Duplicate");
                menu(panel, row, "Add to favorites");
                headerAction(host, window, "New session");
                name(plugin.currentEditor(), "new"); hostname(plugin.currentEditor(), "example");
                agentAuth(plugin.currentEditor()); username(plugin.currentEditor(), "me");
                save(plugin.currentEditor());
                host.requestClose("dialog");
                settle(host);
                assertThat(host.notifications().posts()).extracting(post -> post.spec().title())
                    .containsExactlyInAnyOrder("Could not duplicate the host", "Could not update favorites", "Could not save the host");
                assertThat(host.notifications().posts()).allSatisfy(post -> assertThat(post.spec().cause()).isPresent());
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    @Test void cancelledConnectCreatesNoTabOrRetainedChannel(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var saved = RemoteHost.create("test", "127.0.0.1", server.port(), "deploy", new Auth.Vault(vault.password("p", "deploy", "s3cret")), "", Optional.empty());
            plugin.store().put(saved); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), saved));
            assertThat(host.requestClose("overlay")).isFalse();
            Kits.edt(() -> cancel(plugin.currentConnectionPanel()));
            settle(host);
            assertThat(host.openRequests()).isEmpty();
            assertThat(plugin.connections().channelCount()).isZero();
            assertThat(host.windows()).isEmpty();
        }
    }

    @Test void failedConnectStaysInOverlayAndRetriesWithoutAnEmptyTab(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("p", "deploy", "wrong");
            var saved = RemoteHost.create("test", "127.0.0.1", server.port(), "deploy", new Auth.Vault(credential), "", Optional.empty());
            plugin.store().put(saved); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), saved)); settle(host);
            assertThat(host.openRequests()).isEmpty();
            assertThat(host.windows()).hasSize(1);
            assertThat(connectionStatus(plugin.currentConnectionPanel())).contains("Authentication failed");
            vault.secrets.put(credential, () -> new dev.jasper.vault.api.Credential(credential, "p", dev.jasper.vault.api.Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null));
            Kits.edt(() -> retry(plugin.currentConnectionPanel())); settle(host);
            assertThat(host.openRequests()).hasSize(1);
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void renamesDefaultGroupThroughThePanel(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                var plugin = plugin(dir);
                var context = host.start(INFO, Set.of(), Set.of(), plugin);
                UUID window = host.addTerminalWindow();
                var saved = RemoteHost.create("home", "example", 22, "me", Auth.AGENT, "", Optional.empty());
                plugin.store().put(saved); settle(host);
                host.openPanel(RemotePlugin.PANEL, window);
                var panel = plugin.hostsPanel(window);
                menu(panel, 0, "Rename group\u2026");
                groupName(plugin.currentGroupEditor(), "My machines");
                saveGroup(plugin.currentGroupEditor()); settle(host);
                assertThat(host.windows()).isEmpty();
                assertThat(rows(panel).get(0)).isInstanceOfSatisfying(dev.jasper.remote.ui.HostRows.Group.class,
                    group -> assertThat(group.name()).isEqualTo("My machines"));
                assertThat(new PanelState(context.dataDirectory().resolve("panel-state.toml")).defaultGroup()).isEqualTo("My machines");
                assertThat(plugin.store().hosts().getFirst().group()).isEmpty();
            }
        });
    }

    static final ShellProfileInfo UBUNTU = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));

    @Test void wslDistributionsOpenLocalPanesFromThePanelAndCountTheirSessions(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                host.setShellProfiles(List.of(new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty())));
                RemotePlugin plugin = plugin(dir);
                var context = host.start(INFO, Set.of(), Set.of(), plugin);
                UUID window = host.addTerminalWindow();
                host.activateTerminalWindow(window);
                host.openPanel(RemotePlugin.PANEL, window);
                var panel = plugin.hostsPanel(window);
                assertThat(rows(panel)).as("no WSL group without distributions").isEmpty();

                host.setShellProfiles(List.of(UBUNTU));
                settle(host);
                assertThat(rows(panel).get(0)).isEqualTo(new HostRows.Group(HostRows.WSL, 1, false, true));
                assertThat(rows(panel).get(1)).isEqualTo(new HostRows.Distro(UBUNTU, 0));
                activate(panel, 1);
                settle(host);
                assertThat(host.openRequests()).containsExactly("tab|" + window + "|-|wsl:Ubuntu");
                UUID first = host.terminalPanes().getLast();
                assertThat(rows(panel).get(1)).isEqualTo(new HostRows.Distro(UBUNTU, 1));

                host.focusTerminalPane(first); host.flush();
                menu(panel, 1, "Open in split");
                settle(host);
                assertThat(host.openRequests()).last().isEqualTo("split|" + first + "|RIGHT|-|wsl:Ubuntu");
                assertThat(rows(panel).get(1)).isEqualTo(new HostRows.Distro(UBUNTU, 2));
                UUID second = host.terminalPanes().getLast();
                host.sessionExited(second, OptionalInt.of(0)); settle(host);
                assertThat(rows(panel).get(1)).as("an exited shell is not a session").isEqualTo(new HostRows.Distro(UBUNTU, 1));
                host.closeTerminalPane(first); settle(host);
                assertThat(rows(panel).get(1)).isEqualTo(new HostRows.Distro(UBUNTU, 0));
                assertThat(host.status()).as("the status item counts SSH sessions only").isEmpty();

                toggle(panel, 0); settle(host);
                assertThat(Files.readString(context.dataDirectory().resolve("panel-state.toml")))
                    .as("the WSL group's own flag, not a host group name").contains("wsl_collapsed = true").doesNotContain("\"wsl\"");
                assertThat(new PanelState(context.dataDirectory().resolve("panel-state.toml")).collapsed()).isEmpty();
                host.setShellProfiles(List.of()); settle(host);
                assertThat(rows(panel)).as("the group leaves with the last distribution").isEmpty();
                host.stopAll();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    @Test void sessionsToolbarAndPaletteListWslDistributionsFirst(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                host.setShellProfiles(List.of(UBUNTU));
                RemotePlugin plugin = plugin(dir);
                host.start(INFO, Set.of(), Set.of(), plugin);
                settle(host);
                String manage = "dev.jasper.remote.sessions.manage";
                String ubuntu = "dev.jasper.remote.session.w" + UUID.nameUUIDFromBytes("wsl:Ubuntu".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var alpha = RemoteHost.create("alpha", "a.example", 22, "root", new Auth.Agent(), "", Optional.empty());
                plugin.store().put(alpha); settle(host);
                String alphaAction = "dev.jasper.remote.session.h" + alpha.id();
                assertThat(host.toolbar()).containsExactly("menu:Sessions:" + ubuntu + "," + alphaAction + "," + manage);
                assertThat(host.actions()).contains(ubuntu + "|Ubuntu (WSL 2)|true");
                UUID window = host.addTerminalWindow();
                host.activateTerminalWindow(window);
                assertThat(host.invoke(ubuntu, window, null)).isTrue();
                assertThat(host.openRequests()).containsExactly("tab|" + window + "|-|wsl:Ubuntu");

                assertThat(host.searchScope(RemoteScope.ID, "", window, null)).containsExactly("wsl.Ubuntu|Ubuntu|true", "host." + alpha.id() + "|alpha|true");
                assertThat(host.availableInScope(RemoteScope.ID, "wsl.Ubuntu", "edit", window, null)).isFalse();
                host.executeInScope(RemoteScope.ID, "wsl.Ubuntu", "connect", window, null);
                assertThat(host.openRequests()).hasSize(2).last().isEqualTo("tab|" + window + "|-|wsl:Ubuntu");

                host.setShellProfiles(List.of()); settle(host);
                assertThat(host.toolbar()).containsExactly("menu:Sessions:" + alphaAction + "," + manage);
                assertThat(host.invoke(ubuntu, window, null)).isFalse();
                assertThat(host.searchScope(RemoteScope.ID, "", window, null)).containsExactly("host." + alpha.id() + "|alpha|true");
                host.stopAll();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

}
