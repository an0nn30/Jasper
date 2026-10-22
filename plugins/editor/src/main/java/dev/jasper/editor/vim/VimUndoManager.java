package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RUndoManager;

/**
 * A document's undo history (spec 3.3), shared by every text area that shows the document (pane flexibility spec 6):
 * an undo in any of them undoes the last edit made in any of them. With Vim off it groups edits as RSyntaxTextArea's
 * own manager does (neighbouring typed characters form one step). With Vim on it never guesses: each Vim change the
 * engine brackets with {@link #beginStep} and {@link #endStep} is one step, and any other edit (the Editor's Replace, a
 * paste through ⌘V) is a step of its own. RSyntaxTextArea's atomic edits inside a Vim step join that step.
 *
 * <p>Each view brackets its own steps. The view that edits now is the one the user last typed in, clicked or focused,
 * or whose Vim engine last asked ({@link #view}); when another view edits, the open steps of the one before end there,
 * as Vim's u_sync ends a change when the cursor goes to another window. An edit leaves every other view's scroll
 * position where it was, and an undo or redo moves only its own view's caret to the change: every other view keeps its
 * caret, selection and scroll position.
 */
final class VimUndoManager extends RUndoManager {
    /** One undo step, remembering where the cursor was before it, for Vim's cursor placement after undo. */
    static final class Step extends CompoundEdit {
        int cursorLine, cursorCol;
        int edits;
        /** Its change number, counting every step added (Vim's uh_seq). */
        int seq;

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

    /** One view's open steps and whether Vim groups its edits. */
    private static final class View {
        final RTextArea area;
        boolean vim;
        Step vimStep, internal;
        int stepDepth, internalDepth;

        View(RTextArea area) { this.area = area; }
    }

    private final List<View> views = new ArrayList<>();
    /** The view that edits now. */
    private View view;
    private Step typing;
    private int lastOffset;
    /** The change numbers of the steps on the stack, oldest first; the first {@link #done} are done. */
    private final List<Integer> seqs = new ArrayList<>();
    private int done, seqLast;
    /** The last edit taken: every view registers this history with the document, which hands it each edit once per view. */
    private UndoableEdit heard;
    /** While a view lets go of the document ({@link #leave}): its text area's swap discards nothing here. */
    private boolean leaving;

    VimUndoManager(RTextArea area) {
        // RUndoManager keeps its area only for the grouping and atomic edits this class overrides, so it is given none: the
        // history outlives the view it was made for, which may close first.
        super(null);
        view = new View(area);
        views.add(view);
        setLimit(1000);
    }

    /**
     * {@code area} shows this history's document too, and edits through it from now on.
     *
     * @param area a text area that has not shown the document before
     */
    void join(RTextArea area) { find(area); }

    /** @return whether more than one text area shows the document */
    boolean shared() { return views.size() > 1; }

    /**
     * {@code area} stops showing the document. Its open steps end here, and {@code swap}, which gives it a document of
     * its own, discards nothing of this history; the first of the other views edits next.
     *
     * @param area one of the views, not the only one
     * @param swap gives the area another document
     */
    void leave(RTextArea area, Runnable swap) {
        View leaving = views.stream().filter(candidate -> candidate.area == area).findFirst().orElse(null);
        if (leaving == null || views.size() == 1) return;
        closeAll(leaving);
        typing = seal(typing);
        views.remove(leaving);
        if (view == leaving) view = views.getFirst();
        this.leaving = true;
        try { swap.run(); } finally { this.leaving = false; }
    }

    /**
     * {@code area} edits from now on. When another view edited before, its open steps that changed something end here
     * and open again, empty, at the same depth, so its engine's own {@link #endStep} still balances; the typing step ends
     * too.
     *
     * @param area the text area that edits now
     */
    void view(RTextArea area) {
        if (view.area == area) return;
        View next = find(area);
        sync(view);
        typing = seal(typing);
        view = next;
    }

    /** @param on whether Vim mode groups the edits of the view that edits now */
    void vim(boolean on) {
        view.vim = on;
        typing = seal(typing);
    }

    /**
     * The cursor moved before the open step's first change (an operator goes to its span's start, as Vim's
     * do_pending_operator does before u_save): the step records the cursor there.
     */
    void stepCursor(int cursorLine, int cursorCol) {
        if (view.vimStep != null && view.vimStep.edits == 0) {
            view.vimStep.cursorLine = cursorLine;
            view.vimStep.cursorCol = cursorCol;
        }
    }

    /** Opens a Vim step, or nests inside the open one. */
    void beginStep(int cursorLine, int cursorCol) { beginStep(view, cursorLine, cursorCol); }

    private void beginStep(View in, int cursorLine, int cursorCol) {
        if (in.stepDepth++ == 0) {
            typing = seal(typing);
            in.vimStep = new Step(cursorLine, cursorCol);
        }
    }

    /** Closes one level; the outermost close puts the step on the undo stack if it changed anything. */
    void endStep() { endStep(view); }

    private void endStep(View in) {
        if (in.stepDepth == 0 || --in.stepDepth > 0) return;
        Step step = in.vimStep;
        in.vimStep = null;
        step.end();
        if (step.edits > 0) add(step);
        updateActions();
    }

    /** Adds a new step to the stack, numbering it; the steps that were undone are gone. */
    private void add(Step step) {
        step.seq = ++seqLast;
        while (seqs.size() > done) seqs.removeLast();
        seqs.add(step.seq);
        done++;
        if (seqs.size() > getLimit()) {
            seqs.removeFirst();
            done--;
        }
        addEdit(step);
    }

    /** @return the change numbers of the done steps, oldest first */
    List<Integer> doneSeqs() { return List.copyOf(seqs.subList(0, done)); }

    /** @return the change numbers of the undone steps, in the order redo brings them back */
    List<Integer> undoneSeqs() { return List.copyOf(seqs.subList(done, seqs.size())); }

    boolean stepOpen() { return view.stepDepth > 0; }

    /** u_sync: the open step ends here if it changed anything, and a new one goes on at the same depth. */
    void sync() { sync(view); }

    private void sync(View in) {
        if (in.vimStep == null || in.vimStep.edits == 0) return;
        int depth = in.stepDepth;
        closeAll(in);
        reopen(in, depth);
    }

    /** @return the step {@link #undo} would undo, or null when there is none or it is not a Vim step */
    Step nextUndo() { return editToBeUndone() instanceof Step step ? step : null; }

    /** @return the step {@link #redo} would redo, or null */
    Step nextRedo() { return editToBeRedone() instanceof Step step ? step : null; }

    @Override public void undoableEditHappened(UndoableEditEvent event) {
        UndoableEdit edit = event.getEdit();
        if (edit == heard) return;
        heard = edit;
        if (views.size() > 1) keepOtherViewports(view);
        if (view.vimStep != null) {
            view.vimStep.take(edit);
            return;
        }
        if (view.internalDepth > 0) {
            view.internal.take(edit);
            return;
        }
        if (view.vim) {
            Step step = new Step(-1, -1);
            step.take(edit);
            step.end();
            add(step);
            updateActions();
            return;
        }
        // RSyntaxTextArea's grouping: an edit next to the previous one continues its step.
        int caret = view.area.getCaretPosition();
        if (typing != null && Math.abs(caret - lastOffset) <= 1) {
            typing.take(edit);
            lastOffset = caret;
            return;
        }
        typing = seal(typing);
        typing = new Step(-1, -1);
        typing.take(edit);
        add(typing);
        lastOffset = caret;
        updateActions();
    }

    @Override public void beginInternalAtomicEdit() {
        if (view.internalDepth++ == 0 && view.vimStep == null) {
            typing = seal(typing);
            view.internal = new Step(-1, -1);
        }
    }

    @Override public void endInternalAtomicEdit() { endInternal(view); }

    private void endInternal(View in) {
        if (in.internalDepth == 0 || --in.internalDepth > 0 || in.internal == null) return;
        Step step = in.internal;
        in.internal = null;
        step.end();
        if (step.edits > 0) add(step);
        updateActions();
    }

    /** Counts an open step's edits, since RTextArea asks before it undoes (⌘Z in the middle of an insert). */
    @Override public synchronized boolean canUndo() {
        return view.vimStep != null && view.vimStep.edits > 0 || view.internal != null && view.internal.edits > 0 || super.canUndo();
    }

    /**
     * Nothing is redoable while an open step has edits: closing it for the redo would put it on the undo stack and
     * trim the redo stack, so the redo would fail (an outside ⌘⇧Z in the middle of an insert).
     */
    @Override public synchronized boolean canRedo() {
        if (view.vimStep != null && view.vimStep.edits > 0 || view.internal != null && view.internal.edits > 0) return false;
        return super.canRedo();
    }

    /**
     * An outside undo (⌘Z) while the engine has a step open: closes it so it is undoable, then reopens the same
     * nesting once the undo runs, so the engine's own {@link #endStep} later balances (spec: the rest of an insert
     * in progress stays one step). The other views keep their carets, selections and scroll positions.
     */
    @Override public void undo() {
        View in = view;
        int depth = in.stepDepth;
        closeAll(in);
        Runnable keep = keepOthers(in);
        try {
            super.undo();
            done--;
        } finally {
            keep.run();
            reopen(in, depth);
        }
    }

    /** The redo counterpart of {@link #undo}. */
    @Override public void redo() {
        View in = view;
        int depth = in.stepDepth;
        closeAll(in);
        Runnable keep = keepOthers(in);
        try {
            super.redo();
            done++;
        } finally {
            keep.run();
            reopen(in, depth);
        }
    }

    /**
     * Every view but {@code acting} keeps its scroll position through an edit: an edit before a view's caret moves the
     * caret, which Swing then scrolls into sight later, through the event queue; the view's place is put back after that.
     */
    private void keepOtherViewports(View acting) {
        for (View other : views) {
            if (other == acting || !(other.area.getParent() instanceof JViewport port)) continue;
            java.awt.Point shown = port.getViewPosition();
            SwingUtilities.invokeLater(() -> { if (other.area.getParent() == port) port.setViewPosition(shown); });
        }
    }

    /**
     * Where each view but {@code acting} has its caret, selection and scroll position: an undo or redo sets every caret
     * on the document to the change (Swing's {@code DefaultCaret} reads it from the undo's event), and the views that
     * did not undo go back to the text they were on, as an edit elsewhere leaves them.
     *
     * @return puts them back
     */
    private Runnable keepOthers(View acting) {
        List<Runnable> back = new ArrayList<>();
        for (View other : views) {
            if (other == acting) continue;
            RTextArea area = other.area;
            try {
                Position dot = area.getDocument().createPosition(area.getCaret().getDot());
                Position mark = area.getDocument().createPosition(area.getCaret().getMark());
                JViewport port = area.getParent() instanceof JViewport parent ? parent : null;
                java.awt.Point shown = port == null ? null : port.getViewPosition();
                back.add(() -> {
                    area.getCaret().setDot(mark.getOffset());
                    area.getCaret().moveDot(dot.getOffset());
                    // The caret shows itself later, through the event queue: the view's place is put back after that.
                    if (port != null) SwingUtilities.invokeLater(() -> { if (area.getParent() == port) port.setViewPosition(shown); });
                });
            } catch (BadLocationException impossible) {
                // The caret's offsets are in the document.
            }
        }
        return () -> back.forEach(Runnable::run);
    }

    /** Reopens {@code depth} levels of Vim step after an outside undo/redo, cursor taken from the area's caret now. */
    private void reopen(View in, int depth) {
        if (depth == 0) return;
        int line = 0, col = 0;
        try {
            int offset = in.area.getCaretPosition();
            line = in.area.getLineOfOffset(offset);
            col = offset - in.area.getLineStartOffset(line);
        } catch (BadLocationException ignored) {
            // Falls back to (0, 0); the reopened step's remembered cursor is only advisory.
        }
        for (int n = 0; n < depth; n++) beginStep(in, line, col);
    }

    @Override public void discardAllEdits() {
        if (leaving) return;
        for (View each : views) {
            each.stepDepth = each.internalDepth = 0;
            each.vimStep = each.internal = null;
        }
        typing = null;
        heard = null;
        seqs.clear();
        done = 0;
        seqLast = 0;
        super.discardAllEdits();
    }

    /** Ends every open step of a view, as an undo from outside Vim (⌘Z during an insert) must. */
    private void closeAll(View in) {
        while (in.stepDepth > 0) endStep(in);
        if (in.internal != null) {
            in.internalDepth = 1;
            endInternal(in);
        }
        typing = seal(typing);
    }

    /** @return the view for {@code area}, a new one when it has none */
    private View find(RTextArea area) {
        for (View each : views) if (each.area == area) return each;
        View added = new View(area);
        views.add(added);
        return added;
    }

    private static Step seal(Step step) {
        if (step != null) step.end();
        return null;
    }
}
