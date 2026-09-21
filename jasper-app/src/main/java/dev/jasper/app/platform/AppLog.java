package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.ErrorManager;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Process-local, bounded diagnostics for Jasper-owned loggers. */
public final class AppLog implements AutoCloseable {
    private static final int DEFAULT_FILE_BYTES = 1024 * 1024;
    private static final int DEFAULT_FILE_COUNT = 3;
    private static final int DEFAULT_QUEUE_SIZE = 256;
    private static final int MAX_RECORD_BYTES = 8 * 1024;
    private static final long CLOSE_MILLIS = 2_000;
    private static final Logger NAMESPACE = Logger.getLogger("dev.jasper");
    private static final Object ROUTING_LOCK = new Object();
    private static int activeInstallations;
    private static boolean previousParentHandlers;

    private final AsyncHandler handler;
    private final boolean enabled;
    private final AtomicBoolean closed = new AtomicBoolean();

    private AppLog(AsyncHandler handler, boolean enabled) {
        this.handler = handler;
        this.enabled = enabled;
    }

    public static AppLog open(Path logs) {
        return open(logs, DEFAULT_FILE_BYTES, DEFAULT_FILE_COUNT, DEFAULT_QUEUE_SIZE);
    }

    public static AppLog open(Path logs, int fileBytes, int fileCount, int queueSize) {
        FileHandler files = null;
        AsyncHandler handler = null;
        try {
            if (fileBytes <= 0 || fileCount <= 0 || queueSize <= 0) throw new IllegalArgumentException();
            Files.createDirectories(logs);
            files = new FileHandler(logs.resolve("jasper-%u-%g.log").toString(), fileBytes, fileCount, true);
            files.setEncoding(StandardCharsets.UTF_8.name());
            files.setFormatter(new EncodedFormatter());
            files.setErrorManager(new FixedErrorManager());
            handler = new AsyncHandler(files, queueSize, CLOSE_MILLIS);
            handler.start();
            attach(handler);
            return new AppLog(handler, true);
        } catch (IOException | RuntimeException failure) {
            if (handler != null) handler.close();
            else if (files != null) files.close();
            System.err.println("Jasper diagnostics are unavailable.");
            AsyncHandler disabled = new AsyncHandler(new DiscardingHandler(), DEFAULT_QUEUE_SIZE, CLOSE_MILLIS);
            disabled.start();
            attach(disabled);
            return new AppLog(disabled, false);
        }
    }

    static AppLog install(Handler sink, int queueSize, long closeMillis) {
        AsyncHandler handler = new AsyncHandler(sink, queueSize, closeMillis);
        handler.start();
        attach(handler);
        return new AppLog(handler, true);
    }

    private static void attach(AsyncHandler handler) {
        synchronized (ROUTING_LOCK) {
            boolean first = activeInstallations == 0;
            if (first) {
                previousParentHandlers = NAMESPACE.getUseParentHandlers();
                NAMESPACE.setUseParentHandlers(false);
            }
            try {
                NAMESPACE.addHandler(handler);
                activeInstallations++;
            } catch (RuntimeException failure) {
                if (first) NAMESPACE.setUseParentHandlers(previousParentHandlers);
                throw failure;
            }
        }
    }

    boolean enabled() {
        return enabled;
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true) || handler == null) return;
        synchronized (ROUTING_LOCK) {
            NAMESPACE.removeHandler(handler);
            activeInstallations--;
            if (activeInstallations == 0) NAMESPACE.setUseParentHandlers(previousParentHandlers);
        }
        handler.close();
    }

    private static final class AsyncHandler extends Handler {
        private final Handler sink;
        private final ArrayBlockingQueue<String> queue;
        private final AtomicLong dropped = new AtomicLong();
        private final AtomicBoolean accepting = new AtomicBoolean(true);
        private final Thread writer;
        private final long closeMillis;

        AsyncHandler(Handler sink, int queueSize, long closeMillis) {
            this.sink = sink;
            this.closeMillis = closeMillis;
            queue = new ArrayBlockingQueue<>(queueSize);
            setLevel(java.util.logging.Level.ALL);
            writer = Thread.ofPlatform().name("jasper-log-writer").daemon().unstarted(this::writeLoop);
        }

        void start() {
            writer.start();
        }

        @Override public void publish(LogRecord record) {
            if (!accepting.get() || !isLoggable(record)) return;
            String encoded = encode(record);
            if (!queue.offer(encoded)) dropped.incrementAndGet();
        }

        private void writeLoop() {
            try {
                while (accepting.get() || !queue.isEmpty()) {
                    String encoded = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (encoded != null) write(encoded);
                    if (queue.isEmpty()) writeDropped();
                }
                writeDropped();
            } catch (InterruptedException interrupted) {
                while (!queue.isEmpty()) write(queue.poll());
                writeDropped();
                Thread.currentThread().interrupt();
            } finally {
                try { sink.flush(); }
                finally { sink.close(); }
            }
        }

        private void writeDropped() {
            long count = dropped.getAndSet(0);
            if (count > 0) write(Instant.now() + " WARNING dev.jasper " + count + " log records dropped\n");
        }

        private void write(String encoded) {
            LogRecord copy = new LogRecord(java.util.logging.Level.INFO, encoded);
            sink.publish(copy);
        }

        @Override public void flush() {
            // Producers never perform disk I/O; the owned writer flushes during close.
        }

        @Override public void close() {
            if (!accepting.compareAndSet(true, false)) return;
            writer.interrupt();
            try {
                writer.join(closeMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String encode(LogRecord record) {
        StringBuilder text = new StringBuilder(1024);
        text.append(Instant.ofEpochMilli(record.getMillis())).append(' ')
            .append(record.getLevel().getName()).append(' ')
            .append(safeLogger(record.getLoggerName())).append(' ')
            .append(safeDescription(record.getMessage()));
        Throwable thrown = record.getThrown();
        int frames = 0;
        int causes = 0;
        while (thrown != null && causes++ < 8 && frames < 24) {
            text.append("\n  ").append(thrown.getClass().getName());
            for (StackTraceElement frame : thrown.getStackTrace()) {
                if (frames++ >= 24) break;
                text.append("\n    at ").append(frame.getClassName()).append('.').append(frame.getMethodName())
                    .append('(').append(frame.getFileName() == null ? "Unknown Source" : frame.getFileName());
                if (frame.getLineNumber() >= 0) text.append(':').append(frame.getLineNumber());
                text.append(')');
            }
            thrown = thrown.getCause();
        }
        text.append('\n');
        return boundUtf8(text.toString(), MAX_RECORD_BYTES);
    }

    private static String safeLogger(String name) {
        return name != null && name.startsWith("dev.jasper") ? name : "dev.jasper";
    }

    private static String safeDescription(String message) {
        if (message == null) return "Application operation failed";
        StringBuilder safe = new StringBuilder(Math.min(message.length(), 512));
        for (int index = 0; index < message.length() && safe.length() < 512; index++) {
            char value = message.charAt(index);
            safe.append(value == '\n' || value == '\r' || value == '\t' || Character.isISOControl(value) ? ' ' : value);
        }
        return safe.toString();
    }

    private static String boundUtf8(String value, int maximum) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maximum) return value;
        int end = maximum - 1;
        while (end > 0 && (bytes[end] & 0xc0) == 0x80) end--;
        return new String(bytes, 0, end, StandardCharsets.UTF_8) + "\n";
    }

    private static final class EncodedFormatter extends Formatter {
        @Override public String format(LogRecord record) {
            return record.getMessage();
        }
    }

    /** Keeps privacy routing active without retaining records or touching disk after file setup fails. */
    private static final class DiscardingHandler extends Handler {
        @Override public void publish(LogRecord record) {}
        @Override public void flush() {}
        @Override public void close() {}
    }

    private static final class FixedErrorManager extends ErrorManager {
        private final AtomicBoolean reported = new AtomicBoolean();

        @Override public void error(String message, Exception exception, int code) {
            if (reported.compareAndSet(false, true)) System.err.println("Jasper could not write diagnostics.");
        }
    }
}
