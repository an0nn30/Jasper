package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JComponent;

/**
 * Recorded host content, retaining the actual component across moves. Its tab is a content tab of the scripted
 * workspace (workspace tabs spec 14): it opens, is selected, moves and closes on the tab topics like any tab, and moves to
 * a new child window or the main window as the application's do (spec 6.5).
 */
final class FakeContent implements ContentTab {
    final FakeUi ui;
    final String key;
    final JComponent component;
    /** The workspace tab that shows this content. */
    final FakeWorkspace.Tab tab;
    ContentPresentation presentation;
    List<ContentCommand> commands=List.of();
    List<ContentStatus> status=List.of();
    WindowOwner owner;
    boolean active,closed;
    int pins;
    Runnable focus=()->{};
    final List<Consumer<Boolean>> activated=new ArrayList<>();
    final List<Runnable> disposed=new ArrayList<>();
    FakeContent(FakeUi ui,ContentSpec spec,WindowOwner owner,JComponent component,FakeWorkspace.Tab tab){
        this.ui=ui;this.key=spec.key();this.owner=owner;this.component=component;this.tab=tab;
        presentation=new ContentPresentation(spec.title(),"",null,false,"","");
    }
    public void setPresentation(ContentPresentation value){if(!closed){ui.context.kit().validateIcon(value.icon());presentation=value;tab.title=value.title();}}
    public void setCommands(List<ContentCommand> values){if(!closed)commands=List.copyOf(values);}
    public void setStatus(List<ContentStatus> values){if(!closed)status=List.copyOf(values);}
    public void setFocusTarget(Runnable value){if(!closed)focus=value;}
    public void select(){if(closed)return;ui.host.workspace.select(tab);active(true);focus.run();}
    void active(boolean value){if(active==value)return;active=value;for(var listener:List.copyOf(activated))listener.accept(value);}
    /** Neither closed, pinned by a modal prompt, nor waiting on a close decision. */
    private boolean movable(){return !closed&&pins==0&&ui.pendingContent==null;}
    public void moveToNewWindow(){
        // A child window's only tab already is its own window.
        if(!movable()||!tab.window.main&&tab.window.tabs.size()==1)return;
        moveTo(ui.host.workspace.addWindow());
    }
    public void moveToMainWindow(){
        if(!movable()||tab.window.main)return;
        moveTo(ui.host.workspace.main().orElseGet(ui.host.workspace::addWindow));
    }
    private void moveTo(FakeWorkspace.Window window){
        active(false);
        ui.host.workspace.move(tab, window);
        owner=ui.context.terminals.windowHandle(window.id); select();
    }
    public WindowHandle window(){return ui.context.terminals.windowHandle(tab.window.id);}
    public boolean isActive(){return active;} public boolean isClosed(){return closed;}
    public CompletableFuture<Boolean> requestClose(){return ui.closeContent(List.of(this),true);}
    public Subscription onActive(Consumer<Boolean> value){activated.add(value);return ()->activated.remove(value);}
    public Subscription onClosed(Runnable value){disposed.add(value);return ()->disposed.remove(value);}
    public void close(){if(closed)return;active(false);closed=true;ui.contents.remove(this);ui.host.workspace.close(tab);for(var listener:List.copyOf(disposed))listener.run();disposed.clear();activated.clear();}
}
