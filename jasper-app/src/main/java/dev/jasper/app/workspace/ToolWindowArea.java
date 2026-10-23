package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.metrics.DesignMetrics;

import com.formdev.flatlaf.ui.FlatEmptyBorder;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
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
 * {@link DockState}: a full-width bottom area under the left side, the terminal column and the right side, sized and
 * framed by the window's kit. Each showing tool window sits in a rounded frame under its {@link ToolWindowHeader}; the
 * gaps between frames are this area's dividers. Under flush frames (spec 10.7) the frames are square and touch, each
 * divider is a painted 1 px rule with a wider drag area, and every header is a header strip that shows which frame holds
 * the focus. Under flush frames a frame is also activated by any press inside it or by its opening, which moves the
 * keyboard into it; a press in the terminal column hands the tint back, and a divider drag keeps it. The terminal
 * column has a rounded frame and exterior gutter while a stripe is visible, and is
 * borderless and square on the side of a hidden stripe; with neither stripe it fills its area edge to edge. The center frame is painted in the terminal palette's background, never
 * a theme's tool window colour. The area changes the state only when a divider drag ends; whoever changes the state
 * calls {@link #sync} afterwards. EDT only.
 */
final class ToolWindowArea extends JPanel {
    /** The window's kit's exterior gutter, in logical pixels. */
    private int gutter() { return kit.metrics().value(DesignMetrics.Role.WORKSPACE_GUTTER); }
    /** The window's kit's workspace frame arc, in logical pixels. */
    int surfaceArc() { return kit.metrics().value(DesignMetrics.Role.WORKSPACE_SURFACE_ARC); }
    /** How far, in logical pixels, a press must move before it becomes a dock drag: the window's kit's threshold. */
    int dragThreshold() { return kit.metrics().value(DesignMetrics.Role.DOCK_DRAG_THRESHOLD); }
    /** The window's kit. */
    dev.jasper.ui.kit.Kit kit() { return kit; }
    /** Whether frames are flush: divided by painted rules, with header strips (spec 10.7). */
    boolean flush() { return flush; }

    /** A shared frame around plugin-owned content; gutters belong to the workspace chrome. */
    private final class Host extends JPanel {
        private final int[] gutters = {-1, -1, -1, -1};
        /** Whether each side's corners are rounded; a side that meets the window edge directly is square. */
        private boolean roundLeft = true, roundRight = true;
        /** The opacity in effect for the surface, 1 being opaque; only the centre host follows the window's glass. */
        private float opacity = 1f;
        private JComponent content;
        private Runnable releaseHeader = () -> {};
        private JComponent borrowedHeader;
        /** The tool window this frame shows. */
        private String id;
        /** Shows per-window header metadata on this frame's header, native or borrowed; inert once released. */
        private Consumer<dev.jasper.app.contributions.PanelHeaderState> header = state -> { };
        /** Whether this frame holds the focus owner (flush frames only). */
        private boolean focused;
        /** Shows the focus on this frame's header strip, native or borrowed; inert once released. */
        private Consumer<Boolean> focusedHeader = value -> { };

        void focused(boolean value) {
            if (focused == value) return;
            focused = value;
            focusedHeader.accept(value);
        }

        Host(boolean toolWindow) {
            super(new BorderLayout());
            if (toolWindow) putClientProperty("Jasper.zone", "chrome");
            // A frame with no keyed control takes the keyboard itself when activated (plan 12 Rulings 18). It is
            // focusable only while it holds the keyboard, so it is never a Tab stop or an automatic focus target.
            if (toolWindow && flush) {
                setFocusable(false);
                addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override public void focusLost(java.awt.event.FocusEvent event) { if (!event.isTemporary()) setFocusable(false); }
                });
            }
            int gutter = gutter();
            gutters(gutter, gutter, gutter, gutter);
        }

        void gutters(int top, int left, int bottom, int right) {
            if (gutters[0] == top && gutters[1] == left && gutters[2] == bottom && gutters[3] == right) return;
            gutters[0] = top; gutters[1] = left; gutters[2] = bottom; gutters[3] = right;
            // An app-owned border keeps the gutter through look-and-feel switches.
            setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(top, left, bottom, right), null));
        }

        void rounded(boolean left, boolean right) {
            if (roundLeft == left && roundRight == right) return;
            roundLeft = left;
            roundRight = right;
            repaint();
        }

        private boolean rounded() { return roundLeft || roundRight; }

        private java.awt.Shape surface() {
            var insets = getInsets();
            int width = Math.max(0, getWidth() - insets.left - insets.right);
            int height = Math.max(0, getHeight() - insets.top - insets.bottom), arc = rounded() ? UIScale.scale(surfaceArc()) : 0;
            var round = new RoundRectangle2D.Double(insets.left, insets.top, width, height, arc, arc);
            if (roundLeft == roundRight) return round;
            // One side meets the window edge directly: its half is square.
            var shape = new Area(round);
            double half = width / 2.0;
            shape.add(new Area(new java.awt.geom.Rectangle2D.Double(roundLeft ? insets.left + half : insets.left, insets.top, width - half, height)));
            return shape;
        }

        @Override protected boolean isPaintingOrigin() { return true; }
        @Override public boolean contains(int x, int y) { return surface().contains(x, y); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                Color chrome = ThemeZone.color(null, SemanticColor.SURFACE_APP);
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
                    if (rounded() && chrome != null) {
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
        /** Where in the handle the press was, less how far the divider's leading edge lies into the handle. */
        private int grab;

        DividerHandle(DockGeometry.Handle handle) {
            this.handle = handle;
            setCursor(Cursor.getPredefinedCursor(handle.vertical() ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
            var mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || placed == null) return;
                    dragging = placed;
                    moved = false;
                    // A flush rule's drag area is wider than the rule: the rule's leading edge lies inside it.
                    int offset = handle.vertical() ? placed.bounds().x - getX() : placed.bounds().y - getY();
                    grab = (handle.vertical() ? event.getX() : event.getY()) - offset;
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
                divider.setBounds(divider.placed == null ? new Rectangle() : hitArea(divider.placed));
            });
        }
    }

    private final DockState state;
    /** The window's kit: the frames' arc, gutter, rule and header sizes. */
    private final dev.jasper.ui.kit.Kit kit;
    /** Whether frames are flush (the classic look's frame style). */
    private final boolean flush;
    /** The window's chrome artwork, which the headers' classic names follow (plan 15b). */
    private final dev.jasper.ui.icons.ChromeArtwork artwork;
    /** The window's kit's chrome painter: the rules under flush frames. */
    private final dev.jasper.ui.swing.ChromePainter painter;
    private final Host center;
    private final JPanel lowerInset = new JPanel() {
        @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(surfaceArc() / 2)); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    };
    private final EnumMap<Slot, Host> hosts = new EnumMap<>(Slot.class);
    private final Map<String, dev.jasper.app.contributions.PanelHeaderState> headers = new HashMap<>();
    private final Map<String, Content> contents = new HashMap<>();
    private final EnumMap<DockGeometry.Handle, DividerHandle> dividers = new EnumMap<>(DockGeometry.Handle.class);
    /** A divider drag in progress lays out this copy of the state. */
    private DockState preview;
    private boolean leftStripeVisible, rightStripeVisible, statusVisible, headerVisible;
    /** Runs after every sync, which may have changed which windows show and so the area's minimum size. */
    Runnable onMinimumSizeChanged = () -> { };
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    /** Docks a window or detaches it as a Float or a Window, from its header's Options menu. */
    BiConsumer<String, ToolWindowMode> onMode = (id, mode) -> { };
    Consumer<String> onHide = id -> { };
    /** Shows a borrowed kit header's Options menu under its button; a header that is not showing opens nothing. */
    BiConsumer<JComponent, javax.swing.JPopupMenu> showOptions = (anchor, menu) -> {
        if (anchor.isShowing()) menu.show(anchor, 0, anchor.getHeight());
    };
    /** Hears of each header a sync creates (and its title label), so that a drag can start from it. */
    BiConsumer<JComponent, String> dragSource = (component, id) -> { };
    Consumer<JComponent> releaseDragSource = component -> { };

    /**
     * An area of standard frames over the installed kit, as the default look's tests build it.
     *
     * @param terminalColumn the terminal column the frames dock around
     * @param state the window's dock state
     */
    ToolWindowArea(JComponent terminalColumn, DockState state) {
        this(terminalColumn, state, dev.jasper.ui.swing.FlatLafInstaller.installedKit(), false);
    }

    /**
     * @param terminalColumn the terminal column the frames dock around
     * @param state the window's dock state
     * @param kit the window's kit
     * @param flush whether frames are flush: divided by painted rules, with header strips (spec 10.7)
     */
    ToolWindowArea(JComponent terminalColumn, DockState state, dev.jasper.ui.kit.Kit kit, boolean flush) {
        this(terminalColumn, state, kit, flush, dev.jasper.ui.icons.ChromeArtwork.NEW_UI);
    }

    /**
     * @param terminalColumn the terminal column the frames dock around
     * @param state the window's dock state
     * @param kit the window's kit
     * @param flush whether frames are flush: divided by painted rules, with header strips (spec 10.7)
     * @param artwork the window's chrome artwork: under {@code CLASSIC} the headers' classic names draw IntelliJ's
     *     classic art (plan 15b)
     */
    ToolWindowArea(JComponent terminalColumn, DockState state, dev.jasper.ui.kit.Kit kit, boolean flush,
                   dev.jasper.ui.icons.ChromeArtwork artwork) {
        super(null);
        this.artwork = Objects.requireNonNull(artwork, "artwork");
        this.state = Objects.requireNonNull(state, "state");
        this.kit = Objects.requireNonNull(kit, "kit");
        this.flush = flush;
        this.painter = new dev.jasper.ui.swing.ChromePainter(kit);
        this.center = new Host(false);
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
        // A flush rule's drag area overlaps the frames beside it: the handles stay above them.
        if (flush) for (DividerHandle divider : dividers.values()) setComponentZOrder(divider, 0);
        refreshTheme();
        sync();
    }

    @Override public void updateUI() {
        super.updateUI();
        // The gaps between frames show the workspace chrome.
        setBackground(ThemeZone.color(null, SemanticColor.SURFACE_APP));
    }

    DockState state() { return state; }

    /** What the window with this id shows when it is active: its header title and its content. */
    void setContent(String id, String title, JComponent component) {
        contents.put(Objects.requireNonNull(id), new Content(Objects.requireNonNull(title), Objects.requireNonNull(component)));
    }

    /**
     * Per-window header metadata (SDK {@code PanelHeader}, translated at the plugin boundary). It is kept while the window
     * is hidden and shown on whichever header the frame has: the native header, or a kit panel's borrowed header.
     */
    void setHeader(String id, dev.jasper.app.contributions.PanelHeaderState header) {
        headers.put(id, Objects.requireNonNull(header));
        for (Host host : hosts.values()) if (id.equals(host.id)) host.header.accept(header);
    }

    void removeContent(String id) { contents.remove(id); headers.remove(id); }

    /** Window disposal releases borrowed kit headers even when the plugin closed its view first. */
    void close() {
        for (var host : hosts.values()) { host.releaseHeader.run(); host.removeAll(); remove(host); }
        hosts.clear(); contents.clear(); headers.clear();
    }

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
     * others. Content leaves the component tree while its window is hidden. Every stale frame is released before any
     * new one mounts: a window that moves keeps its content and a kit view's borrowed header, which its old frame's
     * release would otherwise take back from the new frame.
     */
    void sync() {
        for (Slot slot : Slot.values()) {
            Content wanted = wanted(slot);
            Host host = hosts.get(slot);
            if (host != null && (wanted == null || host.content != wanted.component()
                    || host.borrowedHeader != kitHeader(wanted.component()))) {
                remove(host);
                host.releaseHeader.run();
                host.removeAll();
                hosts.remove(slot);
            }
        }
        for (Slot slot : Slot.values()) {
            String id = state.active(slot).orElse(null);
            Content wanted = wanted(slot);
            Host host = hosts.get(slot);
            if (wanted != null && host == null) {
                host = new Host(true);
                host.id = id;
                host.content = wanted.component();
                host.borrowedHeader = kitHeader(wanted.component());
                if (wanted.component() instanceof dev.jasper.ui.swing.KitView view
                        && view.spec().root() instanceof dev.jasper.ui.model.Nodes.Panel panel) {
                    host.releaseHeader = mountHeader(host, view, panel.id(), id);
                } else {
                    ToolWindowHeader header = header(id, wanted.title());
                    host.add(header, BorderLayout.NORTH);
                    var surfaces = header.dragSurfaces();
                    for (JComponent surface : surfaces) dragSource.accept(surface, id);
                    host.header = header::presentation;
                    host.focusedHeader = header::setFocused;
                    Host mounted = host;
                    host.releaseHeader = () -> { mounted.header = state -> { }; mounted.focusedHeader = value -> { }; surfaces.forEach(releaseDragSource); };
                }
                if (wanted.component() instanceof dev.jasper.ui.swing.KitView view) {
                    var release = host.releaseHeader;
                    Host mounted = host;
                    // Plugin views update often (progress, switches, chips). Only a different borrowed header, or a
                    // closed view, changes the dock frame; content-only updates must not relayout the whole area.
                    java.beans.PropertyChangeListener changed = event -> { if (mounted.borrowedHeader != kitHeader(view)) sync(); };
                    view.addPropertyChangeListener("viewSpec", changed);
                    host.releaseHeader = () -> { view.removePropertyChangeListener("viewSpec", changed); release.run(); };
                }
                host.add(wanted.component(), BorderLayout.CENTER);
                ToolWindowSurface.apply(host);
                hosts.put(slot, host);
                add(host);
            }
        }
        refreshGutters();
        revalidate(); repaint();
        onMinimumSizeChanged.run();
        if (flush) focusMoved(java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner());
    }

    /** The frame holding the permanent focus owner, under flush frames. */
    private Slot focused;
    /** Follows the permanent focus owner while the area is showing, under flush frames. */
    private final java.beans.PropertyChangeListener focusListener = event -> focusMoved(event.getNewValue() instanceof Component owner ? owner : null);
    /** Hears every mouse press while the area is showing, under flush frames, and activates the frame it lands in. */
    private final java.awt.event.AWTEventListener pressListener = event -> {
        if (event.getID() == MouseEvent.MOUSE_PRESSED && event.getSource() instanceof Component source
                && SwingUtilities.isDescendingFrom(source, this)) pressed(source);
    };
    /** Moves the keyboard into an activated frame; tests replace it. */
    Consumer<Component> focuser = Component::requestFocusInWindow;

    @Override public void addNotify() {
        super.addNotify();
        if (!flush) return;
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", focusListener);
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(pressListener, java.awt.AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override public void removeNotify() {
        if (flush) {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("permanentFocusOwner", focusListener);
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(pressListener);
        }
        super.removeNotify();
    }

    /** Spec 10.7: the frame holding {@code owner} shows its header strip as focused, the others not; standard frames ignore it. */
    void focusMoved(Component owner) {
        if (!flush) return;
        Slot now = null;
        for (var entry : hosts.entrySet())
            if (owner != null && SwingUtilities.isDescendingFrom(owner, entry.getValue())) now = entry.getKey();
        mark(now);
    }

    /** Shows {@code slot}'s frame, or none, as the focused one. */
    private void mark(Slot slot) {
        focused = slot;
        for (var entry : hosts.entrySet()) entry.getValue().focused(entry.getKey() == focused);
    }

    /**
     * Plan 12 Rulings 18: a press inside a flush frame activates it, as IntelliJ activates a tool window, and once the
     * press is dispatched moves the keyboard into it unless it is there already. A press in the terminal column hands
     * the tint back; a press in no frame, such as on a divider's drag area, changes nothing. Standard frames ignore it.
     */
    void pressed(Component target) {
        if (!flush || target == null) return;
        if (SwingUtilities.isDescendingFrom(target, center)) { mark(null); return; }
        for (var entry : hosts.entrySet())
            if (SwingUtilities.isDescendingFrom(target, entry.getValue())) { activate(entry.getKey()); return; }
    }

    /** Activates the frame showing the tool window {@code id}, as a press inside it would; standard frames ignore it. */
    void activate(String id) {
        if (!flush) return;
        for (var entry : hosts.entrySet())
            if (entry.getValue().id != null && entry.getValue().id.equals(id)) { activate(entry.getKey()); return; }
    }

    private void activate(Slot slot) {
        mark(slot);
        Host host = hosts.get(slot);
        SwingUtilities.invokeLater(() -> {
            Component owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
            if (focused != slot || hosts.get(slot) != host || owner != null && SwingUtilities.isDescendingFrom(owner, host)) return;
            Component target = focusTarget(slot);
            if (target == host) host.setFocusable(true);
            focuser.accept(target);
        });
    }

    /**
     * Where an activated frame's keyboard goes: the first component of its content, depth first, that is enabled,
     * focusable, visible with every ancestor up to the frame and bound to keys while focused, a button only when no
     * other component is, as IntelliJ focuses a tool window's list or tree before its toolbar; else the frame itself.
     * Visibility is read through {@code isVisible()}, never {@code isShowing()}, so it holds before the window shows.
     */
    Component focusTarget(Slot slot) {
        Host host = hosts.get(slot);
        if (host == null) return null;
        Component found = host.content == null ? null : keyed(host.content, false);
        if (found == null && host.content != null) found = keyed(host.content, true);
        return found != null ? found : host;
    }

    private static Component keyed(Component component, boolean buttons) {
        if (!component.isVisible()) return null;
        if (component instanceof JComponent candidate && candidate.isEnabled() && candidate.isFocusable()
                && (buttons || !(candidate instanceof javax.swing.AbstractButton))) {
            var keys = candidate.getInputMap(JComponent.WHEN_FOCUSED).allKeys();
            if (keys != null && keys.length > 0) return candidate;
        }
        if (component instanceof Container container)
            for (Component child : container.getComponents()) {
                Component found = keyed(child, buttons);
                if (found != null) return found;
            }
        return null;
    }

    /** The frame showing its header strip as focused, if any. */
    java.util.Optional<Slot> focusedSlot() { return java.util.Optional.ofNullable(focused); }

    /** A divider's drag area: the divider itself, or under flush frames {@code DOCK_RULE_HIT} across, centred on its rule. */
    private Rectangle hitArea(DockGeometry.Divider divider) {
        Rectangle bounds = divider.bounds();
        if (!flush) return bounds;
        int hit = UIScale.scale(kit.metrics().value(DesignMetrics.Role.DOCK_RULE_HIT));
        int grow = Math.max(0, hit - (divider.handle().vertical() ? bounds.width : bounds.height));
        if (divider.handle().vertical()) { bounds.x -= grow / 2; bounds.width += grow; }
        else { bounds.y -= grow / 2; bounds.height += grow; }
        return bounds;
    }

    /** Under flush frames, each laid out divider is a painted rule (spec 10.7). */
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!flush) return;
        graphics.setColor(painter.dockRule(ThemeZone.theme(this)));
        for (DividerHandle divider : dividers.values()) {
            if (divider.placed == null) continue;
            Rectangle rule = divider.placed.bounds();
            graphics.fillRect(rule.x, rule.y, rule.width, rule.height);
        }
    }

    /** What the slot's frame should show: its active window's content, or null for none or a closed kit view. */
    private Content wanted(Slot slot) {
        Content wanted = state.active(slot).map(contents::get).orElse(null);
        return wanted != null && wanted.component() instanceof dev.jasper.ui.swing.KitView view && view.isClosed() ? null : wanted;
    }

    private static JComponent kitHeader(JComponent component) {
        return component instanceof dev.jasper.ui.swing.KitView view && !view.isClosed()
            && view.spec().root() instanceof dev.jasper.ui.model.Nodes.Panel panel ? view.panelHeader(panel.id()) : null;
    }

    /**
     * Borrow only the kit header; the panel and its footer remain owned by the view. Header metadata appears in the
     * borrowed row as on a native header: detail and dot after the title, contributed actions before Options and Hide.
     */
    private Runnable mountHeader(Host host, dev.jasper.ui.swing.KitView view, String panel, String id) {
        var header = view.panelHeader(panel);
        var sources = new java.util.ArrayList<JComponent>();
        collectHeaderSources(header, sources);
        sources.forEach(component -> dragSource.accept(component, id));
        var ready = dev.jasper.ui.model.ControlState.READY;
        var ghost = dev.jasper.ui.model.Nodes.ButtonKind.GHOST;
        // The view lives only in this window's area, so the flag is never cleared on release.
        if (artwork == dev.jasper.ui.icons.ChromeArtwork.CLASSIC) view.classicChromeIcons(true);
        view.setPanelHostActions(panel, List.of(
            new dev.jasper.ui.model.Nodes.Button("host.options", ready, "", "more.vertical", ghost, "host.options", "Options"),
            new dev.jasper.ui.model.Nodes.Button("host.hide", ready, "", flush ? "remove" : "collapse", ghost, "host.hide", "Hide")), event -> {
                if (event instanceof dev.jasper.ui.model.UiEvent.Action action) {
                    if (action.id().equals("host.hide")) onHide.accept(id);
                    else if (action.id().equals("host.options"))
                        showOptions.accept(optionsAnchor(header), optionsMenu(id));
                }
            });
        // A closed view, or one whose header was replaced (the dock then resyncs), takes no more metadata.
        java.util.function.BooleanSupplier borrowed = () -> !view.isClosed()
            && view.spec().root() instanceof dev.jasper.ui.model.Nodes.Panel current
            && current.id().equals(panel) && view.panelHeader(panel) == header;
        // The last metadata is kept, so a focus change can send the header again with its frame.
        var shown = new java.util.concurrent.atomic.AtomicReference<>(headers.getOrDefault(id, dev.jasper.app.contributions.PanelHeaderState.EMPTY));
        host.header = state -> { shown.set(state); if (borrowed.getAsBoolean()) view.setPanelHostHeader(panel, hostHeader(state, frame(host))); };
        host.focusedHeader = value -> { if (borrowed.getAsBoolean()) view.setPanelHostHeader(panel, hostHeader(shown.get(), frame(host))); };
        host.header.accept(shown.get());
        return () -> {
            host.header = state -> { };
            host.focusedHeader = value -> { };
            sources.forEach(releaseDragSource);
            // Plugin disposal can precede removing the dock frame. A closed view has already released its controls.
            if (borrowed.getAsBoolean()) {
                view.setPanelHostHeader(panel, dev.jasper.ui.swing.PanelHostHeader.EMPTY);
                view.setPanelHostActions(panel, List.of(), event -> {});
            }
        };
    }

    /** How a borrowed header is framed: as the view draws it, or as a flush frame's strip, focused or not. */
    private dev.jasper.ui.swing.PanelHostHeader.Frame frame(Host host) {
        if (!flush) return dev.jasper.ui.swing.PanelHostHeader.Frame.STANDARD;
        return host.focused ? dev.jasper.ui.swing.PanelHostHeader.Frame.FLUSH_FOCUSED : dev.jasper.ui.swing.PanelHostHeader.Frame.FLUSH;
    }

    /**
     * The module's description of the same metadata in its frame; callbacks stay the plugin boundary's guarded, contained
     * ones. On a header strip a host module glyph is described too, so the strip draws it as its other actions (spec 10.7).
     * In a standard frame under classic artwork a host module glyph of the classic set is given as its classic art, which
     * the view draws as given (plan 15b).
     */
    private dev.jasper.ui.swing.PanelHostHeader hostHeader(dev.jasper.app.contributions.PanelHeaderState state,
                                                           dev.jasper.ui.swing.PanelHostHeader.Frame frame) {
        boolean strip = frame != dev.jasper.ui.swing.PanelHostHeader.Frame.STANDARD;
        boolean classic = artwork == dev.jasper.ui.icons.ChromeArtwork.CLASSIC;
        return new dev.jasper.ui.swing.PanelHostHeader(state.detail(), state.active(), state.actions().stream()
            .map(action -> new dev.jasper.ui.swing.PanelHostHeader.Action(action.label(),
                classic && !strip ? dev.jasper.app.platform.AppIcons.classicChromeIcon(action.icon()).orElse(action.icon()) : action.icon(),
                action.invoke(), action.icon() instanceof dev.jasper.app.platform.PresentationIcon named
                ? new dev.jasper.ui.swing.PanelHostHeader.Artwork(named.name(), named.pathData())
                : strip ? dev.jasper.app.platform.AppIcons.kitName(action.icon()).map(name -> new dev.jasper.ui.swing.PanelHostHeader.Artwork(name, null)).orElse(null) : null)).toList(), frame);
    }

    /** The host Options button inside a borrowed header, so the menu opens under it; the header itself otherwise. */
    private static JComponent optionsAnchor(JComponent header) {
        for (var child : header.getComponents()) {
            if (child instanceof javax.swing.AbstractButton button && "Options".equals(button.getToolTipText())) return button;
            if (child instanceof JComponent nested) {
                JComponent found = optionsAnchor(nested);
                if (found != nested) return found;
            }
        }
        return header;
    }

    private static void collectHeaderSources(JComponent component, java.util.List<JComponent> sources) {
        if (component instanceof javax.swing.AbstractButton) return;
        sources.add(component);
        for (var child : component.getComponents())
            if (child instanceof JComponent next) collectHeaderSources(next, sources);
    }

    /** Ends a divider drag without applying it: entering or leaving Zen changes which windows show mid-drag. */
    void cancelResize() {
        dividers.values().forEach(divider -> { divider.dragging = null; divider.moved = false; });
        if (preview == null) return;
        preview = null;
        revalidate(); repaint();
    }

    private DockState layoutState() { return preview != null ? preview : state; }

    /** The window's Options menu as it would open now: its slot, mode and visibility are read from the dock state. */
    private javax.swing.JPopupMenu optionsMenu(String id) {
        return ToolWindowMenu.create(state.slot(id).orElse(Slot.LEFT_TOP), state.mode(id), state.visible(id),
            slot -> onMove.accept(id, slot), mode -> onMode.accept(id, mode), () -> onHide.accept(id));
    }

    private ToolWindowHeader header(String id, String title) {
        var header = new ToolWindowHeader(title,
            () -> optionsMenu(id), () -> onHide.accept(id), kit, flush, artwork);
        header.presentation(headers.getOrDefault(id, dev.jasper.app.contributions.PanelHeaderState.EMPTY));
        return header;
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
        setBackground(ThemeZone.color(null, SemanticColor.SURFACE_APP));
        hosts.values().forEach(ToolWindowSurface::apply);
    }

    /**
     * A visible stripe frames the terminal and supplies its side's spacing. On a side without one the terminal meets
     * the window edge borderless and square, as in Zen, and tool windows keep their exterior gutter. With neither, the
     * terminal fills its available area.
     */
    void setStripesVisible(boolean left, boolean right) {
        if (leftStripeVisible == left && rightStripeVisible == right) return;
        leftStripeVisible = left;
        rightStripeVisible = right;
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
     * Exterior gutters are the window's kit's gutter (6px by default, none under the classic kit) on each edge of the area
     * a frame touches, except where the header, a stripe or the status bar already supplies the spacing; facing edges have
     * none, because the divider is the gap.
     */
    private void refreshGutters() {
        int gutter = gutter();
        int top = headerVisible ? 0 : gutter, bottom = statusVisible ? 0 : gutter;
        int left = leftStripeVisible ? 0 : gutter, right = rightStripeVisible ? 0 : gutter;
        boolean framed = leftStripeVisible || rightStripeVisible;
        boolean bottomShown = layoutState().showing(Side.BOTTOM);
        center.rounded(leftStripeVisible, rightStripeVisible);
        lowerInset.setVisible(framed);
        if (framed) center.gutters(top, 0, bottomShown ? 0 : bottom, 0);
        else center.gutters(0, 0, 0, 0);
        hosts.forEach((slot, host) -> {
            boolean alone = state.active(slot.partner()).isEmpty(), first = !slot.split();
            if (slot.side() == Side.BOTTOM)
                host.gutters(0, first || alone ? left : 0, bottom, !first || alone ? right : 0);
            else
                host.gutters(first || alone ? top : 0, slot.side() == Side.LEFT ? left : 0,
                    (!first || alone) && !bottomShown ? bottom : 0, slot.side() == Side.RIGHT ? right : 0);
        });
    }

    private Dimension size(Dimension terminal, boolean preferred) { return size(terminal, state, metrics(), preferred); }

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
    DockGeometry.Metrics metrics() {
        double scale = UIScale.getUserScaleFactor();
        return flush ? DockGeometry.Metrics.at(scale, kit.metrics().value(DesignMetrics.Role.DOCK_RULE)) : DockGeometry.Metrics.at(scale);
    }
}
