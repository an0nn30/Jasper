package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.app.windows.AuxiliarySurface;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * A capability is authenticated by its exact host and captures both descriptor and native ancestry. An area anchor
 * ({@code UiView.anchorAt}) targets a Custom node's registered content and holds only while the content keeps the size it
 * had when the anchor was minted: a page laid out again has moved what was in the area.
 */
final class HostedAnchor implements UiAnchor {
    final HostedKitView source;
    final Object host, mount;
    final JComponent target;
    /** The area of the target this anchor is on, in the target's coordinates; null for a control's anchor. */
    final Rectangle area;
    /** An area anchor's content size when it was minted; null for a control's anchor. */
    private final Dimension size;
    private final String id;
    private final List<Component> ancestry;
    private final List<Runnable> listeners=new ArrayList<>();
    private final HierarchyListener hierarchy=e -> { if ((e.getChangeFlags() & (HierarchyEvent.PARENT_CHANGED | HierarchyEvent.SHOWING_CHANGED)) != 0) isOpen(); };
    /** Closes an area anchor at its content's first resize, without waiting to be asked; a resize event at the same size changes nothing. */
    private final ComponentListener resized=new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){isOpen();}};
    private boolean closed;
    HostedAnchor(HostedKitView source,String id) { this(source,id,null); }
    HostedAnchor(HostedKitView source,String id,Rectangle area) {
        this.source=source;this.id=id;this.area=area==null?null:new Rectangle(area);host=source.owner.anchorHost;mount=source.mount;
        target=area==null?source.component().anchorTarget(id):source.component().customTarget(id);
        size=area==null?null:target.getSize();ancestry=ancestry();
        if(!valid())throw new IllegalArgumentException(area==null?"Anchor requires a live mounted control":"Anchor requires live mounted Custom content");
        target.addHierarchyListener(hierarchy);
        if(area!=null)target.addComponentListener(resized);
    }
    static HostedAnchor require(UiAnchor value,Object host) {
        if(!(value instanceof HostedAnchor anchor)||anchor.host!=host||!anchor.isOpen())throw new IllegalArgumentException("Foreign or expired anchor");
        return anchor;
    }
    private List<Component> ancestry(){var out=new ArrayList<Component>();for(Component c=target;c!=null;c=c.getParent())out.add(c);return out;}
    AuxiliarySurface ownerSurface(){return mount instanceof SurfaceMount slot ? slot.surface() : mount instanceof AuxiliarySurface surface ? surface : null;}
    private boolean valid() {
        if(!source.owner.live() || source.mount!=mount || !ancestry.equals(ancestry()))return false;
        if(ownerSurface()!=null && !ownerSurface().shown())return false;
        if(!GraphicsEnvironment.isHeadless() && !target.isShowing())return false;
        try{
            if(area==null)return source.component().anchorTarget(id)==target;
            return source.component().customTarget(id)==target&&target.getSize().equals(size);
        }catch(IllegalArgumentException|IllegalStateException stale){return false;}
    }
    @Override public boolean isOpen(){if(!closed&&!valid())close();return !closed;}
    @Override public Subscription onClosed(Runnable handler){java.util.Objects.requireNonNull(handler);if(!isOpen()){handler.run();return () -> {}; }listeners.add(handler);return () -> listeners.remove(handler);}
    /** Marks a picker control expanded; an area's content is no picker and is left alone. */
    void expanded(boolean value){if(area!=null)return;target.putClientProperty("Jasper.pickerExpanded",value);target.repaint();}
    @Override public void close(){if(closed)return;closed=true;target.removeHierarchyListener(hierarchy);target.removeComponentListener(resized);expanded(false);for(var listener:List.copyOf(listeners))try{listener.run();}catch(Throwable failure){source.owner.failed();}listeners.clear();}
}
