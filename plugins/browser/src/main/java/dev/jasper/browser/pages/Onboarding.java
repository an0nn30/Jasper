package dev.jasper.browser.pages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whether the user has been through the onboarding pages ({@link OnboardingPages}): one marker file, {@code
 * onboarding-finished}, in the plugin's data folder. Chromium's IO thread asks {@link #finished} for each request of the
 * welcome page, so the answer comes from memory; the marker is read by {@link #load} and written by {@link #finish} on the
 * executor given, never on the asking thread. Until the read has run the onboarding counts as not finished, which shows
 * the intro once more, and a run that finishes it counts as finished at once, whether or not the marker could be written.
 */
public final class Onboarding {
    private static final System.Logger LOG = System.getLogger(Onboarding.class.getName());
    private final Path marker;
    private final Executor background;
    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile boolean finished;

    /**
     * An onboarding that keeps its marker at {@code marker}. It reads nothing until {@link #load}.
     *
     * @param marker the marker file, whose folder need not exist yet
     * @param background where the marker is read and written: the plugin's background executor
     */
    public Onboarding(Path marker, Executor background) {
        this.marker = Objects.requireNonNull(marker, "marker");
        this.background = Objects.requireNonNull(background, "background");
    }

    /**
     * Whether the onboarding is finished, as far as this run knows. Any thread; never reads a file.
     *
     * @return true after {@link #finish}, or after {@link #load} has found the marker
     */
    public boolean finished() {
        return finished;
    }

    /** Has the executor look for the marker, the first time it is called. Any thread but Chromium's IO thread; never blocks. */
    public void load() {
        if (loading.compareAndSet(false, true)) run("read", () -> {
            if (Files.exists(marker)) finished = true;
        });
    }

    /** Finishes the onboarding now, and has the executor write the marker. A write that fails is logged. */
    public void finish() {
        finished = true;
        run("write", () -> {
            try {
                Files.createDirectories(marker.toAbsolutePath().getParent());
                Files.writeString(marker, "");
            } catch (IOException | RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "The onboarding's marker could not be written: " + failure, failure);
            }
        });
    }

    private void run(String what, Runnable task) {
        try {
            background.execute(task);
        } catch (RejectedExecutionException closed) {
            LOG.log(System.Logger.Level.DEBUG, "The onboarding's marker was not asked to " + what + ": the plugin is closing", closed);
        }
    }
}
