package dev.jasper.editor.ui;

import dev.jasper.sdk.ui.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;

/**
 * Shared find/replace model. The active document tab renders its descriptor subtree. The source and the Basic preview
 * answer at once; the Chromium preview answers later, on the UI thread, so each question is numbered and an answer that
 * arrives after a newer question, or after the bar closed, changes nothing. A cancelled answer, as when the preview
 * closes, changes nothing either.
 */
public final class FindBar {
    static final String ROOT="find.bar", FIND="find.query", REPLACE="find.replacement", CASE="find.case", WORD="find.word", REGEX="find.regex";
    static final String NEXT="find.next", PREVIOUS="find.previous", ONE="find.one", ALL="find.all", CLOSE="find.close";
    static final String ROW="find.row", REPLACE_ROW="find.replace.row", STATUS="find.status";
    private final Supplier<Optional<FindTarget>> target;
    private final Runnable changed;
    private final Consumer<String> focus;
    private final Runnable revealSource;
    private FindTarget captured;
    private final Runnable previewChanged = this::update;
    private String find="", replace="", status="";
    private boolean matchCase, wholeWord, regex, visible, withReplace, typed;
    /** How many times the bar opened, the count was asked for and a match was stepped to: each answer's number. */
    private long opened, counted, stepped;
    /** The count asked for last, which a step asked meanwhile waits for: the Chromium preview answers one at a time. */
    private CompletableFuture<?> counting = CompletableFuture.completedFuture(null);

    public FindBar(Supplier<Optional<FindTarget>> target, Runnable changed, Consumer<String> focus) {
        this(target,changed,focus,() -> {});
    }
    FindBar(Supplier<Optional<FindTarget>> target, Runnable changed, Consumer<String> focus, Runnable revealSource) {
        this.target=target; this.changed=changed; this.focus=focus; this.revealSource=revealSource;
    }
    /**
     * Opens the bar on the pane that has the focus, with its selection as the query when it is one line. A selection the
     * pane reads later takes the query when it arrives, unless the user typed meanwhile.
     */
    public void open(boolean withReplace) {
        detachPreview();
        captured = target.get().filter(FindTarget::available).orElse(null);
        capturedPreview().ifPresent(surface -> surface.onInstalled(previewChanged));
        long opening = ++opened;
        typed = false;
        this.withReplace=withReplace; visible=true;
        if (captured != null) {
            var selection = captured.selectedText();
            if (selection.isDone()) take(selection, this::seed);
            else answer(selection, () -> opening == opened && !typed, selected -> {
                if (seed(selected)) { update(); changed.run(); focus.accept(FIND); }
            });
        }
        update(); focus.accept(FIND);
    }
    /** @return whether {@code selected}, one line, became the query */
    private boolean seed(String selected) {
        if (selected.isEmpty() || selected.contains("\n")) return false;
        find = selected;
        return true;
    }
    public void close() {
        var restore = activeTarget();
        dismiss();
        restore.or(() -> target.get().filter(FindTarget::available)).ifPresent(FindTarget::focus);
    }
    /** Ends captured ownership without moving focus during tab/pane transitions. */
    void dismiss() {
        if (!visible) return;
        visible=false; detachPreview(); captured=null; counting=CompletableFuture.completedFuture(null); changed.run();
    }
    /** @param owner a pane that no longer shows: a target's {@link FindTarget#owner()} */
    void paneHidden(Object owner) {
        if (captured != null && captured.owner() == owner) dismiss();
    }
    private void detachPreview() {
        if (captured instanceof FindTarget.Source source) source.area().clearMarkAllHighlights();
        capturedPreview().ifPresent(surface -> { surface.clearFind(); surface.removeInstalled(previewChanged); });
    }
    private Optional<PreviewSurface> capturedPreview() {
        return captured instanceof FindTarget.Preview preview ? Optional.of(preview.surface()) : Optional.empty();
    }
    private Optional<FindTarget> activeTarget() {
        return (visible ? Optional.ofNullable(captured) : target.get()).filter(FindTarget::available);
    }
    private Optional<RSyntaxTextArea> source() {
        return activeTarget().filter(FindTarget.Source.class::isInstance).map(value -> ((FindTarget.Source)value).area());
    }
    boolean isVisible() { return visible; }
    boolean replaceVisible() { return withReplace; }
    String status() { return status; }
    private void status(String value) { status=value; changed.run(); }

    boolean event(UiEvent event) {
        if (!visible) return false;
        if (event instanceof UiEvent.Edited edit) {
            if (edit.id().equals(FIND)) { find=edit.text(); typed=true; update(); return true; }
            if (edit.id().equals(REPLACE)) { replace=edit.text(); changed.run(); return true; }
        }
        if (!(event instanceof UiEvent.Action action)) return false;
        switch (action.id()) {
            case NEXT -> next(); case PREVIOUS -> previous(); case ONE -> replaceOne(); case ALL -> replaceAll(); case CLOSE -> close();
            case CASE -> { matchCase=!matchCase; update(); } case WORD -> { wholeWord=!wholeWord; update(); } case REGEX -> { regex=!regex; update(); }
            default -> { return false; }
        }
        return true;
    }
    /**
     * One compact document bar row (the kit's DOCUMENT split presentation): the query takes the width the match options
     * (toggle buttons), the match count and the navigation and close icon buttons leave; the replace row lines its field
     * up under the query and its actions under the options. Enter and Shift+Enter in the query go to the next and previous
     * match and Escape anywhere in the bar closes it; each icon button names its action and key in its tooltip, which is
     * also its accessible name.
     */
    Nodes.Stack node() {
        var row=new Nodes.Stack(ROW,ControlState.READY,Nodes.LayoutRole.INLINE,List.of(
            field(FIND,find,"Find",Nodes.FieldKind.SEARCH,NEXT,PREVIOUS),
            toggle(CASE,"match.case","Match Case",matchCase), toggle(WORD,"match.word","Whole Word",wholeWord), toggle(REGEX,"regex","Regular Expression",regex),
            new Nodes.Text(STATUS,ControlState.READY,status,Nodes.TextRole.CAPTION),
            icon(PREVIOUS,"chevron.up","Previous Match (Shift+Enter)"), icon(NEXT,"chevron.down","Next Match (Enter)"), icon(CLOSE,"close","Close (Escape)")));
        var replacement=new Nodes.Stack(REPLACE_ROW,new ControlState(withReplace,true,false,""),Nodes.LayoutRole.INLINE,List.of(
            field(REPLACE,replace,"Replace",Nodes.FieldKind.TEXT,ONE,""),button(ONE,"Replace"),button(ALL,"Replace All")));
        return new Nodes.Stack(ROOT,new ControlState(visible,true,false,""),Nodes.LayoutRole.VERTICAL,List.of(row,replacement),CLOSE);
    }
    private static Nodes.Field field(String id,String value,String label,Nodes.FieldKind kind,String enter,String alternate) {
        return new Nodes.Field(id,ControlState.READY,kind,value,label,label,enter,Optional.empty(),Nodes.FieldPresentation.EMBEDDED,alternate,CLOSE,"");
    }
    private static Nodes.Button button(String id,String label) { return new Nodes.Button(id,ControlState.READY,label,"",Nodes.ButtonKind.SECONDARY,id,""); }
    private static Nodes.Button icon(String id,String icon,String tooltip) { return new Nodes.Button(id,ControlState.READY,"",icon,Nodes.ButtonKind.GHOST,id,tooltip); }
    private static Nodes.Button toggle(String id,String icon,String tooltip,boolean selected) {
        return new Nodes.Button(id,ControlState.READY,"",icon,Nodes.ButtonKind.GHOST,id,tooltip,Nodes.ButtonPresentation.STANDARD,Optional.empty(),Optional.of(selected));
    }
    public void next() { search(true); }
    public void previous() { search(false); }

    private Optional<RSyntaxTextArea> replacementSource() {
        var owner=source();
        if (owner.isEmpty()) return owner;
        revealSource.run();
        // Revalidate after revealing: a shared bar must never edit a different selected tab.
        return owner.filter(area -> target.get().filter(FindTarget::available)
            .map(value -> value.owner() == area).orElse(false));
    }
    public void replaceOne() {
        replacementSource().ifPresent(area -> guarded(() -> {
            SearchContext context = sourceContext(true);
            context.setReplaceWith(replace);
            if (SearchEngine.replace(area, context).wasFound()) update();
            else status("No matches");
        }));
    }

    public void replaceAll() {
        replacementSource().ifPresent(area -> guarded(() -> {
            SearchContext context = sourceContext(true);
            context.setReplaceWith(replace);
            status("Replaced " + SearchEngine.replaceAll(area, context).getCount());
        }));
    }

    private void update() {
        long asked = ++counted;
        counting = CompletableFuture.completedFuture(null);
        var value = activeTarget();
        if (value.isEmpty() || find.isEmpty()) {
            source().ifPresent(RSyntaxTextArea::clearMarkAllHighlights);
            capturedPreview().ifPresent(PreviewSurface::clearFind);
            status(""); return;
        }
        // Clear old ranges even when the new expression is invalid.
        source().ifPresent(RSyntaxTextArea::clearMarkAllHighlights);
        capturedPreview().ifPresent(PreviewSurface::clearFind);
        switch (value.get()) {
            case FindTarget.Source source -> guarded(() -> count(SearchEngine.markAll(source.area(), sourceContext(true)).getMarkedCount()));
            case FindTarget.Preview preview -> {
                var count = preview.surface().markAll(context(true));
                counting = count;
                answer(count, () -> asked == counted, this::count);
                // The options' toggles show now, and the count once it arrives.
                if (!count.isDone()) changed.run();
            }
        }
    }
    private void count(int count) { status(count == 0 ? "No matches" : count == 1 ? "1 match" : count + " matches"); }

    private void search(boolean forward) {
        if (find.isEmpty()) return;
        long asked = ++stepped, queried = counted;
        activeTarget().ifPresent(value -> {
            switch (value) {
                case FindTarget.Source source -> guarded(() -> {
                    if (!SearchEngine.find(source.area(), sourceContext(forward)).wasFound()) status("No matches");
                });
                case FindTarget.Preview preview -> {
                    // No newer step or query since, and the preview still open.
                    BooleanSupplier current = () -> asked == stepped && queried == counted && value.available();
                    Runnable step = () -> answer(preview.surface().find(context(forward)), current, found -> {
                        if (!found) status("No matches");
                    });
                    // A step would cancel the count the page has not answered yet: it follows the count instead.
                    if (counting.isDone()) step.run();
                    else counting.whenComplete((count, failure) -> late(() -> { if (visible && current.getAsBoolean()) step.run(); }));
                }
            }
        });
    }

    /**
     * RSyntaxTextArea's SearchEngine swallows an invalid regex internally (returns no match instead of throwing), so the
     * source's pattern is validated here and guarded() reports it. SearchEngine's regex path wraps the pattern in
     * "\b...\b" when whole-word is on (getNextMatchPosRegExImpl), so this validates exactly what it compiles. A preview
     * decides itself: Basic compiles its own pattern, and Chromium's page its RegExp.
     */
    private SearchContext sourceContext(boolean forward) {
        if (regex) Pattern.compile(wholeWord ? "\\b" + find + "\\b" : find);
        return context(forward);
    }

    private SearchContext context(boolean forward) {
        var context = new SearchContext(find);
        context.setMatchCase(matchCase);
        context.setWholeWord(wholeWord);
        context.setRegularExpression(regex);
        context.setSearchForward(forward);
        context.setSearchWrap(true);
        context.setMarkAll(true);
        return context;
    }

    private void guarded(Runnable search) {
        try { search.run(); }
        catch (PatternSyntaxException bad) { status("Invalid pattern"); }
    }

    /**
     * Hands {@code then} a target's answer: at once when it is ready, as the source's and the Basic preview's are, else
     * when it arrives, on the UI thread, if the bar still shows and {@code current} still holds. A refused pattern shows
     * "Invalid pattern"; a cancelled answer changes nothing; any other failure is thrown, by a late answer to the UI
     * thread's uncaught-exception handler.
     */
    private <T> void answer(CompletableFuture<T> future, BooleanSupplier current, Consumer<T> then) {
        if (future.isDone()) take(future, then);
        else future.whenComplete((value, failure) -> late(() -> { if (visible && current.getAsBoolean()) take(future, then); }));
    }
    /**
     * Runs work a late answer brings. What it throws goes to the thread's uncaught-exception handler, as a throw on the
     * UI thread would, not into the future {@code whenComplete} returns, which nothing reads.
     */
    private static void late(Runnable work) {
        try { work.run(); }
        catch (RuntimeException | Error failure) {
            Thread thread = Thread.currentThread();
            thread.getUncaughtExceptionHandler().uncaughtException(thread, failure);
        }
    }
    private <T> void take(CompletableFuture<T> future, Consumer<T> then) {
        final T value;
        try { value = future.join(); }
        catch (CancellationException cancelled) { return; }
        catch (CompletionException failure) {
            if (failure.getCause() instanceof PatternSyntaxException) { status("Invalid pattern"); return; }
            if (failure.getCause() instanceof CancellationException) return;
            throw failure;
        }
        then.accept(value);
    }

}
