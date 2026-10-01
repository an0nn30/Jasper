package dev.jasper.fileserver;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.DefinitionFile;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.fileserver.ui.FileServersPanel;
import dev.jasper.fileserver.ui.UiTestAccess;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.vault.api.LockState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class FileServerPluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.fileserver", "File Server", "0.1.0",
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
        Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem item && item.getText().equals(title))
            .map(c -> (JMenuItem) c).findFirst().orElseThrow().doClick();
    }

    @Test void registersItsPanelActionsSettingsAndAHiddenStatus() {
        try (var host = new FakePluginHost(root)) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            settle(host);
            assertThat(host.panels()).containsExactly("dev.jasper.fileserver.panel|File Servers|RIGHT|dev.jasper.fileserver.show-panel");
            assertThat(host.actions()).contains("dev.jasper.fileserver.show-panel|Toggle File Servers|true",
                "dev.jasper.fileserver.new|New File Server…|true", "dev.jasper.fileserver.stop-all|Stop All File Servers|false");
            assertThat(host.status()).isEmpty();
            assertThat(host.declared("dev.jasper.fileserver"))
                .anySatisfy(line -> assertThat(line).startsWith("confirm_quit"))
                .anySatisfy(line -> assertThat(line).startsWith("activity_entries"));
        }
    }

    @Test void addsStartsServesAndStopsAServer() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = (FileServersPanel) host.openPanel(FileServerPlugin.PANEL, window);
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
            assertThat(host.status()).isEmpty();
        }
    }

    @Test void quittingAsksWhileAServerRunsUnlessTurnedOff() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            host.addTerminalWindow();
            assertThat(host.requestQuit()).as("nothing runs").isTrue();
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            plugin.registry().start(docs.id(), null);
            await(host, () -> plugin.registry().running() == 1, "the server runs");
            assertThat(host.requestQuit()).as("the question is pending").isFalse();
            assertThat(host.windows()).containsExactly("dialog|Quit Jasper?|true");
            assertThat(UiTestAccess.message(plugin.currentConfirm()).getText()).isEqualTo("1 file server is running and will stop.");
            UiTestAccess.cancel(plugin.currentConfirm()).doClick();
            assertThat(host.windows()).isEmpty();
            var decision = plugin.confirmQuit();
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            assertThat(decision).isCompletedWithValue(true);
            host.setSetting("dev.jasper.fileserver", "confirm_quit", false);
            settle(host);
            assertThat(host.requestQuit()).as("the setting is off").isTrue();
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
            var panel = (FileServersPanel) host.openPanel(FileServerPlugin.PANEL, window);
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            plugin.registry().start(docs.id(), null);
            await(host, () -> plugin.registry().running() == 1, "the server runs");
            var handle = plugin.registry().server(docs.id()).orElseThrow();
            plugin.edit(plugin.windowHandle(window), Optional.of(handle.definition()), true);
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
            assertThat(host.notices()).containsExactly("dev.jasper.fileserver: servers.toml: server 2: 'protocol' is required");
            assertThat(plugin.registry().server(vaulted.id()).orElseThrow().state().phase()).isEqualTo(Phase.WAITING_FOR_VAULT);
            vault.unlock();
            await(host, () -> plugin.registry().server(vaulted.id()).orElseThrow().state().phase() == Phase.RUNNING, "the server starts on unlock");
            assertThat(plugin.registry().password(vaulted.id())).contains("pw");
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
        }
    }
}
