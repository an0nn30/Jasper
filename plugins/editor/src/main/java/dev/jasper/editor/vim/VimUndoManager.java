package dev.jasper.editor.vim;

import javax.swing.UIManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.BadLocationException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RUndoManager;

/**
 * The text area's undo manager (spec 3.3). With Vim off it groups edits as RSyntaxTextArea's own manager does
 * (neighbouring typed characters form one step). With Vim on it never guesses: each Vim change the engine brackets
 * with {@link #beginStep} and {@link #endStep} is one step, and any other edit (the Editor's Replace, a paste
 * through ⌘V) is a step of its own. RSyntaxTextArea's atomic edits inside a Vim step join that step.
 */
final class VimUndoManager extends RUndoManager {
    /** One undo step, remembering where the cursor was before it, for Vim's cursor placement after undo. */
    static final class Step extends CompoundEdit {
        final int cursorLine, cursorCol;
        int edits;

        Step(int cursorLine, int cursorCol) {
            this.cursorLine = cursorLine;
            this.cursorCol = cursorCol;
        }

        void take(UndoableEdit edit) {
            addEdit(edit);
            edits++;
        }

        // The manager treats a step as finished; addEdit above still works until end() because it reads the field.
        @Override public boolean isInProgress() { return false; }
        @Override public String getUndoPresentationName() { return UIManager.getString("AbstractUndoableEdit.undoText"); }
        @Override public String getRedoPresentationName() { return UIManager.getString("AbstractUndoableEdit.redoText"); }
    }

    private final RTextArea area;
    private boolean vim;
    private Step vimStep, internal, typing;
    private int stepDepth, internalDepth, lastOffset;

    VimUndoManager(RTextArea area) {
        super(area);
        this.area = area;
        setLimit(1000);
    }

    /** @param on whether Vim mode groups the edits */
    void vim(boolean on) {
        vim = on;
        typing = seal(typing);
    }

    /** Opens a Vim step, or nests inside the open one. */
    void beginStep(int cursorLine, int cursorCol) {
        if (stepDepth++ == 0) {
            typing = seal(typing);
            vimStep = new Step(cursorLine, cursorCol);
        }
    }

    /** Closes one level; the outermost close puts the step on the undo stack if it changed anything. */
    void endStep() {
        if (stepDepth == 0 || --stepDepth > 0) return;
        Step step = vimStep;
        vimStep = null;
        step.end();
        if (step.edits > 0) addEdit(step);
        updateActions();
    }

    boolean stepOpen() { return stepDepth > 0; }

    /** @return the step {@link #undo} would undo, or null when there is none or it is not a Vim step */
    Step nextUndo() { return editToBeUndone() instanceof Step step ? step : null; }

    /** @return the step {@link #redo} would redo, or null */
    Step nextRedo() { return editToBeRedone() instanceof Step step ? step : null; }

    @Override public void undoableEditHappened(UndoableEditEvent event) {
        UndoableEdit edit = event.getEdit();
        if (vimStep != null) {
            vimStep.take(edit);
            return;
        }
        if (internalDepth > 0) {
            internal.take(edit);
            return;
        }
        if (vim) {
            Step step = new Step(-1, -1);
            step.take(edit);
            step.end();
            addEdit(step);
            updateActions();
            return;
        }
        // RSyntaxTextArea's grouping: an edit next to the previous one continues its step.
        int caret = area.getCaretPosition();
        if (typing != null && Math.abs(caret - lastOffset) <= 1) {
            typing.take(edit);
            lastOffset = caret;
            return;
        }
        typing = seal(typing);
        typing = new Step(-1, -1);
        typing.take(edit);
        addEdit(typing);
        lastOffset = caret;
        updateActions();
    }

    @Override public void beginInternalAtomicEdit() {
        if (internalDepth++ == 0 && vimStep == null) {
            typing = seal(typing);
            internal = new Step(-1, -1);
        }
    }

    @Override public void endInternalAtomicEdit() {
        if (internalDepth == 0 || --internalDepth > 0 || internal == null) return;
        Step step = internal;
        internal = null;
        step.end();
        if (step.edits > 0) addEdit(step);
        updateActions();
    }

    /** Counts an open step's edits, since RTextArea asks before it undoes (⌘Z in the middle of an insert). */
    @Override public synchronized boolean canUndo() {
        return vimStep != null && vimStep.edits > 0 || internal != null && internal.edits > 0 || super.canUndo();
    }

    /**
     * Nothing is redoable while an open step has edits: closing it for the redo would put it on the undo stack and
     * trim the redo stack, so the redo would fail (an outside ⌘⇧Z in the middle of an insert).
     */
    @Override public synchronized boolean canRedo() {
        if (vimStep != null && vimStep.edits > 0 || internal != null && internal.edits > 0) return false;
        return super.canRedo();
    }

    /**
     * An outside undo (⌘Z) while the engine has a step open: closes it so it is undoable, then reopens the same
     * nesting once the undo runs, so the engine's own {@link #endStep} later balances (spec: the rest of an insert
     * in progress stays one step).
     */
    @Override public void undo() {
        int depth = stepDepth;
        closeAll();
        try { super.undo(); }
        finally { reopen(depth); }
    }

    /** The redo counterpart of {@link #undo}. */
    @Override public void redo() {
        int depth = stepDepth;
        closeAll();
        try { super.redo(); }
        finally { reopen(depth); }
    }

    /** Reopens {@code depth} levels of Vim step after an outside undo/redo, cursor taken from the area's caret now. */
    private void reopen(int depth) {
        if (depth == 0) return;
        int line = 0, col = 0;
        try {
            int offset = area.getCaretPosition();
            line = area.getLineOfOffset(offset);
            col = offset - area.getLineStartOffset(line);
        } catch (BadLocationException ignored) {
            // Falls back to (0, 0); the reopened step's remembered cursor is only advisory.
        }
        for (int n = 0; n < depth; n++) beginStep(line, col);
    }

    @Override public void discardAllEdits() {
        stepDepth = internalDepth = 0;
        vimStep = internal = typing = null;
        super.discardAllEdits();
    }

    /** Ends every open step, as an undo from outside Vim (⌘Z during an insert) must. */
    private void closeAll() {
        while (stepDepth > 0) endStep();
        if (internal != null) {
            internalDepth = 1;
            endInternalAtomicEdit();
        }
        typing = seal(typing);
    }

    private static Step seal(Step step) {
        if (step != null) step.end();
        return null;
    }
}
