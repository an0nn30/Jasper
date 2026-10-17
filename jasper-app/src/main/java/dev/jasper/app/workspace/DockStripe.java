package dev.jasper.app.workspace;

import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * One edge of a window's tool window stripes, as the window, its contributions and a dock drag use it: an icon rail
 * ({@link ToolStripe}), a labelled side stripe or the bottom strip (spec 10.7). Each shows the windows whose slot it
 * owns, toggles a window on click, offers its menu on right-click, tells a drag where its groups are and opens a gap
 * where a dragged window would drop. EDT only.
 */
abstract class DockStripe extends JPanel {
    /** Toggles the window with this id. */
    Consumer<String> onToggle = id -> { };
    /** Moves the window to a slot, from its menu. */
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    /** Hides the window, from its menu. */
    Consumer<String> onHide = id -> { };
    /** Hears of each window button a render creates, so that a drag can start from it. */
    BiConsumer<JComponent, String> dragSource = (component, id) -> { };

    DockStripe(LayoutManager layout) { super(layout); }

    /** The window edge the stripe stands on: left, right or, for the bottom strip, bottom. */
    abstract Side side();

    /** Shows the windows whose slot this stripe owns, in the order given, and the rail actions where it shows them. */
    abstract void render(List<ToolStripe.Button> toolWindows, List<Action> contributed);

    /** Changes a window's badge in place; a window this stripe does not show is ignored. */
    abstract void updateBadge(String id, Optional<PanelBadge> badge);

    /** Renames each action's button after its action's title. */
    abstract void refreshActions();

    /** True when the stripe shows neither tool windows nor actions. */
    abstract boolean empty();

    /** True when the window's button, if it has one here, is all this stripe shows. */
    abstract boolean emptyWithout(String id);

    /** Every button, in the stripe's order. */
    abstract List<AbstractButton> buttons();

    /** Sets {@code window.rail_width} in logical pixels; the caller validates the range. */
    abstract void setRailWidth(int width);

    /** Shows every group for a drag of {@code id}, whose stripe label is {@code label}, and lifts its button out. */
    abstract void beginDrag(String id, String label);

    /** Opens the gap at {@code index} in the slot's group when this stripe shows the slot, and closes it otherwise. */
    abstract void showGap(Slot slot, int index);

    /** Back to the normal stripe after a drag. */
    abstract void endDrag();

    abstract boolean dragging();

    /** The gap the last layout opened, in this stripe's coordinates. */
    abstract Optional<Rectangle> gapBounds();

    /** The stripe's groups as a drag resolves them, in this stripe's coordinates. */
    abstract List<DockDropResolver.Group> dropGroups();

    /** Repaints in the installed theme's colours. */
    abstract void refreshTheme();

    /** How deep the stripe stands from its window edge: a side stripe's width, the bottom strip's height. */
    int thickness() { return side() == Side.BOTTOM ? getPreferredSize().height : getPreferredSize().width; }
}
