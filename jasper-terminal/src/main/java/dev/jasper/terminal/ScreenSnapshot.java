package dev.jasper.terminal;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * The visible rows, copied under the buffer lock so painting never races the emulator. Rows start at the absolute row
 * {@code firstRow}; {@code cursorRow} is a viewport row (≥ {@code height} when the cursor is scrolled out of view).
 */
record ScreenSnapshot(int width, int height, List<TerminalLine> lines,
                      int cursorColumn, int cursorRow, boolean cursorVisible, CursorShape cursorShape,
                      long firstRow, int scrollOffset, int historyLines, boolean alternateBuffer) {

    /** Requests the live screen, following new output. */
    static final long FOLLOW_OUTPUT = Long.MAX_VALUE;

    /** Call with the buffer lock held, so {@code discardedLines} and the buffer agree. */
    static ScreenSnapshot capture(TerminalTextBuffer buffer, JediTerminal terminal, SessionDisplay display,
                                  long discardedLines, long requestedTopRow) {
        buffer.lock();
        try {
            int width = buffer.getWidth();
            int height = buffer.getHeight();
            boolean alternate = buffer.isUsingAlternateBuffer();
            int history = buffer.getHistoryLinesCount();
            int scrollable = alternate ? 0 : history;
            long liveTop = discardedLines + history;
            int offset = requestedTopRow == FOLLOW_OUTPUT
                ? 0
                : (int) Math.max(0, Math.min(scrollable, liveTop - requestedTopRow));
            List<TerminalLine> lines = new ArrayList<>(height);
            for (int row = 0; row < height; row++) {
                lines.add(buffer.getLine(row - offset).copy());
            }
            return new ScreenSnapshot(width, height, lines,
                terminal.getCursorX() - 1, terminal.getCursorY() - 1 + offset,
                display.cursorVisible(), display.cursorShape(),
                liveTop - offset, offset, scrollable, alternate);
        } finally {
            buffer.unlock();
        }
    }

    String lineText(int row) {
        return lines.get(row).getText();
    }
}
