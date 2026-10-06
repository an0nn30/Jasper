package dev.jasper.remote;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.testsupport.Kits;
import dev.jasper.remote.ui.sftp.SftpTestAccess;
import dev.jasper.remote.ui.sftp.SftpUi;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Choose Session in the disconnected SFTP sidebar opens the window's host chooser, a kit form (Plan 2 Task 8c). */
class SftpChooseSessionTest {
    static final String CHOOSER="Browse files on SSH host";

    @Test void choosingSessionFromDisconnectedSidebarOpensTheExistingHostChooser(@TempDir Path root) throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            var window=host.addTerminalWindow();
            Kits.edt(()->host.openPanel(SftpUi.PANEL,window));
            var sidebar=SftpTestAccess.panel(plugin.sftp(),window);
            assertThat(host.windows()).isEmpty();
            SftpTestAccess.chooseSession(sidebar);
            var chooser=Kits.value(()->host.windowView("dev.jasper.remote",CHOOSER).orElseThrow());
            Kits.edt(()-> {
                var hosts=Kits.node(chooser,"browse-host.host",Nodes.Choice.class);
                assertThat(hosts.kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
                assertThat(hosts.options()).isEmpty();
                assertThat(Kits.button(chooser,"browse-host.browse").state().enabled()).as("no saved hosts").isFalse();
                assertThat(Kits.primaries(chooser)).as("Enter browses nothing").isEmpty();
                Kits.press(chooser,Kits.form(chooser).cancelAction());
            });
            assertThat(Kits.value(()->host.windowView("dev.jasper.remote",CHOOSER))).as("Escape closes the chooser").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    /** The chooser lists saved hosts literally with the first chosen; choosing and browsing is HostChooserTest's (no connection here). */
    @Test void theChooserListsSavedHostsLiterallyWithTheFirstChosen(@TempDir Path root) throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(Kits.UI,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            var first=RemoteHost.create("<html>first","first.invalid",22,"user",Auth.AGENT,"",Optional.empty());
            var second=RemoteHost.create("second","second.invalid",22,"user",Auth.AGENT,"",Optional.empty());
            plugin.store().put(first);plugin.store().put(second);
            RemotePluginTest.settle(host);
            var window=host.addTerminalWindow();
            Kits.edt(()->host.openPanel(SftpUi.PANEL,window));
            var sidebar=SftpTestAccess.panel(plugin.sftp(),window);
            SftpTestAccess.chooseSession(sidebar);
            var chooser=Kits.value(()->host.windowView("dev.jasper.remote",CHOOSER).orElseThrow());
            Kits.edt(()-> {
                var hosts=Kits.node(chooser,"browse-host.host",Nodes.Choice.class);
                assertThat(hosts.options()).extracting(option->option.label()).as("literal name — hostname").containsExactlyInAnyOrder("<html>first — first.invalid","second — second.invalid");
                assertThat(hosts.selectedId()).as("the first host is chosen").isEqualTo(hosts.options().getFirst().id());
                assertThat(Kits.button(chooser,"browse-host.browse").state().enabled()).isTrue();
                Kits.press(chooser,Kits.form(chooser).cancelAction());
            });
            assertThat(SftpTestAccess.texts(sidebar)).as("nothing connects").doesNotContain("Connecting…");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
}
