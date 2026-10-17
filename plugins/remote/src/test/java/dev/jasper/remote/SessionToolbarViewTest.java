package dev.jasper.remote;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 14.4: under CLASSIC the toolbar's Sessions dropdown becomes one "Session: [combo] [Connect]" view per window. */
class SessionToolbarViewTest {
    @TempDir Path dir;
    final List<String> events = new ArrayList<>();
    final SessionSelection selection = new SessionSelection();
    final AtomicReference<Optional<UUID>> activePane = new AtomicReference<>(Optional.empty());
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final RemoteHost minecraft = RemoteHost.create("minecraft-local", "192.168.1.124", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final ShellProfileInfo ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
    List<ShellProfileInfo> distros = List.of(ubuntu);
    HostStore store;
    SessionsToolbar toolbar;

    /** A context of Remote's identity on the testkit host, the store with two hosts, and the toolbar in {@code layout}. */
    PluginContext start(FakePluginHost host, Layout layout) throws Exception {
        var context = new AtomicReference<PluginContext>();
        host.start(RemotePluginTest.INFO, Set.of(), Set.of(), context::set);
        store = new HostStore(dir.resolve("hosts.toml"), Runnable::run, Runnable::run);
        store.put(mac).join(); store.put(minecraft).join();
        edt(() -> toolbar = new SessionsToolbar(context.get(), store, "network", (window, chosen) -> events.add("activate " + chosen.name()),
            window -> events.add("manage"), () -> distros, (window, profile) -> events.add("open " + profile.name()),
            layout, selection, window -> activePane.get()));
        return context.get();
    }

    /** The window's one toolbar view, built on the UI thread as the kit requires. */
    static FakeKitView view(FakePluginHost host, UUID window) throws Exception { return value(() -> host.toolbarViews(window).getFirst()); }
    /** A node of the view's current tree, read on the UI thread as the kit requires. */
    static <T extends Nodes.Node> T read(FakeKitView view, String id, Class<T> type) throws Exception { return value(() -> node(view, id, type)); }
    static Nodes.Choice choice(FakeKitView view) throws Exception { return read(view, "session.choice", Nodes.Choice.class); }

    @Test void underClassicOneViewListsHostsThenDistributionsThenManage() throws Exception {
        try (var host = new FakePluginHost()) {
            start(host, Layout.CLASSIC);
            var window = host.addTerminalWindow();
            assertThat(host.toolbar()).as("no Sessions dropdown").noneMatch(item -> item.contains("Sessions"));
            var view = view(host, window);
            assertThat(read(view, "session.label", Nodes.Text.class).text()).isEqualTo("Session:");
            assertThat(choice(view).options()).extracting(Option::label)
                .containsExactly("Mac (192.168.1.119)", "minecraft-local (192.168.1.124)", "Ubuntu (WSL 2)", "Manage Sessions…");
            assertThat(choice(view).selectedId()).as("the first host while nothing is chosen").isEqualTo("host:" + mac.id());
            edt(() -> emit(view, new UiEvent.Selected("session.choice", Set.of("manage"))));
            assertThat(events).containsExactly("manage");
            assertThat(choice(view).selectedId()).as("Manage Sessions… is not a selection").isEqualTo("host:" + mac.id());
        }
    }

    @Test void connectActsOnTheChosenHostThenThePanesThenTheFirst() throws Exception {
        try (var host = new FakePluginHost()) {
            start(host, Layout.CLASSIC);
            var window = host.addTerminalWindow();
            var view = view(host, window);
            edt(() -> press(view, "session.connect"));
            activePane.set(Optional.of(minecraft.id()));
            edt(toolbar::refresh);
            edt(() -> press(view, "session.connect"));
            edt(() -> emit(view, new UiEvent.Selected("session.choice", Set.of("distro:" + ubuntu.id()))));
            edt(() -> press(view, "session.connect"));
            assertThat(events).containsExactly("activate Mac", "activate minecraft-local", "open Ubuntu");
            assertThat(selection.get(window)).contains("distro:" + ubuntu.id());
            edt(() -> selection.set(window, Optional.of("host:" + mac.id())));
            assertThat(choice(view).selectedId()).as("the tree's choice reaches the combo").isEqualTo("host:" + mac.id());
        }
    }

    @Test void aDeletedHostLeavesTheComboAndTheProperties() throws Exception {
        try (var host = new FakePluginHost()) {
            start(host, Layout.CLASSIC);
            var window = host.addTerminalWindow();
            var view = view(host, window);
            edt(() -> selection.set(window, Optional.of("host:" + minecraft.id())));
            store.remove(minecraft.id()).join(); store.remove(mac.id()).join();
            distros = List.of();
            edt(toolbar::refresh);
            assertThat(choice(view).options()).extracting(Option::id).containsExactly("none", "manage");
            assertThat(choice(view).selectedId()).isEqualTo("none");
            assertThat(read(view, "session.connect", Nodes.Button.class).state().enabled()).isFalse();
        }
    }

    @Test void theRowsLayoutKeepsTheSessionsDropdown() throws Exception {
        try (var host = new FakePluginHost()) {
            start(host, Layout.ROWS);
            var window = host.addTerminalWindow();
            assertThat(host.toolbar()).anyMatch(item -> item.contains("Sessions"));
            assertThat(value(() -> host.toolbarViews(window))).isEmpty();
        }
    }
}
