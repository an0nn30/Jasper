package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Headless tab drags (workspace tabs spec 12): windows in stand-in frames on a pretend screen, and the mouse events Swing
 * sends a drag, in that screen's coordinates. A stand-in frame is a parentless panel at a place on the pretend screen: a
 * root pane holding the window's content, under a title bar that is the panel's top inset, as a native frame's insets
 * hold its title bar. Screen conversions, the drag's window bounds, the frame's decorations and the overlay's layered
 * pane then work without a display.
 */
final class TabDragTestSupport {
    /** The stand-in title bar's height: the frame's top inset. */
    static final int TITLE = 28;

    private TabDragTestSupport() { }

    /** {@code window} in a stand-in frame at {@code x}, {@code y} on the pretend screen, {@code width} by {@code height}, laid out. */
    static JPanel frame(WindowContent window, int x, int y, int width, int height) {
        var root = new JRootPane();
        root.setContentPane(window);
        var frame = new JPanel(new BorderLayout());
        frame.setBorder(BorderFactory.createEmptyBorder(TITLE, 0, 0, 0));
        frame.add(root, BorderLayout.CENTER);
        place(frame, x, y, width, height);
        return frame;
    }

    /** Moves and sizes a stand-in frame, then lays it out. */
    static void place(JPanel frame, int x, int y, int width, int height) {
        frame.setBounds(x, y, width, height);
        relayout(frame);
    }

    /** Lays a stand-in frame out again, as Swing would after a drop changed its workspace. */
    static void relayout(JPanel frame) {
        LayoutTestSupport.layoutTree(frame);
        LayoutTestSupport.layoutTree(frame);
    }

    /** The middle of a stand-in frame's title bar, on the pretend screen. */
    static Point onTitleBar(JPanel frame) { return new Point(frame.getX() + frame.getWidth() / 2, frame.getY() + TITLE / 2); }

    /** {@code bounds}, in {@code component}'s coordinates, on the pretend screen. */
    static Rectangle onScreen(Component component, Rectangle bounds) {
        Point origin = bounds.getLocation();
        SwingUtilities.convertPointToScreen(origin, component);
        return new Rectangle(origin, bounds.getSize());
    }

    /** The middle of {@code component}, on the pretend screen. */
    static Point middle(Component component) {
        Rectangle bounds = onScreen(component, new Rectangle(component.getSize()));
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    /** A point at fractions of {@code bounds}' width and height, from its top left. */
    static Point inside(Rectangle bounds, double across, double down) {
        return new Point(bounds.x + (int) (bounds.width * across), bounds.y + (int) (bounds.height * down));
    }

    /** The group view that holds {@code tab} in {@code window}. */
    static GroupView groupOf(WindowContent window, Tab tab) {
        return window.workspace().groupView(window.workspace().model().groupOf(tab.id()).orElseThrow());
    }

    /** A point in {@code tab}'s strip entry, {@code across} of its width from its left, on the pretend screen. */
    static Point onTab(WindowContent window, Tab tab, double across) {
        GroupView group = groupOf(window, tab);
        return inside(onScreen(group, group.tabBounds(group).get(group.tabs().indexOf(tab))), across, 0.5);
    }

    /** {@code group}'s card area in {@code window}, on the pretend screen. */
    static Rectangle cards(WindowContent window, GroupId group) {
        WorkspaceView workspace = window.workspace();
        return onScreen(workspace, workspace.groupView(group).contentBounds(workspace));
    }

    /** The component a press on {@code tab}'s strip entry reaches: the equal tabs' title button, or a classic tab. */
    static Component entry(WindowContent window, Tab tab) {
        GroupView group = groupOf(window, tab);
        Component strip = group.strip().component();
        Rectangle bounds = group.tabBounds(strip).get(group.tabs().indexOf(tab));
        return SwingUtilities.getDeepestComponentAt(strip, (int) bounds.getCenterX(), (int) bounds.getCenterY());
    }

    /** A left-button event at {@code screen}, sent to {@code source} as Swing sends a drag's events to the pressed component. */
    static void mouse(Component source, int id, Point screen) {
        Point at = new Point(screen);
        SwingUtilities.convertPointFromScreen(at, source);
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
    }

    /**
     * Presses in the middle of {@code tab}'s strip entry, drags 12 px down (past the 5 px threshold at any scale up to 2)
     * and on to each point, without releasing.
     *
     * @return the pressed component, which takes the drag's later events
     */
    static Component start(WindowContent window, Tab tab, Point... path) {
        Component source = entry(window, tab);
        Point press = middle(source);
        mouse(source, MouseEvent.MOUSE_PRESSED, press);
        mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y + 12));
        for (Point point : path) mouse(source, MouseEvent.MOUSE_DRAGGED, point);
        return source;
    }

    /** As {@link #start}, then releases at the last point. */
    static void drop(WindowContent window, Tab tab, Point... path) {
        mouse(start(window, tab, path), MouseEvent.MOUSE_RELEASED, path[path.length - 1]);
    }

    /** How many listeners take every mouse motion event, as a running drag's lost-release listener does. */
    static int motionListeners() {
        return Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_MOTION_EVENT_MASK).length;
    }

    /** Hands {@code event} to every listener of mouse motion events, as AWT does before any component sees it. */
    static void broadcast(MouseEvent event) {
        for (var listener : Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_MOTION_EVENT_MASK)) listener.eventDispatched(event);
    }
}
