package dev.moray.app;

import java.util.Map;
import javax.swing.ImageIcon;

/** Bundled OldGNOME2 PNG artwork; no network access or recoloring. */
final class AppIcons {
    private static final Map<String, ImageIcon> ICONS = load();
    private AppIcons() {}

    static ImageIcon icon(String name) {
        ImageIcon icon = ICONS.get(name.equals("plus") ? "square-plus" : name);
        if (icon == null) throw new IllegalArgumentException("Unknown application icon: " + name);
        return icon;
    }

    private static Map<String, ImageIcon> load() {
        var icons = new java.util.HashMap<String, ImageIcon>();
        for (String name : java.util.List.of("square-plus", "app-window", "columns-2", "maximize",
                "search", "settings", "refresh", "close")) {
            var resource = java.util.Objects.requireNonNull(AppIcons.class.getResource("icons/oldgnome2/" + name + ".png"), name);
            icons.put(name, new ImageIcon(resource));
        }
        return Map.copyOf(icons);
    }
}
