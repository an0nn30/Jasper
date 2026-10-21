package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.dock.WorkspaceDropResolver;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Headless tab drags (workspace tabs spec 12; tabs and panes spec 6): windows in stand-in frames on a pretend screen,
 * and the mouse events Swing sends a drag, in that screen's coordinates. A stand-in frame is a parentless panel at a
 * place on the pretend screen: a root pane holding the window's content, under a title bar that is the panel's top
 * inset, as a native frame's insets hold its title bar. Screen conversions, the drag's window bounds, the frame's
 * decorations and the overlay's layered pane then work without a display. A tab drags from its entry in its window's
 * strip, which shows the item of the tab's active pane: "the entry of an item" is that entry. A pane drags from a press
 * with the pane drag's modifier ({@link #PANE}) inside it (tabs and panes spec 7).
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

    /** Lays a stand-in frame out again, as Swing would after a drop changed its strip. */
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

    /** The pane {@code group} of {@code window}'s shown tab, on the pretend screen. */
    static Rectangle pane(WindowContent window, GroupId group) {
        GroupView view = window.workspace().groupView(group);
        return onScreen(view, new Rectangle(view.getSize()));
    }

    /** A point in the strip's entry of {@code tab}, {@code across} of its width from its left, on the pretend screen. */
    static Point onEntry(WindowContent window, Tab tab, double across) {
        TabStrip strip = window.tabBar();
        Rectangle entry = strip.tabBounds().get(window.parentTabs().bar().tabs().indexOf(tab));
        return inside(onScreen(strip.component(), entry), across, 0.5);
    }

    /**
     * The landing of a tab dropped at {@code index} of {@code window}'s shown strip, in its layered pane, as the drag
     * shows it: {@code WorkspaceDropResolver.marker} over the strip and its entries but {@code dragged}'s own, in its
     * parent-tabs view's coordinates, {@code TAB_INDICATOR} wide.
     */
    static Rectangle marker(WindowContent window, Tab dragged, int index) {
        TabStrip strip = window.tabBar();
        ParentTabsView parents = window.parentTabs();
        List<Tab> listed = parents.bar().tabs();
        List<Rectangle> bounds = strip.tabBounds();
        var entries = new ArrayList<Rectangle>();
        for (int i = 0; i < listed.size(); i++)
            if (listed.get(i) != dragged) entries.add(SwingUtilities.convertRectangle(strip.component(), bounds.get(i), parents));
        int width = NativeStyles.installed().metric(DesignMetrics.Role.TAB_INDICATOR);
        Rectangle marker = WorkspaceDropResolver.marker(strip.component().getBounds(), entries, index, width);
        return SwingUtilities.convertRectangle(parents, marker, SwingUtilities.getRootPane(window).getLayeredPane());
    }

    /** The component a press on the strip's entry of {@code tab} reaches: the equal tabs' title button, or a classic tab. */
    static Component entry(WindowContent window, Tab tab) {
        TabStrip strip = window.tabBar();
        Rectangle bounds = strip.tabBounds().get(window.parentTabs().bar().tabs().indexOf(tab));
        return SwingUtilities.getDeepestComponentAt(strip.component(), (int) bounds.getCenterX(), (int) bounds.getCenterY());
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
     * Presses in the middle of the strip's entry of {@code tab}, drags 12 px down (past the 5 px threshold at any scale up
     * to 2) and on to each point, without releasing.
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

    /** The pane drag's modifier on this host (tabs and panes spec 7): Cmd+Option on macOS, Ctrl+Alt elsewhere. */
    static final int PANE = System.getProperty("os.name").startsWith("Mac")
        ? InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK : InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;

    /**
     * A left-button event at {@code screen} with {@code keys} held, sent to {@code source} as Swing sends a gesture's events
     * to the pressed component: the button is down but on its release and its click.
     *
     * @return the event, which a listener may have consumed
     */
    static MouseEvent mouse(Component source, int id, Point screen, int keys) {
        Point at = new Point(screen);
        SwingUtilities.convertPointFromScreen(at, source);
        boolean up = id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        var event = new MouseEvent(source, id, System.currentTimeMillis(), keys | (up ? 0 : InputEvent.BUTTON1_DOWN_MASK), at.x,
            at.y, 1, false, button);
        source.dispatchEvent(event);
        return event;
    }

    /** The component a press in the middle of {@code item}'s pane reaches: the deepest one there. */
    static Component inPane(WindowContent window, Tab item) {
        GroupView pane = window.parentTabs().holding(item).orElseThrow().groupView(item.group().orElseThrow());
        return SwingUtilities.getDeepestComponentAt(pane, pane.getWidth() / 2, pane.getHeight() / 2);
    }

    /**
     * Presses the left button with the pane drag's modifier in the middle of {@code item}'s pane, drags 12 px down (past
     * the 5 px threshold at any scale up to 2) and on to each point, without releasing.
     *
     * @return the pressed component, which takes the gesture's later events
     */
    static Component startPane(WindowContent window, Tab item, Point... path) {
        Component source = inPane(window, item);
        Point press = middle(source);
        mouse(source, MouseEvent.MOUSE_PRESSED, press, PANE);
        mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y + 12), PANE);
        for (Point point : path) mouse(source, MouseEvent.MOUSE_DRAGGED, point, PANE);
        return source;
    }

    /** As {@link #startPane}, then releases at the last point. */
    static void dropPane(WindowContent window, Tab item, Point... path) {
        mouse(startPane(window, item, path), MouseEvent.MOUSE_RELEASED, path[path.length - 1], PANE);
    }
}
