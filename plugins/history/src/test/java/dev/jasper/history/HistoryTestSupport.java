package dev.jasper.history;

public final class HistoryTestSupport {
    public static void stopPolling(ShellHistoryIndex index) { index.pollTimer().stop(); }

    static java.util.concurrent.ExecutorService inlineWorker() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public java.util.List<Runnable> shutdownNow() { return java.util.List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        };
    }
}
