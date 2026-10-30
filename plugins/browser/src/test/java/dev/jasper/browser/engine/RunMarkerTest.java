package dev.jasper.browser.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The marker that Chromium starts or runs: written, deleted, not written again once Chromium has stopped, and read once
 * at the next start. A marker that fails only warns; the warnings are captured here, not printed.
 */
class RunMarkerTest {
    @TempDir Path data;
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    // Held, so the logger keeps the handler.
    private final Logger logger = Logger.getLogger(RunMarker.class.getName());
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record.getMessage());
        }
        @Override public void flush() { }
        @Override public void close() { }
    };
    private boolean parentHandlers;

    @BeforeEach void listen() {
        parentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(capture);
    }

    @AfterEach void stopListening() {
        logger.removeHandler(capture);
        logger.setUseParentHandlers(parentHandlers);
    }

    @Test void startedWritesAnEmptyMarkerAndStoppedDeletesIt() throws Exception {
        var marker = new RunMarker(data);
        Path file = data.resolve("chromium.running");
        marker.started();
        assertThat(file).isRegularFile().isEmptyFile();
        marker.stopped();
        assertThat(file).doesNotExist();
        marker.stopped();
        assertThat(warnings).isEmpty();
    }

    /** A close that comes between the start's entry and its write leaves no marker: once stopped, it is not written. */
    @Test void onceStoppedTheMarkerIsNotWrittenAgain() throws Exception {
        var marker = new RunMarker(data);
        marker.stopped();
        marker.started();
        assertThat(data.resolve("chromium.running")).doesNotExist();
        assertThat(warnings).isEmpty();
    }

    @Test void clearAnswersWhetherTheLastRunLeftTheMarkerOnceAndDeletesIt() throws Exception {
        assertThat(RunMarker.clear(data)).as("no marker").isFalse();
        Files.createFile(data.resolve("chromium.running"));
        assertThat(RunMarker.clear(data)).isTrue();
        assertThat(data.resolve("chromium.running")).doesNotExist();
        assertThat(RunMarker.clear(data)).as("once").isFalse();
        assertThat(RunMarker.clear(data.resolve("missing"))).as("no data directory").isFalse();
        assertThat(warnings).isEmpty();
    }

    @Test void aMarkerThatCannotBeWrittenOrDeletedIsAWarningAndThrowsNothing() throws Exception {
        Path file = Files.createFile(data.resolve("file"));
        new RunMarker(file).started();
        assertThat(warnings).singleElement().asString().startsWith("The browser's run marker could not be written");

        // A directory in the marker's place, with something in it, cannot be deleted as the marker is.
        Path stuck = Files.createDirectories(data.resolve("stuck").resolve("chromium.running"));
        Files.createFile(stuck.resolve("inside"));
        new RunMarker(stuck.getParent()).stopped();
        assertThat(warnings).hasSize(2).last().asString().startsWith("The browser's run marker could not be deleted");
        assertThat(RunMarker.clear(stuck.getParent())).as("there, though it could not be deleted").isTrue();
        assertThat(warnings).hasSize(3).last().asString().startsWith("The browser's run marker could not be deleted");
    }
}
