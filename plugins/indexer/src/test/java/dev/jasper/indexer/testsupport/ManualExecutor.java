package dev.jasper.indexer.testsupport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

/** An executor that runs nothing until the test drains it, on the test's thread. */
public final class ManualExecutor implements Executor {
    private final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override public synchronized void execute(Runnable task) { tasks.add(task); }

    /**
     * Runs queued tasks, including tasks they queue, until none are left.
     *
     * @return how many ran
     */
    public int drain() {
        int count = 0;
        for (Runnable task = poll(); task != null; task = poll()) {
            task.run();
            count++;
        }
        return count;
    }

    private synchronized Runnable poll() { return tasks.poll(); }
}
