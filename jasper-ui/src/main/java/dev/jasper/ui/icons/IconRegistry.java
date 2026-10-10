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
    // Fixed immutable engine artwork is parsed once; each receiving view still owns its custom registrations.
    private static final Map<String, IconArtwork> BUILT_INS = builtIns().entrySet().stream().collect(
        java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> new IconArtwork(entry.getValue())));
    private final Map<String, IconArtwork> artworks = new LinkedHashMap<>();
    private boolean closed;
    public IconRegistry() { artworks.putAll(BUILT_INS); }
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
    /** Hero glyphs retain a 1.75-logical-pixel optical stroke at the canonical 24px size. */
    public Icon hero(String name, SemanticPalette palette, Nodes.Hue hue, int logicalSize) {
        return painted(name, tint(palette, hue), null, logicalSize, false, true);
    }
    /** Explicit semantic text tint for hover, accent fills and status indications. */
    public Icon icon(String name, SemanticPalette palette, SemanticColor tint, int logicalSize) {
        return painted(name, palette.color(tint), null, logicalSize, false);
    }
    /**
     * Artwork on a surface outside the semantic palette (for example an independently coloured terminal surface):
     * the surface's own foreground, moved toward black or white until it reaches the 3:1 non-text contrast floor.
     */
    public Icon icon(String name, Color surface, Color foreground, int logicalSize) {
        return painted(name, surfaceTint(surface, foreground), null, logicalSize, false);
    }
    /** The tint {@link #icon(String, Color, Color, int)} paints; one module rule for surface-tinted artwork. */
    public static Color surfaceTint(Color surface, Color foreground) {
        Objects.requireNonNull(surface); Objects.requireNonNull(foreground);
        Color opaque = new Color(foreground.getRGB() & 0xffffff);
        return ColorMath.contrast(opaque, surface) >= SurfaceContrast.INDICATOR ? opaque
            : SurfaceContrast.readable(opaque, surface, SurfaceContrast.INDICATOR, SurfaceContrast.blackOrWhite(surface));
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
    private Icon painted(String name, Color foreground, Color background, int size, boolean tile) {
        return painted(name, foreground, background, size, tile, false);
    }
    private synchronized Icon painted(String name, Color foreground, Color background, int size, boolean tile, boolean hero) {
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
                        g.setColor(background); double radius = size * .25 * 2; // RoundRectangle2D accepts an arc diameter
                        g.fill(new RoundRectangle2D.Double(0, 0, size, size, radius, radius));
                        double inset = size * .2; g.translate(inset, inset); g.scale(size * .6 / 16, size * .6 / 16);
                    } else g.scale(size / 16.0, size / 16.0);
                    g.setColor(foreground); g.setStroke(new BasicStroke(hero ? 7f / 6f : artwork.stroke(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
            Map.entry("bookmark", "M 4 2 H 12 V 14 L 8 11 L 4 14 Z"),
            Map.entry("home", "M 2 8 L 8 2 L 14 8 M 4 6.5 V 14 H 12 V 6.5 M 7 14 V 10 H 9 V 14"),
            Map.entry("desktop", "M 2 3 H 14 V 11 H 2 Z M 6 14 H 10 M 8 11 V 14"),
            Map.entry("preview", "M 1 8 Q 8 1 15 8 Q 8 15 1 8 Z M 10 8 A 2 2 0 1 1 6 8 A 2 2 0 1 1 10 8"),
            Map.entry("logout", "M 6 2 H 2 V 14 H 6 M 6 8 H 14 M 11 5 L 14 8 L 11 11"),
            Map.entry("download", "M 8 2 V 11 M 4 7 L 8 11 L 12 7 M 2 10 V 14 H 14 V 10"),
            Map.entry("help", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 6 5 Q 10 3 10 6 L 8 8 V 9 M 8 12 H 8.1"),
            Map.entry("history", "M 3 4 A 6 6 0 1 1 2 9 M 3 1 V 5 H 7 M 8 4 V 8 L 11 10"),
            Map.entry("link", "M 6 10 L 10 6 M 5 8 L 3 10 A 2 2 0 0 0 6 13 L 8 11 M 8 5 L 10 3 A 2 2 0 0 1 13 6 L 11 8"),
            Map.entry("match.case", "M 2 12 L 5 3 L 8 12 M 3 9 H 7 M 14 8 Q 10 6 10 10 Q 10 13 14 11 V 7"),
            Map.entry("folder.add", "M 2 4 H 6 L 8 6 H 14 V 13 H 2 Z M 8 8 V 12 M 6 10 H 10"),
            Map.entry("window.add", "M 2 3 H 14 V 13 H 2 Z M 2 6 H 14 M 8 8 V 12 M 6 10 H 10"),
            Map.entry("paste", "M 6 3 H 3 V 14 H 13 V 3 H 10 M 6 2 H 10 V 5 H 6 Z"),
            Map.entry("pause", "M 4 3 H 6 V 13 H 4 Z M 10 3 H 12 V 13 H 10 Z"),
            Map.entry("regex", "M 3 12 H 3.1 M 10 3 V 11 M 6 5 L 14 9 M 6 9 L 14 5"),
            Map.entry("remove", "M 3 8 H 13"),
            Map.entry("save", "M 2 2 H 12 L 14 4 V 14 H 2 Z M 5 2 V 6 H 11 V 2 M 5 14 V 9 H 11 V 14"),
            Map.entry("settings", "M 2 4 H 14 M 2 8 H 14 M 2 12 H 14 M 5 2 V 6 M 11 6 V 10 M 7 10 V 14"),
            Map.entry("split", "M 2 2 H 14 V 14 H 2 Z M 8 2 V 14"),
            Map.entry("unlock", "M 6 7 V 5 A 3 3 0 0 1 12 5 M 3 7 H 13 V 14 H 3 Z M 8 10 V 11"),
            Map.entry("zoom", "M 2 6 V 2 H 6 M 10 2 H 14 V 6 M 14 10 V 14 H 10 M 6 14 H 2 V 10"),
            Map.entry("add", "M 8 3 V 13 M 3 8 H 13"),
            Map.entry("more", "M 3 8 H 3.1 M 8 8 H 8.1 M 13 8 H 13.1"),
            Map.entry("menu", "M 3 4 H 13 M 3 8 H 13 M 3 12 H 13"),
            Map.entry("collapse", "M 4 6 L 8 10 L 12 6"),
            Map.entry("chevron.down", "M 4 6 L 8 10 L 12 6"),
            Map.entry("chevron.right", "M 6 4 L 10 8 L 6 12"),
            Map.entry("chevron.up", "M 4 10 L 8 6 L 12 10"),
            Map.entry("search", "M 11 7 A 4 4 0 1 1 3 7 A 4 4 0 1 1 11 7 M 10 10 L 14 14"),
            Map.entry("shield", "M 8 1.333 L 12.85 3.667 V 7.5 C 12.85 11 11 13.333 8 14.333 C 5 13.333 3.15 11 3.15 7.5 V 3.667 Z M 6.5 7 H 9.5 Q 10 7 10 7.5 V 10.5 Q 10 11 9.5 11 H 6.5 Q 6 11 6 10.5 V 7.5 Q 6 7 6.5 7 M 7 7 V 6 A 1 1 0 0 1 9 6 V 7"),
            Map.entry("lock", "M 5 7 V 5 A 3 3 0 0 1 11 5 V 7 M 4.25 7 H 11.75 Q 12.85 7 12.85 8.25 V 12.75 Q 12.85 14 11.75 14 H 4.25 Q 3.15 14 3.15 12.75 V 8.25 Q 3.15 7 4.25 7 M 8 10.5 H 8.05"),
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
            Map.entry("bell", "M 3 11 L 4 9 V 6 A 4 4 0 0 1 12 6 V 9 L 13 11 Z M 6 13 A 2 2 0 0 0 10 13"),
            Map.entry("info", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 8 7 V 11 M 8 4.5 H 8.1"),
            Map.entry("error", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 6 6 L 10 10 M 10 6 L 6 10"),
            Map.entry("plugin", "M 6 2 V 5 M 10 2 V 5 M 4 5 H 12 V 8 A 4 4 0 0 1 4 8 Z M 8 12 V 14"),
            Map.entry("check", "M 3 8 L 6 11 L 13 4"),
            Map.entry("close", "M 4 4 L 12 12 M 12 4 L 4 12"),
            Map.entry("sparkle", "M 8 1 L 10 6 L 15 8 L 10 10 L 8 15 L 6 10 L 1 8 L 6 6 Z"),
            // A favourite: the outline star, and the filled star (outline, an inner star and spokes to the tips) for a chosen one.
            Map.entry("star", "M 8 2 L 9.6 6.3 L 14.3 6.6 L 10.7 9.5 L 11.9 13.9 L 8 11.4 L 4.1 13.9 L 5.3 9.5 L 1.7 6.6 L 6.4 6.3 Z"),
            Map.entry("star.filled", "M 8 2 L 9.6 6.3 L 14.3 6.6 L 10.7 9.5 L 11.9 13.9 L 8 11.4 L 4.1 13.9 L 5.3 9.5 L 1.7 6.6 L 6.4 6.3 Z "
                + "M 8 5 L 8.9 7.4 L 11.4 7.5 L 9.4 9.1 L 10.1 11.5 L 8 10.1 L 5.9 11.5 L 6.6 9.1 L 4.6 7.5 L 7.1 7.4 Z "
                + "M 8 8.6 L 8 3.6 M 8 8.6 L 12.8 7 M 8 8.6 L 11 12.6 M 8 8.6 L 5 12.6 M 8 8.6 L 3.2 7")
        );
    }
}
