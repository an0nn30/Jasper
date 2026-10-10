package dev.jasper.fileserver;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.DefinitionFile;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.fileserver.ui.FileServerScope;
import dev.jasper.fileserver.ui.FileServersPanel;
import dev.jasper.fileserver.ui.UiTestAccess;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.vault.api.LockState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class FileServerPluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.fileserver", "File Server", "0.4.0",
        Set.of(Capabilities.NETWORK_LISTEN, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));
    @TempDir Path home;
    @TempDir Path root;
    final TestUi ui = new TestUi();
    final List<String> copied = new ArrayList<>();
    final List<Path> revealed = new ArrayList<>();

    FileServerPlugin plugin() {
        return new FileServerPlugin(new FileServerPlugin.Environment(ui, List::of, port -> true, copied::add, revealed::add, home,
            "Mac OS X", ZoneOffset.UTC, (interval, task) -> () -> { }));
    }

    FileServerPlugin plugin(IntPredicate portFree) {
        return new FileServerPlugin(new FileServerPlugin.Environment(ui, List::of, portFree, copied::add, revealed::add, home,
            "Mac OS X", ZoneOffset.UTC, (interval, task) -> () -> { }));
    }

    static boolean requestQuit(FakePluginHost host) {
        var result = new java.util.concurrent.atomic.AtomicBoolean();
        UiTestAccess.edt(() -> result.set(host.requestQuit()));
        return result.get();
    }

    static FileServersPanel openPanel(FakePluginHost host, FileServerPlugin plugin, UUID window) {
        UiTestAccess.edt(() -> host.openPanel(FileServerPlugin.PANEL, window));
        return plugin.panel(window);
    }

    static PaneInfo pane(Optional<Path> directory) {
        return new PaneInfo("zsh", directory, Optional.empty(), 80, 24, true, SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING,
            OptionalInt.empty(), "zsh");
    }

    void settle(FakePluginHost host) {
        for (int i = 0; i < 20; i++) { host.runBackground(); ui.drain(); host.flush(); }
    }

    void await(FakePluginHost host, BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { settle(host); Thread.sleep(10); }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    ServerDefinition http(String name, Path folder, int port, Login login, boolean autostart) {
        var base = ServerDefinition.create(name, Protocol.HTTP, folder);
        return new ServerDefinition(base.id(), name, Protocol.HTTP, folder, Bind.LOOPBACK, port, Optional.empty(), false, login,
            List.of(), false, false, autostart);
    }

    static void click(javax.swing.JPopupMenu menu, String title) {
        var item = Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem found && found.getText().equals(title))
            .map(c -> (JMenuItem) c).findFirst().orElseThrow(() -> new AssertionError("Missing menu item " + title));
        assertThat(item.isEnabled()).as("Menu item " + title).isTrue();
        UiTestAccess.edt(item::doClick);
    }

    @Test void servesTheFocusedFolderOverSftpWithASessionPassword() throws Exception {
        Path project = Files.createDirectories(home.resolve("project"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin(port -> port != 2222);
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            assertThat(host.actions()).contains("dev.jasper.fileserver.serve-sftp|Serve Current Folder over SFTP|true");
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID local = host.addTerminalPane(tab, pane(Optional.of(project)));
            UiTestAccess.edt(() -> host.openPanel(FileServerPlugin.PANEL, window));
            assertThat(host.invoke(FileServerPlugin.SERVE_SFTP, window, local)).isTrue();
            await(host, () -> plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the start settles");
            var served = plugin.registry().servers().getFirst();
            assertThat(served.temporary()).isTrue();
            assertThat(served.definition().name()).isEqualTo("project (SFTP)");
            assertThat(served.definition().protocol()).isEqualTo(Protocol.SFTP);
            assertThat(served.definition().folder()).isEqualTo(project);
            assertThat(served.definition().port()).as("2222 was taken").isEqualTo(2223);
            assertThat(served.definition().writable()).isFalse();
            assertThat(served.definition().login()).isEqualTo(Login.SESSION);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }

    @Test void servesTheFocusedFolderOverTftp() throws Exception {
        Path project = Files.createDirectories(home.resolve("project"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin(port -> port != 6969 && FileServerPlugin.portFree(port));
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            assertThat(host.actions()).contains("dev.jasper.fileserver.serve-tftp|Serve Current Folder over TFTP|true");
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID local = host.addTerminalPane(tab, pane(Optional.of(project)));
            UiTestAccess.edt(() -> host.openPanel(FileServerPlugin.PANEL, window));
            assertThat(host.invoke(FileServerPlugin.SERVE_TFTP, window, local)).isTrue();
            await(host, () -> plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the start settles");
            var served = plugin.registry().servers().getFirst();
            assertThat(served.state().phase()).as("the TFTP engine started").isEqualTo(Phase.RUNNING);
            assertThat(served.temporary()).isTrue();
            assertThat(served.definition().name()).isEqualTo("project (TFTP)");
            assertThat(served.definition().protocol()).isEqualTo(Protocol.TFTP);
            assertThat(served.definition().port()).as("6969 was taken; the next free port above it").isGreaterThanOrEqualTo(6970);
            assertThat(served.definition().writable()).isFalse();
            assertThat(served.definition().login()).isEqualTo(Login.ANONYMOUS);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }

    @Test void aPortIsFreeOnlyWhenBothTcpAndUdpAre() throws Exception {
        int port = Ports.free();
        assertThat(FileServerPlugin.portFree(port)).isTrue();
        try (var udp = new java.net.DatagramSocket(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port))) {
            assertThat(udp.isBound()).isTrue();
            assertThat(FileServerPlugin.portFree(port)).as("UDP taken").isFalse();
        }
    }

    @Test void showsTheHostKeyFingerprintAndCopiesIt() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = openPanel(host, plugin, window);
            var base = ServerDefinition.create("keys", Protocol.SFTP, home);
            var sftp = new ServerDefinition(base.id(), "keys", Protocol.SFTP, home, Bind.LOOPBACK, Ports.free(), Optional.empty(), false,
                Login.SESSION, List.of(), false, false, false);
            plugin.store().put(sftp);
            settle(host);
            assertThat(UiTestAccess.cardHostKey(panel).getText()).isEqualTo("SSH host key: created when the server first starts");
            UiTestAccess.toggle(panel).doClick();
            await(host, () -> plugin.registry().server(sftp.id()).orElseThrow().state().phase() == Phase.RUNNING, "the SFTP server runs");
            String fingerprint = plugin.hostKey().cachedFingerprint().orElseThrow();
            assertThat(fingerprint).startsWith("SHA256:");
            assertThat(UiTestAccess.cardHostKey(panel).getText()).isEqualTo("SSH host key " + fingerprint);
            click(UiTestAccess.menuFor(panel, 0), "Show Fingerprints…");
            await(host, () -> plugin.currentConfirm() != null, "the fingerprint dialog opens");
            assertThat(host.windows()).containsExactly("dialog|Fingerprints|true");
            assertThat(UiTestAccess.message(plugin.currentConfirm()).getText()).isEqualTo("SSH host key: " + fingerprint);
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            settle(host);
            assertThat(copied).containsExactly(fingerprint);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }

    @Test void registersItsPanelActionsSettingsAndAHiddenStatus() {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            assertThat(host.panels()).containsExactly("dev.jasper.fileserver.panel|File Servers|LEFT|dev.jasper.fileserver.show-panel");
            assertThat(host.actions()).contains("dev.jasper.fileserver.show-panel|Toggle File Servers|true",
                "dev.jasper.fileserver.new|New File Server…|true", "dev.jasper.fileserver.stop-all|Stop All File Servers|false");
            assertThat(host.status()).isEmpty();
            assertThat(host.declared("dev.jasper.fileserver"))
                .anySatisfy(line -> assertThat(line).startsWith("confirm_quit"))
                .anySatisfy(line -> assertThat(line).startsWith("activity_entries"))
                .anySatisfy(line -> assertThat(line).startsWith("passive_ports"));
            UUID window = host.addTerminalWindow();
            openPanel(host, plugin, window);
            var header = host.panelHeader(FileServerPlugin.PANEL, window);
            assertThat(header.detail()).isEmpty();
            assertThat(header.actions()).singleElement().satisfies(action -> assertThat(action.label()).isEqualTo("New File Server…"));
            UiTestAccess.edt(() -> header.actions().getFirst().invoke().run());
            assertThat(host.windows()).containsExactly("dialog|New File Server|true");
        }
    }

    @Test void keyFilesSkipsLargeFilesAndFolders() throws Exception {
        Path keys = Files.createDirectories(home.resolve(".ssh"));
        Path good = Files.writeString(keys.resolve("id_ed25519.pub"), "ssh-ed25519 AAAA alice");
        Path huge = Files.writeString(keys.resolve("huge.pub"), "x".repeat(64 * 1024 + 1));
        Path exact = Files.writeString(keys.resolve("exact.pub"), "y".repeat(64 * 1024));
        Path folder = Files.createDirectories(keys.resolve("folder.pub"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            var owner = plugin.windowHandle(host.addTerminalWindow());
            host.queuePathSelection(List.of(good, huge, folder, exact));
            assertThat(plugin.keyFiles(owner)).contains("ssh-ed25519 AAAA alice\n" + "y".repeat(64 * 1024) + "\n");
            String reason = ": not a public key file (too large or not a regular file)";
            assertThat(host.notifications().lines()).containsExactly(
                "dev.jasper.fileserver|ERROR|Could not read a public key file|" + huge + reason,
                "dev.jasper.fileserver|ERROR|Could not read a public key file|" + folder + reason);
        }
    }

    @Test void addsStartsServesAndStopsAServer() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = openPanel(host, plugin, window);
            UiTestAccess.add(panel).doClick();
            assertThat(host.windows()).containsExactly("dialog|New File Server|true");
            Path shared = Files.createDirectories(home.resolve("shared"));
            Files.writeString(shared.resolve("hello.txt"), "hi");
            int port = Ports.free();
            var editor = plugin.currentEditor();
            UiTestAccess.name(editor).setText("docs");
            UiTestAccess.folder(editor).setText(shared.toString());
            UiTestAccess.port(editor).setText(String.valueOf(port));
            UiTestAccess.save(editor).doClick();
            settle(host);
            assertThat(host.windows()).isEmpty();
            assertThat(plugin.store().definitions()).extracting(ServerDefinition::name).containsExactly("docs");
            UUID id = plugin.store().definitions().getFirst().id();
            UiTestAccess.toggle(panel).doClick();
            await(host, () -> plugin.registry().server(id).orElseThrow().state().phase() == Phase.RUNNING, "the server runs");
            assertThat(host.status()).containsExactly("dev.jasper.fileserver.status|RIGHT|⇅ 1 server|docs — http://127.0.0.1:" + port + "/|dev.jasper.fileserver.show-panel");
            assertThat(host.actions()).contains("dev.jasper.fileserver.stop-all|Stop All File Servers|true");
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/hello.txt")).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(response.body()).isEqualTo("hi");
            await(host, () -> UiTestAccess.activity(panel).getText().contains("↓ /hello.txt"), "the download is logged");
            click(UiTestAccess.menuFor(panel, 0), "Copy Address");
            click(UiTestAccess.menuFor(panel, 0), "Reveal Folder");
            settle(host);
            assertThat(copied).containsExactly("http://127.0.0.1:" + port + "/");
            assertThat(revealed).containsExactly(shared);
            assertThat(host.invoke(FileServerPlugin.STOP_ALL, window, null)).isTrue();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
            settle(host);
            assertThat(host.status()).isEmpty();
        }
    }

    @Test void quittingAsksWhileAServerRunsUnlessTurnedOff() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            host.addTerminalWindow();
            assertThat(requestQuit(host)).as("nothing runs").isTrue();
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            plugin.registry().start(docs.id(), null);
            await(host, () -> plugin.registry().running() == 1, "the server runs");
            assertThat(requestQuit(host)).as("the question is pending").isFalse();
            assertThat(host.windows()).containsExactly("dialog|Quit Jasper?|true");
            assertThat(UiTestAccess.message(plugin.currentConfirm()).getText()).isEqualTo("1 file server is running and will stop.");
            UiTestAccess.cancel(plugin.currentConfirm()).doClick();
            assertThat(host.windows()).isEmpty();
            var decision = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Boolean>>();
            UiTestAccess.edt(() -> decision.set(plugin.confirmQuit()));
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            assertThat(decision.get()).isCompletedWithValue(true);
            host.setSetting("dev.jasper.fileserver", "confirm_quit", false);
            settle(host);
            assertThat(requestQuit(host)).as("the setting is off").isTrue();
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
        }
    }

    @Test void savingARunningServerOffersARestartAndDeleteAsksFirst() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = openPanel(host, plugin, window);
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            plugin.registry().start(docs.id(), null);
            await(host, () -> plugin.registry().running() == 1, "the server runs");
            var handle = plugin.registry().server(docs.id()).orElseThrow();
            UiTestAccess.edt(() -> plugin.edit(plugin.windowHandle(window), Optional.of(handle.definition()), true));
            int moved = Ports.free();
            UiTestAccess.port(plugin.currentEditor()).setText(String.valueOf(moved));
            UiTestAccess.save(plugin.currentEditor()).doClick();
            settle(host);
            assertThat(host.windows()).containsExactly("dialog|Restart docs?|true");
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            await(host, () -> plugin.registry().server(docs.id()).orElseThrow().addresses().equals(List.of(URI.create("http://127.0.0.1:" + moved + "/"))),
                "the server restarted on the new port");
            click(UiTestAccess.menuFor(panel, 0), "Delete…");
            assertThat(host.windows()).containsExactly("dialog|Delete docs?|true");
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            await(host, () -> plugin.store().definitions().isEmpty() && plugin.registry().running() == 0, "deleted and stopped");
        }
    }

    @Test void asynchronousStartFailureStaysVisibleInTheOwnedPanel() throws Exception {
        try (var blocker = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
             var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = openPanel(host, plugin, window);
            var definition = http("busy", home, blocker.getLocalPort(), Login.ANONYMOUS, false);
            plugin.store().put(definition);
            settle(host);
            plugin.registry().start(definition.id(), null);
            await(host, () -> plugin.registry().server(definition.id()).orElseThrow().state().phase() == Phase.FAILED,
                "the blocked port produces an asynchronous failed state");
            settle(host);
            var list = (dev.jasper.sdk.ui.Nodes.ItemList) UiTestAccess.node((dev.jasper.sdk.testing.FakeKitView) panel.view(),
                FileServersPanel.ROWS);
            assertThat(list.items().getFirst().subtitle()).anyMatch(node -> node instanceof dev.jasper.sdk.ui.Nodes.Text text
                && text.severity().equals(Optional.of(dev.jasper.sdk.ui.Nodes.Severity.DANGER)));
            assertThat(panel.selected()).contains(definition.id());
        }
    }

    @Test void autostartWaitsForTheVaultAndBadEntriesAreReported() throws Exception {
        var vault = new FakeVault();
        UUID account = vault.password("Share login", "guest", "pw");
        vault.state = LockState.LOCKED;
        var vaulted = http("vaulted", home, Ports.free(), new Login.Vault(account), true);
        Path data = Files.createDirectories(root.resolve("dev.jasper.fileserver/data"));
        Files.writeString(data.resolve("servers.toml"), DefinitionFile.format(List.of(vaulted)) + "\n[[server]]\nname = \"broken\"\n");
        try (var host = new FakePluginHost(root)) {
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            settle(host);
            assertThat(host.notifications().lines())
                .containsExactly("dev.jasper.fileserver|WARNING|servers.toml has a problem|server 2: 'protocol' is required");
            assertThat(plugin.registry().server(vaulted.id()).orElseThrow().state().phase()).isEqualTo(Phase.WAITING_FOR_VAULT);
            vault.unlock();
            await(host, () -> plugin.registry().server(vaulted.id()).orElseThrow().state().phase() == Phase.RUNNING, "the server starts on unlock");
            assertThat(plugin.registry().password(vaulted.id())).contains("pw");
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
        }
    }

    @Test void thePaletteListsServersAndStartsThem() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            assertThat(host.searchScope(FileServerScope.ID, "docs", window, null)).singleElement().asString().contains("docs");
            assertThat(host.invoke(FileServerPlugin.SERVERS, window, null)).isTrue();
            assertThat(host.paletteOpens()).anySatisfy(line -> assertThat(line).contains(FileServerScope.ID));
            host.executeInScope(FileServerScope.ID, "server." + docs.id(), "toggle", window, null);
            await(host, () -> plugin.registry().running() == 1, "the palette started it");
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
        }
    }

    @Test void servesTheFocusedFolderOnTheNextFreePort() throws Exception {
        Path project = Files.createDirectories(home.resolve("project"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin(port -> port != 8080);
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID local = host.addTerminalPane(tab, pane(Optional.of(project)));
            var panel = openPanel(host, plugin, window);
            assertThat(host.invoke(FileServerPlugin.SERVE_HTTP, window, local)).isTrue();
            await(host, () -> plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the start settles");
            var served = plugin.registry().servers().getFirst();
            assertThat(served.temporary()).isTrue();
            assertThat(served.definition().name()).isEqualTo("project (HTTP)");
            assertThat(served.definition().folder()).isEqualTo(project);
            assertThat(served.definition().port()).as("8080 was taken").isEqualTo(8081);
            assertThat(served.definition().bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(served.definition().writable()).isFalse();
            assertThat(served.definition().login()).isEqualTo(Login.ANONYMOUS);
            assertThat(panel.selected()).contains(served.definition().id());
            UUID elsewhere = host.addTerminalPane(tab, pane(Optional.empty()));
            assertThat(host.invoke(FileServerPlugin.SERVE_HTTP, window, elsewhere)).isTrue();
            await(host, () -> plugin.registry().servers().size() == 2
                && plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the second start settles");
            assertThat(plugin.registry().servers().get(1).definition().folder()).as("no folder: home").isEqualTo(home);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }

    @Test void aBurstOfActivityIsOneRefreshOnTheUiThread() {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            assertThat(ui.pending()).isZero();
            for (int i = 0; i < 50; i++) plugin.registry().clearActivity(docs.id());
            assertThat(ui.pending()).as("fifty events queue one refresh").isEqualTo(1);
            ui.drain();
            plugin.registry().clearActivity(docs.id());
            assertThat(ui.pending()).as("a later event queues the next refresh").isEqualTo(1);
            ui.drain();
        }
    }

    @Test void eventsAfterStopOpenNoActivity() {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            Instant at = Instant.parse("2026-09-30T10:00:00Z");
            plugin.observe(new ServerEvent.TransferStarted(at, "10.0.0.2", 1, ServerEvent.Direction.DOWNLOAD, "/big.iso", 4096, () -> { }));
            plugin.observe(new ServerEvent.TransferProgress(at.plusSeconds(2), "10.0.0.2", 1, 100, 4096));
            host.flush();
            assertThat(host.activityLog()).as("a slow transfer while running opens an activity").isNotEmpty();
            int before = host.activityLog().size();
            plugin.stop();
            plugin.observe(new ServerEvent.TransferStarted(at, "10.0.0.2", 2, ServerEvent.Direction.DOWNLOAD, "/late.iso", 4096, () -> { }));
            plugin.observe(new ServerEvent.TransferProgress(at.plusSeconds(2), "10.0.0.2", 2, 100, 4096));
            host.flush();
            assertThat(host.activityLog()).as("a late event after stop does nothing").hasSize(before);
        }
    }

    @Test void noRestartPromptWhenTheEditorOrItsWindowWasClosedBeforeTheSaveFinished() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID pane = host.addTerminalPane(tab, pane(Optional.empty()));
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            plugin.registry().start(docs.id(), null);
            await(host, () -> plugin.registry().running() == 1, "the server runs");
            var handle = plugin.registry().server(docs.id()).orElseThrow();
            // the editor dialog is closed by something other than the save while the save is in flight
            UiTestAccess.edt(() -> plugin.edit(plugin.windowHandle(window), Optional.of(handle.definition()), true));
            UiTestAccess.port(plugin.currentEditor()).setText(String.valueOf(Ports.free()));
            UiTestAccess.save(plugin.currentEditor()).doClick();
            assertThat(host.requestClose("dialog")).isTrue();
            settle(host);
            assertThat(plugin.registry().server(docs.id()).orElseThrow().restartNeeded()).as("the save went through").isTrue();
            assertThat(host.windows()).as("no restart question").isEmpty();
            // the owner window closes while the save is in flight
            UiTestAccess.edt(() -> plugin.edit(plugin.windowHandle(window), Optional.of(plugin.registry().server(docs.id()).orElseThrow().definition()), true));
            UiTestAccess.port(plugin.currentEditor()).setText(String.valueOf(Ports.free()));
            UiTestAccess.save(plugin.currentEditor()).doClick();
            host.closeTerminalPane(pane);
            settle(host);
            assertThat(host.windows()).as("no restart question over a closed window").noneMatch(line -> line.contains("Restart"));
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
        }
    }

    @Test void servesTheFocusedFolderOverFtpWithASessionPassword() throws Exception {
        Path project = Files.createDirectories(home.resolve("project"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin(port -> port != 2121);
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            assertThat(host.actions()).contains("dev.jasper.fileserver.serve-ftp|Serve Current Folder over FTP|true");
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID local = host.addTerminalPane(tab, pane(Optional.of(project)));
            UiTestAccess.edt(() -> host.openPanel(FileServerPlugin.PANEL, window));
            assertThat(host.invoke(FileServerPlugin.SERVE_FTP, window, local)).isTrue();
            await(host, () -> plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the start settles");
            var served = plugin.registry().servers().getFirst();
            assertThat(served.temporary()).isTrue();
            assertThat(served.definition().name()).isEqualTo("project (FTP)");
            assertThat(served.definition().protocol()).isEqualTo(Protocol.FTP);
            assertThat(served.definition().port()).as("2121 was taken").isEqualTo(2122);
            assertThat(served.definition().bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(served.definition().writable()).isFalse();
            assertThat(served.definition().login()).isEqualTo(Login.SESSION);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }

    @Test void contributesOnlyAvailableShareTypesAndRemovesThemOnStop() {
        try (var host = new FakePluginHost(root)) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            settle(host);
            assertThat(host.menu("context")).containsExactly("submenu:Share Path",
                "  item:dev.jasper.fileserver.share-http", "  item:dev.jasper.fileserver.share-https",
                "  item:dev.jasper.fileserver.share-ftp", "  item:dev.jasper.fileserver.share-ftps",
                "  item:dev.jasper.fileserver.share-sftp", "  item:dev.jasper.fileserver.share-tftp");
            assertThat(host.actions()).contains("dev.jasper.fileserver.share-http|HTTP|true",
                "dev.jasper.fileserver.share-https|HTTPS|true", "dev.jasper.fileserver.share-ftp|FTP|true",
                "dev.jasper.fileserver.share-ftps|FTPS|true", "dev.jasper.fileserver.share-sftp|SFTP|true",
                "dev.jasper.fileserver.share-tftp|TFTP|true");
            host.stopAll();
            assertThat(host.menu("context")).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"http,HTTP,false,false", "https,HTTP,true,false", "ftp,FTP,false,false", "ftps,FTP,true,true",
        "sftp,SFTP,false,false", "tftp,TFTP,false,false"})
    void sharesTheInvokedPanesFolderWithoutADialog(String type, Protocol protocol, boolean tls, boolean requireTls) throws Exception {
        Path project = Files.createDirectories(home.resolve("project"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin(port -> port != protocol.defaultPort() && FileServerPlugin.portFree(port));
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID clicked = host.addTerminalPane(tab, pane(Optional.of(project)));
            UUID otherWindow = host.addTerminalWindow(), otherTab = host.addTerminalTab(otherWindow, "other");
            UUID other = host.addTerminalPane(otherTab, pane(Optional.of(home)));
            host.focusTerminalPane(other);
            var shared = new java.util.concurrent.atomic.AtomicBoolean();
            UiTestAccess.edt(() -> shared.set(host.invoke("dev.jasper.fileserver.share-" + type, window, clicked)));
            assertThat(shared.get()).isTrue();
            await(host, () -> plugin.registry().running() == 1, "the quick share runs");
            var served = plugin.registry().servers().getFirst();
            var definition = served.definition();
            assertThat(served.temporary()).isTrue();
            assertThat(definition.name()).isEqualTo("project (" + type.toUpperCase(java.util.Locale.ROOT) + ")");
            assertThat(definition.folder()).isEqualTo(project);
            assertThat(definition.protocol()).isEqualTo(protocol);
            assertThat(definition.tls()).isEqualTo(tls);
            assertThat(definition.requireTls()).isEqualTo(requireTls);
            assertThat(definition.bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(definition.writable()).isFalse();
            assertThat(definition.autostart()).isFalse();
            assertThat(definition.port()).isBetween(protocol.defaultPort() + 1, protocol.defaultPort() + 99);
            assertThat(definition.login()).isEqualTo(protocol == Protocol.HTTP || protocol == Protocol.TFTP ? Login.ANONYMOUS : Login.SESSION);
            if (protocol == Protocol.FTP || protocol == Protocol.SFTP) assertThat(plugin.registry().password(definition.id())).hasValueSatisfying(password -> assertThat(password).isNotBlank());
            assertThat(plugin.store().definitions()).isEmpty();
            assertThat(host.windows()).as("no setup or login dialog").isEmpty();
            var panel = openPanel(host, plugin, window);
            assertThat(panel.selected()).contains(definition.id());
            assertThat(host.failures()).isEmpty();
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().servers().isEmpty(), "stopping removes the temporary share");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "missing", "file", "remote", "plugin", "no-pane"})
    void sharePathRefusesUnavailableLocalFoldersInsteadOfSharingHome(String location) throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            Optional<Path> directory = switch (location) {
                case "unknown" -> Optional.empty();
                case "missing" -> Optional.of(home.resolve("gone"));
                case "file" -> Optional.of(Files.writeString(home.resolve("file.txt"), "not a folder"));
                default -> Optional.of(home);
            };
            PaneInfo info = new PaneInfo("zsh", directory,
                location.equals("remote") ? Optional.of(new dev.jasper.sdk.terminal.RemoteDirectory("remote", home.toString())) : Optional.empty(),
                80, 24, true, location.equals("plugin") ? SessionKind.PLUGIN : SessionKind.LOCAL,
                location.equals("plugin") ? Optional.of("dev.jasper.remote") : Optional.empty(), SessionState.RUNNING, OptionalInt.empty(), "zsh");
            UUID clicked = host.addTerminalPane(tab, info);
            host.focusTerminalPane(host.addTerminalPane(tab, pane(Optional.of(home))));
            assertThat(host.invoke("dev.jasper.fileserver.share-http", window, location.equals("no-pane") ? null : clicked)).isTrue();
            settle(host);
            assertThat(plugin.registry().servers()).as("never substitute another folder").isEmpty();
            assertThat(host.notifications().lines()).singleElement().asString().contains("Could not share path");
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void showsTheCertificateFingerprintAndRegeneratesIt() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = openPanel(host, plugin, window);
            var base = http("secure", home, Ports.free(), Login.ANONYMOUS, false);
            var secure = new ServerDefinition(base.id(), base.name(), Protocol.HTTP, home, Bind.LOOPBACK, base.port(), Optional.empty(), false,
                Login.ANONYMOUS, List.of(), true, false, false);
            plugin.store().put(secure);
            settle(host);
            assertThat(UiTestAccess.cardTls(panel).getText()).isEqualTo("TLS certificate: created when the server first starts");
            UiTestAccess.toggle(panel).doClick();
            await(host, () -> plugin.registry().server(secure.id()).orElseThrow().state().phase() == Phase.RUNNING, "the HTTPS server runs");
            String fingerprint = plugin.tls().cachedFingerprint().orElseThrow();
            assertThat(UiTestAccess.cardTls(panel).getText()).isEqualTo("TLS certificate SHA-256 " + fingerprint);
            assertThat(plugin.registry().server(secure.id()).orElseThrow().addresses()).containsExactly(URI.create("https://127.0.0.1:" + secure.port() + "/"));
            click(UiTestAccess.menuFor(panel, 0), "Show Fingerprints…");
            await(host, () -> plugin.currentConfirm() != null, "the fingerprint dialog opens");
            assertThat(host.windows()).containsExactly("dialog|Fingerprints|true");
            assertThat(UiTestAccess.message(plugin.currentConfirm()).getText()).isEqualTo("TLS certificate (SHA-256): " + fingerprint);
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            settle(host);
            assertThat(host.windows()).containsExactly("dialog|Regenerate the TLS certificate?|true");
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            await(host, () -> plugin.tls().cachedFingerprint().filter(next -> !next.equals(fingerprint)).isPresent(), "a new certificate");
            settle(host);
            assertThat(UiTestAccess.cardTls(panel).getText()).isEqualTo("TLS certificate SHA-256 " + plugin.tls().cachedFingerprint().orElseThrow());
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }
}
