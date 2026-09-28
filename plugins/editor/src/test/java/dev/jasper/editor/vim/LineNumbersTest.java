package dev.jasper.editor.vim;

import javax.swing.SwingUtilities;
import org.fife.ui.rtextarea.LineNumberList;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 'number' and 'relativenumber' on the gutter of the text area's scroll pane. */
class LineNumbersTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void theGutterFollowsTheOptionsAndIsPutBack() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("a\nb\nc\nd\ne\n");
            area.setCaretPosition(0);
            var pane = new RTextScrollPane(area);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var gutter = pane.getGutter();
            assertThat(pane.getLineNumbersEnabled()).as("'number' starts on").isTrue();
            type(editor, ":set rnu<CR>jj");
            var formatter = gutter.getLineNumberFormatter();
            assertThat(formatter.format(3)).as("the cursor line with 'number'").isEqualTo("3");
            assertThat(formatter.format(1)).isEqualTo("2");
            assertThat(formatter.format(5)).isEqualTo("2");
            type(editor, ":set nonu<CR>");
            assertThat(formatter.format(3)).as("the cursor line without 'number'").isEqualTo("0");
            assertThat(pane.getLineNumbersEnabled()).isTrue();
            type(editor, ":set nornu<CR>");
            assertThat(pane.getLineNumbersEnabled()).as("neither option").isFalse();
            type(editor, ":set rnu<CR>");
            editor.close();
            assertThat(pane.getLineNumbersEnabled()).as("Vim off: as before").isTrue();
            assertThat(gutter.getLineNumberFormatter()).isSameAs(LineNumberList.DEFAULT_LINE_NUMBER_FORMATTER);
        });
    }

    @Test void aClosedFoldCountsAsOneLine() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("class A {\n  void f() {\n    x();\n  }\n  int y;\n}\n");
            area.setCaretPosition(0);
            var pane = new RTextScrollPane(area);
            area.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA);
            area.setCodeFoldingEnabled(true);
            area.getFoldManager().reparse();
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            type(editor, "jzc:set rnu<CR>gg");
            var formatter = pane.getGutter().getLineNumberFormatter();
            assertThat(formatter.format(2)).isEqualTo("1");
            assertThat(formatter.format(5)).as("lines 2 to 4 are one closed fold").isEqualTo("2");
        });
    }
}
