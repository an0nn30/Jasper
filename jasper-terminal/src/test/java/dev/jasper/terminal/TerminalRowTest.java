package dev.jasper.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalRowTest {
    @Test void aCapturedRowRemainsIndependentOfItsProducer() {
        var line = new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer("ab")));
        var reader = new JediCellReader();
        TerminalRow snapshot = reader.capture(line, 4);
        TerminalRow live = reader.live(line);
        line.writeString(0, new CharBuffer("z"), TextStyle.EMPTY);
        assertThat(snapshot.getText()).isEqualTo("ab");
        assertThat(live.getText()).isEqualTo("zb");
        char[] read = new char[4];
        snapshot.readCells(read, null);
        assertThat(read).containsExactly('a', 'b', ' ', ' ');
        read[0] = 'q';
        snapshot.readCells(read, null);
        assertThat(read).containsExactly('a', 'b', ' ', ' ');
    }

    @Test void appendingAfterNullPaddingCannotChangeDetachedText() {
        var line = new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer((char) 0, 3)));
        var snapshot = new JediCellReader().capture(line, 5);
        line.writeString(3, new CharBuffer("x"), TextStyle.EMPTY);
        assertThat(snapshot.getText()).isEmpty();
        char[] read = new char[5];
        snapshot.readCells(read, null);
        assertThat(new String(read)).isEqualTo("     ");
        assertThat(snapshot.length()).isEqualTo(3);
    }
}
