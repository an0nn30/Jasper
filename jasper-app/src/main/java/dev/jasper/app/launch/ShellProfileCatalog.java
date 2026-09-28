package dev.jasper.app.launch;

import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * The shell profiles this machine offers, for new panes, the Settings dropdown and plugins. Detection of WSL
 * distributions and PowerShell 7 runs off the EDT and is cached until {@link #refresh}; everything but
 * {@link #awaitDetection}, which launches call off the EDT, is EDT only. The Custom profile always follows the saved settings. The application creates one and closes it.
 */
public final class ShellProfileCatalog implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(ShellProfileCatalog.class.getName());
    private final String osName;
    private final Map<String, String> environment;
    private final Supplier<TerminalConfig.Shell> shell;
    private final Supplier<List<WslDistro>> distros;
    private final Supplier<Optional<String>> pwsh;
    private final Executor background;
    private final List<Runnable> listeners = new ArrayList<>();
    private List<WslDistro> detectedDistros = List.of();
    private Optional<String> detectedPwsh = Optional.empty();
    private boolean complete;
    private long generation;
    private boolean closed;
    /** True once a detection completed, false once closed first; read by {@link #awaitDetection} on any thread. */
    private final CompletableFuture<Boolean> firstDetection = new CompletableFuture<>();
    private volatile boolean detecting;

    /**
     * {@code shell} reads the saved shell settings on the EDT; {@code distros} and {@code pwsh} detect on
     * {@code background}, never on the EDT.
     */
    public ShellProfileCatalog(String osName, Map<String, String> environment, Supplier<TerminalConfig.Shell> shell,
                               Supplier<List<WslDistro>> distros, Supplier<Optional<String>> pwsh, Executor background) {
        this.osName = Objects.requireNonNull(osName, "osName");
        this.environment = Map.copyOf(environment);
        this.shell = Objects.requireNonNull(shell, "shell");
        this.distros = Objects.requireNonNull(distros, "distros");
        this.pwsh = Objects.requireNonNull(pwsh, "pwsh");
        this.background = Objects.requireNonNull(background, "background");
    }

    /** This machine's catalog: {@code reg.exe}/{@code wsl.exe} and the file system, on a virtual thread. Call {@link #refresh} to detect. */
    public static ShellProfileCatalog system(Supplier<TerminalConfig.Shell> shell) {
        String os = System.getProperty("os.name");
        Map<String, String> environment = System.getenv();
        return new ShellProfileCatalog(os, environment, shell, () -> WslDistributions.detect(os),
            () -> ShellProfiles.findPwsh(os, environment, ShellProfiles::exists),
            task -> Thread.ofVirtual().name("jasper-shell-profiles").start(task));
    }

    /** The profiles now, platform default first and Custom last. */
    public List<ShellProfile> current() {
        requireEdt();
        return ShellProfiles.available(osName, environment, detectedDistros, detectedPwsh, shell.get());
    }

    /** Whether detection has finished at least once; until then WSL and PowerShell 7 ids are trusted. */
    public boolean complete() {
        requireEdt();
        return complete;
    }

    /** The profile to start for {@code requested} (empty: the configured one) under {@code configured} settings. */
    public ShellProfiles.Resolution resolve(Optional<String> requested, TerminalConfig.Shell configured) {
        requireEdt();
        return ShellProfiles.resolve(requested, ShellProfiles.available(osName, environment, detectedDistros, detectedPwsh, configured),
            complete, osName, environment, configured);
    }

    /** Detects again off the EDT; listeners hear when the result differs. A newer refresh supersedes an older one. */
    public void refresh() {
        requireEdt();
        if (closed) return;
        long ticket = ++generation;
        try {
            background.execute(() -> {
                List<WslDistro> found;
                Optional<String> shell7;
                try { found = List.copyOf(distros.get()); }
                catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "WSL detection failed", failure); found = List.of(); }
                try { shell7 = pwsh.get(); }
                catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "PowerShell 7 detection failed", failure); shell7 = Optional.empty(); }
                List<WslDistro> distributions = found;
                Optional<String> program = shell7;
                SwingUtilities.invokeLater(() -> detected(ticket, distributions, program));
            });
            detecting = true;
        } catch (RuntimeException rejected) {
            LOG.log(System.Logger.Level.WARNING, "Shell detection could not start", rejected);
        }
    }

    private void detected(long ticket, List<WslDistro> found, Optional<String> program) {
        if (closed || ticket != generation) return;
        boolean changed = !complete || !found.equals(detectedDistros) || !program.equals(detectedPwsh);
        detectedDistros = found;
        detectedPwsh = program;
        complete = true;
        firstDetection.complete(true);
        if (changed) for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    /**
     * Waits, off the EDT and at most {@code timeout}, for the first detection; true once it has completed. The EDT
     * publishes detection, so waiting there would deadlock: it throws instead. A catalog nobody has refreshed (the
     * benchmark and tests that supply their own launcher skip detection) or a closed one returns false at once,
     * because nothing would complete it; so does an interrupted wait.
     */
    public boolean awaitDetection(Duration timeout) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Waiting for shell detection would block the EDT");
        if (!detecting && !firstDetection.isDone()) return false;
        try {
            return firstDetection.get(Math.max(0, timeout.toNanos()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException slow) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException impossible) {
            return false;
        }
    }

    /** Runs {@code listener} on the EDT after each detection that changed the profiles. */
    public Subscription onChanged(Runnable listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    @Override public void close() {
        requireEdt();
        closed = true;
        firstDetection.complete(false);
        listeners.clear();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Shell profiles are EDT-confined");
    }
}
