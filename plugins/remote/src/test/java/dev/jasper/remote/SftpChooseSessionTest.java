package dev.jasper.remote;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.testsupport.Kits;
import dev.jasper.remote.ui.sftp.SftpTestAccess;
import dev.jasper.remote.ui.sftp.SftpUi;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/**
 * Choose Session in the disconnected SFTP sidebar is a menu of the saved hosts (Plan 3 Task 6, spec §6.1's dropdown
 * empty-state action): choosing one browses it there, with no chooser dialog in between.
 */
class SftpChooseSessionTest {
    static final String CHOOSER="Browse files on SSH host";

    @Test void withoutSavedHostsTheMenuSaysSoAndOpensNoDialog(@TempDir Path root) throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            var window=host.addTerminalWindow();
            Kits.edt(()->host.openPanel(SftpUi.PANEL,window));
            var sidebar=SftpTestAccess.panel(plugin.sftp(),window);
            assertThat(SftpTestAccess.sessions(sidebar)).containsExactly("No saved sessions");
            assertThatThrownBy(()->SftpTestAccess.chooseSession(sidebar,"No saved sessions")).hasMessageContaining("disabled");
            assertThat(host.windows()).as("no chooser dialog").isEmpty();
            assertThat(Kits.value(()->host.windowView("dev.jasper.remote",CHOOSER))).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** The menu lists the saved hosts literally and follows the host store; choosing one browses it in this sidebar. */
    @Test void theMenuListsSavedHostsLiterallyAndChoosingOneBrowsesIt(@TempDir Path root) throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            var window=host.addTerminalWindow();
            Kits.edt(()->host.openPanel(SftpUi.PANEL,window));
            var sidebar=SftpTestAccess.panel(plugin.sftp(),window);
            // Without an SSH agent or Vault here, each host's connection attempt fails its own way.
            var first=RemoteHost.create("<html>first","first.invalid",22,"user",new Auth.Vault(UUID.randomUUID()),"",Optional.empty());
            var second=RemoteHost.create("second","second.invalid",22,"user",Auth.AGENT,"",Optional.empty());
            plugin.store().put(first);plugin.store().put(second);
            RemotePluginTest.settle(host);
            assertThat(SftpTestAccess.sessions(sidebar)).as("literal names, as the store lists them").containsExactlyInAnyOrder("<html>first","second");
            // Read in the click's own EDT turn: the folder worker reports the attempt's outcome in a later one.
            var started=Kits.value(()-> { SftpTestAccess.chooseSession(sidebar,"second");return SftpTestAccess.texts(sidebar); });
            assertThat(started).as("browsing the chosen host starts here: connecting, or already loading its folder")
                .containsAnyOf("Connecting…","Loading folder…");
            Kits.await(host,()->Kits.texts(sidebar.view()).contains("SSH agent not available"),"the connection attempt for the chosen host");
            assertThat(Kits.value(()->host.windowView("dev.jasper.remote",CHOOSER))).as("no chooser dialog").isEmpty();
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
}
