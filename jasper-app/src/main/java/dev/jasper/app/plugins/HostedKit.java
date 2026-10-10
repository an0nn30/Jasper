package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.*;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.theme.*;
import java.awt.EventQueue;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Lazy plugin lifetime: legacy contexts may be constructed off EDT; actual kit operations may not. */
final class HostedKit implements UiKit,AutoCloseable {
    final String owner;
    Object anchorHost;
    final Containment containment;
    final Appearance appearance;
    private final BooleanSupplier open;
    private final BooleanSupplier animations;
    private final Supplier<ThemePair> themes;
    private final Set<HostedKitView> views=new LinkedHashSet<>();
    private final AtomicBoolean closed=new AtomicBoolean();
    private final IconRegistry icons=new IconRegistry();
    private final Map<String, IconAsset> assets=new java.util.concurrent.ConcurrentHashMap<>();
    private ThemeBinding binding;
    private Subscription updates;
    HostedKit(String owner,BooleanSupplier open,Supplier<ThemePair> themes,Appearance appearance,Containment containment){this(owner,open,themes,appearance,containment,() -> true);}
    HostedKit(String owner,BooleanSupplier open,Supplier<ThemePair> themes,Appearance appearance,Containment containment,BooleanSupplier animations){this.owner=owner;this.open=open;this.themes=themes;this.appearance=appearance;this.containment=containment;this.animations=Objects.requireNonNull(animations);}
    boolean animationsEnabled(){return animations.getAsBoolean();}
    void validateIcon(String name){if(name != null && !icons.names().contains(name))throw new IllegalArgumentException("Unknown icon: " + name);}
    void validateIcons(ViewSpec spec) {
        var available=icons.names();
        KitIconNames.validate(Objects.requireNonNull(spec), name -> {
            if (!available.contains(name)) throw new IllegalArgumentException("Unknown icon: " + name);
        });
    }
    javax.swing.Icon icon(String name){validateIcon(name);
        var asset=assets.get(name);
        return new dev.jasper.app.platform.PresentationIcon(name, asset == null ? null : asset.pathData());
    }

    /** A semantic colour of one theme slot, as the kit paints it; registered specialized content follows its view's slot. */
    java.awt.Color color(ThemeRoute route,dev.jasper.ui.theme.SemanticColor role){return route.select(themes.get()).semantic().color(role);}
    boolean live(){return !closed.get() && open.getAsBoolean();}
    void guard(){if(!EventQueue.isDispatchThread())throw new IllegalStateException("Kit requires EDT");if(!live())throw new IllegalStateException("Plugin kit is closed");}
    private void initialize(){guard();if(binding!=null)return;binding=new ThemeBinding(themes.get(),e -> failed());
        if(appearance!=null)updates=appearance.onChanged(v -> refresh());
    }
    void refresh(){guard();if(binding!=null){binding.publish(themes.get());for(var view:List.copyOf(views))view.updated.run();}}
    void failed(){KitDiagnostics.failure(containment,owner);}
    void removed(HostedKitView view){views.remove(view);}
    @Override public HostedKitView create(ViewSpec spec,Consumer<UiEvent> events){initialize();validateIcons(spec);var view=new HostedKitView(this,new KitContext(binding,ThemeRoute.CHROME,DesignMetrics.DEFAULT,icons,animations),spec,events);views.add(view);return view;}
    @Override public Subscription registerIcon(IconAsset asset) {
        initialize(); Objects.requireNonNull(asset);
        if(!asset.name().startsWith(owner+":"))throw new IllegalArgumentException("Foreign icon namespace");
        var registration=icons.register(asset.name(),asset.pathData());
        assets.put(asset.name(),asset);
        return () -> {
            registration.close(); assets.remove(asset.name(),asset);
        };
    }
    HostedKitView requireOwned(UiView view){guard();if(!(view instanceof HostedKitView hosted)||hosted.owner!=this)throw new IllegalArgumentException("Foreign view");hosted.guard();return hosted;}
    int viewCount(){return views.size();}
    Set<String> iconNames(){guard();return icons.names();}
    @Override public void close(){if(!closed.compareAndSet(false,true))return;Runnable dispose=() -> {if(updates!=null)updates.close();for(var view:List.copyOf(views))view.close();if(binding!=null)binding.close();assets.clear();icons.close();};if(EventQueue.isDispatchThread())dispose.run();else EventQueue.invokeLater(dispose);}
}
