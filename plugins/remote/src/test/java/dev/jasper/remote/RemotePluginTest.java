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
import dev.jasper.sdk.testing.FakePresentations;
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

    @Test void aStartUnderEnterpriseReadsTheClassicLayoutOnce(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            host.setPresentation(FakePresentations.CLASSIC);
            var plugin = plugin(dir.resolve("ssh"));
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin);
            settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(plugin.layout()).isEqualTo(dev.jasper.remote.workstation.Layout.CLASSIC);
        }
        try (var host = new FakePluginHost()) {
            var plugin = plugin(dir.resolve("ssh-default"));
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin);
            settle(host);
            assertThat(plugin.layout()).isEqualTo(dev.jasper.remote.workstation.Layout.ROWS);
        }
    }

    /** Plan 15a: without a classic look Remote keeps its rows, so Sessions stays on the left. */
    @Test void underTheDefaultLookSessionsStaysLeft(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin(dir.resolve("ssh")));
            settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(host.panels()).contains("dev.jasper.remote.panel|Sessions|LEFT|dev.jasper.remote.hosts");
        }
    }

    @Test void underEnterpriseThePanelsTakeTheirSlotsAndTransfersIsABottomPanel(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            host.setPresentation(FakePresentations.CLASSIC);
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin(dir.resolve("ssh")));
            settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(host.panels()).containsExactlyInAnyOrder(
                "dev.jasper.remote.transfers.panel|Transfers|BOTTOM|dev.jasper.remote.transfers",
                "dev.jasper.remote.sftp.panel|SFTP|LEFT|dev.jasper.remote.sftp.toggle",
                "dev.jasper.remote.tunnels.panel|Tunnels|RIGHT+split|dev.jasper.remote.tunnels",
                "dev.jasper.remote.panel|Sessions|RIGHT|dev.jasper.remote.hosts");
            UUID window = host.addTerminalWindow();
            assertThat(Kits.value(() -> host.invoke(dev.jasper.remote.ui.transfers.TransferUi.SHOW, window, null))).isTrue();
            settle(host);
            assertThat(Kits.value(() -> host.panelView(dev.jasper.remote.ui.transfers.TransfersPanel.PANEL))).as("Transfers shows its panel").isPresent();
            Kits.edt(() -> host.openPanel(SftpUi.PANEL));
            settle(host);
            assertThat(Kits.value(() -> dev.jasper.remote.testsupport.Kits.find(host.panelView(SftpUi.PANEL).orElseThrow(),
                dev.jasper.remote.ui.transfers.TransferStrip.ROOT))).as("the SFTP footer holds no transfers under CLASSIC").isEmpty();
        }
    }

    /** Full windows spec 9: a panel request from a second window shows the panel there through {@code Panels.show}. */
    @Test void everyPanelRequestFromASecondWindowRaisesNoOtherWindow(@TempDir Path dir) throws Exception {
        for (var layout : List.of(dev.jasper.remote.workstation.Layout.ROWS, dev.jasper.remote.workstation.Layout.CLASSIC)) try (var host = new FakePluginHost()) {
            if (layout == dev.jasper.remote.workstation.Layout.CLASSIC) host.setPresentation(FakePresentations.CLASSIC);
            var plugin = plugin(dir.resolve("ssh-" + layout));
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin);
            settle(host);
            host.addTerminalWindow();
            UUID child = host.addTerminalWindow();
            for (String action : List.of(RemotePlugin.SFTP, dev.jasper.remote.ui.tunnels.TunnelsUi.SHOW, dev.jasper.remote.ui.transfers.TransferUi.SHOW))
                for (int request = 1; request <= 2; request++) {
                    assertThat(Kits.value(() -> host.invoke(action, child, null))).isTrue();
                    settle(host);
                    assertThat(host.openRequests()).as(layout + ": " + action + ", request " + request).isEmpty();
                }
            Kits.edt(() -> {
                assertThat(SftpTestAccess.panelHost(plugin.sftp(), child).map(built -> built.window().id())).as(layout + ": SFTP's instance").contains(child);
                assertThat(dev.jasper.remote.ui.tunnels.TunnelsTestAccess.panelHost(plugin.tunnels(), child).map(built -> built.window().id()))
                    .as(layout + ": Tunnels' instance").contains(child);
                if (layout == dev.jasper.remote.workstation.Layout.CLASSIC)
                    assertThat(dev.jasper.remote.ui.transfers.TransfersTestAccess.panelHost(plugin.transfersPanels(), child).map(built -> built.window().id()))
                        .as("Transfers' instance").contains(child);
            });
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Full windows spec 9: Show Tunnels from window b builds b's Tunnels panel; a has none. */
    @Test void tunnelsShowInTheWindowTheyAreAskedFrom(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                RemotePlugin plugin = plugin(dir.resolve("ssh"));
                host.start(INFO, Set.of(), Set.of(), plugin);
                settle(host);
                UUID a = host.addTerminalWindow(), b = host.addTerminalWindow();
                assertThat(host.invoke(dev.jasper.remote.ui.tunnels.TunnelsUi.SHOW, b, null)).isTrue();
                settle(host);
                assertThat(dev.jasper.remote.ui.tunnels.TunnelsTestAccess.panelHost(plugin.tunnels(), b)).hasValueSatisfying(built -> {
                    assertThat(built.window().id()).isEqualTo(b);
                    assertThat(built.visible()).isTrue();
                });
                assertThat(dev.jasper.remote.ui.tunnels.TunnelsTestAccess.panelHost(plugin.tunnels(), a)).as("a builds none").isEmpty();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    /** Full windows spec 9: closing a's Tunnels panel leaves b's, which still lists a new tunnel. */
    @Test void closingOneWindowsTunnelsPanelKeepsTheOthers(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                RemotePlugin plugin = plugin(dir.resolve("ssh"));
                host.start(INFO, Set.of(), Set.of(), plugin);
                var prod = RemoteHost.create("prod", "prod.invalid", 22, "me", new Auth.Agent(), "", Optional.empty());
                plugin.store().put(prod);
                settle(host);
                UUID a = host.addTerminalWindow(), b = host.addTerminalWindow();
                host.openPanel(dev.jasper.remote.ui.tunnels.TunnelsUi.PANEL, a);
                host.openPanel(dev.jasper.remote.ui.tunnels.TunnelsUi.PANEL, b);
                var inA = dev.jasper.remote.ui.tunnels.TunnelsTestAccess.panel(plugin.tunnels(), a);
                var inB = dev.jasper.remote.ui.tunnels.TunnelsTestAccess.panel(plugin.tunnels(), b);
                assertThat(inA).isNotNull().isNotSameAs(inB);
                var web = dev.jasper.remote.tunnels.Tunnel.create("web", prod.id(), dev.jasper.remote.tunnels.TunnelKind.LOCAL, "127.0.0.1", 8080, "localhost", 80);
                plugin.tunnelStore().put(web);
                settle(host);
                String row = dev.jasper.remote.ui.tunnels.TunnelsTestAccess.rowId(web.id());
                assertThat(dev.jasper.remote.ui.tunnels.TunnelsTestAccess.rowIds(inA)).as("a's panel lists it").contains(row);
                assertThat(dev.jasper.remote.ui.tunnels.TunnelsTestAccess.rowIds(inB)).as("b's panel lists it").contains(row);
                host.closePanel(dev.jasper.remote.ui.tunnels.TunnelsUi.PANEL, a);
                assertThat(dev.jasper.remote.ui.tunnels.TunnelsTestAccess.panel(plugin.tunnels(), a)).isNull();
                var db = dev.jasper.remote.tunnels.Tunnel.create("db", prod.id(), dev.jasper.remote.tunnels.TunnelKind.LOCAL, "127.0.0.1", 5432, "localhost", 5432);
                plugin.tunnelStore().put(db);
                settle(host);
                assertThat(dev.jasper.remote.ui.tunnels.TunnelsTestAccess.rowIds(inB)).as("b's panel still refreshes")
                    .contains(dev.jasper.remote.ui.tunnels.TunnelsTestAccess.rowId(db.id()));
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    /** Full windows spec 9, under CLASSIC: Transfers from window b builds b's Transfers panel; a has none. */
    @Test void transfersShowInTheWindowTheyAreAskedFrom(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                host.setPresentation(FakePresentations.CLASSIC);
                RemotePlugin plugin = plugin(dir.resolve("ssh"));
                host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin);
                settle(host);
                UUID a = host.addTerminalWindow(), b = host.addTerminalWindow();
                assertThat(host.invoke(dev.jasper.remote.ui.transfers.TransferUi.SHOW, b, null)).isTrue();
                settle(host);
                assertThat(dev.jasper.remote.ui.transfers.TransfersTestAccess.panelHost(plugin.transfersPanels(), b)).hasValueSatisfying(built -> {
                    assertThat(built.window().id()).isEqualTo(b);
                    assertThat(built.visible()).isTrue();
                });
                assertThat(dev.jasper.remote.ui.transfers.TransfersTestAccess.panelHost(plugin.transfersPanels(), a)).as("a builds none").isEmpty();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    /** Full windows spec 9, under CLASSIC: closing a's Transfers panel leaves b's, which a second request shows again. */
    @Test void closingOneWindowsTransfersPanelKeepsTheOthers(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                host.setPresentation(FakePresentations.CLASSIC);
                RemotePlugin plugin = plugin(dir.resolve("ssh"));
                host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin);
                settle(host);
                UUID a = host.addTerminalWindow(), b = host.addTerminalWindow();
                host.openPanel(dev.jasper.remote.ui.transfers.TransfersPanel.PANEL, a);
                host.openPanel(dev.jasper.remote.ui.transfers.TransfersPanel.PANEL, b);
                var inB = dev.jasper.remote.ui.transfers.TransfersTestAccess.panelHost(plugin.transfersPanels(), b).orElseThrow();
                host.closePanel(dev.jasper.remote.ui.transfers.TransfersPanel.PANEL, a);
                assertThat(dev.jasper.remote.ui.transfers.TransfersTestAccess.panelHost(plugin.transfersPanels(), a)).isEmpty();
                assertThat(dev.jasper.remote.ui.transfers.TransfersTestAccess.panelHost(plugin.transfersPanels(), b)).containsSame(inB);
                inB.hide();
                assertThat(host.invoke(dev.jasper.remote.ui.transfers.TransferUi.SHOW, b, null)).isTrue();
                assertThat(inB.visible()).as("b's own instance shows again").isTrue();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    /** Full windows spec 9: Manage Sessions from window b shows b's own Sessions panel, also once a has built one. */
    @Test void sessionsShowInTheWindowTheyAreAskedFrom(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                RemotePlugin plugin = plugin(dir.resolve("ssh"));
                host.start(INFO, Set.of(), Set.of(), plugin);
                settle(host);
                UUID a = host.addTerminalWindow(), b = host.addTerminalWindow();
                String manage = "dev.jasper.remote.sessions.manage";
                assertThat(host.invoke(manage, a, null)).isTrue();
                var inA = plugin.sessionsHost(a).orElseThrow();
                inA.hide();
                assertThat(host.invoke(manage, b, null)).isTrue();
                assertThat(inA.visible()).as("a's panel stays hidden").isFalse();
                assertThat(plugin.sessionsHost(b)).hasValueSatisfying(built -> {
                    assertThat(built.window().id()).isEqualTo(b);
                    assertThat(built.visible()).isTrue();
                });
                assertThat(host.openRequests()).isEmpty();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    /** Full windows spec 9: each window's Sessions panel follows the saved hosts; closing a's leaves b's listing them. */
    @Test void closingOneWindowsSessionsPanelKeepsTheOthers(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                RemotePlugin plugin = plugin(dir.resolve("ssh"));
                host.start(INFO, Set.of(), Set.of(), plugin);
                settle(host);
                UUID a = host.addTerminalWindow(), b = host.addTerminalWindow();
                host.openPanel(RemotePlugin.PANEL, a);
                host.openPanel(RemotePlugin.PANEL, b);
                var inA = plugin.hostsPanel(a);
                var inB = plugin.hostsPanel(b);
                assertThat(inA).isNotNull().isNotSameAs(inB);
                plugin.store().put(RemoteHost.create("prod", "prod.invalid", 22, "me", new Auth.Agent(), "", Optional.empty()));
                settle(host);
                assertThat(rows(inA)).as("a's panel lists the host").hasSize(1);
                assertThat(rows(inB)).as("b's panel lists the host").hasSize(1);
                host.closePanel(RemotePlugin.PANEL, a);
                assertThat(plugin.hostsPanel(a)).isNull();
                plugin.store().put(RemoteHost.create("stage", "stage.invalid", 22, "me", new Auth.Agent(), "", Optional.empty()));
                settle(host);
                assertThat(rows(inB)).as("b's panel still refreshes").hasSize(2);
                assertThat(host.failures()).isEmpty();
            }
        });
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

    /** Spec 13: every entry of the former SSH menu, in its order, in the Session and Transfer standard menus. */
    @Test void theSessionAndTransferMenusHoldEveryEntryTheSshMenuHad(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                host.start(INFO, Set.of(), Set.of(), plugin(dir.resolve("ssh")));
                settle(host);
                assertThat(host.failures()).isEmpty();
                assertThat(host.menu("SESSION")).containsExactly("item:" + RemotePlugin.CONNECT, "item:" + RemotePlugin.HOSTS,
                    "item:" + RemotePlugin.SPLIT, "---", "item:" + RemotePlugin.NEW_HOST, "item:" + RemotePlugin.IMPORT, "---",
                    "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.TOGGLE, "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.PICK,
                    "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.NEW, "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.FORWARD,
                    "item:" + dev.jasper.remote.ui.tunnels.TunnelsUi.STOP_ALL);
                assertThat(host.menu("TRANSFER")).containsExactly("item:" + RemotePlugin.SFTP,
                    "item:" + dev.jasper.remote.ui.transfers.TransferUi.SHOW);
                assertThat(host.menu("top:dev.jasper.remote.menu")).as("no SSH menu any more").isEmpty();
            }
        });
    }

    @Test void newHostOpensAddHostWithoutTheSessionsPanelAndSitsInTheSessionMenu(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                RemotePlugin plugin = plugin(dir.resolve("ssh"));
                host.start(INFO, Set.of(), Set.of(), plugin);
                settle(host);
                UUID window = host.addTerminalWindow();
                assertThat(defaults(host, RemotePlugin.NEW_HOST)).as("unbound until the user binds it").isEqualTo("none|derived|GLOBAL");
                assertThat(host.menu("SESSION")).containsSubsequence("item:" + RemotePlugin.NEW_HOST, "item:" + RemotePlugin.IMPORT);
                assertThat(host.invoke(RemotePlugin.NEW_HOST, window, null)).isTrue();
                assertThat(host.failures()).isEmpty();
                assertThat(host.windows()).containsExactly("dialog|Add Host|true");
                assertThat(plugin.hostsPanel()).as("the Sessions panel stays closed").isNull();
                name(plugin.currentEditor(), "new"); hostname(plugin.currentEditor(), "new.invalid"); agentAuth(plugin.currentEditor());
                username(plugin.currentEditor(), "root");
                saveAndConnect(plugin.currentEditor());
                settle(host);
                assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
                assertThat(host.windows()).as("Add and Connect closes the form and connects to the saved host")
                    .containsExactly("overlay|Connecting to new|true");
                host.stopAll();
                assertThat(host.failures()).isEmpty();
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
    @Test void anOpenedHostNamesItsTabsHostPiece(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            assertThat(host.tabNamePieceSpecs()).extracting(dev.jasper.sdk.terminal.TabNamePieceSpec::name).containsExactly("host");
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            plugin.store().put(prod);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            UUID local = host.addTerminalTab(window, "zsh");
            var handle = context.terminals().window(window).orElseThrow();
            Kits.edt(() -> plugin.openHost(handle, prod)); settle(host);
            UUID pane = host.terminalPanes().getLast();
            UUID tab = Kits.value(() -> context.terminals().pane(pane).orElseThrow().tab().id());
            assertThat(host.tabNamePieces(tab)).as("the saved host's address").isEqualTo(Map.of("host", "127.0.0.1"));
            assertThat(host.tabNamePieces(local)).as("a local tab has no host").isEmpty();
            Kits.edt(() -> host.typeIntoSession(pane, "q")); settle(host);
            assertThat(host.tabNamePieces(tab)).as("kept after the connection ends").isEqualTo(Map.of("host", "127.0.0.1"));
            host.stopAll();
            assertThat(host.tabNamePieceSpecs()).as("the piece ends with Remote").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void remoteStartsWhenAnotherPluginHoldsTheHostTabNamePiece(@TempDir Path dir) throws Exception {
        var logged = new ArrayList<String>();
        var logger = java.util.logging.Logger.getLogger("dev.jasper.plugins.dev.jasper.remote");
        var capture = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) logged.add(record.getMessage() + " " + java.util.Arrays.toString(record.getParameters())); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(capture);
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            // A third-party plugin that sorts before dev.jasper.remote starts first and holds {host}.
            var other = new PluginInfo("dev.jasper.aaa", "Aaa", "1.0.0", Set.of(Capabilities.TERMINAL_OBSERVE));
            host.start(other, Set.of(), Set.of(), context -> context.terminals().addTabNamePiece(
                new dev.jasper.sdk.terminal.TabNamePieceSpec("host", "the other plugin's host")));
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            assertThat(host.failures()).as("Remote starts despite the clash").isEmpty();
            assertThat(host.actionSpec("dev.jasper.remote.connect")).as("Remote's contributions are there").isPresent();
            assertThat(host.tabNamePieceSpecs()).extracting(dev.jasper.sdk.terminal.TabNamePieceSpec::description).containsExactly("the other plugin's host");
            assertThat(logged).as("the clash is logged once").hasSize(1).allMatch(line -> line.contains("{host}") && line.contains("dev.jasper.aaa"));

            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            plugin.store().put(prod);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.addTerminalTab(window, "zsh");
            var handle = context.terminals().window(window).orElseThrow();
            Kits.edt(() -> plugin.openHost(handle, prod)); settle(host);
            UUID pane = host.terminalPanes().getLast();
            UUID tab = Kits.value(() -> context.terminals().pane(pane).orElseThrow().tab().id());
            assertThat(host.tabNamePieces(tab)).as("an SSH tab opens; Remote leaves the other plugin's piece alone").isEmpty();
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        } finally {
            logger.removeHandler(capture);
        }
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
            Kits.edt(() -> host.openPanel(SftpUi.PANEL));
            var panel = SftpTestAccess.panel(plugin.sftp());
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
            Kits.edt(() -> host.openPanel(SftpUi.PANEL));
            var panel = SftpTestAccess.panel(plugin.sftp());
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
            assertThat(host.scopePresentation("dev.jasper.remote.scope")).as("spec 14.4").isEqualTo("GREEN|session|STANDARD,SPLIT,STANDARD");
            assertThat(host.menu("SESSION")).isNotEmpty();
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
                assertThat(host.openPanel(RemotePlugin.PANEL)).as("a kit view, no raw component").isNotNull();
                assertThat(plugin.hostsPanel()).isNotNull();

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
                host.openPanel(RemotePlugin.PANEL);
                var panel = plugin.hostsPanel();
                assertThat(panel).isNotNull();
                headerAction(host, "New session");
                assertThat(host.windows()).containsExactly("dialog|Add Host|true");
                name(plugin.currentEditor(), "new"); hostname(plugin.currentEditor(), "new.example"); agentAuth(plugin.currentEditor());
                username(plugin.currentEditor(), "root");
                save(plugin.currentEditor());
                settle(host);
                assertThat(host.windows()).isEmpty();
                assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
                assertThat(rows(panel)).as("ungrouped host without a redundant header").hasSize(1);
                assertThat(host.panelHeader(RemotePlugin.PANEL).detail()).isEmpty();
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
    static void headerAction(FakePluginHost host, String label) {
        host.panelHeader(RemotePlugin.PANEL).actions().stream().filter(action -> action.label().equals(label)).findFirst()
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
                assertThat(host.openPanel(RemotePlugin.PANEL)).isNotNull();
            }
        });
    }

    @Test void failedMutationsReportAfterTheEditorCloses(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                var plugin = plugin(dir);
                var context = host.start(INFO, Set.of(), Set.of(), plugin);
                host.addTerminalWindow();
                host.openPanel(RemotePlugin.PANEL);
                var panel = plugin.hostsPanel();
                var saved = RemoteHost.create("saved", "example", 22, "me", Auth.AGENT, "", Optional.empty());
                plugin.store().put(saved); settle(host);
                Files.writeString(context.dataDirectory().resolve("hosts.toml"), "broken = [");
                int row = indexOf(panel, saved);
                menu(panel, row, "Duplicate");
                menu(panel, row, "Add to favorites");
                headerAction(host, "New session");
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

    /** An SSH agent holding one RSA key, which {@code server} accepts. */
    private static dev.jasper.remote.agent.FakeAgent agent(LoopbackServer server) throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        server.allow(pair.getPublic());
        return new dev.jasper.remote.agent.FakeAgent(pair);
    }

    /** {@link #plugin(Path)}, signing in through {@code agent} as the SSH agent. */
    RemotePlugin plugin(Path sshDir, dev.jasper.remote.agent.FakeAgent agent) {
        return new TestRemotePlugin(Kits.UI, context -> Optional.of(new dev.jasper.remote.agent.AgentClient(agent::handle)),
            (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
    }

    /**
     * Workspace tabs spec 13: an SSH tab gives its host and comes back through Remote's restorer, waiting until it is
     * shown; then not connected while signing in would ask (a Vault login), and connected when nothing has to be asked
     * (the SSH agent, workspace plan 6b Rulings 9). A state naming no host declines, which is no failure.
     */
    @Test void anSshTabComesBackAndConnectsOnlyWhenSigningInAsksNothing(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", new Auth.Vault(vault.password("p", "deploy", "s3cret")), "", Optional.empty());
            var signed = RemoteHost.create("build", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(saved); plugin.store().put(signed); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), saved)); settle(host);
            UUID pane = host.terminalPanes().getFirst();
            UUID tab = Kits.value(() -> context.terminals().pane(pane).orElseThrow().tab().id());
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            assertThat(Kits.value(() -> host.restoreState(tab))).contains(state);

            vault.state = dev.jasper.vault.api.LockState.LOCKED;
            Optional<UUID> locked = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state));
            assertThat(locked).isPresent();
            settle(host);
            UUID waiting = Kits.value(() -> context.terminals().tab(locked.get()).orElseThrow().terminal().orElseThrow().id());
            assertThat(host.sessionState(waiting)).as("restored, it waits to be shown").isEqualTo("NOT_STARTED|");
            Kits.edt(() -> host.focusTerminalPane(waiting));
            Kits.await(host, () -> host.sessionState(waiting).startsWith("EXITED|"), "the restored tab to decide");
            assertThat(host.sessionState(waiting)).as("signing in would ask: not connected, with Reconnect").startsWith("EXITED|Not connected");
            assertThat(host.windows()).as("nothing asks at restore").isEmpty();
            vault.state = dev.jasper.vault.api.LockState.UNLOCKED;
            Kits.edt(() -> host.reconnectSession(waiting)); settle(host);
            assertThat(host.sessionState(waiting)).isEqualTo("RUNNING|");

            // The SSH agent signs in asking nothing: restored, its tab connects once it is first shown.
            var agentState = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", signed.id().toString(), "name", "build"));
            Optional<UUID> alone = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "build", agentState));
            settle(host);
            UUID connected = Kits.value(() -> context.terminals().tab(alone.orElseThrow()).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> host.focusTerminalPane(connected));
            Kits.await(host, () -> host.sessionState(connected).equals("RUNNING|"), "the restored tab to connect");
            assertThat(Kits.value(() -> host.restoreState(alone.get()))).contains(agentState);

            assertThat(Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "gone",
                new dev.jasper.sdk.RestoreState("ssh", Map.of("host", "not-a-host"))))).isEmpty();
            assertThat(Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "deleted",
                new dev.jasper.sdk.RestoreState("ssh", Map.of("host", UUID.randomUUID().toString()))))).as("hosts loaded: a gone host").isEmpty();
            assertThat(host.declinedRestores()).hasSize(2).allSatisfy(line -> assertThat(line).contains("it names no saved host"));
            assertThat(host.failures()).as("expected outcomes, no failures").isEmpty();
        }
    }

    /**
     * Workspace plan 6b Rulings 9: a Vault login never signs in alone at restore, even while the Vault is unlocked, since
     * the Vault may ask to grant Remote the credential: the restored tab asks for none and offers Reconnect, which signs in.
     */
    @Test void aVaultSignedSshTabRestoredWhileTheVaultIsUnlockedAsksForNoCredentialAndOffersReconnect(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID login = vault.password("p", "deploy", "s3cret");
            var secret = vault.secrets.get(login);
            var asked = new java.util.concurrent.atomic.AtomicInteger();
            vault.secrets.put(login, () -> { asked.incrementAndGet(); return secret.get(); });
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", new Auth.Vault(login), "", Optional.empty());
            plugin.store().put(saved); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            assertThat(vault.state).isEqualTo(dev.jasper.vault.api.LockState.UNLOCKED);
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> host.focusTerminalPane(pane));
            Kits.await(host, () -> host.sessionState(pane).startsWith("EXITED|"), "the restored tab to decide");
            assertThat(host.sessionState(pane)).isEqualTo("EXITED|Not connected: Reconnect to sign in");
            assertThat(asked).as("no credential asked for at restore").hasValue(0);
            assertThat(host.windows()).as("nothing asks at restore").isEmpty();
            Kits.edt(() -> host.reconnectSession(pane)); settle(host);
            assertThat(host.sessionState(pane)).as("Reconnect signs in through the Vault").isEqualTo("RUNNING|");
            assertThat(asked.get()).isPositive();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace plan 6b's review: a restored tab's quiet connect that fails says why, asks nothing and offers Reconnect. */
    @Test void aQuietConnectThatFailsSaysWhyAndOffersReconnect(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var plugin = plugin(dir, new dev.jasper.remote.agent.FakeAgent());
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(saved); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> host.focusTerminalPane(pane));
            Kits.await(host, () -> host.sessionState(pane).startsWith("EXITED|"), "the quiet connect to fail");
            assertThat(host.sessionState(pane)).as("why, with Reconnect").startsWith("EXITED|Not connected: ").contains("no usable keys");
            assertThat(host.windows()).as("its overlay closed, nothing asked").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace tabs spec 13: restored SSH tabs shown together in one window connect one after another, none refused. */
    @Test void restoredSshTabsShownTogetherInOneWindowAllConnect(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(saved); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            UUID first = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID second = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID one = Kits.value(() -> context.terminals().tab(first).orElseThrow().terminal().orElseThrow().id());
            UUID two = Kits.value(() -> context.terminals().tab(second).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> { host.focusTerminalPane(one); host.focusTerminalPane(two); });
            Kits.await(host, () -> host.sessionState(one).equals("RUNNING|") && host.sessionState(two).equals("RUNNING|"),
                "both restored tabs to connect");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b Rulings 29: a restored SSH tab whose host key is not trusted (a host accepted Once is unknown again
     * at the next start) connects quietly, asks nothing and offers Reconnect, which asks as usual.
     */
    @Test void aRestoredSshTabWhoseHostKeyIsUnknownAsksNothingAndOffersReconnect(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(saved); settle(host);
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            // The host's key is unknown: the question it raises mid-handshake is the one asked here. The fake's background
            // work is left queued, so each connect stays in progress while its question is asked.
            var question = new HostKeyVerifier.Question("127.0.0.1", server.port(), "ssh-ed25519", "SHA256:unknown");
            Kits.edt(() -> {
                host.focusTerminalPane(pane);
                assertThat(host.windows()).as("its first connect runs").containsExactly("overlay|Connecting to prod|true");
                var asked = plugin.askHostKey(context.terminals().window(window).orElseThrow(), question);
                assertThat(asked).as("a quiet connect runs there: Cancel, unasked").isCompletedWithValue(HostKeyVerifier.Decision.CANCEL);
                assertThat(host.windows()).as("no host-key question shown").containsExactly("overlay|Connecting to prod|true");
                // The handshake fails on that Cancel: the quiet connect closes its overlay and leaves Reconnect.
                cancel(plugin.currentConnectionPanel());
                assertThat(host.sessionState(pane)).isEqualTo("EXITED|Not connected: Reconnect to sign in");
                assertThat(host.windows()).as("the overlay closed").isEmpty();
                host.reconnectSession(pane);
                var again = plugin.askHostKey(context.terminals().window(window).orElseThrow(), question);
                assertThat(host.windows()).as("Reconnect asks as usual").contains("dialog|Verify host key 127.0.0.1|true");
                host.requestClose("dialog");
                assertThat(again).isCompletedWithValue(HostKeyVerifier.Decision.CANCEL);
                cancel(plugin.currentConnectionPanel());
            });
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace plan 6b's review: a restored SSH tab closed while it waits for its window's connection passes its turn on. */
    @Test void aRestoredSshTabClosedWhileItWaitsPassesItsTurnOn(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(saved); settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            // A connection in progress in the window, its connect held on the host's background executor until it settles.
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), saved));
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            UUID first = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID second = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID one = Kits.value(() -> context.terminals().tab(first).orElseThrow().terminal().orElseThrow().id());
            UUID two = Kits.value(() -> context.terminals().tab(second).orElseThrow().terminal().orElseThrow().id());
            // Both shown while the window's connection is in progress: both wait for it, the first one first.
            Kits.edt(() -> { host.focusTerminalPane(one); host.focusTerminalPane(two); });
            Kits.edt(() -> host.closeTerminalPane(one));
            Kits.edt(() -> cancel(plugin.currentConnectionPanel()));
            Kits.await(host, () -> host.sessionState(two).equals("RUNNING|"), "the second waiting tab to connect");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6a Task 1's hand-off (Rulings 41): picking a host whose restored SSH tab still waits to be shown,
     * NOT_STARTED, shows that tab, which then decides how to connect, instead of opening a second one.
     */
    @Test void activatingAHostShowsItsRestoredTabThatWaitsToBeShown(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var saved = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", new Auth.Vault(vault.password("p", "deploy", "s3cret")), "", Optional.empty());
            plugin.store().put(saved); settle(host);
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", saved.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            assertThat(host.sessionState(pane)).isEqualTo("NOT_STARTED|");
            Kits.edt(() -> plugin.activateHost(context.terminals().window(window).orElseThrow(), saved));
            Kits.await(host, () -> host.sessionState(pane).startsWith("EXITED|"), "the shown tab to decide");
            assertThat(host.terminalPanes()).as("no second tab for the host").containsExactly(pane);
            assertThat(host.sessionState(pane)).as("shown, it decides as any restored tab").startsWith("EXITED|Not connected");
            assertThat(host.failures()).isEmpty();
        }
    }

    /** A Vault whose one login counts each time Remote asks for its secret. */
    record CountedVault(FakeVault vault, UUID login, java.util.concurrent.atomic.AtomicInteger asked) { }

    static CountedVault countedVault(FakePluginHost host) {
        var vault = new FakeVault(); host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
        UUID login = vault.password("p", "deploy", "s3cret");
        var secret = vault.secrets.get(login);
        var asked = new java.util.concurrent.atomic.AtomicInteger();
        vault.secrets.put(login, () -> { asked.incrementAndGet(); return secret.get(); });
        return new CountedVault(vault, login, asked);
    }

    static void trustServer(dev.jasper.sdk.plugin.PluginContext context, LoopbackServer server) throws Exception {
        new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
    }

    /** Restores an SSH tab of {@code target} in a new window, shows it and waits until it decides: its pane. */
    static UUID restoredTabDecides(FakePluginHost host, dev.jasper.sdk.plugin.PluginContext context, RemoteHost target) throws Exception {
        UUID window = host.addTerminalWindow();
        var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", target.id().toString(), "name", target.name()));
        UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, target.name(), state)).orElseThrow();
        UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
        Kits.edt(() -> host.focusTerminalPane(pane));
        Kits.await(host, () -> host.sessionState(pane).startsWith("EXITED|"), "the restored tab to decide");
        return pane;
    }

    /**
     * Runs the host's background work on this thread, not the EDT, until {@code condition} holds: a handshake's host-key
     * question then reaches the EDT while its connect waits for the answer, as in Jasper.
     */
    static void drainOffTheEdt(FakePluginHost host, java.util.function.BooleanSupplier condition, String what) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (!Kits.value(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            host.runBackground();
            Kits.edt(host::flush);
            Thread.sleep(10);
        }
    }

    /** Workspace plan 6b's review probe: an agent whose key the host does not accept says why, asking nothing. */
    @Test void aRestoredSshTabWhoseAgentHoldsNoKeyTheHostAcceptsSaysWhy(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var counted = countedVault(host);
            var generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var plugin = plugin(dir, new dev.jasper.remote.agent.FakeAgent(generator.generateKeyPair()));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(target); settle(host);
            trustServer(context, server);
            UUID pane = restoredTabDecides(host, context, target);
            assertThat(host.sessionState(pane)).startsWith("EXITED|Not connected: ").isNotEqualTo("EXITED|Not connected: Reconnect to sign in");
            assertThat(counted.asked()).as("the Vault asked for nothing").hasValue(0);
            assertThat(host.windows()).as("no window opened").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace plan 6b's review probe: no SSH agent says why, asking nothing. */
    @Test void aRestoredSshTabWithNoAgentSaysWhy(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var counted = countedVault(host);
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(target); settle(host);
            trustServer(context, server);
            UUID pane = restoredTabDecides(host, context, target);
            assertThat(host.sessionState(pane)).startsWith("EXITED|Not connected: ").isNotEqualTo("EXITED|Not connected: Reconnect to sign in");
            assertThat(counted.asked()).hasValue(0);
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace plan 6b's review probe: a host whose key changed is refused, asking nothing, and the tab says so. */
    @Test void aRestoredSshTabWhoseHostKeyChangedSaysSo(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var counted = countedVault(host);
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(target); settle(host);
            var generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty())
                .trust("127.0.0.1", server.port(), generator.generateKeyPair().getPublic());
            UUID pane = restoredTabDecides(host, context, target);
            assertThat(host.sessionState(pane)).startsWith("EXITED|Not connected: ").contains("fingerprint changed");
            assertThat(counted.asked()).hasValue(0);
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace plan 6b's review probe: a Vault-signed jump host on the route asks the Vault for nothing at restore. */
    @Test void aRestoredSshTabBehindAVaultSignedJumpHostAsksTheVaultForNothing(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var counted = countedVault(host);
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            var jump = RemoteHost.create("bastion", "127.0.0.1", server.port(), "deploy", new Auth.Vault(counted.login()), "", Optional.empty());
            plugin.store().put(jump); settle(host);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.of(jump.id()));
            plugin.store().put(target); settle(host);
            trustServer(context, server);
            UUID pane = restoredTabDecides(host, context, target);
            assertThat(host.sessionState(pane)).isEqualTo("EXITED|Not connected: Reconnect to sign in");
            assertThat(counted.asked()).hasValue(0);
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b's review: a restored tab's unknown host key, answered Cancel quietly mid-handshake, is no reason to
     * show: the tab offers Reconnect, asking nothing.
     */
    @Test void aRestoredSshTabsUnknownHostKeyAnsweredQuietlyMidHandshakeOffersReconnect(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(target); settle(host);
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", target.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> host.focusTerminalPane(pane));
            drainOffTheEdt(host, () -> host.sessionState(pane).startsWith("EXITED|"), "the handshake to fail on its quiet Cancel");
            assertThat(host.sessionState(pane)).isEqualTo("EXITED|Not connected: Reconnect to sign in");
            assertThat(host.windows()).as("no host-key question shown, the overlay closed").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace plan 6b's review: a quiet connect whose attach fails says why, closes its overlay and offers Reconnect. */
    @Test void aQuietConnectWhoseAttachFailsSaysWhyAndOffersReconnect(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var agent = agent(server);
            var failing = new java.util.concurrent.atomic.AtomicBoolean(true);
            var attached = new java.util.concurrent.atomic.AtomicInteger();
            RemotePlugin plugin = new TestRemotePlugin(Kits.UI, context -> Optional.of(new dev.jasper.remote.agent.AgentClient(agent::handle)),
                    (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, dir) {
                @Override void attach(dev.jasper.sdk.terminal.PendingSession pending, dev.jasper.remote.client.Connections.Shell shell) {
                    attached.incrementAndGet();
                    if (failing.getAndSet(false)) throw new IllegalStateException("its pane could not take the shell");
                    super.attach(pending, shell);
                }
            };
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(target); settle(host);
            trustServer(context, server);
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", target.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> host.focusTerminalPane(pane));
            Kits.await(host, () -> attached.get() == 1, "the shell to reach the tab");
            settle(host);
            assertThat(host.sessionState(pane)).isEqualTo("EXITED|Not connected: its pane could not take the shell");
            assertThat(host.windows()).as("its overlay closed").isEmpty();
            Kits.edt(() -> host.reconnectSession(pane));
            Kits.await(host, () -> host.sessionState(pane).equals("RUNNING|"), "Reconnect to connect");
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace plan 6b's review: a quiet connect whose host was deleted while it waited says it is not connected. */
    @Test void aRestoredSshTabWhoseHostIsDeletedWhileItWaitsSaysItIsNotConnected(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            // A host nothing listens on: its connect fails and its overlay stays, with Retry, holding the window.
            var down = RemoteHost.create("down", "127.0.0.1", 1, "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(target); plugin.store().put(down); settle(host);
            trustServer(context, server);
            UUID window = host.addTerminalWindow();
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), down)); settle(host);
            assertThat(host.windows()).containsExactly("overlay|Connecting to down|true");
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", target.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> host.focusTerminalPane(pane)); settle(host);
            assertThat(host.sessionState(pane)).as("it waits for the window's connection").startsWith("CONNECTING|");
            plugin.store().remove(target.id()); settle(host);
            Kits.edt(() -> cancel(plugin.currentConnectionPanel())); settle(host);
            assertThat(host.sessionState(pane)).isEqualTo("EXITED|Not connected: Host not found");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b's review: an SSH tab opened in a folder too long for its restore state still opens and connects, and
     * comes back to its host, without that folder.
     */
    @Test void anSshTabWhoseFolderDoesNotFitItsRestoreStateStillOpensAndComesBackWithoutIt(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty()).withFollowDirectory(false);
            plugin.store().put(target); settle(host);
            trustServer(context, server);
            UUID window = host.addTerminalWindow();
            String folder = "/" + "f".repeat(4100);
            Kits.edt(() -> plugin.openHostIn(context.terminals().window(window).orElseThrow(), target, folder));
            Kits.await(host, () -> host.terminalPanes().size() == 1 && host.sessionState(host.terminalPanes().getFirst()).equals("RUNNING|"),
                "the tab to open and connect");
            UUID tab = Kits.value(() -> context.terminals().pane(host.terminalPanes().getFirst()).orElseThrow().tab().id());
            assertThat(Kits.value(() -> host.restoreState(tab))).as("its host and name, without the folder")
                .contains(new dev.jasper.sdk.RestoreState("ssh", Map.of("host", target.id().toString(), "name", "prod")));
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b's review: while Remote's hosts.toml cannot be read, a restored SSH tab says so, and a restore once
     * the read has failed declines saying so, rather than that its host is no longer saved.
     */
    @Test void aRestoredSshTabSaysWhenHostsTomlCannotBeRead(@TempDir Path dir) throws Exception {
        Path data = java.nio.file.Files.createDirectories(dir.resolve("plugins").resolve("dev.jasper.remote").resolve("data"));
        java.nio.file.Files.writeString(data.resolve("hosts.toml"), "[[host]\nname = ");
        try (var host = new FakePluginHost(dir.resolve("plugins"))) {
            var plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", UUID.randomUUID().toString(), "name", "prod"));
            // Restored while the saved hosts still load, the tab comes back and decides once they have been read.
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            settle(host);
            Kits.edt(() -> host.focusTerminalPane(pane)); settle(host);
            assertThat(host.sessionState(pane)).startsWith("EXITED|Remote's hosts.toml could not be read: ").contains("TOML").doesNotContain("\n");
            assertThat(Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "again", state))).isEmpty();
            assertThat(host.declinedRestores()).singleElement().asString().contains("Remote's hosts.toml could not be read: ").contains("TOML");
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b's review: picking a host shows its running tab first; a restored tab of that host waiting to be shown
     * stays waiting, and no second connection starts.
     */
    @Test void activatingAHostShowsItsRunningTabBeforeARestoredOneThatWaits(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(target); settle(host);
            trustServer(context, server);
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), target));
            Kits.await(host, () -> host.terminalPanes().size() == 1 && host.sessionState(host.terminalPanes().getFirst()).equals("RUNNING|"), "a running tab");
            UUID running = host.terminalPanes().getFirst();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", target.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID waiting = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            Kits.edt(() -> plugin.activateHost(context.terminals().window(window).orElseThrow(), target)); settle(host);
            assertThat(Kits.value(() -> context.terminals().activePane().map(dev.jasper.sdk.terminal.PaneHandle::id))).contains(running);
            assertThat(host.sessionState(waiting)).as("still waiting: no second connection").isEqualTo("NOT_STARTED|");
            assertThat(host.terminalPanes()).hasSize(2);
            assertThat(host.failures()).isEmpty();
        }
    }

    /** Workspace plan 6b's review: a restored SSH tab closed before it connects leaves Remote's waiting tabs. */
    @Test void aRestoredSshTabClosedBeforeItConnectsLeavesTheWaitingTabs(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", 22, "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(target); settle(host);
            UUID window = host.addTerminalWindow();
            var state = new dev.jasper.sdk.RestoreState("ssh", Map.of("host", target.id().toString(), "name", "prod"));
            UUID tab = Kits.value(() -> host.restoreTab("dev.jasper.remote", window, "prod", state)).orElseThrow();
            UUID pane = Kits.value(() -> context.terminals().tab(tab).orElseThrow().terminal().orElseThrow().id());
            assertThat(Kits.value(plugin::restoredPaneIds)).containsExactly(pane);
            Kits.edt(() -> host.closeTerminalPane(pane)); settle(host);
            assertThat(Kits.value(plugin::restoredPaneIds)).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b Rulings 29, a document's half: while a restored document's quiet channel opens in a window, a host-key
     * question there is answered Cancel, unasked; once it has opened, one is asked as usual.
     */
    @Test void aDocumentsQuietOpenAnswersItsWindowsHostKeyQuestionsCancelUntilItEnds(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            host.start(RemoteEditingPluginTest.EDITOR, Set.of(), Set.of(), new RemoteEditingPluginTest.FakeEditor());
            var plugin = plugin(dir, agent(server));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.editor"), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", Auth.AGENT, "", Optional.empty());
            plugin.store().put(target); settle(host);
            UUID window = host.addTerminalWindow();
            var question = new HostKeyVerifier.Question("127.0.0.1", server.port(), "ssh-ed25519", "SHA256:unknown");
            Kits.edt(() -> {
                var handle = context.terminals().window(window).orElseThrow();
                // The fake's background work is left queued, so the quiet open stays in progress while the question is asked.
                var opening = plugin.openQuietly(plugin.connections().identity(target.id()), Optional.of(handle));
                assertThat(plugin.askHostKey(handle, question)).as("Cancel, unasked").isCompletedWithValue(HostKeyVerifier.Decision.CANCEL);
                assertThat(host.windows()).as("no host-key question shown").isEmpty();
                opening.cancel(true);
                var again = plugin.askHostKey(handle, question);
                assertThat(host.windows()).as("asked as usual once it ended").contains("dialog|Verify host key 127.0.0.1|true");
                host.requestClose("dialog");
                assertThat(again).isCompletedWithValue(HostKeyVerifier.Decision.CANCEL);
            });
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Workspace plan 6b's review: a document's quiet channel checks again, as it opens, that signing in asks nothing; the
     * connection that let it read quietly may have closed since, and a Vault login would then ask.
     */
    @Test void aDocumentsQuietOpenChecksAgainThatSigningInAsksNothing(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var counted = countedVault(host);
            host.start(RemoteEditingPluginTest.EDITOR, Set.of(), Set.of(), new RemoteEditingPluginTest.FakeEditor());
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin);
            var target = RemoteHost.create("prod", "127.0.0.1", server.port(), "deploy", new Auth.Vault(counted.login()), "", Optional.empty())
                .withFollowDirectory(false);
            plugin.store().put(target); settle(host);
            trustServer(context, server);
            UUID window = host.addTerminalWindow();
            Kits.edt(() -> plugin.openHost(context.terminals().window(window).orElseThrow(), target));
            Kits.await(host, () -> host.terminalPanes().size() == 1 && host.sessionState(host.terminalPanes().getFirst()).equals("RUNNING|"), "a connection");
            var identity = plugin.connections().identity(target.id());
            assertThat(Kits.value(() -> plugin.connections().authenticated(identity))).as("signing in asks nothing while it is open").isTrue();
            // The connection closes: its last tab closes and its linger ends.
            Kits.edt(() -> host.closeTerminalPane(host.terminalPanes().getFirst())); settle(host);
            Kits.edt(() -> List.copyOf(scheduled).forEach(Runnable::run)); settle(host);
            assertThat(Kits.value(() -> plugin.connections().authenticated(identity))).as("closed").isFalse();
            counted.asked().set(0);
            var opening = Kits.value(() -> plugin.openQuietly(identity, context.terminals().window(window)));
            settle(host);
            assertThat(opening).isCompletedExceptionally();
            assertThatThrownBy(opening::join).hasRootCauseMessage("Not signed in to prod; Retry signs in");
            assertThat(counted.asked()).as("the Vault asked for nothing").hasValue(0);
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void renamesDefaultGroupThroughThePanel(@TempDir Path dir) throws Exception {
        Kits.edt(() -> {
            try (var host = new FakePluginHost()) {
                var plugin = plugin(dir);
                var context = host.start(INFO, Set.of(), Set.of(), plugin);
                host.addTerminalWindow();
                var saved = RemoteHost.create("home", "example", 22, "me", Auth.AGENT, "", Optional.empty());
                plugin.store().put(saved); settle(host);
                host.openPanel(RemotePlugin.PANEL);
                var panel = plugin.hostsPanel();
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
                host.openPanel(RemotePlugin.PANEL);
                var panel = plugin.hostsPanel();
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
