package dev.jasper.remote;

import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.RemoteScope;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import dev.jasper.remote.ui.sftp.SftpUi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.ui.UiTestAccess.*;
import static org.assertj.core.api.Assertions.*;

class RemotePluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.2.0",
        Set.of(Capabilities.TERMINAL_OPEN, Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));

    final List<Runnable> scheduled = new ArrayList<>();

    RemotePlugin plugin(Path sshDir) {
        return new TestRemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
    }

    @Test void hostSelectsNetworkArtworkForEitherSkin(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            var delegate = plugin(dir.resolve("ssh"));
            var icons = new ArrayList<dev.jasper.sdk.testing.FakeNamedIcon>();
            host.start(INFO, Set.of(), Set.of(), new dev.jasper.sdk.plugin.Plugin() {
                public void start(dev.jasper.sdk.plugin.PluginContext context) throws Exception {
                    var appearance = (dev.jasper.sdk.ui.Appearance) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{dev.jasper.sdk.ui.Appearance.class}, (proxy, method, args) -> {
                            if (method.getName().equals("icon")) {
                                assertThat(args[0]).isInstanceOf(dev.jasper.sdk.ui.IconName.class);
                            }
                            try {
                                Object value = method.invoke(context.appearance(), args);
                                if (value instanceof dev.jasper.sdk.testing.FakeNamedIcon icon) icons.add(icon);
                                return value;
                            } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                        });
                    var wrapped = (dev.jasper.sdk.plugin.PluginContext) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{dev.jasper.sdk.plugin.PluginContext.class}, (proxy, method, args) -> {
                            if (method.getName().equals("appearance")) return appearance;
                            try { return method.invoke(context, args); }
                            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                        });
                    delegate.start(wrapped);
                }
                public void stop() { delegate.stop(); }
            });
            settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(icons).anyMatch(icon->icon.name()==dev.jasper.sdk.ui.IconName.NETWORK);
            assertThat(icons).as("session tab icon").anyMatch(icon->icon.name()==dev.jasper.sdk.ui.IconName.SERVER);
            assertThat(icons).allSatisfy(icon->assertThat(icon.retro()).isEqualTo(false));
            assertThat(host.toolbar()).anyMatch(item -> item.contains("Sessions"));
            assertThat(host.panels()).containsExactly("dev.jasper.remote.sftp.panel|SFTP|LEFT","dev.jasper.remote.panel|SSH hosts|LEFT");
        }
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
            var panel = (dev.jasper.remote.ui.sftp.SftpPanel) host.openPanel(SftpUi.PANEL, window);
            plugin.openHost(handle, prod); settle(host);
            plugin.openHost(handle, prod); settle(host);
            UUID first = host.terminalPanes().getFirst(), second = host.terminalPanes().getLast();

            host.focusTerminalPane(first); host.flush();
            awaitRowCount(host, panel, 1);
            host.typeIntoSession(first, "q");
            awaitRowCount(host, panel, 0);
            assertThat(panel.directory()).as("the shell exited").isEmpty();

            host.focusTerminalPane(second); host.flush();
            awaitRowCount(host, panel, 1);
            host.closeTerminalPane(second); host.flush();
            awaitRowCount(host, panel, 0);
            assertThat(panel.directory()).as("the pane closed").isEmpty();
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    static void awaitRowCount(FakePluginHost host, dev.jasper.remote.ui.sftp.SftpPanel panel, int count) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (panel.table().getRowCount() != count && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
        assertThat(panel.table().getRowCount()).isEqualTo(count);
    }

    static void settle(FakePluginHost host) { for (int i = 0; i < 20; i++) { host.runBackground(); host.flush(); } }

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
            var panel = (dev.jasper.remote.ui.sftp.SftpPanel) host.openPanel(SftpUi.PANEL, window);
            plugin.openHost(context.terminals().window(window).orElseThrow(), prod); settle(host);
            host.focusTerminalPane(host.terminalPanes().getFirst()); host.flush();
            awaitRowCount(host, panel, 1);

            host.queuePathSelection(List.of(fresh));
            button(panel, "Upload files").doClick();
            awaitRemote(host, files.resolve("fresh.txt"), "fresh");
            assertThat(host.windowContent("dev.jasper.remote", "Items already exist")).as("nothing existed, nothing asked").isEmpty();

            java.nio.file.Files.writeString(fresh, "fresher");
            var before = jobs(plugin);
            host.queuePathSelection(List.of(readme, fresh));
            button(panel, "Upload files").doClick();
            var ask = awaitAsk(host);
            assertThat(find(ask, javax.swing.JLabel.class).orElseThrow().getText()).isEqualTo("2 of 2 items already exist in prod:" + panel.directory() + ".");
            button(ask, "Cancel").doClick();
            settle(host);
            assertThat(host.windowContent("dev.jasper.remote", "Items already exist")).isEmpty();
            assertThat(jobs(plugin)).as("Cancel queues nothing").isSubsetOf(before);

            host.queuePathSelection(List.of(readme, fresh));
            button(panel, "Upload files").doClick();
            button(awaitAsk(host), "Skip existing").doClick();
            var skipped = awaitNewJob(host, plugin, before);
            assertThat(skipped.skippedEntries()).isEqualTo(2);
            assertThat(java.nio.file.Files.readString(files.resolve("readme.txt"))).as("Skip existing keeps the old file").isEqualTo("old");
            assertThat(java.nio.file.Files.readString(files.resolve("fresh.txt"))).isEqualTo("fresh");

            host.queuePathSelection(List.of(readme, fresh));
            button(panel, "Upload files").doClick();
            button(awaitAsk(host), "Replace").doClick();
            awaitRemote(host, files.resolve("readme.txt"), "new");
            awaitRemote(host, files.resolve("fresh.txt"), "fresher");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    static dev.jasper.remote.ui.transfers.ExistingItemsPanel awaitAsk(FakePluginHost host) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (host.windowContent("dev.jasper.remote", "Items already exist").isEmpty() && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
        return (dev.jasper.remote.ui.transfers.ExistingItemsPanel) host.windowContent("dev.jasper.remote", "Items already exist").orElseThrow();
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
            assertThat(host.actions()).contains("dev.jasper.remote.connect|Connect to SSH Host...|true", "dev.jasper.remote.hosts|SSH Hosts|true",
                "dev.jasper.remote.split|Split with Same Host|false", "dev.jasper.remote.import|Import from ~/.ssh/config...|true");
            assertThat(host.panels()).containsExactly("dev.jasper.remote.sftp.panel|SFTP|LEFT","dev.jasper.remote.panel|SSH hosts|LEFT");
            assertThat(host.scopes()).containsExactly("dev.jasper.remote.scope|SSH|connect,split,edit");
            assertThat(host.menu("top:dev.jasper.remote.menu")).isNotEmpty();
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
            assertThat(host.invoke("dev.jasper.remote.session.h" + prod.id(), window, null)).isTrue();
            assertThat(host.openRequests()).isEmpty();
            assertThat(host.windows()).containsExactly("overlay|Connecting to prod|true");
            plugin.openHost(context.terminals().window(window).orElseThrow(), prod);
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
            assertThat(host.invoke(RemotePlugin.SPLIT, window, pane)).isTrue();
            settle(host);
            assertThat(host.openRequests()).hasSize(2).last().asString().startsWith("session-split");
            assertThat(host.terminalPanes()).hasSize(2);
            assertThat(host.status().getFirst()).contains("2 SSH sessions");
            UUID second = host.terminalPanes().stream().filter(id -> !id.equals(pane)).findFirst().orElseThrow();
            host.focusTerminalPane(pane); host.flush();
            assertThat(host.invoke("dev.jasper.remote.session.h" + prod.id(), window, null)).isTrue();
            assertThat(context.terminals().activePane()).get().extracting(dev.jasper.sdk.terminal.PaneHandle::id).isEqualTo(pane);
            host.focusTerminalPane(second); host.flush();
            assertThat(host.invoke("dev.jasper.remote.session.h" + prod.id(), window, null)).isTrue();
            assertThat(context.terminals().activePane()).get().extracting(dev.jasper.sdk.terminal.PaneHandle::id).isEqualTo(second);
            assertThat(host.openRequests()).hasSize(2);
            host.closeTerminalPane(pane);
            host.flush();
            assertThat(host.status().getFirst()).contains("1 SSH session");
            host.typeIntoSession(second, "q");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (!host.sessionState(second).startsWith("EXITED") && System.nanoTime() < deadline) { Thread.sleep(10); host.flush(); }
            assertThat(host.sessionState(second)).startsWith("EXITED");
            host.reconnectSession(second);
            assertThat(host.windows()).containsExactly("overlay|Connecting to prod|true");
            cancel(plugin.currentConnectionPanel()).doClick(); settle(host);
            assertThat(host.sessionState(second)).startsWith("EXITED");
            assertThat(plugin.connections().channelCount()).isZero();
            host.reconnectSession(second); settle(host);
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
            plugin.openHost(handle, shared); settle(host);
            plugin.openHost(handle, shared); settle(host);
            assertThat(server.server.getActiveSessions()).as("unfollowed panes share a connection").hasSize(1);
            plugin.openHost(handle, followed); settle(host);
            plugin.openHost(handle, followed); settle(host);
            assertThat(server.server.getActiveSessions()).as("each followed pane has its own").hasSize(3);

            assertThat(host.openPanel(SftpUi.PANEL, window)).isNotNull();
            UUID followedPane = host.terminalPanes().getLast();
            host.focusTerminalPane(followedPane); host.flush();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!server.execCommands.contains("sh -s") && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
            assertThat(server.execCommands).as("focus probes the followed pane").contains("sh -s");

            long before = server.execCommands.stream().filter("sh -s"::equals).count();
            host.typeIntoSession(followedPane, "\r");
            deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (server.execCommands.stream().filter("sh -s"::equals).count() == before && System.nanoTime() < deadline) {
                var tasks = List.copyOf(scheduled); scheduled.clear(); tasks.forEach(Runnable::run);
                settle(host); Thread.sleep(20);
            }
            assertThat(server.execCommands.stream().filter("sh -s"::equals).count()).as("Enter probes again").isGreaterThan(before);

            long probes = server.execCommands.stream().filter("sh -s"::equals).count();
            host.focusTerminalPane(host.terminalPanes().getFirst()); host.flush(); settle(host);
            assertThat(server.execCommands.stream().filter("sh -s"::equals).count()).as("unfollowed panes are never probed").isEqualTo(probes);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void sessionsToolbarTracksSavedHostsAndCleansUp(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            RemotePlugin plugin = plugin(dir);
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            String manage = "dev.jasper.remote.sessions.manage";
            assertThat(host.toolbar()).containsExactly("menu:Sessions:" + manage);
            assertThat(host.actions()).contains(manage + "|Manage Sessions...|true");
            UUID window = host.addTerminalWindow();
            assertThat(host.invoke(manage, window, null)).isTrue();
            assertThat(host.openPanel(RemotePlugin.PANEL, window)).isInstanceOf(HostsPanel.class);

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
            cancel(plugin.currentConnectionPanel()).doClick(); settle(host);

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
    }

    @Test void hostKeyPromptIsAWindowModalDialog(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            RemotePlugin plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            host.addTerminalWindow();
            CompletableFuture<HostKeyVerifier.Decision> decision = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-ed25519", "SHA256:x"));
            assertThat(host.windows()).containsExactly("dialog|Verify host key h|true");
            trust(plugin.currentHostKeyPanel()).doClick();
            assertThat(decision).isCompletedWithValue(HostKeyVerifier.Decision.TRUST);
            assertThat(host.windows()).isEmpty();
            CompletableFuture<HostKeyVerifier.Decision> closed = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-ed25519", "SHA256:x"));
            host.requestClose("dialog");
            assertThat(closed).isCompletedWithValue(HostKeyVerifier.Decision.CANCEL);
        }
    }

    @Test void thePanelAddsEditsDeletesAndImports(@TempDir Path dir) throws Exception {
        Path sshDir = Files.createDirectories(dir.resolve("ssh"));
        Files.writeString(sshDir.resolve("config"), "Host imported\n  HostName imported.example\n  User me\n");
        try (var host = new FakePluginHost()) {
            var vault = new FakeVault(); vault.password("Imported login", "me", "secret");
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(sshDir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var panel = (HostsPanel) host.openPanel(RemotePlugin.PANEL, window);
            assertThat(panel).isNotNull();
            add(panel).doClick();
            assertThat(host.windows()).containsExactly("dialog|Add Host|true");
            name(plugin.currentEditor()).setText("new"); hostname(plugin.currentEditor()).setText("new.example"); agentAuth(plugin.currentEditor()).doClick();
            username(plugin.currentEditor()).setText("root");
            save(plugin.currentEditor()).doClick();
            settle(host);
            assertThat(host.windows()).isEmpty();
            assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
            assertThat(list(panel).getModel().getSize()).as("Other group + host").isEqualTo(2);
            panel.select(plugin.store().hosts().getFirst().id());
            assertThat(cardCredential(panel).getText()).isEqualTo("SSH agent");
            assertThat(host.invoke(RemotePlugin.IMPORT, window, null)).isTrue();
            settle(host);
            assertThat(host.windows()).containsExactly("dialog|Import from ~/.ssh/config|true");
            chooseCredential(plugin.currentImport(), 0).doClick();
            importButton(plugin.currentImport()).doClick();
            settle(host);
            assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new", "imported");
            RemoteHost imported = plugin.store().hosts().get(1);
            cancelImport(plugin.currentImport()).doClick();
            javax.swing.JPopupMenu menu = menuFor(panel, indexOf(panel, imported));
            menuItem(menu,"Delete…").doClick();
            assertThat(host.windows()).containsExactly("dialog|Delete imported?|true");
            plugin.currentConfirm().confirm.doClick();
            settle(host);
            assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
            toggle(panel, 0);
            settle(host);
            assertThat(Files.readString(context.dataDirectory().resolve("panel-state.toml"))).contains("Other");
        }
    }

    static int indexOf(HostsPanel panel, RemoteHost host) {
        for (int i = 0; i < list(panel).getModel().getSize(); i++)
            if (list(panel).getModel().getElementAt(i) instanceof dev.jasper.remote.ui.HostRows.Host row && row.host().id().equals(host.id())) return i;
        throw new AssertionError("host row not shown");
    }
    @Test void cancellationClosesTheHostKeyDialog(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            RemotePlugin plugin = plugin(dir);
            host.start(INFO, Set.of(), Set.of(), plugin);
            host.addTerminalWindow();
            var pending = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-rsa", "SHA256:x"));
            pending.cancel(true);
            assertThat(host.windows()).isEmpty();
        }
    }

    @Test void hostsActionCreatesThePanelWithoutOpeningThePalette(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            var plugin = plugin(dir);
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            host.invoke(RemotePlugin.HOSTS, window, null);
            assertThat(host.paletteOpens()).isEmpty();
            assertThat(host.openPanel(RemotePlugin.PANEL, window)).isNotNull();
        }
    }

    @Test void failedMutationsReportAfterTheEditorCloses(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            var panel = (HostsPanel) host.openPanel(RemotePlugin.PANEL, window);
            var saved = RemoteHost.create("saved", "example", 22, "me", Auth.AGENT, "", Optional.empty());
            plugin.store().put(saved); settle(host);
            Files.writeString(context.dataDirectory().resolve("hosts.toml"), "broken = [");
            var menu = menuFor(panel, indexOf(panel, saved));
            menuItem(menu,"Duplicate").doClick();
            menuItem(menu,"Add to favorites").doClick();
            add(panel).doClick();
            name(plugin.currentEditor()).setText("new"); hostname(plugin.currentEditor()).setText("example");
            agentAuth(plugin.currentEditor()).doClick(); username(plugin.currentEditor()).setText("me");
            save(plugin.currentEditor()).doClick();
            host.requestClose("dialog");
            settle(host);
            assertThat(host.notices()).hasSize(3);
            assertThat(host.failures()).isEmpty();
        }
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
            plugin.openHost(context.terminals().window(window).orElseThrow(), saved);
            assertThat(host.requestClose("overlay")).isFalse();
            cancel(plugin.currentConnectionPanel()).doClick();
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
            plugin.openHost(context.terminals().window(window).orElseThrow(), saved); settle(host);
            assertThat(host.openRequests()).isEmpty();
            assertThat(host.windows()).hasSize(1);
            assertThat(connectionStatus(plugin.currentConnectionPanel())).contains("Authentication failed");
            vault.secrets.put(credential, () -> new dev.jasper.vault.api.Credential(credential, "p", dev.jasper.vault.api.Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null));
            retry(plugin.currentConnectionPanel()).doClick(); settle(host);
            assertThat(host.openRequests()).hasSize(1);
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void renamesDefaultGroupThroughThePanel(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            var saved = RemoteHost.create("home", "example", 22, "me", Auth.AGENT, "", Optional.empty());
            plugin.store().put(saved); settle(host);
            var panel = (HostsPanel) host.openPanel(RemotePlugin.PANEL, window);
            var menu = menuFor(panel, 0);
            ((javax.swing.JMenuItem) menu.getComponent(0)).doClick();
            groupName(plugin.currentGroupEditor()).setText("My machines");
            saveGroup(plugin.currentGroupEditor()).doClick(); settle(host);
            assertThat(host.windows()).isEmpty();
            assertThat(list(panel).getModel().getElementAt(0)).isInstanceOfSatisfying(dev.jasper.remote.ui.HostRows.Group.class,
                group -> assertThat(group.name()).isEqualTo("My machines"));
            assertThat(new PanelState(context.dataDirectory().resolve("panel-state.toml")).defaultGroup()).isEqualTo("My machines");
            assertThat(plugin.store().hosts().getFirst().group()).isEmpty();
        }
    }

}
