package dev.moray.app;

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

/** Process-local, bounded diagnostics for Moray-owned loggers. */
final class AppLog implements AutoCloseable {
    private static final int DEFAULT_FILE_BYTES = 1024 * 1024;
    private static final int DEFAULT_FILE_COUNT = 3;
    private static final int DEFAULT_QUEUE_SIZE = 256;
    private static final int MAX_RECORD_BYTES = 8 * 1024;
    private static final long CLOSE_MILLIS = 2_000;
    private static final Logger NAMESPACE = Logger.getLogger("dev.moray");

    private final AsyncHandler handler;
    private final boolean enabled;
    private final AtomicBoolean closed = new AtomicBoolean();

    private AppLog(AsyncHandler handler, boolean enabled) {
        this.handler = handler;
        this.enabled = enabled;
    }

    static AppLog open(Path logs) {
        return open(logs, DEFAULT_FILE_BYTES, DEFAULT_FILE_COUNT, DEFAULT_QUEUE_SIZE);
    }

    static AppLog open(Path logs, int fileBytes, int fileCount, int queueSize) {
        FileHandler files = null;
        AsyncHandler handler = null;
        try {
            if (fileBytes <= 0 || fileCount <= 0 || queueSize <= 0) throw new IllegalArgumentException();
            Files.createDirectories(logs);
            files = new FileHandler(logs.resolve("moray-%u-%g.log").toString(), fileBytes, fileCount, true);
            files.setEncoding(StandardCharsets.UTF_8.name());
            files.setFormatter(new EncodedFormatter());
            files.setErrorManager(new FixedErrorManager());
            handler = new AsyncHandler(files, queueSize);
            NAMESPACE.addHandler(handler);
            handler.start();
            return new AppLog(handler, true);
        } catch (IOException | RuntimeException failure) {
            if (handler != null) NAMESPACE.removeHandler(handler);
            if (handler != null) handler.close();
            else if (files != null) files.close();
            System.err.println("Moray diagnostics are unavailable.");
            return new AppLog(null, false);
        }
    }

    boolean enabled() {
        return enabled;
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true) || handler == null) return;
        NAMESPACE.removeHandler(handler);
        handler.close();
    }

    private static final class AsyncHandler extends Handler {
        private final FileHandler files;
        private final ArrayBlockingQueue<String> queue;
        private final AtomicLong dropped = new AtomicLong();
        private final AtomicBoolean accepting = new AtomicBoolean(true);
        private final Thread writer;

        AsyncHandler(FileHandler files, int queueSize) {
            this.files = files;
            queue = new ArrayBlockingQueue<>(queueSize);
            setLevel(java.util.logging.Level.ALL);
            writer = Thread.ofPlatform().name("moray-log-writer").daemon().unstarted(this::writeLoop);
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
            }
        }

        private void writeDropped() {
            long count = dropped.getAndSet(0);
            if (count > 0) write(Instant.now() + " WARNING dev.moray " + count + " log records dropped\n");
        }

        private void write(String encoded) {
            LogRecord copy = new LogRecord(java.util.logging.Level.INFO, encoded);
            files.publish(copy);
        }

        @Override public void flush() {
            // Producers never perform disk I/O; the owned writer flushes during close.
        }

        @Override public void close() {
            if (!accepting.compareAndSet(true, false)) return;
            writer.interrupt();
            try {
                writer.join(CLOSE_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            files.flush();
            files.close();
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
        return name != null && name.startsWith("dev.moray") ? name : "dev.moray";
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

    private static final class FixedErrorManager extends ErrorManager {
        private final AtomicBoolean reported = new AtomicBoolean();

        @Override public void error(String message, Exception exception, int code) {
            if (reported.compareAndSet(false, true)) System.err.println("Moray could not write diagnostics.");
        }
    }
}
