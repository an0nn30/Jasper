package dev.jasper.editor.ui;

import dev.jasper.sdk.ui.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

/** Shared find/replace model. The active document tab renders its descriptor subtree. */
public final class FindBar {
    static final String ROOT="find.bar", FIND="find.query", REPLACE="find.replacement", CASE="find.case", WORD="find.word", REGEX="find.regex";
    static final String NEXT="find.next", PREVIOUS="find.previous", ONE="find.one", ALL="find.all", CLOSE="find.close";
    private final Supplier<Optional<RSyntaxTextArea>> target;
    private final Runnable changed;
    private final Consumer<String> focus;
    private String find="", replace="", status="";
    private boolean matchCase, wholeWord, regex, visible, withReplace;

    public FindBar(Supplier<Optional<RSyntaxTextArea>> target, Runnable changed, Consumer<String> focus) {
        this.target=target; this.changed=changed; this.focus=focus;
    }
    public void open(boolean withReplace) {
        target.get().ifPresent(area -> {
            String selected=area.getSelectedText();
            if (selected!=null && !selected.isEmpty() && !selected.contains("\n")) find=selected;
        });
        this.withReplace=withReplace; visible=true; update(); focus.accept(FIND);
    }
    public void close() {
        visible=false; changed.run();
        target.get().ifPresent(area -> { area.clearMarkAllHighlights(); area.requestFocusInWindow(); });
    }
    boolean isVisible() { return visible; }
    boolean replaceVisible() { return withReplace; }
    String status() { return status; }
    private void status(String value) { status=value; changed.run(); }

    boolean event(UiEvent event) {
        if (!visible) return false;
        if (event instanceof UiEvent.Edited edit) {
            if (edit.id().equals(FIND)) { find=edit.text(); update(); return true; }
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
    Nodes.Stack node() {
        var row=new Nodes.Stack("find.row",ControlState.READY,Nodes.LayoutRole.INLINE,List.of(
            field(FIND,find,"Find",NEXT,PREVIOUS), button(PREVIOUS,"Previous"), button(NEXT,"Next"),
            toggle(CASE,"Aa","Match case",matchCase), toggle(WORD,"W","Whole word",wholeWord), toggle(REGEX,".*","Regular expression",regex),
            new Nodes.Text("find.status",ControlState.READY,status,Nodes.TextRole.CAPTION),button(CLOSE,"Close")));
        var replacement=new Nodes.Stack("find.replace.row",new ControlState(withReplace,true,false,""),Nodes.LayoutRole.INLINE,List.of(
            field(REPLACE,replace,"Replace",ONE,""),button(ONE,"Replace"),button(ALL,"Replace All")));
        return new Nodes.Stack(ROOT,new ControlState(visible,true,false,""),Nodes.LayoutRole.VERTICAL,List.of(row,replacement),CLOSE);
    }
    private static Nodes.Field field(String id,String value,String label,String enter,String alternate) {
        return new Nodes.Field(id,ControlState.READY,Nodes.FieldKind.SEARCH,value,label,label,enter,Optional.empty(),Nodes.FieldPresentation.EMBEDDED,alternate,CLOSE,"");
    }
    private static Nodes.Button button(String id,String label) { return new Nodes.Button(id,ControlState.READY,label,"",Nodes.ButtonKind.GHOST,id,""); }
    private static Nodes.Button toggle(String id,String label,String tooltip,boolean selected) {
        return new Nodes.Button(id,ControlState.READY,label,"",Nodes.ButtonKind.GHOST,id,tooltip,Nodes.ButtonPresentation.STANDARD,Optional.empty(),Optional.of(selected));
    }
    public void next() { search(true); }
    public void previous() { search(false); }

    public void replaceOne() {
        target.get().ifPresent(area -> guarded(() -> {
            SearchContext context = context(true);
            context.setReplaceWith(replace);
            if (SearchEngine.replace(area, context).wasFound()) update();
            else status("No matches");
        }));
    }

    public void replaceAll() {
        target.get().ifPresent(area -> guarded(() -> {
            SearchContext context = context(true);
            context.setReplaceWith(replace);
            status("Replaced " + SearchEngine.replaceAll(area, context).getCount());
        }));
    }

    private void update() {
        Optional<RSyntaxTextArea> area = target.get();
        if (area.isEmpty() || find.isEmpty()) {
            area.ifPresent(RSyntaxTextArea::clearMarkAllHighlights);
            status("");
            return;
        }
        guarded(() -> {
            int count = SearchEngine.markAll(area.get(), context(true)).getMarkedCount();
            status(count == 0 ? "No matches" : count == 1 ? "1 match" : count + " matches");
        });
    }

    private void search(boolean forward) {
        if (find.isEmpty()) return;
        target.get().ifPresent(area -> guarded(() -> {
            SearchResult result = SearchEngine.find(area, context(forward));
            if (!result.wasFound()) status("No matches");
        }));
    }

    private SearchContext context(boolean forward) {
        // RSyntaxTextArea's SearchEngine swallows an invalid regex internally (returns no match
        // instead of throwing), so validate the pattern ourselves and let guarded() report it.
        // SearchEngine's regex path wraps the pattern in "\b...\b" when whole-word is on
        // (getNextMatchPosRegExImpl), so validate exactly what it compiles, not the raw text.
        if (regex) Pattern.compile(wholeWord ? "\\b" + find + "\\b" : find);
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

}
