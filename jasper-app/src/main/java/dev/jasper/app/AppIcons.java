package dev.jasper.app;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import javax.swing.UIManager;

/** Bundled Tabler icons; no network access is needed to render chrome. */
final class AppIcons {
    private AppIcons() {}
    static FlatSVGIcon icon(String name) {
        if (!java.util.Set.of("square-plus", "app-window", "columns-2", "maximize", "search", "settings", "refresh", "command", "history", "bookmark").contains(name))
            throw new IllegalArgumentException("Unknown application icon: " + name);
        FlatSVGIcon icon = new FlatSVGIcon("dev/jasper/app/icons/" + name + ".svg", 16, 16);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed("Jasper.chromeForeground", source)));
    }

    private static Color themed(String key, Color source) {
        Color target = UIManager.getColor(key);
        if (target == null) return source;
        return new Color(target.getRed(), target.getGreen(), target.getBlue(), source.getAlpha());
    }
}
