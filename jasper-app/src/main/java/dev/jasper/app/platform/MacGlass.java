package dev.jasper.app.platform;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;

/**
 * The macOS side of {@link WindowGlass}. AWT refuses a translucent background on a decorated frame, so translucency
 * goes through two JDK internals (the launcher opens them); blur is a small JNI bridge that places a public
 * {@code NSVisualEffectView} behind the window's content, masked to its rounded corners. Every failure returns false
 * rather than throwing. EDT only.
 */
final class MacGlass {
    private static final Color CLEAR = new Color(0, 0, 0, 0);
    private static final Map<Window, Color> ORIGINAL = new WeakHashMap<>();
    private static Boolean loaded;

    private MacGlass() { }

    static boolean translucent(JRootPane root) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window == null) return false;
        try {
            Object peer = peer(window);
            if (peer == null) return false;
            ORIGINAL.putIfAbsent(window, window.getBackground());
            background(window, peer, CLEAR);
            Class.forName("java.awt.peer.WindowPeer").getMethod("setOpaque", boolean.class).invoke(peer, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            restore(window);
            return false;
        }
    }

    static Runnable watch(JRootPane root, Runnable reset) {
        Window window = SwingUtilities.getWindowAncestor(root);
        return window == null ? () -> {} : watchBackground(window, reset);
    }

    /**
     * Glass sets the window background without notifying anyone, so a notified opaque background comes from
     * elsewhere: FlatLaf, for one, sets the frame background a turn after a look-and-feel switch, and
     * {@code Window.setBackground} then makes the peer opaque. That background is the one to restore from now on.
     */
    static Runnable watchBackground(Component window, Runnable reset) {
        PropertyChangeListener listener = event -> {
            if (event.getNewValue() instanceof Color color && color.getAlpha() < 255) return;
            ORIGINAL.remove(window); // The next translucent() records the new background.
            reset.run();
        };
        window.addPropertyChangeListener("background", listener);
        return () -> window.removePropertyChangeListener("background", listener);
    }

    static void opaque(JRootPane root) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window != null) restore(window);
    }

    /** Each step is tried on its own, background first, so a later failure cannot leave the field transparent. */
    private static void restore(Window window) {
        Color original = restored(ORIGINAL.remove(window), UIManager.getColor("control"));
        Object peer = null;
        try {
            peer = peer(window);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Still restore the field below.
        }
        if (original != null) {
            try {
                background(window, peer, original);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Nothing more can be undone here; WindowGlass already reports an opaque window.
            }
        }
        if (peer != null) {
            try {
                Class.forName("java.awt.peer.WindowPeer").getMethod("setOpaque", boolean.class).invoke(peer, true);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // As above.
            }
        }
    }

    /**
     * The background to restore. A look-and-feel default recorded before a theme switch is stale, so the current
     * theme's window colour replaces it.
     */
    static Color restored(Color original, Color control) {
        return original instanceof UIResource && control != null ? control : original;
    }

    private static Object peer(Window window) throws ReflectiveOperationException {
        Field field = Component.class.getDeclaredField("peer");
        field.setAccessible(true);
        return field.get(window);
    }

    /**
     * Frame.setBackground refuses alpha on a decorated frame, so the field and the peer are set directly, without
     * notifying {@link #watchBackground} listeners. The field is set first; a missing peer is left alone.
     */
    private static void background(Window window, Object peer, Color color) throws ReflectiveOperationException {
        Field field = Component.class.getDeclaredField("background");
        field.setAccessible(true);
        field.set(window, color);
        if (peer != null) Class.forName("java.awt.peer.ComponentPeer").getMethod("setBackground", Color.class).invoke(peer, color);
    }

    /** NSVisualEffectMaterial.hudWindow: the material for a dark workspace theme. */
    static final int DARK_MATERIAL = 13;
    /** NSVisualEffectMaterial.underWindowBackground: the material for a light workspace theme. */
    static final int LIGHT_MATERIAL = 21;

    /**
     * Shows or removes the effect view behind the window: {@code hudWindow} in the dark appearance for a dark
     * workspace, {@code underWindowBackground} in the light appearance for a light one.
     */
    static boolean blur(JRootPane root, WindowGlass.Blur blur) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window == null || !load()) return false;
        try {
            return switch (blur) {
                case NONE -> setEffect(window, -1, false);
                case DARK -> setEffect(window, DARK_MATERIAL, false);
                case LIGHT -> setEffect(window, LIGHT_MATERIAL, true);
            };
        } catch (UnsatisfiedLinkError failure) {
            return false;
        }
    }

    /** Under {@code ./gradlew run} the build passes {@code jasper.glassLibrary}; a packaged app has it in Contents/app. */
    static Path library(String override, String appPath) {
        if (override != null && !override.isBlank()) return Path.of(override);
        Path bridge = MacNative.libraryPath(appPath);
        return bridge == null ? null : bridge.resolveSibling("libjasper-glass.dylib");
    }

    @SuppressWarnings("restricted") // The launcher explicitly enables native access.
    private static synchronized boolean load() {
        if (loaded == null) {
            Path library = library(System.getProperty("jasper.glassLibrary"), System.getProperty("jpackage.app-path"));
            try {
                if (library == null) loaded = false;
                else {
                    // The bridge finds JAWT_GetAWT at run time, so the JDK's JAWT library is loaded first.
                    System.loadLibrary("jawt");
                    System.load(library.toString());
                    loaded = true;
                }
            } catch (UnsatisfiedLinkError | SecurityException failure) {
                loaded = false;
            }
        }
        return loaded;
    }

    /** Shows the effect view with this NSVisualEffectMaterial ({@code material < 0} removes it), light or dark. */
    private static native boolean setEffect(Window window, int material, boolean light);
}
