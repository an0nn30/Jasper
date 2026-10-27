package dev.jasper.remote;

import dev.jasper.remote.transfer.*;
import dev.jasper.remote.transfer.store.TransferStore;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SftpPluginTest {
    @TempDir Path root;
    @Test void contributionsAndPausedStartupWorkWithoutBuddyOrAuthentication() throws Exception {
        UUID restored;
        var remote=dev.jasper.remote.hosts.RemoteHost.create("offline","not-contacted.invalid",22,"user",dev.jasper.remote.hosts.Auth.AGENT,"",Optional.empty());
        var identity=new dev.jasper.remote.client.ConnectionIdentity(List.of(new dev.jasper.remote.client.ConnectionIdentity.Hop(remote,"user")));
        try(var queue=new TransferStore(root.resolve("home/dev.jasper.remote/data/transfers"))) {
            restored=queue.create(new TransferRequest(EndpointRef.remote(identity),List.of("/data/file"),EndpointRef.local(),root.toString()));queue.markRunning(restored);
        }
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            var context=host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            assertThat(host.failures()).isEmpty();assertThat(host.menu("TRANSFER")).containsExactly("item:dev.jasper.remote.sftp","item:dev.jasper.remote.transfers");
            assertThat(host.panels()).contains("dev.jasper.remote.sftp.panel|SFTP|LEFT+split|dev.jasper.remote.sftp.toggle").noneMatch(panel->panel.startsWith("dev.jasper.remote.transfers.panel"));
            assertThat(host.actions()).noneMatch(action->action.startsWith("dev.jasper.remote.transfers.toggle"));
            assertThat(host.openRequests()).isEmpty();assertThat(host.windows()).isEmpty();
            assertThat(plugin.transfers().job(restored).get(5,TimeUnit.SECONDS).state()).isEqualTo(TransferState.PAUSED);
            host.setSetting("dev.jasper.remote","sftp.max_parallel_files",8L);host.setSetting("dev.jasper.remote","sftp.request_timeout_seconds",1L);
            assertThat(plugin.sftpSettings().maxParallelFiles()).isEqualTo(8);assertThat(plugin.sftpSettings().requestTimeout().toSeconds()).isEqualTo(1);
            UUID window=host.addTerminalWindow(),second=host.addTerminalWindow();dev.jasper.remote.testsupport.Kits.edt(()-> { host.invoke("dev.jasper.remote.transfers",window,null);host.invoke("dev.jasper.remote.transfers",second,null); });
            assertThat(host.openRequests()).as("each window's request shows the panel there, raising no other window").isEmpty();assertThat(host.failures()).isEmpty();
            assertThat(host.windows()).as("showing transfers never opens the host chooser").isEmpty();
            dev.jasper.remote.testsupport.Kits.edt(()->host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL));
            var sidebar=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp());
            dev.jasper.remote.testsupport.Kits.await(host,()->dev.jasper.remote.testsupport.Kits.find(sidebar.view(),dev.jasper.remote.ui.transfers.TransferStrip.ROWS).isPresent(),
                "the restored transfer shows");
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.texts(sidebar)).as("the strip lives in the SFTP sidebar").contains("Transfers","Paused");
        }
    }

    /** Task 6d acceptance carried into Task 8: the SFTP header keeps its connection name and connected dot through setHeader. */
    @Test void theSftpHeaderCarriesTheConnectionAndItsDotAndTheShortcutsActOnTheWindowsExplorer() throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            UUID window=host.addTerminalWindow();
            dev.jasper.remote.testsupport.Kits.edt(()->host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL));
            var panel=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp());
            var header=host.panelHeader(dev.jasper.remote.ui.sftp.SftpUi.PANEL);
            assertThat(header.detail()).isEmpty();assertThat(header.active()).isFalse();assertThat(header.actions()).isEmpty();
            var moves=new ArrayList<String>();
            dev.jasper.remote.testsupport.Kits.edt(()-> {
                panel.navigation(()->moves.add("back"),()->moves.add("forward"));
                panel.showPage("prod","/srv",List.of(new dev.jasper.remote.sftp.FileEntry("a",dev.jasper.remote.sftp.FileEntry.Kind.FILE,1,0,0644,"")),0,1,false);
                panel.navigationAvailable(true,true);
            });
            header=host.panelHeader(dev.jasper.remote.ui.sftp.SftpUi.PANEL);
            assertThat(header.detail()).as("the connection name").isEqualTo("prod");
            assertThat(header.active()).as("the connected dot").isTrue();
            assertThat(header.actions()).isEmpty();
            dev.jasper.remote.testsupport.Kits.edt(()-> {
                host.invoke(dev.jasper.remote.ui.sftp.SftpUi.BACK,window,null);
                host.invoke(dev.jasper.remote.ui.sftp.SftpUi.FORWARD,window,null);
            });
            assertThat(moves).containsExactly("back","forward");
            UUID other=host.addTerminalWindow();
            dev.jasper.remote.testsupport.Kits.edt(()->host.invoke(dev.jasper.remote.ui.sftp.SftpUi.BACK,other,null));
            assertThat(moves).as("Back from a window with no explorer of its own acts on no other window's").containsExactly("back","forward");
            dev.jasper.remote.testsupport.Kits.edt(()->panel.clear("SSH session closed"));
            header=host.panelHeader(dev.jasper.remote.ui.sftp.SftpUi.PANEL);
            assertThat(header.detail()).isEmpty();assertThat(header.active()).isFalse();
            host.closePanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL);
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp())).as("a closed panel leaves no explorer").isNull();
            dev.jasper.remote.testsupport.Kits.edt(()->host.invoke(dev.jasper.remote.ui.sftp.SftpUi.BACK,window,null));
            assertThat(moves).as("a closed panel has nothing to go back in").hasSize(2);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
    @Test void cancellingResumeValidationWithdrawsPendingCredentialResolution() {
        var source=new CompletableFuture<dev.jasper.remote.client.ConnectionIdentity>();
        var validated=RemotePlugin.validateResumeIdentity(null,source);
        validated.cancel(true);
        assertThat(source.isCancelled()).isTrue();
    }

    /**
     * Plan 4b's final review: the explorer's window, the main one, guards a Browse's follow-up, so a requesting child window
     * that closes while the host's credential is pending ends the attempt and leaves the explorer idle, not "Connecting…".
     */
    @Test void aBrowseWhoseWindowClosesMidConnectLeavesTheOtherWindowAlone() throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var vault=new FakeVault();host.start(FakeVault.INFO,Set.of(),Set.of(),vault);
            var plugin=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            var context=host.start(RemotePluginTest.INFO,Set.of(),Set.of("dev.jasper.vault"),plugin);
            UUID credential=vault.password("deploy login","deploy","s3cret");
            var prod=dev.jasper.remote.hosts.RemoteHost.create("prod","prod.invalid",22,"",new dev.jasper.remote.hosts.Auth.Vault(credential),"",Optional.empty());
            plugin.store().put(prod);RemotePluginTest.settle(host);
            UUID first=host.addTerminalWindow();host.addTerminalTab(first,"zsh");UUID child=host.addTerminalWindow(),tab=host.addTerminalTab(child,"zsh");
            var pending=new CompletableFuture<Optional<dev.jasper.vault.api.Credential>>();vault.pendingCredential=pending;
            dev.jasper.remote.testsupport.Kits.edt(()->plugin.sftp().browse(context.terminals().window(child).orElseThrow(),prod));
            var sidebar=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),child);
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.texts(sidebar)).contains("Connecting…");
            dev.jasper.remote.testsupport.Kits.edt(()->host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,first));
            var other=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),first);
            var before=dev.jasper.remote.ui.sftp.SftpTestAccess.texts(other);
            // Its last tab moves away, so the window closes, and its explorer with it; then the credential arrives.
            dev.jasper.remote.testsupport.Kits.edt(()-> { host.moveTab(tab,first);pending.complete(Optional.of(vault.secrets.get(credential).get())); });
            RemotePluginTest.settle(host);
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),child)).as("the closed window's explorer closed with it").isNull();
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.texts(other)).as("the other window's explorer is left alone").isEqualTo(before);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Full windows spec 9: Open SFTP here from a second window with no SSH pane shows the panel there, raising no other
     * window, and the host chosen in its chooser browses in the explorer.
     */
    @Test void theHostChooserAskedForFromASecondWindowBrowsesInTheExplorer() throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            plugin.store().put(dev.jasper.remote.hosts.RemoteHost.create("second","second.invalid",22,"user",dev.jasper.remote.hosts.Auth.AGENT,"",Optional.empty()));
            RemotePluginTest.settle(host);
            UUID first=host.addTerminalWindow();
            UUID child=host.addTerminalWindow();host.addTerminalTab(child,"zsh");
            dev.jasper.remote.testsupport.Kits.edt(()->assertThat(host.invoke(RemotePlugin.SFTP,child,null)).isTrue());
            assertThat(host.openRequests()).isEmpty();
            dev.jasper.remote.testsupport.Kits.edt(()-> {
                assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.panelHost(plugin.sftp(),child)).as("the asking window's explorer").hasValueSatisfying(built-> {
                    assertThat(built.window().id()).isEqualTo(child);assertThat(built.visible()).isTrue(); });
                assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.panelHost(plugin.sftp(),first)).as("the other window builds none").isEmpty();
            });
            var chooser=dev.jasper.remote.testsupport.Kits.value(()->host.windowView("dev.jasper.remote","Browse files on SSH host").orElseThrow());
            dev.jasper.remote.ui.sftp.SftpTestAccess.browse(chooser);
            var sidebar=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),child);
            dev.jasper.remote.testsupport.Kits.await(host,()->dev.jasper.remote.testsupport.Kits.texts(sidebar.view()).contains("SSH agent not available"),
                "the chosen host's connection attempt in the explorer");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Full windows spec 9: each window's SFTP explorer is its own. Closing a's leaves b's, whose Choose Session menu still
     * follows the saved hosts and whose choice browses in b's explorer.
     */
    @Test void closingOneWindowsExplorerKeepsTheOthers() throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            plugin.store().put(dev.jasper.remote.hosts.RemoteHost.create("first","first.invalid",22,"user",dev.jasper.remote.hosts.Auth.AGENT,"",Optional.empty()));
            RemotePluginTest.settle(host);
            UUID a=host.addTerminalWindow(),b=host.addTerminalWindow();
            dev.jasper.remote.testsupport.Kits.edt(()-> { host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,a);host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,b); });
            var inA=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),a);
            var inB=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),b);
            assertThat(inA).isNotNull().isNotSameAs(inB);
            plugin.store().put(dev.jasper.remote.hosts.RemoteHost.create("second","second.invalid",22,"user",dev.jasper.remote.hosts.Auth.AGENT,"",Optional.empty()));
            RemotePluginTest.settle(host);
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.sessions(inA)).as("a's menu follows the hosts").contains("second");
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.sessions(inB)).as("b's menu follows the hosts").contains("second");
            dev.jasper.remote.testsupport.Kits.edt(()->host.closePanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,a));
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),a)).isNull();
            plugin.store().put(dev.jasper.remote.hosts.RemoteHost.create("third","third.invalid",22,"user",dev.jasper.remote.hosts.Auth.AGENT,"",Optional.empty()));
            RemotePluginTest.settle(host);
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.sessions(inB)).as("b's menu still follows the hosts").contains("third");
            dev.jasper.remote.ui.sftp.SftpTestAccess.chooseSession(inB,"third");
            dev.jasper.remote.testsupport.Kits.await(host,()->dev.jasper.remote.testsupport.Kits.texts(inB.view()).contains("SSH agent not available"),
                "the chosen host's connection attempt in b's explorer");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    /**
     * Full windows spec 9: a requester that closed before its host was chosen browses nowhere: its own explorer closed with
     * it, and another window's explorer is left alone.
     */
    @Test void aHostChosenAfterTheAskingWindowClosedBrowsesNowhere() throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            plugin.store().put(dev.jasper.remote.hosts.RemoteHost.create("second","second.invalid",22,"user",dev.jasper.remote.hosts.Auth.AGENT,"",Optional.empty()));
            RemotePluginTest.settle(host);
            UUID a=host.addTerminalWindow();host.addTerminalTab(a,"zsh");
            UUID b=host.addTerminalWindow(),tab=host.addTerminalTab(b,"zsh");
            dev.jasper.remote.testsupport.Kits.edt(()->assertThat(host.invoke(RemotePlugin.SFTP,b,null)).isTrue());
            var chooser=dev.jasper.remote.testsupport.Kits.value(()->host.windowView("dev.jasper.remote","Browse files on SSH host").orElseThrow());
            // a's explorer is built after b's, while b's chooser is open.
            dev.jasper.remote.testsupport.Kits.edt(()->host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,a));
            var inA=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),a);
            var before=dev.jasper.remote.ui.sftp.SftpTestAccess.texts(inA);
            dev.jasper.remote.testsupport.Kits.edt(()->host.moveTab(tab,a));
            assertThat(dev.jasper.remote.testsupport.Kits.value(()->host.windowTabs(b))).as("b closed with its last tab").isEmpty();
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),b)).as("b's explorer closed with it").isNull();
            dev.jasper.remote.ui.sftp.SftpTestAccess.browse(chooser);
            RemotePluginTest.settle(host);
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.texts(inA)).as("a's explorer is left alone").isEqualTo(before);
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),b)).as("no explorer is built for the closed window").isNull();
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
}
