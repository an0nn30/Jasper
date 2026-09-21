package dev.jasper.terminal;

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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/** A program running in a pseudo-terminal, emulated by JediTerm on a dedicated reader thread. */
public final class TerminalSession implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(TerminalSession.class.getName());

    /** Callbacks arrive on the session's reader thread. */
    public interface Listener {
        default void screenChanged() {
        }

        default void titleChanged(String title) {
        }

        default void bell() {
        }

        default void workingDirectoryChanged(Path directory) {
        }

        /** The scrollback was erased, so absolute rows held from before now name different lines (or none). */
        default void scrollbackReset() {
        }

        /** The active terminal buffer changed, so absolute rows from the previous buffer are no longer meaningful. */
        default void alternateBufferChanged(boolean alternate) {
        }

        /**
         * The shell marked the start of a command it is about to run (OSC 133 C). Reader thread, and
         * fired outside the buffer lock. Exactly one start per {@link #commandExecuted}, except when
         * the pane is closed mid-command — then there is a start and no finish, which is precisely
         * the case a running notice exists to show.
         */
        default void commandStarted(String command) {
        }

        /**
         * The shell ran a command it marked with OSC 133 B/C (and D when it sends one). Reader thread.
         * {@code duration} is measured from the command-start mark. A cycle that never saw one has no
         * command to report either, so it fires no callback at all rather than one with a zero duration.
         */
        default void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory,
                                     Duration duration) {
        }
    }

    private final TtyConnector connector;
    private final PtyConnector pty;
    private final JediCellReader cells = new JediCellReader();
    private final TerminalTextBuffer buffer;
    private final JediTerminal terminal;
    private final SessionDisplay display;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Integer> exit = new CompletableFuture<>();
    private final AbsoluteRowState rowState = new AbsoluteRowState();
    private final BufferQueries queries;
    private ShellCommandTracker shell;
    private volatile int columns;
    private volatile int rows;

    /** Starts {@code command} in a new pseudo-terminal and begins emulating its output. */
    public static TerminalSession start(List<String> command, Map<String, String> environment, Path workingDirectory,
                                        int columns, int rows, int scrollback) throws IOException {
        return start(new SessionLaunchOptions(command, environment, workingDirectory,
            new GridSize(columns, rows), scrollback));
    }

    /** Starts a validated launch description and owns cleanup if session construction fails. */
    public static TerminalSession start(SessionLaunchOptions options) throws IOException {
        return PtySessionFactory.start(Objects.requireNonNull(options, "options"));
    }

    TerminalSession(TtyConnector connector, int columns, int rows, int scrollback) {
        this(connector, columns, rows, scrollback, System::nanoTime);
    }

    /** {@code clock} supplies monotonic nanoseconds; tests drive it instead of sleeping. */
    TerminalSession(TtyConnector connector, int columns, int rows, int scrollback, LongSupplier clock) {
        this.connector = new ShellIntegrationConnector(connector);
        this.pty = connector instanceof PtyConnector value ? value : null;
        this.columns = columns;
        this.rows = rows;
        StyleState styleState = new StyleState();
        buffer = new TerminalTextBuffer(columns, rows, styleState, scrollback);
        display = new SessionDisplay(
            title -> listeners.forEach(l -> l.titleChanged(title)),
            () -> listeners.forEach(Listener::bell),
            () -> listeners.forEach(Listener::screenChanged),
            alternate -> {
                rowState.invalidate();
                if (shell != null) shell.discardUnusedPayload();
                listeners.forEach(l -> l.alternateBufferChanged(alternate));
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
        buffer.addModelListener(() -> listeners.forEach(Listener::screenChanged));
        queries = new BufferQueries(buffer, terminal, display, rowState, cells);
        shell = new ShellCommandTracker(clock, queries::cursor, queries::captureCommand, queries::recordPrompt,
            directory -> listeners.forEach(l -> l.workingDirectoryChanged(directory)),
            command -> listeners.forEach(l -> l.commandStarted(command)),
            command -> listeners.forEach(l -> l.commandExecuted(command.command(), command.status(), command.directory(), command.duration())),
            () -> listeners.forEach(Listener::screenChanged), display::resetCursorShape);
        terminal.addCustomCommandListener(shell::accept);
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
                if (shell != null) shell.discardUnusedPayload();
                listeners.forEach(Listener::scrollbackReset);
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

    void startReading() {
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
        writeExitMessage(code);
        exit.complete(code);
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

    /** The directory the shell last reported with OSC 7, if any. */
    public Optional<Path> workingDirectory() {
        return shell.workingDirectory();
    }

    /** True once the shell has reported its first prompt (mark A) through Jasper's shell integration. */
    public boolean shellIntegrationDetected() {
        return shell.detected();
    }

    @Override
    public void close() {
        connector.close();
    }

    byte[] codeForKey(int keyCode, int modifiers) {
        return terminal.getCodeForKey(keyCode, modifiers);
    }

    SessionDisplay display() {
        return display;
    }

    /** Monotonic generation for history, reflow and alternate-buffer changes that invalidate absolute rows. */
    long absoluteRowEpoch() {
        return rowState.epoch();
    }

    /** Absolute rows of the prompts the shell marked with OSC 133;A that are still in the scrollback, oldest first. */
    List<Long> promptRows() {
        return rowState.prompts();
    }

    /** Whether the program asked for mouse reports, so clicks go to it instead of to local selection. */
    boolean mouseReporting() {
        return display.mouseMode() != MouseMode.MOUSE_REPORTING_NONE;
    }

    boolean usingAlternateBuffer() {
        buffer.lock();
        try {
            return buffer.isUsingAlternateBuffer();
        } finally {
            buffer.unlock();
        }
    }

    /** Reports a mouse event at a screen cell, clamped onto the screen; false when the program did not ask for it. */
    boolean reportMouse(int column, int row, MouseInput event) {
        if (!mouseReporting()) {
            return false;
        }
        int x = Math.max(0, Math.min(columns - 1, column));
        int y = Math.max(0, Math.min(rows - 1, row));
        return terminal.onMouseEvent(x, y, jediEvent(event), new MouseEventProcessingSettings(true, usingAlternateBuffer(), false));
    }

    /** Pastes text: newlines become carriage returns, wrapped in bracketed-paste markers when the program asked. */
    void paste(String text) {
        String normalized = text.replace("\r\n", "\r").replace('\n', '\r');
        if (display.bracketedPaste()) {
            write("\033[200~" + normalized.replace("\033[201~", "") + "\033[201~");
        } else {
            write(normalized);
        }
    }

    ScreenSnapshot snapshot() { return queries.snapshot(); }

    /** The rows starting at an absolute top row (clamped to the scrollback), or the live screen for FOLLOW_OUTPUT. */
    ScreenSnapshot snapshot(long topRow) { return queries.snapshot(topRow); }

    MouseGeometry mouseGeometry(long requestedTopRow) { return queries.mouseGeometry(requestedTopRow); }

    /** Cursor eligibility without copying terminal lines; called only while a view can actually blink. */
    boolean blinkingCursorInView(long requestedTopRow, boolean configuredBlink) { return queries.blinkingCursorInView(requestedTopRow, configuredBlink); }

    /** The text of the line at an absolute row, or null when it is no longer in the scrollback. */
    String lineText(long absoluteRow) { return queries.lineText(absoluteRow); }

    /** The text of a selection: soft-wrapped rows joined, wide characters whole, trailing spaces trimmed. */
    String text(Selection selection) { return queries.text(selection); }

    List<SelectedCells> selectedLiveCells(Selection selection) { return queries.selectedLiveCells(selection); }

    boolean selectionUnchanged(List<SelectedCells> cells) { return queries.selectionUnchanged(cells); }

    /** Validation and extraction share a lock so Copy cannot pick up an overwrite between the two. */
    Optional<String> selectedText(Selection selection, List<SelectedCells> cells) { return queries.selectedText(selection, cells); }

    /**
     * Every match in the scrollback and on screen, oldest first; an empty query finds nothing. On the alternate
     * screen only its own rows are searched, since the scrollback behind it is not what the user is looking at.
     */
    List<TerminalSearch.Match> search(String query, boolean regex, boolean caseSensitive) { return queries.search(query, regex, caseSensitive); }

    /**
     * The link at an absolute row and column: an OSC 8 hyperlink, or else a URL written in the text. A program chooses
     * an OSC 8 target freely, so only web and mail schemes are opened; a cell whose OSC 8 target has any other scheme
     * has no link at all, whatever its text says.
     */
    Optional<String> linkAt(long absoluteRow, int column) { return queries.linkAt(absoluteRow, column); }

    /** The word at an absolute row and column, as a stream selection. */
    Selection wordSelection(long row, int column) { return queries.wordSelection(row, column); }

    /** A logical line, bounded by {@link LogicalLine}'s extreme-line fallback, always including the clicked row. */
    Selection lineSelection(long row) { return queries.lineSelection(row); }
}
