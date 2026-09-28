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
}
