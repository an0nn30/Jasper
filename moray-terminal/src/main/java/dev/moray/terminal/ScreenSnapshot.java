package dev.moray.terminal;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;

import java.util.ArrayList;
import java.util.List;

/** The visible screen, copied under the buffer lock so painting never races the emulator. Cursor is 0-based. */
record ScreenSnapshot(int width, int height, List<TerminalLine> lines,
                      int cursorColumn, int cursorRow, boolean cursorVisible, CursorShape cursorShape) {

    static ScreenSnapshot capture(TerminalTextBuffer buffer, JediTerminal terminal, SessionDisplay display) {
        buffer.lock();
        try {
            int width = buffer.getWidth();
            int height = buffer.getHeight();
            List<TerminalLine> lines = new ArrayList<>(height);
            for (int row = 0; row < height; row++) {
                lines.add(buffer.getLine(row).copy());
            }
            return new ScreenSnapshot(width, height, lines,
                terminal.getCursorX() - 1, terminal.getCursorY() - 1,
                display.cursorVisible(), display.cursorShape());
        } finally {
            buffer.unlock();
        }
    }

    String lineText(int row) {
        return lines.get(row).getText();
    }
}
