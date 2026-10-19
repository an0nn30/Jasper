package dev.jasper.app.plugins;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/** Test teardown waits for workers' resource cleanup after the production shutdown's bounded interruption grace. */
public final class PluginShutdownTestSupport {
    private PluginShutdownTestSupport() { }

    /** Captures workers before stop so teardown can await their actual termination off the EDT. */
    public static Workers capture(PluginRuntime runtime) throws Exception {
        PluginHost host = (PluginHost) field(PluginRuntime.class, "host").get(runtime);
        var workers = host == null ? new Workers(List.of()) : capture(host);
        return new Workers(workers.executors(), (java.util.concurrent.CompletableFuture<?>)
            field(PluginRuntime.class, "resourcesClosed").get(runtime));
    }

    static Workers capture(PluginHost host) {
        return AppContractTest.onEdtValue(() -> {
            try {
                var contexts = (Map<?, ?>) field(PluginHost.class, "contexts").get(host);
                Field executor = field(HostedContext.class, "executor");
                var result = new java.util.ArrayList<ExecutorService>();
                for (Object context : contexts.values()) result.add((ExecutorService) executor.get(context));
                return new Workers(List.copyOf(result));
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    /** The workers owned by this fixture before its plugins stop. */
    public record Workers(List<ExecutorService> executors, java.util.concurrent.CompletableFuture<?> resourcesClosed) {
        Workers(List<ExecutorService> executors) { this(executors, java.util.concurrent.CompletableFuture.completedFuture(null)); }
        /** Waits for accepted tasks and their finally blocks before deleting fixture files. */
        public void await() throws InterruptedException {
            if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Do not wait for plugin workers on the EDT");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            for (ExecutorService executor : executors) {
                if (!executor.isShutdown()) throw new AssertionError("Stop plugins before waiting for their workers");
                if (!executor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                    throw new AssertionError("Plugin workers did not finish resource cleanup after stop");
            }
            try { resourcesClosed.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
            catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
                throw new AssertionError("Plugin resources did not close after worker termination", failure);
            }
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
