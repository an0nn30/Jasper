package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalOutputStream;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** A program running in a pseudo-terminal, emulated by JediTerm on a dedicated reader thread. */
public final class TerminalSession implements AutoCloseable {

    /** Callbacks arrive on the session's reader thread. */
    public interface Listener {
        default void screenChanged() {
        }

        default void titleChanged(String title) {
        }

        default void bell() {
        }
    }

    private final TtyConnector connector;
    private final TerminalTextBuffer buffer;
    private final JediTerminal terminal;
    private final SessionDisplay display;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Integer> exit = new CompletableFuture<>();
    private volatile int columns;
    private volatile int rows;
    private volatile Thread reader;

    /** Starts {@code command} in a new pseudo-terminal and begins emulating its output. */
    public static TerminalSession start(List<String> command, Map<String, String> environment, Path workingDirectory,
                                        int columns, int rows, int scrollback) throws IOException {
        Map<String, String> env = new HashMap<>(environment);
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        PtyProcess process = new PtyProcessBuilder(command.toArray(String[]::new))
            .setEnvironment(env)
            .setDirectory(workingDirectory.toString())
            .setInitialColumns(columns)
            .setInitialRows(rows)
            .setUnixOpenTtyToPreserveOutputAfterTermination(true)
            .start();
        TerminalSession session = new TerminalSession(new PtyConnector(process), columns, rows, scrollback);
        session.startReading();
        return session;
    }

    TerminalSession(TtyConnector connector, int columns, int rows, int scrollback) {
        this.connector = connector;
        this.columns = columns;
        this.rows = rows;
        StyleState styleState = new StyleState();
        buffer = new TerminalTextBuffer(columns, rows, styleState, scrollback);
        display = new SessionDisplay(
            title -> listeners.forEach(l -> l.titleChanged(title)),
            () -> listeners.forEach(Listener::bell));
        terminal = new JediTerminal(display, buffer, styleState);
        terminal.setTerminalOutput(new TerminalOutputStream() {
            @Override
            public void sendBytes(byte[] bytes, boolean userInput) {
                write(bytes);
            }

            @Override
            public void sendString(String string, boolean userInput) {
                write(string);
            }
        });
        buffer.addModelListener(() -> listeners.forEach(Listener::screenChanged));
    }

    void startReading() {
        reader = Thread.ofPlatform().name("moray-session-reader").daemon().start(this::readLoop);
    }

    private void readLoop() {
        JediEmulator emulator = new JediEmulator(new TtyBasedArrayDataStream(connector), terminal);
        try {
            while (!Thread.currentThread().isInterrupted() && emulator.hasNext()) {
                emulator.next();
            }
        } catch (IOException endOfStream) {
            // End of output, or EIO from a macOS PTY whose child has exited.
        } catch (RuntimeException emulatorFailure) {
            emulatorFailure.printStackTrace(); // replaced by the app log in plan 4
        }
        int code;
        try {
            code = connector.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            code = -1;
        }
        exit.complete(code);
    }

    public void write(byte[] bytes) {
        try {
            connector.write(bytes);
        } catch (IOException closed) {
            // The program has exited; input is dropped.
        }
    }

    public void write(String text) {
        write(text.getBytes(StandardCharsets.UTF_8));
    }

    public void resize(int newColumns, int newRows) {
        if (newColumns == columns && newRows == rows) {
            return;
        }
        columns = newColumns;
        rows = newRows;
        TermSize size = new TermSize(newColumns, newRows);
        buffer.lock();
        try {
            terminal.resize(size, RequestOrigin.User);
        } finally {
            buffer.unlock();
        }
        connector.resize(size);
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public String title() {
        return display.title();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Completes with the exit code once the program's output has ended. */
    public CompletableFuture<Integer> exitFuture() {
        return exit.copy();
    }

    @Override
    public void close() {
        connector.close();
        Thread thread = reader;
        if (thread != null) {
            thread.interrupt();
        }
    }

    ScreenSnapshot snapshot() {
        return ScreenSnapshot.capture(buffer, terminal, display);
    }

    byte[] codeForKey(int keyCode, int modifiers) {
        return terminal.getCodeForKey(keyCode, modifiers);
    }

    SessionDisplay display() {
        return display;
    }
}
