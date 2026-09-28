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

    @Test void incsearchShowsAnOffScreenMatchAndEscapeReturnsTheView() throws Exception {
        // Each step runs in its own event so that what Swing queues after a key (the caret's own scrolling) has run.
        Setup[] s = new Setup[1];
        int[] before = new int[2];
        onEdt(() -> {
            s[0] = setup();
            type(s[0].editor(), "5G");
            before[0] = s[0].editor().view().topline;
            before[1] = s[0].scroll().getViewport().getViewPosition().y;
        });
        onEdt(() -> type(s[0].editor(), "/line 50"));
        onEdt(() -> {
            assertThat(s[0].scroll().getViewport().getViewRect().contains(0, yFor(s[0], 49) + 1)).as("the match is shown while typing").isTrue();
            assertThat(s[0].editor().cursor().line).as("the cursor stays").isEqualTo(4);
            type(s[0].editor(), "<Esc>");
        });
        onEdt(() -> {
            assertThat(s[0].editor().view().topline).isEqualTo(before[0]);
            assertThat(s[0].scroll().getViewport().getViewPosition().y).isEqualTo(before[1]);
            assertThat(s[0].editor().cursor().line).isEqualTo(4);
            type(s[0].editor(), "/line 50<CR>");
        });
        onEdt(() -> {
            assertThat(s[0].editor().cursor().line).isEqualTo(49);
            assertThat(s[0].scroll().getViewport().getViewRect().contains(0, yFor(s[0], 49) + 1)).isTrue();
            assertThat(s[0].scroll().getViewport().getViewPosition().y).isEqualTo(yFor(s[0], s[0].editor().view().topline - 1));
        });
    }

    @Test void theViewMayScrollPastTheEndOfTheText() throws Exception {
        onEdt(() -> {
            var s = setup();
            type(s.editor(), "Gzt");
            assertThat(s.editor().view().topline).isEqualTo(60);
            assertThat(s.scroll().getViewport().getViewPosition().y).isEqualTo(yFor(s, 59));
            type(s.editor(), "gg<C-f><C-f><C-f><C-f><C-f><C-f><C-f>");
            assertThat(s.scroll().getViewport().getViewPosition().y).isEqualTo(yFor(s, s.editor().view().topline - 1));
            type(s.editor(), "Gzt10<C-y>H");
            assertThat(s.editor().view().topline).isEqualTo(50);
            assertThat(s.scroll().getViewport().getViewPosition().y).isEqualTo(yFor(s, 49));
            assertThat(s.editor().cursor().line).as("H goes to the top line shown").isEqualTo(49);
        });
    }

    @Test void theViewportFollowsTheViewAsVimScrollsForAJump() throws Exception {
        onEdt(() -> {
            var s = setup();
            var detached = new VimTextArea();
            detached.setText(s.area().getText());
            detached.setCaretPosition(0);
            var alone = new VimEditor(new Vim(VimHost.detached()), detached, EditorDefaults.VIM);
            alone.view().resize(s.editor().view().height(), s.editor().view().width());
            for (String keys : new String[] {"30G", "45G", "12G", "<C-d>", "k"}) {
                type(s.editor(), keys);
                type(alone, keys);
                assertThat(s.editor().view().topline).as(keys).isEqualTo(alone.view().topline);
                assertThat(s.scroll().getViewport().getViewPosition().y).as(keys).isEqualTo(yFor(s, s.editor().view().topline - 1));
            }
        });
    }

    @Test void wrappedScreenLinesAreTheSameInTheViewAndOnTheScreen() throws Exception {
        onEdt(() -> {
            var s = setup();
            s.area().setLineWrap(true);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 12; i++) text.append("word").append(i).append(' ');
            s.area().setText(text + "\nnext\n");
            s.area().setCaretPosition(0);
            s.scroll().doLayout();
            s.scroll().getViewport().doLayout();
            resized(s);
            type(s.editor(), "g$");
            int width = s.editor().view().width();
            assertThat(s.editor().cursor().col).isEqualTo(width - 1);
            try {
                var start = s.area().modelToView2D(0);
                var last = s.area().modelToView2D(width - 1);
                var next = s.area().modelToView2D(width);
                assertThat(last.getY()).as("g$ stays on the first screen line").isEqualTo(start.getY());
                assertThat(next.getY()).as("the next character starts the second screen line").isGreaterThan(start.getY());
                type(s.editor(), "0gj");
                var down = s.area().modelToView2D(s.area().getCaretPosition());
                assertThat(s.editor().cursor().col).isEqualTo(width);
                assertThat(down.getY()).isEqualTo(next.getY());
                assertThat(down.getX()).isEqualTo(start.getX());
            } catch (javax.swing.text.BadLocationException bad) { throw new AssertionError(bad); }
        });
    }

    @Test void resizingTheViewportKeepsTheCursorInTheView() throws Exception {
        onEdt(() -> {
            var s = setup();
            type(s.editor(), "9G");
            assertThat(s.editor().view().topline).isEqualTo(1);
            s.scroll().setSize(s.scroll().getWidth(), 5 * s.area().getLineHeight() + 4);
            s.scroll().doLayout();
            resized(s);
            assertThat(s.editor().view().height()).isLessThan(9);
            int top = s.editor().view().topline;
            assertThat(top).as("the cursor's line is in the window").isGreaterThan(1);
            assertThat(s.scroll().getViewport().getViewPosition().y).isEqualTo(yFor(s, top - 1));
        });
    }

    /** What the viewport's component listener hears when the scroll pane is laid out at a new size. */
    private static void resized(Setup s) {
        var port = s.scroll().getViewport();
        for (var listener : port.getComponentListeners())
            listener.componentResized(new java.awt.event.ComponentEvent(port, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
    }

    private static int yFor(Setup s, int line) {
        try { return s.area().yForLine(line); }
        catch (javax.swing.text.BadLocationException bad) { throw new AssertionError(bad); }
    }
}
