package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;

/** Host-owned attachment/fitting and dismissal; all visible popup content is the producer's kit view. */
final class HostedPopover implements PluginPopover {
    private final HostedKit kit;
    private final HostedAnchor anchor;
    private final JPopupMenu popup=new JPopupMenu();
    private final List<Runnable> listeners=new ArrayList<>();
    private final Subscription lifetime;
    private HostedKitView content;
    private boolean closed,shown,fitting;
    private final Runnable dismiss=this::close;
    private final HierarchyBoundsListener moved=new HierarchyBoundsAdapter(){
        @Override public void ancestorMoved(HierarchyEvent e){refit();}
        @Override public void ancestorResized(HierarchyEvent e){refit();}
    };
    private final java.beans.PropertyChangeListener fonts=e->{
        if("defaultFont".equals(e.getPropertyName())||"Label.font".equals(e.getPropertyName())||"Jasper.uiFontFamilyOverride".equals(e.getPropertyName()))SwingUtilities.invokeLater(this::refit);
    };
    HostedPopover(HostedKit kit,HostedAnchor anchor){
        this.kit=kit;this.anchor=anchor;
        popup.setLayout(new BorderLayout());popup.setBorder(new javax.swing.border.EmptyBorder(0,0,0,0));popup.setOpaque(false);
        popup.addPopupMenuListener(new PopupMenuListener(){public void popupMenuWillBecomeVisible(PopupMenuEvent e){}public void popupMenuWillBecomeInvisible(PopupMenuEvent e){close();}public void popupMenuCanceled(PopupMenuEvent e){close();}});
        lifetime=anchor.onClosed(this::close);
        anchor.target.addHierarchyBoundsListener(moved);UIManager.getDefaults().addPropertyChangeListener(fonts);
    }
    @Override public void setContent(UiView view){kit.guard();if(closed)throw new IllegalStateException("Popover closed");var next=kit.requireOwned(view);next.mount(this);if(next==content)return;var old=content;content=next;next.updated=this::refit;popup.removeAll();popup.add(next.component());if(old!=null)old.close();}
    @Override public void show(){kit.guard();if(closed||!anchor.isOpen())throw new IllegalArgumentException("Expired anchor");if(content==null)throw new IllegalStateException("Missing popup content");shown=true;anchor.expanded(true);anchor.target.putClientProperty("Jasper.pickerDismiss",dismiss);
        if(!GraphicsEnvironment.isHeadless()){refit();var p=anchor.target.getLocationOnScreen();var bounds=placement();popup.show(anchor.target,bounds.x-p.x,bounds.y-p.y);refit();}
    }
    /** Fitting is host geometry only; no producer descriptor values enter attachment authority. */
    static Rectangle fit(Rectangle anchor,Dimension preferred,Rectangle usable){
        // A popover is at least as wide as the control that opened it (a picker matches its face).
        int width=Math.min(Math.max(preferred.width,anchor.width),usable.width),height=Math.min(preferred.height,usable.height);
        int below=anchor.y+anchor.height;
        int y=below+height<=usable.y+usable.height?below:anchor.y-height;
        return new Rectangle(Math.max(usable.x,Math.min(anchor.x,usable.x+usable.width-width)),Math.max(usable.y,Math.min(y,usable.y+usable.height-height)),width,height);
    }
    private Rectangle screen(){var gc=anchor.target.getGraphicsConfiguration();var bounds=new Rectangle(gc.getBounds());var insets=Toolkit.getDefaultToolkit().getScreenInsets(gc);bounds.x+=insets.left;bounds.y+=insets.top;bounds.width-=insets.left+insets.right;bounds.height-=insets.top+insets.bottom;return bounds;}
    private Rectangle placement(){var p=anchor.target.getLocationOnScreen();return fit(new Rectangle(p,anchor.target.getSize()),content.component().getPreferredSize(),screen());}
    private void refit(){
        if(closed||!shown||fitting||content==null||GraphicsEnvironment.isHeadless()||!anchor.isOpen())return;
        fitting=true;try{
            content.component().setAvailableHeight(Math.max(1,com.formdev.flatlaf.util.UIScale.unscale(screen().height)));
            var bounds=placement();popup.setPopupSize(bounds.getSize());if(popup.isVisible())popup.setLocation(bounds.x,bounds.y);
        }finally{fitting=false;}
    }
    @Override public Subscription onClosed(Runnable handler){kit.guard();java.util.Objects.requireNonNull(handler);if(closed){handler.run();return () -> {}; }listeners.add(handler);return () -> listeners.remove(handler);}
    @Override public void close(){if(closed)return;closed=true;
        Component focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean restore=shown && focus!=null && SwingUtilities.isDescendingFrom(focus,popup);
        popup.setVisible(false);anchor.target.removeHierarchyBoundsListener(moved);UIManager.getDefaults().removePropertyChangeListener(fonts);if(anchor.target.getClientProperty("Jasper.pickerDismiss")==dismiss){anchor.expanded(false);anchor.target.putClientProperty("Jasper.pickerDismiss",null);}if(lifetime!=null)lifetime.close();if(content!=null)content.close();
        for(var listener:List.copyOf(listeners))try{listener.run();}catch(Throwable failure){kit.failed();}listeners.clear();
        if(restore&&anchor.isOpen()&&anchor.target.isShowing())anchor.target.requestFocusInWindow();
    }
}
