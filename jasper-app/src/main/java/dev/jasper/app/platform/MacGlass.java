package dev.jasper.app.platform;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * The macOS side of {@link WindowGlass}. AWT refuses a translucent background on a decorated frame, so translucency
 * goes through two JDK internals (the launcher opens them); blur is a JNI bridge to a private CoreGraphics call that
 * Terminal.app, iTerm2, Alacritty and WezTerm also use. Every failure returns false rather than throwing. EDT only.
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

    static void opaque(JRootPane root) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window != null) restore(window);
    }

    private static void restore(Window window) {
        try {
            Object peer = peer(window);
            if (peer != null) Class.forName("java.awt.peer.WindowPeer").getMethod("setOpaque", boolean.class).invoke(peer, true);
            Color original = ORIGINAL.remove(window);
            if (original != null && peer != null) background(window, peer, original);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Nothing more can be undone; WindowGlass already reports an opaque window.
        }
    }

    private static Object peer(Window window) throws ReflectiveOperationException {
        Field field = Component.class.getDeclaredField("peer");
        field.setAccessible(true);
        return field.get(window);
    }

    /** Frame.setBackground refuses alpha on a decorated frame, so the field and the peer are set directly. */
    private static void background(Window window, Object peer, Color color) throws ReflectiveOperationException {
        Field field = Component.class.getDeclaredField("background");
        field.setAccessible(true);
        field.set(window, color);
        Class.forName("java.awt.peer.ComponentPeer").getMethod("setBackground", Color.class).invoke(peer, color);
    }

    static boolean blur(JRootPane root, int radius) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window == null || !load()) return false;
        try {
            return setBlur(window, radius);
        } catch (UnsatisfiedLinkError failure) {
            return false;
        }
    }

    /** Under {@code ./gradlew run} the build passes {@code jasper.glassLibrary}; a packaged app has it in Contents/app. */
    static Path library(String override, String appPath) {
        if (override != null && !override.isBlank()) return Path.of(override);
        Path notifications = MacNotifications.libraryPath(appPath);
        return notifications == null ? null : notifications.resolveSibling("libjasper-glass.dylib");
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

    private static native boolean setBlur(Window window, int radius);
}
