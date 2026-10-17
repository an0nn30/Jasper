package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The classic tag (spec 5.2 and 7.1, the Workstation "HTTP" chip): the label in upper case, in its hue through the text
 * guard, on the default tint inside a 1 px box in the hue's tag border role (plan 4c Rulings 11).
 */
final class ClassicTagFace implements TagFace {
    private static final List<Nodes.Hue> HUES = List.of(Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE, Nodes.Hue.NEUTRAL);
    private final List<PaintPair> pairs;

    ClassicTagFace() {
        var all = new ArrayList<PaintPair>();
        for (var hue : HUES) {
            all.add(new PaintPair(hue(hue), surface(hue), SurfaceContrast.Guard.TEXT));
            all.add(new PaintPair(border(hue), surface(hue), SurfaceContrast.Guard.STRUCTURAL));
        }
        pairs = List.copyOf(all);
    }

    @Override public List<PaintPair> pairs() { return pairs; }

    /** The text pair of {@code hue}; NONE takes the neutral pair. */
    PaintPair text(Nodes.Hue hue) { return pairs.get(2 * index(hue)); }

    /** The box pair of {@code hue}; NONE takes the neutral pair. */
    PaintPair edge(Nodes.Hue hue) { return pairs.get(2 * index(hue) + 1); }

    private static int index(Nodes.Hue hue) { return HUES.indexOf(hue == Nodes.Hue.NONE ? Nodes.Hue.NEUTRAL : hue); }

    private static SemanticColor hue(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL;
        };
    }

    private static SemanticColor surface(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE_SURFACE; case GREEN -> SemanticColor.HUE_GREEN_SURFACE; case AMBER -> SemanticColor.HUE_AMBER_SURFACE;
            case PURPLE -> SemanticColor.HUE_PURPLE_SURFACE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL_SURFACE;
        };
    }

    private static DerivedColor border(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> DerivedColor.TAG_BORDER_BLUE; case GREEN -> DerivedColor.TAG_BORDER_GREEN; case AMBER -> DerivedColor.TAG_BORDER_AMBER;
            case PURPLE -> DerivedColor.TAG_BORDER_PURPLE; case NEUTRAL, NONE -> DerivedColor.TAG_BORDER_NEUTRAL;
        };
    }

    @Override public void style(Indicators.ChipControl chip, Indicators.ChipLabel label) {
        var node = (Nodes.Chip) chip.node;
        label.setText(node.label().toUpperCase(Locale.ROOT));
        // A chip on a selected row keeps the default inked colours, boxed in the ink.
        if (chip.inked) { label.edge = label.getForeground(); return; }
        label.setForeground(ThemePainting.painted(chip.context(), text(node.hue())));
        label.edge = ThemePainting.painted(chip.context(), edge(node.hue()));
    }

    @Override public void paint(Indicators.ChipLabel label, Graphics2D g) {
        label.paintTile(g);
        if (label.edge == null) return;
        int line = ThemePainting.metric(label.context, DesignMetrics.Role.HAIRLINE);
        double radius = ThemePainting.metric(label.context, DesignMetrics.Role.CHIP_RADIUS);
        var ring = new Area(new RoundRectangle2D.Double(0, 0, label.getWidth(), label.getHeight(), 2 * radius, 2 * radius));
        ring.subtract(new Area(new RoundRectangle2D.Double(line, line, label.getWidth() - 2.0 * line, label.getHeight() - 2.0 * line,
            2 * Math.max(0, radius - line), 2 * Math.max(0, radius - line))));
        g.setColor(label.edge);
        g.fill(ring);
    }
}
