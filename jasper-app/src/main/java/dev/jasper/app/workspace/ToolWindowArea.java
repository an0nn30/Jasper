package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatEmptyBorder;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The terminal column with the tool windows docked around it, laid out by {@link DockGeometry} from the window's
 * {@link DockState}: a full-width bottom area under the left side, the terminal column and the right side. Each
 * showing tool window sits in a rounded frame under its {@link ToolWindowHeader}; the gaps between frames are this
 * area's dividers. The terminal column has a rounded frame and exterior gutter only while the stripes are visible;
 * otherwise it fills its area edge to edge. The center frame is painted in the terminal palette's background, never
 * a theme's tool window colour. The area changes the state only when a divider drag ends; whoever changes the state
 * calls {@link #sync} afterwards. EDT only.
 */
final class ToolWindowArea extends JPanel {
    private static final int GUTTER = 6, ARC = 16;

    /** A shared frame around plugin-owned content; gutters belong to the workspace chrome. */
    private static final class Host extends JPanel {
        private final int[] gutters = {-1, -1, -1, -1};
        private boolean rounded = true;
        /** The opacity in effect for the surface, 1 being opaque; only the centre host follows the window's glass. */
        private float opacity = 1f;
        private JComponent content;

        Host(boolean toolWindow) {
            super(new BorderLayout());
            if (toolWindow) putClientProperty("Jasper.zone", "chrome");
            gutters(GUTTER, GUTTER, GUTTER, GUTTER);
        }

        void gutters(int top, int left, int bottom, int right) {
            if (gutters[0] == top && gutters[1] == left && gutters[2] == bottom && gutters[3] == right) return;
            gutters[0] = top; gutters[1] = left; gutters[2] = bottom; gutters[3] = right;
            // An app-owned border keeps the gutter through look-and-feel switches.
            setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(top, left, bottom, right), null));
        }

        private java.awt.Shape surface() {
            var insets = getInsets();
            int width = Math.max(0, getWidth() - insets.left - insets.right);
            int height = Math.max(0, getHeight() - insets.top - insets.bottom), arc = rounded ? UIScale.scale(ARC) : 0;
            return new RoundRectangle2D.Double(insets.left, insets.top, width, height, arc, arc);
        }

        @Override protected boolean isPaintingOrigin() { return true; }
        @Override public boolean contains(int x, int y) { return surface().contains(x, y); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                Color chrome = UIManager.getColor("Jasper.titleBackground");
                g.setColor(chrome);
                g.fillRect(0, 0, getWidth(), getHeight());
                if (opacity < 1f) {
                    // A translucent window: the surface is glass of the terminal background, replacing what lies
                    // beneath. Panes replace it again with the same fill, so every pixel gets exactly one alpha
                    // fill, and a strip no pane covers (the lower arc) is glass rather than a hole. Java2D's Metal
                    // pipeline renders a translucent Src fill in software, reading the GPU back, unless it is an
                    // unantialiased rectangle (200 ms instead of 1 ms per frame): fill the bounds as a plain
                    // rectangle, then round the corners with opaque, antialiased chrome over it.
                    Color background = getBackground();
                    Rectangle bounds = surface().getBounds();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    g.setComposite(AlphaComposite.Src);
                    g.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(),
                        Math.round(255 * opacity)));
                    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                    if (rounded && chrome != null) {
                        var corners = new Area(bounds);
                        corners.subtract(new Area(surface()));
                        g.setComposite(AlphaComposite.SrcOver);
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(chrome);
                        g.fill(corners);
                    }
                } else {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // The terminal column (tab strip included) is one surface in the terminal palette, so every
                    // theme follows the same frame, including its optional rounded corners.
                    g.setColor(getBackground()); g.fill(surface());
                }
            } finally { g.dispose(); }
        }

        @Override protected void paintChildren(Graphics graphics) {
            var clipped = (Graphics2D) graphics.create();
            try { clipped.clip(surface()); super.paintChildren(clipped); }
            finally { clipped.dispose(); }
        }
    }

    /** A gap between two frames that resizes what it divides. It paints nothing: the area's chrome shows through. */
    private final class DividerHandle extends JComponent {
        private final DockGeometry.Handle handle;
        /** Where the last layout put it, or null while it is not part of the layout. */
        private DockGeometry.Divider placed;
        private DockGeometry.Divider dragging;
        /** Whether a drag event arrived since the press; a plain click must not rewrite a squeezed size. */
        private boolean moved;
        private int grab;

        DividerHandle(DockGeometry.Handle handle) {
            this.handle = handle;
            setCursor(Cursor.getPredefinedCursor(handle.vertical() ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
            var mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || placed == null) return;
                    dragging = placed;
                    moved = false;
                    grab = handle.vertical() ? event.getX() : event.getY();
                }

                @Override public void mouseDragged(MouseEvent event) {
                    if (dragging == null) return;
                    moved = true;
                    // The drag lays out a scratch copy; the real state changes once, on release.
                    preview = state.copy();
                    DockGeometry.drag(preview, dragging, position(event), metrics());
                    ToolWindowArea.this.revalidate(); ToolWindowArea.this.repaint();
                }

                @Override public void mouseReleased(MouseEvent event) {
                    if (dragging == null) return;
                    var divider = dragging;
                    boolean dragged = moved;
                    dragging = null;
                    preview = null;
                    moved = false;
                    if (dragged) DockGeometry.drag(state, divider, position(event), metrics());
                    ToolWindowArea.this.revalidate(); ToolWindowArea.this.repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        /** Where the dragged leading edge goes, in the area's coordinates. */
        private int position(MouseEvent event) {
            Point point = SwingUtilities.convertPoint(this, event.getPoint(), ToolWindowArea.this);
            return (handle.vertical() ? point.x : point.y) - grab;
        }
    }

    /** What a tool window shows while it is active: its title and its content. */
    private record Content(String title, JComponent component) { }

    /** Lays the frames and dividers out from the geometry; the minimum keeps showing frames at their minimum. */
    private final class DockLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return size(center.getPreferredSize(), true); }
        @Override public Dimension minimumLayoutSize(Container parent) { return size(center.getMinimumSize(), false); }

        @Override public void layoutContainer(Container parent) {
            var layout = DockGeometry.compute(new Rectangle(0, 0, getWidth(), getHeight()), layoutState(), metrics());
            center.setBounds(layout.terminal());
            hosts.forEach((slot, host) -> host.setBounds(layout.slot(slot).orElseGet(Rectangle::new)));
            dividers.values().forEach(divider -> {
                divider.placed = layout.divider(divider.handle).orElse(null);
                divider.setBounds(divider.placed == null ? new Rectangle() : divider.placed.bounds());
            });
        }
    }

    private final DockState state;
    private final DockState emptyLayout = new DockState();
    private boolean toolsSuppressed;
    private final Host center = new Host(false);
    private final JPanel lowerInset = new JPanel() {
        @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(ARC / 2)); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    };
    private final EnumMap<Slot, Host> hosts = new EnumMap<>(Slot.class);
    private final Map<String, Content> contents = new HashMap<>();
    private final EnumMap<DockGeometry.Handle, DividerHandle> dividers = new EnumMap<>(DockGeometry.Handle.class);
    /** A divider drag in progress lays out this copy of the state. */
    private DockState preview;
    private boolean stripesVisible, statusVisible, headerVisible;
    /** Runs after every sync, which may have changed which windows show and so the area's minimum size. */
    Runnable onMinimumSizeChanged = () -> { };
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    Consumer<String> onHide = id -> { };
    /** Hears of each header a sync creates (and its title label), so that a drag can start from it. */
    BiConsumer<JComponent, String> dragSource = (component, id) -> { };

    ToolWindowArea(JComponent terminalColumn, DockState state) {
        super(null);
        this.state = Objects.requireNonNull(state, "state");
        setLayout(new DockLayout());
        center.add(terminalColumn, BorderLayout.CENTER);
        lowerInset.setOpaque(false);
        center.add(lowerInset, BorderLayout.SOUTH);
        add(center);
        for (DockGeometry.Handle handle : DockGeometry.Handle.values()) {
            var divider = new DividerHandle(handle);
            dividers.put(handle, divider);
            add(divider);
        }
        refreshTheme();
        sync();
    }

    @Override public void updateUI() {
        super.updateUI();
        // The gaps between frames show the workspace chrome.
        setBackground(UIManager.getColor("Jasper.titleBackground"));
    }

    DockState state() { return state; }

    /** What the window with this id shows when it is active: its header title and its content. */
    void setContent(String id, String title, JComponent component) {
        contents.put(Objects.requireNonNull(id), new Content(Objects.requireNonNull(title), Objects.requireNonNull(component)));
    }

    void removeContent(String id) { contents.remove(id); }

    /** The content showing in the slot, or null. */
    JComponent content(Slot slot) {
        Host host = hosts.get(slot);
        return host == null ? null : host.content;
    }

    /** The header of the slot's frame, or null when the slot shows nothing. */
    ToolWindowHeader headerOf(Slot slot) {
        Host host = hosts.get(slot);
        return host == null ? null : (ToolWindowHeader) ((BorderLayout) host.getLayout()).getLayoutComponent(BorderLayout.NORTH);
    }

    /** The divider for a handle; it has empty bounds while the layout has no such divider. */
    JComponent divider(DockGeometry.Handle handle) { return dividers.get(handle); }

    /**
     * Brings the frames in line with the state: a frame for each slot that shows a window with content, none for the
     * others. Content leaves the component tree while its window is hidden.
     */
    void sync() {
        for (Slot slot : Slot.values()) {
            String id = toolsSuppressed ? null : state.active(slot).orElse(null);
            Content wanted = id == null ? null : contents.get(id);
            Host host = hosts.get(slot);
            if (host != null && (wanted == null || host.content != wanted.component())) {
                remove(host);
                host.removeAll();
                hosts.remove(slot);
                host = null;
            }
            if (wanted != null && host == null) {
                host = new Host(true);
                host.content = wanted.component();
                ToolWindowHeader header = header(id, wanted.title());
                host.add(header, BorderLayout.NORTH);
                dragSource.accept(header, id);
                dragSource.accept(header.titleLabel(), id);
                host.add(wanted.component(), BorderLayout.CENTER);
                ToolWindowSurface.apply(host);
                hosts.put(slot, host);
                add(host);
            }
        }
        refreshGutters();
        revalidate(); repaint();
        onMinimumSizeChanged.run();
    }

    /** Zen changes the presentation only: no dock mutation, persistence or disposal of plugin content. */
    void setToolsSuppressed(boolean suppressed) {
        if (toolsSuppressed == suppressed) return;
        toolsSuppressed = suppressed;
        preview = null;
        dividers.values().forEach(divider -> { divider.dragging = null; divider.moved = false; });
        sync();
    }

    private DockState layoutState() { return toolsSuppressed ? emptyLayout : preview != null ? preview : state; }

    private ToolWindowHeader header(String id, String title) {
        return new ToolWindowHeader(title,
            () -> ToolWindowMenu.create(state.slot(id).orElse(Slot.LEFT_TOP), state.visible(id),
                slot -> onMove.accept(id, slot), () -> onHide.accept(id)),
            () -> onHide.accept(id));
    }

    /** The terminal palette background of the whole center, including the tab strip and any lower arc. */
    void setTerminalBackground(Color background) { center.setBackground(background); }

    /**
     * The opacity in effect for the center surface, 1 being opaque. Below 1 the surface is filled with the terminal
     * background at that opacity, the same fill the panes inside paint over it.
     */
    void setGlass(float opacity) {
        if (center.opacity == opacity) return;
        center.opacity = opacity;
        center.repaint();
    }

    /** Reapplies the tool window colour after a theme change reset the frame backgrounds. */
    void refreshTheme() {
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        hosts.values().forEach(ToolWindowSurface::apply);
    }

    /** Visible stripes frame the terminal; hiding them lets the terminal fill its available area. */
    void setStripesVisible(boolean visible) {
        if (stripesVisible == visible) return;
        stripesVisible = visible;
        refreshGutters();
        revalidate(); repaint();
    }

    /** The visible status bar supplies the spacing below the workspace surfaces. */
    void setStatusVisible(boolean visible) {
        if (statusVisible == visible) return;
        statusVisible = visible;
        refreshGutters();
        revalidate(); repaint();
    }

    /** The visible title row or toolbar already supplies the spacing above the workspace. */
    void setHeaderVisible(boolean visible) {
        if (headerVisible == visible) return;
        headerVisible = visible;
        refreshGutters();
        revalidate(); repaint();
    }

    /**
     * Exterior gutters are 6px on each edge of the area a frame touches, except where the header, a stripe or the
     * status bar already supplies the spacing; facing edges have none, because the 6px divider is the gap.
     */
    private void refreshGutters() {
        int top = headerVisible ? 0 : GUTTER, edge = stripesVisible ? 0 : GUTTER, bottom = statusVisible ? 0 : GUTTER;
        boolean bottomShown = layoutState().showing(Side.BOTTOM);
        center.rounded = stripesVisible;
        lowerInset.setVisible(stripesVisible);
        if (stripesVisible) center.gutters(top, 0, bottomShown ? 0 : bottom, 0);
        else center.gutters(0, 0, 0, 0);
        hosts.forEach((slot, host) -> {
            boolean alone = state.active(slot.partner()).isEmpty(), first = !slot.split();
            if (slot.side() == Side.BOTTOM)
                host.gutters(0, first || alone ? edge : 0, bottom, !first || alone ? edge : 0);
            else
                host.gutters(first || alone ? top : 0, slot.side() == Side.LEFT ? edge : 0,
                    (!first || alone) && !bottomShown ? bottom : 0, slot.side() == Side.RIGHT ? edge : 0);
        });
    }

    private Dimension size(Dimension terminal, boolean preferred) { return size(terminal, toolsSuppressed ? emptyLayout : state, metrics(), preferred); }

    /**
     * The area's preferred or minimum size around a terminal column of the given size. Only a split side raises the
     * height above the terminal's: two tool window minimums and the divider between them, when a side shows both halves.
     * The bottom row adds its divider and its minimum, or its saved size when preferred.
     */
    static Dimension size(Dimension terminal, DockState state, DockGeometry.Metrics metrics, boolean preferred) {
        int gap = metrics.scaled(metrics.divider()), floor = metrics.scaled(metrics.toolWindowMinimum());
        int width = terminal.width, height = Math.max(terminal.height, DockGeometry.splitMinimum(state, metrics));
        for (Side side : List.of(Side.LEFT, Side.RIGHT))
            if (state.showing(side)) width += gap + (preferred ? Math.max(floor, metrics.scaled(state.size(side))) : floor);
        if (state.showing(Side.BOTTOM))
            height += gap + (preferred ? Math.max(floor, metrics.scaled(state.size(Side.BOTTOM))) : floor);
        return new Dimension(width, height);
    }

    /** The dock metrics at the current UI scale; the drag's preview uses the same. */
    static DockGeometry.Metrics metrics() { return DockGeometry.Metrics.at(UIScale.getUserScaleFactor()); }
}
