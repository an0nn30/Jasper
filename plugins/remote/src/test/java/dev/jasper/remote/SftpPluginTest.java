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
            assertThat(host.openRequests()).isEmpty();assertThat(host.failures()).isEmpty();
            assertThat(host.windows()).as("showing transfers never opens the host chooser").isEmpty();
            dev.jasper.remote.testsupport.Kits.edt(()->host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,window));
            var sidebar=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),window);
            dev.jasper.remote.testsupport.Kits.await(host,()->dev.jasper.remote.testsupport.Kits.find(sidebar.view(),dev.jasper.remote.ui.transfers.TransferStrip.ROWS).isPresent(),
                "the restored transfer shows");
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.texts(sidebar)).as("the strip lives in the SFTP sidebar").contains("Transfers","Paused");
        }
    }

    /** Task 6d acceptance carried into Task 8: the SFTP header keeps its connection name and connected dot through setHeader. */
    @Test void theSftpHeaderCarriesTheConnectionAndItsDotAndTheShortcutsActOnTheWindowsPanel() throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            UUID window=host.addTerminalWindow();
            dev.jasper.remote.testsupport.Kits.edt(()->host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,window));
            var panel=dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),window);
            var header=host.panelHeader(dev.jasper.remote.ui.sftp.SftpUi.PANEL,window);
            assertThat(header.detail()).isEmpty();assertThat(header.active()).isFalse();assertThat(header.actions()).isEmpty();
            var moves=new ArrayList<String>();
            dev.jasper.remote.testsupport.Kits.edt(()-> {
                panel.navigation(()->moves.add("back"),()->moves.add("forward"));
                panel.showPage("prod","/srv",List.of(new dev.jasper.remote.sftp.FileEntry("a",dev.jasper.remote.sftp.FileEntry.Kind.FILE,1,0,0644,"")),0,1,false);
                panel.navigationAvailable(true,true);
            });
            header=host.panelHeader(dev.jasper.remote.ui.sftp.SftpUi.PANEL,window);
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
            assertThat(moves).as("a window without the panel has nothing to go back in").hasSize(2);
            dev.jasper.remote.testsupport.Kits.edt(()->panel.clear("SSH session closed"));
            header=host.panelHeader(dev.jasper.remote.ui.sftp.SftpUi.PANEL,window);
            assertThat(header.detail()).isEmpty();assertThat(header.active()).isFalse();
            host.closePanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,window);
            assertThat(dev.jasper.remote.ui.sftp.SftpTestAccess.panel(plugin.sftp(),window)).as("a closed panel leaves the map").isNull();
            dev.jasper.remote.testsupport.Kits.edt(()->host.invoke(dev.jasper.remote.ui.sftp.SftpUi.BACK,window,null));
            assertThat(moves).hasSize(2);
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

}
