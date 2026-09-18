package dev.jasper.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** A macOS system notification, for a command that finished somewhere you were not looking. */
final class NativeNotifier implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(NativeNotifier.class.getName());

    /**
     * The command line is whatever the user ran, so it can hold quotes, backslashes and newlines.
     * Interpolating it into an AppleScript string would let a crafted command inject script, so the
     * text travels as {@code argv} and is never parsed as code.
     */
    private static final List<String> SCRIPT = List.of("/usr/bin/osascript",
        "-e", "on run argv",
        "-e", "display notification (item 1 of argv) with title (item 2 of argv)",
        "-e", "end run", "--");

    private final ExecutorService worker;
    private final boolean supported;
    private boolean warned;

    NativeNotifier() {
        this(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"));
    }

    NativeNotifier(boolean supported) {
        this.supported = supported;
        this.worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("jasper-notify").factory());
    }

    /** The exact process arguments for a notice, so a test can assert them without running anything. */
    static List<String> command(String title, String detail) {
        var arguments = new ArrayList<>(SCRIPT);
        arguments.add(detail);
        arguments.add(title);
        return List.copyOf(arguments);
    }

    /** Shows a system notification. Silent on anything that is not macOS, which the docs state. */
    void send(String title, String detail) {
        if (!supported) return;
        try {
            worker.execute(() -> run(command(title, detail)));
        } catch (RejectedExecutionException closed) {
            // Shutting down; a missed notification is not worth reporting.
        }
    }

    private void run(List<String> arguments) {
        try {
            new ProcessBuilder(arguments).redirectErrorStream(true).start();
        } catch (IOException failure) {
            // Once per session: a broken osascript would otherwise log on every long command.
            if (!warned) {
                warned = true;
                LOG.log(System.Logger.Level.WARNING, "Could not show a system notification", failure);
            }
        }
    }

    @Override public void close() { worker.shutdownNow(); }
}
