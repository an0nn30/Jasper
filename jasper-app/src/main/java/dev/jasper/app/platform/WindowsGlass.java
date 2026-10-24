package dev.jasper.app.platform;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;

/**
 * Throwaway Windows 11 glass experiment. DWM mode preserves the normal AWT surface; layered mode asks AWT for
 * per-pixel alpha instead. Native success cannot establish whether Swing preserved the pixels' alpha: that is the
 * purpose of the Windows visual check. EDT only; loaded only after explicit opt-in on Windows.
 */
final class WindowsGlass {
    private static final Logger LOG = Logger.getLogger(WindowsGlass.class.getName());
    private static final Map<Window, Color> ORIGINAL = new WeakHashMap<>();
    private static final Map<Window, Integer> ORIGINAL_DARK = new WeakHashMap<>();
    private static final int BACKDROP = 38, DARK_MODE = 20;

    private WindowsGlass() { }

    static boolean translucent(JRootPane root) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window == null) return false;
        try {
            int build = windowsBuild();
            if (build < 22621) {
                LOG.warning("Windows glass prototype requires Windows 11 build 22621+, found " + build);
                return false;
            }
            ORIGINAL.putIfAbsent(window, window.getBackground());
            Object peer = peer(window);
            background(window, peer, new Color(0, 0, 0, 0));
            if (layered()) {
                opaquePeer(peer, false);
            } else {
                // Do not turn this into a layered window: test the ordinary AWT surface against DWM's backdrop.
                ORIGINAL_DARK.putIfAbsent(window, attribute(window, DARK_MODE));
                frame(window, -1);
            }
            LOG.info("Windows glass prototype: mode=" + System.getProperty("jasper.windows.glass")
                + ", build=" + build + ", java=" + System.getProperty("java.runtime.version")
                + ". Native setup accepted; visual alpha/blur is unverified.");
            window.repaint();
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LOG.log(Level.WARNING, "Windows glass prototype setup failed; restoring opaque painting", failure);
            opaque(root);
            return false;
        }
    }

    static boolean blur(JRootPane root, WindowGlass.Blur blur) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window == null) return false;
        // WindowGlass clears blur after opaque() has already restored the native state.
        if (!ORIGINAL.containsKey(window)) return blur == WindowGlass.Blur.NONE;
        // Layered mode is a plain-transparency control experiment, without a DWM material.
        if (layered()) return blur == WindowGlass.Blur.NONE;
        try {
            attribute(window, DARK_MODE, blur == WindowGlass.Blur.NONE
                ? ORIGINAL_DARK.getOrDefault(window, 0) : blur == WindowGlass.Blur.DARK ? 1 : 0);
            attribute(window, BACKDROP, blur == WindowGlass.Blur.NONE ? 1 : 3);
            LOG.info("Windows glass prototype: DWM backdrop=" + blur);
            window.repaint();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(Level.WARNING, "Windows glass prototype backdrop failed", failure);
            return false;
        }
    }

    static void opaque(JRootPane root) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window == null || !ORIGINAL.containsKey(window)) return;
        Color original = ORIGINAL.remove(window);
        if (original instanceof UIResource) {
            Color current = UIManager.getColor("control");
            if (current != null) original = current;
        }
        if (!layered()) {
            attempt(() -> { attribute(window, BACKDROP, 1); return null; });
            attempt(() -> { frame(window, 0); return null; });
            Integer dark = ORIGINAL_DARK.remove(window);
            if (dark != null) attempt(() -> { attribute(window, DARK_MODE, dark); return null; });
        }
        Color restored = original;
        attempt(() -> { background(window, peer(window), restored); return null; });
        if (layered()) attempt(() -> { opaquePeer(peer(window), true); return null; });
        window.repaint();
    }

    static Runnable watch(JRootPane root, Runnable reset) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window == null) return () -> {};
        PropertyChangeListener listener = event -> {
            if (event.getNewValue() instanceof Color color && color.getAlpha() < 255) return;
            ORIGINAL.put(window, (Color) event.getNewValue());
            reset.run();
        };
        window.addPropertyChangeListener("background", listener);
        return () -> window.removePropertyChangeListener("background", listener);
    }

    private static boolean layered() { return "layered".equals(System.getProperty("jasper.windows.glass")); }

    private static int windowsBuild() {
        // RtlGetVersion avoids manifest-dependent GetVersionEx results. OSVERSIONINFOEXW is 284 bytes.
        try (Memory version = new Memory(284)) {
            version.clear();
            version.setInt(0, 284);
            int status = function("ntdll", "RtlGetVersion").invokeInt(new Object[] { version });
            if (status != 0) throw new IllegalStateException("RtlGetVersion: " + status);
            return version.getInt(4) >= 10 ? version.getInt(12) : 0;
        }
    }

    private static int attribute(Window window, int attribute) {
        try (Memory data = new Memory(4)) {
            checked("DwmGetWindowAttribute(" + attribute + ")",
                function("dwmapi", "DwmGetWindowAttribute").invokeInt(new Object[] { handle(window), attribute, data, 4 }));
            return data.getInt(0);
        }
    }

    private static void attribute(Window window, int attribute, int value) {
        try (Memory data = new Memory(4)) {
            data.setInt(0, value);
            checked("DwmSetWindowAttribute(" + attribute + "," + value + ")",
                function("dwmapi", "DwmSetWindowAttribute").invokeInt(new Object[] { handle(window), attribute, data, 4 }));
        }
    }

    private static void frame(Window window, int margin) {
        try (Memory margins = new Memory(16)) {
            for (int offset = 0; offset < 16; offset += 4) margins.setInt(offset, margin);
            checked("DwmExtendFrameIntoClientArea",
                function("dwmapi", "DwmExtendFrameIntoClientArea").invokeInt(new Object[] { handle(window), margins }));
        }
    }

    private static Pointer handle(Window window) { return Native.getComponentPointer(window); }

    private static Function function(String library, String name) {
        return NativeLibrary.getInstance(library).getFunction(name, Function.ALT_CONVENTION);
    }

    private static void checked(String operation, int result) {
        if (result < 0) throw new IllegalStateException(operation + " HRESULT=0x" + Integer.toHexString(result));
    }

    private static Object peer(Window window) throws ReflectiveOperationException {
        Field field = Component.class.getDeclaredField("peer");
        field.setAccessible(true);
        Object peer = field.get(window);
        if (peer == null) throw new IllegalStateException("Window has no peer");
        return peer;
    }

    private static void background(Window window, Object peer, Color color) throws ReflectiveOperationException {
        Field field = Component.class.getDeclaredField("background");
        field.setAccessible(true);
        field.set(window, color);
        Class.forName("java.awt.peer.ComponentPeer").getMethod("setBackground", Color.class).invoke(peer, color);
    }

    private static void opaquePeer(Object peer, boolean opaque) throws ReflectiveOperationException {
        Class.forName("java.awt.peer.WindowPeer").getMethod("setOpaque", boolean.class).invoke(peer, opaque);
    }

    private static void attempt(java.util.concurrent.Callable<Void> action) {
        try { action.call(); }
        catch (Exception | LinkageError failure) { LOG.log(Level.WARNING, "Windows glass prototype restore failed", failure); }
    }
}
