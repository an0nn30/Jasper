package dev.jasper.app.workspace;

import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.GeneratedTheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.Action;

/**
 * One of the classic look's labelled tool window stripes (spec 10.7): each window as a label, "N: " first when its
 * toggle ends in a digit, read bottom to top on the left stripe and top to bottom on the right, on {@code surface.stripe},
 * an open window's label on {@code surface.control.selected}; the top group's labels from {@code RAIL_GAP} below the top,
 * then the second group's, then on the left the plugins' rail actions; a 1 px rule along the edge toward the workspace.
 * The stripe is as thick as a label's line and its two rail edges, the line centred across it; {@code window.rail_width}
 * does not apply (the user's direction of 2026-10-18). A badge ends its label with a count pill. A click toggles the window and a right-click opens its {@link ToolWindowMenu}.
 * During a dock drag both groups show (an empty group as the dragged label's drop space), the dragged label is lifted
 * out and its length stays at the end of its group, and a gap as long as it opens where it would drop. Labels past the
 * stripe's end are cut off by it. EDT only.
 */
final class LabelledStripe extends DockStripe {
    /** Where the last layout put everything; during a drag, also each group's drop bounds and the gap. */
    private static final class Placement {
        final Map<Component, Rectangle> bounds = new IdentityHashMap<>();
        final EnumMap<Slot.Group, List<Rectangle>> buttons = new EnumMap<>(Slot.Group.class);
        final EnumMap<Slot.Group, Rectangle> groups = new EnumMap<>(Slot.Group.class);
        Rectangle gap;
    }

    /** A side stripe's groups; the bottom slots' windows show on the bottom strip (Rulings 25). */
    private static final List<Slot.Group> GROUPS = List.of(Slot.Group.TOP, Slot.Group.SECOND);

    private final Side side;
    private final StripeLabels labels;
    private final EnumMap<Slot.Group, List<StripeLabels.Label>> groups = new EnumMap<>(Slot.Group.class);
    private final Map<String, StripeLabels.Label> byId = new LinkedHashMap<>();
    private final List<StripeLabels.Label> actions = new ArrayList<>();
    private boolean dragging;
    private String lifted;
    private int liftedLength;
    private Slot.Group gapGroup;
    private int gapIndex;
    private Rectangle gap;

    LabelledStripe(Side side, Kit kit) {
        super(null);
        if (side == Side.BOTTOM) throw new IllegalArgumentException("A labelled stripe stands on the left or the right");
        this.side = side;
        this.labels = new StripeLabels(kit);
        setOpaque(true);
        getAccessibleContext().setAccessibleName(side == Side.LEFT ? "Left tool windows" : "Right tool windows");
        render(List.of(), List.of());
    }

    @Override Side side() { return side; }

    @Override void render(List<ToolStripe.Button> toolWindows, List<Action> contributed) {
        dragging = false; lifted = null; gapGroup = null; gap = null;
        removeAll();
        byId.clear();
        actions.clear();
        for (Slot.Group group : GROUPS) groups.put(group, new ArrayList<>());
        for (ToolStripe.Button window : toolWindows) {
            if (window.slot().side() != side) continue;
            var label = new StripeLabels.Label(window, this::paintLabel);
            label.setSelected(window.selected());
            label.addActionListener(event -> onToggle.accept(window.id()));
            label.setComponentPopupMenu(ToolWindowMenu.create(window.slot(), window.selected(),
                slot -> onMove.accept(window.id(), slot), () -> onHide.accept(window.id())));
            describe(label);
            groups.get(window.slot().group()).add(label);
            byId.put(window.id(), label);
            dragSource.accept(label, window.id());
        }
        for (Action action : contributed) {
            var label = new StripeLabels.Label(action, this::paintLabel);
            describe(label);
            actions.add(label);
        }
        for (Slot.Group group : GROUPS) groups.get(group).forEach(this::add);
        actions.forEach(this::add);
        revalidate();
        repaint();
    }

    /** The label's tooltip and accessible name and description: its text, its title, and its badge's words. */
    static void describe(StripeLabels.Label label) {
        String text = label.text();
        Optional<PanelBadge> badge = label.badge();
        label.setToolTipText(badge.map(mark -> text + " (" + mark.description() + ")").orElse(text));
        label.getAccessibleContext().setAccessibleName(label.window == null ? text : label.window.title());
        label.getAccessibleContext().setAccessibleDescription(badge.map(PanelBadge::description).orElse(null));
    }

    @Override void updateBadge(String id, Optional<PanelBadge> badge) {
        StripeLabels.Label label = byId.get(id);
        if (label == null) return;
        var window = label.window;
        label.window = new ToolStripe.Button(window.id(), window.title(), window.icon(), window.slot(), window.selected(), badge, window.number());
        describe(label);
        // A pill changes the label's length: lay out again; a drag's lifted label stays lifted.
        revalidate();
        doLayout();
        repaint();
    }

    @Override void refreshActions() {
        actions.forEach(LabelledStripe::describe);
        revalidate();
        doLayout();
        repaint();
    }

    @Override boolean empty() { return byId.isEmpty() && actions.isEmpty(); }

    @Override boolean emptyWithout(String id) { return actions.isEmpty() && byId.keySet().stream().allMatch(id::equals); }

    @Override List<AbstractButton> buttons() {
        var all = new ArrayList<AbstractButton>();
        for (Slot.Group group : GROUPS) all.addAll(groups.get(group));
        all.addAll(actions);
        return List.copyOf(all);
    }

    /** A labelled stripe fits its labels: {@code window.rail_width} does not apply. */
    @Override void setRailWidth(int width) { }

    @Override void beginDrag(String id, String label) {
        dragging = true;
        lifted = id;
        StripeLabels.Label own = byId.get(id);
        liftedLength = own != null ? labels.length(own.text(), own.badge()) : labels.length(label, Optional.empty());
        gapGroup = null;
        doLayout();
        repaint();
    }

    @Override void showGap(Slot slot, int index) {
        if (!dragging) return;
        Slot.Group group = slot != null && slot.side() == side ? slot.group() : null;
        if (group == gapGroup && (group == null || index == gapIndex)) return;
        gapGroup = group;
        gapIndex = index;
        doLayout();
        repaint();
    }

    @Override void endDrag() {
        if (!dragging) return;
        dragging = false; lifted = null; gapGroup = null; gap = null;
        revalidate();
        doLayout();
        repaint();
    }

    @Override boolean dragging() { return dragging; }

    @Override Optional<Rectangle> gapBounds() { return Optional.ofNullable(gap).map(Rectangle::new); }

    @Override List<DockDropResolver.Group> dropGroups() {
        Placement placement = place(false);
        var result = new ArrayList<DockDropResolver.Group>();
        for (Slot.Group group : GROUPS)
            result.add(new DockDropResolver.Group(Slot.on(side, group), placement.groups.get(group), placement.buttons.get(group)));
        return result;
    }

    @Override void refreshTheme() { repaint(); }

    @Override public Dimension getMinimumSize() {
        return new Dimension(labels.thickness(), labels.styles().metric(DesignMetrics.Role.RAIL_MIN_HEIGHT));
    }

    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        Placement placement = place(true);
        for (Component child : getComponents()) child.setBounds(placement.bounds.getOrDefault(child, new Rectangle()));
        gap = placement.gap;
    }

    /** The two groups down from {@code RAIL_GAP}, then the actions; the drop groups meet after the top group. */
    private Placement place(boolean withGap) {
        var placement = new Placement();
        var styles = labels.styles();
        int rule = styles.metric(DesignMetrics.Role.DOCK_RULE);
        int across = Math.max(0, getWidth() - rule), x = side == Side.LEFT ? 0 : rule;
        int between = styles.metric(DesignMetrics.Role.STRIPE_LABEL_GAP);
        int y = column(Slot.Group.TOP, styles.metric(DesignMetrics.Role.RAIL_GAP), x, across, between, withGap, placement);
        int split = y;
        y = column(Slot.Group.SECOND, y, x, across, between, withGap, placement);
        if (!actions.isEmpty()) {
            y += styles.metric(DesignMetrics.Role.RAIL_SECTION_GAP);
            for (StripeLabels.Label action : actions) {
                int length = labels.length(action.text(), Optional.empty());
                placement.bounds.put(action, new Rectangle(x, y, across, length));
                y += length + between;
            }
        }
        placement.groups.put(Slot.Group.TOP, new Rectangle(0, 0, getWidth(), split));
        placement.groups.put(Slot.Group.SECOND, new Rectangle(0, split, getWidth(), Math.max(0, getHeight() - split)));
        return placement;
    }

    /** The group's shown labels, the lifted one left out. */
    private List<StripeLabels.Label> shown(Slot.Group group) {
        return groups.get(group).stream().filter(label -> !dragging || !label.id().equals(lifted)).toList();
    }

    /** True while the dragged window's label belongs to this group. */
    private boolean liftedIn(Slot.Group group) {
        return dragging && groups.get(group).stream().anyMatch(label -> label.id().equals(lifted));
    }

    /** Lays a group out downward from y and returns where the next label starts. */
    private int column(Slot.Group group, int y, int x, int across, int between, boolean withGap, Placement placement) {
        List<StripeLabels.Label> shown = shown(group);
        boolean gapHere = withGap && dragging && gapGroup == group;
        int gapAt = Math.clamp(gapIndex, 0, shown.size());
        var rects = new ArrayList<Rectangle>();
        for (int i = 0; i <= shown.size(); i++) {
            if (gapHere && i == gapAt) {
                placement.gap = new Rectangle(x, y, across, liftedLength);
                y += liftedLength + between;
            }
            if (i == shown.size()) break;
            var label = shown.get(i);
            var at = new Rectangle(x, y, across, labels.length(label.text(), label.badge()));
            placement.bounds.put(label, at);
            rects.add(at);
            y += at.height + between;
        }
        // The lifted label's length stays at the end of its group, and an empty group shows one label's drop space; a
        // gap in the group takes that place.
        if (dragging && !gapHere && (shown.isEmpty() || liftedIn(group))) y += liftedLength + between;
        placement.buttons.put(group, rects);
        return y;
    }

    @Override protected void paintComponent(Graphics graphics) {
        GeneratedTheme theme = ThemeZone.theme(this);
        var painter = labels.painter();
        graphics.setColor(painter.stripeSurface(theme));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        int rule = labels.styles().metric(DesignMetrics.Role.DOCK_RULE);
        graphics.setColor(painter.dockRule(theme));
        graphics.fillRect(side == Side.LEFT ? getWidth() - rule : 0, 0, rule, getHeight());
    }

    /** A label's tile, then its text and pill turned to read bottom to top (left) or top to bottom (right). */
    private void paintLabel(StripeLabels.Label label, Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            GeneratedTheme theme = ThemeZone.theme(this);
            boolean open = label.isSelected();
            boolean hover = label.getModel().isRollover();
            Color tile = labels.painter().stripeTile(theme, open, hover);
            if (tile != null) {
                g.setColor(tile);
                g.fillRect(0, 0, label.getWidth(), label.getHeight());
            }
            if (side == Side.LEFT) { g.translate(0, label.getHeight()); g.rotate(-Math.PI / 2); }
            else { g.translate(label.getWidth(), 0); g.rotate(Math.PI / 2); }
            labels.paint(g, label.text(), label.badge(), label.getWidth(), true, theme, open, hover);
        } finally { g.dispose(); }
    }
}
