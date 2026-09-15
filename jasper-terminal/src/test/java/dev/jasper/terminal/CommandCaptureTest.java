package dev.jasper.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandCaptureTest {
    private static TerminalLine line(String text, boolean wrapped) {
        var line = new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(text)));
        line.setWrapped(wrapped);
        return line;
    }

    @Test void joinsWrappedRowsSkipsThePromptAndStripsContinuationCells() {
        var lines = List.of(line("$ echo wide 中x", true), line("y", false), line("> more", false));
        String text = CommandCapture.text(0, 2, 2, 20, row -> lines.get((int) row));
        assertThat(text).isEqualTo("echo wide 中xy\n> more");
        assertThat(CommandCapture.text(5, 0, 4, 20, row -> null)).isEmpty();
        assertThat(CommandCapture.text(0, 0, 0, 20, row -> line("   ", false))).isEmpty();
    }
}
