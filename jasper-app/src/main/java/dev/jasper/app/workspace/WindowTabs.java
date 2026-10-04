package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.config.AnimationSettings;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.util.function.LongSupplier;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.appearance.Token;
import dev.jasper.app.platform.SystemFonts;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import javax.swing.*;

/**
 * Folder tabs over WindowContent's retained Swing selection model. The selected folder joins the workspace
 * surface; the recessed strip and fixed controls frame it independently of the surrounding chrome.
 */
final class WindowTabs extends JPanel implements AutoCloseable {
    private static final int MIN_TAB = 80, MAX_TAB = 240, LIST_WIDTH = 24, EDGE = 8, GAP = 6;
    /** Titles come from remote programs; tooltips and the tab list show at most this many characters. */
    private static final int TITLE_LIMIT = 200;
    private final WindowContent owner;
    private final LongSupplier clock;
    private final List<Slot> slots = new ArrayList<>();
    private final Value folderX = new Value(0), folderWidth = new Value(0), folderAlpha = new Value(0);
    private AnimationSettings animations = AnimationSettings.defaults();
    private Timer timer;
    private int layoutHeight = -1;
    private final IdentityHashMap<Component, Entry> entries = new IdentityHashMap<>();
    private final List<Component> order = new ArrayList<>();
    private final JButton list = new JButton();
    private final JButton add = new JButton();
    private final JPanel viewport = new JPanel(null) {
        // Child hover/close repaints must also repaint the fade above them.
        @Override protected boolean isPaintingOrigin() { return true; }
        @Override protected void paintChildren(Graphics graphics) {
            paintExits((Graphics2D) graphics);
            super.paintChildren(graphics);
            paintOverflow((Graphics2D) graphics);
        }
    };
    private Component selected;
    private double scrollOffset;
    private int contentWidth;
    private int layoutWidth = -1;
    private boolean revealSelection = true;
    private boolean active = true;
    private boolean disposed;

    WindowTabs(WindowContent owner) { this(owner, System::nanoTime); }

    WindowTabs(WindowContent owner, LongSupplier clock) {
        super(null);
        this.owner = owner;
        this.clock = clock;
        setName("windowTabs");
        flat(list); flat(add);
        add.setName("newTab");
        add.setIcon(AppIcons.named("ADD"));
        add.setToolTipText("New tab"); add.getAccessibleContext().setAccessibleName("New tab");
        add.addActionListener(event -> { if (!disposed) owner.invoke(ActionId.NEW_TAB); });
        list.setName("tabList");
        list.setIcon(AppIcons.named("DROPDOWN"));
        list.setToolTipText("Show all tabs"); list.getAccessibleContext().setAccessibleName("Show all tabs");
        list.addActionListener(event -> tabList().show(list, 0, list.getHeight()));
        setOpaque(false);
        viewport.setOpaque(false);
        viewport.setName("tabViewport");
        add(viewport); add(list); add(add);
        addMouseWheelListener(this::wheel);
        viewport.addMouseWheelListener(this::wheel);
        list.addMouseWheelListener(this::wheel);
        add.addMouseWheelListener(this::wheel);
    }

    void refresh() {
        if (disposed) return;
        long now = clock.getAsLong();
        sample(now);
        var updated = new ArrayList<Component>();
        for (int i = 0; i < owner.tabStrip().getTabCount(); i++) updated.add(owner.tabStrip().getComponentAt(i));
        boolean changed = !updated.equals(order);
        boolean added = updated.stream().anyMatch(tab -> !entries.containsKey(tab));
        boolean removed = order.stream().anyMatch(tab -> !updated.contains(tab));
        int duration = added ? animations.tabOpenMillis() : removed ? animations.tabCloseMillis() : 0;
        if (changed) {
            for (Component tab : List.copyOf(order)) if (!updated.contains(tab)) {
                Entry entry = entries.remove(tab);
                Slot slot = entry.slot;
                slot.exitWidth = entry.getWidth(); slot.exitHeight = entry.getHeight();
                slot.exit = snapshot(entry);
                slot.entry = null; // the exiting visual owns pixels only, never a closed tab or plugin
                viewport.remove(entry);
                slot.width.target(0, now, duration(animations.tabCloseMillis()));
                slot.alpha.target(0, now, duration(animations.tabCloseMillis()));
            }
            // Drag/reorder changes settle; they do not create a second motion system.
            if (!added && !removed) { settle(); slots.clear(); }
            order.clear(); order.addAll(updated);
            for (int i = 0; i < order.size(); i++) {
                Component tab = order.get(i);
                if (!entries.containsKey(tab)) {
                    Entry entry = new Entry(tab); entries.put(tab, entry); viewport.add(entry);
                    int at = i == 0 ? 0 : slots.indexOf(entries.get(order.get(i - 1)).slot) + 1;
                    // Keep an unfinished exit before a new tab opened in its former position.
                    while (at < slots.size() && slots.get(at).entry == null) at++;
                    slots.add(at, entry.slot);
                    entry.slot.width.target(entry.tabWidth(), now, duration(animations.tabOpenMillis()));
                    entry.slot.alpha.target(1, now, duration(animations.tabOpenMillis()));
                } else if (!added && !removed) slots.add(entries.get(tab).slot);
            }
            revealSelection = true;
        }
        boolean switched = selected != owner.selectedComponent();
        if (switched) { selected = owner.selectedComponent(); revealSelection = true; }
        Color background = owner.theme().palette().background();
        // A small workspace-derived recess works with independently light or dark terminal palettes.
        float shade = owner.theme().workspace().dark() ? .78f : .94f;
        setBackground(new Color(Math.round(background.getRed() * shade), Math.round(background.getGreen() * shade),
            Math.round(background.getBlue() * shade)));
        list.setIcon(AppIcons.forBackground(AppIcons.named("DROPDOWN"), getBackground()));
        add.setIcon(AppIcons.forBackground(AppIcons.named("ADD"), getBackground()));
        boolean retitled = false;
        for (Component tab : order) retitled |= entries.get(tab).refresh();
        // A tab that grows can push the selected tab out; keep it shown if it was shown.
        Entry current = entries.get(selected);
        if (retitled && current != null && current.isVisible()) revealSelection = true;
        list.setEnabled(!disposed); add.setEnabled(!disposed);
        for (Component tab : order) {
            Entry entry = entries.get(tab);
            if (entry.slot.width.to != entry.tabWidth()) {
                // Metadata changes settle an existing tab's width; opening keeps its current progress.
                int remaining = Math.max(entry.slot.width.remainingMillis(now), entry.slot.alpha.remainingMillis(now));
                entry.slot.width.target(entry.tabWidth(), now, duration(remaining));
                retitled = true;
            }
        }
        int remaining = slots.stream().mapToInt(slot -> slot.width.remainingMillis(now)).max().orElse(0);
        if (changed || switched || retitled) retargetFolder(now,
            changed ? duration : switched ? animations.tabSwitchMillis() : remaining);
        if (layoutWidth >= 0 && (changed || retitled)) doLayout();
        syncTimer();
        revalidate(); repaint();
    }

    void setActive(boolean active) { this.active = active; refresh(); }

    /** Scrolls the strip without changing the selected terminal. */
    private void wheel(MouseWheelEvent event) {
        if (disposed) return;
        settle(); doLayout();
        double distance = event.getPreciseWheelRotation() * (event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
            ? viewport.getWidth() : UIScale.scale(16) * event.getScrollAmount());
        scrollOffset = Math.max(0, Math.min(Math.max(0, contentWidth - viewport.getWidth()), scrollOffset + distance));
        revealSelection = false;
        doLayout(); repaint();
        event.consume();
    }

    /** Every tab in order, titles literal; choosing one selects and reveals it. */
    JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (Component tab : order) {
            var item = new JCheckBoxMenuItem(capped(owner.tabTitle(tab)), owner.tabIcon(tab), tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) { revealSelection = true; owner.selectTab(tab); revalidate(); repaint(); } });
            menu.add(item);
        }
        return menu;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(MIN_TAB), UIScale.scale(owner.tabHeight())); }
    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(MIN_TAB), UIScale.scale(owner.tabHeight())); }

    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        if (width != layoutWidth || height != layoutHeight) {
            layoutWidth = width; layoutHeight = height; revealSelection = true;
            settle();
        }
        int addWidth = Math.min(width, UIScale.scale(LIST_WIDTH));
        int listWidth = Math.min(Math.max(0, width - addWidth), UIScale.scale(LIST_WIDTH));
        int space = Math.max(0, width - listWidth - addWidth);
        double total = 0, selectedStart = 0, selectedWidth = 0;
        for (Slot slot : slots) {
            if (slot.entry != null && slot.entry.tab == selected) { selectedStart = total; selectedWidth = slot.width.current; }
            total += slot.width.current;
        }
        contentWidth = (int) Math.ceil(total);
        if (revealSelection && selectedWidth > 0) {
            double margin = selectedWidth + UIScale.scale(32) <= space ? UIScale.scale(16) : 0;
            if (selectedStart - margin < scrollOffset || selectedWidth > space) scrollOffset = selectedStart - margin;
            else if (selectedStart + selectedWidth + margin > scrollOffset + space) scrollOffset = selectedStart + selectedWidth + margin - space;
            // Continue revealing an expanding selected tab until its width has settled.
            revealSelection = entries.get(selected).slot.width.running;
        }
        scrollOffset = Math.max(0, Math.min(Math.max(0, contentWidth - space), scrollOffset));
        viewport.setBounds(0, 0, space, height);
        double x = 0;
        for (Slot slot : slots) {
            slot.x = x;
            int left = (int) Math.round(x - scrollOffset), right = (int) Math.round(x + slot.width.current - scrollOffset);
            if (slot.entry != null) {
                Entry entry = slot.entry;
                entry.setBounds(left, 0, Math.max(0, right - left), height);
                entry.setVisible(right > 0 && left < space && right > left);
                if (entry.isVisible()) entry.doLayout();
            }
            x += slot.width.current;
        }
        list.setBounds(space, 0, listWidth, height);
        add.setBounds(width - addWidth, 0, addWidth, height);
    }

    // Hover/close repaints reconstruct the strip before its children, including shoulders outside the entry.
    @Override protected boolean isPaintingOrigin() { return true; }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            // Replace the parent's glass once with the same workspace alpha. Paint only opaque negative space
            // over it: translucent antialiased Src shapes cause expensive Metal readbacks on macOS.
            g.setComposite(AlphaComposite.Src);
            g.setColor(owner.workspaceSurface());
            g.fillRect(0, 0, getWidth(), getHeight());
            var recess = new java.awt.geom.Area(new Rectangle(0, 0, getWidth(), getHeight()));
            if (folderWidth.current > 0 && folderAlpha.current > 0) {
                var folder = new java.awt.geom.Area(folder(folderX.current - scrollOffset, folderWidth.current));
                folder.intersect(new java.awt.geom.Area(viewport.getBounds()));
                recess.subtract(folder);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fill(recess);
            // Fade through opaque recess over the already replaced glass surface; repeated painting is idempotent.
            if (folderAlpha.current < 1 && folderWidth.current > 0) {
                g.setComposite(AlphaComposite.SrcOver.derive((float) (1 - folderAlpha.current)));
                var fading = new java.awt.geom.Area(folder(folderX.current - scrollOffset, folderWidth.current));
                fading.intersect(new java.awt.geom.Area(viewport.getBounds()));
                g.fill(fading);
            }
            // A slim accent pill travels with the folder, staying inside the scrolling tab viewport.
            double indicatorWidth = Math.min(UIScale.scale(32), Math.max(0, folderWidth.current - UIScale.scale(16)));
            if (indicatorWidth > 0 && folderAlpha.current > 0) {
                g.clip(viewport.getBounds());
                g.setComposite(AlphaComposite.SrcOver.derive((float) folderAlpha.current));
                g.setColor(owner.theme().workspace().tokens().get(Token.ACCENT));
                double thickness = UIScale.scale(2f);
                g.fill(new java.awt.geom.RoundRectangle2D.Double(
                    folderX.current - scrollOffset + (folderWidth.current - indicatorWidth) / 2,
                    getHeight() - UIScale.scale(3), indicatorWidth, thickness, thickness, thickness));
            }
        } finally { g.dispose(); }
    }

    /** Soft top corners with quarter-circle cutouts forming broad outward bottom shoulders. */
    private java.awt.Shape folder(double x, double w) {
        double h = getHeight();
        double r = Math.min(UIScale.scale(10f), Math.max(0, h / 2));
        double upper = Math.min(Math.min(UIScale.scale(ToolWindowArea.SURFACE_ARC) / 2.0, w / 2), Math.max(0, h / 2));
        double k = r * .5522847498;
        double upperK = upper * .5522847498;
        var shape = new java.awt.geom.Path2D.Double();
        shape.moveTo(x - r, h);
        shape.curveTo(x - r + k, h, x, h - r + k, x, h - r);
        shape.lineTo(x, upper);
        shape.curveTo(x, upper - upperK, x + upper - upperK, 0, x + upper, 0);
        shape.lineTo(x + w - upper, 0);
        shape.curveTo(x + w - upper + upperK, 0, x + w, upper - upperK, x + w, upper);
        shape.lineTo(x + w, h - r);
        shape.curveTo(x + w, h - r + k, x + w + r - k, h, x + w + r, h);
        shape.closePath();
        return shape;
    }

    /** Fade clipped tabs into the recessed strip; the tab-list button stays outside the viewport. */
    private void paintOverflow(Graphics2D graphics) {
        int width = viewport.getWidth(), height = viewport.getHeight();
        int fade = Math.min(UIScale.scale(16), width / 2);
        if (fade < 2) return;
        Color surface = getBackground();
        Color clear = new Color(surface.getRed(), surface.getGreen(), surface.getBlue(), 0);
        var g = (Graphics2D) graphics.create();
        try {
            if (scrollOffset >= 0.5) {
                g.setPaint(new GradientPaint(0, 0, surface, fade - 1, 0, clear));
                g.fillRect(0, 0, fade, height);
            }
            if (Math.round(scrollOffset) + width < contentWidth) {
                g.setPaint(new GradientPaint(width - fade, 0, clear, width - 1, 0, surface));
                g.fillRect(width - fade, 0, fade, height);
            }
        } finally { g.dispose(); }
    }

    private static String capped(String title) {
        if (title.length() <= TITLE_LIMIT) return title;
        int end = TITLE_LIMIT - 1;
        if (Character.isHighSurrogate(title.charAt(end - 1))) end--; // never split an emoji
        return title.substring(0, end) + "\u2026";
    }

    void setAnimations(AnimationSettings settings) {
        if (disposed || animations.equals(settings)) return;
        animations = settings;
        // Config edits, including temporary duration edits, settle consistently across all windows.
        settle(); doLayout(); repaint();
    }

    private int duration(int millis) { return animations.enabled() && layoutWidth >= 0 ? millis : 0; }

    private void retargetFolder(long now, int millis) {
        double x = 0;
        for (Slot slot : slots) {
            if (slot.entry != null && slot.entry.tab == selected) {
                folderX.target(x, now, duration(millis));
                folderWidth.target(slot.width.to, now, duration(millis));
                folderAlpha.target(1, now, duration(millis));
                return;
            }
            x += slot.width.to;
        }
        folderWidth.target(0, now, duration(millis));
        folderAlpha.target(0, now, duration(millis));
    }

    /** One elapsed-time frame, also callable with an injected monotonic clock in headless tests. */
    void animate(long now) {
        if (disposed) return;
        sample(now); doLayout(); syncTimer(); repaint();
    }

    private void sample(long now) {
        for (Slot slot : slots) { slot.width.sample(now); slot.alpha.sample(now); }
        folderX.sample(now); folderWidth.sample(now); folderAlpha.sample(now);
        slots.removeIf(slot -> slot.entry == null && !slot.width.running);
    }

    private void settle() {
        for (Slot slot : slots) { slot.width.finish(); slot.alpha.finish(); }
        slots.removeIf(slot -> slot.entry == null);
        folderX.finish(); folderWidth.finish(); folderAlpha.finish();
        retargetFolder(clock.getAsLong(), 0);
        syncTimer();
    }

    boolean motionRunning() { return timer != null && timer.isRunning(); }
    Rectangle2D folderBounds() { return new Rectangle2D.Double(folderX.current - scrollOffset, 0, folderWidth.current, getHeight()); }

    private void syncTimer() {
        boolean moving = folderX.running || folderWidth.running || folderAlpha.running
            || slots.stream().anyMatch(slot -> slot.width.running || slot.alpha.running);
        if (moving && !disposed && timer == null) {
            timer = new Timer(16, event -> animate(clock.getAsLong()));
            timer.start();
        } else if ((!moving || disposed) && timer != null) { timer.stop(); timer = null; }
    }

    private BufferedImage snapshot(Entry entry) {
        if (entry.getWidth() <= 0 || entry.getHeight() <= 0) return null;
        GraphicsConfiguration configuration = entry.getGraphicsConfiguration();
        var transform = configuration == null ? new java.awt.geom.AffineTransform() : configuration.getDefaultTransform();
        double scaleX = Math.max(1, Math.abs(transform.getScaleX())), scaleY = Math.max(1, Math.abs(transform.getScaleY()));
        var image = new BufferedImage((int) Math.ceil(entry.getWidth() * scaleX),
            (int) Math.ceil(entry.getHeight() * scaleY), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.scale(scaleX, scaleY);
            entry.paintSnapshot(graphics);
        } finally { graphics.dispose(); }
        return image;
    }

    private void paintExits(Graphics2D graphics) {
        for (Slot slot : slots) if (slot.entry == null && slot.exit != null && slot.width.current > 0) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setComposite(AlphaComposite.SrcOver.derive((float) slot.alpha.current));
                int x = (int) Math.round(slot.x - scrollOffset);
                g.clipRect(x, 0, (int) Math.round(slot.width.current), getHeight());
                g.drawImage(slot.exit, x, 0, slot.exitWidth, slot.exitHeight, null);
            } finally { g.dispose(); }
        }
    }

    @Override public void close() {
        disposed = true; syncTimer(); slots.clear(); entries.clear(); order.clear(); selected = null;
        viewport.removeAll(); list.setEnabled(false); add.setEnabled(false);
    }

    /** Retains no component after removal; exit is an inert raster of its last title and controls. */
    private static final class Slot {
        Entry entry;
        BufferedImage exit;
        int exitWidth, exitHeight;
        final Value width = new Value(0), alpha = new Value(0);
        double x;
        Slot(Entry entry) { this.entry = entry; }
    }

    /** A retargetable elapsed-time value. Smoothstep has stationary endpoints and no queued transitions. */
    private static final class Value {
        double current, from, to;
        long start, nanos;
        boolean running;
        Value(double value) { current = from = to = value; }
        void target(double value, long now, int millis) {
            if (to == value && running && millis > 0) return;
            from = current; to = value; start = now; nanos = millis * 1_000_000L;
            running = millis > 0 && current != to;
            if (!running) finish();
        }
        void sample(long now) {
            if (!running) return;
            double progress = Math.max(0, Math.min(1, (double) (now - start) / nanos));
            current = from + (to - from) * progress * progress * (3 - 2 * progress);
            if (progress >= 1) finish();
        }
        int remainingMillis(long now) {
            return running ? (int) Math.max(0, (nanos - (now - start) + 999_999L) / 1_000_000L) : 0;
        }
        void finish() { current = from = to; running = false; }
    }

    private static void flat(JButton button) {
        button.putClientProperty("html.disable", true);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false); button.setFocusable(false);
    }

    private final class Entry extends JPanel {
        private final Component tab;
        private final Slot slot;
        private final JButton close = new JButton();
        private final JButton select = new JButton() {
            // Swing creates a fresh tip that does not inherit html.disable from its component.
            @Override public JToolTip createToolTip() {
                var tip = new JToolTip();
                tip.putClientProperty("html.disable", true);
                tip.setComponent(this);
                return tip;
            }
        };
        private boolean hovered;
        private Point origin;

        Entry(Component tab) {
            super(null);
            this.tab = tab;
            slot = new Slot(this);
            setOpaque(false);
            flat(select); flat(close);
            select.setHorizontalAlignment(SwingConstants.LEADING);
            select.setIconTextGap(UIScale.scale(GAP));
            select.addActionListener(event -> { if (!disposed) owner.selectTab(tab); });
            close.setIcon(AppIcons.named("CLOSE"));
            close.setRolloverIcon(AppIcons.named("CLOSE_HOVERED"));
            close.setRolloverEnabled(true);
            close.setToolTipText("Close tab"); close.getAccessibleContext().setAccessibleName("Close tab");
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
                    if (start.distance(end) <= UIScale.scale(5) || !viewport.getBounds().contains(end)) return;
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
            addMouseWheelListener(WindowTabs.this::wheel);
            select.addMouseWheelListener(WindowTabs.this::wheel);
            close.addMouseWheelListener(WindowTabs.this::wheel);
            add(select); add(close);
        }

        private void hover(boolean value) {
            hovered = value;
            close.setVisible(tab == selected || hovered);
            repaint();
        }

        int tabWidth() {
            FontMetrics metrics = select.getFontMetrics(select.getFont());
            int content = UIScale.scale(EDGE + 16 + GAP) + metrics.stringWidth(owner.tabTitle(tab)) + UIScale.scale(GAP + 16 + EDGE);
            return Math.max(UIScale.scale(MIN_TAB), Math.min(UIScale.scale(MAX_TAB), content));
        }

        /** Returns whether the title changed. */
        boolean refresh() {
            String text = owner.tabTitle(tab);
            boolean retitled = !text.equals(select.getText());
            if (retitled) {
                select.setText(text); select.getAccessibleContext().setAccessibleName(text);
                select.setName("select:" + text); close.setName("close:" + text);
            }
            String shortcut = owner.tabShortcut(order.indexOf(tab));
            String tip = capped(owner.tabTooltip(tab));
            select.setToolTipText(shortcut.isEmpty() ? tip : tip + " (" + shortcut + ")");
            Color surface = tab == selected ? owner.theme().palette().background() : WindowTabs.this.getBackground();
            select.setIcon(tab == selected ? AppIcons.forBackground(owner.tabIcon(tab), surface) : null);
            close.setIcon(AppIcons.forBackground(AppIcons.named("CLOSE"), surface));
            close.setRolloverIcon(AppIcons.forBackground(AppIcons.named("CLOSE_HOVERED"), surface));
            select.setSelected(tab == selected);
            select.setFont(SystemFonts.ui(Font.PLAIN, 13f));
            select.setForeground(tab == selected ? owner.theme().palette().foreground()
                : WorkspaceTheme.color(this, Token.TEXT_MUTED, !active ? "Jasper.titleInactiveForeground" : "Jasper.titleForeground"));
            close.setVisible(tab == selected || hovered);
            return retitled;
        }

        void paintSnapshot(Graphics graphics) { super.paintChildren(graphics); }

        @Override public void paint(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setComposite(AlphaComposite.SrcOver.derive((float) slot.alpha.current));
                super.paint(g);
            } finally { g.dispose(); }
        }

        @Override public void doLayout() {
            int edge = UIScale.scale(EDGE), closeWidth = Math.min(UIScale.scale(16), getWidth());
            int closeX = Math.max(0, getWidth() - edge - closeWidth);
            close.setBounds(closeX, 0, closeWidth, getHeight());
            // Reserve the icon's space even on plain inactive labels, so selecting never changes the tab width.
            int leading = edge + (tab == selected ? 0 : UIScale.scale(16 + GAP));
            select.setBounds(Math.min(leading, getWidth()), 0,
                Math.max(0, closeX - UIScale.scale(GAP) - leading), getHeight());
        }
    }
}
