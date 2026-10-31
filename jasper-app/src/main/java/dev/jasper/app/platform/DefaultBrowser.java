package dev.jasper.app.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Whether Jasper is the system's default web browser: the application that opens {@code https} links.
 * <ul>
 *   <li><b>macOS:</b> Launch Services opens them with the bundle {@code dev.jasper.app}.</li>
 *   <li><b>Windows:</b> the user's choice for them is {@code JasperURL}. The choice is the {@code ProgId} value of
 *       {@code HKEY_CURRENT_USER\Software\Microsoft\Windows\Shell\Associations\UrlAssociations\https\UserChoiceLatest},
 *       else of its {@code ProgId} subkey, else of {@code ...\https\UserChoice}: the first found decides.</li>
 *   <li><b>Linux:</b> {@code xdg-settings get default-web-browser} names the package's desktop entry,
 *       {@code jasper-workstation-Jasper.desktop}.</li>
 * </ul>
 * The answer is kept from the last check that answered, so asking never waits; {@link #refresh} checks again on the
 * worker. A check that fails or takes too long gives no answer and keeps the last one; any other system answers no.
 * {@link #onChanged} hears each answer that changes the value, on the EDT.
 */
public final class DefaultBrowser {
    private static final System.Logger LOG = System.getLogger(DefaultBrowser.class.getName());
    private static final String SCHEME = "https";
    private static final String BUNDLE_ID = "dev.jasper.app";
    /** The user's choices for {@code https} links live under this key. */
    private static final String HTTPS = "Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\https";
    private static final String PROG_ID = "JasperURL";
    private static final String DESKTOP_ID = "jasper-workstation-Jasper.desktop";
    private static final List<String> XDG_SETTINGS = List.of("xdg-settings", "get", "default-web-browser");
    /** How long the Linux tool may take. */
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    /** The shortest time between two checks. */
    private static final Duration INTERVAL = Duration.ofSeconds(30);
    /** How long {@link #firstAnswer} waits for an answer before it says no. */
    private static final Duration FIRST_ANSWER_WITHIN = Duration.ofSeconds(3);

    private final Executor worker;
    private final Clock clock;
    private final Supplier<Optional<Boolean>> check;
    private final CompletableFuture<Boolean> firstAnswer = new CompletableFuture<>();
    private volatile boolean jasper;
    /** Hears each change of {@link #jasper}, on the EDT. */
    private volatile Consumer<Boolean> changed = value -> { };
    /** When the last check was handed to the worker; null before the first. Guarded by this. */
    private Instant checked;
    /** Whether a check has been handed to the worker and has not finished. Guarded by this. */
    private boolean checking;

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
     * @param check whether Jasper is the default, or empty when it could not tell; one that throws or returns null
     *     could not tell either
     */
    public DefaultBrowser(Executor worker, Clock clock, Supplier<Optional<Boolean>> check) {
        this(worker, clock, check, FIRST_ANSWER_WITHIN);
    }

    DefaultBrowser(Executor worker, Clock clock, Supplier<Optional<Boolean>> check, Duration firstAnswerWithin) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.check = Objects.requireNonNull(check, "check");
        firstAnswer.completeOnTimeout(false, firstAnswerWithin.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * The last answer, at once. Any thread.
     *
     * @return false before the first check has answered; a check without an answer leaves it as it was
     */
    public boolean isJasper() { return jasper; }

    /**
     * The first answer, for a decision that must not take the "no" {@link #isJasper} gives before any check has
     * answered, such as where a link from a cold launch goes. It completes with that answer, or with false 3 seconds
     * after this object was made if no check has answered by then, so nothing waits on it for ever. Completing the
     * returned future changes nothing here. Any thread; never wait on it on the EDT.
     *
     * @return a future of the first answer
     */
    public CompletableFuture<Boolean> firstAnswer() { return firstAnswer.copy(); }

    /**
     * Tells {@code listener}, on the EDT, each answer that changes {@link #isJasper}, in the order the answers came; an
     * answer equal to the last tells it nothing, and so does a check without an answer. One listener at a time: a later
     * call replaces it. Any thread.
     *
     * @param listener hears the new answer
     */
    public void onChanged(Consumer<Boolean> listener) { changed = Objects.requireNonNull(listener, "listener"); }

    /**
     * Checks again on the worker, unless a check is still running or the last one began less than 30 seconds ago.
     * Returns at once; any thread. Called at start-up and whenever a Jasper window activates, which is when the user
     * may have changed the setting.
     */
    public void refresh() {
        synchronized (this) {
            Instant now = clock.instant();
            if (checking) return;
            // A clock set back leaves the last check in the future: that never holds the next one back.
            if (checked != null && !now.isBefore(checked) && now.isBefore(checked.plus(INTERVAL))) return;
            checked = now;
            checking = true;
        }
        try {
            worker.execute(this::check);
        } catch (RejectedExecutionException closed) {
            finished();
            LOG.log(System.Logger.Level.DEBUG, "The default browser check was not run", closed);
        }
    }

    private void check() {
        try {
            Optional<Boolean> answer;
            try {
                answer = Objects.requireNonNull(check.get(), "answer");
            } catch (RuntimeException | LinkageError failure) {
                LOG.log(System.Logger.Level.DEBUG, "Could not tell whether Jasper is the default browser", failure);
                answer = Optional.empty();
            }
            answer.ifPresent(value -> {
                // One check runs at a time, so nothing else writes the answer meanwhile.
                boolean was = jasper;
                jasper = value;
                firstAnswer.complete(value);
                if (was != value) SwingUtilities.invokeLater(() -> changed.accept(value));
            });
        } finally {
            finished();
        }
    }

    private synchronized void finished() { checking = false; }

    /**
     * Asks the system named {@code osName} on the calling thread, which may wait up to the Linux tool's time-out.
     *
     * @return the answer; empty when the system could not tell (a bridge that cannot load, a registry read that fails,
     *     a Linux tool that is missing, fails or is late); any other system answers no
     */
    static Optional<Boolean> ask(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        try {
            // Outside a packaged app there is no bridge, and Launch Services opens https links with no Jasper.
            if (os.startsWith("mac")) return Optional.of(parseMac(MacLaunchServices.defaultHandler(SCHEME)));
            if (os.startsWith("windows")) return askWindows(HTTPS, DefaultBrowser::readRegistry);
            if (os.startsWith("linux")) return askLinux(XDG_SETTINGS, TIMEOUT);
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(System.Logger.Level.DEBUG, "Could not ask the system for its default browser", failure);
            return Optional.empty();
        }
        return Optional.of(false);
    }

    /**
     * Windows: the user's choice for the links whose associations live under {@code https}, in {@code HKEY_CURRENT_USER}.
     *
     * @param https the key holding the choices, {@code ...\UrlAssociations\https} outside tests
     * @param registry reads a string value, empty when the key or value is missing, and throws
     *     {@link UncheckedIOException} when the read fails
     * @return the first {@code ProgId} found compared with Jasper's; no if none is found (no choice was made); empty when
     *     a read fails
     */
    static Optional<Boolean> askWindows(String https, BiFunction<String, String, Optional<String>> registry) {
        try {
            for (String key : List.of(https + "\\UserChoiceLatest", https + "\\UserChoiceLatest\\ProgId", https + "\\UserChoice")) {
                Optional<String> progId = registry.apply(key, "ProgId");
                if (progId.isPresent()) return Optional.of(parseWindows(progId.get()));
            }
            return Optional.of(false);
        } catch (UncheckedIOException failure) {
            LOG.log(System.Logger.Level.DEBUG, "Could not read the user's choice of browser", failure);
            return Optional.empty();
        }
    }

    /** A string value under {@code HKEY_CURRENT_USER}, for {@link #askWindows}; Windows only, elsewhere it throws. */
    static Optional<String> readRegistry(String key, String name) {
        try {
            return WindowsUserPath.read(key, name);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Linux: what {@code command} prints, given at most {@code timeout}.
     *
     * @return its output compared with Jasper's desktop entry when it exits with 0; empty when it is missing, exits
     *     otherwise or is late
     */
    static Optional<Boolean> askLinux(List<String> command, Duration timeout) {
        return run(command, timeout).map(DefaultBrowser::parseLinux);
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
     * @param progId the chosen {@code ProgId}, or null; ProgIds ignore case
     */
    static boolean parseWindows(String progId) { return PROG_ID.equalsIgnoreCase(progId); }

    /**
     * Linux: whether {@code xdg-settings} names the package's desktop entry.
     *
     * @param output what it printed, or null; desktop file ids keep their case
     */
    static boolean parseLinux(String output) { return output != null && DESKTOP_ID.equals(output.strip()); }

    /**
     * Runs {@code command} with no input and no shell, its error output discarded.
     *
     * @return its output when it exits with 0 within {@code timeout}; empty when it cannot start, exits with another
     *     code, or is still running at the time-out, when it and every process it started are ended
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
            // xdg-settings is a shell script: what it started goes too. Once it has exited, its children belong to init
            // and are no longer its descendants, and a reused process id could be another program's.
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
