package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Whether Jasper is the system's default web browser: the application that opens {@code https} links.
 * <ul>
 *   <li><b>macOS:</b> Launch Services opens them with the bundle {@code dev.jasper.app}.</li>
 *   <li><b>Windows:</b> the user's choice for them, {@code ProgId} under
 *       {@code HKEY_CURRENT_USER\Software\Microsoft\Windows\Shell\Associations\UrlAssociations\https\UserChoice}, is
 *       {@code JasperURL}.</li>
 *   <li><b>Linux:</b> {@code xdg-settings get default-web-browser} names the package's desktop entry,
 *       {@code jasper-workstation-Jasper.desktop}, within 2 seconds.</li>
 * </ul>
 * The answer is kept from the last check, so asking never waits; {@link #refresh} checks again on the worker. A check
 * that fails or takes too long answers no, and so does any other system.
 */
public final class DefaultBrowser {
    private static final System.Logger LOG = System.getLogger(DefaultBrowser.class.getName());
    private static final String SCHEME = "https";
    private static final String BUNDLE_ID = "dev.jasper.app";
    private static final String USER_CHOICE = "Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\https\\UserChoice";
    private static final String PROG_ID = "JasperURL";
    private static final String DESKTOP_ID = "jasper-workstation-Jasper.desktop";
    private static final List<String> XDG_SETTINGS = List.of("xdg-settings", "get", "default-web-browser");
    /** How long the Linux tool may take. */
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    /** The shortest time between two checks. */
    private static final Duration INTERVAL = Duration.ofSeconds(30);

    private final Executor worker;
    private final Clock clock;
    private final BooleanSupplier check;
    private volatile boolean jasper;
    /** When the last check was handed to the worker; null before the first. Guarded by this. */
    private Instant checked;

    /**
     * Asks this system.
     *
     * @param worker where each check runs, never the caller's thread: a check may take up to its time-out
     * @param clock what {@link #refresh} measures the time since the last check with
     */
    public DefaultBrowser(Executor worker, Clock clock) {
        this(worker, clock, () -> ask(System.getProperty("os.name")));
    }

    /**
     * Asks {@code check} instead of the system, so tests never reach the operating system.
     *
     * @param worker where each check runs
     * @param clock what {@link #refresh} measures the time since the last check with
     * @param check whether Jasper is the default; a check that throws answers no
     */
    public DefaultBrowser(Executor worker, Clock clock, BooleanSupplier check) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.check = Objects.requireNonNull(check, "check");
    }

    /**
     * The last check's answer, at once. Any thread.
     *
     * @return false before the first check has answered, and after one that failed or timed out
     */
    public boolean isJasper() { return jasper; }

    /**
     * Checks again on the worker, unless the last check began less than 30 seconds ago. Returns at once; any thread.
     * Called at start-up and whenever a Jasper window activates, which is when the user may have changed the setting.
     */
    public void refresh() {
        synchronized (this) {
            Instant now = clock.instant();
            // A clock set back leaves the last check in the future: that never holds the next one back.
            if (checked != null && !now.isBefore(checked) && now.isBefore(checked.plus(INTERVAL))) return;
            checked = now;
        }
        try {
            worker.execute(this::check);
        } catch (RejectedExecutionException closed) {
            LOG.log(System.Logger.Level.DEBUG, "The default browser check was not run", closed);
        }
    }

    private void check() {
        boolean answer;
        try {
            answer = check.getAsBoolean();
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(System.Logger.Level.DEBUG, "Could not tell whether Jasper is the default browser", failure);
            answer = false;
        }
        jasper = answer;
    }

    /**
     * Asks the system named {@code osName} on the calling thread, which may wait up to the Linux tool's time-out.
     *
     * @return false on any error, on a time-out, and on any other system
     */
    static boolean ask(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        try {
            if (os.startsWith("mac")) return parseMac(MacLaunchServices.defaultHandler(SCHEME));
            if (os.startsWith("windows")) return askWindows(USER_CHOICE);
            if (os.startsWith("linux")) return askLinux(XDG_SETTINGS, TIMEOUT);
        } catch (IOException | RuntimeException | LinkageError failure) {
            LOG.log(System.Logger.Level.DEBUG, "Could not ask the system for its default browser", failure);
        }
        return false;
    }

    /** Windows: the {@code ProgId} under {@code key}, in {@code HKEY_CURRENT_USER}; a missing key or value answers no. */
    static boolean askWindows(String key) throws IOException {
        return parseWindows(WindowsUserPath.read(key, "ProgId").orElse(null));
    }

    /** Linux: what {@code command} prints, given at most {@code timeout}; a tool that is missing, fails or is late answers no. */
    static boolean askLinux(List<String> command, Duration timeout) {
        return parseLinux(run(command, timeout).orElse(null));
    }

    /**
     * macOS: whether Launch Services opens {@code https} with Jasper's bundle.
     *
     * @param bundleId the opening application's bundle identifier, or null for none; identifiers ignore case
     */
    static boolean parseMac(String bundleId) { return BUNDLE_ID.equalsIgnoreCase(bundleId); }

    /**
     * Windows: whether the user's choice for {@code https} is Jasper's.
     *
     * @param progId the {@code UserChoice} key's {@code ProgId}, or null when the key or value is missing; ProgIds
     *     ignore case
     */
    static boolean parseWindows(String progId) { return PROG_ID.equalsIgnoreCase(progId); }

    /**
     * Linux: whether {@code xdg-settings} names the package's desktop entry.
     *
     * @param output what it printed, or null when it gave no answer; desktop file ids keep their case
     */
    static boolean parseLinux(String output) { return output != null && DESKTOP_ID.equals(output.strip()); }

    /**
     * Runs {@code command} with no input and no shell, its error output discarded.
     *
     * @return its output when it exits with 0 within {@code timeout}; empty when it cannot start, exits with another
     *     code, or is still running at the time-out, when it is ended
     */
    static Optional<String> run(List<String> command, Duration timeout) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException | RuntimeException missing) {
            return Optional.empty();
        }
        try {
            process.getOutputStream().close();
            long deadline = System.nanoTime() + timeout.toNanos();
            // Read while waiting: a child that filled its pipe would otherwise stall until the time-out.
            var output = new FutureTask<>(() -> process.getInputStream().readAllBytes());
            Thread.ofVirtual().name("jasper-default-browser-output").start(output);
            if (!process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS)) return Optional.empty();
            byte[] bytes = output.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return process.exitValue() == 0 ? Optional.of(new String(bytes, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | ExecutionException | TimeoutException failure) {
            return Optional.empty();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
