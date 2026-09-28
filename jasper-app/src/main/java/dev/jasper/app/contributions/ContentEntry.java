package dev.jasper.app.contributions;

import dev.jasper.app.lifecycle.Subscription;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;

/** One live content component and its presentation; moving never replaces this object. EDT only. */
public final class ContentEntry implements AutoCloseable {
    private final ContentRegistry registry;
    private final String plugin, key;
    private final JComponent component;
    private final List<Runnable> changed = new ArrayList<>(), disposed = new ArrayList<>();
    private final List<Consumer<Boolean>> activation = new ArrayList<>();
    private String title, detail="", location="", host="";
    private Icon icon;
    private boolean dirty, closed, active;
    private int pins;
    private UUID window;
    private Runnable focus;
    private List<ContentCommand> commands=List.of();
    private List<ContentStatus> status=List.of();
    ContentEntry(ContentRegistry registry,String plugin,String key,String title,JComponent component) {
        this.registry=registry;this.plugin=plugin;this.key=key;this.title=title;this.component=component;
        focus=component::requestFocusInWindow;
    }
    public String plugin(){return plugin;} public String key(){return key;}
    public String title(){return title;} public String detail(){return detail;} public Icon icon(){return icon;}
    public String location(){return location;} public String host(){return host;} public boolean dirty(){return dirty;}
    public boolean closed(){return closed;} public boolean active(){return active;} public UUID window(){return window;}
    public JComponent component(){return component;}
    public List<ContentCommand> commands(){return commands;} public List<ContentStatus> status(){return status;}
    public Subscription pin() { pins++; return new Subscription(() -> pins--); }
    public boolean pinned() { return pins > 0; }
    public boolean detached(){return registry.detached(window);}
    public void presentation(String title,String detail,Icon icon,boolean dirty,String location,String host){
        if(closed)return; this.title=Objects.requireNonNull(title);this.detail=Objects.requireNonNull(detail);
        this.icon=icon;this.dirty=dirty;this.location=Objects.requireNonNull(location);this.host=Objects.requireNonNull(host);fire();
    }
    public void commands(List<ContentCommand> value){if(!closed){commands=List.copyOf(value);fire();}}
    public void status(List<ContentStatus> value){if(!closed){status=List.copyOf(value);fire();}}
    public void focusTarget(Runnable value){if(!closed)focus=Objects.requireNonNull(value);}
    public void focus(){if(!closed)focus.run();}
    public void select(){registry.select(this);}
    public void move(boolean separate){registry.move(this,separate);}
    public java.util.concurrent.CompletableFuture<Boolean> requestClose(){return registry.requestClose(List.of(this));}
    public Subscription onChanged(Runnable listener){if(closed)return new Subscription(()->{});changed.add(listener);return new Subscription(()->changed.remove(listener));}
    public Subscription onClosed(Runnable listener){if(closed)return new Subscription(()->{});disposed.add(listener);return new Subscription(()->disposed.remove(listener));}
    public Subscription onActive(Consumer<Boolean> listener){if(closed)return new Subscription(()->{});activation.add(listener);return new Subscription(()->activation.remove(listener));}
    public void active(boolean value){if(closed||active==value)return;active=value;for(var c:List.copyOf(activation))c.accept(value);}
    void window(UUID value){window=value;fire();}
    private void fire(){for(var listener:List.copyOf(changed))listener.run();}
    void dispose(){if(closed)return;active(false);closed=true;for(var listener:List.copyOf(disposed))listener.run();changed.clear();disposed.clear();activation.clear();}
    @Override public void close(){registry.remove(this);}
}
