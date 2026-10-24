package dev.jasper.app.testsupport;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * What a logger logs while the recorder is open, kept for a test to assert instead of printed on the console: the logger's
 * parent handlers are off until {@link #close()}. {@code System.Logger}s of the same name are recorded too. Any thread.
 */
public final class LogRecorder implements AutoCloseable {
    /** Held: the log manager keeps its loggers weakly, and a collected logger would drop the handler. */
    private final Logger logger;
    private final boolean parentHandlers;
    private final Queue<LogRecord> records = new ConcurrentLinkedQueue<>();
    private final Handler handler = new Handler() {
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    private LogRecorder(Logger logger) {
        this.logger = logger;
        this.parentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    /**
     * Starts recording.
     *
     * @param loggerName the logger's name, the logging class's name for a class's own logger
     * @return the recorder, to close
     */
    public static LogRecorder of(String loggerName) {
        return new LogRecorder(Logger.getLogger(loggerName));
    }

    /** @return every record logged since this recorder opened, in order */
    public List<LogRecord> records() { return List.copyOf(records); }

    /** @return the messages of the {@link Level#WARNING} records, in order */
    public List<String> warnings() {
        return records.stream().filter(record -> record.getLevel() == Level.WARNING).map(LogRecord::getMessage).toList();
    }

    /** Stops recording and gives the logger's parent handlers back as they were. */
    @Override public void close() {
        logger.removeHandler(handler);
        logger.setUseParentHandlers(parentHandlers);
    }
}
