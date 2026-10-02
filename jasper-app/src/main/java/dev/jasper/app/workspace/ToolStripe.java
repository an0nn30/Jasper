package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.Command;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

/**
 * One of the window's two tool window stripes (IntelliJ's tool window bars). Top to bottom: the top group; a separator
 * when the second group has icons; the second group; on the left stripe, the plugins' rail actions behind their own
 * separator; flexible space; and the foot group, pinned to the bottom. A button is selected while its window shows;
 * a click toggles the window and a right-click opens its {@link ToolWindowMenu}. The stripe owns no state: the window
 * renders it again after every change. EDT only.
 */
final class ToolStripe extends JPanel {
    /** One tool window's button. */
    record Button(String id, String title, Icon icon, Slot slot, boolean selected) { }

    Consumer<String> onToggle = id -> { };
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    Consumer<String> onHide = id -> { };
    private final Side side;
    private final List<AbstractButton> buttons = new ArrayList<>();
    private final List<Component> upper = new ArrayList<>();
    private final List<Component> foot = new ArrayList<>();
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

    private static void style(AbstractButton button, Icon icon, String name) {
        button.setHideActionText(true);
        button.setText(null);
        button.setIcon(icon);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.putClientProperty("FlatLaf.style", "arc: 10");
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
    }

    /**
     * Shows this stripe's windows by group, in the order given, and the actions after the second group. Windows whose
     * slot belongs to the other stripe are ignored.
     */
    void render(List<Button> windows, List<Action> actions) {
        removeAll();
        buttons.clear(); upper.clear(); foot.clear();
        List<Button> mine = windows.stream().filter(window -> window.slot().stripe() == side).toList();
        for (Button window : group(mine, Slot.Group.TOP)) upper.add(toggle(window));
        List<Button> second = group(mine, Slot.Group.SECOND);
        if (!second.isEmpty()) upper.add(new JSeparator());
        for (Button window : second) upper.add(toggle(window));
        if (!actions.isEmpty() && !upper.isEmpty()) upper.add(new JSeparator());
        for (Action action : actions) upper.add(action(action));
        for (Button window : group(mine, Slot.Group.FOOT)) foot.add(toggle(window));
        upper.forEach(this::add);
        foot.forEach(this::add);
        revalidate(); repaint();
    }

    private static List<Button> group(List<Button> windows, Slot.Group group) {
        return windows.stream().filter(window -> window.slot().group() == group).toList();
    }

    private AbstractButton toggle(Button window) {
        var button = new JToggleButton();
        style(button, window.icon(), window.title());
        button.setSelected(window.selected());
        button.addActionListener(event -> onToggle.accept(window.id()));
        button.setComponentPopupMenu(ToolWindowMenu.create(window.slot(), window.selected(),
            slot -> onMove.accept(window.id(), slot), () -> onHide.accept(window.id())));
        buttons.add(button);
        return button;
    }

    private AbstractButton action(Action action) {
        var button = new JButton(action);
        Icon icon = action.getValue(Command.ICON) instanceof Icon contributed ? contributed : AppIcons.icon("command");
        style(button, icon, (String) action.getValue(Action.NAME));
        buttons.add(button);
        return button;
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

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(railWidth), UIScale.scale(80)); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        // Square buttons 3 px in from each side and 4 px apart; separators inset 7 px; the foot ends 4 px from the bottom.
        int x = UIScale.scale(3), square = UIScale.scale(railWidth - 6), gap = UIScale.scale(4), y = UIScale.scale(4);
        for (Component child : upper) y = place(child, x, y, square, gap);
        int footHeight = foot.isEmpty() ? 0 : foot.size() * square + (foot.size() - 1) * gap;
        int footY = Math.max(y, getHeight() - UIScale.scale(4) - footHeight);
        for (Component child : foot) footY = place(child, x, footY, square, gap);
    }

    /** Places one child at y and returns where the next one starts. */
    private int place(Component child, int x, int y, int square, int gap) {
        if (child instanceof JSeparator) {
            child.setBounds(UIScale.scale(7), y + UIScale.scale(3), UIScale.scale(railWidth - 14), UIScale.scale(1));
            return y + UIScale.scale(8);
        }
        child.setBounds(x, y, square, square);
        return y + square + gap;
    }

    void refreshTheme() {
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        repaint();
    }
}
