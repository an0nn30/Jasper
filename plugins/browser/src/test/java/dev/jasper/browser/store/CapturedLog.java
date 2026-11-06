package dev.jasper.browser.store;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** What a {@code System.Logger} writes while this is open, kept off the console. */
public final class CapturedLog implements AutoCloseable {
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    // Held, so the logger keeps the handler.
    private final Logger logger;
    private final boolean parentHandlers;
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    CapturedLog(Class<?> owner) {
        this(owner.getName());
    }

    /**
     * Captures what the logger {@code name} writes, as {@code System.getLogger(name)} gives it.
     *
     * @param name the logger's name
     */
    public CapturedLog(String name) {
        logger = Logger.getLogger(name);
        parentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(capture);
    }

    /**
     * The records written so far.
     *
     * @return them, in order
     */
    public List<LogRecord> records() {
        return List.copyOf(records);
    }

    @Override public void close() {
        logger.removeHandler(capture);
        logger.setUseParentHandlers(parentHandlers);
    }
}
