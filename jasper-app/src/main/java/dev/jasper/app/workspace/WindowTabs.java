package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.NativeControlStyles;
import dev.jasper.ui.theme.SemanticColor;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.LongSupplier;
import javax.swing.*;

/** Equal-width, centered workspace tabs over the retained Swing selection model. EDT only. */
final class WindowTabs extends JPanel implements AutoCloseable {
    private static final int TITLE_LIMIT = 200;
    private final WindowContent owner;
    private final LongSupplier clock;
    private final List<Slot> slots = new ArrayList<>();
    private final IdentityHashMap<Component, Entry> entries = new IdentityHashMap<>();
    private final List<Component> order = new ArrayList<>();
    private final JPanel viewport = new JPanel(null);
    private final Value inset = new Value();
    private AnimationSettings animations = AnimationSettings.defaults();
    private Timer timer;
    private Component selected;
    private int layoutWidth = -1, layoutHeight = -1;
    private boolean active = true, disposed;

    WindowTabs(WindowContent owner) { this(owner, System::nanoTime); }
    WindowTabs(WindowContent owner, LongSupplier clock) {
        super(null);
        this.owner = owner; this.clock = clock;
        setName("windowTabs"); setOpaque(false); setVisible(false);
        viewport.setOpaque(false); viewport.setName("tabViewport"); add(viewport);
    }

    void refresh() {
        if (disposed) return;
        long now = clock.getAsLong(); sample(now);
        var updated = new ArrayList<Component>();
        for (int i = 0; i < owner.tabStrip().getTabCount(); i++) updated.add(owner.tabStrip().getComponentAt(i));
        boolean changed = !updated.equals(order);
        boolean added = updated.stream().anyMatch(tab -> !entries.containsKey(tab));
        boolean removed = order.stream().anyMatch(tab -> !updated.contains(tab));
        boolean reveal = order.size() <= 1 && updated.size() > 1;
        if (reveal && getParent() != null) {
            // BorderLayout does not size a hidden NORTH child. Establish its real width before starting motion.
            Insets border = getParent().getInsets();
            int width = Math.max(0, getParent().getWidth() - border.left - border.right);
            setSize(width, UIScale.scale(owner.tabHeight()));
            doLayout();
        }
        if (changed) {
            for (Component tab : List.copyOf(order)) if (!updated.contains(tab)) {
                Entry entry = entries.remove(tab);
                entry.slot.entry = null; // The shrinking gap retains no closed session, plugin or controls.
                viewport.remove(entry);
            }
            if (!added && !removed) { settle(); slots.clear(); }
            order.clear(); order.addAll(updated);
            for (int i = 0; i < order.size(); i++) {
                Component tab = order.get(i);
                if (!entries.containsKey(tab)) {
                    var entry = new Entry(tab); entries.put(tab, entry); viewport.add(entry);
                    int at = i == 0 ? 0 : slots.indexOf(entries.get(order.get(i - 1)).slot) + 1;
                    while (at < slots.size() && slots.get(at).entry == null) at++;
                    slots.add(at, entry.slot);
                } else if (!added && !removed) slots.add(entries.get(tab).slot);
            }
            Curve curve = reveal ? Curve.REVEAL : added ? Curve.OPEN : Curve.CLOSE;
            int millis = reveal ? (int) Math.round(animations.tabOpenMillis() * 490.0 / 475)
                : added ? animations.tabOpenMillis() : animations.tabCloseMillis();
            if (order.size() <= 1 || !added && !removed || !animations.enabled() || layoutWidth <= 0) millis = 0;
            if (reveal) {
                // The reference first exposes the full-height rail, then expands a compact group at 100 logical px.
                for (Slot slot : slots) slot.width.set(0);
                inset.set(Math.min(NativeControlStyles.metric(DesignMetrics.Role.TAB_REVEAL_INSET), Math.max(0, getWidth()) / 2.0));
            }
            inset.target(0, now, millis, curve);
            for (Slot slot : slots) slot.width.target(slot.entry == null ? 0 : 1.0 / order.size(), now, millis, curve);
            sample(now);
        }
        selected = owner.selectedComponent();
        setBackground(owner.theme().palette().background());
        setVisible(order.size() > 1);
        for (Component tab : order) entries.get(tab).refresh();
        doLayout(); syncTimer(); revalidate(); repaint();
    }

    void setActive(boolean active) { this.active = active; refresh(); }

    /** Literal bounded titles, also used by hosted-content integrations. */
    JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (Component tab : order) {
            var item = new JCheckBoxMenuItem(capped(owner.tabTitle(tab)), owner.tabIcon(tab), tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) owner.selectTab(tab); });
            menu.add(item);
        }
        NativeControlStyles.menuTree(menu, FlatLafInstaller::installedTheme);
        return menu;
    }

    @Override public Dimension getMinimumSize() {
        return new Dimension(0, order.size() > 1 ? UIScale.scale(owner.tabHeight()) : 0);
    }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        // Revealing the bar reclaims its full height immediately; a genuine resize settles motion.
        if (layoutWidth > 0 && (width != layoutWidth || layoutHeight > 0 && height > 0 && height != layoutHeight)) settle();
        layoutWidth = width; layoutHeight = height;
        viewport.setBounds(0, 0, width, height);
        double x = inset.current;
        for (Slot slot : slots) {
            int left = (int) Math.round(x);
            x += slot.width.current * width;
            int right = (int) Math.round(x);
            slot.bounds.setBounds(left, 0, Math.max(0, right - left), height);
            if (slot.entry != null) {
                slot.entry.setBounds(slot.bounds);
                slot.entry.setVisible(right > left);
                slot.entry.doLayout();
            }
        }
    }

    // Repaint the translucent surface exactly once, even for child hover repaints.
    @Override protected boolean isPaintingOrigin() { return true; }
    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(surface(true)); g.fillRect(0, 0, getWidth(), getHeight());
            for (Slot slot : slots) {
                g.setColor(surface(slot.entry != null && slot.entry.tab == selected));
                g.fill(slot.bounds);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(owner.theme().workspace().semantic().color(SemanticColor.BORDER_WINDOW));
            int line = NativeControlStyles.metric(DesignMetrics.Role.TAB_DIVIDER);
            for (int i = 0; i < slots.size() - 1; i++) {
                Rectangle bounds = slots.get(i).bounds;
                if (bounds.width > 0) g.fillRect(bounds.x + bounds.width - line, 0, line, getHeight());
            }
            g.fillRect(0, getHeight() - line, getWidth(), line);
        } finally { g.dispose(); }
    }

    private Color surface(boolean selected) {
        Color base = owner.workspaceSurface();
        boolean dark = owner.theme().workspace().dark();
        double blend = selected ? (dark ? .035 : .12) : (dark ? .3 : .06);
        int target = selected ? 255 : 0;
        return new Color((int) Math.round(base.getRed() * (1 - blend) + target * blend),
            (int) Math.round(base.getGreen() * (1 - blend) + target * blend),
            (int) Math.round(base.getBlue() * (1 - blend) + target * blend), base.getAlpha());
    }

    private static String capped(String title) {
        if (title.length() <= TITLE_LIMIT) return title;
        int end = TITLE_LIMIT - 1;
        if (Character.isHighSurrogate(title.charAt(end - 1))) end--; // never split an emoji
        return title.substring(0, end) + "\u2026";
    }

    void setAnimations(AnimationSettings settings) {
        if (disposed || animations.equals(settings)) return;
        animations = settings; settle(); doLayout(); repaint();
    }

    /** One elapsed-time frame; tests inject the same monotonic clock used when retargeting. */
    void animate(long now) {
        if (disposed) return;
        sample(now); doLayout(); syncTimer(); repaint();
    }
    private void sample(long now) {
        inset.sample(now);
        for (Slot slot : slots) slot.width.sample(now);
        slots.removeIf(slot -> slot.entry == null && !slot.width.running);
    }
    private void settle() {
        inset.set(0);
        slots.removeIf(slot -> slot.entry == null);
        for (Slot slot : slots) slot.width.set(order.isEmpty() ? 0 : 1.0 / order.size());
        syncTimer();
    }
    boolean motionRunning() { return timer != null && timer.isRunning(); }
    private void syncTimer() {
        boolean moving = inset.running || slots.stream().anyMatch(slot -> slot.width.running);
        if (moving && !disposed && timer == null) {
            timer = new Timer(8, event -> animate(clock.getAsLong())); timer.start();
        } else if ((!moving || disposed) && timer != null) { timer.stop(); timer = null; }
    }
    @Override public void close() {
        disposed = true; syncTimer(); slots.clear(); entries.clear(); order.clear(); selected = null;
        viewport.removeAll(); setVisible(false);
    }

    private static final class Slot {
        Entry entry;
        final Value width = new Value();
        final Rectangle bounds = new Rectangle();
        Slot(Entry entry) { this.entry = entry; }
    }

    /** Measured monotonic response, normalized to operation duration. See docs/design/alacritty-tabs.md. */
    private enum Curve {
        REVEAL(new double[]{0, 25, 42, 58, 75, 92, 108, 125, 142, 158, 175, 192, 208, 233, 267, 300, 350, 400, 490},
            new double[]{0, .012, .143, .263, .387, .497, .595, .678, .747, .803, .847, .884, .911, .940, .967, .982, .994, .998, 1}),
        OPEN(new double[]{0, 25, 42, 58, 75, 92, 108, 125, 142, 158, 175, 192, 208, 225, 250, 283, 317, 367, 417, 475},
            new double[]{0, .088, .201, .331, .444, .546, .637, .711, .775, .824, .863, .894, .919, .940, .958, .975, .986, .993, .997, 1}),
        CLOSE(new double[]{0, 25, 42, 58, 75, 92, 108, 125, 142, 158, 175, 200, 233, 267, 300, 350, 375, 425},
            new double[]{0, .077, .250, .373, .486, .585, .665, .736, .796, .842, .877, .919, .951, .972, .986, .993, .997, 1});
        private final double[] times, values;
        Curve(double[] times, double[] values) { this.times = times; this.values = values; }
        double at(double fraction) {
            double time = fraction * times[times.length - 1];
            for (int i = 1; i < times.length; i++) if (time <= times[i]) {
                double local = (time - times[i - 1]) / (times[i] - times[i - 1]);
                return values[i - 1] + local * (values[i] - values[i - 1]);
            }
            return 1;
        }
    }
    private static final class Value {
        double current, from, to;
        long start, nanos;
        boolean running;
        Curve curve = Curve.OPEN;
        void set(double value) { current = from = to = value; running = false; }
        void target(double value, long now, int millis, Curve curve) {
            from = current; to = value; start = now; nanos = millis * 1_000_000L; this.curve = curve;
            running = millis > 0 && current != to;
            if (!running) set(value);
        }
        void sample(long now) {
            if (!running) return;
            double progress = Math.max(0, Math.min(1, (double) (now - start) / nanos));
            current = from + (to - from) * curve.at(progress);
            if (progress >= 1) set(to);
        }
    }

    private static void flat(JButton button) {
        button.putClientProperty("html.disable", true);
        NativeControlStyles.unframed(button);
        button.setContentAreaFilled(false); button.setFocusable(false);
    }
    private final class Entry extends JPanel {
        private final Component tab;
        private final Slot slot;
        private final JButton close = new JButton();
        private final JButton select = new JButton() {
            @Override public JToolTip createToolTip() {
                var tip = new JToolTip(); tip.putClientProperty("html.disable", true); tip.setComponent(this); return tip;
            }
        };
        private boolean hovered;
        private Point origin;

        Entry(Component tab) {
            super(null); this.tab = tab; slot = new Slot(this); setOpaque(false);
            flat(select); flat(close); select.setHorizontalAlignment(SwingConstants.CENTER);
            select.addActionListener(event -> { if (!disposed) owner.selectTab(tab); });
            close.setRolloverEnabled(true); close.setToolTipText("Close tab");
            close.getAccessibleContext().setAccessibleName("Close tab");
            close.addActionListener(event -> { if (!disposed) owner.closeTab(tab); });
            MouseAdapter gestures = new MouseAdapter() {
                private void popup(MouseEvent event) { owner.tabMenu(tab).show(event.getComponent(), event.getX(), event.getY()); }
                @Override public void mouseEntered(MouseEvent event) { hover(true); }
                @Override public void mouseExited(MouseEvent event) {
                    hover(contains(SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), Entry.this)));
                }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event) && event.getComponent() != close) {
                        owner.selectTab(tab);
                        origin = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    }
                }
                @Override public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) { popup(event); return; }
                    Point start = origin; origin = null;
                    if (disposed || start == null || !SwingUtilities.isLeftMouseButton(event)) return;
                    Point end = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    if (start.distance(end) <= NativeControlStyles.metric(DesignMetrics.Role.TAB_REORDER_THRESHOLD)
                        || !viewport.getBounds().contains(end)) return;
                    settle(); doLayout();
                    for (int i = 0; i < order.size(); i++) {
                        Entry target = entries.get(order.get(i));
                        if (target.isVisible() && SwingUtilities.convertRectangle(viewport, target.getBounds(), WindowTabs.this).contains(end)) {
                            owner.reorderTab(owner.tabStrip().indexOfComponent(tab), i); break;
                        }
                    }
                }
            };
            addMouseListener(gestures); select.addMouseListener(gestures); close.addMouseListener(gestures);
            add(close); add(select);
        }
        private void hover(boolean value) { hovered = value; doLayout(); repaint(); }
        void refresh() {
            String text = owner.tabTitle(tab);
            select.setText(text); select.getAccessibleContext().setAccessibleName(text);
            select.setName("select:" + text); close.setName("close:" + text);
            String shortcut = owner.tabShortcut(order.indexOf(tab));
            String tip = capped(owner.tabTooltip(tab));
            select.setToolTipText(shortcut.isEmpty() ? tip : tip + " (" + shortcut + ")");
            boolean running = tab instanceof TerminalTab terminal && terminal.focusedPane() != null && terminal.focusedPane().running();
            select.getAccessibleContext().setAccessibleDescription(running ? "Session running" : null);
            Color surface = surface(tab == selected), ink = owner.theme().palette().foreground();
            close.setIcon(AppIcons.forSurface(AppIcons.named("CLOSE"), surface, ink));
            close.setRolloverIcon(AppIcons.forSurface(AppIcons.named("CLOSE_HOVERED"), surface, ink));
            select.setSelected(tab == selected); select.setFont(ThemeZone.font(Typography.Role.BODY));
            select.setForeground(tab == selected ? ink
                : ThemeZone.color(this, !active ? SemanticColor.TEXT_TERTIARY : SemanticColor.TEXT_SECONDARY));
            doLayout();
        }
        @Override public void doLayout() {
            boolean closeFits = getWidth() >= NativeControlStyles.metric(DesignMetrics.Role.TAB_CLOSE_FIT_WIDTH);
            int edge = Math.min(NativeControlStyles.metric(closeFits ? DesignMetrics.Role.TAB_CLOSE_GUTTER
                : DesignMetrics.Role.TAB_COMPACT_GUTTER), getWidth() / 2);
            // Reserve symmetric close gutters only when text and the affordance can fit together.
            select.setBounds(edge, 0, Math.max(0, getWidth() - 2 * edge), getHeight());
            int closeWidth = NativeControlStyles.metric(DesignMetrics.Role.TAB_CLOSE_WIDTH);
            close.setBounds(Math.max(0, getWidth() - NativeControlStyles.metric(DesignMetrics.Role.TAB_CLOSE_TRAILING) - closeWidth),
                0, closeWidth, getHeight());
            close.setVisible(hovered && closeFits);
        }
    }
}
