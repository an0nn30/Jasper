package dev.jasper.remote.ui.transfers;

import static org.assertj.core.api.Assertions.*;
import static dev.jasper.remote.testsupport.Kits.find;

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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
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
                var strip = transfers.strip(context.terminals().window(window).orElseThrow());
                var id = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), dest.toString()), null).get(5, TimeUnit.SECONDS);
                awaitView(transfers, strip, id, row -> row.status().equals("Done") && TransferStrip.fullTitle(row).equals("↑ report.txt → " + dest));
                assertThat(strip.visible()).isTrue();
                assertThat(find(strip.node().orElseThrow(), TransferStrip.closeId(id))).as("the row shows in the sidebar").isPresent();
                now[0] += FinishedJobs.FADE_NANOS;
                awaitGone(transfers, strip, id);
                assertThat(strip.visible()).isFalse();
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

    @Test void dismissClearsAFinishedTransferAndAsksFirstWhenCleanupIsPending() throws Exception {
        root = root.toRealPath();
        Path source = Files.writeString(root.resolve("report.txt"), "payload"), dest = Files.createDirectory(root.resolve("dest"));
        UUID dirty;
        try (var store = new dev.jasper.remote.transfer.store.TransferStore(root.resolve("queue"))) {
            dirty = store.create(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), dest.toString()));
            store.discover(dirty, List.of(new dev.jasper.remote.transfer.store.TransferStore.Discovered("report.txt", source.toString(), dest.resolve("report.txt").toString(),
                new dev.jasper.remote.sftp.FileEntry("report.txt", dev.jasper.remote.sftp.FileEntry.Kind.FILE, 7, 1000, 0644, "", "file-1"))));
            store.planTemporary(store.entries(dirty, 0, 1).getFirst().id(), dest.resolve(".jasper-partial").toString());
            store.intent(dirty, TransferJob.Intent.CANCEL);
        }
        try (var host = new FakePluginHost(root.resolve("home")); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var context = host.start(INFO, Set.of(), Set.of(), ignored -> { });
            var coordinator = new TransferCoordinator(root.resolve("queue"), executor, Runnable::run, (ref, owner) -> CompletableFuture.completedFuture(new LocalEndpoint()), () -> 2);
            TransferUi transfers = null;
            try {
                transfers = new TransferUi(context, coordinator, Runnable::run, window -> { }, () -> 0L, false);
                UUID window = host.addTerminalWindow();
                var strip = transfers.strip(context.terminals().window(window).orElseThrow());
                var clean = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), dest.toString()), null).get(5, TimeUnit.SECONDS);
                awaitView(transfers, strip, clean, row -> row.status().equals("Done"));
                coordinator.clear(clean, false).get(5, TimeUnit.SECONDS); // the fade gets there first
                click(strip, TransferStrip.closeId(clean));
                awaitGone(transfers, strip, clean);
                assertThat(host.notifications().lines()).as("an already cleared job is not an error").isEmpty();
                var again = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), root.resolve("dest").toString()).withExisting(ConflictDecision.REPLACE), null).get(5, TimeUnit.SECONDS);
                awaitView(transfers, strip, again, row -> row.status().equals("Done"));
                click(strip, TransferStrip.closeId(again));
                awaitGone(transfers, strip, again);
                assertThat(coordinator.snapshot(0, 50).get(5, TimeUnit.SECONDS).jobs()).as("dismiss cleared it").extracting(TransferJob::id).containsExactly(dirty);
                assertThat(Files.readString(dest.resolve("report.txt"))).as("destination kept").isEqualTo("payload");

                awaitView(transfers, strip, dirty, row -> row.status().equals("Cancelled") && row.action().map(TransferRows.Action::label).orElse("").equals("Retry cleanup"));
                assertThat(button(strip, TransferStrip.actionId(dirty)).label()).isEqualTo("Retry cleanup");
                assertThat(button(strip, TransferStrip.closeId(dirty)).state().enabled()).as("a cancelled transfer can be dismissed").isTrue();
                // The confirmation is a kit view (Task 8a), which lives on the EDT.
                dev.jasper.remote.testsupport.Kits.edt(() -> click(strip, TransferStrip.closeId(dirty)));
                var confirm = dev.jasper.remote.testsupport.Kits.value(() -> host.windowView("dev.jasper.remote", "Clear transfer history").orElseThrow());
                assertThat(dev.jasper.remote.testsupport.Kits.<java.util.List<dev.jasper.sdk.ui.Nodes.Button>>value(() -> dev.jasper.remote.testsupport.Kits.primaries(confirm)))
                    .as("Enter clears nothing").isEmpty();
                assertThat(coordinator.job(dirty).get(5, TimeUnit.SECONDS).cleanupPending()).as("nothing cleared before confirming").isEqualTo(1);
                dev.jasper.remote.testsupport.Kits.edt(() -> dev.jasper.remote.testsupport.Kits.click(confirm, dev.jasper.remote.ui.ConfirmPanel.CONFIRM));
                awaitGone(transfers, strip, dirty);
                assertThat(host.windowContent("dev.jasper.remote", "Clear transfer history")).isEmpty();
                assertThat(coordinator.snapshot(0, 50).get(5, TimeUnit.SECONDS).jobs()).isEmpty();
                assertThat(host.failures()).isEmpty();
                assertThat(host.notifications().lines()).isEmpty();
            } finally {
                if (transfers != null) transfers.close();
                coordinator.close();
                coordinator.stopped().get(5, TimeUnit.SECONDS);
            }
        }
    }

    /** Its failing item is a FIFO, which Windows does not have (Git Bash's mkfifo succeeds without making one). */
    @Test @DisabledOnOs(OS.WINDOWS) void theActionButtonResumesAndRetriesFailedItems() throws Exception {
        root = root.toRealPath();
        Path source = Files.writeString(root.resolve("report.txt"), "payload"), dest = Files.createDirectory(root.resolve("dest")), pipe = root.resolve("pipe");
        assertThat(new ProcessBuilder("mkfifo", pipe.toString()).inheritIO().start().waitFor()).isZero();
        var hold = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var host = new FakePluginHost(root.resolve("home")); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var context = host.start(INFO, Set.of(), Set.of(), ignored -> { });
            var coordinator = new TransferCoordinator(root.resolve("queue"), executor, Runnable::run,
                (ref, owner) -> hold.get() ? new CompletableFuture<>() : CompletableFuture.completedFuture(new LocalEndpoint()), () -> 2);
            TransferUi transfers = null;
            try {
                transfers = new TransferUi(context, coordinator, Runnable::run, window -> { }, () -> 0L, false);
                UUID window = host.addTerminalWindow();
                var strip = transfers.strip(context.terminals().window(window).orElseThrow());
                var paused = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), dest.toString()), null).get(5, TimeUnit.SECONDS);
                coordinator.pause(paused);
                awaitView(transfers, strip, paused, row -> row.status().equals("Paused") && row.action().isPresent());
                hold.set(false);
                assertThat(button(strip, TransferStrip.actionId(paused)).label()).isEqualTo("Resume");
                click(strip, TransferStrip.actionId(paused));
                awaitView(transfers, strip, paused, row -> row.status().equals("Done"));
                assertThat(Files.readString(dest.resolve("report.txt"))).isEqualTo("payload");

                var failed = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(pipe.toString()), EndpointRef.local(), dest.toString()), null).get(5, TimeUnit.SECONDS);
                awaitView(transfers, strip, failed, row -> row.status().equals("Done · 1 failed") && row.action().isPresent());
                assertThat(button(strip, TransferStrip.actionId(failed)).label()).isEqualTo("Retry failed");
                Files.delete(pipe);
                Files.writeString(pipe, "fixed");
                click(strip, TransferStrip.actionId(failed));
                awaitView(transfers, strip, failed, row -> row.status().equals("Done"));
                assertThat(Files.readString(dest.resolve("pipe"))).isEqualTo("fixed");
                assertThat(host.failures()).isEmpty();
                assertThat(host.notifications().lines()).isEmpty();
            } finally {
                if (transfers != null) transfers.close();
                coordinator.close();
                coordinator.stopped().get(5, TimeUnit.SECONDS);
            }
        }
    }

    /** The row's button as the kit shows it. */
    static dev.jasper.sdk.ui.Nodes.Button button(TransferStrip strip, String id) {
        return (dev.jasper.sdk.ui.Nodes.Button) find(strip.node().orElseThrow(), id).orElseThrow(() -> new AssertionError("no " + id));
    }

    /** A click on the row's enabled button: the event the kit reports. */
    static void click(TransferStrip strip, String id) {
        var button = button(strip, id);
        if (!button.state().enabled()) throw new AssertionError(id + " is disabled");
        strip.handle(new dev.jasper.sdk.ui.UiEvent.Action(button.action()));
    }

    static void awaitView(TransferUi transfers, TransferStrip strip, UUID id, Predicate<TransferRows.Row> done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            transfers.refresh();
            var row = strip.row(id);
            if (row.isPresent() && done.test(row.get())) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Row not reached: " + strip.row(id).map(r -> TransferStrip.fullTitle(r) + " | " + r.status()).orElse("absent"));
    }

    static void awaitGone(TransferUi transfers, TransferStrip strip, UUID id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            transfers.refresh();
            if (strip.row(id).isEmpty()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Row did not fade");
    }
}
