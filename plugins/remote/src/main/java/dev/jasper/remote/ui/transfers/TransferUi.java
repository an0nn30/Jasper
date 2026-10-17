package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * The aggregate status item and every window's transfer rows over the plugin-owned queue: its SFTP sidebar shows them,
 * or under the classic composition its Transfers panel ({@link TransfersPanels}).
 */
public final class TransferUi implements AutoCloseable {
    public static final String SHOW = "dev.jasper.remote.transfers", CANCEL = SHOW + ".cancel";
    private static final int PAGE = 50;
    private static final String GONE = "Transfer no longer exists";
    private final PluginContext context;
    private final TransferCoordinator coordinator;
    private final Executor ui;
    private final Consumer<WindowHandle> showSidebar;
    private final LongSupplier clock;
    private final Map<UUID, TransferStrip> strips = new HashMap<>();
    private final Map<UUID, TransferRequest> requests = new HashMap<>();
    private final Set<UUID> requesting = new HashSet<>(), unreadable = new HashSet<>(), clearing = new HashSet<>();
    private final TransferPresentation presentation = new TransferPresentation();
    private final JobSpeeds speeds = new JobSpeeds();
    private final FinishedJobs finished = new FinishedJobs();
    private final StatusProgress status;
    private final javax.swing.Timer timer;
    private TransferCoordinator.Snapshot latest;
    private boolean pending;
    private volatile boolean closed;

    public TransferUi(PluginContext context, TransferCoordinator coordinator, Executor ui, Consumer<WindowHandle> showSidebar) {
        this(context, coordinator, ui, showSidebar, System::nanoTime, true);
    }

    TransferUi(PluginContext context, TransferCoordinator coordinator, Executor ui, Consumer<WindowHandle> showSidebar, LongSupplier clock, boolean autoRefresh) {
        this.context = context; this.coordinator = coordinator; this.ui = ui; this.showSidebar = showSidebar; this.clock = clock;
        context.actions().register(ActionSpec.of(SHOW, "Transfers").withIcon("download")
            .withKeywords(List.of("sftp", "upload", "download", "queue")).withDefaultBindings("cmd+alt+3", "alt+3"),
            invoked -> showSidebar.accept(invoked.window()));
        context.actions().register(ActionSpec.of(CANCEL, "Cancel transfer").withIcon("close"), invoked -> {
            if (latest != null && latest.activeJobs().size() == 1) coordinator.cancel(latest.activeJobs().getFirst());
            else showSidebar.accept(invoked.window());
        });
        status = context.statusBar().addProgress(new StatusItemSpec(SHOW + ".progress", Side.RIGHT, 65));
        status.setVisible(false);
        timer = autoRefresh ? new javax.swing.Timer(200, event -> refresh()) : null;
        if (timer != null) timer.start();
        refresh();
    }

    /** The transfer rows for {@code window}'s SFTP sidebar or Transfers panel, which renders them; the same queue in every window. */
    public TransferStrip strip(WindowHandle window) {
        var strip = new TransferStrip(new TransferStrip.Actions((id, action) -> act(window, id, action), coordinator::cancel, id -> dismiss(window, id)));
        strips.put(window.id(), strip);
        refresh();
        return strip;
    }

    public void release(WindowHandle window) { strips.remove(window.id()); }

    /** Drops {@code strip}, the window's rows from {@link #strip}, unless a newer strip has taken its place. */
    public void release(WindowHandle window, TransferStrip strip) { strips.remove(window.id(), strip); }

    void refresh() {
        if (closed || pending) return;
        pending = true;
        coordinator.snapshot(0, PAGE).whenComplete((snapshot, error) -> ui.execute(() -> {
            pending = false;
            if (closed) return;
            if (error != null) {
                String text = message(error);
                status.update(new StatusProgressState("Transfers unavailable", text, text, OptionalDouble.empty(), SHOW, null));
                status.setVisible(true);
                for (var strip : strips.values()) strip.unavailable(text);
                return;
            }
            latest = snapshot;
            long now = clock.getAsLong();
            status.update(presentation.update(snapshot, now));
            var summary = snapshot.summary();
            status.setVisible(summary.runnable() > 0 || summary.paused() > 0 || summary.attention() > 0);
            for (UUID id : finished.expired(snapshot.jobs(), now)) {
                if (clearing.add(id)) coordinator.clear(id, false).whenComplete((ignored, failure) -> ui.execute(() -> clearing.remove(id)));
            }
            var inflight = new HashMap<UUID, Long>();
            for (var sample : snapshot.progress()) inflight.merge(sample.job(), sample.bytes(), Long::sum);
            var jobs = new ArrayList<>(snapshot.jobs());
            jobs.sort(Comparator.comparingLong(TransferJob::createdMillis).reversed());
            var rows = new ArrayList<TransferRows.Row>();
            var ids = new HashSet<UUID>();
            for (var job : jobs) {
                if (clearing.contains(job.id())) continue;
                ids.add(job.id());
                fetchRequest(job.id());
                long done = job.confirmedBytes() + inflight.getOrDefault(job.id(), 0L);
                if (job.totalBytes() > 0) done = Math.min(done, job.totalBytes());
                rows.add(TransferRows.row(job, Optional.ofNullable(requests.get(job.id())), done, speeds.update(job.id(), job.state(), done, now)));
            }
            speeds.retain(ids);
            requests.keySet().retainAll(ids);
            unreadable.retainAll(ids);
            for (var strip : strips.values()) strip.rows(rows);
        }));
    }

    private void fetchRequest(UUID id) {
        if (requests.containsKey(id) || unreadable.contains(id) || !requesting.add(id)) return;
        coordinator.request(id).whenComplete((request, error) -> ui.execute(() -> {
            requesting.remove(id);
            if (closed) return;
            if (request != null) requests.put(id, request);
            else unreadable.add(id); // the row keeps its fallback title
        }));
    }

    private void act(WindowHandle window, UUID id, TransferRows.Action action) {
        switch (action) {
            case RESUME -> report("Could not resume the transfer", coordinator.resume(id, window));
            case RETRY_FAILED -> report("Could not retry the failed items", coordinator.retryFailed(id, window));
            case RETRY_CLEANUP -> report("Could not clean up the transfer", coordinator.cleanup(id, window));
            case RESOLVE -> resolve(window, id);
        }
    }

    private void report(String title, CompletableFuture<?> operation) {
        operation.whenComplete((ignored, error) -> ui.execute(() -> {
            if (closed) return;
            if (error != null) context.notifications().group().error(title, message(error), error);
            refresh();
        }));
    }

    /** Clears a finished transfer; one that is already gone (say, the fade got there first) counts as cleared. */
    private void clear(UUID id, boolean acknowledgeCleanup) {
        if (!clearing.add(id)) return;
        coordinator.clear(id, acknowledgeCleanup).whenComplete((ignored, error) -> ui.execute(() -> {
            clearing.remove(id);
            if (closed) return;
            if (error != null && !GONE.equals(message(error)))
                context.notifications().group().error("Could not clear the transfer", message(error), error);
            refresh();
        }));
    }

    private void dismiss(WindowHandle window, UUID id) {
        var job = latest == null ? Optional.<TransferJob>empty() : latest.jobs().stream().filter(candidate -> candidate.id().equals(id)).findFirst();
        if (job.isEmpty()) return;
        if (job.get().cleanupPending() == 0) { clear(id, false); return; }
        var dialog = context.windows().dialog(new DialogSpec("Clear transfer history", window, false));
        var confirm = new dev.jasper.remote.ui.ConfirmPanel(context.kit(), "Unfinished cleanup will be forgotten. Partial files may remain. Clear this history?", "Clear history",
            () -> { dialog.close(); clear(id, true); }, dialog::close);
        dialog.setContent(confirm.view());
        dialog.onClosed(confirm::close);
        dialog.show();
    }

    private void resolve(WindowHandle window, UUID id) {
        coordinator.firstAttention(id).whenComplete((entry, error) -> ui.execute(() -> {
            if (closed) return;
            if (error != null) { context.notifications().group().error("Could not resolve the transfer", message(error), error); return; }
            if (entry.isEmpty()) { report("Could not resume the transfer", coordinator.resume(id, window)); return; }
            var target = entry.get();
            var dialog = context.windows().dialog(new DialogSpec("Resolve transfer", window, false));
            var panel = new ResolvePanel(context.kit(), target, choice -> {
                dialog.close();
                if (choice.decision() == ConflictDecision.RENAME) rename(window, target);
                else resolved(window, target, choice.decision(), null, choice.remaining());
            }, () -> { dialog.close(); report("Could not restart the transfer", coordinator.restart(target.id(), window)); }, dialog::close);
            dialog.setContent(panel.view());
            dialog.onClosed(panel::close);
            dialog.show();
        }));
    }

    private void rename(WindowHandle window, TransferEntry entry) {
        var dialog = context.windows().dialog(new DialogSpec("Rename destination", window, false));
        var panel = new RenamePanel(context.kit(), entry.sourceInfo().name(), name -> { dialog.close(); resolved(window, entry, ConflictDecision.RENAME, name, false); }, dialog::close);
        dialog.setContent(panel.view());
        dialog.onClosed(panel::close);
        dialog.show();
    }

    private void resolved(WindowHandle window, TransferEntry entry, ConflictDecision decision, String name, boolean remaining) {
        coordinator.resolve(entry.id(), decision, name, remaining).whenComplete((ignored, error) -> ui.execute(() -> {
            if (closed) return;
            if (error != null) { context.notifications().group().error("Could not resolve the conflict", message(error), error); return; }
            report("Could not resume the transfer", coordinator.resume(entry.jobId(), window));
        }));
    }

    private static String message(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? "Transfer operation failed" : error.getMessage();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (timer != null) timer.stop();
        status.close();
        strips.clear();
    }
}
