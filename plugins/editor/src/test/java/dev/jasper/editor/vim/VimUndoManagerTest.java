package dev.jasper.editor.vim;

import java.awt.Point;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VimUndoManagerTest {
    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    /** @return a text area of 600 lines, "line 1" to "line 600", its history empty */
    private static VimTextArea lines() {
        var area = new VimTextArea();
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 600; i++) text.append("line ").append(i).append('\n');
        area.setText(text.toString());
        area.setCaretPosition(0);
        area.discardAllEdits();
        return area;
    }

    /** Puts {@code area} in a scroll pane about 20 lines high, in {@code port}. */
    private static void scrolled(VimTextArea area, JViewport port) {
        var scroll = new JScrollPane();
        scroll.setViewport(port);
        port.setView(area);
        scroll.setSize(400, 20 * area.getLineHeight() + 4);
        scroll.doLayout();
        port.doLayout();
    }

    private static int yFor(VimTextArea area, int line) {
        try { return area.yForLine(line); }
        catch (BadLocationException bad) { throw new AssertionError(bad); }
    }

    private static int lineStart(VimTextArea area, int line) {
        try { return area.getLineStartOffset(line); }
        catch (BadLocationException bad) { throw new AssertionError(bad); }
    }

    private static String line(VimTextArea area, int line) {
        try { return area.getText(lineStart(area, line), area.getLineEndOffset(line) - lineStart(area, line)).strip(); }
        catch (BadLocationException bad) { throw new AssertionError(bad); }
    }

    /** @return the text of the line {@code area}'s viewport shows first */
    private static String top(VimTextArea area) {
        var port = (JViewport) area.getParent();
        try { return line(area, area.getLineOfOffset(area.viewToModel2D(new Point(0, port.getViewPosition().y)))); }
        catch (BadLocationException bad) { throw new AssertionError(bad); }
    }

    /** A viewport that counts the moves the undo history asks of it, not the scroll pane's echoes of them. */
    private static final class CountingViewport extends JViewport {
        int moves;

        @Override public void setViewPosition(Point position) {
            boolean history = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.skip(1).findFirst())
                .map(caller -> caller.getDeclaringClass() == VimUndoManager.class).orElse(false);
            if (history) moves++;
            super.setViewPosition(position);
        }
    }

    @Test void withVimOffNeighbouringTypingIsOneStepAsInRSyntaxTextArea() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x");
            area.discardAllEdits();
            area.setCaretPosition(1);
            area.replaceSelection("a");
            area.replaceSelection("b");
            area.setCaretPosition(0);
            area.replaceSelection("z");
            area.undoLastAction();
            assertThat(area.getText()).isEqualTo("xab");
            area.undoLastAction();
            assertThat(area.getText()).isEqualTo("x");
        });
    }

    @Test void withVimOnEachStepIsWhatTheEngineBracketed() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x");
            area.discardAllEdits();
            VimUndoManager undo = area.vimUndo();
            undo.vim(true);
            undo.beginStep(0, 1);
            area.setCaretPosition(1);
            area.replaceSelection("a");
            area.setCaretPosition(0);
            area.replaceSelection("z");
            undo.beginStep(0, 0);
            area.replaceSelection("y");
            undo.endStep();
            assertThat(undo.stepOpen()).isTrue();
            undo.endStep();
            area.setCaretPosition(0);
            area.replaceSelection("q");
            assertThat(undo.nextUndo().cursorLine).as("an edit outside a step is a step of its own").isEqualTo(-1);
            area.undoLastAction();
            assertThat(area.getText()).isEqualTo("zyxa");
            assertThat(undo.nextUndo().cursorCol).isEqualTo(1);
            area.undoLastAction();
            assertThat(area.getText()).isEqualTo("x");
            area.redoLastAction();
            assertThat(area.getText()).isEqualTo("zyxa");
        });
    }

    @Test void aStepWithoutEditsLeavesNothingToUndoAndUndoClosesAnOpenStep() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x");
            area.discardAllEdits();
            VimUndoManager undo = area.vimUndo();
            undo.vim(true);
            undo.beginStep(0, 0);
            undo.endStep();
            assertThat(area.canUndo()).isFalse();
            undo.beginStep(0, 0);
            area.replaceSelection("a");
            area.undoLastAction();
            // Bug fix (Task 4 review): undo() reopens the same nesting it closed, so a step open when an outside
            // undo arrives (a real ⌘Z mid-insert) stays open afterward, ready for the engine's own endStep.
            assertThat(undo.stepOpen()).as("the outside undo reopens the step it had to close").isTrue();
            assertThat(area.getText()).isEqualTo("x");
        });
    }

    /** Pane flexibility spec 6: two views of one document share its one history, so an undo in either undoes its last edit. */
    @Test void twoViewsOfADocumentShareOneUndoHistory() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("x");
            a.discardAllEdits();
            var b = new VimTextArea();
            b.share(a);
            assertThat(b.getDocument()).isSameAs(a.getDocument());
            assertThat(b.vimUndo()).isSameAs(a.vimUndo());
            a.setCaretPosition(1);
            a.replaceSelection("a");
            assertThat(a.vimUndo().nextUndo().edits).as("taken once, though each view registers the history with the document")
                .isEqualTo(1);
            b.setCaretPosition(0);
            b.replaceSelection("b");
            assertThat(a.getText()).isEqualTo("bxa");
            a.undoLastAction();
            assertThat(b.getText()).as("the last edit, made in the other view").isEqualTo("xa");
            b.undoLastAction();
            assertThat(a.getText()).isEqualTo("x");
            assertThat(a.canUndo()).isFalse();
            b.redoLastAction();
            a.redoLastAction();
            assertThat(a.getText()).isEqualTo("bxa");
        });
    }

    /** An undo puts only its own view's caret on the change; the other view keeps its selection on the text it was on. */
    @Test void anUndoMovesOnlyItsOwnViewsCaretToTheChange() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("one\ntwo\nthree\n");
            a.discardAllEdits();
            var b = new VimTextArea();
            b.share(a);
            b.select(8, 13);
            a.setCaretPosition(0);
            a.replaceSelection("zero\n");
            assertThat(b.getSelectedText()).as("an edit above moves the other view's selection with its text").isEqualTo("three");
            a.undoLastAction();
            assertThat(a.getCaretPosition()).as("the undoing view's caret goes to the change").isZero();
            assertThat(b.getSelectedText()).as("the other view keeps its selection").isEqualTo("three");
            assertThat(b.getCaretPosition()).isEqualTo(13);
            a.redoLastAction();
            assertThat(b.getSelectedText()).isEqualTo("three");
            assertThat(b.getSelectionStart()).isEqualTo(13);
        });
    }

    /**
     * A Vim change open in one view ends where another view edits, as Vim's u_sync ends a change where the cursor goes to
     * another window: undo takes the other view's edit first, then the change, and the first view's engine still closes
     * what it opened.
     */
    @Test void aVimChangeOpenInOneViewEndsWhereAnotherViewEdits() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("x");
            a.discardAllEdits();
            var b = new VimTextArea();
            b.share(a);
            VimUndoManager undo = a.vimUndo();
            undo.vim(true);
            undo.beginStep(0, 1);
            a.setCaretPosition(1);
            a.replaceSelection("a");
            b.setCaretPosition(0);
            b.replaceSelection("b");
            assertThat(b.getText()).isEqualTo("bxa");
            b.undoLastAction();
            assertThat(a.getText()).as("the other view's edit, the last").isEqualTo("xa");
            assertThat(a.vimUndo().stepOpen()).as("the change stays open for the first view's engine").isTrue();
            a.undoLastAction();
            assertThat(a.getText()).as("then the first view's change").isEqualTo("x");
            a.vimUndo().endStep();
            assertThat(a.vimUndo().stepOpen()).isFalse();
            assertThat(a.canUndo()).isFalse();
            a.redoLastAction();
            a.redoLastAction();
            assertThat(b.getText()).isEqualTo("bxa");
        });
    }

    /** The view a document was first shown in lets go of it: the other keeps the text and its history, and it gets an empty one. */
    @Test void aViewThatLetsGoLeavesTheOtherTheTextAndItsHistory() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("x");
            a.discardAllEdits();
            var b = new VimTextArea();
            b.share(a);
            b.setCaretPosition(1);
            b.replaceSelection("b");
            var shared = b.getDocument();
            a.unshare();
            assertThat(a.getDocument()).isNotSameAs(shared);
            assertThat(a.getText()).isEmpty();
            assertThat(a.canUndo()).as("a history of its own").isFalse();
            a.replaceSelection("z");
            assertThat(b.getText()).isEqualTo("xb");
            assertThat(b.canUndo()).as("the other keeps its history").isTrue();
            b.undoLastAction();
            assertThat(b.getText()).isEqualTo("x");
            assertThat(a.getText()).isEqualTo("z");
            b.unshare();
            assertThat(b.getDocument()).as("the only view keeps its document").isSameAs(shared);
        });
    }

    /**
     * Each view keeps its own scroll position (pane flexibility spec 6): an edit in one view that moves another view's
     * caret leaves that view where it was scrolled, though Swing scrolls a moved caret into sight. Each step runs as an
     * event of its own, so the scrolls Swing queues run between them.
     */
    @Test void anEditInOneViewLeavesTheOtherViewsScrollPosition() throws Exception {
        var a = new java.util.concurrent.atomic.AtomicReference<VimTextArea>();
        var b = new java.util.concurrent.atomic.AtomicReference<VimTextArea>();
        var port = new java.util.concurrent.atomic.AtomicReference<javax.swing.JViewport>();
        onEdt(() -> {
            a.set(new VimTextArea());
            a.get().setText("line\n".repeat(600));
            a.get().discardAllEdits();
            b.set(new VimTextArea());
            b.get().share(a.get());
            var scroll = new org.fife.ui.rtextarea.RTextScrollPane(a.get());
            scroll.setSize(400, 200);
            scroll.doLayout();
            scroll.getViewport().doLayout();
            port.set(scroll.getViewport());
            try { a.get().setCaretPosition(a.get().getLineStartOffset(10)); }
            catch (javax.swing.text.BadLocationException impossible) { throw new AssertionError(impossible); }
        });
        onEdt(() -> port.get().setViewPosition(new java.awt.Point(0, 4500)));
        onEdt(() -> {
            b.get().setCaretPosition(0);
            b.get().replaceSelection("x");
        });
        onEdt(() -> { });
        onEdt(() -> assertThat(port.get().getViewPosition().y).as("the first view stays where it was scrolled").isEqualTo(4500));
    }

    /**
     * Lines added above a Vim view, in another view, leave it on the text it showed and its cursor on its line, as Vim's
     * mark_adjust moves another window's topline with its text: a view put back at its old pixels would show other text,
     * and Vim would pull the cursor into that window.
     */
    @Test void linesAddedAboveAVimViewLeaveItOnItsTextAndItsCursorOnItsLine() throws Exception {
        var shown = new AtomicReference<VimTextArea>();
        var other = new AtomicReference<VimTextArea>();
        var editor = new AtomicReference<VimEditor>();
        onEdt(() -> {
            shown.set(lines());
            other.set(new VimTextArea());
            other.get().share(shown.get());
            scrolled(shown.get(), new JViewport());
            editor.set(new VimEditor(new Vim(VimHost.detached()), shown.get(), EditorDefaults.VIM));
            type(editor.get(), "400Gzt");
        });
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(top(shown.get())).isEqualTo("line 400");
            other.get().setCaretPosition(0);
            other.get().replaceSelection("new\n".repeat(100));
        });
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(top(shown.get())).as("the view shows the same text").isEqualTo("line 400");
            assertThat(line(shown.get(), editor.get().cursor().line)).as("the cursor stays on its text").isEqualTo("line 400");
        });
    }

    /** Lines removed above a view, in another view, leave it on the text it showed, as far into its first line. */
    @Test void linesRemovedAboveAViewLeaveItOnItsText() throws Exception {
        var shown = new AtomicReference<VimTextArea>();
        var other = new AtomicReference<VimTextArea>();
        onEdt(() -> {
            shown.set(lines());
            other.set(new VimTextArea());
            other.get().share(shown.get());
            scrolled(shown.get(), new JViewport());
        });
        onEdt(() -> ((JViewport) shown.get().getParent()).setViewPosition(new Point(0, yFor(shown.get(), 299) + 3)));
        onEdt(() -> {
            other.get().select(lineStart(other.get(), 9), lineStart(other.get(), 59));
            other.get().replaceSelection("");
        });
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(top(shown.get())).as("the view shows the same text").isEqualTo("line 300");
            assertThat(((JViewport) shown.get().getParent()).getViewPosition().y).isEqualTo(yFor(shown.get(), 249) + 3);
        });
    }

    /** An edit inside a view's window, in another view, leaves the view where it is, as an edit below it does. */
    @Test void anEditInsideAViewsWindowLeavesItWhereItIs() throws Exception {
        var shown = new AtomicReference<VimTextArea>();
        var other = new AtomicReference<VimTextArea>();
        var y = new int[1];
        onEdt(() -> {
            shown.set(lines());
            other.set(new VimTextArea());
            other.get().share(shown.get());
            scrolled(shown.get(), new JViewport());
        });
        onEdt(() -> {
            y[0] = yFor(shown.get(), 299);
            ((JViewport) shown.get().getParent()).setViewPosition(new Point(0, y[0]));
        });
        onEdt(() -> {
            other.get().setCaretPosition(lineStart(other.get(), 305));
            other.get().replaceSelection("new\n".repeat(3));
            other.get().setCaretPosition(lineStart(other.get(), 500));
            other.get().replaceSelection("new\n".repeat(3));
        });
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(((JViewport) shown.get().getParent()).getViewPosition().y).isEqualTo(y[0]);
            assertThat(top(shown.get())).isEqualTo("line 300");
        });
    }

    /**
     * Text replaced whole while another view edits, as a reload replaces it, leaves a view on its line, as each caret
     * keeps its line: there is no text left to follow.
     */
    @Test void textReplacedWholeLeavesAViewOnItsLine() throws Exception {
        var shown = new AtomicReference<VimTextArea>();
        var other = new AtomicReference<VimTextArea>();
        onEdt(() -> {
            shown.set(lines());
            other.set(new VimTextArea());
            other.get().share(shown.get());
            scrolled(shown.get(), new JViewport());
        });
        onEdt(() -> ((JViewport) shown.get().getParent()).setViewPosition(new Point(0, yFor(shown.get(), 299) + 3)));
        onEdt(() -> other.get().replaceSelection("x"));
        onEdt(() -> other.get().setText("row\n".repeat(400)));
        onEdt(() -> { });
        onEdt(() -> assertThat(((JViewport) shown.get().getParent()).getViewPosition().y).isEqualTo(yFor(shown.get(), 299) + 3));
    }

    /** An undo in another view that takes lines away above a view leaves the view on its text, its selection kept. */
    @Test void anUndoInAnotherViewLeavesAViewOnItsText() throws Exception {
        var shown = new AtomicReference<VimTextArea>();
        var other = new AtomicReference<VimTextArea>();
        onEdt(() -> {
            shown.set(lines());
            other.set(new VimTextArea());
            other.get().share(shown.get());
            scrolled(shown.get(), new JViewport());
            other.get().setCaretPosition(0);
            other.get().replaceSelection("new\n".repeat(100));
        });
        onEdt(() -> { });
        onEdt(() -> {
            ((JViewport) shown.get().getParent()).setViewPosition(new Point(0, yFor(shown.get(), 499)));
            shown.get().select(lineStart(shown.get(), 500), lineStart(shown.get(), 501));
        });
        onEdt(() -> other.get().undoLastAction());
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(top(shown.get())).as("the view shows the same text").isEqualTo("line 400");
            assertThat(((JViewport) shown.get().getParent()).getViewPosition().y).isEqualTo(yFor(shown.get(), 399));
            assertThat(shown.get().getSelectedText()).isEqualTo("line 401\n");
        });
    }

    /** A compound edit of a thousand changes above two other views puts each back once, after them all, on its text. */
    @Test void aThousandChangesInOneEditPutEachOtherViewBackOnce() throws Exception {
        var editing = new AtomicReference<VimTextArea>();
        var shown = List.of(new VimTextArea(), new VimTextArea());
        var ports = List.of(new CountingViewport(), new CountingViewport());
        onEdt(() -> {
            editing.set(lines());
            for (int n = 0; n < 2; n++) {
                shown.get(n).share(editing.get());
                scrolled(shown.get(n), ports.get(n));
                ports.get(n).setViewPosition(new Point(0, yFor(shown.get(n), 299)));
            }
        });
        onEdt(() -> {
            ports.forEach(port -> port.moves = 0);
            var area = editing.get();
            area.beginAtomicEdit();
            try {
                for (int n = 0; n < 1000; n++) {
                    area.setCaretPosition(lineStart(area, 100));
                    area.replaceSelection("x\n");
                }
            } finally { area.endAtomicEdit(); }
        });
        onEdt(() -> { });
        onEdt(() -> {
            for (int n = 0; n < 2; n++) {
                assertThat(ports.get(n).moves).as("one put-back for view %d", n).isEqualTo(1);
                assertThat(top(shown.get(n))).isEqualTo("line 300");
            }
            editing.get().undoLastAction();
            assertThat(editing.get().getText()).as("the thousand changes are one step").isEqualTo(lines().getText());
        });
    }

    /** A text area shares a document only before Vim turns on in it, since Vim reads the document it shows. */
    @Test void anAreaWithVimOnSharesNoDocument() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            var vimOn = new VimTextArea();
            new VimEditor(new Vim(VimHost.detached()), vimOn, EditorDefaults.VIM);
            assertThatThrownBy(() -> vimOn.share(a)).isInstanceOf(IllegalStateException.class);
            assertThat(vimOn.getDocument()).isNotSameAs(a.getDocument());
        });
    }

    /** A text area that already shares a document, as the first view or another, shares no other. */
    @Test void anAreaThatSharesADocumentSharesNoOther() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            var b = new VimTextArea();
            b.share(a);
            var c = new VimTextArea();
            assertThatThrownBy(() -> b.share(c)).as("another view of a's document").isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> a.share(c)).as("the first view of the document b shares").isInstanceOf(IllegalStateException.class);
            assertThat(b.getDocument()).isSameAs(a.getDocument());
        });
    }

    /** The history made before a document's second view opens is undoable from the new view. */
    @Test void theHistoryBeforeASecondViewIsUndoableFromIt() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("x");
            a.discardAllEdits();
            a.setCaretPosition(1);
            a.replaceSelection("a");
            var b = new VimTextArea();
            b.share(a);
            assertThat(b.canUndo()).isTrue();
            b.undoLastAction();
            assertThat(a.getText()).isEqualTo("x");
            assertThat(a.canUndo()).isFalse();
        });
    }

    /**
     * Each view registers the history with the document once, and a view that lets go takes its registration with it:
     * the middle of three views lets go, then the first, and the last takes each edit once, with nothing left waiting
     * on the document's edits for the others.
     */
    @Test void registrationsStayBalancedAsViewsLetGo() throws Exception {
        onEdt(() -> {
            var a = new VimTextArea();
            a.setText("x");
            a.discardAllEdits();
            var b = new VimTextArea();
            b.share(a);
            var c = new VimTextArea();
            c.share(a);
            var document = (AbstractDocument) a.getDocument();
            VimUndoManager history = c.vimUndo();
            assertThat(registrations(document, history)).isEqualTo(3);
            b.unshare();
            assertThat(registrations(document, history)).isEqualTo(2);
            assertThat(registrations((AbstractDocument) b.getDocument(), history)).isZero();
            a.unshare();
            assertThat(registrations(document, history)).isEqualTo(1);
            assertThat(registrations((AbstractDocument) a.getDocument(), history)).isZero();
            assertThat(document.getDocumentFilter()).as("one view: nothing waits on the document's edits").isNull();
            c.setCaretPosition(1);
            c.replaceSelection("c");
            assertThat(c.vimUndo().nextUndo().edits).isEqualTo(1);
            c.undoLastAction();
            assertThat(c.getText()).isEqualTo("x");
            assertThat(c.canUndo()).isFalse();
        });
    }

    private static long registrations(AbstractDocument document, VimUndoManager history) {
        return Arrays.stream(document.getUndoableEditListeners()).filter(listener -> listener == history).count();
    }
}
