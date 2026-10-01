package dev.jasper.fileserver;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/** The plugin's UI thread in tests: tasks queue from any thread and run when the test drains them. */
public final class TestUi implements Executor {
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    @Override public void execute(Runnable task) { queue.add(task); }

    /** Tasks waiting to run. */
    public int pending() { return queue.size(); }

    public void drain() {
        for (Runnable task; (task = queue.poll()) != null; ) task.run();
    }
}
