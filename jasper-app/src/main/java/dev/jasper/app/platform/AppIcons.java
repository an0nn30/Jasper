package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import javax.swing.UIManager;

/** Bundled Tabler icons; no network access is needed to render chrome. */
public final class AppIcons {
    private AppIcons() {}
    public static FlatSVGIcon icon(String name) {
        if (!java.util.Set.of("square-plus", "app-window", "columns-2", "maximize", "search", "settings", "refresh", "command", "history", "bookmark").contains(name))
            throw new IllegalArgumentException("Unknown application icon: " + name);
        FlatSVGIcon icon = new FlatSVGIcon("dev/jasper/app/icons/" + name + ".svg", 16, 16);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed("Jasper.chromeForeground", source)));
    }

    /**
     * A chrome-sized icon from an SVG that another class loader holds, recolored like the bundled icons.
     * The color is read from the look and feel at paint time, so the icon follows theme changes.
     */
    public static javax.swing.Icon themed(ClassLoader loader, String svgResourcePath) {
        java.util.Objects.requireNonNull(loader, "loader");
        if (svgResourcePath == null || loader.getResource(svgResourcePath) == null)
            throw new IllegalArgumentException("No such icon resource: " + svgResourcePath);
        FlatSVGIcon icon = new FlatSVGIcon(svgResourcePath, 16, 16, loader);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed("Jasper.chromeForeground", source)));
    }

    private static Color themed(String key, Color source) {
        Color target = UIManager.getColor(key);
        if (target == null) return source;
        return new Color(target.getRed(), target.getGreen(), target.getBlue(), source.getAlpha());
    }
}
