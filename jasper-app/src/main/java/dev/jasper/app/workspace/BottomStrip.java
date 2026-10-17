package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
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
 * The classic look's bottom strip (spec 10.7): the Bottom Left windows' labels rightward from the side stripes' fill (their
 * thickness less their rule, where the mocks start them), each followed by a 1 px {@code border.divider}, and the Bottom
 * Right windows' labels ending that far from the right, each preceded by one; a 1 px rule along its top; 24 px above its
 * rule, as the mocks draw it, whatever {@code window.rail_width}. Labels read across, an open window's on its tile, a badge
 * as a count pill. Its two groups meet midway between them and lie across, for the drag. It shows no rail actions. EDT
 * only.
 */
final class BottomStrip extends DockStripe {
    /** Where the last layout put everything: the labels, the dividers, each group's drop bounds and the gap. */
    private static final class Placement {
        final Map<Component, Rectangle> bounds = new IdentityHashMap<>();
        final EnumMap<Slot, List<Rectangle>> buttons = new EnumMap<>(Slot.class);
        final EnumMap<Slot, Rectangle> groups = new EnumMap<>(Slot.class);
        final List<Integer> dividers = new ArrayList<>();
        Rectangle gap;
    }

    /** One place along the strip: a label, the gap, or a dragged label's reserved space (neither label nor gap). */
    private record Item(StripeLabels.Label label, int length, boolean gap) { }

    private static final List<Slot> SLOTS = List.of(Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT);

    private final StripeLabels labels;
    private final EnumMap<Slot, List<StripeLabels.Label>> groups = new EnumMap<>(Slot.class);
    private final Map<String, StripeLabels.Label> byId = new LinkedHashMap<>();
    private int railWidth = ConfigSnapshot.DEFAULT_RAIL_WIDTH;
    private boolean dragging;
    private String lifted;
    private int liftedLength;
    private Slot gapSlot;
    private int gapIndex;
    private Rectangle gap;
    private List<Integer> dividers = List.of();

    BottomStrip(Kit kit) {
        super(null);
        this.labels = new StripeLabels(kit);
        setOpaque(true);
        getAccessibleContext().setAccessibleName("Bottom tool windows");
        render(List.of(), List.of());
    }

    @Override Side side() { return Side.BOTTOM; }

    @Override void render(List<ToolStripe.Button> toolWindows, List<Action> contributed) {
        dragging = false; lifted = null; gapSlot = null; gap = null;
        removeAll();
        byId.clear();
        for (Slot slot : SLOTS) groups.put(slot, new ArrayList<>());
        for (ToolStripe.Button window : toolWindows) {
            if (window.slot().side() != Side.BOTTOM) continue;
            var label = new StripeLabels.Label(window, this::paintLabel);
            label.setSelected(window.selected());
            label.addActionListener(event -> onToggle.accept(window.id()));
            label.setComponentPopupMenu(ToolWindowMenu.create(window.slot(), window.selected(),
                slot -> onMove.accept(window.id(), slot), () -> onHide.accept(window.id())));
            LabelledStripe.describe(label);
            groups.get(window.slot()).add(label);
            byId.put(window.id(), label);
            dragSource.accept(label, window.id());
        }
        for (Slot slot : SLOTS) groups.get(slot).forEach(this::add);
        revalidate();
        repaint();
    }

    @Override void updateBadge(String id, Optional<PanelBadge> badge) {
        StripeLabels.Label label = byId.get(id);
        if (label == null) return;
        var window = label.window;
        label.window = new ToolStripe.Button(window.id(), window.title(), window.icon(), window.slot(), window.selected(), badge, window.number());
        LabelledStripe.describe(label);
        revalidate();
        doLayout();
        repaint();
    }

    /** The strip shows no actions. */
    @Override void refreshActions() { }

    @Override boolean empty() { return byId.isEmpty(); }

    @Override boolean emptyWithout(String id) { return byId.keySet().stream().allMatch(id::equals); }

    @Override List<AbstractButton> buttons() {
        var all = new ArrayList<AbstractButton>();
        for (Slot slot : SLOTS) all.addAll(groups.get(slot));
        return List.copyOf(all);
    }

    @Override void setRailWidth(int width) {
        if (railWidth == width) return;
        railWidth = width;
        revalidate();
        repaint();
    }

    @Override void beginDrag(String id, String label) {
        dragging = true;
        lifted = id;
        StripeLabels.Label own = byId.get(id);
        liftedLength = own != null ? labels.length(own.text(), own.badge()) : labels.length(label, Optional.empty());
        gapSlot = null;
        doLayout();
        repaint();
    }

    @Override void showGap(Slot slot, int index) {
        if (!dragging) return;
        Slot target = slot != null && slot.side() == Side.BOTTOM ? slot : null;
        if (target == gapSlot && (target == null || index == gapIndex)) return;
        gapSlot = target;
        gapIndex = index;
        doLayout();
        repaint();
    }

    @Override void endDrag() {
        if (!dragging) return;
        dragging = false; lifted = null; gapSlot = null; gap = null;
        revalidate();
        doLayout();
        repaint();
    }

    @Override boolean dragging() { return dragging; }

    @Override Optional<Rectangle> gapBounds() { return Optional.ofNullable(gap).map(Rectangle::new); }

    @Override List<DockDropResolver.Group> dropGroups() {
        Placement placement = place(false);
        var result = new ArrayList<DockDropResolver.Group>();
        for (Slot slot : SLOTS)
            result.add(new DockDropResolver.Group(slot, placement.groups.get(slot), placement.buttons.get(slot), DockDropResolver.Axis.HORIZONTAL));
        return result;
    }

    @Override void refreshTheme() { repaint(); }

    @Override public Dimension getMinimumSize() { return new Dimension(0, labels.stripThickness(this)); }

    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        Placement placement = place(true);
        for (Component child : getComponents()) child.setBounds(placement.bounds.getOrDefault(child, new Rectangle()));
        gap = placement.gap;
        dividers = List.copyOf(placement.dividers);
    }

    /** A group's places in order: its shown labels with the gap among them, or the lifted label's reserved space at the end. */
    private List<Item> items(Slot slot, boolean withGap) {
        List<StripeLabels.Label> shown = groups.get(slot).stream().filter(label -> !dragging || !label.id().equals(lifted)).toList();
        boolean gapHere = withGap && dragging && gapSlot == slot;
        int gapAt = Math.clamp(gapIndex, 0, shown.size());
        var items = new ArrayList<Item>();
        for (int i = 0; i <= shown.size(); i++) {
            if (gapHere && i == gapAt) items.add(new Item(null, liftedLength, true));
            if (i < shown.size()) items.add(new Item(shown.get(i), labels.length(shown.get(i).text(), shown.get(i).badge()), false));
        }
        boolean liftedHere = dragging && groups.get(slot).stream().anyMatch(label -> label.id().equals(lifted));
        if (dragging && !gapHere && (shown.isEmpty() || liftedHere)) items.add(new Item(null, liftedLength, false));
        return items;
    }

    /** Bottom Left from the lead rightward, Bottom Right ending at the lead from the right; the groups meet midway. */
    private Placement place(boolean withGap) {
        var placement = new Placement();
        int rule = labels.styles().metric(DesignMetrics.Role.DOCK_RULE), across = Math.max(0, getHeight() - rule);
        int lead = labels.thickness(railWidth, this) - rule;
        int x = lead;
        var left = new ArrayList<Rectangle>();
        for (Item item : items(Slot.BOTTOM_LEFT, withGap)) {
            var at = new Rectangle(x, rule, item.length(), across);
            if (item.label() != null) { placement.bounds.put(item.label(), at); left.add(at); }
            if (item.gap()) placement.gap = at;
            if (item.label() != null || item.gap()) placement.dividers.add(x + item.length());
            x += item.length() + rule;
        }
        int leftEnd = x;
        var rightItems = items(Slot.BOTTOM_RIGHT, withGap);
        int total = rightItems.stream().mapToInt(item -> rule + item.length()).sum();
        int start = Math.max(leftEnd, getWidth() - lead - total);
        x = start;
        var right = new ArrayList<Rectangle>();
        for (Item item : rightItems) {
            if (item.label() != null || item.gap()) placement.dividers.add(x);
            x += rule;
            var at = new Rectangle(x, rule, item.length(), across);
            if (item.label() != null) { placement.bounds.put(item.label(), at); right.add(at); }
            if (item.gap()) placement.gap = at;
            x += item.length();
        }
        int boundary = Math.clamp((leftEnd + start) / 2, 0, Math.max(0, getWidth()));
        placement.buttons.put(Slot.BOTTOM_LEFT, left);
        placement.buttons.put(Slot.BOTTOM_RIGHT, right);
        placement.groups.put(Slot.BOTTOM_LEFT, new Rectangle(0, 0, boundary, getHeight()));
        placement.groups.put(Slot.BOTTOM_RIGHT, new Rectangle(boundary, 0, Math.max(0, getWidth() - boundary), getHeight()));
        return placement;
    }

    @Override protected void paintComponent(Graphics graphics) {
        GeneratedTheme theme = ThemeZone.theme(this);
        var painter = labels.painter();
        int rule = labels.styles().metric(DesignMetrics.Role.DOCK_RULE);
        graphics.setColor(painter.stripeSurface(theme));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        graphics.setColor(painter.dockRule(theme));
        graphics.fillRect(0, 0, getWidth(), rule);
        graphics.setColor(painter.stripDivider(theme));
        for (int x : dividers) graphics.fillRect(x, rule, rule, Math.max(0, getHeight() - rule));
    }

    /** A label's tile, then its text and pill, reading across. */
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
            labels.paint(g, label.text(), label.badge(), label.getHeight(), theme, open, hover);
        } finally { g.dispose(); }
    }
}
