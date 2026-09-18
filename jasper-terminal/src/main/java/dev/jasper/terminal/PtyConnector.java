package dev.jasper.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.pty4j.unix.UnixPtyProcess;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Connects JediTerm to a pty4j process: UTF-8 output in, bytes out, window size changes. */
final class PtyConnector implements TtyConnector {
    private static final System.Logger LOG = System.getLogger(PtyConnector.class.getName());
    private static final long CLOSE_GRACE_MILLIS = 500;

    private final PtyProcess process;
    private final String initialProgram;
    private final boolean loginShell;
    private final Reader reader;
    private final OutputStream input;
    private final AtomicBoolean closing = new AtomicBoolean();

    PtyConnector(PtyProcess process) { this(process, List.of()); }

    PtyConnector(PtyProcess process, List<String> command) {
        this.process = process;
        this.initialProgram = command.isEmpty() ? "" : Path.of(command.getFirst()).getFileName().toString();
        this.loginShell = List.of("zsh", "bash", "fish", "sh", "ksh").contains(initialProgram)
            && loginOption(command);
        this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
        this.input = process.getOutputStream();
    }

    private static boolean loginOption(List<String> command) {
        boolean login = false;
        for (int i = 1; i < command.size(); i++) {
            String option = command.get(i);
            if (option.equals("--") || option.equals("-") || !option.startsWith("-")) break;
            if (option.equals("--login")) login = true;
            else if (option.equals("--rcfile") || option.equals("--init-file")) i++;
            else if (!option.startsWith("--")) {
                login |= option.indexOf('l') > 0;
                if (option.indexOf('c') > 0) break;
                if (option.endsWith("o") || option.endsWith("O")) i++;
            }
        }
        return login;
    }

    /** Native process metadata only; never starts a helper process or reads terminal output. */
    Optional<String> foregroundJob() {
        if (closing.get() || !process.isAlive() || !(process instanceof UnixPtyProcess unix)) return Optional.empty();
        try {
            Function function = ForegroundGroup.GET;
            if (function == null) return Optional.empty();
            int group = function.invokeInt(new Object[]{unix.getPty().getMasterFD()});
            if (group <= 0 || closing.get()) return Optional.empty();
            return ProcessHandle.of(group).flatMap(handle -> handle.info().command()).map(command -> {
                String name = Path.of(command).getFileName().toString();
                return group == process.pid() && loginShell && name.equals(initialProgram) ? "-" + name : name;
            });
        } catch (RuntimeException | LinkageError unavailable) {
            // Unsupported OS/runtime, a disappearing process, or an already closed terminal.
            return Optional.empty();
        }
    }

    private static final class ForegroundGroup {
        static final Function GET = find();
        private static Function find() {
            try { return Platform.isWindows() ? null : NativeLibrary.getInstance(Platform.C_LIBRARY_NAME).getFunction("tcgetpgrp"); }
            catch (RuntimeException | LinkageError unavailable) { return null; }
        }
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
