package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * The classic group (spec 6.4, DESIGN_LANGUAGE §6, New File Server mock): a Section and its Card as one titled box, a
 * 1 px {@code border.window} edge whose top line the semibold title breaks; the card inside is transparent, a SETTINGS
 * card pads its rows 8 above and below, a MIXED card draws one {@code border.row} divider where its content gives way to
 * its settings, and a Settings page's card divides every row.
 */
final class ClassicGroupFace implements GroupFace {
    private final PaintPair box, title;

    /** A face whose boxes stand on {@code surface}, the kit's dialog surface. */
    ClassicGroupFace(SemanticColor surface) {
        box = new PaintPair(SemanticColor.BORDER_WINDOW, surface, SurfaceContrast.Guard.STRUCTURAL);
        title = new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT);
    }

    @Override public List<PaintPair> pairs() { return List.of(box, title); }

    @Override public LayoutManager sectionLayout(Cards.SectionControl section) { return new TitledBoxLayout(section.context()); }

    @Override public void styleTitle(JLabel label, KitContext context) {
        label.setFont(ThemePainting.font(context, Typography.Role.SECTION_LABEL));
        label.setForeground(ThemePainting.painted(context, title));
    }

    @Override public void paintSection(Cards.SectionSurface s, Graphics2D g) {
        if (s.getComponentCount() < 2) return;
        var c = s.context; var label = s.getComponent(0);
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE), top = label.getHeight() / 2, pad = ThemePainting.metric(c, DesignMetrics.Role.GROUP_TITLE_PADDING);
        double radius = ThemePainting.metric(c, DesignMetrics.Role.CARD_RADIUS);
        ThemePainting.antialias(g);
        var edge = new Area(new RoundRectangle2D.Double(0, top, s.getWidth(), s.getHeight() - top, 2 * radius, 2 * radius));
        edge.subtract(new Area(new RoundRectangle2D.Double(line, top + line, s.getWidth() - 2.0 * line, s.getHeight() - top - 2.0 * line,
            2 * Math.max(0, radius - line), 2 * Math.max(0, radius - line))));
        // The title breaks the top line: the gap holds the title and its padding on either side.
        if (label.isVisible() && label.getWidth() > 0) edge.subtract(new Area(new Rectangle(label.getX() - pad, 0, label.getWidth() + 2 * pad, top + 2 * line)));
        g.setColor(ThemePainting.painted(c, box));
        g.fill(edge);
    }

    @Override public void paintCard(Cards.CardSurface card, Graphics2D g) {
        var c = card.context;
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE), pad = ThemePainting.metric(c, DesignMetrics.Role.CARD_PADDING);
        g.setColor(ThemePainting.role(c, DerivedColor.BORDER_ROW));
        Component previous = null; boolean divided = false;
        for (var child : card.getComponents()) if (child.isVisible()) {
            boolean setting = card.settings.contains(child), before = previous != null && card.settings.contains(previous);
            if (previous != null && card.page) g.fillRect(0, child.getY(), card.getWidth(), line);
            else if (previous != null && !divided && card.kind == Nodes.CardKind.MIXED && setting && !before) {
                g.fillRect(pad, child.getY(), Math.max(0, card.getWidth() - 2 * pad), line);
                divided = true;
            }
            previous = child;
        }
    }

    @Override public Border cardBorder(KitContext context, Nodes.CardKind kind, boolean page, EmptyBorder standard) {
        if (kind != Nodes.CardKind.SETTINGS || page) return standard;
        int gap = ThemePainting.metric(context, DesignMetrics.Role.ROW_GAP);
        return new EmptyBorder(gap, 0, gap, 0);
    }

    /** The title at the box's title inset; the body from just below the title's middle, where the box's top line runs. */
    static final class TitledBoxLayout implements Composition.WidthLayout {
        private final KitContext context;
        TitledBoxLayout(KitContext context) { this.context = context; }
        int x() { return ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_INSET) + ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_PADDING); }
        int offset(Component title) { return title.getPreferredSize().height / 2 + ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_GAP); }
        @Override public int height(Container p, int width) {
            return p.getComponentCount() < 2 ? 0 : offset(p.getComponent(0)) + Composition.height(p.getComponent(1), width);
        }
        @Override public Dimension preferredLayoutSize(Container p) {
            int w = p.getComponentCount() < 2 ? 0 : Math.max(p.getComponent(1).getPreferredSize().width, 2 * x() + p.getComponent(0).getPreferredSize().width);
            return new Dimension(w, height(p, p.getWidth() > 0 ? p.getWidth() : w));
        }
        @Override public void layoutContainer(Container p) {
            if (p.getComponentCount() < 2) return;
            var title = p.getComponent(0); var size = title.getPreferredSize();
            title.setBounds(x(), 0, Math.min(size.width, Math.max(0, p.getWidth() - 2 * x())), size.height);
            int top = offset(title);
            p.getComponent(1).setBounds(0, top, p.getWidth(), Composition.height(p.getComponent(1), p.getWidth()));
        }
    }
}
