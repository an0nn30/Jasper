package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JComponent;

/** Recorded host content, retaining the actual component across moves. */
final class FakeContent implements ContentTab {
    final FakeUi ui;
    final String key;
    final JComponent component;
    ContentPresentation presentation;
    List<ContentCommand> commands=List.of();
    List<ContentStatus> status=List.of();
    WindowOwner owner;
    boolean active,closed,separate;
    int pins;
    Runnable focus=()->{};
    final List<Consumer<Boolean>> activated=new ArrayList<>();
    final List<Runnable> disposed=new ArrayList<>();
    FakeContent(FakeUi ui,ContentSpec spec,WindowOwner owner,JComponent component){
        this.ui=ui;this.key=spec.key();this.owner=owner;this.component=component;
        presentation=new ContentPresentation(spec.title(),"",null,false,"","");
    }
    public void setPresentation(ContentPresentation value){if(!closed)presentation=value;}
    public void setCommands(List<ContentCommand> values){if(!closed)commands=List.copyOf(values);}
    public void setStatus(List<ContentStatus> values){if(!closed)status=List.copyOf(values);}
    public void setFocusTarget(Runnable value){if(!closed)focus=value;}
    public void select(){if(closed)return;for(var value:ui.host.contentTabs()) { var other=(FakeContent)value; if(other!=this && Objects.equals(owner, other.owner))other.active(false); }active(true);focus.run();}
    void active(boolean value){if(active==value)return;active=value;for(var listener:List.copyOf(activated))listener.accept(value);}
    public void move(boolean value){if(!closed&&pins==0&&ui.pendingContent==null&&separate!=value){
        active(false); separate=value;
        var window = value ? ui.host.workspace.addWindow() : ui.host.workspace.windows.stream().filter(w -> w.open && !w.detached).findFirst().orElseGet(ui.host.workspace::addWindow);
        if(value)window.detached=true;
        owner=ui.context.terminals.windowHandle(window.id); select();
    }}
    public boolean isSeparate(){return separate;} public boolean isActive(){return active;} public boolean isClosed(){return closed;}
    public CompletableFuture<Boolean> requestClose(){return ui.closeContent(List.of(this),true);}
    public Subscription onActive(Consumer<Boolean> value){activated.add(value);return ()->activated.remove(value);}
    public Subscription onClosed(Runnable value){disposed.add(value);return ()->disposed.remove(value);}
    public void close(){if(closed)return;active(false);closed=true;ui.contents.remove(this);for(var listener:List.copyOf(disposed))listener.run();disposed.clear();activated.clear();}
}
