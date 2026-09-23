package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Dimension;
import java.util.Map;
import java.util.function.Supplier;

/** Application-owned singleton reference window. All operations and suppliers run on the EDT. */
public final class ShortcutHelp {
    private final AuxiliaryWindows windows;
    private final Contributions contributions;
    private final Supplier<KeyBindings> bindings;
    private final Supplier<Map<String, String>> pluginNames;
    private final boolean macOs;
    private AuxiliarySurface surface;
    private ShortcutPanel panel;

    public ShortcutHelp(AuxiliaryWindows windows, Contributions contributions, Supplier<KeyBindings> bindings,
                        Supplier<Map<String, String>> pluginNames, boolean macOs) {
        this.windows = windows; this.contributions = contributions; this.bindings = bindings;
        this.pluginNames = pluginNames; this.macOs = macOs;
    }

    /** Opens the reference or focuses its existing window, preserving the current search. */
    public void open() {
        if (surface == null) {
            var opened = windows.window("app.shortcuts", "Keyboard Shortcuts", new Dimension(1000, 640), true);
            surface = opened;
            var view = new ShortcutPanel(); panel = view;
            opened.setContent(view);
            Subscription listening = contributions.onChanged(kind -> {
                if (kind == Contributions.Kind.ACTIONS) refresh();
            });
            opened.onClosed(() -> {
                listening.close(); view.close();
                if (surface == opened) { surface = null; panel = null; }
            });
            opened.onActivated(this::refresh);
        }
        refresh(); surface.show(); surface.toFront();
    }

    /** Called after app configuration reload; plugin changes also refresh through contributions. */
    public void refresh() {
        if (panel != null) panel.setRows(ShortcutCatalog.rows(bindings.get(), contributions.actions(), pluginNames.get(), macOs));
    }
}
