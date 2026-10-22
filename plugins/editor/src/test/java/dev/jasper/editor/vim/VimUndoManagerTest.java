package dev.jasper.editor.vim;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VimUndoManagerTest {
    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

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
}
