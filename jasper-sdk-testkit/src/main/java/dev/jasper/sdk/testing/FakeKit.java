package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.*;
import java.awt.EventQueue;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Functional, EDT-confined plugin kit; construct freely, then operate on the EDT. */
public final class FakeKit implements UiKit, AutoCloseable {
    private final String owner;
    private final Set<FakeKitView> views = new LinkedHashSet<>();
    private final Map<String,IconAsset> icons = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    public FakeKit(String owner) { this.owner=Objects.requireNonNull(owner); }
    static void edt() { if(!EventQueue.isDispatchThread()) throw new IllegalStateException("Kit requires EDT"); }
    void guard() { edt(); if(closed.get()) throw new IllegalStateException("Kit is closed"); }
    void failed() { failures.add("Plugin kit callback failed"); }
    void removed(FakeKitView view) { views.remove(view); }
    @Override public FakeKitView create(ViewSpec spec, Consumer<UiEvent> events) { guard();var view=new FakeKitView(this,spec,events);views.add(view);return view; }
    @Override public Subscription registerIcon(IconAsset asset) {
        guard();Objects.requireNonNull(asset);
        if(!asset.name().startsWith(owner+":")) throw new IllegalArgumentException("Foreign icon namespace");
        if(icons.putIfAbsent(asset.name(),asset)!=null) throw new IllegalArgumentException("Duplicate icon");
        var done=new AtomicBoolean();return () -> { if(done.compareAndSet(false,true)) { Runnable remove=() -> icons.remove(asset.name(),asset); if(EventQueue.isDispatchThread())remove.run();else EventQueue.invokeLater(remove); } };
    }
    /** Inspect currently owned live views. */
    public List<FakeKitView> views() { edt();return List.copyOf(views); }
    /** Inspect registered paths. */
    public Map<String,IconAsset> icons() { edt();return Map.copyOf(icons); }
    /** Sanitized diagnostics never retain exceptions or input content. */
    public List<String> failures() { edt();return List.copyOf(failures); }
    /** Test mounting seam: foreign and disposed handles are rejected. */
    public void requireOwned(UiView view) { guard();if(!(view instanceof FakeKitView fake) || fake.owner!=this)throw new IllegalArgumentException("Foreign view");fake.guard(); }
    @Override public void close() { if(!closed.compareAndSet(false,true))return;Runnable cleanup=() -> {for(var view:List.copyOf(views))view.close();icons.clear();}; if(EventQueue.isDispatchThread())cleanup.run();else EventQueue.invokeLater(cleanup); }
}
