package dev.jasper.terminal;

import com.jediterm.core.input.MouseEvent;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ArrayTerminalDataStream;
import com.jediterm.terminal.HyperlinkStyle;
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
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** A program running in a pseudo-terminal, emulated by JediTerm on a dedicated reader thread. */
public final class TerminalSession implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(TerminalSession.class.getName());
    /** Schemes an OSC 8 hyperlink may open; anything else could launch an application or a custom handler. */
    private static final Set<String> OSC8_SCHEMES = Set.of("http", "https", "ftp", "mailto");
    /** Longer than this is not a command line; ShellHistoryParser bounds its own lines the same way. */
    private static final int MAX_COMMAND_BYTES = 16 * 1024;

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
         * The shell ran a command it marked with OSC 133 B/C (and D when it sends one). Reader thread.
         * {@code duration} is measured from the command-start mark. A cycle that never saw one has no
         * command to report either, so it fires no callback at all rather than one with a zero duration.
         */
        default void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory,
                                     Duration duration) {
        }
    }

    private final LongSupplier clock;
    private final TtyConnector connector;
    private final TerminalTextBuffer buffer;
    private final JediTerminal terminal;
    private final SessionDisplay display;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Integer> exit = new CompletableFuture<>();
    private final List<Long> promptRows = new CopyOnWriteArrayList<>();
    /** Generation of resets that make previously captured absolute rows obsolete. */
    private final AtomicLong absoluteRowEpoch = new AtomicLong();
    /** Lines dropped off the top of the scrollback so far; the base of absolute row numbers. */
    private volatile long discardedLines;
    private volatile Path workingDirectory;
    private volatile int columns;
    private volatile int rows;
    /** Set on the reader thread at the first prompt mark, read on the Event Dispatch Thread. */
    private volatile boolean shellIntegrationDetected;
    // Reader thread only: tracks the command typed between OSC 133 B and C.
    private long commandStartRow = -1;
    private int commandStartColumn;
    private String pendingCommand;
    private String pendingCommandText;
    /**
     * nanoTime at the command-start mark. Only meaningful while {@code pendingCommand} is set, which
     * is the same moment it is written — a zero here is a real reading, not a "never started" flag.
     */
    private long commandStartedAt;

    /** Starts {@code command} in a new pseudo-terminal and begins emulating its output. */
    public static TerminalSession start(List<String> command, Map<String, String> environment, Path workingDirectory,
                                        int columns, int rows, int scrollback) throws IOException {
        GridSize initial = new GridSize(columns, rows);
        columns = initial.columns(); rows = initial.rows();
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
        this(connector, columns, rows, scrollback, System::nanoTime);
    }

    /** {@code clock} supplies monotonic nanoseconds; tests drive it instead of sleeping. */
    TerminalSession(TtyConnector connector, int columns, int rows, int scrollback, LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.connector = new ShellIntegrationConnector(connector);
        this.columns = columns;
        this.rows = rows;
        StyleState styleState = new StyleState();
        buffer = new TerminalTextBuffer(columns, rows, styleState, scrollback);
        display = new SessionDisplay(
            title -> listeners.forEach(l -> l.titleChanged(title)),
            () -> listeners.forEach(Listener::bell),
            () -> listeners.forEach(Listener::screenChanged),
            alternate -> {
                absoluteRowEpoch.incrementAndGet();
                pendingCommandText = null;
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
        terminal.addCustomCommandListener(this::onCustomCommand);
        // Without a filter JediTerm drops OSC 8 links; one item spanning the whole URI makes it keep them.
        terminal.setUrlHyperlinkFilter(uri -> new LinkResult(new LinkResultItem(0, uri.length(), new UriLink(uri))));
        buffer.addChangesListener(new TextBufferChangesListener() {
            @Override
            public void linesDiscardedFromHistory(List<TerminalLine> lines) {
                discardedLines += lines.size(); // reader thread, under the buffer lock
                promptRows.removeIf(row -> row < discardedLines);
            }

            @Override
            public void historyCleared() {
                absoluteRowEpoch.incrementAndGet();
                promptRows.clear();
                pendingCommandText = null;
                listeners.forEach(Listener::scrollbackReset);
            }
        });
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
                absoluteRowEpoch.incrementAndGet();
                promptRows.clear(); // JediTerm reflows soft-wrapped lines, so recorded rows now name other lines
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
        return Optional.ofNullable(workingDirectory);
    }

    /** True once the shell has reported its first prompt (mark A) through Jasper's shell integration. */
    public boolean shellIntegrationDetected() {
        return shellIntegrationDetected;
    }

    @Override
    public void close() {
        connector.close();
    }

    ScreenSnapshot snapshot() {
        return snapshot(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    /** The rows starting at an absolute top row (clamped to the scrollback), or the live screen for FOLLOW_OUTPUT. */
    ScreenSnapshot snapshot(long topRow) {
        buffer.lock();
        try {
            return ScreenSnapshot.capture(buffer, terminal, display, discardedLines, topRow);
        } finally {
            buffer.unlock();
        }
    }

    /** Geometry only: mouse reports do not need copied lines or cursor/style data. */
    record MouseGeometry(int width, int height, long firstRow, int scrollOffset, boolean alternateBuffer) { }

    MouseGeometry mouseGeometry(long requestedTopRow) {
        buffer.lock();
        try {
            int history = buffer.getHistoryLinesCount();
            boolean alternate = buffer.isUsingAlternateBuffer();
            long liveTop = discardedLines + history;
            int offset = requestedTopRow == ScreenSnapshot.FOLLOW_OUTPUT ? 0
                : (int) Math.max(0, Math.min(alternate ? 0 : history, liveTop - requestedTopRow));
            return new MouseGeometry(buffer.getWidth(), buffer.getHeight(), liveTop - offset, offset, alternate);
        } finally {
            buffer.unlock();
        }
    }

    /** Cursor eligibility without copying terminal lines; called only while a view can actually blink. */
    boolean blinkingCursorInView(long requestedTopRow, boolean configuredBlink) {
        buffer.lock();
        try {
            if (!display.cursorVisible() || !CursorStyle.effectiveBlink(display.cursorShape(), configuredBlink)) {
                return false;
            }
            int history = buffer.getHistoryLinesCount();
            long liveTop = discardedLines + history;
            long offset = requestedTopRow == ScreenSnapshot.FOLLOW_OUTPUT ? 0
                : Math.max(0, Math.min(buffer.isUsingAlternateBuffer() ? 0 : history, liveTop - requestedTopRow));
            long row = terminal.getCursorY() - 1L + offset;
            // Like TerminalPainter, pin a pending-wrap cursor to the final cell.
            int column = Math.min(terminal.getCursorX() - 1, buffer.getWidth() - 1);
            return row >= 0 && row < buffer.getHeight() && column >= 0;
        } finally {
            buffer.unlock();
        }
    }

    byte[] codeForKey(int keyCode, int modifiers) {
        return terminal.getCodeForKey(keyCode, modifiers);
    }

    SessionDisplay display() {
        return display;
    }

    /** Monotonic generation for history, reflow and alternate-buffer changes that invalidate absolute rows. */
    long absoluteRowEpoch() {
        return absoluteRowEpoch.get();
    }

    /** Absolute rows of the prompts the shell marked with OSC 133;A that are still in the scrollback, oldest first. */
    List<Long> promptRows() {
        long oldest = discardedLines;
        return promptRows.stream().filter(row -> row >= oldest).toList();
    }

    /** The text of the line at an absolute row, or null when it is no longer in the scrollback. */
    String lineText(long absoluteRow) {
        buffer.lock();
        try {
            TerminalLine line = lineAtLocked(absoluteRow);
            return line == null ? null : line.getText();
        } finally {
            buffer.unlock();
        }
    }

    /** The text of a selection: soft-wrapped rows joined, wide characters whole, trailing spaces trimmed. */
    String text(Selection selection) {
        buffer.lock();
        try {
            return SelectionText.extract(selection, this::lineAtLocked, buffer.getWidth());
        } finally {
            buffer.unlock();
        }
    }

    /** Only the selected intersection with the live grid is retained, never a copy of selected scrollback. */
    record SelectedCells(long row, int column, String cells) { }

    List<SelectedCells> selectedLiveCells(Selection selection) {
        buffer.lock();
        try {
            int width = buffer.getWidth();
            long liveTop = absoluteRow(0);
            long first = Math.max(liveTop, selection.startRow());
            long last = Math.min(liveTop + buffer.getHeight() - 1, selection.endRow());
            List<SelectedCells> cells = new ArrayList<>();
            char[] chars = new char[width];
            for (long row = first; row <= last; row++) {
                RunBuilder.readCells(lineAtLocked(row), width, chars, null);
                int[] columns = SelectionText.wholeCharacterColumns(selection.columnsOn(row, width), chars);
                if (columns[0] <= columns[1]) {
                    cells.add(new SelectedCells(row, columns[0], new String(chars, columns[0], columns[1] - columns[0] + 1)));
                }
            }
            return List.copyOf(cells);
        } finally {
            buffer.unlock();
        }
    }

    boolean selectionUnchanged(List<SelectedCells> cells) {
        buffer.lock();
        try {
            return selectionUnchangedLocked(cells);
        } finally {
            buffer.unlock();
        }
    }

    /** Validation and extraction share a lock so Copy cannot pick up an overwrite between the two. */
    Optional<String> selectedText(Selection selection, List<SelectedCells> cells) {
        buffer.lock();
        try {
            return selectionUnchangedLocked(cells)
                ? Optional.of(SelectionText.extract(selection, this::lineAtLocked, buffer.getWidth())) : Optional.empty();
        } finally {
            buffer.unlock();
        }
    }

    private boolean selectionUnchangedLocked(List<SelectedCells> cells) {
        if (cells.isEmpty()) return true;
        int width = buffer.getWidth();
        char[] chars = new char[width];
        for (SelectedCells selected : cells) {
            TerminalLine line = lineAtLocked(selected.row());
            if (line == null || selected.column() + selected.cells().length() > width) return false;
            RunBuilder.readCells(line, width, chars, null);
            for (int i = 0; i < selected.cells().length(); i++) {
                if (chars[selected.column() + i] != selected.cells().charAt(i)) return false;
            }
        }
        return true;
    }

    /**
     * Every match in the scrollback and on screen, oldest first; an empty query finds nothing. On the alternate
     * screen only its own rows are searched, since the scrollback behind it is not what the user is looking at.
     */
    List<TerminalSearch.Match> search(String query, boolean regex, boolean caseSensitive) {
        if (query.isEmpty()) {
            return List.of();
        }
        Pattern pattern = TerminalSearch.pattern(query, regex, caseSensitive);
        int history;
        long firstRow;
        int width;
        List<TerminalLine> lines;
        buffer.lock();
        try {
            history = buffer.isUsingAlternateBuffer() ? 0 : buffer.getHistoryLinesCount();
            firstRow = absoluteRow(-history);
            width = buffer.getWidth();
            lines = new ArrayList<>(history + buffer.getHeight());
            for (int row = -history; row < buffer.getHeight(); row++) {
                lines.add(buffer.getLine(row).copy());
            }
        } finally {
            buffer.unlock();
        }
        // Run regex matching outside the lock so a slow pattern cannot stall the reader thread
        return TerminalSearch.find(pattern, firstRow, lines, width);
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
    boolean reportMouse(int column, int row, MouseEvent event) {
        if (!mouseReporting()) {
            return false;
        }
        int x = Math.max(0, Math.min(columns - 1, column));
        int y = Math.max(0, Math.min(rows - 1, row));
        return terminal.onMouseEvent(x, y, event, new MouseEventProcessingSettings(true, usingAlternateBuffer(), false));
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

    /**
     * The link at an absolute row and column: an OSC 8 hyperlink, or else a URL written in the text. A program chooses
     * an OSC 8 target freely, so only web and mail schemes are opened; a cell whose OSC 8 target has any other scheme
     * has no link at all, whatever its text says.
     */
    Optional<String> linkAt(long absoluteRow, int column) {
        buffer.lock();
        try {
            if (column < 0 || column >= buffer.getWidth()) return Optional.empty();
            TerminalLine line = lineAtLocked(absoluteRow);
            if (line == null) {
                return Optional.empty();
            }
            if (column < line.length()
                && line.getStyleAt(column) instanceof HyperlinkStyle hyperlink
                && hyperlink.getLinkInfo() instanceof UriLink link) {
                return openableScheme(link.uri()) ? Optional.of(link.uri()) : Optional.empty();
            }
            return urlAcrossWrappedRows(absoluteRow, column);
        } finally {
            buffer.unlock();
        }
    }

    private static boolean openableScheme(String uri) {
        int colon = uri.indexOf(':');
        String scheme = colon < 0 ? "" : uri.substring(0, colon).toLowerCase(Locale.ROOT);
        return OSC8_SCHEMES.contains(scheme);
    }

    /**
     * A plain-text URL search that follows soft wraps: joins the wrapped screen rows around {@code absoluteRow}
     * into one logical line (the same wrap-walking {@link #lineSelection} uses) before searching, so a URL split
     * across a wrap boundary is still recognized as one link. Call with the buffer lock held.
     */
    private Optional<String> urlAcrossWrappedRows(long absoluteRow, int column) {
        int width = buffer.getWidth();
        LogicalLine range = LogicalLine.around(absoluteRow, width, this::lineAtLocked);
        if (range.truncated()) return Optional.empty();
        // The shared traversal bounds this multiplication to at most MAX_CELLS.
        int cells = range.rowCount() * width;
        StringBuilder text = new StringBuilder(cells);
        int[] columns = new int[cells];
        int[] lastColumns = new int[cells];
        for (int rowIndex = 0; rowIndex < range.rowCount(); rowIndex++) {
            TerminalLine line = lineAtLocked(range.firstRow() + rowIndex);
            RowText rowText = RowText.of(line, width);
            int offset = rowIndex * width;
            for (int i = 0; i < rowText.text().length(); i++) {
                int index = text.length();
                text.append(rowText.text().charAt(i));
                columns[index] = offset + rowText.columns()[i];
                lastColumns[index] = offset + rowText.lastColumns()[i];
            }
        }
        int virtualColumn = (int) ((absoluteRow - range.firstRow()) * width) + column;
        RowText combined = new RowText(text.toString(), columns, lastColumns);
        return LinkDetector.urlAt(combined, virtualColumn);
    }

    /** The word at an absolute row and column, as a stream selection. */
    Selection wordSelection(long row, int column) {
        buffer.lock();
        try {
            TerminalLine line = lineAtLocked(row);
            if (line == null) {
                return Selection.at(row, column, false);
            }
            int[] word = WordBoundaries.wordAt(line, buffer.getWidth(), column);
            return new Selection(row, word[0], row, word[1], false);
        } finally {
            buffer.unlock();
        }
    }

    /** A logical line, bounded by {@link LogicalLine}'s extreme-line fallback, always including the clicked row. */
    Selection lineSelection(long row) {
        buffer.lock();
        try {
            LogicalLine range = LogicalLine.around(row, buffer.getWidth(), this::lineAtLocked);
            return new Selection(range.firstRow(), 0, range.lastRow(), range.columns() - 1, false);
        } finally {
            buffer.unlock();
        }
    }

    /**
     * The absolute row of a buffer row (0 = top of the live screen, negative = scrollback). An absolute row stays
     * attached to its line while output scrolls. Call with the buffer lock held.
     */
    private long absoluteRow(int bufferRow) {
        return discardedLines + buffer.getHistoryLinesCount() + bufferRow;
    }

    /** The line at an absolute row, or null outside the scrollback and screen. Call with the buffer lock held. */
    private TerminalLine lineAtLocked(long absoluteRow) {
        int history = buffer.getHistoryLinesCount();
        long bufferRow = absoluteRow - discardedLines - history;
        if (bufferRow < -history || bufferRow >= buffer.getHeight()) {
            return null;
        }
        return buffer.getLine((int) bufferRow);
    }

    private void onCustomCommand(List<String> args) {
        if (args.size() < 2 || !"jasper".equals(args.get(0))) {
            return;
        }
        switch (args.get(1)) {
            case "cwd" -> directoryFromUri(String.join(";", args.subList(2, args.size()))).ifPresent(directory -> {
                workingDirectory = directory;
                listeners.forEach(l -> l.workingDirectoryChanged(directory));
            });
            case "cmd" -> pendingCommandText = args.size() > 2
                ? decodeCommand(String.join(";", args.subList(2, args.size()))) : null;
            case "mark" -> {
                String mark = args.size() > 2 ? args.get(2) : "";
                switch (mark) {
                    case "A" -> {
                        // A shell that emits its own A (fish 4) must not flush the cycle early:
                        // the command would be reported without the status its own D carries.
                        if (!shellIntegrationDetected) {
                            // The mark draws nothing, so without this the owner only learns that
                            // integration is live when the prompt that follows happens to repaint.
                            shellIntegrationDetected = true;
                            listeners.forEach(Listener::screenChanged);
                        }
                        // Only the flush is conditional. A prompt never sits inside a cycle, so a
                        // half-started capture and a payload no C consumed are dropped either way;
                        // a prompt redrawn in place (zle reset-prompt) repeats the row and would
                        // otherwise leave both behind for the next C to pick up.
                        if (recordPrompt()) flushPendingCommand(OptionalInt.empty());
                        commandStartRow = -1;
                        pendingCommandText = null;
                    }
                    case "B" -> markCommandStart();
                    case "C" -> captureCommand();
                    case "D" -> flushPendingCommand(exitStatus(args));
                    default -> {
                        // Other FinalTerm marks carry nothing Jasper tracks.
                    }
                }
            }
            case "cursor-reset" -> display.resetCursorShape();
            default -> {
                // A command from a newer Jasper shell-integration script; nothing to do.
            }
        }
    }

    /** Records the prompt row; false when this A repeats the row Jasper already marked. */
    private boolean recordPrompt() {
        buffer.lock();
        try {
            long row = absoluteRow(terminal.getCursorY() - 1);
            if (!promptRows.isEmpty() && promptRows.getLast() == row) return false;
            promptRows.add(row);
            return true;
        } finally {
            buffer.unlock();
        }
    }

    private void markCommandStart() {
        buffer.lock();
        try {
            commandStartRow = absoluteRow(terminal.getCursorY() - 1);
            commandStartColumn = terminal.getCursorX() - 1;
        } finally {
            buffer.unlock();
        }
    }

    /**
     * At C the shell has echoed the command and moved on. Jasper's shell-integration scripts send the exact
     * command line first (the {@code cmd} custom command); when that is missing or malformed, fall back to
     * reading the rows from B to the cursor.
     */
    private void captureCommand() {
        String reported = pendingCommandText;
        pendingCommandText = null;
        buffer.lock();
        try {
            String text = reported;
            if (text == null) {
                if (commandStartRow < 0) return;
                long endRow = absoluteRow(terminal.getCursorY() - 1);
                if (terminal.getCursorX() - 1 == 0) endRow--; // Enter moved the cursor to a fresh line
                text = CommandCapture.text(commandStartRow, commandStartColumn, endRow, buffer.getWidth(), this::lineAtLocked);
            }
            commandStartRow = -1;
            pendingCommand = text.isEmpty() ? null : text;
            commandStartedAt = clock.getAsLong();
        } finally {
            buffer.unlock();
        }
    }

    /**
     * Decodes the base64 UTF-8 {@code cmd} payload from Jasper's shell-integration scripts, or null if
     * malformed — the caller then falls back to reading the command off the screen. The payload is the
     * exact command line, so it is not trimmed; it is only bounded, because OSC 1341 is an open channel
     * and the history index enforces the same limit on the lines it parses from disk.
     */
    private static String decodeCommand(String encoded) {
        String trimmed = encoded.trim();
        if (trimmed.length() > (MAX_COMMAND_BYTES / 3 + 1) * 4) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(trimmed);
            if (bytes.length > MAX_COMMAND_BYTES) return null;
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return text.isEmpty() ? null : text;
        } catch (IllegalArgumentException | CharacterCodingException malformed) {
            return null;
        }
    }

    private void flushPendingCommand(OptionalInt exitStatus) {
        String command = pendingCommand;
        long startedAt = commandStartedAt;
        pendingCommand = null;
        pendingCommandText = null;
        if (command == null) return;
        // Only captureCommand sets pendingCommand, and it stamps the clock in the same breath, so a
        // non-null command always has a real start. nanoTime is monotonic, so this cannot go negative.
        Duration ran = Duration.ofNanos(clock.getAsLong() - startedAt);
        Optional<Path> directory = workingDirectory();
        listeners.forEach(l -> l.commandExecuted(command, exitStatus, directory, ran));
    }

    private static OptionalInt exitStatus(List<String> args) {
        if (args.size() < 4) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(args.get(3).trim()));
        } catch (NumberFormatException malformed) {
            return OptionalInt.empty();
        }
    }

    /** The local path of an OSC 7 {@code file://host/path} URI; the host is ignored. */
    static Optional<Path> directoryFromUri(String uri) {
        try {
            URI parsed = new URI(uri);
            String path = parsed.getPath();
            if (!"file".equalsIgnoreCase(parsed.getScheme()) || path == null || path.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Path.of(new URI("file", null, path, null)));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
