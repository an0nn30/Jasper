package dev.jasper.editor.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;
import javax.swing.KeyStroke;

/** Shortcuts with the platform's menu modifier, and how they read. Uses no Toolkit, so it works headless. */
public final class KeyNames {
    /** Whether this is macOS. */
    public static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    /** Cmd on macOS, Ctrl elsewhere. */
    public static final int MENU = MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

    private KeyNames() { }

    /**
     * @param keyCode a {@link KeyEvent} key code
     * @param extraModifiers modifiers besides the menu modifier, such as {@link InputEvent#SHIFT_DOWN_MASK}
     * @return the stroke
     */
    public static KeyStroke stroke(int keyCode, int extraModifiers) { return KeyStroke.getKeyStroke(keyCode, MENU | extraModifiers); }

    /**
     * @param stroke a shortcut
     * @return how it reads on this platform, such as "⇧⌘S" or "Ctrl+Shift+S"
     */
    public static String describe(KeyStroke stroke) { return describe(stroke, MAC); }

    static String describe(KeyStroke stroke, boolean mac) {
        int modifiers = stroke.getModifiers();
        StringBuilder out = new StringBuilder();
        if (mac) {
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) out.append('⌃');
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) out.append('⌥');
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) out.append('⇧');
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) out.append('⌘');
        } else {
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) out.append("Ctrl+");
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) out.append("Alt+");
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) out.append("Shift+");
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) out.append("Meta+");
        }
        return out.append(key(stroke.getKeyCode())).toString();
    }

    private static String key(int code) {
        return switch (code) {
            case KeyEvent.VK_SLASH -> "/";
            case KeyEvent.VK_OPEN_BRACKET -> "[";
            case KeyEvent.VK_CLOSE_BRACKET -> "]";
            default -> code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z || code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9
                ? String.valueOf((char) code) : KeyEvent.getKeyText(code);
        };
    }
}
