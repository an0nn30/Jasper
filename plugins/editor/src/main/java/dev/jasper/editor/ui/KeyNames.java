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
     * @param backwards Find Previous rather than Find Next
     * @return Cmd+G and Shift+Cmd+G on macOS; F3 and Shift+F3 elsewhere, where Ctrl+Shift+G is the window's Find Next
     */
    public static KeyStroke findAgain(boolean backwards) { return findAgain(backwards, MAC); }

    static KeyStroke findAgain(boolean backwards, boolean mac) {
        int shift = backwards ? InputEvent.SHIFT_DOWN_MASK : 0;
        return mac ? KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.META_DOWN_MASK | shift) : KeyStroke.getKeyStroke(KeyEvent.VK_F3, shift);
    }

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

    /**
     * @param key a Vim key with Ctrl, Alt, ⌘ or Shift, or a named key
     * @return the stroke that types it, for {@link #describe}
     */
    static KeyStroke stroke(dev.jasper.editor.vim.KeyInput key) {
        int modifiers = 0;
        if ((key.modifiers() & dev.jasper.editor.vim.KeyInput.CTRL) != 0) modifiers |= InputEvent.CTRL_DOWN_MASK;
        if ((key.modifiers() & dev.jasper.editor.vim.KeyInput.ALT) != 0) modifiers |= InputEvent.ALT_DOWN_MASK;
        if ((key.modifiers() & dev.jasper.editor.vim.KeyInput.SHIFT) != 0) modifiers |= InputEvent.SHIFT_DOWN_MASK;
        if ((key.modifiers() & dev.jasper.editor.vim.KeyInput.META) != 0) modifiers |= InputEvent.META_DOWN_MASK;
        int code = switch (key.key()) {
            case "Esc" -> KeyEvent.VK_ESCAPE;
            case "CR" -> KeyEvent.VK_ENTER;
            case "Tab" -> KeyEvent.VK_TAB;
            case "BS" -> KeyEvent.VK_BACK_SPACE;
            case "Del" -> KeyEvent.VK_DELETE;
            case "Insert" -> KeyEvent.VK_INSERT;
            case "Home" -> KeyEvent.VK_HOME;
            case "End" -> KeyEvent.VK_END;
            case "PageUp" -> KeyEvent.VK_PAGE_UP;
            case "PageDown" -> KeyEvent.VK_PAGE_DOWN;
            case "Up" -> KeyEvent.VK_UP;
            case "Down" -> KeyEvent.VK_DOWN;
            case "Left" -> KeyEvent.VK_LEFT;
            case "Right" -> KeyEvent.VK_RIGHT;
            // Ctrl+6 types <C-^>.
            case "^" -> KeyEvent.VK_6;
            default -> key.key().matches("F([1-9]|1[0-2])") ? KeyEvent.VK_F1 + Integer.parseInt(key.key().substring(1)) - 1
                : KeyEvent.getExtendedKeyCodeForChar(key.key().codePointAt(0));
        };
        return KeyStroke.getKeyStroke(code, modifiers);
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
