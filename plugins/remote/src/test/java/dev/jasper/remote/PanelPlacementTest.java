package dev.jasper.remote;

import static dev.jasper.remote.RemotePluginTest.settle;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.testsupport.Kits;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.PanelMode;
import dev.jasper.sdk.ui.PanelPlacement;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Wide tool window layouts: Remote's tool windows describe their wide layout in a window of its own (Float or Window) and
 * docked at the bottom, and their docked layout anywhere else, from the moment the panel is built and after every move.
 */
class PanelPlacementTest {
    @TempDir Path root;

    /** Docked on {@code anchor}, in its side's upper half. */
    static PanelPlacement docked(Anchor anchor) { return new PanelPlacement(anchor, false, PanelMode.DOCKED); }

    /** In a window of its own, with the left stripe's button. */
    static PanelPlacement own(PanelMode mode) { return new PanelPlacement(Anchor.LEFT, false, mode); }

    /** Remote started in the host, with a terminal window added as the main window's tool windows need. */
    RemotePlugin started(FakePluginHost host) {
        var plugin = new TestRemotePlugin(Kits.UI, context -> Optional.empty(), (delay, work) -> () -> { }, root.resolve("ssh"));
        host.start(RemotePluginTest.INFO, Set.of(), Set.of(), plugin);
        host.addTerminalWindow();
        return plugin;
    }

    static void place(FakePluginHost host, String panel, PanelPlacement placement) throws Exception { Kits.edt(() -> host.placePanel(panel, placement)); }

    /** Whether the open panel's view describes a node with this id; the kit's views are read on the EDT. */
    static boolean has(FakePluginHost host, String panel, String id) throws Exception {
        return Kits.value(() -> Kits.find(host.panelView(panel).orElseThrow(), id).isPresent());
    }

    @Test void theSessionsPanelIsWideInAWindowOfItsOwnAndAtTheBottom() throws Exception {
        try (var host = new FakePluginHost(root.resolve("home"))) {
            var plugin = started(host);
            plugin.store().put(RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty()));
            settle(host);
            Kits.edt(() -> host.openPanel(RemotePlugin.PANEL));
            assertThat(has(host, RemotePlugin.PANEL, HostsPanel.COLUMNS)).as("docked on the left").isFalse();
            place(host, RemotePlugin.PANEL, own(PanelMode.FLOAT));
            assertThat(has(host, RemotePlugin.PANEL, HostsPanel.COLUMNS)).as("floating").isTrue();
            place(host, RemotePlugin.PANEL, own(PanelMode.WINDOW));
            assertThat(has(host, RemotePlugin.PANEL, HostsPanel.COLUMNS)).as("a window of its own too").isTrue();
            place(host, RemotePlugin.PANEL, docked(Anchor.LEFT));
            assertThat(has(host, RemotePlugin.PANEL, HostsPanel.COLUMNS)).as("docked again").isFalse();
            place(host, RemotePlugin.PANEL, docked(Anchor.BOTTOM));
            assertThat(has(host, RemotePlugin.PANEL, HostsPanel.COLUMNS)).as("docked at the bottom").isTrue();
            place(host, RemotePlugin.PANEL, docked(Anchor.RIGHT));
            assertThat(has(host, RemotePlugin.PANEL, HostsPanel.COLUMNS)).as("docked on the right").isFalse();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** A panel built while it has a window of its own, as one moved while closed or restored there is, starts wide. */
    @Test void aSessionsPanelBuiltInAWindowOfItsOwnStartsWide() throws Exception {
        try (var host = new FakePluginHost(root.resolve("home"))) {
            var plugin = started(host);
            plugin.store().put(RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty()));
            settle(host);
            place(host, RemotePlugin.PANEL, own(PanelMode.WINDOW));
            Kits.edt(() -> host.openPanel(RemotePlugin.PANEL));
            assertThat(has(host, RemotePlugin.PANEL, HostsPanel.COLUMNS)).isTrue();
            assertThat(host.failures()).isEmpty();
        }
    }
}
