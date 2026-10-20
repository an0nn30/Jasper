package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.SemanticColor;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.LongSupplier;
import javax.swing.*;

/**
 * Equal-width, centered tabs of one tab group, always shown (workspace tabs spec 10). A tab that opens or closes moves
 * into or out of its place; one that joins or leaves the group by a move, or is reordered, takes its place at once
 * (spec 4, Rulings 27). EDT only.
 */
final class WindowTabs extends JPanel implements TabStrip {
    static final int TITLE_LIMIT = 200;
    private final GroupView group;
    private final LongSupplier clock;
    private final List<Slot> slots = new ArrayList<>();
    private final IdentityHashMap<Tab, Entry> entries = new IdentityHashMap<>();
    private final List<Tab> order = new ArrayList<>();
    private final JPanel viewport = new JPanel(null);
    private AnimationSettings animations = AnimationSettings.defaults();
    private Timer timer;
    private Tab selected;
    private int layoutWidth = -1, layoutHeight = -1;
    private boolean active = true, disposed;

    WindowTabs(GroupView group) { this(group, System::nanoTime); }
    WindowTabs(GroupView group, LongSupplier clock) {
        super(null);
        this.group = group; this.clock = clock;
        setName("windowTabs"); setOpaque(false);
        viewport.setOpaque(false); viewport.setName("tabViewport"); add(viewport);
    }

    @Override public JComponent component() { return this; }

    @Override public void refresh() {
        if (disposed) return;
        long now = clock.getAsLong(); sample(now);
        var updated = new ArrayList<>(group.tabs());
        boolean changed = !updated.equals(order);
        boolean added = updated.stream().anyMatch(tab -> !entries.containsKey(tab));
        boolean removed = order.stream().anyMatch(tab -> !updated.contains(tab));
        // Only an open or a close moves: a tab that arrives or leaves by a move does not (Rulings 27).
        boolean opened = updated.stream().anyMatch(tab -> !entries.containsKey(tab) && !group.moved(tab));
        boolean closing = order.stream().anyMatch(tab -> !updated.contains(tab) && tab.closed());
        if (changed) {
            for (Tab tab : List.copyOf(order)) if (!updated.contains(tab)) {
                Entry entry = entries.remove(tab);
                entry.slot.entry = null; // The shrinking gap retains no closed session, plugin or controls.
                viewport.remove(entry);
            }
            if (!added && !removed) { settle(); slots.clear(); }
            order.clear(); order.addAll(updated);
            for (int i = 0; i < order.size(); i++) {
                Tab tab = order.get(i);
                if (!entries.containsKey(tab)) {
                    var entry = new Entry(tab); entries.put(tab, entry); viewport.add(entry);
                    int at = i == 0 ? 0 : slots.indexOf(entries.get(order.get(i - 1)).slot) + 1;
                    while (at < slots.size() && slots.get(at).entry == null) at++;
                    slots.add(at, entry.slot);
                } else if (!added && !removed) slots.add(entries.get(tab).slot);
            }
            Curve curve = added ? Curve.OPEN : Curve.CLOSE;
            int millis = added ? animations.tabOpenMillis() : animations.tabCloseMillis();
            if (order.size() <= 1 || !opened && !closing || !animations.enabled() || !group.owner().lookAnimates() || layoutWidth <= 0) millis = 0;
            for (Slot slot : slots) slot.width.target(slot.entry == null ? 0 : 1.0 / order.size(), now, millis, curve);
            sample(now);
        }
        selected = group.selected();
        setBackground(group.owner().theme().surface());
        for (Tab tab : order) entries.get(tab).refresh();
        doLayout(); syncTimer(); revalidate(); repaint();
    }

    @Override public void setActive(boolean active) { this.active = active; refresh(); }
    /** Whether the strip shows as active. */
    boolean active() { return active; }

    /** Literal bounded titles, also used by hosted-content integrations. */
    @Override public JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (Tab tab : order) {
            var item = new JCheckBoxMenuItem(capped(group.title(tab)), group.icon(tab), tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) group.select(tab); });
            menu.add(item);
        }
        NativeStyles.installed().menuTree(menu, FlatLafInstaller::installedTheme);
        return menu;
    }

    @Override public List<Rectangle> tabBounds() {
        var bounds = new ArrayList<Rectangle>(order.size());
        for (Tab tab : order) bounds.add(SwingUtilities.convertRectangle(viewport, entries.get(tab).getBounds(), this));
        return bounds;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(group.owner().tabHeight())); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        // A genuine resize settles motion.
        if (layoutWidth > 0 && (width != layoutWidth || layoutHeight > 0 && height > 0 && height != layoutHeight)) settle();
        layoutWidth = width; layoutHeight = height;
        viewport.setBounds(0, 0, width, height);
        double x = 0;
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
        var bounds = new ArrayList<Rectangle>(slots.size());
        int chosen = -1, hovered = -1;
        for (Slot slot : slots) {
            if (slot.entry != null && slot.entry.tab == selected) chosen = bounds.size();
            if (slot.entry != null && slot.entry.hovered) hovered = bounds.size();
            bounds.add(slot.bounds);
        }
        ChromePainter.installed().tabStrip(this, graphics, group.owner().theme().workspace(), group.owner().workspaceSurface(), bounds, chosen, hovered, active);
    }

    private Color surface(boolean selected) {
        return ChromePainter.installed().tabSurface(group.owner().theme().workspace(), group.owner().workspaceSurface(), selected);
    }

    /** {@code title}, or past {@code TITLE_LIMIT} characters its start and an ellipsis within that limit, for tips and menus. */
    static String capped(String title) {
        if (title.length() <= TITLE_LIMIT) return title;
        int end = TITLE_LIMIT - 1;
        if (Character.isHighSurrogate(title.charAt(end - 1))) end--; // never split an emoji
        return title.substring(0, end) + "\u2026";
    }

    @Override public void setAnimations(AnimationSettings settings) {
        if (disposed || animations.equals(settings)) return;
        animations = settings; settle(); doLayout(); repaint();
    }

    /** One elapsed-time frame; tests inject the same monotonic clock used when retargeting. */
    void animate(long now) {
        if (disposed) return;
        sample(now); doLayout(); syncTimer(); repaint();
    }
    private void sample(long now) {
        for (Slot slot : slots) slot.width.sample(now);
        slots.removeIf(slot -> slot.entry == null && !slot.width.running);
    }
    private void settle() {
        slots.removeIf(slot -> slot.entry == null);
        for (Slot slot : slots) slot.width.set(order.isEmpty() ? 0 : 1.0 / order.size());
        syncTimer();
    }
    boolean motionRunning() { return timer != null && timer.isRunning(); }
    private void syncTimer() {
        boolean moving = slots.stream().anyMatch(slot -> slot.width.running);
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
        NativeStyles.installed().unframed(button);
        button.setContentAreaFilled(false); button.setFocusable(false);
    }
    private final class Entry extends JPanel {
        private final Tab tab;
        private final Slot slot;
        private final JButton close = new JButton();
        private final JButton select = new JButton() {
            @Override public JToolTip createToolTip() {
                var tip = new JToolTip(); tip.putClientProperty("html.disable", true); tip.setComponent(this); return tip;
            }
        };
        private boolean hovered;

        Entry(Tab tab) {
            super(null); this.tab = tab; slot = new Slot(this); setOpaque(false);
            flat(select); flat(close); select.setHorizontalAlignment(SwingConstants.CENTER);
            select.addActionListener(event -> { if (!disposed) group.select(tab); });
            close.setRolloverEnabled(true); close.setToolTipText("Close tab");
            close.getAccessibleContext().setAccessibleName("Close tab");
            close.addActionListener(event -> { if (!disposed) group.close(tab); });
            MouseAdapter gestures = new MouseAdapter() {
                private void popup(MouseEvent event) { group.menu(tab).show(event.getComponent(), event.getX(), event.getY()); }
                @Override public void mouseEntered(MouseEvent event) { hover(true); }
                @Override public void mouseExited(MouseEvent event) {
                    hover(contains(SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), Entry.this)));
                }
                @Override public void mousePressed(MouseEvent event) {
                    if (disposed) return;
                    if (event.isPopupTrigger()) { popup(event); return; }
                    if (SwingUtilities.isMiddleMouseButton(event)) { group.close(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event) && event.getComponent() != close) {
                        group.select(tab);
                        // The tab drag may start here (workspace tabs spec 12).
                        group.pressed(tab, event);
                    }
                }
                @Override public void mouseDragged(MouseEvent event) { group.dragged(event); }
                @Override public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) { popup(event); return; }
                    group.released(event);
                }
            };
            addMouseListener(gestures); select.addMouseListener(gestures); close.addMouseListener(gestures);
            addMouseMotionListener(gestures); select.addMouseMotionListener(gestures); close.addMouseMotionListener(gestures);
            add(close); add(select);
        }
        private void hover(boolean value) { hovered = value; doLayout(); repaint(); }
        void refresh() {
            String text = group.title(tab);
            select.setText(text); select.getAccessibleContext().setAccessibleName(text);
            select.setName("select:" + text); close.setName("close:" + text);
            String shortcut = group.shortcut(order.indexOf(tab));
            String tip = capped(group.tooltip(tab));
            select.setToolTipText(shortcut.isEmpty() ? tip : tip + " (" + shortcut + ")");
            select.getAccessibleContext().setAccessibleDescription(group.running(tab) ? "Session running" : null);
            Color surface = surface(tab == selected), ink = ThemeZone.color(this, SemanticColor.TEXT_PRIMARY);
            close.setIcon(AppIcons.forSurface(AppIcons.named("CLOSE"), surface, ink));
            close.setRolloverIcon(AppIcons.forSurface(AppIcons.named("CLOSE_HOVERED"), surface, ink));
            select.setSelected(tab == selected); select.setFont(ThemeZone.font(Typography.Role.BODY));
            select.setForeground(tab == selected ? ink
                : ThemeZone.color(this, !active ? SemanticColor.TEXT_TERTIARY : SemanticColor.TEXT_SECONDARY));
            doLayout();
        }
        @Override public void doLayout() {
            boolean closeFits = getWidth() >= NativeStyles.installed().metric(DesignMetrics.Role.TAB_CLOSE_FIT_WIDTH);
            int edge = Math.min(NativeStyles.installed().metric(closeFits ? DesignMetrics.Role.TAB_CLOSE_GUTTER
                : DesignMetrics.Role.TAB_COMPACT_GUTTER), getWidth() / 2);
            // Reserve symmetric close gutters only when text and the affordance can fit together.
            select.setBounds(edge, 0, Math.max(0, getWidth() - 2 * edge), getHeight());
            int closeWidth = NativeStyles.installed().metric(DesignMetrics.Role.TAB_CLOSE_WIDTH);
            close.setBounds(Math.max(0, getWidth() - NativeStyles.installed().metric(DesignMetrics.Role.TAB_CLOSE_TRAILING) - closeWidth),
                0, closeWidth, getHeight());
            close.setVisible(hovered && closeFits);
        }
    }
}
