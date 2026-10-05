package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeControlStyles;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.Command;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

/**
 * One of the window's two tool window stripes (IntelliJ's tool window bars). Top to bottom: the top group; a separator
 * when the second group has icons; the second group; on the left stripe, the plugins' rail actions behind their own
 * separator; flexible space; and the foot group, pinned to the bottom. A button is selected while its window shows;
 * a click toggles the window and a right-click opens its {@link ToolWindowMenu}. A button with an unread badge shows a
 * {@link BadgedIcon} and names the badge in its tooltip and accessible description; {@link #updateBadge} changes a
 * badge on the existing button, so a badge change never rebuilds the stripe or ends a drag. During a dock drag the
 * stripe shows all three groups with their separators (an empty group as one button's drop space), lifts the dragged
 * window's button out, and opens a gap where the window would drop. The lifted button's step stays empty at the end of
 * its group, so no later group slides under the cursor, and a gap in that group uses it. The stripe keeps only what
 * its last render built, and the window renders it again after every dock change; a badge change re-decorates the
 * existing button in place via {@link #updateBadge}, and an action's title change renames its button in place via
 * {@link #refreshActions} (the button follows the action's enabled state itself). EDT only.
 */
final class ToolStripe extends JPanel {
    /** One tool window's button, with its unread badge if it has one. */
    record Button(String id, String title, Icon icon, Slot slot, boolean selected, Optional<PanelBadge> badge) {
        Button(String id, String title, Icon icon, Slot slot, boolean selected) {
            this(id, title, icon, slot, selected, Optional.empty());
        }
    }

    /** Where the last layout put everything; during a drag, also each group's drop bounds and the gap. */
    private static final class Placement {
        final Map<Component, Rectangle> bounds = new IdentityHashMap<>();
        final EnumMap<Slot.Group, List<Rectangle>> buttons = new EnumMap<>(Slot.Group.class);
        final EnumMap<Slot.Group, Rectangle> groups = new EnumMap<>(Slot.Group.class);
        Rectangle gap;
    }

    Consumer<String> onToggle = id -> { };
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    Consumer<String> onHide = id -> { };
    /** Hears of each window button a render creates, so that a drag can start from it. */
    BiConsumer<JComponent, String> dragSource = (component, id) -> { };
    private final Side side;
    private final List<AbstractButton> buttons = new ArrayList<>();
    private final EnumMap<Slot.Group, List<AbstractButton>> groups = new EnumMap<>(Slot.Group.class);
    private final Map<AbstractButton, String> ids = new IdentityHashMap<>();
    /** What each window button shows now, by window id: a badge change replaces the record without a render. */
    private final Map<String, Button> windows = new HashMap<>();
    private final Map<String, AbstractButton> byId = new HashMap<>();
    private final List<AbstractButton> actions = new ArrayList<>();
    /** The separators the normal layout has, or null; a drag adds the missing ones for its duration. */
    private JSeparator secondSeparator, actionsSeparator;
    private final List<JSeparator> dragSeparators = new ArrayList<>();
    private boolean dragging;
    private String lifted;
    private Slot.Group gapGroup;
    private int gapIndex;
    private Rectangle gap;
    /** The stripe's width in logical pixels; buttons, separators and spacing follow it. */
    private int railWidth = ConfigSnapshot.DEFAULT_RAIL_WIDTH;

    ToolStripe(Side side) {
        super(null);
        if (side == Side.BOTTOM) throw new IllegalArgumentException("A stripe stands on the left or the right");
        this.side = side;
        getAccessibleContext().setAccessibleName(side == Side.LEFT ? "Left tool windows" : "Right tool windows");
        render(List.of(), List.of());
        refreshTheme();
    }

    Side side() { return side; }

    private static void style(AbstractButton button) {
        button.setHideActionText(true);
        button.setText(null);
        NativeControlStyles.button(button, () -> ThemeZone.theme(button), DesignMetrics.Role.RAIL_BUTTON_ARC);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
    }

    /**
     * Shows this stripe's windows by group, in the order given, and the actions after the second group. Windows whose
     * slot belongs to the other stripe are ignored. A render ends any drag view.
     */
    void render(List<Button> toolWindows, List<Action> contributed) {
        dragging = false; lifted = null; gapGroup = null; gap = null;
        removeAll();
        buttons.clear(); ids.clear(); windows.clear(); byId.clear(); actions.clear(); dragSeparators.clear();
        for (Slot.Group group : Slot.Group.values()) groups.put(group, new ArrayList<>());
        secondSeparator = null; actionsSeparator = null;
        List<Button> mine = toolWindows.stream().filter(window -> window.slot().stripe() == side).toList();
        for (Button window : group(mine, Slot.Group.TOP)) groups.get(Slot.Group.TOP).add(toggle(window));
        List<Button> second = group(mine, Slot.Group.SECOND);
        if (!second.isEmpty()) secondSeparator = new JSeparator();
        for (Button window : second) groups.get(Slot.Group.SECOND).add(toggle(window));
        boolean windowsAbove = !groups.get(Slot.Group.TOP).isEmpty() || !groups.get(Slot.Group.SECOND).isEmpty();
        if (!contributed.isEmpty() && windowsAbove) actionsSeparator = new JSeparator();
        for (Action action : contributed) actions.add(action(action));
        for (Button window : group(mine, Slot.Group.FOOT)) groups.get(Slot.Group.FOOT).add(toggle(window));
        groups.get(Slot.Group.TOP).forEach(this::add);
        if (secondSeparator != null) add(secondSeparator);
        groups.get(Slot.Group.SECOND).forEach(this::add);
        if (actionsSeparator != null) add(actionsSeparator);
        actions.forEach(this::add);
        groups.get(Slot.Group.FOOT).forEach(this::add);
        revalidate(); repaint();
    }

    private static List<Button> group(List<Button> windows, Slot.Group group) {
        return windows.stream().filter(window -> window.slot().group() == group).toList();
    }

    private AbstractButton toggle(Button window) {
        var button = new JToggleButton();
        style(button);
        decorate(button, window);
        button.setSelected(window.selected());
        button.addActionListener(event -> onToggle.accept(window.id()));
        button.setComponentPopupMenu(ToolWindowMenu.create(window.slot(), window.selected(),
            slot -> onMove.accept(window.id(), slot), () -> onHide.accept(window.id())));
        buttons.add(button);
        ids.put(button, window.id());
        windows.put(window.id(), window);
        byId.put(window.id(), button);
        dragSource.accept(button, window.id());
        return button;
    }

    /**
     * Shows the window's icon, with its badge's dot if it has one, and its tooltip and accessible name and description.
     * It also serves a button that stays while its badge changes, so it clears what a gone badge set.
     */
    private void decorate(AbstractButton button, Button window) {
        Optional<PanelBadge> badge = window.badge();
        button.setIcon(badge.<Icon>map(mark -> new BadgedIcon(window.icon(), mark.tone())).orElse(window.icon()));
        button.setToolTipText(badge.map(mark -> window.title() + " (" + mark.description() + ")").orElse(window.title()));
        button.getAccessibleContext().setAccessibleName(window.title());
        button.getAccessibleContext().setAccessibleDescription(badge.map(PanelBadge::description).orElse(null));
    }

    /**
     * Changes the badge of the window's button in place and repaints it; a window this stripe does not show is ignored.
     * It rebuilds nothing, asks for no layout and leaves a drag as it is: the dot is part of the icon, whose size is the
     * base icon's, so the button keeps its place and a lifted button stays lifted.
     */
    void updateBadge(String id, Optional<PanelBadge> badge) {
        AbstractButton button = byId.get(id);
        Button window = windows.get(id);
        if (button == null || window == null) return;
        Button updated = new Button(window.id(), window.title(), window.icon(), window.slot(), window.selected(), badge);
        windows.put(id, updated);
        decorate(button, updated);
        button.repaint();
    }

    private AbstractButton action(Action action) {
        var button = new JButton(action);
        Icon icon = action.getValue(Command.ICON) instanceof Icon contributed ? contributed : AppIcons.icon("command");
        style(button);
        button.setIcon(icon);
        name(button, action);
        buttons.add(button);
        return button;
    }

    /** Names the action's button after the action's current title, in its tooltip and its accessible name. */
    private static void name(AbstractButton button, Action action) {
        String name = (String) action.getValue(Action.NAME);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
    }

    /**
     * Gives each action button the action's current title and repaints it; the button already follows the action's
     * enabled state. It rebuilds nothing, asks for no layout and leaves a drag as it is: which actions show and where
     * does not change with a title, so a lifted button stays lifted. An action that is added or removed needs a
     * {@link #render} instead.
     */
    void refreshActions() {
        for (AbstractButton button : actions) {
            name(button, button.getAction());
            button.repaint();
        }
    }

    /** True when there are neither tool windows nor actions on this stripe. */
    boolean empty() { return buttons.isEmpty(); }

    /** Every button, top to bottom: the groups' toggles and the actions. */
    List<AbstractButton> buttons() { return List.copyOf(buttons); }

    /** Sets the width in logical pixels; the caller validates the range. */
    void setRailWidth(int width) {
        if (railWidth == width) return;
        railWidth = width;
        revalidate(); repaint();
    }

    /**
     * Shows all three groups for a drag and lifts the dragged window's button out of its place.
     *
     * @param id the dragged window; it may be on the other stripe
     */
    void beginDrag(String id) {
        dragging = true;
        lifted = id;
        gapGroup = null;
        if (secondSeparator == null) secondSeparator = dragSeparator();
        if (actionsSeparator == null && !actions.isEmpty()) actionsSeparator = dragSeparator();
        doLayout(); repaint();
    }

    private JSeparator dragSeparator() {
        var separator = new JSeparator();
        dragSeparators.add(separator);
        add(separator);
        return separator;
    }

    /** Opens the gap at index in the slot's group when the slot is on this stripe, and closes it otherwise. */
    void showGap(Slot slot, int index) {
        if (!dragging) return;
        Slot.Group group = slot != null && slot.stripe() == side ? slot.group() : null;
        if (group == gapGroup && (group == null || index == gapIndex)) return;
        gapGroup = group;
        gapIndex = index;
        doLayout(); repaint();
    }

    /** Back to the normal stripe after a drag, whether it dropped or not. */
    void endDrag() {
        if (!dragging) return;
        dragging = false; lifted = null; gapGroup = null; gap = null;
        for (JSeparator separator : dragSeparators) {
            remove(separator);
            if (separator == secondSeparator) secondSeparator = null;
            if (separator == actionsSeparator) actionsSeparator = null;
        }
        dragSeparators.clear();
        revalidate(); doLayout(); repaint();
    }

    boolean dragging() { return dragging; }

    /** The gap the last layout opened, in this stripe's coordinates. */
    Optional<Rectangle> gapBounds() { return Optional.ofNullable(gap).map(Rectangle::new); }

    /**
     * The three groups as a drag resolves them, in this stripe's coordinates: they split the stripe from top to bottom,
     * and their buttons are where the drag layout puts them before any gap opens, without the lifted one.
     */
    List<DockDropResolver.Group> dropGroups() {
        Placement placement = place(false);
        List<DockDropResolver.Group> result = new ArrayList<>();
        for (Slot.Group group : Slot.Group.values())
            result.add(new DockDropResolver.Group(Slot.on(side, group), placement.groups.get(group), placement.buttons.get(group)));
        return result;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(railWidth), NativeControlStyles.metric(DesignMetrics.Role.RAIL_MIN_HEIGHT)); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        Placement placement = place(true);
        for (Component child : getComponents()) child.setBounds(placement.bounds.getOrDefault(child, new Rectangle()));
        gap = placement.gap;
    }

    /**
     * Square buttons 3 px in from each side and 4 px apart; separators inset 7 px; the foot ends 4 px from the bottom.
     * During a drag every group shows, the lifted button's step stays empty at the end of its group, and an empty group
     * shows one step of drop space. With the gap, the gap takes the lifted button's step in its group and an empty
     * group's drop space, so neither grows; in any other group it adds one step.
     */
    private Placement place(boolean withGap) {
        var placement = new Placement();
        int x = NativeControlStyles.metric(DesignMetrics.Role.RAIL_EDGE), square = UIScale.scale(railWidth - 6), step = square + NativeControlStyles.metric(DesignMetrics.Role.RAIL_GAP);
        int y = column(Slot.Group.TOP, NativeControlStyles.metric(DesignMetrics.Role.RAIL_GAP), x, square, step, withGap, placement);
        int split = y;
        if (secondSeparator != null && (dragging || !groups.get(Slot.Group.SECOND).isEmpty())) {
            placement.bounds.put(secondSeparator, separator(y));
            split = y + NativeControlStyles.metric(DesignMetrics.Role.RAIL_GAP);
            y += NativeControlStyles.metric(DesignMetrics.Role.RAIL_SECTION_GAP);
        }
        y = column(Slot.Group.SECOND, y, x, square, step, withGap, placement);
        if (actionsSeparator != null) {
            placement.bounds.put(actionsSeparator, separator(y));
            y += NativeControlStyles.metric(DesignMetrics.Role.RAIL_SECTION_GAP);
        }
        for (AbstractButton action : actions) {
            placement.bounds.put(action, new Rectangle(x, y, square, square));
            y += step;
        }
        int count = footCount(withGap);
        int footHeight = count == 0 ? 0 : count * step - NativeControlStyles.metric(DesignMetrics.Role.RAIL_GAP);
        int footY = Math.max(y, getHeight() - NativeControlStyles.metric(DesignMetrics.Role.RAIL_GAP) - footHeight);
        column(Slot.Group.FOOT, footY, x, square, step, withGap, placement);
        int boundary = (y + footY) / 2;
        placement.groups.put(Slot.Group.TOP, new Rectangle(0, 0, getWidth(), split));
        placement.groups.put(Slot.Group.SECOND, new Rectangle(0, split, getWidth(), Math.max(0, boundary - split)));
        placement.groups.put(Slot.Group.FOOT, new Rectangle(0, boundary, getWidth(), Math.max(0, getHeight() - boundary)));
        return placement;
    }

    private Rectangle separator(int y) {
        return new Rectangle(NativeControlStyles.metric(DesignMetrics.Role.RAIL_SEPARATOR_INSET), y + NativeControlStyles.metric(DesignMetrics.Role.RAIL_EDGE), UIScale.scale(railWidth - 14), NativeControlStyles.metric(DesignMetrics.Role.HAIRLINE));
    }

    /** The group's shown buttons, the lifted one left out. */
    private List<AbstractButton> shown(Slot.Group group) {
        return groups.get(group).stream().filter(button -> !dragging || !ids.get(button).equals(lifted)).toList();
    }

    /** True while the dragged window's button belongs to this group of this stripe. */
    private boolean liftedIn(Slot.Group group) {
        return dragging && groups.get(group).stream().anyMatch(button -> ids.get(button).equals(lifted));
    }

    private int footCount(boolean withGap) {
        int shown = shown(Slot.Group.FOOT).size();
        boolean gapHere = withGap && dragging && gapGroup == Slot.Group.FOOT;
        return shown + (gapHere || dragging && (liftedIn(Slot.Group.FOOT) || shown == 0) ? 1 : 0);
    }

    /** Lays a group out downward from y and returns where the next item starts. */
    private int column(Slot.Group group, int y, int x, int square, int step, boolean withGap, Placement placement) {
        List<AbstractButton> shown = shown(group);
        boolean gapHere = withGap && dragging && gapGroup == group;
        int gapAt = Math.clamp(gapIndex, 0, shown.size());
        List<Rectangle> rects = new ArrayList<>();
        for (int i = 0; i <= shown.size(); i++) {
            if (gapHere && i == gapAt) {
                placement.gap = new Rectangle(x, y, square, square);
                y += step;
            }
            if (i == shown.size()) break;
            var at = new Rectangle(x, y, square, square);
            placement.bounds.put(shown.get(i), at);
            rects.add(at);
            y += step;
        }
        // The lifted button's step stays empty at the end of its group, and an empty group shows one button's drop space;
        // a gap in the group takes that step.
        if (dragging && !gapHere && (shown.isEmpty() || liftedIn(group))) y += step;
        placement.buttons.put(group, rects);
        return y;
    }

    void refreshTheme() {
        setBackground(ThemeZone.color(null, SemanticColor.SURFACE_APP));
        repaint();
    }
}
