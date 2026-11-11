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
        popup.addPopupMenuListener(new PopupMenuListener(){public void popupMenuWillBecomeVisible(PopupMenuEvent e){}public void popupMenuWillBecomeInvisible(PopupMenuEvent e){dismissed(EventQueue.getCurrentEvent());}public void popupMenuCanceled(PopupMenuEvent e){dismissed(EventQueue.getCurrentEvent());}});
        lifetime=anchor.onClosed(this::close);
        anchor.target.addHierarchyBoundsListener(moved);UIManager.getDefaults().addPropertyChangeListener(fonts);
    }
    @Override public void setContent(UiView view){kit.guard();if(closed)throw new IllegalStateException("Popover closed");var next=kit.requireOwned(view);next.mount(this);if(next==content)return;var old=content;content=next;next.updated=this::refit;popup.removeAll();popup.add(next.component());if(old!=null)old.close();}
    @Override public void show(){kit.guard();if(closed||!anchor.isOpen())throw new IllegalArgumentException("Expired anchor");if(content==null)throw new IllegalStateException("Missing popup content");shown=true;
        // An area's content is the page, no picker: it gets neither picker property (F16).
        if(anchor.area==null){anchor.expanded(true);anchor.target.putClientProperty("Jasper.pickerDismiss",dismiss);}
        if(!GraphicsEnvironment.isHeadless()){refit();var p=anchor.target.getLocationOnScreen();var bounds=placement();popup.show(anchor.target,bounds.x-p.x,bounds.y-p.y);refit();}
    }
    /**
     * Fitting is host geometry only; no producer descriptor values enter attachment authority. The popover goes below
     * the control (or the area, for an area anchor), or above it where the screen has no room below; its left edge is the control's while the control's
     * window has room to the right, and otherwise its right edge is the control's, as Chrome's bookmark bubble under its
     * menu button and IntelliJ's popups sit, though never left of the window. The usable screen bounds it last.
     */
    static Rectangle fit(Rectangle anchor,Dimension preferred,Rectangle usable,Rectangle window){
        // A popover is at least as wide as the control that opened it (a picker matches its face).
        int width=Math.min(Math.max(preferred.width,anchor.width),usable.width),height=Math.min(preferred.height,usable.height);
        int below=anchor.y+anchor.height;
        int y=below+height<=usable.y+usable.height?below:anchor.y-height;
        int x=anchor.x+width<=window.x+window.width?anchor.x:Math.max(window.x,anchor.x+anchor.width-width);
        return new Rectangle(Math.max(usable.x,Math.min(x,usable.x+usable.width-width)),Math.max(usable.y,Math.min(y,usable.y+usable.height-height)),width,height);
    }
    private Rectangle screen(){var gc=anchor.target.getGraphicsConfiguration();var bounds=new Rectangle(gc.getBounds());var insets=Toolkit.getDefaultToolkit().getScreenInsets(gc);bounds.x+=insets.left;bounds.y+=insets.top;bounds.width-=insets.left+insets.right;bounds.height-=insets.top+insets.bottom;return bounds;}
    /** Where an anchor is on the screen: its control's bounds, or the area of its content for an area anchor. */
    static Rectangle onScreen(Point location,Dimension size,Rectangle area){
        return area==null?new Rectangle(location,size):new Rectangle(location.x+area.x,location.y+area.y,area.width,area.height);
    }
    private Rectangle placement(){var p=anchor.target.getLocationOnScreen();return fit(onScreen(p,anchor.target.getSize(),anchor.area),content.component().getPreferredSize(),screen(),window());}
    /** The anchor's window on the screen; the usable screen when it has none. */
    private Rectangle window(){var window=SwingUtilities.getWindowAncestor(anchor.target);return window==null||!window.isShowing()?screen():new Rectangle(window.getLocationOnScreen(),window.getSize());}
    private void refit(){
        if(closed||!shown||fitting||content==null||GraphicsEnvironment.isHeadless()||!anchor.isOpen())return;
        fitting=true;try{
            content.component().setAvailableHeight(Math.max(1,com.formdev.flatlaf.util.UIScale.unscale(screen().height)));
            var bounds=placement();popup.setPopupSize(bounds.getSize());if(popup.isVisible())popup.setLocation(bounds.x,bounds.y);
        }finally{fitting=false;}
    }
    /**
     * The popup went away while {@code current} was being dispatched, or none was. Swing closes a popup on the press that
     * lands outside it, and that press would then reach the control under it: when it lands on the popover's own anchor it
     * is consumed, so the anchor's action does not run and the click that closed the popover does not open it again (as
     * Swing's PopupMenu.consumeEventOnClose, but for the anchor only). A press elsewhere, and Escape, are left alone. An
     * area anchor's content is the page, so only a press inside the area is consumed there (F16): the rest of the page
     * takes its press, as the next field's button does.
     */
    void dismissed(AWTEvent current){
        if(!closed&&current instanceof MouseEvent mouse&&mouse.getID()==MouseEvent.MOUSE_PRESSED&&mouse.getSource() instanceof Component source
            &&SwingUtilities.isDescendingFrom(source,anchor.target)
            &&(anchor.area==null||anchor.area.contains(SwingUtilities.convertPoint(source,mouse.getPoint(),anchor.target))))mouse.consume();
        close();
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
