package dev.jasper.app.platform;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/** Bounded admission on any thread; one transient menu and its listeners belong to EDT. */
final class LinuxTrayPopup implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(LinuxTrayPopup.class.getName());
    record Screen(Rectangle deviceBounds, Rectangle userBounds, Insets insets, AffineTransform transform) {
        Screen {
            deviceBounds = new Rectangle(deviceBounds); userBounds = new Rectangle(userBounds);
            insets = (Insets)insets.clone(); transform = new AffineTransform(transform);
        }
        @Override public Rectangle deviceBounds() { return new Rectangle(deviceBounds); }
        @Override public Rectangle userBounds() { return new Rectangle(userBounds); }
        @Override public Insets insets() { return (Insets)insets.clone(); }
        @Override public AffineTransform transform() { return new AffineTransform(transform); }
    }
    private final Object lock = new Object();
    private final Consumer<Runnable> edt;
    private final Consumer<LinuxLegacyTray.MenuRequest> create;
    private LinuxLegacyTray.MenuRequest pending;
    private long revision;
    private boolean queued, dirty, closed, unavailableLogged, retiring;
    // EDT only. Each open invocation owns exactly one Window and one cleanup function.
    private Runnable dispose;
    private Window activeWindow;
    private Lease lease;
    private final Function<Runnable, Runnable> focusDeadline;
    private Runnable cancelFocus;
    private static final class Lease {
        final LinuxLegacyTray.MenuRequest request;
        final Runnable show;
        final long token;
        boolean complete, success;
        Lease(LinuxLegacyTray.MenuRequest request, Runnable show, long token) { this.request = request; this.show = show; this.token = token; }
    }

    LinuxTrayPopup(Consumer<Runnable> edt) { this.edt = edt; this.create = this::show; this.focusDeadline = LinuxTrayPopup::focusDeadline; }
    LinuxTrayPopup(Consumer<Runnable> edt, Consumer<LinuxLegacyTray.MenuRequest> create) { this(edt, create, () -> {}); }
    LinuxTrayPopup(Consumer<Runnable> edt, Consumer<LinuxLegacyTray.MenuRequest> create, Runnable cleanup) {
        this(edt, create, cleanup, LinuxTrayPopup::focusDeadline);
    }
    LinuxTrayPopup(Consumer<Runnable> edt, Consumer<LinuxLegacyTray.MenuRequest> create, Runnable cleanup, Function<Runnable, Runnable> focusDeadline) {
        this.edt = edt; this.create = request -> { dispose = cleanup; create.accept(request); }; this.focusDeadline = focusDeadline;
    }
    private static Runnable focusDeadline(Runnable expired) {
        var timer = new javax.swing.Timer(2000, event -> expired.run()); timer.setRepeats(false); timer.start();
        return () -> { timer.stop(); for (var listener : timer.getActionListeners()) timer.removeActionListener(listener); };
    }
    private void stopFocusDeadline() { var cancel = cancelFocus; cancelFocus = null; if (cancel != null) cancel.run(); }
    void awaitFocus(BooleanSupplier focused) {
        stopFocusDeadline();
        if (focused.getAsBoolean()) return;
        long token; synchronized (lock) { token = revision; }
        cancelFocus = focusDeadline.apply(() -> {
            synchronized (lock) { if (closed || revision != token) return; }
            if (!focused.getAsBoolean()) { cancel(); disposeSwing(); }
        });
    }
    void request(LinuxLegacyTray.MenuRequest request) {
        synchronized (lock) { if (closed) return; pending = request; ++revision; dirty = true; }
        enqueue();
    }
    void cancel() {
        synchronized (lock) { pending = null; ++revision; dirty = true; }
        enqueue();
    }
    @Override public void close() {
        synchronized (lock) { if (closed) return; closed = true; pending = null; ++revision; dirty = true; }
        enqueue();
    }
    private void enqueue() {
        synchronized (lock) { if (queued || !dirty) return; queued = true; }
        edt.accept(this::drain);
    }
    private boolean valid(long token, LinuxLegacyTray.MenuRequest request) {
        synchronized (lock) { if (closed || token != revision) return false; }
        return request.valid().getAsBoolean();
    }
    private void drain() {
        LinuxLegacyTray.MenuRequest request; long token; Lease ready;
        synchronized (lock) {
            if (retiring || lease != null && !lease.complete) { dirty = false; queued = false; return; }
            ready = lease; lease = null;
            request = pending; pending = null; token = revision; dirty = false;
            if (ready == null && request == null) ++revision;
        }
        try {
            if (ready != null) {
                if (request == null && ready.success && valid(ready.token, ready.request)) ready.show.run();
                else disposeSwing();
                if (request == null) return;
            }
            disposeSwing();
            synchronized (lock) {
                if (retiring) {
                    if (request != null && revision == token && pending == null) pending = request;
                    return;
                }
            }
            if (request == null || !valid(token, request)) return;
            var selected = new AtomicBoolean();
            create.accept(new LinuxLegacyTray.MenuRequest(request.epoch(), request.rootBounds(), request.rootScreenBounds(), request.pointer(), id -> {
                if (!valid(token, request) || !selected.compareAndSet(false, true)) return;
                try { request.action().accept(id); }
                finally { cancelIfCurrent(token); }
            }, () -> valid(token, request), request.hints(), request.userTime()));
        } finally {
            synchronized (lock) { queued = false; }
            enqueue();
        }
    }
    void lease(LinuxLegacyTray.MenuRequest request, long window, Runnable show) { lease(request, window, false, show); }
    void activate(LinuxLegacyTray.MenuRequest request, long window, Runnable after) { lease(request, window, true, after); }
    private void lease(LinuxLegacyTray.MenuRequest request, long window, boolean activation, Runnable show) {
        long token; synchronized (lock) { token = revision; }
        var admitted = new Lease(request, show, token);
        synchronized (lock) { if (lease != null) throw new IllegalStateException("Popup peer lease already outstanding"); lease = admitted; }
        request.hints().accept(new LinuxLegacyTray.PopupHint(window, activation, () -> valid(token, request), success -> {
            synchronized (lock) {
                if (admitted.complete) return;
                admitted.complete = true; admitted.success = success; dirty = true;
            }
            enqueue();
        }));
    }
    private void cancelIfCurrent(long token) {
        synchronized (lock) { if (revision != token) return; pending = null; ++revision; dirty = true; }
        enqueue();
    }
    private void dismissAfterSelection(Window window) {
        if (activeWindow != window) return;
        // Swing clears the path before JMenuItem.doClick; retain its action until that dispatch completes.
        synchronized (lock) { pending = null; dirty = true; }
        enqueue();
    }
    private void disposeSwing() { stopFocusDeadline(); synchronized (lock) { if (lease != null && !lease.complete) return; } var cleanup = dispose; dispose = null; if (cleanup != null) cleanup.run(); }

    private void disposeWindow(Window window) { if (activeWindow == window) { cancel(); disposeSwing(); } }

    static Rectangle toUser(Rectangle root, Screen screen) {
        try {
            var inverse = screen.transform.createInverse();
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (double x : new double[] {root.getMinX(), root.getMaxX()})
                for (double y : new double[] {root.getMinY(), root.getMaxY()}) {
                    var p = inverse.deltaTransform(new Point2D.Double(x - screen.deviceBounds.x, y - screen.deviceBounds.y), null);
                    minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY());
                    maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY());
                }
            int x = (int)Math.floor(minX), y = (int)Math.floor(minY);
            return new Rectangle(screen.userBounds.x + x, screen.userBounds.y + y,
                (int)Math.ceil(maxX) - x, (int)Math.ceil(maxY) - y);
        } catch (NoninvertibleTransformException e) { throw new IllegalArgumentException("Noninvertible screen transform", e); }
    }
    static Point clamp(Point anchor, Dimension menu, Screen screen) {
        int x = screen.userBounds.x + screen.insets.left, y = screen.userBounds.y + screen.insets.top;
        int width = Math.max(0, screen.userBounds.width - screen.insets.left - screen.insets.right);
        int height = Math.max(0, screen.userBounds.height - screen.insets.top - screen.insets.bottom);
        return new Point(Math.max(x, Math.min(anchor.x, x + width - menu.width)),
            Math.max(y, Math.min(anchor.y, y + height - menu.height)));
    }
    static Screen match(Rectangle nativeScreen, List<Screen> candidates) {
        var matches = candidates.stream().filter(s -> nativeScreen.equals(s.deviceBounds)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
    static Point anchor(Rectangle icon, boolean pointer, Point pointerUser, Screen screen) {
        var mapped = toUser(icon, screen);
        return pointer && pointerUser != null && mapped.contains(pointerUser) ? new Point(pointerUser)
            : new Point(mapped.x + mapped.width / 2, mapped.y + mapped.height);
    }
    static Screen verifiedScreen(Rectangle nativeBounds, Rectangle user, Insets insets, AffineTransform transform, int devices) {
        // X11 setup proves a root extent, not individual monitor identities. Do not infer them.
        var extent = transform.deltaTransform(new Point2D.Double(user.width, user.height), null);
        if (devices != 1 || user.x != 0 || user.y != 0 || user.width <= 0 || user.height <= 0
                || nativeBounds.x != 0 || nativeBounds.y != 0 || nativeBounds.width <= 0 || nativeBounds.height <= 0
                || transform.getShearX() != 0 || transform.getShearY() != 0
                || transform.getScaleX() <= 0 || transform.getScaleY() <= 0
                || Math.abs(extent.getX() - nativeBounds.width) > 0.001
                || Math.abs(extent.getY() - nativeBounds.height) > 0.001) return null;
        return match(nativeBounds, List.of(new Screen(new Rectangle(0, 0, (int)Math.round(extent.getX()), (int)Math.round(extent.getY())),
            user, insets, transform)));
    }
    private void unavailable() {
        if (!unavailableLogged) { unavailableLogged = true; LOG.log(System.Logger.Level.DEBUG, "Tray popup screen mapping unavailable"); }
    }
    private void show(LinuxLegacyTray.MenuRequest request) {
        if (GraphicsEnvironment.isHeadless() || request.userTime() == 0 || !request.valid().getAsBoolean()) { unavailable(); return; }
        var devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (devices.length != 1) { unavailable(); return; }
        var config = devices[0].getDefaultConfiguration();
        var screen = verifiedScreen(request.rootScreenBounds(), config.getBounds(),
            Toolkit.getDefaultToolkit().getScreenInsets(config), config.getDefaultTransform(), devices.length);
        if (screen == null) { unavailable(); return; }
        if (!request.valid().getAsBoolean()) return;
        var dialog = new JDialog((Window)null);
        dialog.setName("jasper-tray-popup"); dialog.setTitle("Jasper tray menu");
        dialog.setUndecorated(true); dialog.setType(Window.Type.UTILITY); dialog.setFocusableWindowState(true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        var menu = new EmbeddedMenu();
        var disposed = new AtomicBoolean();
        var closing = new AtomicBoolean();
        var finish = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var retirementTimer = new java.util.concurrent.atomic.AtomicReference<javax.swing.Timer>();
        var focus = new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent event) {
                if (activeWindow != dialog || !request.valid().getAsBoolean()) return;
                stopFocusDeadline(); dialog.getRootPane().requestFocusInWindow();
                MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[] {menu, (MenuElement)menu.getComponent(0)});
            }
            @Override public void windowLostFocus(WindowEvent event) {
                if (closing.get()) finish.get().run(); else disposeWindow(dialog);
            }
            @Override public void windowClosing(WindowEvent event) { cancel(); }
            @Override public void windowClosed(WindowEvent event) { disposeWindow(dialog); }
        };
        AWTEventListener outside = event -> {
            if (event instanceof MouseEvent mouse && mouse.getID() == MouseEvent.MOUSE_PRESSED
                    && mouse.getSource() instanceof java.awt.Component component && !SwingUtilities.isDescendingFrom(component, dialog)) disposeWindow(dialog);
        };
        var listener = new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { disposeWindow(dialog); }
            @Override public void popupMenuCanceled(PopupMenuEvent event) { disposeWindow(dialog); }
        };
        for (var row : LinuxTrayMenu.rows()) {
            if (row.separator()) menu.addSeparator();
            else {
                var item = new JMenuItem(row.label()); item.setName(Integer.toString(row.id()));
                item.addActionListener(event -> { try { request.action().accept(row.id()); } finally { disposeWindow(dialog); } });
                menu.add(item);
            }
        }
        activeWindow = dialog;
        finish.set(() -> {
            if (!disposed.compareAndSet(false, true)) return;
            var timer = retirementTimer.getAndSet(null);
            if (timer != null) { timer.stop(); for (var action : timer.getActionListeners()) timer.removeActionListener(action); }
            dialog.removeWindowFocusListener(focus); dialog.removeWindowListener(focus);
            dialog.setFocusableWindowState(false); dialog.getContentPane().removeAll(); dialog.dispose();
            activeWindow = null;
            boolean resume;
            synchronized (lock) { resume = retiring; retiring = false; if (resume) dirty = true; }
            if (resume) enqueue();
        });
        dispose = () -> {
            if (!closing.compareAndSet(false, true)) return;
            dispose = null;
            menu.dismiss = null; menu.visible = false;
            Toolkit.getDefaultToolkit().removeAWTEventListener(outside);
            menu.removePopupMenuListener(listener);
            MenuSelectionManager.defaultManager().clearSelectedPath();
            for (var component : menu.getComponents()) if (component instanceof JMenuItem item)
                for (var action : item.getActionListeners()) item.removeActionListener(action);
            menu.setInvoker(null); menu.setUI(null);
            boolean focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == dialog;
            if (focused && dialog.isDisplayable()) {
                synchronized (lock) { retiring = true; }
                var timer = new javax.swing.Timer(1000, event -> finish.get().run());
                timer.setRepeats(false); retirementTimer.set(timer); timer.start();
                // Keep the live peer/listener until AWT acknowledges focus leaving the hidden window.
                dialog.setVisible(false);
            } else finish.get().run();
        };
        menu.dismiss = () -> dismissAfterSelection(dialog);
        menu.addPopupMenuListener(listener); dialog.addWindowFocusListener(focus); dialog.addWindowListener(focus);
        Toolkit.getDefaultToolkit().addAWTEventListener(outside, AWTEvent.MOUSE_EVENT_MASK);
        try {
            menu.setInvoker(dialog.getRootPane()); dialog.setContentPane(menu); dialog.pack();
            if (!request.valid().getAsBoolean()) { disposeSwing(); return; }
            lease(request, com.sun.jna.Native.getWindowID(dialog), () -> {
                if (!request.valid().getAsBoolean()) { disposeWindow(dialog); return; }
                var current = devices[0].getDefaultConfiguration();
                if (!current.getBounds().equals(screen.userBounds()) || !current.getDefaultTransform().equals(screen.transform())) {
                    disposeWindow(dialog); return;
                }
                Point pointer = null;
                if (request.pointer()) {
                    try { var info = MouseInfo.getPointerInfo(); if (info != null && info.getDevice().equals(devices[0])) pointer = info.getLocation(); }
                    catch (SecurityException ignored) { /* Verified icon placement remains available. */ }
                }
                dialog.setLocation(clamp(anchor(request.rootBounds(), request.pointer(), pointer, screen), dialog.getSize(), screen));
                dialog.setVisible(true);
                activate(request, com.sun.jna.Native.getWindowID(dialog), () -> awaitFocus(dialog::isFocused));
            });
        } catch (RuntimeException | Error failure) { disposeSwing(); throw failure; }
    }
    /** Embeds the ordinary menu UI in our owned dialog instead of asking PopupFactory for another window. */
    private static final class EmbeddedMenu extends JPopupMenu {
        private boolean visible = true;
        private Runnable dismiss;
        @Override public boolean isVisible() { return visible; }
        @Override public void setVisible(boolean value) { if (!value && dismiss != null) dismiss.run(); }
    }
}
