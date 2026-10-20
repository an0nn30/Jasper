package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.sdk.ui.ContentStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.text.BadLocationException;

/** Document status descriptions; the app creates and styles the actual controls. */
public final class StatusLine {
    private final Runnable languageAction, wrapAction, vimAction;
    private String notice="",position="",language="",encoding="",separator="",indent="";
    private boolean ready,wrap,vim;
    /** @param chooseLanguage opens the language chooser
     * @param toggleWrap toggles soft wrapping
     * @param toggleVim toggles Vim mode */
    public StatusLine(Runnable chooseLanguage,Runnable toggleWrap,Runnable toggleVim){languageAction=chooseLanguage;wrapAction=toggleWrap;vimAction=toggleVim;}
    /** @param tab selected document, or empty
     * @param tabSize configured indentation width
     * @param vimOn whether Vim mode is on */
    public void display(Optional<EditorTab> tab,int tabSize,boolean vimOn){
        vim=vimOn;
        ready=tab.isPresent()&&tab.get().session().state()==DocumentSession.State.READY;
        if(tab.isEmpty()){notice="";return;}
        var session=tab.get().session();var area=tab.get().area();
        notice=session.saving()?"Saving…":tab.get().navigationNotice().or(() -> session.notice()).orElse("");
        if(!ready)return;
        int line=area.getCaretLineNumber(),start;
        try{start=area.getLineStartOffset(line);}catch(BadLocationException impossible){start=0;}
        position=(tab.get().markdown() != null && tab.get().markdown().mode() == dev.jasper.editor.markdown.MarkdownMode.PREVIEW ? "Source " : "")+"Ln "+(line+1)+", Col "+(area.getCaretPosition()-start+1);
        language=Languages.name(session.style());encoding=session.format().label();
        separator=(session.mixed()?"Mixed → ":"")+session.format().separator().label();
        indent=(session.tabs()?"Tab: ":"Spaces: ")+tabSize;wrap=session.wrap();
    }
    /** @return immutable descriptions for the host status bar */
    public List<ContentStatus> items(){
        var items=new ArrayList<ContentStatus>();
        if(!notice.isEmpty())items.add(item(notice,null));
        if(ready){items.add(item(position,null));items.add(item(language,languageAction));items.add(item(encoding,null));
            items.add(item(separator,null));items.add(item(indent,null));items.add(item(wrap?"Wrap: On":"Wrap: Off",wrapAction));
            items.add(item(vim?"Vim: On":"Vim: Off",vimAction));}
        return List.copyOf(items);
    }
    private static ContentStatus item(String text,Runnable action){return new ContentStatus(text,text,action);}
    String positionText(){return position;} String languageText(){return language;} String encodingText(){return encoding;}
    String separatorText(){return separator;} String indentText(){return indent;} String noticeText(){return notice;}
    boolean wrapSelected(){return wrap;} boolean vimSelected(){return vim;}
}
