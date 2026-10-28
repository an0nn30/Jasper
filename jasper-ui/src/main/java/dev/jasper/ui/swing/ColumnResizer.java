package dev.jasper.ui.swing;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Drags the boundaries between a header's column titles: within a few pixels of a boundary the pointer shows the
 * horizontal-resize cursor, a drag there moves width between the two neighbouring columns, a double-click fits a
 * column to its content, and the target reports the new widths once, when a drag ends (or is cut short) or a fit changes
 * a width. The zone is invisible apart from the cursor; a click inside it never sorts ({@link #inBoundary}).
 */
final class ColumnResizer extends MouseAdapter implements ContainerListener {
    /** A header whose boundaries can be dragged: a table's or a column tree's. */
    interface Target {
        /** The x of each boundary in header coordinates, one per adjacent pair of columns. */
        int[] edges();

        /** Moves boundary {@code boundary} by {@code dx} pixels, within both neighbours' minimums. */
        void drag(int boundary, int dx);

        /**
         * Fits the column boundary {@code boundary} sizes to its content: a table's column on its left, a column tree's
         * trailing column on its right. Returns whether any width changed.
         */
        boolean fit(int boundary);

        /** Reports the columns' widths after a drag or a fit. */
        void finish();
    }

    private static final String KEY = "jasper.columnResizer";
    private final ControlRegistry.Control owner;
    private final JComponent header;
    private final Target target;
    /** The boundary being dragged, or -1. */
    private int boundary = -1;
    /** Where the drag began, in header coordinates, and the dragged boundary's x then. */
    private int pressX, pressEdge;
    private boolean moved;
    /** A drag or a fit just ended: the click that follows its release never sorts. */
    private boolean resized;

    private ColumnResizer(ControlRegistry.Control owner, JComponent header, Target target) {
        this.owner = owner;
        this.header = header;
        this.target = target;
    }

    /** Lets {@code header}'s boundaries be dragged, over the header and every title it holds now or later. */
    static void install(ControlRegistry.Control owner, JComponent header, Target target) {
        var resizer = new ColumnResizer(owner, header, target);
        header.putClientProperty(KEY, resizer);
        header.addMouseListener(resizer);
        header.addMouseMotionListener(resizer);
        header.addContainerListener(resizer);
        for (var title : header.getComponents()) resizer.listen(title);
    }

    /** Whether {@code event} on a header title lies in a boundary's zone, or ends a resize: such a click never sorts. */
    static boolean inBoundary(JComponent title, MouseEvent event) {
        if (!(title.getParent() instanceof JComponent header && header.getClientProperty(KEY) instanceof ColumnResizer resizer))
            return false;
        return resizer.resized || resizer.zone(resizer.x(event)) >= 0;
    }

    private void listen(Component title) {
        title.addMouseListener(this);
        title.addMouseMotionListener(this);
    }

    @Override public void componentAdded(ContainerEvent event) { listen(event.getChild()); }

    @Override public void componentRemoved(ContainerEvent event) {
        event.getChild().removeMouseListener(this);
        event.getChild().removeMouseMotionListener(this);
    }

    private int x(MouseEvent event) {
        return SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), header).x;
    }

    /** The boundary whose zone holds header x {@code x}, the nearest when zones meet, or -1. */
    private int zone(int x) {
        int[] edges = target.edges();
        int reach = com.formdev.flatlaf.util.UIScale.scale(3), best = -1;
        for (int i = 0; i < edges.length; i++)
            if (Math.abs(x - edges[i]) <= reach && (best < 0 || Math.abs(x - edges[i]) < Math.abs(x - edges[best]))) best = i;
        return best;
    }

    private boolean live() {
        return owner.component.isEnabled();
    }

    @Override public void mouseMoved(MouseEvent event) {
        if (boundary >= 0) return;
        event.getComponent().setCursor(live() && zone(x(event)) >= 0 ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR) : null);
    }

    @Override public void mouseExited(MouseEvent event) {
        if (boundary < 0) event.getComponent().setCursor(null);
    }

    @Override public void mousePressed(MouseEvent event) {
        resized = false;
        cancel();
        if (!SwingUtilities.isLeftMouseButton(event) || !live()) return;
        int x = x(event), hit = zone(x);
        if (hit < 0) return;
        if (event.getClickCount() == 2) {
            if (target.fit(hit)) target.finish();
            resized = true;
            return;
        }
        boundary = hit;
        pressX = x;
        pressEdge = target.edges()[hit];
        moved = false;
    }

    @Override public void mouseDragged(MouseEvent event) {
        if (boundary < 0) return;
        int[] edges = target.edges();
        if (boundary >= edges.length) { cancel(); return; }
        int dx = pressEdge + x(event) - pressX - edges[boundary];
        if (dx == 0) return;
        target.drag(boundary, dx);
        moved = true;
    }

    /** Ends a drag without its release; widths it already moved are reported, as the header now shows them. */
    private void cancel() {
        if (boundary >= 0 && moved) target.finish();
        boundary = -1;
        moved = false;
    }

    @Override public void mouseReleased(MouseEvent event) {
        if (boundary < 0) return;
        boundary = -1;
        if (moved) target.finish();
        resized = true;
        mouseMoved(event);
    }
}
