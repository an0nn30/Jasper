package dev.moray.app;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import javax.swing.UIManager;

/** Bundled Tabler icons; no network access is needed to render chrome. */
final class AppIcons {
    private AppIcons() {}
    static FlatSVGIcon icon(String name) {
        String colorKey = switch (name) {
            case "square-plus" -> "Moray.icon.newTab";
            case "app-window" -> "Moray.icon.newWindow";
            case "columns-2" -> "Moray.icon.split";
            case "maximize" -> "Moray.icon.zoom";
            case "search" -> "Moray.icon.find";
            case "settings" -> "Moray.icon.settings";
            case "refresh" -> "Moray.icon.reload";
            default -> throw new IllegalArgumentException("Unknown application icon: " + name);
        };
        FlatSVGIcon icon = new FlatSVGIcon("dev/moray/app/icons/" + name + ".svg", 28, 28);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed(colorKey, source)));
    }

    private static Color themed(String key, Color source) {
        Color target = UIManager.getColor(key);
        if (target == null) return source;
        return new Color(target.getRed(), target.getGreen(), target.getBlue(), source.getAlpha());
    }
}
