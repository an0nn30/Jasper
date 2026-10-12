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

/** Named vectors and authored RGBA artwork. Registrations are scoped; returned icons are immutable snapshots. */
public final class IconRegistry implements AutoCloseable {
    // Fixed immutable engine artwork is parsed once; each receiving view still owns its custom registrations.
    private static final Map<String, IconArtwork> BUILT_INS = builtIns().entrySet().stream().collect(
        java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> new IconArtwork(entry.getValue())));
    // Only explicitly authored built-ins opt into silhouette fill. Public registrations retain
    // the validated outline stroke contract; a name suffix does not grant filled painting.
    private static final Set<String> FILLED_BUILT_INS = Set.of("folder.filled");
    // Original compact-panel sources establish lighter plus/search/pencil/import outlines. Other built-ins
    // retain the common optical stroke; custom registrations never enter this catalog.
    private static final Set<String> FINE_PANEL_BUILT_INS = Set.of("add", "search", "edit", "import");
    /** Panel-context overflow dots are drawn two logical pixels across, as every plugin-panel reference header shows. */
    private static final Set<String> DOT_PANEL_BUILT_INS = Set.of("more");
    private final Map<String, Received> encoded = new LinkedHashMap<>();
    private final Map<String, IconArtwork> artworks = new LinkedHashMap<>();
    private record Received(DecodedArtwork artwork, Nodes.Hue tileHue, IdentityArtwork.SelectedInk selectedInk) { }
    private boolean closed;
    public IconRegistry() { prepareBuiltIns(); artworks.putAll(BUILT_INS); }
    /** Optional off-EDT production prewarm; standalone construction remains self-contained. */
    public static void prepareBuiltIns() { BuiltInArtwork.catalog(); BuiltInArtwork.chrome(); }
    public synchronized Set<String> names() { requireOpen(); var names = new java.util.HashSet<>(artworks.keySet()); names.addAll(encoded.keySet()); names.addAll(BuiltInArtwork.catalog().keySet()); return Set.copyOf(names); }
    public synchronized IconRegistration register(String name, String pathData) {
        requireOpen(); Objects.requireNonNull(name);
        if (!name.matches("[a-z][a-z0-9.-]*:[a-z][a-z0-9.-]*")) throw new IllegalArgumentException("Icon name must be namespace:name");
        if (artworks.containsKey(name) || encoded.containsKey(name)) throw new IllegalArgumentException("Icon already registered: " + name);
        var artwork = new IconArtwork(pathData); artworks.put(name, artwork);
        return new IconRegistration(() -> { synchronized (this) { artworks.remove(name, artwork); } });
    }
    /** Registers authored color artwork atomically; decoding occurs before publication. */
    public synchronized IconRegistration register(String name, EncodedArtwork artwork) {
        return register(name, artwork, null, IdentityArtwork.SelectedInk.AUTHORED);
    }
    /** Registers immutable identity metadata; declared hue takes precedence only in tiles. */
    public synchronized IconRegistration register(String name, IdentityArtwork identity) {
        Objects.requireNonNull(identity);
        return register(name, identity.artwork(), identity.tileHue(), identity.selectedInk());
    }
    private IconRegistration register(String name, EncodedArtwork artwork, Nodes.Hue hue, IdentityArtwork.SelectedInk selectedInk) {
        requireOpen(); Objects.requireNonNull(name); Objects.requireNonNull(artwork);
        if (!name.matches("[a-z][a-z0-9.-]*:[a-z][a-z0-9.-]*")) throw new IllegalArgumentException("Icon name must be namespace:name");
        if (artworks.containsKey(name) || encoded.containsKey(name)) throw new IllegalArgumentException("Icon already registered: " + name);
        var decoded = new Received(artwork.decoded(), hue, selectedInk); encoded.put(name, decoded);
        return new IconRegistration(() -> { synchronized (this) { encoded.remove(name, decoded); } });
    }
    /** Authored artwork ignores tint; callers may reuse its prepared Icon on hover. */
    public synchronized boolean authored(String name) { return authored(name,false); }
    /** Whether this ordinary/compact context retains authored ink instead of re-tinting on hover. */
    public synchronized boolean authored(String name, boolean compact) {
        return encoded.containsKey(name) || (compact ? BuiltInArtwork.IDENTITIES.contains(name) : BuiltInArtwork.catalog().containsKey(name));
    }
    public Icon icon(String name, SemanticPalette palette, Nodes.Hue hue, int logicalSize) {
        return ordinary(name, null, palette, tint(palette,hue), logicalSize);
    }
    /** Ordinary authored builtins select their sibling from the actual receiving theme. */
    public Icon icon(String name, GeneratedTheme theme, Nodes.Hue hue, int logicalSize) {
        return ordinary(name, theme, theme.semantic(), tint(theme.semantic(),hue), logicalSize);
    }
    /** Explicit receiving theme and semantic ink; authored pixels retain their own colors. */
    public Icon icon(String name, GeneratedTheme theme, SemanticColor tint, int logicalSize) {
        return ordinary(name, theme, theme.semantic(), theme.semantic().color(tint), logicalSize);
    }
    private Icon ordinary(String name, GeneratedTheme theme, SemanticPalette palette, Color foreground, int size) {
        return painted(name,foreground,null,size,size,false,false,0,false,select(name,theme,palette,true),false);
    }
    private DecodedArtwork select(String name, GeneratedTheme theme, SemanticPalette palette, boolean ordinary) {
        var pair=BuiltInArtwork.catalog().get(name);
        // Palette-only compatibility admits identity meanings, preserving the legacy settings/chevron contexts.
        if(pair==null || (!BuiltInArtwork.IDENTITIES.contains(name) && (!ordinary || theme==null)))return null;
        boolean dark=theme!=null ? theme.dark() : ColorMath.luminance(palette.color(SemanticColor.SURFACE_PANEL))<.5;
        return pair.select(dark);
    }
    /** Prepared native receiving snapshot. Both siblings are retained independently of the decoder size cache. */
    public PreparedIcon themedIcon(String name, int logicalSize) { return prepared(name, logicalSize, false); }
    /**
     * As {@link #themedIcon}, for the native workspace chrome's own receiving contexts (title bar, toolbar, tool window
     * stripes and status bar): the meanings that chrome depicts use their original desktop artwork. Every other name, and
     * every descriptor, panel, tile or plugin-registered context, is unchanged.
     */
    public PreparedIcon chromeIcon(String name, int logicalSize) { return prepared(name, logicalSize, true); }
    private PreparedIcon prepared(String name, int logicalSize, boolean chrome) {
        synchronized(this) {
            requireOpen();
            if(!names().contains(name))throw new IllegalArgumentException("Unknown icon: "+name);
            // Chrome originals are keyed by built-in meanings; namespaced registrations can never collide with them.
            var pair=chrome && BuiltInArtwork.chrome().containsKey(name) ? BuiltInArtwork.chrome().get(name) : BuiltInArtwork.catalog().get(name);
            var path=artworks.get(name);
            var registration=encoded.get(name);
            var received=registration==null?null:registration.artwork();
            Icon light=pair==null && received==null?null:painted(name,Color.BLACK,null,logicalSize,logicalSize,false,false,0,false,pair==null?received:pair.light(),false);
            Icon dark=pair==null?light:painted(name,Color.BLACK,null,logicalSize,logicalSize,false,false,0,false,pair.dark(),false);
            return new PreparedIcon(name,logicalSize,path,light,dark);
        }
    }
    /** Immutable native preparation; resolving live ink and appearance never builds a raster or cache. */
    public static final class PreparedIcon {
        private final Icon light, dark;
        private final IconArtwork path;
        private final java.awt.Shape preparedPath;
        private final String name;
        private final int size;
        private PreparedIcon(String name,int size,IconArtwork path,Icon light,Icon dark) {
            if(size<1 || size>512)throw new IllegalArgumentException("Invalid logical icon size");
            this.name=name;this.path=path;this.size=size;this.preparedPath=path==null?null:path.path();
            this.light=light;this.dark=dark;
        }
        /** Resolves the actual receiving theme. Does no lookup, decoding, size caching or raster allocation. */
        public Icon icon(GeneratedTheme theme, SemanticColor tint) {
            Objects.requireNonNull(theme);Objects.requireNonNull(tint);
            if(light!=null)return theme.dark()?dark:light;
            // Custom/native paths remain color-free; paint the already-parsed immutable path with live semantic ink.
            Color color=theme.semantic().color(tint);
            return new Icon() {
                public int getIconWidth(){return size;} public int getIconHeight(){return size;}
                public void paintIcon(Component c,Graphics graphics,int x,int y){
                    var g=(Graphics2D)graphics.create();
                    try{g.translate(x,y);g.scale(size/16.0,size/16.0);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(color);g.setStroke(new BasicStroke(path.stroke(),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                        if(FILLED_BUILT_INS.contains(name))g.fill(preparedPath);else g.draw(preparedPath);
                    }finally{g.dispose();}
                }
            };
        }
    }
    /** Hero glyphs retain a 1.75-logical-pixel optical stroke at the canonical 24px size. */
    public Icon hero(String name, SemanticPalette palette, Nodes.Hue hue, int logicalSize) {
        return painted(name,tint(palette,hue),null,logicalSize,logicalSize,false,true,0,false,select(name,null,palette,false),false);
    }
    /** Compact panel artwork uses a lighter optical stroke for canonical built-ins only.
     * Custom registrations retain their validated authored stroke.
     */
    public synchronized Icon panel(String name, SemanticPalette palette, SemanticColor tint, int logicalSize) {
        return painted(name, palette.color(tint), null, logicalSize, logicalSize, false, false, 0, artworks.get(name) == BUILT_INS.get(name),select(name,null,palette,false),false);
    }
    /** Explicit semantic text tint for hover, accent fills and status indications. */
    public Icon icon(String name, SemanticPalette palette, SemanticColor tint, int logicalSize) {
        return ordinary(name, null, palette, palette.color(tint), logicalSize);
    }
    /**
     * Artwork on a surface outside the semantic palette (for example an independently coloured terminal surface):
     * the surface's own foreground, moved toward black or white until it reaches the 3:1 non-text contrast floor.
     */
    public Icon icon(String name, Color surface, Color foreground, int logicalSize) {
        // Existing explicit surface routes retain their path/tint contract. New catalog-only meanings remain usable.
        var pair=artworks.containsKey(name)?null:BuiltInArtwork.catalog().get(name);
        return painted(name,surfaceTint(surface,foreground),null,logicalSize,logicalSize,false,false,0,false,
            pair==null?null:pair.select(ColorMath.luminance(surface)<.5),false);
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
        return tile(name, palette, hue, logicalSize, logicalSize, selected);
    }
    /** Rectangular category tile; glyph remains optically centered and unstretched. */
    public Icon tile(String name, SemanticPalette palette, Nodes.Hue hue, int width, int height, boolean selected) {
        return tile(name, palette, hue, width, height, 0, selected);
    }
    /** Category tile with explicit glyph extent; zero retains the standard optical sizing policy. */
    public Icon tile(String name, SemanticPalette palette, Nodes.Hue hue, int width, int height, int glyphSize, boolean selected) {
        return tile(name,palette,null,hue,width,height,glyphSize,selected);
    }
    /** Identity tiles select the actual receiving-theme sibling; legacy settings/group geometry is preserved. */
    public Icon tile(String name, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize, boolean selected) {
        return tile(name,theme.semantic(),theme,hue,width,height,glyphSize,selected);
    }
    /** Receiving identity header: normal authored ink with the shared inward semantic contour. */
    public Icon identityHeader(String name, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize) {
        return tile(name,theme.semantic(),theme,hue,width,height,glyphSize,false,theme.semantic().color(SemanticColor.BORDER_IDENTITY));
    }
    private Icon tile(String name, SemanticPalette palette, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize, boolean selected) {
        return tile(name,palette,theme,hue,width,height,glyphSize,selected,null);
    }
    private Icon tile(String name, SemanticPalette palette, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize, boolean selected, Color outline) {
        if (glyphSize < 0 || glyphSize > Math.min(width, height)) throw new IllegalArgumentException("Invalid tile glyph size");
        boolean selectedForeground;
        synchronized (this) {
            var identity = encoded.get(name);
            if (identity != null && identity.tileHue() != null) hue = identity.tileHue();
            selectedForeground = selected && identity != null && identity.selectedInk() == IdentityArtwork.SelectedInk.FOREGROUND;
        }
        Color foreground = selected ? palette.color(SemanticColor.TEXT_ONACCENT) : tint(palette, hue);
        Color fill = selected ? foreground : palette.color(switch(hue) {
            case BLUE -> SemanticColor.HUE_BLUE_SURFACE; case GREEN -> SemanticColor.HUE_GREEN_SURFACE;
            case AMBER -> SemanticColor.HUE_AMBER_SURFACE; case PURPLE -> SemanticColor.HUE_PURPLE_SURFACE;
            case NEUTRAL -> SemanticColor.HUE_NEUTRAL_SURFACE; case NONE -> SemanticColor.TEXT_SECONDARY;
        });
        // A palette-only tile has no theme: decide the variant as ordinary(...) does, from the panel surface.
        boolean darkTile = theme != null ? theme.dark() : ColorMath.luminance(palette.color(SemanticColor.SURFACE_PANEL)) < .5;
        return painted(name, foreground, ColorMath.opacity(fill, DesignMetrics.DEFAULT.value(darkTile ? DesignMetrics.Role.TILE_PERCENT_DARK : DesignMetrics.Role.TILE_PERCENT_LIGHT) / 100.0), width, height, true, false, glyphSize,false,select(name,theme,palette,false),selectedForeground,outline);
    }
    /** Compact context preserves calibrated legacy settings/chevron optics. */
    public Icon panel(String name,GeneratedTheme theme,SemanticColor tint,int size) {
        return painted(name,theme.semantic().color(tint),null,size,size,false,false,0,artworks.get(name)==BUILT_INS.get(name),select(name,theme,theme.semantic(),false),false);
    }
    /** Hero context retains legacy geometry except explicitly admitted identity meanings. */
    public Icon hero(String name,GeneratedTheme theme,Nodes.Hue hue,int size) {
        return painted(name,tint(theme.semantic(),hue),null,size,size,false,true,0,false,select(name,theme,theme.semantic(),false),false);
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
        return painted(name, foreground, background, size, size, tile, hero);
    }
    private synchronized Icon painted(String name, Color foreground, Color background, int width, int height, boolean tile, boolean hero) {
        return painted(name, foreground, background, width, height, tile, hero, 0);
    }
    private synchronized Icon painted(String name, Color foreground, Color background, int width, int height, boolean tile, boolean hero, int glyphSize) {
        return painted(name, foreground, background, width, height, tile, hero, glyphSize, false);
    }
    private synchronized Icon painted(String name, Color foreground, Color background, int width, int height, boolean tile, boolean hero, int glyphSize, boolean panel) {
        return painted(name,foreground,background,width,height,tile,hero,glyphSize,panel,null,false);
    }
    private synchronized Icon painted(String name,Color foreground,Color background,int width,int height,boolean tile,boolean hero,int glyphSize,boolean panel,DecodedArtwork override,boolean selectedForeground) {
        return painted(name,foreground,background,width,height,tile,hero,glyphSize,panel,override,selectedForeground,null);
    }
    private synchronized Icon painted(String name,Color foreground,Color background,int width,int height,boolean tile,boolean hero,int glyphSize,boolean panel,DecodedArtwork override,boolean selectedForeground,Color outline) {
        requireOpen(); Objects.requireNonNull(name);
        int size = Math.min(width, height);
        if (size < 1 || Math.max(width, height) > 512) throw new IllegalArgumentException("Invalid logical icon size");
        var artwork = artworks.get(name);
        var receivedEntry = encoded.get(name);
        var authored = override!=null?override:receivedEntry == null ? null : receivedEntry.artwork();
        if (artwork == null && authored == null) throw new IllegalArgumentException("Unknown icon: " + name);
        var path = artwork == null ? null : artwork.path();
        boolean filled = FILLED_BUILT_INS.contains(name);
        // Callers supply user-scaled tile extents; optical limits are logical metrics.
        double tileGlyph = glyphSize > 0 ? glyphSize : Math.min(size, Math.min(com.formdev.flatlaf.util.UIScale.scale(16.5f),
            Math.max(com.formdev.flatlaf.util.UIScale.scale(10f), size * .4)));
        var snapshot=authored==null?null:authored.snapshot(Math.max(1,(int)Math.round(tile?tileGlyph:size)));
        var received=selectedForeground && snapshot!=null?DecodedArtwork.foreground(snapshot,foreground):snapshot;
        final float outlineWidth=com.formdev.flatlaf.util.UIScale.scale(1f);
        return new Icon() {
            @Override public int getIconWidth() { return width; }
            @Override public int getIconHeight() { return height; }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
                var g = (Graphics2D)graphics.create();
                try {
                    g.translate(x, y); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (tile) {
                        g.setColor(background); double radius = size * .25 * 2; // RoundRectangle2D accepts an arc diameter
                        g.fill(new RoundRectangle2D.Double(0, 0, width, height, radius, radius));
                        if(outline!=null) {
                            double inset=outlineWidth/2.0;var border=(Graphics2D)g.create();
                            try {border.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);border.setColor(outline);border.setStroke(new BasicStroke(outlineWidth));
                                border.draw(new RoundRectangle2D.Double(inset,inset,width-outlineWidth,height-outlineWidth,Math.max(0,radius-outlineWidth),Math.max(0,radius-outlineWidth)));
                            } finally {border.dispose();}
                        }
                        double glyph = tileGlyph;
                        if (authored == null) { g.translate((width - glyph) / 2, (height - glyph) / 2); g.scale(glyph / 16, glyph / 16); }
                    } else if (authored == null) g.scale(size / 16.0, size / 16.0);
                    if (authored != null) {
                        int w=received.getWidth(null),h=received.getHeight(null);
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g.drawImage(received, (width-w)/2, (height-h)/2, w, h, null);
                        return;
                    }
                    float stroke = panel ? FINE_PANEL_BUILT_INS.contains(name) ? 1f : DOT_PANEL_BUILT_INS.contains(name) ? 2f : 7f / 6f
                        : hero ? 7f / 6f
                        : tile && glyphSize > 0 && artwork == BUILT_INS.get(name) ? 7f / 6f : artwork.stroke();
                    g.setColor(foreground); g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    // The canonical search artwork has a right/down optical bias in the compact slot.
                    // Custom artwork and ordinary field/manager contexts retain their exact path origin.
                    if(panel && name.equals("search")) g.translate(-1, -.5);
                    if (filled) g.fill(path); else g.draw(path);
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
    @Override public synchronized void close() { if (!closed) { closed = true; artworks.clear(); encoded.clear(); } }

    /** Original Jasper vector paths; explicit built-in fill metadata is above. No third-party artwork is copied. */
    private static Map<String, String> builtIns() {
        return Map.ofEntries(
            Map.entry("bookmark", "M 4 2 H 12 V 14 L 8 11 L 4 14 Z"),
            Map.entry("home", "M 2 8 L 8 2 L 14 8 M 4 6.5 V 14 H 12 V 6.5 M 7 14 V 10 H 9 V 14"),
            Map.entry("desktop", "M 2 3 H 14 V 11 H 2 Z M 6 14 H 10 M 8 11 V 14"),
            // A route preview's endpoints and its connecting arrow (Plan 3 Task 6 tunnel editor).
            Map.entry("laptop", "M 3 4 H 13 V 11 H 3 Z M 1.5 13 H 14.5"),
            Map.entry("target", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 11 8 A 3 3 0 1 1 5 8 A 3 3 0 1 1 11 8 M 8 8 H 8.1"),
            Map.entry("arrow.right", "M 1.5 8 H 14.5 M 11 4.5 L 14.5 8 L 11 11.5"),
            Map.entry("preview", "M 1 8 Q 8 1 15 8 Q 8 15 1 8 Z M 10 8 A 2 2 0 1 1 6 8 A 2 2 0 1 1 10 8"),
            Map.entry("logout", "M 6 2 H 2 V 14 H 6 M 6 8 H 14 M 11 5 L 14 8 L 11 11"),
            Map.entry("download", "M 8 2 V 11 M 4 7 L 8 11 L 12 7 M 2 10 V 14 H 14 V 10"),
            // Import: an arrow into a corner bracket, as the reference Sessions header shows (not the download tray).
            Map.entry("import", "M 3.5 4.5 V 13 H 11 M 12.5 3.5 L 7.5 8.5 M 7.5 4.5 V 8.5 H 11"),
            Map.entry("question", "M 6 5 Q 10 3 10 6 L 8 8 V 9 M 8 12 H 8.1"),
            Map.entry("help", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 6 5 Q 10 3 10 6 L 8 8 V 9 M 8 12 H 8.1"),
            Map.entry("history", "M 3 4 A 6 6 0 1 1 2 9 M 3 1 V 5 H 7 M 8 4 V 8 L 11 10"),
            Map.entry("clock", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 8 4.75 V 8 L 10.25 9.5"),
            Map.entry("link", "M 6 10 L 10 6 M 5 8 L 3 10 A 2 2 0 0 0 6 13 L 8 11 M 8 5 L 10 3 A 2 2 0 0 1 13 6 L 11 8"),
            Map.entry("match.case", "M 2 12 L 5 3 L 8 12 M 3 9 H 7 M 14 8 Q 10 6 10 10 Q 10 13 14 11 V 7"),
            // Whole word: a W standing on the word boundary underline.
            Map.entry("match.word", "M 2.5 3 L 4.75 10.5 L 8 5 L 11.25 10.5 L 13.5 3 M 2 13.5 H 14"),
            Map.entry("folder.add", "M 2 4 H 6 L 8 6 H 14 V 13 H 2 Z M 8 8 V 12 M 6 10 H 10"),
            Map.entry("window.add", "M 2 3 H 14 V 13 H 2 Z M 2 6 H 14 M 8 8 V 12 M 6 10 H 10"),
            Map.entry("paste", "M 6 3 H 3 V 14 H 13 V 3 H 10 M 6 2 H 10 V 5 H 6 Z"),
            Map.entry("pause", "M 4 3 H 6 V 13 H 4 Z M 10 3 H 12 V 13 H 10 Z"),
            Map.entry("regex", "M 3 12 H 3.1 M 10 3 V 11 M 6 5 L 14 9 M 6 9 L 14 5"),
            Map.entry("none", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 4 12 L 12 4"),
            Map.entry("remove", "M 3 8 H 13"),
            Map.entry("save", "M 2 2 H 12 L 14 4 V 14 H 2 Z M 5 2 V 6 H 11 V 2 M 5 14 V 9 H 11 V 14"),
            Map.entry("settings", "M 6.5 1.5 H 9.5 L 10 3.3 L 11.4 4.1 L 13.2 3.6 L 14.7 6.2 L 13.4 7.5 V 8.5 L 14.7 9.8 L 13.2 12.4 L 11.4 11.9 L 10 12.7 L 9.5 14.5 H 6.5 L 6 12.7 L 4.6 11.9 L 2.8 12.4 L 1.3 9.8 L 2.6 8.5 V 7.5 L 1.3 6.2 L 2.8 3.6 L 4.6 4.1 L 6 3.3 Z M 10.5 8 A 2.5 2.5 0 1 1 5.5 8 A 2.5 2.5 0 1 1 10.5 8"),
            Map.entry("appearance", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 8 2 V 14"),
            // Appearance actions (Settings theme row): a small sun, its disc and eight short rays.
            Map.entry("brightness", "M 10.6 8 A 2.6 2.6 0 1 1 5.4 8 A 2.6 2.6 0 1 1 10.6 8 M 8 1.6 V 3.1 M 8 12.9 V 14.4 M 1.6 8 H 3.1 M 12.9 8 H 14.4 "
                + "M 3.47 3.47 L 4.54 4.54 M 11.46 11.46 L 12.53 12.53 M 3.47 12.53 L 4.54 11.46 M 11.46 4.54 L 12.53 3.47"),
            Map.entry("window", "M 2 3 H 14 V 13 H 2 Z M 2 6 H 14"),
            Map.entry("font", "M 2 14 L 8 2 L 14 14 M 4.5 10 H 11.5"),
            Map.entry("cursor", "M 5 2 H 11 M 8 2 V 14 M 5 14 H 11"),
            Map.entry("keyboard", "M 1.5 4 H 14.5 V 12 H 1.5 Z M 4 6.5 H 4.1 M 7 6.5 H 7.1 M 10 6.5 H 12 M 4 9.5 H 12"),
            Map.entry("notification.card", "M 2 3 H 14 V 13 H 2 Z M 5 6 H 11 M 5 9 H 9"),
            Map.entry("notification.sticky", "M 2 5 H 14 V 14 H 2 Z M 5 8 H 11 M 10 2 L 12 4 M 11 1 L 9 5"),
            Map.entry("notification.history", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 8 4 V 8 L 11 10"),
            Map.entry("notification.off", "M 3 11 L 4 9 V 6 M 5 3 A 4 4 0 0 1 12 6 V 9 L 13 11 H 7 M 6 13 H 10 M 2 2 L 14 14"),
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
            // Every section open (chevrons parting) and every section closed (chevrons meeting).
            Map.entry("expand.all", "M 5 5.5 L 8 2.5 L 11 5.5 M 5 10.5 L 8 13.5 L 11 10.5"),
            Map.entry("collapse.all", "M 5 2.5 L 8 5.5 L 11 2.5 M 5 13.5 L 8 10.5 L 11 13.5"),
            Map.entry("search", "M 11 7 A 4 4 0 1 1 3 7 A 4 4 0 1 1 11 7 M 10 10 L 14 14"),
            Map.entry("shield", "M 8 1.333 L 12.85 3.667 V 7.5 C 12.85 11 11 13.333 8 14.333 C 5 13.333 3.15 11 3.15 7.5 V 3.667 Z M 6.5 7 H 9.5 Q 10 7 10 7.5 V 10.5 Q 10 11 9.5 11 H 6.5 Q 6 11 6 10.5 V 7.5 Q 6 7 6.5 7 M 7 7 V 6 A 1 1 0 0 1 9 6 V 7"),
            // Control-sized locks are a plain padlock; the hero padlock carries a solid 2.67-unit keyhole (source 4 logical at 24px).
            Map.entry("lock", "M 5 7 V 5 A 3 3 0 0 1 11 5 V 7 M 4.25 7 H 11.75 Q 12.85 7 12.85 8.25 V 12.75 Q 12.85 14 11.75 14 H 4.25 Q 3.15 14 3.15 12.75 V 8.25 Q 3.15 7 4.25 7"),
            Map.entry("lock.keyhole", "M 5 7 V 5 A 3 3 0 0 1 11 5 V 7 M 4.25 7 H 11.75 Q 12.85 7 12.85 8.25 V 12.75 Q 12.85 14 11.75 14 H 4.25 Q 3.15 14 3.15 12.75 V 8.25 Q 3.15 7 4.25 7 M 8.75 10.5 A 0.75 0.75 0 1 1 7.25 10.5 A 0.75 0.75 0 1 1 8.75 10.5"),
            Map.entry("edit", "M 3 10 L 10 3 L 13 6 L 6 13 L 2 14 Z M 9 4 L 12 7"),
            Map.entry("delete", "M 2 4 H 14 M 6 4 V 2 H 10 V 4 M 4 4 L 5 14 H 11 L 12 4 M 7 7 V 11 M 9 7 V 11"),
            Map.entry("copy", "M 6 5 H 14 V 14 H 6 Z M 10 5 V 2 H 2 V 11 H 6"),
            Map.entry("reveal", "M 1 8 Q 8 0 15 8 Q 8 16 1 8 Z M 10 8 A 2 2 0 1 1 6 8 A 2 2 0 1 1 10 8"),
            Map.entry("refresh", "M 13 5 A 5 5 0 1 0 13 11 M 13 2 V 5 H 10"),
            // Back to the default value: an arrow turning back over itself (Settings row Reset).
            Map.entry("reset", "M 5.25 3 L 2.5 5.75 L 5.25 8.5 M 2.5 5.75 H 10 A 3.75 3.75 0 0 1 10 13.25 H 5.5"),
            Map.entry("upload", "M 8 11 V 2 M 4 6 L 8 2 L 12 6 M 2 10 V 14 H 14 V 10"),
            Map.entry("back", "M 10 3 L 5 8 L 10 13 M 5 8 H 14"),
            Map.entry("forward", "M 6 3 L 11 8 L 6 13 M 2 8 H 11"),
            Map.entry("folder.filled", "M 2.5 3 H 6.2 L 8.1 5 H 13.5 Q 14.5 5 14.5 6 V 12.5 Q 14.5 13.5 13.5 13.5 H 2.5 Q 1.5 13.5 1.5 12.5 V 4 Q 1.5 3 2.5 3 Z"),
            Map.entry("folder", "M 2 4 H 6 L 8 6 H 14 V 13 H 2 Z"),
            Map.entry("file", "M 4 2 H 9 L 13 6 V 14 H 4 Z M 9 2 V 6 H 13"),
            Map.entry("server", "M 2 2 H 14 V 7 H 2 Z M 2 9 H 14 V 14 H 2 Z M 5 4.5 H 5.1 M 5 11.5 H 5.1"),
            Map.entry("key", "M 8 11 A 3 3 0 1 1 2 11 A 3 3 0 1 1 8 11 M 7 9 L 13 3 M 11 5 L 13 7 M 13 3 L 15 5"),
            Map.entry("user", "M 11 5 A 3 3 0 1 1 5 5 A 3 3 0 1 1 11 5 M 2 14 Q 2 9 8 9 Q 14 9 14 14"),
            Map.entry("login", "M 10 2 H 14 V 14 H 10 M 2 8 H 10 M 7 5 L 10 8 L 7 11"),
            Map.entry("note", "M 4 1 H 9 L 13 5 V 15 H 4 Z M 9 1 V 5 H 13 M 7 8 H 10 M 7 11 H 10"),
            Map.entry("tunnel", "M 2 13 V 8 A 6 6 0 0 1 14 8 V 13 M 5 13 V 8 A 3 3 0 0 1 11 8 V 13 M 8 10 V 14"),
            Map.entry("command-palette", "M 2 3 H 14 V 13 H 2 Z M 4 6 L 7 8 L 4 10 M 9 10 H 12"),
            Map.entry("chevron.left", "M 10 4 L 6 8 L 10 12"),
            Map.entry("terminal", "M 2 3 H 14 V 13 H 2 Z M 4 6 L 7 8 L 4 10 M 9 10 H 12"),
            Map.entry("play", "M 5 2 L 14 8 L 5 14 Z"),
            Map.entry("warning", "M 8 2 L 15 14 H 1 Z M 8 6 V 9 M 8 12 H 8.1"),
            Map.entry("bell", "M 3 11 L 4 9 V 6 A 4 4 0 0 1 12 6 V 9 L 13 11 Z M 6 13 A 2 2 0 0 0 10 13"),
            Map.entry("info", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 8 7 V 11 M 8 4.5 H 8.1"),
            Map.entry("error", "M 14 8 A 6 6 0 1 1 2 8 A 6 6 0 1 1 14 8 M 6 6 L 10 10 M 10 6 L 6 10"),
            Map.entry("plugin", "M 6 2 V 5 M 10 2 V 5 M 4 5 H 12 V 8 A 4 4 0 0 1 4 8 Z M 8 12 V 14"),
            Map.entry("check", "M 3 8 L 6 11 L 13 4"),
            Map.entry("close", "M 4 4 L 12 12 M 12 4 L 4 12"),
            Map.entry("sparkle", "M 8 1 V 5 M 8 11 V 15 M 1 8 H 5 M 11 8 H 15 M 3 3 L 5.2 5.2 M 10.8 10.8 L 13 13 M 3 13 L 5.2 10.8 M 10.8 5.2 L 13 3"),
            // A favourite: the outline star, and the filled star (outline, an inner star and spokes to the tips) for a chosen one.
            Map.entry("star", "M 8 2 L 9.6 6.3 L 14.3 6.6 L 10.7 9.5 L 11.9 13.9 L 8 11.4 L 4.1 13.9 L 5.3 9.5 L 1.7 6.6 L 6.4 6.3 Z"),
            Map.entry("star.filled", "M 8 2 L 9.6 6.3 L 14.3 6.6 L 10.7 9.5 L 11.9 13.9 L 8 11.4 L 4.1 13.9 L 5.3 9.5 L 1.7 6.6 L 6.4 6.3 Z "
                + "M 8 5 L 8.9 7.4 L 11.4 7.5 L 9.4 9.1 L 10.1 11.5 L 8 10.1 L 5.9 11.5 L 6.6 9.1 L 4.6 7.5 L 7.1 7.4 Z "
                + "M 8 8.6 L 8 3.6 M 8 8.6 L 12.8 7 M 8 8.6 L 11 12.6 M 8 8.6 L 5 12.6 M 8 8.6 L 3.2 7")
        );
    }
}
