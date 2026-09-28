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
}
