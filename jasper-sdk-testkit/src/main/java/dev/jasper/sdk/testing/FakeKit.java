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
    private java.awt.Font contentFont=new java.awt.Font(java.awt.Font.SANS_SERIF,java.awt.Font.PLAIN,13);
    java.awt.Font contentFont(){return contentFont;}
    /**
     * Sets the font this kit gives custom content, as Jasper gives it the interface's body font and follows changes
     * to that font. Content registered from now on receives it, and content already registered in this kit's views
     * is set to it at once, firing its {@code font} property change. Until this is called the font is plain 13-point
     * {@code SansSerif}. UI thread only.
     *
     * @param font the body font to apply
     * @throws IllegalStateException off the event dispatch thread or once the kit is closed
     */
    public void contentFont(java.awt.Font font){guard();contentFont=Objects.requireNonNull(font);for(var view:List.copyOf(views))view.contentFont(font);}

    Object anchorHost;
    private final Set<FakeKitView> views = new LinkedHashSet<>();
    private final Map<String,IconAsset> icons = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    /**
     * A kit for one plugin. Each {@link FakePluginHost} plugin context has its own; a test of a view alone can make one.
     *
     * @param owner the plugin's id; icons registered through this kit must be named {@code <owner>:<name>}
     */
    public FakeKit(String owner) { this.owner=Objects.requireNonNull(owner); }
    static void edt() { if(!EventQueue.isDispatchThread()) throw new IllegalStateException("Kit requires EDT"); }
    void guard() { edt(); if(closed.get()) throw new IllegalStateException("Kit is closed"); }
    private static final Set<String> BUILT_INS=Set.of("editor.source","editor.split","editor.preview","editor.scroll-sync","editor.source.classic","editor.split.classic","editor.preview.classic","editor.scroll-sync.classic","question","appearance","window","font","cursor","keyboard","bell","notification.card","notification.sticky","notification.off","notification.history","command-palette","chevron.left","none","user","bookmark","home","desktop","preview","hidden","logout","download","help","history","clock","lock.keyhole","link","match.case","folder.add","window.add","paste","pause","regex","remove","save","settings","split","unlock","zoom","add","more","menu","collapse","chevron.down","chevron.right","chevron.up","search","shield","lock","edit","delete","copy","reveal","refresh","upload","back","forward","folder","folder.filled","file","server","key","login","note","network","more.vertical","tunnel","terminal","play","play.filled","triangle.down","warning","info","error","plugin","check","close","sparkle","star","star.filled","match.word","laptop","target","arrow.right","import","brightness","reset","expand.all","collapse.all","run","application");
    void validateIcon(String name){if(name!=null && !BUILT_INS.contains(name) && !icons.containsKey(name))throw new IllegalArgumentException("Unknown icon: " + name);}
    void validateIcons(ViewSpec spec) {
        var available=new HashSet<>(BUILT_INS);available.addAll(icons.keySet());
        KitIconNames.validate(Objects.requireNonNull(spec), name -> {
            if (!available.contains(name)) throw new IllegalArgumentException("Unknown icon: " + name);
        });
    }
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
    public FakeKitView requireOwned(UiView view) { guard();if(!(view instanceof FakeKitView fake) || fake.owner!=this)throw new IllegalArgumentException("Foreign view");fake.guard();return fake; }
    /**
     * Closes the kit: every live view closes and the registered icons are dropped, at once on the EDT or later from
     * another thread. Afterwards {@code create} and {@code registerIcon} throw {@link IllegalStateException}; a second
     * close does nothing.
     */
    @Override public void close() { if(!closed.compareAndSet(false,true))return;Runnable cleanup=() -> {for(var view:List.copyOf(views))view.close();icons.clear();}; if(EventQueue.isDispatchThread())cleanup.run();else EventQueue.invokeLater(cleanup); }
}
