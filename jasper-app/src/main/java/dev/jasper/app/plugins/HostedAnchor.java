package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.app.windows.AuxiliarySurface;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** A capability is authenticated by its exact host and captures both descriptor and native ancestry. */
final class HostedAnchor implements UiAnchor {
    final HostedKitView source;
    final Object host, mount;
    final JComponent target;
    private final String id;
    private final List<Component> ancestry;
    private final List<Runnable> listeners=new ArrayList<>();
    private final HierarchyListener hierarchy=e -> { if ((e.getChangeFlags() & (HierarchyEvent.PARENT_CHANGED | HierarchyEvent.SHOWING_CHANGED)) != 0) isOpen(); };
    private boolean closed;
    HostedAnchor(HostedKitView source,String id) {
        this.source=source;this.id=id;host=source.owner.anchorHost;mount=source.mount;
        target=source.component().anchorTarget(id);ancestry=ancestry();
        if(!valid())throw new IllegalArgumentException("Anchor requires a live mounted control");
        target.addHierarchyListener(hierarchy);
    }
    static HostedAnchor require(UiAnchor value,Object host) {
        if(!(value instanceof HostedAnchor anchor)||anchor.host!=host||!anchor.isOpen())throw new IllegalArgumentException("Foreign or expired anchor");
        return anchor;
    }
    private List<Component> ancestry(){var out=new ArrayList<Component>();for(Component c=target;c!=null;c=c.getParent())out.add(c);return out;}
    private boolean valid() {
        if(!source.owner.live() || source.mount!=mount || !ancestry.equals(ancestry()))return false;
        if(mount instanceof AuxiliarySurface surface && !surface.shown())return false;
        if(!GraphicsEnvironment.isHeadless() && !target.isShowing())return false;
        try{return source.component().anchorTarget(id)==target;}catch(IllegalArgumentException|IllegalStateException stale){return false;}
    }
    @Override public boolean isOpen(){if(!closed&&!valid())close();return !closed;}
    @Override public Subscription onClosed(Runnable handler){java.util.Objects.requireNonNull(handler);if(!isOpen()){handler.run();return () -> {}; }listeners.add(handler);return () -> listeners.remove(handler);}
    void expanded(boolean value){target.putClientProperty("Jasper.pickerExpanded",value);target.repaint();}
    @Override public void close(){if(closed)return;closed=true;target.removeHierarchyListener(hierarchy);expanded(false);for(var listener:List.copyOf(listeners))try{listener.run();}catch(Throwable failure){source.owner.failed();}listeners.clear();}
}
