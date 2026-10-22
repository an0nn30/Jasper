package dev.jasper.editor.vim;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
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
 * or whose Vim engine last asked ({@link #view(RTextArea)}); when another view edits, the open steps of the one before
 * end there, as Vim's u_sync ends a change when the cursor goes to another window. An edit that code makes into another
 * view without any of those first (a Vim mapping's timeout after the focus moved) is the current view's: its caret
 * groups it and its Vim step takes it.
 *
 * <p>Every other view stays on the text it showed. An edit that adds or removes lines above a view's first shown line
 * moves the view with its text, as Vim's mark_adjust moves another window's topline; an edit inside or below its window
 * leaves it where it is. Two edits depart from mark_adjust: an insert exactly at the start of the first shown line
 * keeps that line on top, where Vim shows the new line, and an edit that takes away the first shown line's start shows
 * the text after the deletion, where Vim shows the line before it. Text replaced whole (a reload) leaves each view on
 * its line's number, as each caret keeps its line. A Vim view's cursor, which moves with its text as Vim's w_cursor
 * does, stays on it: when the edit leaves it outside the window, the window scrolls as little as Vim's update_topline
 * scrolls it into sight, with 'scrolloff'; a view without Vim keeps its first line, and its caret may leave it. An undo
 * or redo moves only its own view's caret to the change: every other view keeps its caret, its selection and its
 * place on the text.
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

        /**
         * @param undo whether the step is undone, or else redone
         * @param length the document's length now
         * @return whether undoing or redoing the step takes away the whole text at some point, as undoing a paste over
         *     Select All does
         */
        boolean takesWhole(boolean undo, int length) {
            // CompoundEdit's list of edits: this class's own field of that name counts them.
            List<UndoableEdit> order = new ArrayList<>(super.edits);
            if (undo) java.util.Collections.reverse(order);
            for (UndoableEdit edit : order) {
                if (!(edit instanceof DocumentEvent change)) continue;
                // An undo takes out what an insert put in, a redo what a removal took.
                if ((change.getType() == DocumentEvent.EventType.INSERT) == undo) {
                    if (change.getOffset() == 0 && change.getLength() == length) return true;
                    length -= change.getLength();
                } else {
                    length += change.getLength();
                }
            }
            return false;
        }

        // The manager treats a step as finished; addEdit above still works until end() because it reads the field.
        @Override public boolean isInProgress() { return false; }
        @Override public String getUndoPresentationName() { return UIManager.getString("AbstractUndoableEdit.undoText"); }
        @Override public String getRedoPresentationName() { return UIManager.getString("AbstractUndoableEdit.redoText"); }
    }

    /** One view's open steps, whether Vim groups its edits, and its place on the text while another view edits. */
    private static final class View {
        final RTextArea area;
        boolean vim;
        Step vimStep, internal;
        int stepDepth, internalDepth;
        /** Where the view showed the text before the edits its queued put-back answers; null when none is queued. */
        Place held;
        /** The event its latest put-back was queued in, and that put-back's number: only the latest one acts. */
        AWTEvent queuedIn;
        int queued;

        View(RTextArea area) { this.area = area; }
    }

    /**
     * Where a view shows the text: its viewport's position, the start of the first line it shows, as a position that
     * moves with the text, and how far below that line's top the viewport begins. When the whole text is replaced
     * (a reload), there is no text to follow, and the place keeps the line's number instead, as each caret keeps its
     * line.
     *
     * @param number the line's number, which the place keeps once the whole text is replaced; -1 before
     */
    private record Place(JViewport port, Point shown, Position line, int number, int below) {
        /** @return where {@code area} shows the text now, or null when it is not in a viewport */
        static Place of(RTextArea area) {
            if (!(area.getParent() instanceof JViewport port)) return null;
            Point shown = port.getViewPosition();
            try {
                int line = area.getLineOfOffset(Math.max(0, area.viewToModel2D(new Point(0, shown.y))));
                int top = area.yForLine(line);
                Position start = area.getDocument().createPosition(area.getLineStartOffset(line));
                return new Place(port, shown, start, -1, top < 0 ? 0 : shown.y - top);
            } catch (BadLocationException impossible) {
                // The offsets are the document's own; Swing's own scrolling then stands.
                return null;
            }
        }

        /** @return this place on the line that starts at {@code start} */
        Place at(Position start) { return new Place(port, shown, start, number, below); }

        /** @return this place, keeping its line's number from now on: the whole text is about to be replaced */
        Place byNumber(RTextArea area) {
            return number >= 0 ? this : new Place(port, shown, line, area.getDocument().getDefaultRootElement().getElementIndex(line.getOffset()), below);
        }

        /** @return the viewport position that shows the place's line where it was shown, wherever the line is now */
        Point now(RTextArea area) {
            try {
                int at = number >= 0 ? Math.min(number, area.getLineCount() - 1) : area.getLineOfOffset(line.getOffset());
                int top = area.yForLine(at);
                // A line folded away has no place: the view stays where it was.
                return top < 0 ? shown : new Point(shown.x, top + below);
            } catch (BadLocationException impossible) {
                return shown;
            }
        }
    }

    /**
     * While several views show the document, it hands each edit here first: each view but the one that edits notes its
     * place before the edit and queues its put-back after it. Any filter the document had goes on working inside it.
     */
    private final class Places extends DocumentFilter {
        private interface Edit { void run() throws BadLocationException; }

        private final AbstractDocument document;
        private final DocumentFilter inner;
        /**
         * Text inserted at the document's start, by an edit or an undo: a Swing position at offset 0 stays there, where
         * one anywhere else moves past text inserted at it, so a place held at the start moves past it here.
         */
        private final DocumentListener start = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { if (event.getOffset() == 0) insertedAtStart(event.getLength()); }
            @Override public void removeUpdate(DocumentEvent event) { }
            @Override public void changedUpdate(DocumentEvent event) { }
        };

        Places(AbstractDocument document) {
            this.document = document;
            inner = document.getDocumentFilter();
            document.setDocumentFilter(this);
            document.addDocumentListener(start);
        }

        /** The document hands its edits to the filter it had before, and nothing here listens to it. */
        void lift() {
            if (document.getDocumentFilter() == this) document.setDocumentFilter(inner);
            document.removeDocumentListener(start);
        }

        private void insertedAtStart(int length) {
            for (View other : views) {
                if (other.held == null || other.held.number() >= 0 || other.held.line().getOffset() != 0) continue;
                try { other.held = other.held.at(document.createPosition(length)); }
                catch (BadLocationException impossible) {
                    // The inserted text is in the document.
                }
            }
        }

        /** @param whole whether the edit takes away the whole text */
        private void around(boolean whole, Edit edit) throws BadLocationException {
            View acting = view;
            hold(acting, whole);
            try { edit.run(); }
            finally { release(acting); }
        }

        @Override public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws BadLocationException {
            around(false, () -> {
                if (inner == null) bypass.insertString(offset, text, attributes);
                else inner.insertString(bypass, offset, text, attributes);
            });
        }

        @Override public void remove(FilterBypass bypass, int offset, int length) throws BadLocationException {
            around(offset == 0 && length == document.getLength(), () -> {
                if (inner == null) bypass.remove(offset, length);
                else inner.remove(bypass, offset, length);
            });
        }

        @Override public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
            around(offset == 0 && length == document.getLength(), () -> {
                if (inner == null) bypass.replace(offset, length, text, attributes);
                else inner.replace(bypass, offset, length, text, attributes);
            });
        }
    }

    private final List<View> views = new ArrayList<>();
    /** The document's filter while several views show it. */
    private Places places;
    /** How many put-backs have been queued: one for each other view in each event that edits. */
    private int putBacksQueued;
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
    void join(RTextArea area) {
        find(area);
        if (places == null && views.size() > 1) places = new Places((AbstractDocument) views.getFirst().area.getDocument());
    }

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
        // Its queued put-back, if any, does nothing: the place is on the document it leaves.
        leaving.held = null;
        views.remove(leaving);
        if (view == leaving) view = views.getFirst();
        if (views.size() == 1) {
            places.lift();
            places = null;
        }
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

    /** @return whether {@code area} is the view that edits now */
    boolean editsNow(RTextArea area) { return view.area == area; }

    /** @return whether {@code area}'s view holds its place on the text for a put-back that has not run yet */
    boolean holds(RTextArea area) {
        for (View each : views) if (each.area == area) return each.held != null;
        return false;
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

    /** @return how many put-backs have been queued, one for each other view in each event that edits the document */
    int putBacksQueued() { return putBacksQueued; }

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
     * in progress stays one step). The other views keep their carets, selections and places on the text.
     */
    @Override public void undo() {
        View in = view;
        int depth = in.stepDepth;
        closeAll(in);
        Runnable keep = keepOthers(in, editToBeUndone() instanceof Step step && step.takesWhole(true, in.area.getDocument().getLength()));
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
        Runnable keep = keepOthers(in, editToBeRedone() instanceof Step step && step.takesWhole(false, in.area.getDocument().getLength()));
        try {
            super.redo();
            done++;
        } finally {
            keep.run();
            reopen(in, depth);
        }
    }

    /**
     * Before an edit: each view but {@code acting} that holds no place yet notes where it shows the text. A place held
     * from an edit before, whose put-back has not run, stays: its line has moved with the text since.
     *
     * @param whole whether the edit takes away the whole text, so that the places keep their lines' numbers
     */
    private void hold(View acting, boolean whole) {
        for (View other : views) {
            if (other == acting) continue;
            if (other.held == null) other.held = Place.of(other.area);
            if (whole && other.held != null) other.held = other.held.byNumber(other.area);
        }
    }

    /**
     * After an edit: each view but {@code acting} that holds a place queues its put-back, once in each event. Swing
     * scrolls a view's moved caret into sight later, through the event queue, after each edit of the event; the put-back
     * goes through the queue twice, so it runs after everything the event queued, its later edits' scrolling among it,
     * and only the last one queued puts the view back.
     */
    private void release(View acting) {
        AWTEvent now = EventQueue.getCurrentEvent();
        for (View other : views) {
            if (other == acting || other.held == null || now != null && now == other.queuedIn) continue;
            other.queuedIn = now;
            int number = ++other.queued;
            putBacksQueued++;
            SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> putBack(other, number)));
        }
    }

    /**
     * Shows {@code other}'s held place's line where it was shown, unless a later put-back is queued for it; a Vim view's
     * engine then keeps its cursor in sight.
     */
    private static void putBack(View other, int number) {
        Place place = other.held;
        if (number != other.queued || place == null) return;
        other.held = null;
        // The event goes with the put-back: for typing, its source is the text area that edited.
        other.queuedIn = null;
        RTextArea area = other.area;
        if (area.getParent() != place.port()) return;
        Point position = place.now(area);
        if (area instanceof VimTextArea text) text.keepPlace(position);
        else place.port().setViewPosition(position);
    }

    /**
     * Where each view but {@code acting} has its caret, selection and place on the text: an undo or redo sets every
     * caret on the document to the change (Swing's {@code DefaultCaret} reads it from the undo's event), and the views
     * that did not undo go back to the text they were on, as an edit elsewhere leaves them. An undo does not reach the
     * document's filter, so the places are held here.
     *
     * @param whole whether the undo or redo takes away the whole text, so that the places keep their lines' numbers
     * @return puts them back
     */
    private Runnable keepOthers(View acting, boolean whole) {
        hold(acting, whole);
        List<Runnable> back = new ArrayList<>();
        for (View other : views) {
            if (other == acting) continue;
            RTextArea area = other.area;
            try {
                Position dot = area.getDocument().createPosition(area.getCaret().getDot());
                Position mark = area.getDocument().createPosition(area.getCaret().getMark());
                back.add(() -> {
                    area.getCaret().setDot(mark.getOffset());
                    area.getCaret().moveDot(dot.getOffset());
                });
            } catch (BadLocationException impossible) {
                // The caret's offsets are in the document.
            }
        }
        return () -> {
            back.forEach(Runnable::run);
            // After the carets: each shows itself later, through the event queue, and the put-backs run after that.
            release(acting);
        };
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
