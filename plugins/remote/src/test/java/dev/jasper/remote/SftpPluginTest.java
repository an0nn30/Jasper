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
            var plugin=new TestRemotePlugin(Runnable::run,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            var context=host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            assertThat(host.failures()).isEmpty();assertThat(host.menu("top:dev.jasper.remote.menu")).contains("item:dev.jasper.remote.sftp","item:dev.jasper.remote.transfers");
            assertThat(host.panels()).contains("dev.jasper.remote.sftp.panel|SFTP|LEFT").noneMatch(panel->panel.startsWith("dev.jasper.remote.transfers.panel"));
            assertThat(host.actions()).noneMatch(action->action.startsWith("dev.jasper.remote.transfers.toggle"));
            assertThat(host.openRequests()).isEmpty();assertThat(host.windows()).isEmpty();
            assertThat(plugin.transfers().job(restored).get(5,TimeUnit.SECONDS).state()).isEqualTo(TransferState.PAUSED);
            host.setConfig("dev.jasper.remote",Map.of("sftp",Map.of("max_parallel_files",99L,"request_timeout_seconds",0L),"shortcuts",Map.of("toggle_sftp","cmd+alt+f","toggle_transfers","cmd+alt+t")));
            assertThat(RemoteSettings.sftp(context.config()).maxParallelFiles()).isEqualTo(8);assertThat(RemoteSettings.sftp(context.config()).requestTimeout().toSeconds()).isEqualTo(1);
            UUID window=host.addTerminalWindow(),second=host.addTerminalWindow();host.invoke("dev.jasper.remote.transfers",window,null);host.invoke("dev.jasper.remote.transfers",second,null);
            assertThat(host.openRequests()).isEmpty();assertThat(host.failures()).isEmpty();
            assertThat(host.windows()).as("showing transfers never opens the host chooser").isEmpty();
            var sidebar=host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,window);
            assertThat(find(sidebar,dev.jasper.remote.ui.transfers.TransferStrip.class)).as("the strip lives in the SFTP sidebar").isPresent();
        }
    }
    static <T> Optional<T> find(java.awt.Component component,Class<T> type) {
        if(type.isInstance(component)) return Optional.of(type.cast(component));
        if(component instanceof java.awt.Container container) for(var child:container.getComponents()) { var found=find(child,type);if(found.isPresent()) return found; }
        return Optional.empty();
    }
    @Test void cancellingResumeValidationWithdrawsPendingCredentialResolution() {
        var source=new CompletableFuture<dev.jasper.remote.client.ConnectionIdentity>();
        var validated=RemotePlugin.validateResumeIdentity(null,source);
        validated.cancel(true);
        assertThat(source.isCancelled()).isTrue();
    }

}
