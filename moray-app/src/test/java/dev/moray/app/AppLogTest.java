package dev.moray.app;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class AppLogTest {
    @TempDir Path temporary;

    @Test
    void persistsUtf8WarningWithoutExceptionMessagesOrParameters() throws Exception {
        Path logs = temporary.resolve("logs");
        StringBuilder parentOutput = new StringBuilder();
        Handler parent = new Handler() {
            @Override public void publish(LogRecord record) {
                parentOutput.append(record.getMessage());
                if (record.getThrown() != null) parentOutput.append(record.getThrown().getMessage());
                if (record.getParameters() != null) for (Object value : record.getParameters()) parentOutput.append(value);
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        Logger root = Logger.getLogger("");
        root.addHandler(parent);
        try (var log = AppLog.open(logs, 64 * 1024, 3, 32)) {
            assertThat(log.enabled()).isTrue();
            System.getLogger("dev.moray.terminal.Test").log(System.Logger.Level.WARNING,
                "Terminal operation failed \u2713 {0}", "SECRET_PARAMETER");
            System.getLogger("dev.moray.terminal.Test").log(System.Logger.Level.ERROR,
                "Terminal emulation failed", new IOException("SECRET_SENTINEL"));
        } finally {
            root.removeHandler(parent);
        }

        String text = contents(logs);
        assertThat(text).contains("WARNING", "Terminal operation failed \u2713", "SEVERE",
            "Terminal emulation failed", "java.io.IOException", "AppLogTest");
        assertThat(text).doesNotContain("SECRET_PARAMETER", "SECRET_SENTINEL");
        assertThat(parentOutput).doesNotContain("SECRET_PARAMETER", "SECRET_SENTINEL");
    }

    @Test
    void rotatesAtTheConfiguredByteBound() throws Exception {
        Path logs = temporary.resolve("rotated");
        try (var log = AppLog.open(logs, 512, 3, 64)) {
            assertThat(log.enabled()).isTrue();
            for (int index = 0; index < 80; index++) {
                System.getLogger("dev.moray.app.Test").log(System.Logger.Level.WARNING,
                    "A fixed diagnostic record that makes rotation observable");
            }
        }

        assertThat(logFiles(logs)).hasSizeGreaterThan(1).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void boundsRecordsAndThrowableCauseSummaries() throws Exception {
        Path logs = temporary.resolve("bounded");
        Throwable failure = new IOException("SECRET_SENTINEL");
        for (int index = 0; index < 80; index++) {
            failure = new IllegalStateException("SECRET_SENTINEL", failure);
        }
        try (var log = AppLog.open(logs, 64 * 1024, 3, 32)) {
            assertThat(log.enabled()).isTrue();
            System.getLogger("dev.moray.app.Test").log(System.Logger.Level.ERROR,
                "x".repeat(20_000), failure);
        }

        String text = contents(logs);
        assertThat(text.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8 * 1024);
        assertThat(text).doesNotContain("SECRET_SENTINEL");
    }

    @Test
    void closeIsBoundedWhileWriterOwnsFinalDrainAndCleanup() throws Exception {
        var sink = new BlockingHandler();
        var log = AppLog.install(sink, 1, 50);
        System.getLogger("dev.moray.app.Test").log(System.Logger.Level.WARNING, "Writer blocker");
        assertThat(sink.entered.await(1, TimeUnit.SECONDS)).isTrue();
        System.getLogger("dev.moray.app.Test").log(System.Logger.Level.WARNING, "Accepted trailing record");
        System.getLogger("dev.moray.app.Test").log(System.Logger.Level.WARNING, "Overflow record");

        assertTimeout(Duration.ofMillis(500), log::close);
        assertThat(sink.closed.getCount()).isOne();
        sink.release.countDown();
        assertThat(sink.closed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(String.join("", sink.records)).contains("Accepted trailing record", "log records dropped");
    }

    @Test
    void unexpectedApplicationFailureUsesSanitizedLogAndRestoresPriorHandler() throws Exception {
        var sink = new RecordingHandler();
        var priorFailure = new AtomicReference<Throwable>();
        Thread.UncaughtExceptionHandler prior = (thread, failure) -> priorFailure.set(failure);
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(prior);
        try {
            try (var log = AppLog.install(sink, 8, 500);
                 var exceptions = Main.installUnexpectedExceptionHandler()) {
                assertThat(log.enabled()).isTrue();
                assertThat(exceptions).isNotNull();
                assertThat(Thread.getDefaultUncaughtExceptionHandler()).isNotSameAs(prior);
                Thread failure = Thread.ofPlatform().unstarted(() -> {
                    throw new IllegalStateException("SECRET_UNCAUGHT");
                });
                failure.start();
                failure.join();
            }
            assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(prior);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }

        assertThat(String.join("", sink.records)).contains("Unexpected application failure", "IllegalStateException")
            .doesNotContain("SECRET_UNCAUGHT");
        assertThat(priorFailure).hasValue(null);
    }

    @Test
    void edtStartupFailureSchedulesLogCleanupOffTheEdt() throws Exception {
        var sink = new BlockingHandler();
        var log = AppLog.install(sink, 1, 50);
        System.getLogger("dev.moray.app.Test").log(System.Logger.Level.WARNING, "Writer blocker");
        assertThat(sink.entered.await(1, TimeUnit.SECONDS)).isTrue();
        var cleanupThread = new AtomicReference<Thread>();
        var cleanupDone = new CountDownLatch(1);

        assertTimeout(Duration.ofMillis(300), () -> SwingUtilities.invokeAndWait(() ->
            Main.closeLogAfterStartupFailure(log, () -> {
                cleanupThread.set(Thread.currentThread());
                cleanupDone.countDown();
            })));
        sink.release.countDown();
        assertThat(cleanupDone.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(cleanupThread.get().getName()).startsWith("moray-startup-cleanup");
    }

    @Test
    void concurrentInstallationsOwnOnlyTheirHandlersAndCloseIndependently() throws Exception {
        Logger namespace = Logger.getLogger("dev.moray");
        Set<Handler> before = Set.of(namespace.getHandlers());
        boolean parentBefore = namespace.getUseParentHandlers();
        var first = AppLog.open(temporary.resolve("one"), 64 * 1024, 3, 16);
        var second = AppLog.open(temporary.resolve("two"), 64 * 1024, 3, 16);
        try {
            assertThat(namespace.getUseParentHandlers()).isFalse();
            first.close();
            assertThat(namespace.getUseParentHandlers()).isFalse();
            System.getLogger("dev.moray.app.Test").log(System.Logger.Level.WARNING,
                "Second installation remains active");
        } finally {
            first.close();
            second.close();
        }

        assertThat(contents(temporary.resolve("two"))).contains("Second installation remains active");
        assertThat(Set.of(namespace.getHandlers())).isEqualTo(before);
        assertThat(namespace.getUseParentHandlers()).isEqualTo(parentBefore);
    }

    @Test
    void closeIsIdempotentAndReleasesFileLock() throws Exception {
        Path logs = temporary.resolve("close");
        var log = AppLog.open(logs, 64 * 1024, 3, 16);
        assertThat(lockFiles(logs)).isNotEmpty();
        log.close();
        log.close();

        assertThat(lockFiles(logs)).isEmpty();
        for (Path file : logFiles(logs)) Files.delete(file);
        assertThat(logFiles(logs)).isEmpty();
    }

    @Test
    void unwritableDestinationRetainsPrivateRoutingForDiagnosticsAndUncaughtFailures() throws Exception {
        Path file = temporary.resolve("not-a-directory");
        Files.writeString(file, "occupied");
        PrintStream original = System.err;
        var captured = new ByteArrayOutputStream();
        var parentRecords = new RecordingHandler();
        Logger root = Logger.getLogger("");
        Logger namespace = Logger.getLogger("dev.moray");
        boolean parentBefore = namespace.getUseParentHandlers();
        AppLog failed = null;
        root.addHandler(parentRecords);
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            failed = AppLog.open(file);
            assertThat(failed.enabled()).isFalse();
            assertThat(namespace.getUseParentHandlers()).isFalse();
            System.getLogger("dev.moray.app.Test").log(System.Logger.Level.ERROR,
                "Failed-open diagnostic {0}", "SECRET_FAILED_PARAMETER");
            try (var exceptions = Main.installUnexpectedExceptionHandler()) {
                assertThat(exceptions).isNotNull();
                Thread failure = Thread.ofPlatform().unstarted(() -> {
                    throw new IllegalStateException("SECRET_FAILED_UNCAUGHT");
                });
                failure.start();
                failure.join();
            }
            try (var normal = AppLog.open(temporary.resolve("normal-alongside-disabled"), 64 * 1024, 3, 8)) {
                assertThat(normal.enabled()).isTrue();
                failed.close();
                assertThat(namespace.getUseParentHandlers()).isFalse();
                System.getLogger("dev.moray.app.Test").log(System.Logger.Level.ERROR,
                    "Overlapping installation diagnostic", new IOException("SECRET_OVERLAP"));
            }
        } finally {
            if (failed != null) failed.close();
            System.setErr(original);
            root.removeHandler(parentRecords);
        }
        assertThat(captured.toString(StandardCharsets.UTF_8))
            .isEqualTo("Moray diagnostics are unavailable." + System.lineSeparator());
        assertThat(String.join("", parentRecords.records))
            .doesNotContain("SECRET_FAILED_PARAMETER", "SECRET_FAILED_UNCAUGHT", "SECRET_OVERLAP");
        assertThat(namespace.getUseParentHandlers()).isEqualTo(parentBefore);
    }

    private static String contents(Path directory) throws IOException {
        StringBuilder result = new StringBuilder();
        for (Path file : logFiles(directory)) result.append(Files.readString(file, StandardCharsets.UTF_8));
        return result.toString();
    }

    private static java.util.List<Path> logFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return java.util.List.of();
        try (var files = Files.list(directory)) {
            return files.filter(path -> !path.getFileName().toString().endsWith(".lck"))
                .sorted().collect(Collectors.toList());
        }
    }

    private static List<Path> lockFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".lck")).toList();
        }
    }

    private static final class BlockingHandler extends Handler {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final List<String> records = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override public void publish(LogRecord record) {
            if (entered.getCount() > 0) {
                entered.countDown();
                boolean waiting = true;
                while (waiting) {
                    try { release.await(); waiting = false; }
                    catch (InterruptedException ignored) { /* Simulate an uninterruptible filesystem write. */ }
                }
            }
            records.add(record.getMessage());
        }

        @Override public void flush() {}
        @Override public void close() { closed.countDown(); }
    }

    private static final class RecordingHandler extends Handler {
        final List<String> records = java.util.Collections.synchronizedList(new ArrayList<>());
        @Override public void publish(LogRecord record) { records.add(record.getMessage()); }
        @Override public void flush() {}
        @Override public void close() {}
    }
}
