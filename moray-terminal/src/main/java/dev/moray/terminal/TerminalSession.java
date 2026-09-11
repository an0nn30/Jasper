package dev.moray.terminal;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

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

        default void workingDirectoryChanged(Path directory) {
        }
    }

    private final TtyConnector connector;
    private final TerminalTextBuffer buffer;
    private final JediTerminal terminal;
    private final SessionDisplay display;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Integer> exit = new CompletableFuture<>();
    private final List<Long> promptRows = new CopyOnWriteArrayList<>();
    /** Lines dropped off the top of the scrollback so far; the base of absolute row numbers. */
    private volatile long discardedLines;
    private volatile Path workingDirectory;
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
        this.connector = new ShellIntegrationConnector(connector);
        this.columns = columns;
        this.rows = rows;
        StyleState styleState = new StyleState();
        buffer = new TerminalTextBuffer(columns, rows, styleState, scrollback);
        display = new SessionDisplay(
            title -> listeners.forEach(l -> l.titleChanged(title)),
            () -> listeners.forEach(Listener::bell),
            () -> listeners.forEach(Listener::screenChanged));
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
        terminal.addCustomCommandListener(this::onCustomCommand);
        // Without a filter JediTerm drops OSC 8 links; one item spanning the whole URI makes it keep them.
        terminal.setUrlHyperlinkFilter(uri -> new LinkResult(new LinkResultItem(0, uri.length(), new UriLink(uri))));
        buffer.addChangesListener(new TextBufferChangesListener() {
            @Override
            public void linesDiscardedFromHistory(List<TerminalLine> lines) {
                discardedLines += lines.size(); // reader thread, under the buffer lock
            }

            @Override
            public void historyCleared() {
                promptRows.clear();
            }
        });
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
        writeExitMessage(code);
        exit.complete(code);
    }

    private void writeExitMessage(int code) {
        char[] message = ("\r\n[process exited with code " + code + "]").toCharArray();
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

    /** The directory the shell last reported with OSC 7, if any. */
    public Optional<Path> workingDirectory() {
        return Optional.ofNullable(workingDirectory);
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

    byte[] codeForKey(int keyCode, int modifiers) {
        return terminal.getCodeForKey(keyCode, modifiers);
    }

    SessionDisplay display() {
        return display;
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

    /** Every match in the scrollback and on screen, oldest first; an empty query finds nothing. */
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
            history = buffer.getHistoryLinesCount();
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

    /** The link at an absolute row and column: an OSC 8 hyperlink, or else a URL written in the text. */
    Optional<String> linkAt(long absoluteRow, int column) {
        buffer.lock();
        try {
            TerminalLine line = lineAtLocked(absoluteRow);
            if (line == null) {
                return Optional.empty();
            }
            if (column < line.length()
                && line.getStyleAt(column) instanceof HyperlinkStyle hyperlink
                && hyperlink.getLinkInfo() instanceof UriLink link) {
                return Optional.of(link.uri());
            }
            return urlAcrossWrappedRows(absoluteRow, column);
        } finally {
            buffer.unlock();
        }
    }

    /**
     * A plain-text URL search that follows soft wraps: joins the wrapped screen rows around {@code absoluteRow}
     * into one logical line (the same wrap-walking {@link #lineSelection} uses) before searching, so a URL split
     * across a wrap boundary is still recognized as one link. Call with the buffer lock held.
     */
    private Optional<String> urlAcrossWrappedRows(long absoluteRow, int column) {
        long first = absoluteRow;
        while (true) {
            TerminalLine above = lineAtLocked(first - 1);
            if (above == null || !above.isWrapped()) {
                break;
            }
            first--;
        }
        long last = absoluteRow;
        while (true) {
            TerminalLine line = lineAtLocked(last);
            if (line == null || !line.isWrapped() || lineAtLocked(last + 1) == null) {
                break;
            }
            last++;
        }
        int width = buffer.getWidth();
        StringBuilder text = new StringBuilder();
        List<Integer> columns = new ArrayList<>();
        List<Integer> lastColumns = new ArrayList<>();
        for (long row = first; row <= last; row++) {
            TerminalLine line = lineAtLocked(row);
            if (line == null) {
                continue;
            }
            RowText rowText = RowText.of(line, width);
            int offset = (int) ((row - first) * width);
            for (int i = 0; i < rowText.text().length(); i++) {
                text.append(rowText.text().charAt(i));
                columns.add(offset + rowText.columns()[i]);
                lastColumns.add(offset + rowText.lastColumns()[i]);
            }
        }
        int virtualColumn = (int) ((absoluteRow - first) * width) + column;
        RowText combined = new RowText(text.toString(),
            columns.stream().mapToInt(Integer::intValue).toArray(),
            lastColumns.stream().mapToInt(Integer::intValue).toArray());
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

    /** The whole logical line at an absolute row, soft-wrapped rows included. */
    Selection lineSelection(long row) {
        buffer.lock();
        try {
            long first = row;
            while (true) {
                TerminalLine above = lineAtLocked(first - 1);
                if (above == null || !above.isWrapped()) {
                    break;
                }
                first--;
            }
            long last = row;
            while (true) {
                TerminalLine line = lineAtLocked(last);
                if (line == null || !line.isWrapped() || lineAtLocked(last + 1) == null) {
                    break;
                }
                last++;
            }
            return new Selection(first, 0, last, buffer.getWidth() - 1, false);
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
        if (args.size() < 2 || !"moray".equals(args.get(0))) {
            return;
        }
        switch (args.get(1)) {
            case "cwd" -> directoryFromUri(String.join(";", args.subList(2, args.size()))).ifPresent(directory -> {
                workingDirectory = directory;
                listeners.forEach(l -> l.workingDirectoryChanged(directory));
            });
            case "mark" -> {
                if (args.size() > 2 && "A".equals(args.get(2))) {
                    recordPrompt();
                }
            }
            case "cursor-reset" -> display.resetCursorShape();
            default -> {
                // A command from a newer Moray shell-integration script; nothing to do.
            }
        }
    }

    private void recordPrompt() {
        buffer.lock();
        try {
            long row = absoluteRow(terminal.getCursorY() - 1);
            if (promptRows.isEmpty() || promptRows.getLast() != row) {
                promptRows.add(row);
            }
        } finally {
            buffer.unlock();
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
