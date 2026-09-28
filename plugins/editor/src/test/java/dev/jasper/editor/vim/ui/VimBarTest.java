package dev.jasper.editor.vim.ui;

import dev.jasper.editor.vim.EditorDefaults;
import dev.jasper.editor.vim.KeyInput;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimEditor;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.editor.vim.VimTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VimBarTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void showsTheModeThePartlyTypedCommandAndMessages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("abc\n");
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            assertThat(bar.statusText()).isEmpty();
            type(editor, "i");
            assertThat(bar.statusText()).isEqualTo("-- INSERT --");
            type(editor, "<Esc>2d");
            assertThat(bar.statusText()).isEmpty();
            assertThat(bar.commandText()).isEqualTo("2d");
            type(editor, "<Esc>\"qp");
            assertThat(bar.statusText()).isEqualTo("E353: Nothing in register q");
            assertThat(bar.flashing()).isTrue();
            type(editor, "R");
            assertThat(bar.statusText()).isEqualTo("-- REPLACE --");
            bar.detach();
            type(editor, "<Esc>i");
            assertThat(bar.statusText()).as("a detached bar no longer follows the editor").isEmpty();
        });
    }

    @Test void showsTheCommandLineWithItsCursor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("one two\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "/tw");
            assertThat(bar.statusText()).isEqualTo("/tw");
            assertThat(bar.commandLineCursor()).isEqualTo(3);
            type(editor, "<Left>");
            assertThat(bar.commandLineCursor()).isEqualTo(2);
            type(editor, "<C-v><C-a>");
            assertThat(bar.statusText()).as("a control character shows as ^A").isEqualTo("/t^Aw");
            type(editor, "<Esc>");
            assertThat(bar.commandLineCursor()).isEqualTo(-1);
            type(editor, "/two<CR>");
            assertThat(bar.statusText()).as("a search echoes its pattern").isEqualTo("/two");
            type(editor, "n");
            assertThat(bar.statusText()).isEqualTo("search hit BOTTOM, continuing at TOP");
        });
    }

    @Test void showsTheVisualAreasSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText("ab\u00e9d\nefgh\nijkl\n");
            area.setCaretPosition(0);
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            var bar = new VimBar();
            bar.attach(editor);
            type(editor, "vl");
            assertThat(bar.statusText()).isEqualTo("-- VISUAL --");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "l");
            assertThat(bar.commandText()).as("characters, then UTF-8 bytes when they differ").isEqualTo("3-4");
            type(editor, "j");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "<C-v>");
            assertThat(bar.commandText()).isEqualTo("2x3");
            type(editor, "V");
            assertThat(bar.commandText()).isEqualTo("2");
            type(editor, "\"");
            assertThat(bar.commandText()).as("a pending key replaces the size").isEqualTo("\"");
        });
    }
}
