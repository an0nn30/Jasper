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
                var strip = (TransferStrip) transfers.strip(context.terminals().window(window).orElseThrow());
                var clean = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), dest.toString()), null).get(5, TimeUnit.SECONDS);
                awaitView(transfers, strip, clean, view -> view.statusText().equals("Done"));
                coordinator.clear(clean, false).get(5, TimeUnit.SECONDS); // the fade gets there first
                strip.view(clean).orElseThrow().closeButton().doClick();
                awaitGone(transfers, strip, clean);
                assertThat(host.notices()).as("an already cleared job is not an error").isEmpty();
                var again = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), root.resolve("dest").toString()).withExisting(ConflictDecision.REPLACE), null).get(5, TimeUnit.SECONDS);
                awaitView(transfers, strip, again, view -> view.statusText().equals("Done"));
                strip.view(again).orElseThrow().closeButton().doClick();
                awaitGone(transfers, strip, again);
                assertThat(coordinator.snapshot(0, 50).get(5, TimeUnit.SECONDS).jobs()).as("dismiss cleared it").extracting(TransferJob::id).containsExactly(dirty);
                assertThat(Files.readString(dest.resolve("report.txt"))).as("destination kept").isEqualTo("payload");

                awaitView(transfers, strip, dirty, view -> view.statusText().equals("Cancelled") && view.actionButton().getText().equals("Retry cleanup"));
                var close = strip.view(dirty).orElseThrow().closeButton();
                assertThat(close.isEnabled()).as("a cancelled transfer can be dismissed").isTrue();
                close.doClick();
                var confirm = (dev.jasper.remote.ui.ConfirmPanel) host.windowContent("dev.jasper.remote", "Clear transfer history").orElseThrow();
                assertThat(coordinator.job(dirty).get(5, TimeUnit.SECONDS).cleanupPending()).as("nothing cleared before confirming").isEqualTo(1);
                confirm.confirm.doClick();
                awaitGone(transfers, strip, dirty);
                assertThat(host.windowContent("dev.jasper.remote", "Clear transfer history")).isEmpty();
                assertThat(coordinator.snapshot(0, 50).get(5, TimeUnit.SECONDS).jobs()).isEmpty();
                assertThat(host.failures()).isEmpty();
                assertThat(host.notices()).isEmpty();
            } finally {
                if (transfers != null) transfers.close();
                coordinator.close();
                coordinator.stopped().get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test void theActionButtonResumesAndRetriesFailedItems() throws Exception {
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
                var strip = (TransferStrip) transfers.strip(context.terminals().window(window).orElseThrow());
                var paused = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), dest.toString()), null).get(5, TimeUnit.SECONDS);
                coordinator.pause(paused);
                awaitView(transfers, strip, paused, view -> view.statusText().equals("Paused") && view.actionButton().isVisible());
                hold.set(false);
                assertThat(strip.view(paused).orElseThrow().actionButton().getText()).isEqualTo("Resume");
                strip.view(paused).orElseThrow().actionButton().doClick();
                awaitView(transfers, strip, paused, view -> view.statusText().equals("Done"));
                assertThat(Files.readString(dest.resolve("report.txt"))).isEqualTo("payload");

                var failed = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(pipe.toString()), EndpointRef.local(), dest.toString()), null).get(5, TimeUnit.SECONDS);
                awaitView(transfers, strip, failed, view -> view.statusText().equals("Done · 1 failed") && view.actionButton().isVisible());
                assertThat(strip.view(failed).orElseThrow().actionButton().getText()).isEqualTo("Retry failed");
                Files.delete(pipe);
                Files.writeString(pipe, "fixed");
                strip.view(failed).orElseThrow().actionButton().doClick();
                awaitView(transfers, strip, failed, view -> view.statusText().equals("Done"));
                assertThat(Files.readString(dest.resolve("pipe"))).isEqualTo("fixed");
                assertThat(host.failures()).isEmpty();
                assertThat(host.notices()).isEmpty();
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
