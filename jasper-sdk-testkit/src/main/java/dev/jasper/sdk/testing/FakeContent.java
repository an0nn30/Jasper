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
    /** How the tab comes back when Jasper restores its windows; empty while it does not (workspace tabs spec 13). */
    Optional<dev.jasper.sdk.RestoreState> restore;
    WindowOwner owner;
    /** The owned view the tab shows; null for raw content. */
    FakeKitView view;
    boolean active,closed;
    int pins;
    Runnable focus=()->{};
    final List<Consumer<Boolean>> activated=new ArrayList<>();
    final List<Runnable> disposed=new ArrayList<>();
    FakeContent(FakeUi ui,ContentSpec spec,WindowOwner owner,JComponent component,FakeWorkspace.Tab tab){
        this.ui=ui;this.key=spec.key();this.owner=owner;this.component=component;this.tab=tab;
        presentation=new ContentPresentation(spec.title(),"",null,false,"","");
        restore=spec.restore();
    }
    public void setPresentation(ContentPresentation value){if(!closed){ui.context.kit().validateIcon(value.icon());presentation=value;tab.title=value.title();}}
    public void setCommands(List<ContentCommand> values){if(!closed)commands=List.copyOf(values);}
    public void setStatus(List<ContentStatus> values){if(!closed)status=List.copyOf(values);}
    public void setFocusTarget(Runnable value){if(!closed)focus=value;}
    public void setRestore(Optional<dev.jasper.sdk.RestoreState> value){Objects.requireNonNull(value,"value");if(!closed)restore=value;}
    public void select(){if(closed)return;ui.host.workspace.select(tab);active(true);focus.run();}
    void active(boolean value){if(active==value)return;active=value;for(var listener:List.copyOf(activated))listener.accept(value);}
    /**
     * Neither closed nor itself waiting on a close decision: a pane may open beside it, as the application's registry
     * decides for each tab.
     */
    private boolean splittable(){return !closed&&!ui.pendingTabs.contains(this);}
    /** Splittable, not pinned by a modal prompt, and not a restorer's tab while its restore runs (6b Task 1's review). */
    private boolean movable(){return splittable()&&pins==0&&(ui.host.workspace.slot==null||ui.host.workspace.slot.taken!=tab);}
    public void moveToNewWindow(){
        // A child window's only tab already is its own window.
        if(!movable()||!tab.window.main&&tab.window.tabs.size()==1)return;
        moveTo(ui.host.workspace.addWindow());
    }
    public void moveToMainWindow(){
        if(!movable()||tab.window.main)return;
        moveTo(ui.host.workspace.main().orElseGet(ui.host.workspace::addWindow));
    }
    /** Tabs and panes spec 9: its tab's only pane, a pinned item and one waiting on a close decision stay. */
    public boolean mayMoveToNewTab(){return movable()&&tab.group.recent.size()>1;}
    public void moveToNewTab(){if(!mayMoveToNewTab())return;ui.host.workspace.moveToNewTab(tab);select();}
    /** Pane flexibility spec 4: its window's other tabs, numbered from 1 in strip order; none for an item that may not move. */
    public List<TabChoice> tabChoices(){
        if(!movable())return List.of();
        var strip=ui.host.workspace.strip(tab.window);
        var choices=new ArrayList<TabChoice>();
        for(int i=0;i<strip.size();i++)if(strip.get(i)!=tab.group)choices.add(new TabChoice(i+1,capped(strip.get(i).recent.getFirst().title)));
        return List.copyOf(choices);
    }
    /** Past 200 characters a title's start and an ellipsis within that limit, as Jasper's menus cut a tab's title. */
    private static String capped(String title){
        if(title.length()<=200)return title;
        int end=Character.isHighSurrogate(title.charAt(198))?198:199;
        return title.substring(0,end)+"…";
    }
    /** Move Pane to Tab: a number it offers moves its pane right after that tab's shown item, where it is selected. */
    public void moveToTab(int number){
        if(tabChoices().stream().noneMatch(choice->choice.number()==number))return;
        ui.host.workspace.moveToTab(tab,number);select();
    }
    /** Spec 8: selected first, then the terminal beside it, or the split item's action, as the application runs them. */
    public void split(SplitChoice choice,dev.jasper.sdk.terminal.Direction direction){
        Objects.requireNonNull(choice,"choice");Objects.requireNonNull(direction,"direction");
        // Unguarded, so a closed content tab's split does nothing, as every other method of a closed handle does.
        if(!ui.splitChoices().contains(choice))throw new IllegalArgumentException("Not one of Jasper's split choices: "+choice.label());
        boolean terminal=choice.equals(SplitChoice.TERMINAL);
        // Rulings 19: a local shell needs terminal.open, refused first, whatever the tab's state, as Terminals refuses.
        if(terminal)ui.context.terminals.require(dev.jasper.sdk.Capabilities.TERMINAL_OPEN);
        // A closed tab, or one waiting on a close decision, splits nothing, as it moves nowhere.
        if(!splittable())return;
        select();
        if(terminal){
            ui.host.workspace.openRequests.add("beside|"+tab.id+"|"+direction);
            var local=(dev.jasper.sdk.terminal.OpenRequest.Local)dev.jasper.sdk.terminal.OpenRequest.local();
            ui.host.workspace.focus(ui.host.workspace.openTerminal(tab.window,"opened",FakeTerminals.openedInfo(local),tab));
            return;
        }
        ui.host.invoke(direction==dev.jasper.sdk.terminal.Direction.RIGHT?choice.rightAction():choice.downAction(),tab.window.id,null);
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
