package dev.moray.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AppLogTest {
    private static final Map<Handler, Filter> originalRootFilters = new IdentityHashMap<>();
    @TempDir Path temporary;

    @BeforeAll
    static void containDeliberateFailureRecords() {
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            originalRootFilters.put(handler, handler.getFilter());
            Filter original = handler.getFilter();
            handler.setFilter(record -> (record.getLoggerName() == null
                || !record.getLoggerName().startsWith("dev.moray"))
                && (original == null || original.isLoggable(record)));
        }
    }

    @AfterAll
    static void restoreFailureRecordRouting() {
        originalRootFilters.forEach(Handler::setFilter);
        originalRootFilters.clear();
    }

    @Test
    void persistsUtf8WarningWithoutExceptionMessagesOrParameters() throws Exception {
        Path logs = temporary.resolve("logs");
        try (var log = AppLog.open(logs, 64 * 1024, 3, 32)) {
            assertThat(log.enabled()).isTrue();
            System.getLogger("dev.moray.terminal.Test").log(System.Logger.Level.WARNING,
                "Terminal operation failed \u2713 {0}", "SECRET_PARAMETER");
            System.getLogger("dev.moray.terminal.Test").log(System.Logger.Level.ERROR,
                "Terminal emulation failed", new IOException("SECRET_SENTINEL"));
        }

        String text = contents(logs);
        assertThat(text).contains("WARNING", "Terminal operation failed \u2713", "SEVERE",
            "Terminal emulation failed", "java.io.IOException", "AppLogTest");
        assertThat(text).doesNotContain("SECRET_PARAMETER", "SECRET_SENTINEL");
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
    void overflowDoesNotBlockAndCloseDrainsAcceptedRecords() throws Exception {
        Path logs = temporary.resolve("overflow");
        try (var log = AppLog.open(logs, 1024 * 1024, 3, 1)) {
            assertThat(log.enabled()).isTrue();
            for (int index = 0; index < 20_000; index++) {
                System.getLogger("dev.moray.app.Test").log(System.Logger.Level.WARNING,
                    "Queue pressure diagnostic");
            }
            System.getLogger("dev.moray.app.Test").log(System.Logger.Level.ERROR,
                "Final drain diagnostic");
        }

        assertThat(contents(logs)).contains("log records dropped");
    }

    @Test
    void concurrentInstallationsOwnOnlyTheirHandlersAndCloseIndependently() throws Exception {
        Logger namespace = Logger.getLogger("dev.moray");
        Set<Handler> before = Set.of(namespace.getHandlers());
        boolean parentBefore = namespace.getUseParentHandlers();
        var first = AppLog.open(temporary.resolve("one"), 64 * 1024, 3, 16);
        var second = AppLog.open(temporary.resolve("two"), 64 * 1024, 3, 16);
        try {
            first.close();
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
        log.close();
        log.close();

        for (Path file : logFiles(logs)) Files.delete(file);
        assertThat(logFiles(logs)).isEmpty();
    }

    @Test
    void unwritableDestinationReturnsDisabledCloseable() throws Exception {
        Path file = temporary.resolve("not-a-directory");
        Files.writeString(file, "occupied");

        try (var log = AppLog.open(file)) {
            assertThat(log.enabled()).isFalse();
        }
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
}
