package dev.jasper.ui.icons;

import dev.jasper.ui.theme.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.Icon;

/** Named, monochrome vector art. Registrations are scoped; returned icons are immutable theme snapshots. */
public final class IconRegistry implements AutoCloseable {
    private final Map<String, IconArtwork> artworks = new LinkedHashMap<>();
    private boolean closed;
    public IconRegistry() { builtIns().forEach((name, path) -> artworks.put(name, new IconArtwork(path))); }
    public synchronized Set<String> names() { requireOpen(); return Set.copyOf(artworks.keySet()); }
    public synchronized IconRegistration register(String name, String pathData) {
        requireOpen(); Objects.requireNonNull(name);
        if (!name.matches("[a-z][a-z0-9.-]*:[a-z][a-z0-9.-]*")) throw new IllegalArgumentException("Icon name must be namespace:name");
        if (artworks.containsKey(name)) throw new IllegalArgumentException("Icon already registered: " + name);
        var artwork = new IconArtwork(pathData); artworks.put(name, artwork);
        return new IconRegistration(() -> { synchronized (this) { artworks.remove(name, artwork); } });
    }
    public Icon icon(String name, SemanticPalette palette, Nodes.Hue hue, int logicalSize) {
        return painted(name, tint(palette, hue), null, logicalSize, false);
    }
    /** Explicit semantic text tint for hover, accent fills and status indications. */
    public Icon icon(String name, SemanticPalette palette, SemanticColor tint, int logicalSize) {
        return painted(name, palette.color(tint), null, logicalSize, false);
    }
    /** Category tile. Selected treatment is shared across list/menu renderers, applied to base alpha exactly once. */
    public Icon tile(String name, SemanticPalette palette, Nodes.Hue hue, int logicalSize, boolean selected) {
        Color foreground = selected ? palette.color(SemanticColor.TEXT_ONACCENT) : tint(palette, hue);
        return painted(name, foreground, ColorMath.opacity(foreground, selected ? .18 : .16), logicalSize, true);
    }
    public int size(Nodes.IconSize size) {
        return DesignMetrics.DEFAULT.value(switch (size) {
            case CAPTION -> DesignMetrics.Role.ICON_CAPTION;
            case CONTROL -> DesignMetrics.Role.ICON_CONTROL;
            case RAIL -> DesignMetrics.Role.ICON_RAIL;
            case EMPTY -> DesignMetrics.Role.ICON_EMPTY;
            case HERO -> DesignMetrics.Role.ICON_HERO;
        });
    }
    private synchronized Icon painted(String name, Color foreground, Color background, int size, boolean tile) {
        requireOpen(); Objects.requireNonNull(name);
        if (size < 1 || size > 512) throw new IllegalArgumentException("Invalid logical icon size");
        var artwork = artworks.get(name);
        if (artwork == null) throw new IllegalArgumentException("Unknown icon: " + name);
        var path = artwork.path();
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
                var g = (Graphics2D)graphics.create();
                try {
                    g.translate(x, y); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (tile) {
                        g.setColor(background); double radius = size * .25;
                        g.fill(new RoundRectangle2D.Double(0, 0, size, size, radius, radius));
                        double inset = size * .2; g.translate(inset, inset); g.scale(size * .6 / 16, size * .6 / 16);
                    } else g.scale(size / 16.0, size / 16.0);
                    g.setColor(foreground); g.setStroke(new BasicStroke(artwork.stroke(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(path);
                } finally { g.dispose(); }
            }
        };
    }
    private static Color tint(SemanticPalette palette, Nodes.Hue hue) {
        return palette.color(switch (Objects.requireNonNull(hue)) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN;
            case AMBER -> SemanticColor.HUE_AMBER; case PURPLE -> SemanticColor.HUE_PURPLE;
            case NEUTRAL -> SemanticColor.HUE_NEUTRAL; case NONE -> SemanticColor.TEXT_SECONDARY;
        });
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Icon registry is closed"); }
    @Override public synchronized void close() { if (!closed) { closed = true; artworks.clear(); } }

    /** Original Jasper outline paths, authored for this kit; no third-party artwork is copied. */
    private static Map<String, String> builtIns() {
        return Map.ofEntries(
            Map.entry("add", "M 8 3 V 13 M 3 8 H 13"),
            Map.entry("more", "M 3 8 H 3.1 M 8 8 H 8.1 M 13 8 H 13.1"),
            Map.entry("collapse", "M 4 6 L 8 10 L 12 6"),
            Map.entry("chevron.down", "M 4 6 L 8 10 L 12 6"),
            Map.entry("chevron.up", "M 4 10 L 8 6 L 12 10"),
            Map.entry("search", "M 11 7 A 4 4 0 1 1 3 7 A 4 4 0 1 1 11 7 M 10 10 L 14 14"),
            Map.entry("lock", "M 5 7 V 5 A 3 3 0 0 1 11 5 V 7 M 3 7 H 13 V 14 H 3 Z M 8 10 V 11"),
            Map.entry("edit", "M 3 10 L 10 3 L 13 6 L 6 13 L 2 14 Z M 9 4 L 12 7"),
            Map.entry("delete", "M 2 4 H 14 M 6 4 V 2 H 10 V 4 M 4 4 L 5 14 H 11 L 12 4 M 7 7 V 11 M 9 7 V 11"),
            Map.entry("copy", "M 6 5 H 14 V 14 H 6 Z M 10 5 V 2 H 2 V 11 H 6"),
            Map.entry("reveal", "M 1 8 Q 8 0 15 8 Q 8 16 1 8 Z M 10 8 A 2 2 0 1 1 6 8 A 2 2 0 1 1 10 8"),
            Map.entry("refresh", "M 13 5 A 5 5 0 1 0 13 11 M 13 2 V 5 H 10"),
            Map.entry("upload", "M 8 11 V 2 M 4 6 L 8 2 L 12 6 M 2 10 V 14 H 14 V 10"),
            Map.entry("back", "M 10 3 L 5 8 L 10 13 M 5 8 H 14"),
            Map.entry("forward", "M 6 3 L 11 8 L 6 13 M 2 8 H 11"),
            Map.entry("folder", "M 2 4 H 6 L 8 6 H 14 V 13 H 2 Z"),
            Map.entry("file", "M 4 2 H 9 L 13 6 V 14 H 4 Z M 9 2 V 6 H 13"),
            Map.entry("server", "M 2 2 H 14 V 7 H 2 Z M 2 9 H 14 V 14 H 2 Z M 5 4.5 H 5.1 M 5 11.5 H 5.1 M 9 4.5 H 11 M 9 11.5 H 11"),
            Map.entry("key", "M 8 5 A 3 3 0 1 1 2 5 A 3 3 0 1 1 8 5 M 7 7 L 13 13 M 11 11 L 13 9 M 13 13 L 15 11"),
            Map.entry("login", "M 10 2 H 14 V 14 H 10 M 2 8 H 10 M 7 5 L 10 8 L 7 11"),
            Map.entry("note", "M 3 2 H 13 V 11 L 10 14 H 3 Z M 10 14 V 11 H 13 M 6 5 H 10 M 6 8 H 10"),
            Map.entry("tunnel", "M 2 13 V 8 A 6 6 0 0 1 14 8 V 13 M 5 13 V 8 A 3 3 0 0 1 11 8 V 13 M 8 10 V 14"),
            Map.entry("terminal", "M 2 3 H 14 V 13 H 2 Z M 4 6 L 7 8 L 4 10 M 9 10 H 12"),
            Map.entry("play", "M 5 2 L 14 8 L 5 14 Z"),
            Map.entry("warning", "M 8 2 L 15 14 H 1 Z M 8 6 V 9 M 8 12 H 8.1"),
            Map.entry("info", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 8 7 V 11 M 8 4.5 H 8.1"),
            Map.entry("check", "M 3 8 L 6 11 L 13 4"),
            Map.entry("close", "M 4 4 L 12 12 M 12 4 L 4 12"),
            Map.entry("sparkle", "M 8 1 L 10 6 L 15 8 L 10 10 L 8 15 L 6 10 L 1 8 L 6 6 Z")
        );
    }
}
