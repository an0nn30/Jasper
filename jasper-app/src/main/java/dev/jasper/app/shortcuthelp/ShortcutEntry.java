package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import java.util.Objects;
import javax.swing.KeyStroke;

/** One reference row; a null stroke means the action is currently unassigned. {@code where} names the context. */
record ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords,
                     boolean editable, String defaultBinding, String where, String groupIcon) {
    ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords,
                  boolean editable, String defaultBinding, String where) {
        this(id,group,name,stroke,context,keywords,editable,defaultBinding,where,"plugin");
    }
    ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords,
                  boolean editable, String defaultBinding) {
        this(id, group, name, stroke, context, keywords, editable, defaultBinding, "Global");
    }

    ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords) {
        this(id, group, name, stroke, context, keywords, false, "none", context);
    }

    /** Whether an editable row's shortcut differs from its default. */
    boolean customized(boolean macOs) {
        return editable && !Objects.equals(stroke, KeyBindings.parse(defaultBinding, macOs).orElse(null));
    }
}
