package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.vim.Mode;
import dev.jasper.sdk.ui.ContentStatus;
import dev.jasper.sdk.ui.ContentStatus.MenuEntry;
import dev.jasper.sdk.ui.IconAsset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.text.BadLocationException;

/** Document status descriptions; the app creates and styles the actual controls. */
public final class StatusLine {
    /** The Vim widget's icon, a V, which the Editor registers. */
    public static final IconAsset VIM_ICON=new IconAsset("dev.jasper.editor:vim","M 3 3 L 8 13 L 13 3");
    private final Runnable languageAction, wrapAction, vimAction, openVimrc, reloadVimrc, showSettings;
    private String notice="",position="",language="",encoding="",separator="",indent="";
    private boolean ready,wrap,vim;
    private Mode mode=Mode.NORMAL;
    /** @param chooseLanguage opens the language chooser
     * @param toggleWrap toggles soft wrapping
     * @param toggleVim toggles Vim mode
     * @param openVimrc opens the vimrc
     * @param reloadVimrc reads the vimrc again
     * @param showSettings opens the Editor's Settings page */
    public StatusLine(Runnable chooseLanguage,Runnable toggleWrap,Runnable toggleVim,Runnable openVimrc,Runnable reloadVimrc,Runnable showSettings){
        languageAction=chooseLanguage;wrapAction=toggleWrap;vimAction=toggleVim;
        this.openVimrc=openVimrc;this.reloadVimrc=reloadVimrc;this.showSettings=showSettings;}
    /** @param tab selected document, or empty
     * @param tabSize configured indentation width
     * @param vimOn whether Vim mode is on
     * @param vimMode the selected document's Vim mode */
    public void display(Optional<EditorTab> tab,int tabSize,boolean vimOn,Mode vimMode){
        vim=vimOn;mode=vimMode;
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
            items.add(new ContentStatus(vim?word(mode):"","Vim mode",null,VIM_ICON.name(),!vim,List.of(MenuEntry.check("Enabled",vim,vimAction),
                MenuEntry.item("Open vimrc",openVimrc),MenuEntry.item("Reload vimrc",reloadVimrc),MenuEntry.separatorLine(),
                MenuEntry.item("Settings…",showSettings))));}
        return List.copyOf(items);
    }
    /** The mode as IdeaVim's widget names it (Vim IdeaVim keys spec 4). */
    private static String word(Mode mode){
        return switch(mode){
            case NORMAL,OPERATOR_PENDING->"NORMAL";case INSERT->"INSERT";case REPLACE->"REPLACE";case INSERT_NORMAL->"(INSERT)";
            case VISUAL->"VISUAL";case VISUAL_LINE->"V-LINE";case VISUAL_BLOCK->"V-BLOCK";case COMMAND_LINE->"COMMAND";
        };
    }
    private static ContentStatus item(String text,Runnable action){return new ContentStatus(text,text,action);}
    String positionText(){return position;} String languageText(){return language;} String encodingText(){return encoding;}
    String separatorText(){return separator;} String indentText(){return indent;} String noticeText(){return notice;}
    boolean wrapSelected(){return wrap;} boolean vimSelected(){return vim;}
}
