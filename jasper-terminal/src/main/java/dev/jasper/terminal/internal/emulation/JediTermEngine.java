package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.internal.process.PtyChild;
import dev.jasper.terminal.internal.shell.CompletedCommand;
import dev.jasper.terminal.internal.text.AbsoluteRowState;
import dev.jasper.terminal.internal.text.MouseInput;

import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalOutputStream;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.emulator.mouse.MouseEventProcessingSettings;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.TextBufferChangesListener;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import dev.jasper.terminal.internal.transport.AttachedTransport;

/** A program running in a pseudo-terminal, emulated by JediTerm on a dedicated reader thread. */
public final class JediTermEngine implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(JediTermEngine.class.getName());

    private final TtyConnector connector;
    private final PtyConnector pty;
    private final AttachedTransport attached;
    private final JediCellReader cells = new JediCellReader();
    private final TerminalTextBuffer buffer;
    private final JediTerminal terminal;
    private final SessionDisplay display;
    private final Events events;
    private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
    private java.util.function.Consumer<List<String>> customCommands = args -> {};
    private Runnable discardUnusedPayload = () -> {};
    private final CompletableFuture<Integer> exit = new CompletableFuture<>();
    private final AbsoluteRowState rowState = new AbsoluteRowState();
    private final BufferQueries queries;
    private volatile int columns;
    private volatile int rows;

    /** Test construction seam; no reader starts until the composition is complete. */
    JediTermEngine(TtyConnector connector, int columns, int rows, int scrollback, Events events) {
        this.events = Objects.requireNonNull(events, "events");
        this.connector = new ShellIntegrationConnector(connector);
        this.pty = connector instanceof PtyConnector value ? value : null;
        this.attached = connector instanceof AttachedConnector value ? value.transport() : null;
        this.columns = columns;
        this.rows = rows;
        StyleState styleState = new StyleState();
        buffer = new TerminalTextBuffer(columns, rows, styleState, scrollback);
        display = new SessionDisplay(
            title -> events.titleChanged().accept(title),
            () -> events.bell().run(),
            () -> events.screenChanged().run(),
            alternate -> {
                rowState.invalidate();
                discardUnusedPayload.run();
                events.alternateBufferChanged().accept(alternate);
            });
        terminal = new JediTerminal(display, buffer, styleState) {
            @Override
            public void reset(boolean fullReset) {
                // JediTerm 3.76 clears its row storages without locking during RIS. Protect the whole
                // reset (including cursor home), never emulator.next(), which can block on PTY input.
                // The superclass initializes this buffer before invoking reset in its constructor.
                TerminalTextBuffer resetBuffer = getTerminalTextBuffer();
                resetBuffer.lock();
                try {
                    super.reset(fullReset);
                } finally {
                    resetBuffer.unlock();
                }
            }
        };
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
        buffer.addModelListener(() -> events.screenChanged().run());
        queries = new BufferQueries(buffer, terminal, display, rowState, cells);
        terminal.addCustomCommandListener(args -> customCommands.accept(args));
        // Without a filter JediTerm drops OSC 8 links; one item spanning the whole URI makes it keep them.
        terminal.setUrlHyperlinkFilter(uri -> new LinkResult(new LinkResultItem(0, uri.length(), new UriLink(uri))));
        buffer.addChangesListener(new TextBufferChangesListener() {
            @Override
            public void linesDiscardedFromHistory(List<TerminalLine> lines) {
                rowState.discard(lines.size()); // reader thread, under the buffer lock
            }

            @Override
            public void historyCleared() {
                rowState.invalidate();
                rowState.clearPrompts();
                discardUnusedPayload.run();
                events.scrollbackReset().run();
            }
        });
    }

    private static com.jediterm.core.input.MouseEvent jediEvent(MouseInput event) {
        MouseInput.Type type = event.type();
        MouseInput.Button held = event.button();
        int notches = event.wheelDirection();
        int modifiers = (event.shift() ? MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG : 0)
            | (event.alt() ? MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG : 0)
            | (event.control() ? MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG : 0);

        if (type == MouseInput.Type.WHEEL) {
            // JediTerm names these X11 buttons opposite to terminal scroll direction (4 = up, 5 = down).
            int button = notches < 0 ? MouseButtonCodes.SCROLLDOWN : MouseButtonCodes.SCROLLUP;
            return new com.jediterm.core.input.MouseWheelEvent(button, modifiers, Integer.signum(notches));
        }
        int button = switch (held) {
            case LEFT -> MouseButtonCodes.LEFT;
            case MIDDLE -> MouseButtonCodes.MIDDLE;
            case RIGHT -> MouseButtonCodes.RIGHT;
            case NONE -> MouseButtonCodes.RELEASE;
        };
        return new com.jediterm.core.input.MouseEvent(com.jediterm.core.input.MouseEvent.Type.valueOf(type.name()), button, modifiers);
    }

    public void startReading() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("session reader already started");
        if (attached != null) attached.start();
        Thread.ofPlatform().name("jasper-session-reader").daemon().start(this::readLoop);
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
            LOG.log(System.Logger.Level.ERROR, "Terminal emulation failed", emulatorFailure);
        }
        int code;
        try {
            code = connector.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            code = -1;
        }
        if (attached == null) {
            writeExitMessage(code);
            exit.complete(code);
            return;
        }
        // An attached pane's banner reports the end; a failed transport or an unknown status is not an exit code.
        Optional<String> failure = attached.failure();
        if (failure.isPresent()) exit.completeExceptionally(new IOException(failure.get()));
        else if (code == AttachedTransport.UNKNOWN_STATUS) exit.completeExceptionally(new IOException("The connection ended without an exit status"));
        else exit.complete(code);
    }

    private void writeExitMessage(int code) {
        // Reset the style first so nothing the program left (hidden, colours) applies to the message.
        char[] message = ("\033[0m\r\n[process exited with code " + code + "]").toCharArray();
        JediEmulator emulator = new JediEmulator(new ArrayTerminalDataStream(message), terminal);
        try {
            while (emulator.hasNext()) {
                emulator.next();
            }
        } catch (IOException endOfMessage) {
            // ArrayTerminalDataStream signals its end with EOF.
        }
    }

    public void write(byte[] bytes) {
        try {
            connector.write(bytes);
        } catch (IOException closed) {
            // A closed program normally rejects late input; a live one indicates an unexpected write failure.
            if (connector.isConnected()) LOG.log(System.Logger.Level.WARNING, "Terminal write failed", closed);
        }
    }

    public void write(String text) {
        write(text.getBytes(StandardCharsets.UTF_8));
    }

    public void resize(int newColumns, int newRows) {
        GridSize requested = new GridSize(newColumns, newRows);
        newColumns = requested.columns(); newRows = requested.rows();
        if (newColumns == columns && newRows == rows) {
            return;
        }
        boolean widthChanged = newColumns != columns;
        columns = newColumns;
        rows = newRows;
        TermSize size = new TermSize(newColumns, newRows);
        buffer.lock();
        try {
            terminal.resize(size, RequestOrigin.User);
            if (widthChanged) {
                rowState.invalidate();
                rowState.clearPrompts(); // JediTerm reflows soft-wrapped lines, so recorded rows now name other lines
            }
        } finally {
            buffer.unlock();
        }
        try {
            connector.resize(size);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Terminal resize failed", failure);
            throw failure;
        }
    }

    /** Clears saved history while preserving the live screen and notifying listeners that absolute rows were reset. */
    public void clearScrollback() {
        buffer.clearHistory();
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    /** Current foreground job's executable name, when the OS exposes it. Query off the EDT. */
    public Optional<String> foregroundJob() {
        return pty == null ? Optional.empty() : pty.foregroundJob();
    }

    public String title() {
        return display.title();
    }

    /** Completes with the exit code after output ends, the child wait completes, and the exit message reaches the buffer. */
    public CompletableFuture<Integer> exitFuture() {
        return exit.copy();
    }

    @Override
    public void close() {
        connector.close();
    }

    public byte[] codeForKey(int keyCode, int modifiers) {
        return terminal.getCodeForKey(keyCode, modifiers);
    }

    /** Monotonic generation for history, reflow and alternate-buffer changes that invalidate absolute rows. */
    public long absoluteRowEpoch() {
        return rowState.epoch();
    }

    /** Absolute rows of the prompts the shell marked with OSC 133;A retained in history or the live screen, oldest first. */
    public List<Long> promptRows() {
        return rowState.prompts();
    }

    /** Whether the program asked for mouse reports, so clicks go to it instead of to local selection. */
    public boolean mouseReporting() {
        return display.mouseMode() != MouseMode.MOUSE_REPORTING_NONE;
    }

    public boolean usingAlternateBuffer() {
        buffer.lock();
        try {
            return buffer.isUsingAlternateBuffer();
        } finally {
            buffer.unlock();
        }
    }

    /** Reports a mouse event at a screen cell, clamped onto the screen; false when the program did not ask for it. */
    public boolean reportMouse(int column, int row, MouseInput event) {
        if (!mouseReporting()) {
            return false;
        }
        int x = Math.max(0, Math.min(columns - 1, column));
        int y = Math.max(0, Math.min(rows - 1, row));
        return terminal.onMouseEvent(x, y, jediEvent(event), new MouseEventProcessingSettings(true, usingAlternateBuffer(), false));
    }

    /** Pastes text: newlines become carriage returns, wrapped in bracketed-paste markers when the program asked. */
    public void paste(String text) {
        String normalized = text.replace("\r\n", "\r").replace('\n', '\r');
        if (display.bracketedPaste()) {
            write("\033[200~" + normalized.replace("\033[201~", "") + "\033[201~");
        } else {
            write(normalized);
        }
    }

    /** Internal concrete callback bundle; constructed before emulator initialization.
     * @param screenChanged fast synchronous dirty notification, may hold the buffer lock
     * @param titleChanged reader-thread title notification
     * @param bell reader-thread bell notification
     * @param scrollbackReset coordinate-reset notification on causing thread
     * @param alternateBufferChanged buffer-switch notification on causing thread
     * @param workingDirectoryChanged reader-thread shell directory notification
     * @param commandStarted reader-thread command-start notification outside the capture lock
     * @param commandFinished reader-thread completed-command notification outside the capture lock
     */
    public record Events(Runnable screenChanged, java.util.function.Consumer<String> titleChanged,
        Runnable bell, Runnable scrollbackReset, java.util.function.Consumer<Boolean> alternateBufferChanged,
        java.util.function.Consumer<Path> workingDirectoryChanged, java.util.function.Consumer<String> commandStarted,
        java.util.function.Consumer<CompletedCommand> commandFinished) { }

    /** An attached connection instead of a child process. */
    public JediTermEngine(AttachedTransport transport, int columns, int rows, int scrollback, Events events) {
        this(new AttachedConnector(transport), columns, rows, scrollback, events);
    }

    public JediTermEngine(PtyChild child, int columns, int rows, int scrollback, Events events) {
        this(new PtyConnector(child), columns, rows, scrollback, events);
    }
    public void setShellHooks(java.util.function.Consumer<List<String>> customCommands, Runnable discardUnusedPayload) {
        if (started.get()) throw new IllegalStateException("shell hooks must be installed before reading");
        this.customCommands = Objects.requireNonNull(customCommands, "customCommands");
        this.discardUnusedPayload = Objects.requireNonNull(discardUnusedPayload, "discardUnusedPayload");
    }
    public BufferQueries queries() { return queries; }
    public void resetCursorShape() { display.resetCursorShape(); }
}
