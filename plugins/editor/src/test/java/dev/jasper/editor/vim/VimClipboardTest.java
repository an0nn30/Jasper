package dev.jasper.editor.vim;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.fife.ui.rtextarea.ClipboardHistory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The Editor's Copy, Cut and Paste in Vim's Visual mode act on the Visual area (IdeaVim). */
class VimClipboardTest {
    private final Map<Character, String> clipboard = new HashMap<>();
    private final Vim vim = new Vim(new VimHost(r -> Optional.ofNullable(clipboard.get(r)), clipboard::put));

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private VimEditor editor(VimTextArea area, String text) {
        area.setText(text);
        area.discardAllEdits();
        area.setCaretPosition(0);
        return new VimEditor(vim, area, EditorDefaults.VIM);
    }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void copyInCharwiseVisualCopiesAndStays() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc def\n");
            type(editor, "vl");
            area.copy();
            assertThat(clipboard.get('+')).isEqualTo("ab");
            assertThat(editor.mode()).isEqualTo(Mode.VISUAL);
            assertThat(area.getText()).isEqualTo("abc def\n");
            assertThat(ClipboardHistory.get().getHistory()).as("Paste from a list offers it").contains("ab");
            clipboard.clear();
            type(editor, "l");
            area.copyAsStyledText();
            assertThat(clipboard.get('+')).isEqualTo("abc");
            assertThat(editor.mode()).isEqualTo(Mode.VISUAL);
        });
    }

    @Test void copyInLinewiseVisual() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "one\ntwo\nthree\n");
            type(editor, "Vj");
            area.copy();
            assertThat(clipboard.get('+')).isEqualTo("one\ntwo\n");
        });
    }

    @Test void linewiseCopyOfTheLastLineWithoutABreak() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "one\ntwo");
            type(editor, "jV");
            area.copy();
            assertThat(clipboard.get('+')).isEqualTo("two\n");
        });
    }

    @Test void copyInBlockwiseVisual() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\ndef\n");
            type(editor, "l<C-v>j");
            area.copy();
            assertThat(clipboard.get('+')).isEqualTo("b\ne");
            assertThat(editor.mode()).isEqualTo(Mode.VISUAL_BLOCK);
        });
    }

    @Test void cutDeletesAndEndsVisual() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc def\n");
            type(editor, "wyw0");
            var unnamed = vim.registers().get('"');
            type(editor, "vl");
            area.cut();
            assertThat(clipboard.get('+')).isEqualTo("ab");
            assertThat(area.getText()).isEqualTo("c def\n");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(vim.registers().get('"')).as("Vim's registers are untouched").isEqualTo(unnamed);
            type(editor, "u");
            assertThat(area.getText()).isEqualTo("abc def\n");
        });
    }

    @Test void pasteReplacesCharwise() throws Exception {
        onEdt(() -> {
            clipboard.put('+', "XY");
            var area = new VimTextArea();
            var editor = editor(area, "abc def\n");
            var unnamed = vim.registers().get('"');
            type(editor, "vl");
            area.paste();
            assertThat(area.getText()).isEqualTo("XYc def\n");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 1));
            assertThat(vim.registers().get('"')).isEqualTo(unnamed);
            type(editor, "u");
            assertThat(area.getText()).isEqualTo("abc def\n");
        });
    }

    @Test void pasteReplacesLinesKeepingTheBreak() throws Exception {
        onEdt(() -> {
            for (String text : new String[] {"new", "new\n"}) {
                clipboard.put('+', text);
                var area = new VimTextArea();
                var editor = editor(area, "one\ntwo\nthree\n");
                type(editor, "Vj");
                area.paste();
                assertThat(area.getText()).as(text).isEqualTo("new\nthree\n");
                assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
                assertThat(editor.cursor()).isEqualTo(new Pos(0, 2));
                type(editor, "u");
                assertThat(area.getText()).isEqualTo("one\ntwo\nthree\n");
            }
        });
    }

    @Test void pasteReplacesBlockAtItsCorner() throws Exception {
        onEdt(() -> {
            clipboard.put('+', "Z");
            var area = new VimTextArea();
            var editor = editor(area, "abc\ndef\n");
            type(editor, "l<C-v>j");
            area.paste();
            assertThat(area.getText()).isEqualTo("aZc\ndf\n");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 1));
            type(editor, "u");
            assertThat(area.getText()).isEqualTo("abc\ndef\n");
        });
    }

    @Test void pasteWithEmptyClipboardLeavesTheText() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc def\n");
            type(editor, "vl");
            area.paste();
            assertThat(area.getText()).isEqualTo("abc def\n");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
        });
    }

    @Test void historyPasteReplacesTheArea() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc def\n");
            type(editor, "vl");
            area.replaceSelection("Q");
            assertThat(area.getText()).isEqualTo("Qc def\n");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
        });
    }

    @Test void outsideVisualTheyActAsBefore() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc def\n");
            assertThat(editor.visual()).isFalse();
            type(editor, "li");
            area.replaceSelection("Q");
            assertThat(area.getText()).as("Insert mode inserts at the caret").isEqualTo("aQbc def\n");
            assertThat(editor.mode()).isEqualTo(Mode.INSERT);
            type(editor, "<Esc>");
            editor.close();
            area.select(0, 2);
            area.replaceSelection("Z");
            assertThat(area.getText()).as("with Vim off, the selection is replaced").isEqualTo("Zbc def\n");
        });
    }
}
