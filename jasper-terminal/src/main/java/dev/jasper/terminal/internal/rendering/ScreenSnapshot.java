package dev.jasper.terminal.internal.rendering;

import dev.jasper.terminal.internal.text.CursorRequest;
import dev.jasper.terminal.internal.text.TerminalRow;

import java.util.List;

/**
 * The visible rows, copied under the buffer lock so painting never races the emulator. Rows start at the absolute row
 * {@code firstRow}; {@code cursorRow} is a viewport row (≥ {@code height} when the cursor is scrolled out of view).
 * @param width grid columns
 * @param height viewport rows
 * @param lines detached rows captured together under the buffer lock
 * @param cursorColumn zero-based cursor cell
 * @param cursorRow viewport-relative cursor row, possibly outside the viewport
 * @param cursorVisible whether the program enables the cursor
 * @param cursorShape program request, or null to use configuration
 * @param firstRow absolute top viewport row
 * @param scrollOffset distance from live output in rows
 * @param historyLines retained history count
 * @param alternateBuffer whether this snapshot belongs to the alternate screen
 */
public record ScreenSnapshot(int width, int height, List<TerminalRow> lines,
                      int cursorColumn, int cursorRow, boolean cursorVisible, CursorRequest cursorShape,
                      long firstRow, int scrollOffset, int historyLines, boolean alternateBuffer) {

    /** Requests the live screen, following new output. */
    public static final long FOLLOW_OUTPUT = Long.MAX_VALUE;

    public String lineText(int row) {
        return lines.get(row).getText();
    }
}
