package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import java.util.Objects;
import javax.swing.KeyStroke;

/** One reference row; a null stroke means the action is currently unassigned. */
record ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords,
                     boolean editable, String defaultBinding) {
    ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords) {
        this(id, group, name, stroke, context, keywords, false, "none");
    }

    /** Whether an editable row's shortcut differs from its default. */
    boolean customized(boolean macOs) {
        return editable && !Objects.equals(stroke, KeyBindings.parse(defaultBinding, macOs).orElse(null));
    }
}
