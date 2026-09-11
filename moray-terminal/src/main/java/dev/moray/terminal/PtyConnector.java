package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.pty4j.unix.UnixPtyProcess;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Connects JediTerm to a pty4j process: UTF-8 output in, bytes out, window size changes. */
final class PtyConnector implements TtyConnector {
    private static final long CLOSE_GRACE_MILLIS = 500;

    private final PtyProcess process;
    private final Reader reader;
    private final OutputStream input;
    private final AtomicBoolean closing = new AtomicBoolean();

    PtyConnector(PtyProcess process) {
        this.process = process;
        this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
        this.input = process.getOutputStream();
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        input.write(bytes);
        input.flush();
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public void resize(TermSize size) {
        process.setWinSize(new WinSize(size.getColumns(), size.getRows()));
    }

    @Override
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "pty";
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        if (!process.isAlive()) {
            closeStreams();
            return;
        }
        if (process instanceof UnixPtyProcess unixProcess) {
            unixProcess.hangup();
        } else {
            process.destroy();
        }
        // Non-daemon so closing the final window cannot end the JVM before the bounded force-kill fallback runs.
        Thread.ofPlatform().name("moray-pty-close").start(() -> {
            try {
                if (!process.waitFor(CLOSE_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(CLOSE_GRACE_MILLIS, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            } finally {
                closeStreams();
            }
        });
    }

    private void closeStreams() {
        try {
            input.close();
        } catch (IOException ignored) {
            // Already closed with the PTY.
        }
        try {
            reader.close();
        } catch (IOException ignored) {
            // Already closed with the PTY.
        }
    }
}
