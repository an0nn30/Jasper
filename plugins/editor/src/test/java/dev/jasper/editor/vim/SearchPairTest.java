package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** searchpair() (Search.searchpair), which matchit's port moves with. */
class SearchPairTest {
    private static final VimStrings STRINGS = new VimStrings(false, VimRegex.Settings.DEFAULT.keyword());

    private static VimEditor editor(String text, Pos cursor) {
        var area = new VimTextArea();
        area.setText(text);
        var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
        editor.setCursor(cursor);
        return editor;
    }

    @Test void nestedPairsArePassedOver() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor("(a (b) c)\nif\n  if\n  else\n  endif\nelse\nendif\n", new Pos(0, 0));
            assertThat(Search.searchpair(editor, STRINGS, "(", "", ")", false, false, p -> false)).isEqualTo(1);
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 8));
            assertThat(Search.searchpair(editor, STRINGS, "(", "", ")", true, false, p -> false)).as("back to the start").isEqualTo(1);
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 0));
            editor.setCursor(new Pos(1, 0));
            assertThat(Search.searchpair(editor, STRINGS, "\\<if\\>", "\\<else\\>", "\\<endif\\>", false, false, p -> false))
                .as("the outer else, past the inner group").isEqualTo(6);
            assertThat(editor.cursor()).isEqualTo(new Pos(5, 0));
        });
    }

    @Test void skippedMatchesAndNoMatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor("(a ) b)\n", new Pos(0, 0));
            assertThat(Search.searchpair(editor, STRINGS, "(", "", ")", false, false, p -> p.col == 3)).as("the first ) skipped").isEqualTo(1);
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 6));
            editor.setCursor(new Pos(0, 1));
            assertThat(Search.searchpair(editor, STRINGS, "\\[", "", "]", false, false, p -> false)).as("none: the cursor stays").isZero();
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 1));
        });
    }
}
