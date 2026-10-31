package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.jasper.app.testsupport.MutableClock;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Whether Jasper is the system's default browser: each system's answer compared on every system, the kept answer and
 * its throttle with stand-in checks, and the Linux tool's failures and time-out with stand-in commands. Nothing here
 * asks the real registry or {@code xdg-settings}.
 */
class DefaultBrowserTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

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
        assertThat(DefaultBrowser.parseWindows(null)).as("the key or its value is missing").isFalse();
    }

    @Test void onLinuxXdgSettingsMustNameThePackagesDesktopEntry() {
        assertThat(DefaultBrowser.parseLinux("jasper-workstation-Jasper.desktop\n")).isTrue();
        assertThat(DefaultBrowser.parseLinux("jasper-workstation-Jasper.desktop")).isTrue();
        assertThat(DefaultBrowser.parseLinux("firefox.desktop\n")).isFalse();
        assertThat(DefaultBrowser.parseLinux("Jasper.desktop\n")).isFalse();
        assertThat(DefaultBrowser.parseLinux("jasper-workstation-jasper.desktop\n")).as("desktop file ids keep their case").isFalse();
        assertThat(DefaultBrowser.parseLinux("\n")).as("no default set").isFalse();
        assertThat(DefaultBrowser.parseLinux(null)).as("the tool gave no answer").isFalse();
    }

    @Test void beforeTheFirstAnswerJasperIsNotTheDefault() {
        var queued = new ArrayDeque<Runnable>();
        var browser = new DefaultBrowser(queued::add, new MutableClock(), () -> true);
        assertThat(browser.isJasper()).isFalse();
        browser.refresh();
        assertThat(browser.isJasper()).as("the check waits on the worker").isFalse();
        assertThat(queued).hasSize(1);
        queued.remove().run();
        assertThat(browser.isJasper()).isTrue();
    }

    @Test void refreshChecksAtMostOncePerThirtySeconds() {
        var clock = new MutableClock();
        var checks = new AtomicInteger();
        var browser = new DefaultBrowser(Runnable::run, clock, () -> checks.incrementAndGet() > 0);
        browser.refresh();
        assertThat(checks).hasValue(1);
        clock.advance(Duration.ofSeconds(10));
        browser.refresh();
        clock.advance(Duration.ofMillis(19_999));
        browser.refresh();
        assertThat(checks).as("within 30 seconds of the last check").hasValue(1);
        clock.advance(Duration.ofMillis(1));
        browser.refresh();
        browser.refresh();
        assertThat(checks).as("30 seconds on, once").hasValue(2);
    }

    @Test void aClockSetBackDoesNotStopTheChecks() {
        var clock = new MutableClock();
        var checks = new AtomicInteger();
        var browser = new DefaultBrowser(Runnable::run, clock, () -> checks.incrementAndGet() > 0);
        browser.refresh();
        clock.advance(Duration.ofHours(-1));
        browser.refresh();
        assertThat(checks).as("the last check is not an hour in the future").hasValue(2);
        browser.refresh();
        assertThat(checks).as("throttled from the new time").hasValue(2);
    }

    @Test void theAnswerFollowsTheLatestCheckAndAFailureAnswersNo() {
        var clock = new MutableClock();
        var answer = new AtomicReference<BooleanSupplier>(() -> true);
        var browser = new DefaultBrowser(Runnable::run, clock, () -> answer.get().getAsBoolean());
        browser.refresh();
        assertThat(browser.isJasper()).isTrue();
        answer.set(() -> { throw new IllegalStateException("the system could not say"); });
        clock.advance(Duration.ofSeconds(30));
        browser.refresh();
        assertThat(browser.isJasper()).as("a check that throws").isFalse();
        answer.set(() -> true);
        clock.advance(Duration.ofSeconds(30));
        browser.refresh();
        answer.set(() -> { throw new UnsatisfiedLinkError("the bridge could not load"); });
        clock.advance(Duration.ofSeconds(30));
        browser.refresh();
        assertThat(browser.isJasper()).as("a bridge that cannot load").isFalse();
    }

    @Test void aWorkerThatRefusesTheCheckKeepsTheAnswer() {
        var browser = new DefaultBrowser(work -> { throw new java.util.concurrent.RejectedExecutionException("closed"); },
            new MutableClock(), () -> true);
        assertThatCode(browser::refresh).doesNotThrowAnyException();
        assertThat(browser.isJasper()).isFalse();
    }

    @Test void aSlowCheckNeverBlocksTheCaller() throws Exception {
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var browser = new DefaultBrowser(work -> Thread.ofVirtual().start(() -> { work.run(); finished.countDown(); }),
            Clock.systemUTC(), () -> {
                try { return release.await(30, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
            });
        long began = System.nanoTime();
        browser.refresh();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("refresh only hands the check over").isLessThan(Duration.ofSeconds(1));
        assertThat(browser.isJasper()).as("no answer yet").isFalse();
        release.countDown();
        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(browser.isJasper()).isTrue();
    }

    @Test void aMissingToolAnswersNo() {
        List<String> missing = List.of("jasper-no-such-tool-" + UUID.randomUUID(), "get", "default-web-browser");
        assertThat(DefaultBrowser.run(missing, TIMEOUT)).isEmpty();
        assertThat(DefaultBrowser.askLinux(missing, TIMEOUT)).isFalse();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void theToolsAnswerIsItsOutput() {
        List<String> answering = List.of("echo", "jasper-workstation-Jasper.desktop");
        assertThat(DefaultBrowser.run(answering, TIMEOUT)).contains("jasper-workstation-Jasper.desktop\n");
        assertThat(DefaultBrowser.askLinux(answering, TIMEOUT)).isTrue();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void aToolThatFailsAnswersNoWhateverItPrinted() {
        // xdg-settings exits with 3 when a tool it needs is missing and 4 when it could not tell.
        List<String> failing = List.of("/bin/sh", "-c", "echo jasper-workstation-Jasper.desktop; exit 4");
        assertThat(DefaultBrowser.run(failing, TIMEOUT)).isEmpty();
        assertThat(DefaultBrowser.askLinux(failing, TIMEOUT)).isFalse();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void aToolThatTakesTooLongAnswersNoAndIsEnded() throws Exception {
        Set<Long> before = ProcessHandle.current().children().map(ProcessHandle::pid).collect(Collectors.toSet());
        long began = System.nanoTime();
        assertThat(DefaultBrowser.askLinux(List.of("sleep", "30"), Duration.ofMillis(200))).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("answered at its time-out").isLessThan(Duration.ofSeconds(5));
        for (ProcessHandle child : ProcessHandle.current().children().filter(child -> !before.contains(child.pid())).toList())
            assertThat(child.onExit().get(5, TimeUnit.SECONDS).isAlive()).as("the timed-out tool was ended").isFalse();
    }

    @Test @EnabledOnOs(OS.WINDOWS) void aMissingRegistryKeyAnswersNo() throws IOException {
        assertThat(DefaultBrowser.askWindows("Software\\Jasper-test-" + UUID.randomUUID() + "\\UserChoice")).isFalse();
    }

    @Test void otherSystemsAndATestJvmAnswerNo() {
        assertThat(DefaultBrowser.ask(null)).isFalse();
        assertThat(DefaultBrowser.ask("SunOS")).isFalse();
        // A test JVM is not a packaged macOS app: the bridge does not load, so Launch Services is not asked.
        assertThat(DefaultBrowser.ask("Mac OS X")).isFalse();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void elsewhereTheRegistryIsNeverRead() {
        assertThat(DefaultBrowser.ask("Windows 11")).isFalse();
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
