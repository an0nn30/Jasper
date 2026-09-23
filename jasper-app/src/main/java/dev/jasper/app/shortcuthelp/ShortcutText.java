package dev.jasper.app.shortcuthelp;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.KeyStroke;

/** Human-readable physical modifiers, shared by display, text search and capture. */
final class ShortcutText {
    private ShortcutText() { }

    static String format(KeyStroke stroke) {
        if (stroke == null) return "Unassigned";
        int modifiers = stroke.getModifiers();
        var text = new StringBuilder();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) text.append("Ctrl+");
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) text.append("Alt+");
        if ((modifiers & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) text.append("AltGraph+");
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) text.append("Shift+");
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) text.append("Cmd+");
        // Keep the familiar Cmd+Shift order while retaining the actual modifier set.
        String prefix = text.toString().replace("Shift+Cmd+", "Cmd+Shift+");
        String key = switch (stroke.getKeyCode()) {
            case KeyEvent.VK_ENTER -> "Enter"; case KeyEvent.VK_ESCAPE -> "Escape";
            case KeyEvent.VK_TAB -> "Tab"; case KeyEvent.VK_SPACE -> "Space";
            case KeyEvent.VK_BACK_SPACE -> "Backspace"; case KeyEvent.VK_DELETE -> "Delete";
            case KeyEvent.VK_UP -> "Up"; case KeyEvent.VK_DOWN -> "Down";
            case KeyEvent.VK_LEFT -> "Left"; case KeyEvent.VK_RIGHT -> "Right";
            case KeyEvent.VK_HOME -> "Home"; case KeyEvent.VK_END -> "End";
            case KeyEvent.VK_PAGE_UP -> "Page Up"; case KeyEvent.VK_PAGE_DOWN -> "Page Down";
            case KeyEvent.VK_INSERT -> "Insert"; case KeyEvent.VK_CONTEXT_MENU -> "Context Menu";
            case KeyEvent.VK_OPEN_BRACKET -> "["; case KeyEvent.VK_CLOSE_BRACKET -> "]";
            case KeyEvent.VK_EQUALS -> "="; case KeyEvent.VK_MINUS -> "-";
            case KeyEvent.VK_COMMA -> ","; case KeyEvent.VK_PERIOD -> ".";
            case KeyEvent.VK_SLASH -> "/"; case KeyEvent.VK_BACK_SLASH -> "\\";
            case KeyEvent.VK_SEMICOLON -> ";"; case KeyEvent.VK_QUOTE -> "'";
            case KeyEvent.VK_BACK_QUOTE -> "`";
            default -> KeyEvent.getKeyText(stroke.getKeyCode());
        };
        return prefix + key;
    }

    static boolean matches(ShortcutEntry row, String query, KeyStroke captured) {
        if (captured != null) return captured.equals(row.stroke());
        String q = normalize(query);
        if (q.isBlank()) return true;
        if (q.contains("+")) return row.stroke() != null && tokens(q).equals(tokens(normalize(format(row.stroke()))));
        String haystack = normalize(row.name() + " " + row.group() + " " + row.context() + " "
            + row.keywords() + " " + row.id() + " " + format(row.stroke()));
        return Arrays.stream(q.split("\\s+")).allMatch(haystack::contains);
    }

    private static Set<String> tokens(String text) {
        return Arrays.stream(text.split("\\+", -1)).map(String::strip).collect(Collectors.toSet());
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT)
            .replace("\u2318", "cmd+").replace("\u21e7", "shift+").replace("\u2325", "alt+").replace("\u2303", "ctrl+")
            .replaceAll("\\b(command|meta)\\b", "cmd").replaceAll("\\bcontrol\\b", "ctrl")
            .replaceAll("\\boption\\b", "alt").replaceAll("\\besc\\b", "escape")
            .replaceAll("\\breturn\\b", "enter");
    }
}
