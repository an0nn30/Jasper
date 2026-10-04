package dev.jasper.app.workspace;

import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * Puts plain content into a dock without a plugin, as the old {@code regions().show(...)} did, and aims drags at a
 * slot's workspace zone. Test only.
 */
final class DockTestSupport {
    private DockTestSupport() { }

    /** Shows content in a slot, with its side at size; the same slot again replaces the content. */
    static void show(ToolWindowArea area, Slot slot, JComponent content, int size) {
        String id = id(slot);
        DockState state = area.state();
        area.setContent(id, slot.displayName(), content);
        if (state.slot(id).isEmpty()) state.add(id, slot);
        state.resize(slot.side(), size);
        state.show(id);
        area.sync();
    }

    static void hide(ToolWindowArea area, Slot slot) {
        area.state().hide(id(slot));
        area.sync();
    }

    /** The same in a window's own area; a connected window's contributions ignore these ids. */
    static void show(WindowContent owner, Slot slot, JComponent content, int size) { show(owner.area(), slot, content, size); }

    static void hide(WindowContent owner, Slot slot) { hide(owner.area(), slot); }

    /** The id the helper gives a slot's content, such as {@code test.left-top}. */
    static String id(Slot slot) { return "test." + slot.name().toLowerCase(Locale.ROOT).replace('_', '-'); }

    /**
     * A point well inside the slot's drop zone of a workspace: near the edge the zone belongs to and clearly farther
     * from the other two, so it holds for any size or aspect ratio.
     */
    static Point zone(Rectangle workspace, Slot slot) {
        double[] share = switch (slot) {
            case LEFT_TOP -> new double[] {0.10, 0.20};
            case LEFT_BOTTOM -> new double[] {0.10, 0.80};
            case RIGHT_TOP -> new double[] {0.90, 0.20};
            case RIGHT_BOTTOM -> new double[] {0.90, 0.80};
            case BOTTOM_LEFT -> new double[] {0.25, 0.95};
            case BOTTOM_RIGHT -> new double[] {0.75, 0.95};
        };
        return new Point(workspace.x + (int) (workspace.width * share[0]), workspace.y + (int) (workspace.height * share[1]));
    }
}
