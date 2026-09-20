package dev.jasper.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.pty4j.unix.UnixPtyProcess;
import java.util.List;
import java.util.Optional;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns a child PTY and its streams, including bounded asynchronous shutdown. */
final class PtyChild implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(PtyChild.class.getName());
    private static final long CLOSE_GRACE_MILLIS = 500;

    private final PtyProcess process;
    private final ForegroundJobResolver foreground;
    private final Reader reader;
    private final OutputStream input;
    private final AtomicBoolean closing = new AtomicBoolean();

    PtyChild(PtyProcess process) { this(process, List.of()); }

    PtyChild(PtyProcess process, List<String> command) {
        this.process = process;
        this.foreground = new ForegroundJobResolver(process, command, closing);
        this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
        this.input = process.getOutputStream();
    }

    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    public void write(byte[] bytes) throws IOException {
        input.write(bytes);
        input.flush();
    }

    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isConnected() {
        return process.isAlive();
    }

    public void resize(int columns, int rows) {
        process.setWinSize(new WinSize(columns, rows));
    }

    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    public boolean ready() throws IOException {
        return reader.ready();
    }

    public String getName() {
        return "pty";
    }

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
        Thread.ofPlatform().name("jasper-pty-close").start(() -> {
            try {
                if (!process.waitFor(CLOSE_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(CLOSE_GRACE_MILLIS, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Terminal process cleanup failed", failure);
                throw failure;
            } finally {
                closeStreams();
            }
        });
    }

    Optional<String> foregroundJob() { return foreground.foregroundJob(); }

    static PtyChild start(SessionLaunchOptions options) throws IOException {
        PtyProcess process = new com.pty4j.PtyProcessBuilder(options.command().toArray(String[]::new))
            .setEnvironment(PtySessionFactory.environment(options.environment()))
            .setDirectory(options.workingDirectory().toString())
            .setInitialColumns(options.grid().columns())
            .setInitialRows(options.grid().rows())
            .setUnixOpenTtyToPreserveOutputAfterTermination(true)
            .start();
        try {
            return new PtyChild(process, options.command());
        } catch (RuntimeException | Error failure) {
            try { process.destroyForcibly(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void closeStreams() {
        try {
            input.close();
        } catch (IOException failure) {
            if (process.isAlive()) LOG.log(System.Logger.Level.WARNING, "Terminal input cleanup failed", failure);
        }
        try {
            reader.close();
        } catch (IOException failure) {
            if (process.isAlive()) LOG.log(System.Logger.Level.WARNING, "Terminal output cleanup failed", failure);
        }
    }
}
