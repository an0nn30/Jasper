package dev.jasper.editor.vim;

import java.util.List;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BufferTest {
    private static Buffer buffer(String text) throws Exception {
        var document = new PlainDocument();
        document.insertString(0, text, null);
        return new Buffer(document);
    }

    @Test void aFinalLineBreakEndsTheLastLine() throws Exception {
        Buffer b = buffer("a\nb\n");
        assertThat(b.count()).isEqualTo(2);
        assertThat(b.line(1)).isEqualTo("b");
        assertThat(b.end(1)).isEqualTo(3);
        assertThat(buffer("").count()).isEqualTo(1);
        assertThat(buffer("\n").line(0)).isEmpty();
        assertThat(buffer("a\n\n").count()).isEqualTo(2);
    }

    @Test void convertsBetweenOffsetsAndPositions() throws Exception {
        Buffer b = buffer("ab\ncd\n");
        assertThat(b.pos(4)).isEqualTo(new Pos(1, 1));
        assertThat(b.offset(new Pos(1, 1))).isEqualTo(4);
        assertThat(b.lineOf(6)).isEqualTo(1);
    }

    @Test void insertsAndDeletesWholeLinesKeepingTheFinalBreak() throws Exception {
        Buffer b = buffer("a\nb\n");
        b.insertLines(2, List.of("c"));
        assertThat(b.text()).isEqualTo("a\nb\nc\n");
        b.insertLines(0, List.of("z"));
        assertThat(b.text()).isEqualTo("z\na\nb\nc\n");
        b.deleteLines(3, 3);
        assertThat(b.text()).isEqualTo("z\na\nb\n");
        b.deleteLines(0, 0);
        assertThat(b.text()).isEqualTo("a\nb\n");
        b.deleteLines(0, 1);
        assertThat(b.text()).isEmpty();
        Buffer bare = buffer("x");
        bare.insertLines(1, List.of("y"));
        assertThat(bare.text()).isEqualTo("x\ny");
    }

    @Test void seesEditsMadeOutsideIt() throws Exception {
        var document = new PlainDocument();
        document.insertString(0, "one\n", null);
        Buffer b = new Buffer(document);
        assertThat(b.line(0)).isEqualTo("one");
        document.insertString(0, "zero\n", null);
        assertThat(b.count()).isEqualTo(2);
        b.close();
    }
}
