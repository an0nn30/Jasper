package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.internal.rendering.ScreenSnapshot;
import dev.jasper.terminal.internal.shell.CommandLocation;
import dev.jasper.terminal.internal.text.AbsoluteRowState;
import dev.jasper.terminal.internal.text.CellAttributes;
import dev.jasper.terminal.internal.text.CommandCapture;
import dev.jasper.terminal.internal.text.CursorRequest;
import dev.jasper.terminal.internal.text.FileTarget;
import dev.jasper.terminal.internal.text.LinkDetector;
import dev.jasper.terminal.internal.text.LinkHit;
import dev.jasper.terminal.internal.text.LogicalLine;
import dev.jasper.terminal.internal.text.MouseGeometry;
import dev.jasper.terminal.internal.text.PathDetector;
import dev.jasper.terminal.internal.text.RowText;
import dev.jasper.terminal.internal.text.SelectedCells;
import dev.jasper.terminal.internal.text.Selection;
import dev.jasper.terminal.internal.text.SelectionText;
import dev.jasper.terminal.internal.text.TerminalRow;
import dev.jasper.terminal.internal.text.TerminalSearch;
import dev.jasper.terminal.internal.text.WordBoundaries;

import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalTextBuffer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Buffer reads with explicit locking; regex and rendering consume detached rows afterward. */
public final class BufferQueries {
    private static final Set<String> OSC8_SCHEMES = Set.of("http", "https", "ftp", "mailto");
    private final TerminalTextBuffer buffer;
    private final JediTerminal terminal;
    private final SessionDisplay display;
    private final AbsoluteRowState rowState;
    private final JediCellReader cells;
    BufferQueries(TerminalTextBuffer buffer, JediTerminal terminal, SessionDisplay display,
                  AbsoluteRowState rowState, JediCellReader cells) {
        this.buffer = buffer; this.terminal = terminal; this.display = display;
        this.rowState = rowState; this.cells = cells;
    }
    public CommandLocation cursor() {
        buffer.lock();
        try { return new CommandLocation(absoluteRow(terminal.getCursorY() - 1), terminal.getCursorX() - 1); }
        finally { buffer.unlock(); }
    }
    public String captureCommand(CommandLocation start) {
        buffer.lock();
        try {
            long endRow = absoluteRow(terminal.getCursorY() - 1);
            if (terminal.getCursorX() - 1 == 0) endRow--;
            return CommandCapture.text(start.row(), start.column(), endRow, buffer.getWidth(), this::lineAtLocked);
        } finally { buffer.unlock(); }
    }
    public boolean recordPrompt() {
        buffer.lock();
        try { return rowState.recordPrompt(absoluteRow(terminal.getCursorY() - 1)); }
        finally { buffer.unlock(); }
    }
    /** Call with the buffer lock held so the captured discard count and buffer agree. */
    private ScreenSnapshot capture(TerminalTextBuffer buffer, JediTerminal terminal, SessionDisplay display,
                                  long captureBase, long requestedTopRow) {
        buffer.lock();
        try {
            int width = buffer.getWidth();
            int height = buffer.getHeight();
            boolean alternate = buffer.isUsingAlternateBuffer();
            int history = buffer.getHistoryLinesCount();
            int scrollable = alternate ? 0 : history;
            long liveTop = captureBase + history;
            int offset = requestedTopRow == ScreenSnapshot.FOLLOW_OUTPUT
                ? 0
                : (int) Math.max(0, Math.min(scrollable, liveTop - requestedTopRow));
            List<TerminalRow> lines = new ArrayList<>(height);
            for (int row = 0; row < height; row++) {
                lines.add(cells.capture(buffer.getLine(row - offset), width));
            }
            return new ScreenSnapshot(width, height, lines,
                terminal.getCursorX() - 1, terminal.getCursorY() - 1 + offset,
                display.cursorVisible(), JediCellReader.cursor(display.cursorShape()),
                liveTop - offset, offset, scrollable, alternate);
        } finally {
            buffer.unlock();
        }
    }

    public ScreenSnapshot snapshot() {
        return snapshot(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    /** The rows starting at an absolute top row (clamped to the scrollback), or the live screen for FOLLOW_OUTPUT. */
    public ScreenSnapshot snapshot(long topRow) {
        buffer.lock();
        try {
            return capture(buffer, terminal, display, rowState.discarded(), topRow);
        } finally {
            buffer.unlock();
        }
    }

    public MouseGeometry mouseGeometry(long requestedTopRow) {
        buffer.lock();
        try {
            int history = buffer.getHistoryLinesCount();
            boolean alternate = buffer.isUsingAlternateBuffer();
            long liveTop = rowState.discarded() + history;
            int offset = requestedTopRow == ScreenSnapshot.FOLLOW_OUTPUT ? 0
                : (int) Math.max(0, Math.min(alternate ? 0 : history, liveTop - requestedTopRow));
            return new MouseGeometry(buffer.getWidth(), buffer.getHeight(), liveTop - offset, offset, alternate);
        } finally {
            buffer.unlock();
        }
    }

    /** Cursor eligibility without copying terminal lines; called only while a view can actually blink. */
    public boolean blinkingCursorInView(long requestedTopRow, boolean configuredBlink) {
        buffer.lock();
        try {
            if (!display.cursorVisible() || !CursorRequest.effectiveBlink(JediCellReader.cursor(display.cursorShape()), configuredBlink)) {
                return false;
            }
            int history = buffer.getHistoryLinesCount();
            long liveTop = rowState.discarded() + history;
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

    /** The text of the line at an absolute row, or null when it is no longer in the scrollback. */
    public String lineText(long absoluteRow) {
        buffer.lock();
        try {
            TerminalRow line = lineAtLocked(absoluteRow);
            return line == null ? null : line.getText();
        } finally {
            buffer.unlock();
        }
    }

    /** The text of a selection: soft-wrapped rows joined, wide characters whole, trailing spaces trimmed. */
    public String text(Selection selection) {
        buffer.lock();
        try {
            return SelectionText.extract(selection, this::lineAtLocked, buffer.getWidth());
        } finally {
            buffer.unlock();
        }
    }

    public List<SelectedCells> selectedLiveCells(Selection selection) {
        buffer.lock();
        try {
            int width = buffer.getWidth();
            long liveTop = absoluteRow(0);
            long first = Math.max(liveTop, selection.startRow());
            long last = Math.min(liveTop + buffer.getHeight() - 1, selection.endRow());
            List<SelectedCells> cells = new ArrayList<>();
            char[] chars = new char[width];
            for (long row = first; row <= last; row++) {
                lineAtLocked(row).readCells(width, chars, null);
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

    public boolean selectionUnchanged(List<SelectedCells> cells) {
        buffer.lock();
        try {
            return selectionUnchangedLocked(cells);
        } finally {
            buffer.unlock();
        }
    }

    /** Validation and extraction share a lock so Copy cannot pick up an overwrite between the two. */
    public Optional<String> selectedText(Selection selection, List<SelectedCells> cells) {
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
            TerminalRow line = lineAtLocked(selected.row());
            if (line == null || selected.column() + selected.cells().length() > width) return false;
            line.readCells(width, chars, null);
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
    public List<TerminalSearch.Match> search(String query, boolean regex, boolean caseSensitive) {
        if (query.isEmpty()) {
            return List.of();
        }
        Pattern pattern = TerminalSearch.pattern(query, regex, caseSensitive);
        int history;
        long firstRow;
        int width;
        List<TerminalRow> lines;
        buffer.lock();
        try {
            history = buffer.isUsingAlternateBuffer() ? 0 : buffer.getHistoryLinesCount();
            firstRow = absoluteRow(-history);
            width = buffer.getWidth();
            lines = new ArrayList<>(history + buffer.getHeight());
            for (int row = -history; row < buffer.getHeight(); row++) {
                lines.add(cells.capture(buffer.getLine(row), width));
            }
        } finally {
            buffer.unlock();
        }
        // Run regex matching outside the lock so a slow pattern cannot stall the reader thread
        return TerminalSearch.find(pattern, firstRow, lines, width);
    }

    /**
     * The link at an absolute row and column: an OSC 8 hyperlink with the {@code target} its program named, or else a
     * URL written in the text, which has no target. A program chooses an OSC 8 URI freely, so only http, https, ftp and
     * mailto schemes are accepted; a cell whose OSC 8 URI has any other scheme has no link at all, whatever its text says.
     */
    public Optional<LinkHit> linkAt(long absoluteRow, int column) {
        buffer.lock();
        try {
            if (column < 0 || column >= buffer.getWidth()) return Optional.empty();
            TerminalRow line = lineAtLocked(absoluteRow);
            if (line == null) {
                return Optional.empty();
            }
            CellAttributes cell = column < line.length() ? line.attributesAt(column) : CellAttributes.DEFAULT;
            if (cell.link() != null) {
                return openableScheme(cell.link())
                    ? Optional.of(new LinkHit(cell.link(), Optional.ofNullable(cell.linkTarget()))) : Optional.empty();
            }
            return urlAcrossWrappedRows(absoluteRow, column).map(url -> new LinkHit(url, Optional.empty()));
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
        return wrappedRowLocked(absoluteRow, column).flatMap(wrapped -> LinkDetector.urlAt(wrapped.text(), wrapped.column()));
    }

    /**
     * The file a click at an absolute row and column names. An OSC 8 link on the cell counts when {@code localFiles}
     * maps it to this machine; a cell with any other OSC 8 target names nothing, whatever its text says. Otherwise the
     * soft-wrapped rows around the row are joined under the lock, as for URLs, and read after it: a plain-text
     * {@code file://} URL under the column counts the same way, with the line and column it ends in, and anything
     * else is left to {@link PathDetector}.
     *
     * @param absoluteRow the clicked row
     * @param column the clicked column
     * @param windows whether {@code C:\} and backslash paths count
     * @param localFiles the path a {@code file} URI names on this machine; empty for a remote one or any other URI
     * @return the file, or empty when the click names none
     */
    public Optional<FileTarget> fileLinkAt(long absoluteRow, int column, boolean windows,
                                           Function<String, Optional<Path>> localFiles) {
        String link;
        Optional<WrappedRow> wrapped;
        buffer.lock();
        try {
            if (column < 0 || column >= buffer.getWidth()) return Optional.empty();
            TerminalRow line = lineAtLocked(absoluteRow);
            if (line == null) return Optional.empty();
            link = column < line.length() ? line.attributesAt(column).link() : null;
            wrapped = link == null ? wrappedRowLocked(absoluteRow, column) : Optional.empty();
        } finally {
            buffer.unlock();
        }
        // Match outside the lock, as search does, so a long logical line cannot stall the reader thread.
        // An OSC 8 target is an exact URI: a colon and digits at its end are part of the path.
        if (link != null) return localFile(link, 0, 0, localFiles);
        if (wrapped.isEmpty()) return Optional.empty();
        RowText text = wrapped.get().text();
        int at = wrapped.get().column();
        Optional<String> url = LinkDetector.fileUrlAt(text, at);
        if (url.isPresent()) {
            LinkDetector.UrlPosition split = LinkDetector.splitPosition(url.get());
            return localFile(split.url(), split.line(), split.column(), localFiles);
        }
        return PathDetector.pathAt(text, at, windows).map(match -> new FileTarget(match.path(), match.line(), match.column()));
    }

    private static Optional<FileTarget> localFile(String uri, int line, int column, Function<String, Optional<Path>> localFiles) {
        return localFiles.apply(uri).map(path -> new FileTarget(path.toString(), line, column));
    }

    /** Soft-wrapped rows joined into one, and the clicked column on it. */
    private record WrappedRow(RowText text, int column) { }

    /**
     * The wrapped screen rows around {@code absoluteRow} joined into one row, within {@link LogicalLine}'s bounds, and
     * {@code column} on it; empty when the line was truncated. Call with the buffer lock held.
     */
    private Optional<WrappedRow> wrappedRowLocked(long absoluteRow, int column) {
        int width = buffer.getWidth();
        LogicalLine range = LogicalLine.around(absoluteRow, width, this::lineAtLocked);
        if (range.truncated()) return Optional.empty();
        // The shared traversal bounds this multiplication to at most MAX_CELLS.
        int cells = range.rowCount() * width;
        StringBuilder text = new StringBuilder(cells);
        int[] columns = new int[cells];
        int[] lastColumns = new int[cells];
        for (int rowIndex = 0; rowIndex < range.rowCount(); rowIndex++) {
            TerminalRow line = lineAtLocked(range.firstRow() + rowIndex);
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
        return Optional.of(new WrappedRow(new RowText(text.toString(), columns, lastColumns), virtualColumn));
    }

    /** The word at an absolute row and column, as a stream selection. */
    public Selection wordSelection(long row, int column) {
        buffer.lock();
        try {
            TerminalRow line = lineAtLocked(row);
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
    public Selection lineSelection(long row) {
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
        return rowState.discarded() + buffer.getHistoryLinesCount() + bufferRow;
    }

    /** The line at an absolute row, or null outside the scrollback and screen. Call with the buffer lock held. */
    private TerminalRow lineAtLocked(long absoluteRow) {
        int history = buffer.getHistoryLinesCount();
        long bufferRow = absoluteRow - rowState.discarded() - history;
        if (bufferRow < -history || bufferRow >= buffer.getHeight()) {
            return null;
        }
        return cells.live(buffer.getLine((int) bufferRow));
    }
}
