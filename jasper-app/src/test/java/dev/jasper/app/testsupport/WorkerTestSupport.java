package dev.jasper.app.testsupport;

import java.util.List;


/** Shared headless test fixture; never shipped. */
public final class WorkerTestSupport {
    public static java.util.concurrent.ExecutorService inlineWorker() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        };
    }
}
