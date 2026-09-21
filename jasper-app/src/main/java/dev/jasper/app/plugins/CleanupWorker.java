package dev.jasper.app.plugins;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The application-owned thread for plugin code that must run when nobody else can run it: cancellation handlers,
 * which usually abort blocking network work, and the closes of connections. It is not a plugin's background
 * executor, because that stops admitting tasks during shutdown while a connect may still be running, and never
 * the EDT. It never rejects: after shutdown each task gets a daemon thread of its own.
 */
final class CleanupWorker implements Executor {
    private static final System.Logger LOG = System.getLogger(CleanupWorker.class.getName());
    private final ExecutorService worker =
        Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-plugin-cleanup").factory());

    @Override public void execute(Runnable task) {
        Runnable safe = () -> {
            try { task.run(); }
            catch (RuntimeException | LinkageError failure) { LOG.log(System.Logger.Level.WARNING, "A cleanup task failed", failure); }
        };
        try { worker.execute(safe); }
        catch (RejectedExecutionException afterShutdown) { Thread.ofPlatform().daemon().name("jasper-plugin-cleanup-late").start(safe); }
    }

    /** Completes when everything queued before this call has run; joins the bounded shutdown wait. */
    CompletableFuture<Void> drained() {
        var done = new CompletableFuture<Void>();
        execute(() -> done.complete(null));
        return done;
    }

    void shutdown() { worker.shutdown(); }
}
