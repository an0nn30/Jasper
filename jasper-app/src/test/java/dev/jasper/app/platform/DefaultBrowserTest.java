package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.jasper.app.testsupport.MutableClock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Whether Jasper is the system's default browser: each system's answer compared on every system, the kept answer, its
 * throttle and the first answer with stand-in checks, the Windows sources with a stand-in registry, and the Linux tool's
 * failures and time-out with stand-in commands. Nothing here asks the real {@code xdg-settings}, and only a Windows run
 * reads the registry, under a scratch key.
 */
class DefaultBrowserTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String HTTPS = "Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\https";

    @Test void onMacOSLaunchServicesMustOpenHttpsWithJaspersBundle() {
        assertThat(DefaultBrowser.parseMac("dev.jasper.app")).isTrue();
        assertThat(DefaultBrowser.parseMac("Dev.Jasper.App")).as("bundle identifiers ignore case").isTrue();
        assertThat(DefaultBrowser.parseMac("com.apple.Safari")).isFalse();
        assertThat(DefaultBrowser.parseMac("dev.jasper.app.helper")).isFalse();
        assertThat(DefaultBrowser.parseMac("")).isFalse();
        assertThat(DefaultBrowser.parseMac(null)).as("no application opens https links").isFalse();
    }

    @Test void onWindowsTheUsersChoiceForHttpsMustBeJaspersProgId() {
        assertThat(DefaultBrowser.parseWindows("JasperURL")).isTrue();
        assertThat(DefaultBrowser.parseWindows("jasperurl")).as("ProgIds ignore case").isTrue();
        assertThat(DefaultBrowser.parseWindows("MSEdgeHTM")).isFalse();
        assertThat(DefaultBrowser.parseWindows("ChromeHTML")).isFalse();
        assertThat(DefaultBrowser.parseWindows("JasperURL2")).isFalse();
        assertThat(DefaultBrowser.parseWindows("")).isFalse();
        assertThat(DefaultBrowser.parseWindows(null)).isFalse();
    }

    @Test void onLinuxXdgSettingsMustNameThePackagesDesktopEntry() {
        assertThat(DefaultBrowser.parseLinux("jasper-workstation-Jasper.desktop\n")).isTrue();
        assertThat(DefaultBrowser.parseLinux("jasper-workstation-Jasper.desktop")).isTrue();
        assertThat(DefaultBrowser.parseLinux("firefox.desktop\n")).isFalse();
        assertThat(DefaultBrowser.parseLinux("Jasper.desktop\n")).isFalse();
        assertThat(DefaultBrowser.parseLinux("jasper-workstation-jasper.desktop\n")).as("desktop file ids keep their case").isFalse();
        assertThat(DefaultBrowser.parseLinux("\n")).as("no default set").isFalse();
        assertThat(DefaultBrowser.parseLinux(null)).isFalse();
    }

    /** A stand-in registry: values keyed by {@code key + "|" + name}, with every read recorded. */
    private static BiFunction<String, String, Optional<String>> registry(Map<String, String> values, List<String> reads) {
        return (key, name) -> {
            reads.add(key.substring(HTTPS.length()) + "|" + name);
            return Optional.ofNullable(values.get(key + "|" + name));
        };
    }

    @Test void onWindowsUserChoiceLatestDecidesBeforeUserChoice() {
        var reads = new ArrayList<String>();
        assertThat(DefaultBrowser.askWindows(HTTPS, registry(Map.of(HTTPS + "\\UserChoice|ProgId", "JasperURL"), reads)))
            .as("only the older key").contains(true);
        assertThat(reads).as("the newer sources first").containsExactly(
            "\\UserChoiceLatest|ProgId", "\\UserChoiceLatest\\ProgId|ProgId", "\\UserChoice|ProgId");
        reads.clear();
        assertThat(DefaultBrowser.askWindows(HTTPS, registry(Map.of(HTTPS + "\\UserChoiceLatest|ProgId", "ChromeHTML",
            HTTPS + "\\UserChoice|ProgId", "JasperURL"), reads))).as("a stale older key is ignored").contains(false);
        assertThat(reads).as("the first found decides").containsExactly("\\UserChoiceLatest|ProgId");
        assertThat(DefaultBrowser.askWindows(HTTPS, registry(Map.of(HTTPS + "\\UserChoiceLatest|ProgId", "JasperURL",
            HTTPS + "\\UserChoice|ProgId", "MSEdgeHTM"), new ArrayList<>()))).contains(true);
    }

    @Test void onWindowsUserChoiceLatestsProgIdSubkeyComesNext() {
        assertThat(DefaultBrowser.askWindows(HTTPS, registry(Map.of(HTTPS + "\\UserChoiceLatest\\ProgId|ProgId", "JasperURL",
            HTTPS + "\\UserChoice|ProgId", "MSEdgeHTM"), new ArrayList<>()))).as("the subkey before the older key").contains(true);
        assertThat(DefaultBrowser.askWindows(HTTPS, registry(Map.of(HTTPS + "\\UserChoiceLatest|ProgId", "MSEdgeHTM",
            HTTPS + "\\UserChoiceLatest\\ProgId|ProgId", "JasperURL"), new ArrayList<>()))).as("the value before the subkey").contains(false);
    }

    @Test void onWindowsNoChoiceAnswersNoAndAFailedReadGivesNoAnswer() {
        assertThat(DefaultBrowser.askWindows(HTTPS, registry(Map.of(), new ArrayList<>()))).as("no key or value: no choice made").contains(false);
        BiFunction<String, String, Optional<String>> failing = (key, name) -> { throw new UncheckedIOException(new IOException("RegGetValueW failed with 5")); };
        assertThat(DefaultBrowser.askWindows(HTTPS, failing)).as("a read that fails").isEmpty();
    }

    @Test @EnabledOnOs(OS.WINDOWS) void theRealRegistryIsReadInTheSameOrder() throws IOException {
        String scratch = "Software\\Jasper-test-" + UUID.randomUUID();
        try {
            assertThat(DefaultBrowser.askWindows(scratch, DefaultBrowser::readRegistry)).as("a missing key answers no").contains(false);
            WindowsUserPath.write(scratch + "\\UserChoice", "ProgId", "JasperURL");
            assertThat(DefaultBrowser.askWindows(scratch, DefaultBrowser::readRegistry)).contains(true);
            WindowsUserPath.write(scratch + "\\UserChoiceLatest", "ProgId", "MSEdgeHTM");
            assertThat(DefaultBrowser.askWindows(scratch, DefaultBrowser::readRegistry)).contains(false);
        } finally {
            WindowsUserPath.delete(scratch);
        }
    }

    @Test void beforeTheFirstAnswerJasperIsNotTheDefault() {
        var queued = new ArrayDeque<Runnable>();
        var browser = new DefaultBrowser(queued::add, new MutableClock(), () -> Optional.of(true));
        assertThat(browser.isJasper()).isFalse();
        browser.refresh();
        assertThat(browser.isJasper()).as("the check waits on the worker").isFalse();
        assertThat(queued).hasSize(1);
        queued.remove().run();
        assertThat(browser.isJasper()).isTrue();
    }

    /** Ruling Q19: a user who changed the default and comes back to Jasper is seen at the next activation. */
    @Test void refreshChecksAtMostOncePerFiveSeconds() {
        var clock = new MutableClock();
        var checks = new AtomicInteger();
        var browser = new DefaultBrowser(Runnable::run, clock, () -> Optional.of(checks.incrementAndGet() > 0));
        browser.refresh();
        assertThat(checks).hasValue(1);
        clock.advance(Duration.ofSeconds(2));
        browser.refresh();
        clock.advance(Duration.ofMillis(2_999));
        browser.refresh();
        assertThat(checks).as("within 5 seconds of the last check").hasValue(1);
        clock.advance(Duration.ofMillis(1));
        browser.refresh();
        browser.refresh();
        assertThat(checks).as("5 seconds on, once").hasValue(2);
    }

    @Test void aClockSetBackDoesNotStopTheChecks() {
        var clock = new MutableClock();
        var checks = new AtomicInteger();
        var browser = new DefaultBrowser(Runnable::run, clock, () -> Optional.of(checks.incrementAndGet() > 0));
        browser.refresh();
        clock.advance(Duration.ofHours(-1));
        browser.refresh();
        assertThat(checks).as("the last check is not an hour in the future").hasValue(2);
        browser.refresh();
        assertThat(checks).as("throttled from the new time").hasValue(2);
    }

    @Test void aCheckStillRunningIsNotStartedAgain() {
        var clock = new MutableClock();
        var queued = new ArrayDeque<Runnable>();
        var answers = new ArrayDeque<>(List.of(Optional.of(true), Optional.of(false)));
        var browser = new DefaultBrowser(queued::add, clock, answers::remove);
        browser.refresh();
        clock.advance(Duration.ofMinutes(5));
        browser.refresh();
        assertThat(queued).as("the first check still runs: no second one piles up behind it").hasSize(1);
        queued.remove().run();
        assertThat(browser.isJasper()).isTrue();
        browser.refresh();
        assertThat(queued).as("once it has finished, the next refresh checks").hasSize(1);
        queued.remove().run();
        assertThat(browser.isJasper()).isFalse();
    }

    @Test void aCheckWithoutAnAnswerKeepsTheLastAnswer() {
        var clock = new MutableClock();
        var answer = new AtomicReference<Supplier<Optional<Boolean>>>(Optional::empty);
        var browser = new DefaultBrowser(Runnable::run, clock, () -> answer.get().get());
        browser.refresh();
        assertThat(browser.isJasper()).as("no answer yet").isFalse();
        answer.set(() -> Optional.of(true));
        next(clock, browser);
        assertThat(browser.isJasper()).isTrue();
        answer.set(Optional::empty);
        next(clock, browser);
        assertThat(browser.isJasper()).as("a check that could not tell, such as a time-out").isTrue();
        answer.set(() -> { throw new IllegalStateException("the system could not say"); });
        next(clock, browser);
        assertThat(browser.isJasper()).as("a check that throws").isTrue();
        answer.set(() -> { throw new UnsatisfiedLinkError("the bridge could not load"); });
        next(clock, browser);
        assertThat(browser.isJasper()).as("a bridge that cannot load").isTrue();
        answer.set(() -> null);
        next(clock, browser);
        assertThat(browser.isJasper()).as("a check that returns nothing at all").isTrue();
        answer.set(() -> Optional.of(false));
        next(clock, browser);
        assertThat(browser.isJasper()).as("only an answer changes it").isFalse();
        answer.set(Optional::empty);
        next(clock, browser);
        assertThat(browser.isJasper()).isFalse();
    }

    private static void next(MutableClock clock, DefaultBrowser browser) {
        clock.advance(Duration.ofSeconds(5));
        browser.refresh();
    }

    /**
     * Browser 4c Task 2 (ruling Q7): the listener hears each answer that changes the value, once and in order, on the EDT;
     * an answer equal to the last, and a check without an answer, tell it nothing.
     */
    @Test void theListenerHearsEachChangeOnceOnTheEventDispatchThread() throws Exception {
        var clock = new MutableClock();
        var answers = new ArrayDeque<>(List.of(Optional.of(false), Optional.of(true), Optional.of(true), Optional.<Boolean>empty(),
            Optional.of(false)));
        var browser = new DefaultBrowser(Runnable::run, clock, answers::remove);
        var heard = new CopyOnWriteArrayList<String>();
        browser.onChanged(jasper -> heard.add(jasper + (SwingUtilities.isEventDispatchThread() ? " on the EDT" : " elsewhere")));
        browser.refresh();
        for (int i = 1; i < 5; i++) next(clock, browser);
        assertThat(answers).as("every answer asked for").isEmpty();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(heard).as("no to yes, then yes to no").containsExactly("true on the EDT", "false on the EDT");
        var later = new CopyOnWriteArrayList<Boolean>();
        browser.onChanged(later::add);
        answers.add(Optional.of(true));
        next(clock, browser);
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(later).as("a later listener replaces the first").containsExactly(true);
        assertThat(heard).hasSize(2);
    }

    @Test void aWorkerThatRefusesTheCheckKeepsTheAnswerAndTriesAgainLater() {
        var clock = new MutableClock();
        var offered = new AtomicInteger();
        var browser = new DefaultBrowser(work -> {
            offered.incrementAndGet();
            throw new RejectedExecutionException("closed");
        }, clock, () -> Optional.of(true));
        assertThatCode(browser::refresh).doesNotThrowAnyException();
        assertThat(browser.isJasper()).isFalse();
        next(clock, browser);
        assertThat(offered).as("a refused check is not still running").hasValue(2);
    }

    /**
     * The final review (T1a): a worker that throws anything but {@link RejectedExecutionException} leaves no check
     * running either, so the next one runs; what it threw is logged, and an {@link Error} still reaches the caller.
     */
    @Test void aWorkerThatThrowsLeavesNoCheckRunning() {
        var clock = new MutableClock();
        var failures = new ArrayDeque<Throwable>(List.of(new IllegalStateException("the worker broke"), new InternalError("the VM broke")));
        var checks = new AtomicInteger();
        var browser = new DefaultBrowser(work -> {
            switch (failures.poll()) {
                case null -> work.run();
                case RuntimeException failure -> throw failure;
                case Error failure -> throw failure;
                default -> throw new AssertionError();
            }
        }, clock, () -> Optional.of(checks.incrementAndGet() > 0));
        try (var log = dev.jasper.app.testsupport.LogRecorder.of(DefaultBrowser.class.getName())) {
            assertThatCode(browser::refresh).doesNotThrowAnyException();
            assertThat(log.warnings()).containsExactly("The default browser check could not be started");
        }
        clock.advance(Duration.ofSeconds(5));
        assertThatThrownBy(browser::refresh).isInstanceOf(InternalError.class).hasMessage("the VM broke");
        next(clock, browser);
        assertThat(checks).as("neither left a check running").hasValue(1);
        assertThat(browser.isJasper()).isTrue();
    }

    @Test void aSlowCheckNeverBlocksTheCaller() throws Exception {
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var browser = new DefaultBrowser(work -> Thread.ofVirtual().start(() -> { work.run(); finished.countDown(); }),
            Clock.systemUTC(), () -> {
                try { return Optional.of(release.await(30, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return Optional.empty(); }
            });
        long began = System.nanoTime();
        browser.refresh();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("refresh only hands the check over").isLessThan(Duration.ofSeconds(1));
        assertThat(browser.isJasper()).as("no answer yet").isFalse();
        release.countDown();
        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(browser.isJasper()).isTrue();
    }

    @Test void theFirstAnswerCompletesTheFuture() throws Exception {
        var clock = new MutableClock();
        var queued = new ArrayDeque<Runnable>();
        var answers = new ArrayDeque<>(List.of(Optional.<Boolean>empty(), Optional.of(true), Optional.of(false)));
        // A bound the test never reaches: only the checks below answer.
        var browser = new DefaultBrowser(queued::add, clock, answers::remove, Duration.ofHours(1));
        CompletableFuture<Boolean> first = browser.firstAnswer();
        assertThat(first).as("before any check").isNotDone();
        browser.refresh();
        queued.remove().run();
        assertThat(first).as("a check without an answer").isNotDone();
        next(clock, browser);
        queued.remove().run();
        assertThat(first.get(0, TimeUnit.SECONDS)).isTrue();
        next(clock, browser);
        queued.remove().run();
        assertThat(browser.isJasper()).isFalse();
        assertThat(browser.firstAnswer().get(0, TimeUnit.SECONDS)).as("later answers leave the first").isTrue();
    }

    @Test void withoutAnAnswerTheFirstAnswerIsNoOnceItsBoundHasPassed() throws Exception {
        var queued = new ArrayDeque<Runnable>();
        var browser = new DefaultBrowser(queued::add, new MutableClock(), () -> Optional.of(true), Duration.ofMillis(50));
        browser.refresh();
        long began = System.nanoTime();
        assertThat(browser.firstAnswer().get(5, TimeUnit.SECONDS)).as("the check never ran").isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(5));
        queued.remove().run();
        assertThat(browser.isJasper()).as("a late answer still counts from then on").isTrue();
        assertThat(browser.firstAnswer().get(0, TimeUnit.SECONDS)).isFalse();
    }

    @Test void completingTheReturnedFutureChangesNothing() {
        var browser = new DefaultBrowser(new ArrayDeque<Runnable>()::add, new MutableClock(), () -> Optional.of(false), Duration.ofHours(1));
        assertThat(browser.firstAnswer().complete(true)).isTrue();
        assertThat(browser.firstAnswer()).isNotDone();
        assertThat(browser.isJasper()).isFalse();
    }

    @Test void aMissingToolGivesNoAnswer() {
        List<String> missing = List.of("jasper-no-such-tool-" + UUID.randomUUID(), "get", "default-web-browser");
        assertThat(DefaultBrowser.run(missing, TIMEOUT)).isEmpty();
        assertThat(DefaultBrowser.askLinux(missing, TIMEOUT)).isEmpty();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void theToolsOutputIsTheAnswer() {
        List<String> answering = List.of("echo", "jasper-workstation-Jasper.desktop");
        assertThat(DefaultBrowser.run(answering, TIMEOUT)).contains("jasper-workstation-Jasper.desktop\n");
        assertThat(DefaultBrowser.askLinux(answering, TIMEOUT)).contains(true);
        assertThat(DefaultBrowser.askLinux(List.of("echo", "firefox.desktop"), TIMEOUT)).contains(false);
        assertThat(DefaultBrowser.askLinux(List.of("true"), TIMEOUT)).as("exits with 0 and names no browser").contains(false);
    }

    @Test @DisabledOnOs(OS.WINDOWS) void aToolThatFailsGivesNoAnswerWhateverItPrinted() {
        // xdg-settings exits with 3 when a tool it needs is missing and 4 when it could not tell.
        List<String> failing = List.of("/bin/sh", "-c", "echo jasper-workstation-Jasper.desktop; exit 4");
        assertThat(DefaultBrowser.run(failing, TIMEOUT)).isEmpty();
        assertThat(DefaultBrowser.askLinux(failing, TIMEOUT)).isEmpty();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void aToolThatTakesTooLongGivesNoAnswerAndIsEnded() throws Exception {
        Set<Long> before = ProcessHandle.current().children().map(ProcessHandle::pid).collect(Collectors.toSet());
        long began = System.nanoTime();
        assertThat(DefaultBrowser.askLinux(List.of("sleep", "30"), Duration.ofMillis(200))).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("given up at its time-out").isLessThan(Duration.ofSeconds(5));
        for (ProcessHandle child : ProcessHandle.current().children().filter(child -> !before.contains(child.pid())).toList())
            assertThat(child.onExit().get(5, TimeUnit.SECONDS).isAlive()).as("the timed-out tool was ended").isFalse();
    }

    /** {@code xdg-settings} is a shell script: a time-out ends what it started too, not only the shell. */
    @Test @DisabledOnOs(OS.WINDOWS) void aTimedOutToolsOwnChildrenAreEndedToo() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "needs /bin/sh");
        Set<Long> before = ProcessHandle.current().descendants().map(ProcessHandle::pid).collect(Collectors.toSet());
        var answer = new CompletableFuture<Optional<String>>();
        Thread.ofVirtual().start(() -> answer.complete(DefaultBrowser.run(List.of("/bin/sh", "-c", "sleep 30; :"), Duration.ofSeconds(2))));
        ProcessHandle sleeper = null;
        boolean unnamed = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (sleeper == null && System.nanoTime() < deadline) {
            List<ProcessHandle> started = ProcessHandle.current().descendants().filter(process -> !before.contains(process.pid())).toList();
            sleeper = started.stream().filter(process -> process.info().command().orElse("").endsWith("sleep")).findFirst().orElse(null);
            unnamed |= started.stream().anyMatch(process -> process.info().command().isEmpty());
            if (sleeper == null) Thread.sleep(10);
        }
        if (sleeper == null) {
            assertThat(answer.get(10, TimeUnit.SECONDS)).isEmpty();
            assumeTrue(!unnamed, "this system does not say which program the shell's child runs, so it cannot be found");
        }
        assertThat(sleeper).as("the shell's own child, found while the shell waits").isNotNull();
        assertThat(answer.get(10, TimeUnit.SECONDS)).isEmpty();
        assertThat(sleeper.onExit().get(5, TimeUnit.SECONDS).isAlive()).as("the shell's child was ended with it").isFalse();
    }

    @Test void otherSystemsAndATestJvmAnswerNo() {
        assertThat(DefaultBrowser.ask(null)).contains(false);
        assertThat(DefaultBrowser.ask("SunOS")).contains(false);
        // A test JVM is not a packaged macOS app: the bridge does not load, so Launch Services is not asked.
        assertThat(DefaultBrowser.ask("Mac OS X")).contains(false);
    }

    @Test @DisabledOnOs(OS.WINDOWS) void elsewhereTheRegistryIsNeverRead() {
        assertThat(DefaultBrowser.ask("Windows 11")).as("WindowsUserPath refuses before reading").isEmpty();
    }

    @Test
    @EnabledIfSystemProperty(named = "jasper.testNativeLibrary", matches = ".+")
    @SuppressWarnings("restricted")
    void theRealBridgeAsksLaunchServices() {
        System.load(System.getProperty("jasper.testNativeLibrary"));
        assertThat(MacLaunchServices.defaultBrowserBundleId("jasper-test-no-such-scheme")).as("no application opens it").isNull();
        assertThatCode(() -> MacLaunchServices.defaultBrowserBundleId("https")).doesNotThrowAnyException();
    }
}
