package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;
import java.util.function.IntSupplier;
import javax.swing.*;
import javax.swing.border.AbstractBorder;

/** Shared receiving surface for native composition. The owner refreshes/repaints it with its retained views;
 * this panel owns no subscriptions, domain actions or child lifetime. */
public class SurfacePanel extends JPanel {
    /** Floating rounded hit shape, inset content with a square header, and rectangular-hit notification. */
    public enum Kind { POPUP, CONTENT, NOTICE }
    private final KitContext context;
    private final Kind kind;
    private final IntSupplier header;

    /** Creates a surface without a separate header allocation. */
    public SurfacePanel(KitContext context, Kind kind) { this(context, kind, () -> 0); }

    /** Creates a surface whose content header retains its owner's actual measured allocation. */
    public SurfacePanel(KitContext context, Kind kind, IntSupplier header) {
        super(new BorderLayout());
        this.context = Objects.requireNonNull(context);
        this.kind = Objects.requireNonNull(kind);
        this.header = Objects.requireNonNull(header);
        setOpaque(false);
        if (kind == Kind.POPUP) setBorder(new AbstractBorder() {
            @Override public Insets getBorderInsets(Component c, Insets insets) {
                int line = metric(DesignMetrics.Role.HAIRLINE); insets.set(line,line,line,line); return insets;
            }
            @Override public Insets getBorderInsets(Component c) { return getBorderInsets(c,new Insets(0,0,0,0)); }
            @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
                var g = (Graphics2D) graphics.create();
                try {
                    ThemePainting.antialias(g);
                    var ring = new Area(shape(0)); ring.subtract(new Area(shape(metric(DesignMetrics.Role.HAIRLINE))));
                    g.setColor(ThemePainting.color(context, SemanticColor.BORDER_MENU)); g.fill(ring);
                } finally { g.dispose(); }
            }
        });
    }
    private int metric(DesignMetrics.Role role) { return ThemePainting.metric(context,role); }
    /** Actual clamped boundary between the square header and rounded content. */
    public int surfaceTop() { return kind == Kind.CONTENT ? Math.max(0,Math.min(getHeight(),header.getAsInt())) : 0; }
    /** Arc diameters: the popup and content surfaces' named arcs; a notice is a grouped card (twice the card radius). */
    private int arc() { return kind == Kind.NOTICE ? 2 * metric(DesignMetrics.Role.CARD_RADIUS) : metric(switch(kind) {
        case POPUP -> DesignMetrics.Role.PALETTE_CORNER_ARC;
        default -> DesignMetrics.Role.WORKSPACE_SURFACE_ARC;
    }); }
    private Shape shape(int inset) {
        return new RoundRectangle2D.Double(inset,surfaceTop()+inset,Math.max(0,getWidth()-2*inset),
            Math.max(0,getHeight()-surfaceTop()-2*inset),Math.max(0,arc()-2*inset),Math.max(0,arc()-2*inset));
    }
    @Override protected boolean isPaintingOrigin() { return true; }
    @Override public boolean contains(int x,int y) {
        return kind == Kind.NOTICE || kind == Kind.CONTENT && y < surfaceTop() ? super.contains(x,y) : shape(0).contains(x,y);
    }
    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D)graphics.create();
        try {
            ThemePainting.antialias(g);
            if(kind == Kind.CONTENT) { g.setColor(getBackground());g.fillRect(0,0,getWidth(),getHeight()); }
            g.setColor(ThemePainting.color(context,SemanticColor.SURFACE_CARD));
            if(kind == Kind.NOTICE) {
                // The grouped card face: its fill, then a crisp hairline on the pixel grid (pure stroke control).
                g.fill(new RoundRectangle2D.Double(0,0,getWidth(),getHeight(),arc(),arc()));
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
                g.setColor(ThemePainting.color(context,SemanticColor.BORDER_SUBTLE));
                g.draw(new RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,arc(),arc()));
            } else g.fill(shape(0));
        } finally { g.dispose(); }
    }
    @Override protected void paintChildren(Graphics graphics) {
        if(kind == Kind.POPUP) {
            var g = (Graphics2D)graphics.create();
            try { g.clip(shape(metric(DesignMetrics.Role.HAIRLINE)));super.paintChildren(g); } finally { g.dispose(); }
        } else {
            super.paintChildren(graphics);
            if(kind == Kind.CONTENT) {
                var g = (Graphics2D)graphics.create();
                try {
                    var corners = new Area(new Rectangle(0,surfaceTop(),getWidth(),Math.max(0,getHeight()-surfaceTop())));
                    corners.subtract(new Area(shape(0)));ThemePainting.antialias(g);g.setColor(getBackground());g.fill(corners);
                } finally { g.dispose(); }
            }
        }
    }

    /** Two control-height units reserve a usable native content lane before explicit splitter movement. */
    public static int bodyLaneWidth(KitContext context) {
        return DesignMetrics.NATIVE_BODY_LANE_UNITS * ThemePainting.height(context,DesignMetrics.Role.FIELD_HEIGHT,
            dev.jasper.ui.metrics.Typography.Role.BODY);
    }

    /** Native split with measured automatic allocation until a caller or Swing drag explicitly moves the divider.
     * Layout-internal Swing adjustments are not user intent; retained user locations keep normal Swing resize weight. */
    public static class NativeSplit extends JSplitPane {
        private final IntSupplier preferredLeading, receivingMinimum;
        private final KitContext context;
        private boolean automatic=true, layingOut;
        private int automaticBaseWidth=-1;
        /** Creates a retained horizontal split; callers retain native UI, metrics and child lifetime ownership. */
        public NativeSplit(KitContext context,JComponent leading,JComponent receiving,IntSupplier preferredLeading,IntSupplier receivingMinimum) {
            super(JSplitPane.HORIZONTAL_SPLIT,leading,receiving);
            this.context=Objects.requireNonNull(context);
            this.preferredLeading=Objects.requireNonNull(preferredLeading);
            this.receivingMinimum=Objects.requireNonNull(receivingMinimum);
        }
        @Override public void setDividerLocation(int location) {
            if(preferredLeading!=null && !layingOut) automatic=false;
            super.setDividerLocation(location);
        }
        /** Receiving allocation at a proposed complete split width, including current insets and divider. */
        public int receivingWidth(int width) {
            Insets insets=getInsets();
            int available=Math.max(0,width-insets.left-insets.right-getDividerSize());
            return Math.max(0,available-(automatic?automaticLeading(available):Math.max(0,getDividerLocation()-insets.left)));
        }
        private int automaticLeading(int available) {
            int reserve=Math.max(0,receivingMinimum.getAsInt());
            int minimum=Math.max(0,getLeftComponent().getMinimumSize().width);
            int preferred=preferredLeading.getAsInt();
            int desired=preferred+(automaticBaseWidth<0?0:(int)Math.round((available-automaticBaseWidth)*getResizeWeight()));
            if(minimum+reserve<=available)return Math.max(minimum,Math.min(desired,available-reserve));
            int lane=bodyLaneWidth(context);
            if(available<2*lane)return (int)((long)available*Math.max(1,minimum)/Math.max(2,minimum+reserve));
            return available-Math.min(reserve,available-Math.min(minimum,lane));
        }
        @Override public void doLayout() {
            layingOut=true;
            try {
                if(automatic && getWidth()>0) {
                    Insets insets=getInsets();
                    int available=Math.max(0,getWidth()-insets.left-insets.right-getDividerSize());
                    if(automaticBaseWidth<0 && available>=preferredLeading.getAsInt()+receivingMinimum.getAsInt()) automaticBaseWidth=available;
                    super.setDividerLocation(insets.left+automaticLeading(available));
                }
                super.doLayout();
            } finally {layingOut=false;}
        }
    }

    /** Retains an optional sidecar at its preferred width, stacking it below the main body when both cannot fit.
     * Suppliers measure useful content at the actual allocated width and must not query this panel's preferred size. */
    public static JPanel sidecar(KitContext context,JComponent main,JComponent sidecar,
                                java.util.function.IntUnaryOperator mainHeight,java.util.function.IntUnaryOperator sideHeight) {
        var panel=new JPanel();panel.setOpaque(false);panel.add(main);panel.add(sidecar);
        panel.setLayout(new Composition.WidthLayout() {
            boolean stacked(int width) {return sidecar.isVisible() && width < sidecar.getPreferredSize().width+Math.max(bodyLaneWidth(context),main.getMinimumSize().width);}
            int sideWidth(int width) {return sidecar.isVisible()?Math.min(Math.max(0,width),sidecar.getPreferredSize().width):0;}
            @Override public int height(Container parent,int width) {
                if(!sidecar.isVisible())return mainHeight.applyAsInt(width);
                return stacked(width)?mainHeight.applyAsInt(width)+sideHeight.applyAsInt(width)
                    :Math.max(mainHeight.applyAsInt(Math.max(0,width-sideWidth(width))),sideHeight.applyAsInt(sideWidth(width)));
            }
            @Override public Dimension preferredLayoutSize(Container parent) {
                int width=main.getPreferredSize().width+(sidecar.isVisible()?sidecar.getPreferredSize().width:0);
                return new Dimension(width,height(parent,parent.getWidth()>0?parent.getWidth():width));
            }
            @Override public void layoutContainer(Container parent) {
                int width=parent.getWidth(),height=parent.getHeight();
                if(stacked(width)) {
                    int side=Math.min(height,sideHeight.applyAsInt(width));
                    main.setBounds(0,0,width,Math.max(0,height-side));sidecar.setBounds(0,Math.max(0,height-side),width,side);
                } else {
                    int side=sideWidth(width);main.setBounds(0,0,Math.max(0,width-side),height);
                    sidecar.setBounds(Math.max(0,width-side),0,side,height);
                }
            }
        });return panel;
    }

    /** Vertical overflow for a retained native composition. The supplier measures at the current viewport width,
     * including any scrollbar allocation; it must not ask this scroll pane for its preferred size. */
    public static JScrollPane scrolling(KitContext context, JComponent content,
                                        java.util.function.IntUnaryOperator minimumHeight) {
        class Body extends JPanel implements Scrollable {
            Body() { super(new BorderLayout()); setOpaque(false); add(content); }
            int width() { return getParent() instanceof JViewport viewport ? viewport.getExtentSize().width : content.getPreferredSize().width; }
            @Override public Dimension getPreferredSize() { return new Dimension(Math.max(0,width()),Math.max(0,minimumHeight.applyAsInt(Math.max(0,width())))); }
            @Override public Dimension getPreferredScrollableViewportSize() { return content.getPreferredSize(); }
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
            @Override public boolean getScrollableTracksViewportHeight() {
                return getParent() instanceof JViewport viewport && viewport.getExtentSize().height >= getPreferredSize().height;
            }
            @Override public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction) {
                return ThemePainting.height(context,DesignMetrics.Role.FIELD_HEIGHT,dev.jasper.ui.metrics.Typography.Role.BODY);
            }
            @Override public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction) {
                return Math.max(getScrollableUnitIncrement(visible,orientation,direction),visible.height-getScrollableUnitIncrement(visible,orientation,direction));
            }
        }
        var scroll = new JScrollPane(new Body(),JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("native.surface.scroll"); scroll.setOpaque(false); scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false); return scroll;
    }

    /** Native toolbar: the value keeps a usable lane; constrained action groups pack below it. */
    public static JPanel toolbar(KitContext context, JComponent leading, JComponent value, JComponent trailing) {
        var valueRow = new JPanel(new Navigation.InlineLayout(context, DesignMetrics.Role.BUTTON_GAP) {
            @Override boolean flexible(Component component) { return component == value; }
            @Override public int height(Container parent,int width) {
                return Math.max(super.height(parent,width),ThemePainting.height(context,DesignMetrics.Role.FIELD_HEIGHT,dev.jasper.ui.metrics.Typography.Role.BODY));
            }
            @Override public void layoutContainer(Container parent) {
                boolean narrow = wraps(parent,parent.getWidth());
                super.layoutContainer(parent);
                if (!narrow) value.setBounds(value.getX(),0,value.getWidth(),parent.getHeight());
            }
            @Override int receiverMinimum(Component component) {
                return ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, dev.jasper.ui.metrics.Typography.Role.BODY);
            }
        });
        valueRow.setOpaque(false); valueRow.add(leading); valueRow.add(value);
        var packed = new JPanel(new Navigation.InlineLayout(context, DesignMetrics.Role.BUTTON_GAP) {
            @Override boolean flexible(Component component) { return component == valueRow; }
            @Override int receiverMinimum(Component component) {
                return leading.getPreferredSize().width + gap()
                    + ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, dev.jasper.ui.metrics.Typography.Role.BODY);
            }
            @Override public void layoutContainer(Container parent) {
                boolean narrow = wraps(parent,parent.getWidth());
                super.layoutContainer(parent);
                if (!narrow) {
                    valueRow.setSize(Math.max(0,parent.getWidth()-trailing.getWidth()-gap()),valueRow.getHeight());
                    trailing.setLocation(parent.getWidth()-trailing.getWidth(),trailing.getY());
                }
            }
        });
        packed.setOpaque(false); packed.add(valueRow); packed.add(trailing);
        var result = row(context, DesignMetrics.Role.FIELD_HEIGHT, false);
        result.add(packed); return result;
    }

    /** Two retained native action groups keep their wide alignment and pack without overlap when constrained. */
    public static JPanel actionGroups(KitContext context, java.util.List<? extends JComponent> leading,
                                     java.util.List<? extends JComponent> trailing, DesignMetrics.Role gapRole) {
        var left = actionGroup(context, leading, gapRole);
        var right = actionGroup(context, trailing, gapRole);
        var packed = new JPanel(new Navigation.InlineLayout(context, gapRole) {
            @Override boolean flexible(Component component) { return false; }
            @Override public void layoutContainer(Container parent) {
                boolean narrow = wraps(parent, parent.getWidth());
                super.layoutContainer(parent);
                if (!narrow) right.setLocation(parent.getWidth()-right.getWidth(), right.getY());
            }
        });
        packed.setOpaque(false); packed.add(left); packed.add(right);
        var result = row(context, DesignMetrics.Role.HAIRLINE, false);
        result.add(packed); return result;
    }

    private static JPanel actionGroup(KitContext context, java.util.List<? extends JComponent> children, DesignMetrics.Role gapRole) {
        var result = new JPanel(new Stacks.StackLayout(context, dev.jasper.ui.model.Nodes.LayoutRole.ACTIONS) {
            @Override int gap() { return ThemePainting.metric(context, gapRole); }
        });
        result.setOpaque(false);
        // The group's own outer gap remains distinct from its owner's surrounding content padding.
        result.setBorder(new AbstractBorder() {
            @Override public Insets getBorderInsets(Component component) { return getBorderInsets(component, new Insets(0,0,0,0)); }
            @Override public Insets getBorderInsets(Component component, Insets insets) {
                int gap = ThemePainting.metric(context, gapRole); insets.set(0,gap,0,gap); return insets;
            }
        });
        children.forEach(result::add); return result;
    }

    /** Native label/control composition with source metrics and a full-width control at constrained widths.
     * Child lifetime belongs to the caller, as for the other native surface rows. */
    public static JPanel labelledRow(KitContext context, JComponent label, JComponent field) {
        wrapLabels(label);
        var panel = new JPanel() {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); }
        }; panel.setOpaque(false);
        panel.add(label); panel.add(field);
        panel.setLayout(new Composition.WidthLayout() {
            int side() { return ThemePainting.metric(context,DesignMetrics.Role.PALETTE_STEP_SIDE_PADDING); }
            int vertical() { return ThemePainting.metric(context,DesignMetrics.Role.PALETTE_STEP_ROW_PADDING); }
            int column() { return ThemePainting.metric(context,DesignMetrics.Role.PALETTE_STEP_LABEL_WIDTH); }
            int gap() { return ThemePainting.metric(context,DesignMetrics.Role.PALETTE_STEP_LABEL_GAP); }
            boolean stacked(int width) { return width < 2*side()+column()+gap()+field.getPreferredSize().width; }
            @Override public int height(Container parent,int width) {
                int available=Math.max(0,width-2*side());
                int content=stacked(width) ? measuredHeight(label,available)+gap()+measuredHeight(field,available)
                    : Math.max(measuredHeight(label,column()),measuredHeight(field,Math.max(0,available-column()-gap())));
                return Math.max(ThemePainting.metric(context,DesignMetrics.Role.PALETTE_STEP_ROW_HEIGHT),content+2*vertical());
            }
            @Override public Dimension preferredLayoutSize(Container parent) {
                int width=2*side()+column()+gap()+field.getPreferredSize().width;
                return new Dimension(width,height(parent,parent.getWidth()>0?parent.getWidth():width));
            }
            @Override public void layoutContainer(Container parent) {
                int available=Math.max(0,parent.getWidth()-2*side());
                if(stacked(parent.getWidth())) {
                    int h=measuredHeight(label,available);label.setBounds(side(),vertical(),available,h);
                    field.setBounds(side(),vertical()+h+gap(),available,measuredHeight(field,available));
                } else {
                    int h=Math.max(0,parent.getHeight()-2*vertical());
                    label.setBounds(side(),vertical(),column(),h);
                    field.setBounds(side()+column()+gap(),vertical(),Math.max(0,available-column()-gap()),h);
                }
            }
        });
        return panel;
    }
    private static void wrapLabels(Component component) {
        if(component instanceof Labels.LiteralLabel label)label.wrap=true;
        if(component instanceof Container container)for(var child:container.getComponents())wrapLabels(child);
    }
    /** Measures retained native rows at the offered width without assigning bounds or invoking layout. */
    public static int measuredHeight(Component component,int width) {
        if(component instanceof NativeRow row)return row.heightAt(width);
        if(component instanceof JPanel panel && panel.getLayout() instanceof BorderLayout layout) {
            var insets=panel.getInsets();int available=Math.max(0,width-insets.left-insets.right),height=0,middle=0;
            for(var child:panel.getComponents())if(child.isVisible()) {
                Object constraint=layout.getConstraints(child);
                int measured=measuredHeight(child,available);
                if(BorderLayout.NORTH.equals(constraint)||BorderLayout.SOUTH.equals(constraint))height+=measured+layout.getVgap();
                else middle=Math.max(middle,measured);
            }
            return height+middle+insets.top+insets.bottom;
        }
        if(component instanceof KitView view && view.getComponentCount()==1)return measuredHeight(view.getComponent(0),width);
        return Composition.height(component,width);
    }

    /**
     * A native window's footer band, as a WINDOW form paints its own: the sidebar surface below one full-width hairline,
     * holding {@code content} (status on the leading side, actions on the trailing side) inside the window footer's
     * vertical and side padding. Child lifetime belongs to the caller.
     */
    public static JPanel footerBand(KitContext context, JComponent content) {
        var band = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(ThemePainting.color(context, SemanticColor.SURFACE_SIDEBAR));
                graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE));
                graphics.fillRect(0, 0, getWidth(), ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE));
            }
            @Override public Insets getInsets() {
                int vertical = ThemePainting.metric(context, DesignMetrics.Role.WINDOW_FOOTER_VERTICAL), side = ThemePainting.metric(context, DesignMetrics.Role.WINDOW_FOOTER_SIDE);
                return new Insets(vertical, side, vertical, side);
            }
        };
        band.setOpaque(true);
        band.add(content, BorderLayout.CENTER);
        return band;
    }

    /** Content-measured row retaining a semantic minimum; optional top rule uses the shared subtle border. */
    public static JPanel row(KitContext context, DesignMetrics.Role minimum, boolean separator) {
        return new NativeRow(context,minimum,separator);
    }
    private static final class NativeRow extends JPanel {
        private final KitContext context;
        private final DesignMetrics.Role minimum;
        private final boolean separator;
        NativeRow(KitContext context,DesignMetrics.Role minimum,boolean separator) {
            super(new BorderLayout());this.context=context;this.minimum=minimum;this.separator=separator;setOpaque(false);
        }
        int heightAt(int width) {
            var insets=getInsets();int measured=insets.top+insets.bottom;
            for(var child:getComponents())if(child.isVisible())measured=Math.max(measured,
                insets.top+insets.bottom+measuredHeight(child,Math.max(0,width-insets.left-insets.right)));
            return Math.max(ThemePainting.metric(context,minimum),measured);
        }
        @Override public Dimension getMinimumSize() { return new Dimension(0,getPreferredSize().height); }
        @Override public Dimension getPreferredSize() {
            var natural=super.getPreferredSize();return new Dimension(natural.width,heightAt(getWidth()>0?getWidth():natural.width));
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); }
        @Override protected void paintComponent(Graphics graphics) {
            if(separator) {
                graphics.setColor(ThemePainting.color(context,SemanticColor.BORDER_SUBTLE));
                graphics.fillRect(0,0,getWidth(),ThemePainting.metric(context,DesignMetrics.Role.HAIRLINE));
            }
        }
    }
}
