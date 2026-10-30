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

/** The marker that Chromium runs: written, deleted, and read once at the next start; a marker that fails only warns. */
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

    @BeforeEach void listen() { logger.addHandler(capture); }

    @AfterEach void stopListening() { logger.removeHandler(capture); }

    @Test void runningWritesAnEmptyMarkerAndEndedDeletesIt() throws Exception {
        var marker = new RunMarker(data);
        Path file = data.resolve("chromium.running");
        marker.running();
        assertThat(file).isRegularFile().isEmptyFile();
        marker.ended();
        assertThat(file).doesNotExist();
        marker.ended();
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
        new RunMarker(file).running();
        assertThat(warnings).singleElement().asString().startsWith("The browser's run marker could not be written");

        // A directory in the marker's place, with something in it, cannot be deleted as the marker is.
        Path stuck = Files.createDirectories(data.resolve("stuck").resolve("chromium.running"));
        Files.createFile(stuck.resolve("inside"));
        new RunMarker(stuck.getParent()).ended();
        assertThat(warnings).hasSize(2).last().asString().startsWith("The browser's run marker could not be deleted");
        assertThat(RunMarker.clear(stuck.getParent())).as("there, though it could not be deleted").isTrue();
        assertThat(warnings).hasSize(3);
    }
}
