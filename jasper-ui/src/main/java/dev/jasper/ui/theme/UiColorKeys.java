package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Color-only legacy UI key policy; never admits geometry, fonts or delegates. */
public final class UiColorKeys {
    private static final Pattern CLASS_NAME = Pattern.compile("(?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*");
    private UiColorKeys() {}
    /** Whether a legacy UI key describes a color or a referenced color variable. */
    public static boolean supports(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return key.startsWith("@") || lower.endsWith("background") || lower.endsWith("foreground")
            || lower.endsWith("color") || lower.endsWith("thumb") || lower.endsWith("track")
            || lower.endsWith("caret") || lower.endsWith("border") && !lower.endsWith("windowborder")
            || lower.startsWith("actions.") || lower.startsWith("objects.");
    }
    /** Whether both the key and value are color-only, including per-platform color choices. */
    public static boolean supports(String key, Object value) {
        if (!supports(key)) return false;
        if (value instanceof Color) return true;
        if (value instanceof String text) return !CLASS_NAME.matcher(text.strip()).matches();
        if (value instanceof Map<?, ?> choices && !choices.isEmpty())
            return choices.entrySet().stream().allMatch(entry -> entry.getKey() instanceof String os
                && os.startsWith("os.") && supports(key, entry.getValue()));
        return false;
    }
}
