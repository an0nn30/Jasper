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
    final Containment containment;
    final Appearance appearance;
    private final BooleanSupplier open;
    private final BooleanSupplier animations;
    private final Supplier<ThemePair> themes;
    private final Set<HostedKitView> views=new LinkedHashSet<>();
    private final AtomicBoolean closed=new AtomicBoolean();
    private IconRegistry icons;
    private ThemeBinding binding;
    private Subscription updates;
    HostedKit(String owner,BooleanSupplier open,Supplier<ThemePair> themes,Appearance appearance,Containment containment){this(owner,open,themes,appearance,containment,() -> true);}
    HostedKit(String owner,BooleanSupplier open,Supplier<ThemePair> themes,Appearance appearance,Containment containment,BooleanSupplier animations){this.owner=owner;this.open=open;this.themes=themes;this.appearance=appearance;this.containment=containment;this.animations=Objects.requireNonNull(animations);}
    boolean animationsEnabled(){return animations.getAsBoolean();}
    javax.swing.Icon icon(String name){initialize();
        // Resolve on each paint so a panel stripe follows theme changes without replacing its registration.
        icons.icon(name,binding.current().chrome().semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,16);
        return new javax.swing.Icon(){public int getIconWidth(){return 16;}public int getIconHeight(){return 16;}
            public void paintIcon(java.awt.Component c,java.awt.Graphics g,int x,int y){if(live())icons.icon(name,binding.current().chrome().semantic(),dev.jasper.ui.model.Nodes.Hue.NONE,16).paintIcon(c,g,x,y);}};
    }
    boolean live(){return !closed.get() && open.getAsBoolean();}
    void guard(){if(!EventQueue.isDispatchThread())throw new IllegalStateException("Kit requires EDT");if(!live())throw new IllegalStateException("Plugin kit is closed");}
    private void initialize(){guard();if(binding!=null)return;icons=new IconRegistry();binding=new ThemeBinding(themes.get(),e -> failed());
        if(appearance!=null)updates=appearance.onChanged(v -> refresh());
    }
    void refresh(){guard();if(binding!=null)binding.publish(themes.get());}
    void failed(){KitDiagnostics.failure(containment,owner);}
    void removed(HostedKitView view){views.remove(view);}
    @Override public HostedKitView create(ViewSpec spec,Consumer<UiEvent> events){initialize();var view=new HostedKitView(this,new KitContext(binding,ThemeRoute.CHROME,DesignMetrics.DEFAULT,icons,animations),spec,events);views.add(view);return view;}
    @Override public Subscription registerIcon(IconAsset asset){initialize();Objects.requireNonNull(asset);if(!asset.name().startsWith(owner+":"))throw new IllegalArgumentException("Foreign icon namespace");var registration=icons.register(asset.name(),asset.pathData());return registration::close;}
    HostedKitView requireOwned(UiView view){guard();if(!(view instanceof HostedKitView hosted)||hosted.owner!=this)throw new IllegalArgumentException("Foreign view");hosted.guard();return hosted;}
    int viewCount(){return views.size();}
    Set<String> iconNames(){guard();return icons==null?Set.of():icons.names();}
    @Override public void close(){if(!closed.compareAndSet(false,true))return;Runnable dispose=() -> {if(updates!=null)updates.close();for(var view:List.copyOf(views))view.close();if(binding!=null)binding.close();if(icons!=null)icons.close();};if(EventQueue.isDispatchThread())dispose.run();else EventQueue.invokeLater(dispose);}
}
