package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.FlatLaf.DisabledIconProvider;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Central, offline icon manager. Bundled New UI SVGs retain their authored colours and
 * automatically select upstream dark siblings when the live look and feel changes.
 */
public final class AppIcons {
    private static final String BASE = "dev/jasper/app/icons/intellij/";
    private static final Set<String> CATALOG = readCatalog();
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> CHROME = Map.ofEntries(
        Map.entry("square-plus", "ADD"), Map.entry("app-window", "NEW_WINDOW"),
        Map.entry("columns-2", "SPLIT"), Map.entry("maximize", "ZOOM"),
        Map.entry("search", "SEARCH"), Map.entry("settings", "SETTINGS"), Map.entry("refresh", "REFRESH"),
        Map.entry("command", "EXECUTE"), Map.entry("history", "HISTORY"), Map.entry("bookmark", "BOOKMARK"),
        Map.entry("close", "CLOSE"), Map.entry("exit", "EXIT"));
    private static final Icon KEY = new FlatSVGIcon("dev/jasper/app/icons/standard/KEY.svg", 16, 16)
        .setColorFilter(new FlatSVGIcon.ColorFilter(source -> {
            Color color = UIManager.getColor("Jasper.chromeForeground");
            return color == null ? source : new Color(color.getRed(), color.getGreen(), color.getBlue(), source.getAlpha());
        }));
    private static final Icon DROPDOWN = new FittedIcon(loadSvg(NamedIcons.id("DROPDOWN")), 8);
    private AppIcons() { }

    /** Compact dropdown affordance for existing toolbar spacing. */
    public static Icon dropdown() { return DROPDOWN; }

    /** A 16-pixel application chrome icon by Jasper name. */
    public static Icon icon(String name) {
        String meaning = CHROME.get(name);
        if (meaning == null) throw new IllegalArgumentException("Unknown application icon: " + name);
        return named(meaning);
    }

    /** Host-owned semantic artwork, independent of any plugin's resource loader. */
    public static Icon named(String name) {
        String id = NamedIcons.id(name);
        return id.equals("standard/KEY") ? KEY : bundled(id);
    }

    /** All supported catalog IDs, including explicit size choices but excluding automatic dark siblings. */
    public static Set<String> bundledIds() { return CATALOG; }

    /** A catalog choice. Only IDs in the generated index are accepted, never arbitrary resource paths. */
    public static Icon bundled(String id) {
        if (id == null || !CATALOG.contains(id)) throw new IllegalArgumentException("Unknown bundled icon: " + id);
        return CACHE.computeIfAbsent(id, key -> {
            FlatSVGIcon svg = loadSvg(key);
            return new FittedIcon(svg, 16);
        });
    }

    /** A plugin's 16-pixel SVG drawn as authored, using only its own resource loader. */
    public static Icon plugin(ClassLoader loader, String svgResourcePath) {
        Objects.requireNonNull(loader, "loader");
        if (svgResourcePath == null || loader.getResource(svgResourcePath) == null)
            throw new IllegalArgumentException("No such icon resource: " + svgResourcePath);
        return new FlatSVGIcon(svgResourcePath, 16, 16, loader);
    }

    private static FlatSVGIcon loadSvg(String id) {
        var svg = new FlatSVGIcon(BASE + id + ".svg", AppIcons.class.getClassLoader());
        if (!svg.hasFound()) throw new IllegalStateException("Invalid bundled icon: " + id);
        return svg;
    }

    private static Set<String> readCatalog() {
        try (var input = AppIcons.class.getClassLoader().getResourceAsStream(BASE + "catalog.txt")) {
            if (input == null) throw new IllegalStateException("Missing bundled icon catalog");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().collect(Collectors.toUnmodifiableSet());
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }

    /** Preserve non-square catalog artwork within the same square box as ordinary controls. */
    private record FittedIcon(Icon source, int size) implements Icon, DisabledIconProvider {
        @Override public Icon getDisabledIcon() {
            return new FittedIcon(((DisabledIconProvider) source).getDisabledIcon(), size);
        }
        @Override public int getIconWidth() { return UIScale.scale(size); }
        @Override public int getIconHeight() { return UIScale.scale(size); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                double scale = (double) getIconWidth() / Math.max(source.getIconWidth(), source.getIconHeight());
                copy.translate(x + (getIconWidth() - scale * source.getIconWidth()) / 2,
                    y + (getIconHeight() - scale * source.getIconHeight()) / 2);
                copy.scale(scale, scale);
                source.paintIcon(component, copy, 0, 0);
            } finally { copy.dispose(); }
        }
    }
}
