package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.persistence.UiState;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * A window's detached tool windows' own windows (floating tool windows spec, The detached window): one native
 * window per shown detached tool window that has a frame in the window's {@link ToolWindowArea}, opened, moved, reopened
 * in its other mode and disposed so that they match the area after each dock change ({@link #sync}). The native windows
 * are {@link Shell}s that {@link #shells} makes ({@link DetachedFrame} in the application), as {@link WindowShell} is
 * for workspace windows; without shells (headless) or before their window shows ({@link #ready}) windows wait, and
 * open on the next sync. A window opens at the bounds its tool window has in the dock, kept on today's displays by
 * {@link WindowPlacement#restore}'s rule, and reports moves back to the dock, which saves them with its next change. A
 * window that fails to open hides its tool window, and one that fails to dispose goes all the same: each is logged, and
 * the dock change around it completes. EDT only.
 */
final class DetachedToolWindows implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(DetachedToolWindows.class.getName());
    /** The smallest size a detached tool window's window opens at, in logical pixels. */
    private static final int MINIMUM_WIDTH = 240, MINIMUM_HEIGHT = 160;

    /** The smallest size of a detached tool window's window, scaled: it opens at least this large and never shrinks below it. */
    static Dimension minimumSize() { return new Dimension(UIScale.scale(MINIMUM_WIDTH), UIScale.scale(MINIMUM_HEIGHT)); }

    /**
     * What a detached tool window's native window opens with.
     *
     * @param id the tool window
     * @param title its title, the window's
     * @param mode {@link ToolWindowMode#FLOAT} (a dialog owned by {@code owner}) or {@link ToolWindowMode#WINDOW} (a frame)
     * @param bounds where the window opens, in screen coordinates
     * @param frame the tool window's frame, its header over its content, which the window holds
     * @param owner their window's native window, Float's owner; null while it has none (a headless test)
     * @param keys binds Jasper's shortcuts on the window's root pane; the handle it returns unbinds them
     */
    record Opening(String id, String title, ToolWindowMode mode, Rectangle bounds, JComponent frame, Window owner,
                   Function<JRootPane, AutoCloseable> keys) { }

    /**
     * What the native window reports. Inert once its window was disposed.
     *
     * @param closing the user closed it (the OS close button): the tool window hides and stays detached
     * @param moved it moved or was resized, to these screen bounds
     */
    record Events(Runnable closing, Consumer<Rectangle> moved) { }

    /**
     * The native window's functions.
     *
     * @param show shows it, once, after it opened, without taking the focus: only {@link #activate} (an activating
     *     request) focuses it
     * @param toFront raises it and gives it the focus, restoring it when it is minimized
     * @param dispose disposes it
     * @param active whether it is the active window
     * @param place moves and sizes it to screen bounds
     * @param content puts another frame in it: the area remounted the tool window's frame (a kit view's new header)
     */
    record Shell(Runnable show, Runnable toFront, Runnable dispose, BooleanSupplier active, Consumer<Rectangle> place,
                 Consumer<JComponent> content) {
        /** Requires every function. */
        Shell {
            Objects.requireNonNull(show, "show"); Objects.requireNonNull(toFront, "toFront"); Objects.requireNonNull(dispose, "dispose");
            Objects.requireNonNull(active, "active"); Objects.requireNonNull(place, "place"); Objects.requireNonNull(content, "content");
        }
    }

    /** One open window: the mode it opened in, the frame it holds and the bounds it last had. */
    private static final class Open {
        final ToolWindowMode mode;
        JComponent frame;
        Rectangle bounds;
        Shell shell;

        Open(ToolWindowMode mode, JComponent frame, Rectangle bounds) {
            this.mode = mode;
            this.frame = frame;
            this.bounds = bounds;
        }
    }

    /** Makes a tool window's native window; null while there are none (the headless default), and windows wait. */
    BiFunction<Opening, Events, Shell> shells;
    /** Whether windows may open: by default, while their window's content is showing, which a Float needs for its owner. */
    BooleanSupplier ready;
    /** Today's displays, whole and usable, the default display first; empty when none is known (headless). */
    Supplier<List<WindowPlacement.Display>> displays = WindowFrame::displays;

    private final WindowContent owner;
    private final DockState dock;
    private final ToolWindowArea area;
    private final Function<String, String> titles;
    private final Consumer<String> hide;
    private final BiFunction<JRootPane, Runnable, AutoCloseable> keys;
    private final Map<String, Open> open = new LinkedHashMap<>();
    private boolean closed;

    /**
     * The detached tool windows of the window whose content is {@code owner}: its {@link WindowContent#dock() dock}
     * says which show, its {@link WindowContent#area() area} holds their frames, and its native window owns a Float.
     *
     * @param owner the window's content
     * @param titles a tool window's title, its window's
     * @param hide hides a tool window: its window was closed, or its keys' Close Pane, Close Tab or Close Window
     * @param keys binds Jasper's shortcuts on a window's root pane, the second argument hiding its tool window; the handle
     *     it returns unbinds them
     */
    DetachedToolWindows(WindowContent owner, Function<String, String> titles, Consumer<String> hide,
                        BiFunction<JRootPane, Runnable, AutoCloseable> keys) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.dock = owner.dock();
        this.area = owner.area();
        this.titles = Objects.requireNonNull(titles, "titles");
        this.hide = Objects.requireNonNull(hide, "hide");
        this.keys = Objects.requireNonNull(keys, "keys");
        ready = owner::isShowing;
    }

    /**
     * Brings the windows in line with the area: a window whose tool window no longer shows detached with a frame is
     * disposed; one whose mode changed is disposed and opens again in the new mode at the same bounds; one whose frame
     * the area remounted takes the new frame; one whose dock bounds changed (a drop outside their window) moves there;
     * a shown detached tool window without a window opens, when there are shells and their window is ready. A shell's
     * function may change the dock at once (a window event, a listener), which runs another sync inside this one: each
     * step reads again what the nested sync may have changed, and a tool window it took the frame or the window of is
     * left alone.
     */
    void sync() {
        if (closed) return;
        List<String> framed = area.detachedFrames();
        for (String id : List.copyOf(open.keySet())) if (!framed.contains(id)) dispose(id);
        for (String id : framed) {
            Open window = open.get(id);
            if (window != null && window.mode != dock.mode(id)) { dispose(id); window = open.get(id); }
            JComponent frame = area.detachedFrame(id);
            if (frame == null) continue;
            if (window == null) {
                if (shells != null && ready.getAsBoolean()) open(id, frame);
                continue;
            }
            if (window.frame != frame) { window.frame = frame; window.shell.content().accept(frame); }
            if (open.get(id) != window) continue;
            Optional<Rectangle> bounds = dock.bounds(id);
            if (bounds.isPresent() && !bounds.get().equals(window.bounds)) {
                window.bounds = bounds.get();
                window.shell.place().accept(new Rectangle(window.bounds));
            }
        }
    }

    /** Whether the tool window's own window is open. */
    boolean showing(String id) { return open.containsKey(id); }

    /** Whether the tool window's own window is open and the active window. */
    boolean active(String id) {
        Open window = open.get(id);
        return window != null && window.shell.active().getAsBoolean();
    }

    /** Raises the tool window's own window, if open, and moves the keyboard into its frame. */
    void activate(String id) {
        Open window = open.get(id);
        if (window != null) window.shell.toFront().run();
        area.activate(id);
    }

    /**
     * Where a tool window's own window first opens when it has never had one: its docked frame's place and size on
     * screen, or while it is hidden the size it would dock at centred on their window; at least the minimum, and kept
     * on the usable part of the display holding its centre (the default display when none does).
     */
    Rectangle initialBounds(String id) {
        Optional<Rectangle> docked = area.frameOnScreen(id);
        Dimension size = size(id);
        Point at = docked.map(Rectangle::getLocation).orElseGet(() -> {
            Rectangle main = mainOnScreen();
            return new Point(main.x + (main.width - size.width) / 2, main.y + (main.height - size.height) / 2);
        });
        var bounds = new Rectangle(at, size);
        return onDisplay(bounds, new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()));
    }

    /**
     * Where a tool window's own window goes for a dock drag released on Detach (floating tool windows spec, The drag
     * out): at the size it opens at, with the point the press held under the pointer (that point kept inside the
     * window), and kept on the usable part of the display under the pointer, its menu bar, Dock or taskbar included; the
     * default display when the pointer is on none.
     *
     * @param screen the release point on screen
     * @param grab where the press held the dragged stripe button or header, in its coordinates
     */
    Rectangle droppedBounds(String id, Point screen, Point grab) {
        Dimension size = size(id);
        int x = Math.clamp(grab.x, 0, size.width - 1), y = Math.clamp(grab.y, 0, size.height - 1);
        return onDisplay(new Rectangle(new Point(screen.x - x, screen.y - y), size), screen);
    }

    /**
     * The size a tool window's own window opens at (floating tool windows spec, The detached window): the bounds it last
     * had, else its docked frame's on screen, else the size it would dock at; at least the minimum.
     */
    private Dimension size(String id) {
        Dimension size = dock.bounds(id).or(() -> area.frameOnScreen(id)).map(Rectangle::getSize)
            .orElseGet(() -> area.landingSize(id));
        Dimension minimum = minimumSize();
        return new Dimension(Math.max(minimum.width, size.width), Math.max(minimum.height, size.height));
    }

    /**
     * {@code bounds} kept on the usable part of the display whose whole bounds hold {@code point}, as a torn-out tab's
     * window is ({@code WindowFrame}), else of the default display; as they are with none known.
     */
    private Rectangle onDisplay(Rectangle bounds, Point point) {
        List<WindowPlacement.Display> screens = displays.get();
        if (screens.isEmpty()) return bounds;
        int index = WindowPlacement.displayFor(point, screens.stream().map(WindowPlacement.Display::bounds).toList()).orElse(0);
        return WindowPlacement.fitted(bounds, screens.get(index).usable());
    }

    /** Disposes every window, each whether or not another fails to; nothing opens afterwards. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        for (String id : List.copyOf(open.keySet())) dispose(id);
    }

    /**
     * Opens the tool window's window at its dock bounds (or its first ones) on today's displays, which the dock keeps. A
     * window that fails to be made or shown is logged and goes, and its tool window hides: the change that opened it
     * completes and saves it hidden, so neither the next change nor the next start opens it again.
     */
    private void open(String id, JComponent frame) {
        Rectangle bounds = dock.bounds(id).orElseGet(() -> initialBounds(id));
        bounds = new WindowPlacement(Optional.of(new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height)), Frame.NORMAL)
            .restore(minimumSize(), displays.get().stream().map(WindowPlacement.Display::usable).toList()).orElse(bounds);
        dock.setBounds(id, bounds);
        var window = new Open(dock.mode(id), frame, bounds);
        var events = new Events(() -> { if (open.get(id) == window) hide.accept(id); }, moved -> {
            if (open.get(id) != window || moved.isEmpty()) return;
            window.bounds = new Rectangle(moved);
            dock.setBounds(id, moved);
        });
        Shell shell;
        try {
            // The binding is made with the window's root pane; its Close keys hide this tool window, as its close button does.
            shell = shells.apply(new Opening(id, titles.apply(id), window.mode, new Rectangle(bounds), frame, ownerWindow(),
                root -> keys.apply(root, () -> hide.accept(id))), events);
        } catch (RuntimeException failure) {
            failed(id, failure);
            return;
        }
        // A dock change made while the shell was made ran a sync that opened this tool window's window itself or took its
        // frame (it hid, or docked): no window is left for this shell, which never showed.
        if (open.containsKey(id) || area.detachedFrame(id) != frame) { dispose(id, shell); return; }
        window.shell = shell;
        open.put(id, window);
        try {
            shell.show().run();
        } catch (RuntimeException failure) {
            if (open.get(id) == window) dispose(id);
            failed(id, failure);
        }
    }

    /** Logs that the tool window's window failed to open, and hides the tool window. */
    private void failed(String id, RuntimeException failure) {
        LOG.log(System.Logger.Level.WARNING, "The tool window " + id + "'s own window could not be opened; it is hidden.", failure);
        hide.accept(id);
    }

    private void dispose(String id) {
        Open window = open.remove(id);
        if (window != null) dispose(id, window.shell);
    }

    /** Disposes a tool window's window; a failure is logged, and the window is no longer this one's all the same. */
    private static void dispose(String id, Shell shell) {
        try {
            shell.dispose().run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The tool window " + id + "'s own window could not be disposed.", failure);
        }
    }

    /** Their window's native window, or null when its content is in none (a headless test). */
    private Window ownerWindow() { return SwingUtilities.getWindowAncestor(owner); }

    /** Their window on screen: its native window, or in a headless test the stand-in frame its content sits in. */
    private Rectangle mainOnScreen() {
        Component top = owner;
        while (!(top instanceof Window) && top.getParent() != null) top = top.getParent();
        var at = new Point();
        SwingUtilities.convertPointToScreen(at, top);
        return new Rectangle(at, top.getSize());
    }
}
