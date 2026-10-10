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
        for (Runnable task; (task = queue.poll()) != null; ) {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) task.run();
            else {
                try { javax.swing.SwingUtilities.invokeAndWait(task); }
                catch (java.lang.reflect.InvocationTargetException failure) {
                    throw new AssertionError("UI task failed", failure.getCause());
                }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            }
        }
    }
}
