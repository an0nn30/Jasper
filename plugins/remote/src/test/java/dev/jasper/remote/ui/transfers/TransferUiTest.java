package dev.jasper.remote.ui.transfers;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferUiTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.2.0",
        Set.of(Capabilities.TERMINAL_OPEN, Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));
    @TempDir Path root;

    @Test void theStripShowsACopyLetsItFadeAndTheShowActionRevealsTheSidebar() throws Exception {
        root = root.toRealPath();
        Path source = Files.writeString(root.resolve("report.txt"), "payload"), dest = Files.createDirectory(root.resolve("dest"));
        long[] now = {0};
        var shown = new ArrayList<UUID>();
        try (var host = new FakePluginHost(root.resolve("home")); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var context = host.start(INFO, Set.of(), Set.of(), ignored -> { });
            var coordinator = new TransferCoordinator(root.resolve("queue"), executor, Runnable::run, (ref, owner) -> CompletableFuture.completedFuture(new LocalEndpoint()), () -> 2);
            TransferUi transfers = null;
            try {
                transfers = new TransferUi(context, coordinator, Runnable::run, window -> shown.add(window.id()), () -> now[0], false);
                UUID window = host.addTerminalWindow();
                var strip = (TransferStrip) transfers.strip(context.terminals().window(window).orElseThrow());
                var id = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), dest.toString()), null).get(5, TimeUnit.SECONDS);
                awaitView(transfers, strip, id, view -> view.statusText().equals("Done") && view.fullTitle().equals("↑ report.txt → " + dest));
                assertThat(strip.isVisible()).isTrue();
                now[0] += FinishedJobs.FADE_NANOS;
                awaitGone(transfers, strip, id);
                assertThat(strip.isVisible()).isFalse();
                assertThat(coordinator.snapshot(0, 50).get(5, TimeUnit.SECONDS).jobs()).as("history cleared").isEmpty();
                assertThat(Files.readString(dest.resolve("report.txt"))).as("destination kept").isEqualTo("payload");
                host.invoke(TransferUi.SHOW, window, null);
                assertThat(shown).containsExactly(window);
            } finally {
                if (transfers != null) transfers.close();
                coordinator.close();
                coordinator.stopped().get(5, TimeUnit.SECONDS);
            }
        }
    }

    static void awaitView(TransferUi transfers, TransferStrip strip, UUID id, Predicate<TransferStrip.RowView> done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            transfers.refresh();
            var view = strip.view(id);
            if (view.isPresent() && done.test(view.get())) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Row not reached: " + strip.view(id).map(v -> v.fullTitle() + " | " + v.statusText()).orElse("absent"));
    }

    static void awaitGone(TransferUi transfers, TransferStrip strip, UUID id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            transfers.refresh();
            if (strip.view(id).isEmpty()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Row did not fade");
    }
}
