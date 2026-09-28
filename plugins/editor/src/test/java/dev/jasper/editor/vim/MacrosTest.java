package dev.jasper.editor.vim;

import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Recording what the corpus cannot type: input-method text, and the mode text the Vim bar shows (spec 4.3). */
class MacrosTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static VimEditor editor(String text) {
        var area = new VimTextArea();
        area.setText(text);
        area.setCaretPosition(0);
        return new VimEditor(new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { })), area, EditorDefaults.VIM);
    }

    @Test void recordingIsShownWithTheMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor("abc\n");
            type(editor, "qa");
            assertThat(editor.modeText()).isEqualTo("recording @a");
            type(editor, "i");
            assertThat(editor.modeText()).isEqualTo("-- INSERT --recording @a");
            type(editor, "<Esc>:set nosmd<CR>");
            assertThat(editor.modeText()).as("without 'showmode' too").isEqualTo("recording @a");
            type(editor, "q");
            assertThat(editor.modeText()).isEmpty();
        });
    }

    @Test void inputMethodTextIsRecorded() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor("abc\n");
            type(editor, "qai");
            editor.typedByInputMethod("日本");
            type(editor, "<Esc>q");
            assertThat(editor.vim().registers().get('a').orElseThrow().text()).isEqualTo("i日本\u001b");
            assertThat(editor.buffer().line(0)).as("the text area inserts what the input method commits").isEqualTo("abc");
            type(editor, "@a");
            assertThat(editor.buffer().line(0)).isEqualTo("日本abc");
        });
    }
}
