package dev.jasper.app.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What a log recorder keeps, and what it keeps from the console. */
class LogRecorderTest {
    private static final String PARENT = "dev.jasper.app.testsupport.LogRecorderTest.parent";
    private static final String CHILD = PARENT + ".child";
    /** The loggers are held here: the log manager keeps its loggers weakly. */
    private final Logger parent = Logger.getLogger(PARENT);
    private final Logger child = Logger.getLogger(CHILD);
    /** What the child's parent heard, as the console's handler, on the root logger, would. */
    private final Queue<String> heardByParent = new ConcurrentLinkedQueue<>();
    private final Handler parentHandler = new Handler() {
        @Override public void publish(LogRecord record) { heardByParent.add(record.getMessage()); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeEach void listen() {
        parent.setUseParentHandlers(false);
        parent.addHandler(parentHandler);
        child.setUseParentHandlers(true);
    }

    @AfterEach void stopListening() {
        parent.removeHandler(parentHandler);
    }

    @Test void keepsEveryRecordTheLoggerLogsWhileOpenAndNoneAfter() {
        LogRecorder recorder = LogRecorder.of(CHILD);
        IllegalStateException why = new IllegalStateException("why");
        child.log(Level.WARNING, "first", why);
        child.info("second");
        recorder.close();
        child.warning("third");
        assertThat(recorder.records()).extracting(LogRecord::getMessage).containsExactly("first", "second");
        assertThat(recorder.records().getFirst().getThrown()).isSameAs(why);
    }

    @Test void warningsAreTheMessagesOfTheWarningRecordsAlone() {
        try (LogRecorder recorder = LogRecorder.of(CHILD)) {
            child.info("note");
            child.warning("trouble");
            child.severe("worse");
            assertThat(recorder.warnings()).containsExactly("trouble");
        }
    }

    @Test void keepsTheParentsHandlersFromHearingWhileOpenAndGivesThemBackOnClose() {
        try (LogRecorder recorder = LogRecorder.of(CHILD)) {
            child.warning("recorded");
            assertThat(recorder.records()).hasSize(1);
            assertThat(heardByParent).as("not printed").isEmpty();
        }
        assertThat(child.getUseParentHandlers()).isTrue();
        child.warning("printed");
        assertThat(heardByParent).containsExactly("printed");
    }

    @Test void leavesParentHandlersOffWhenTheyWereOffBefore() {
        child.setUseParentHandlers(false);
        LogRecorder recorder = LogRecorder.of(CHILD);
        child.warning("recorded");
        recorder.close();
        assertThat(recorder.records()).hasSize(1);
        assertThat(child.getUseParentHandlers()).isFalse();
    }
}
