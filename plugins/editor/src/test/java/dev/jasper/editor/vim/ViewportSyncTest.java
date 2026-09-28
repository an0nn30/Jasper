package dev.jasper.editor.vim;

import java.awt.Font;
import java.awt.Point;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The view and the text area's scroll pane move together (spec 5.1); the corpus checks the view itself. */
class ViewportSyncTest {
    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private record Setup(VimTextArea area, JScrollPane scroll, VimEditor editor) { }

    /** A text area of 60 lines in a scroll pane about 10 lines high and 30 characters wide. */
    private static Setup setup() {
        var area = new VimTextArea();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 60; i++) text.append("line ").append(i).append('\n');
        area.setText(text.toString());
        area.setCaretPosition(0);
        var scroll = new JScrollPane(area);
        int charWidth = area.getFontMetrics(area.getFont()).charWidth('m');
        scroll.setSize(30 * charWidth + 40, 10 * area.getLineHeight() + 4);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
        return new Setup(area, scroll, editor);
    }

    @Test void theViewTakesItsSizeFromTheScrollPane() throws Exception {
        onEdt(() -> {
            var s = setup();
            type(s.editor(), "j");
            var visible = s.scroll().getViewport().getViewRect();
            int charWidth = s.area().getFontMetrics(s.area().getFont()).charWidth('m');
            assertThat(s.editor().view().height()).isEqualTo(visible.height / s.area().getLineHeight());
            assertThat(s.editor().view().width()).isEqualTo(visible.width / charWidth);
            assertThat(s.editor().view().height()).isGreaterThan(5);
        });
    }

    @Test void theViewportShowsTheViewsFirstLine() throws Exception {
        onEdt(() -> {
            var s = setup();
            type(s.editor(), "40Gzt");
            assertThat(s.editor().view().topline).isEqualTo(40);
            assertThat(s.scroll().getViewport().getViewPosition().y).isEqualTo(yFor(s, 39));
            type(s.editor(), "<C-y>");
            assertThat(s.scroll().getViewport().getViewPosition().y).isEqualTo(yFor(s, 38));
        });
    }

    @Test void scrollingTheViewportMovesTheViewAndKeepsTheCursorInside() throws Exception {
        onEdt(() -> {
            var s = setup();
            s.scroll().getViewport().setViewPosition(new Point(0, yFor(s, 20)));
            assertThat(s.editor().view().topline).isEqualTo(21);
            assertThat(s.editor().cursor().line).as("the cursor comes into the window").isEqualTo(20);
            type(s.editor(), "j");
            assertThat(s.editor().cursor().line).isEqualTo(21);
            assertThat(s.editor().view().topline).as("a move inside the window does not scroll").isEqualTo(21);
        });
    }

    @Test void turningVimOffGivesTheTextAreaBackItsWrappingAndFoldNavigation() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setWrapStyleWord(true);
            var filter = area.getNavigationFilter();
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            assertThat(area.getWrapStyleWord()).as("Vim wraps at any character").isFalse();
            assertThat(area.getNavigationFilter()).as("the cursor may be in a closed fold").isNull();
            editor.close();
            assertThat(area.getWrapStyleWord()).isTrue();
            assertThat(area.getNavigationFilter()).isSameAs(filter);
        });
    }

    private static int yFor(Setup s, int line) {
        try { return s.area().yForLine(line); }
        catch (javax.swing.text.BadLocationException bad) { throw new AssertionError(bad); }
    }
}
