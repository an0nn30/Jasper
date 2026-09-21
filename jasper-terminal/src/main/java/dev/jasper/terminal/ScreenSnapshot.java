package dev.jasper.terminal;


import java.util.ArrayList;
import java.util.List;

/**
 * The visible rows, copied under the buffer lock so painting never races the emulator. Rows start at the absolute row
 * {@code firstRow}; {@code cursorRow} is a viewport row (≥ {@code height} when the cursor is scrolled out of view).
 */
record ScreenSnapshot(int width, int height, List<TerminalRow> lines,
                      int cursorColumn, int cursorRow, boolean cursorVisible, CursorRequest cursorShape,
                      long firstRow, int scrollOffset, int historyLines, boolean alternateBuffer) {

    /** Requests the live screen, following new output. */
    static final long FOLLOW_OUTPUT = Long.MAX_VALUE;

    String lineText(int row) {
        return lines.get(row).getText();
    }
}
