package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * The classic prompt (spec 5.2 as amended, Unlock Vault mock): no tile over a title; the title belongs to the dialog's
 * header strip, and a 30 px accent tile with an on-accent glyph sits beside the message.
 */
final class ClassicPromptFace implements PromptFace {
    private final PaintPair edge, glyph;

    /** A face whose tile stands on {@code surface}, the kit's dialog surface. */
    ClassicPromptFace(SemanticColor surface) {
        edge = new PaintPair(DerivedColor.ACCENT_EDGE, surface, SurfaceContrast.Guard.INDICATOR);
        glyph = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.INDICATOR);
    }

    @Override public List<PaintPair> pairs() { return List.of(edge, glyph); }

    @Override public LayoutManager headerLayout(Stacks.StackControl header) { return new BesideLayout(header); }

    @Override public void paintTile(Stacks.Tile tile, Graphics2D g) {
        var c = tile.context;
        ThemePainting.antialias(g);
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE);
        double radius = ThemePainting.metric(c, DesignMetrics.Role.PROMPT_TILE_RADIUS);
        var outer = new RoundRectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight(), 2 * radius, 2 * radius);
        var inner = new RoundRectangle2D.Double(line, line, tile.getWidth() - 2.0 * line, tile.getHeight() - 2.0 * line,
            2 * Math.max(0, radius - line), 2 * Math.max(0, radius - line));
        g.setColor(ThemePainting.color(c, SemanticColor.ACCENT)); g.fill(inner);
        var ring = new Area(outer); ring.subtract(new Area(inner));
        g.setColor(ThemePainting.painted(c, edge)); g.fill(ring);
    }

    @Override public void children(Stacks.StackControl header, List<ControlRegistry.Control> controls) {
        for (var control : controls) {
            // The title belongs to the dialog's header strip; the hook keeps it hidden through every later refresh
            // (KitView refreshes every control once more when it subscribes to the theme, after the first pass).
            if (control.node instanceof Nodes.Text text && text.role() == Nodes.TextRole.PROMPT_TITLE && control instanceof Labels.TextControl title) {
                title.styled = label -> label.setVisible(false);
                title.component.setVisible(false);
            }
            // The message reads from the tile (Unlock Vault mock); the reconciler's flow pass, which runs before
            // children on every update, centres prompt header text for the default face.
            if (control.component instanceof Labels.LiteralLabel label) label.wrapped.centered = false;
            // The glyph is the on-accent ink on the tile, held there by its guard; the reconciler clears it before each update.
            if (control instanceof Labels.IconControl icon && icon.facePair != glyph) { icon.facePair = glyph; icon.refresh(); }
        }
    }

    /**
     * The tile, then the other children stacked beside it, the block centred on the tile when it is shorter. A header
     * whose tile is not mounted (a hero or section without an icon) lays its lines out from the leading edge.
     */
    static final class BesideLayout implements Composition.WidthLayout {
        private final Stacks.StackControl owner;
        BesideLayout(Stacks.StackControl owner) { this.owner = owner; }
        int gap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.PROMPT_TILE_GAP); }
        boolean tiled(Container p) { return owner.tile.getParent() == p && owner.tile.isVisible(); }
        Dimension tile(Container p) { return tiled(p) ? owner.tile.getPreferredSize() : new Dimension(); }
        int lead(Container p) { return tiled(p) ? owner.tile.getPreferredSize().width + gap() : 0; }
        List<Component> lines(Container p) {
            return java.util.Arrays.stream(p.getComponents()).filter(c -> c.isVisible() && c != owner.tile).toList();
        }
        int room(Container p, int width) { return Math.max(0, width - lead(p)); }
        int text(Container p, int width) {
            var lines = lines(p); int h = 0;
            for (int i = 0; i < lines.size(); i++) h += (i > 0 ? ThemePainting.metric(owner.context(), DesignMetrics.Role.DESCRIPTION_GAP) : 0) + Composition.height(lines.get(i), room(p, width));
            return h;
        }
        @Override public int height(Container p, int width) { return Math.max(tile(p).height, text(p, width)); }
        @Override public Dimension preferredLayoutSize(Container p) {
            int w = lead(p) + lines(p).stream().mapToInt(c -> c.getPreferredSize().width).max().orElse(0);
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }
        @Override public void layoutContainer(Container p) {
            var size = tile(p);
            int textHeight = text(p, p.getWidth()), top = Math.max(0, (size.height - textHeight) / 2);
            if (tiled(p)) owner.tile.setBounds(0, Math.max(0, (textHeight - size.height) / 2), size.width, size.height);
            int x = lead(p), y = top, width = room(p, p.getWidth());
            for (var line : lines(p)) {
                int h = Composition.height(line, width);
                line.setBounds(x, y, width, h);
                y += h + ThemePainting.metric(owner.context(), DesignMetrics.Role.DESCRIPTION_GAP);
            }
        }
    }
}
