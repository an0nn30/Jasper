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
 * its settings, and a Settings page's card pads its rows 6 above and below and divides every row with a rule that stops
 * short of the box. A manager detail's card of label and value rows is a grid (the Credential Vault mock's Fields): a
 * {@code surface.card} box with a {@code border.divider} edge inside the group's, its rows divided by {@code border.row}.
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
        if (card.grid) { paintGrid(card, g); return; }
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE), pad = ThemePainting.metric(c, DesignMetrics.Role.CARD_PADDING);
        int rule = ThemePainting.metric(c, DesignMetrics.Role.SETTINGS_ROW_SIDE);
        g.setColor(ThemePainting.role(c, DerivedColor.BORDER_ROW));
        Component previous = null; boolean divided = false;
        for (var child : card.getComponents()) if (child.isVisible()) {
            boolean setting = card.settings.contains(child), before = previous != null && card.settings.contains(previous);
            // The mocks' row rules stop short of the box on both sides, by the rows' side inset inside the stroke (plan 9b
            // Execution notes N7; mock 02 draws them from 260 to 1010 in a box from 247 to 1023).
            if (previous != null && card.page) g.fillRect(child.getX() + rule, child.getY(), Math.max(0, child.getWidth() - 2 * rule), line);
            else if (previous != null && !divided && card.kind == Nodes.CardKind.MIXED && setting && !before) {
                g.fillRect(pad, child.getY(), Math.max(0, card.getWidth() - 2 * pad), line);
                divided = true;
            }
            previous = child;
        }
    }

    @Override public Border cardBorder(KitContext context, Nodes.CardKind kind, boolean page, EmptyBorder standard) {
        if (kind != Nodes.CardKind.SETTINGS) return standard;
        // A Settings page's box holds its rows inside its stroke, padded by their own vertical padding above the first and
        // below the last (plan 9b Task 1: mocks 01 and 02 draw the first row rule 49 below the box line, the box line 45
        // below the last rule, and the rows' rules, text and controls 13 inside the box's outer edge).
        if (page) {
            int pad = ThemePainting.metric(context, DesignMetrics.Role.SETTING_VERTICAL_PADDING), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            var insets = standard.getBorderInsets();
            return new EmptyBorder(insets.top + pad, insets.left + line, insets.bottom + pad, insets.right + line);
        }
        int gap = ThemePainting.metric(context, DesignMetrics.Role.ROW_GAP);
        return new EmptyBorder(gap, 0, gap, 0);
    }

    /**
     * A FORM card on a Settings page spaces its content by the rows' vertical padding (the Notifications mock's description
     * 6 above its groups table, plan 9b Task 2).
     */
    @Override public int cardGap(KitContext context, Nodes.CardKind kind, boolean page, int standard) {
        return page && kind == Nodes.CardKind.FORM ? ThemePainting.metric(context, DesignMetrics.Role.SETTING_VERTICAL_PADDING) : standard;
    }

    /**
     * A Settings page's list (an ordered list with its side column and entry row) spans the row below its title and
     * description, as the Shell, Font &amp; Colors and Project Index mocks draw it (plan 9b Task 6).
     */
    @Override public boolean listUnderTitle(KitContext context) { return true; }

    @Override public boolean detailGrid() { return true; }

    /** The box's top line, not its title, stands the gap below what precedes it; the title rides over the gap. */
    @Override public int sectionGap(Cards.SectionSurface section, int standard) {
        return section.getComponentCount() < 2 ? standard : Math.max(0, standard - section.getComponent(0).getPreferredSize().height / 2);
    }

    /** The grid's box inside the card ({@link Cards.GridRows}) and a rule between each two of its rows. */
    private static void paintGrid(Cards.CardSurface card, Graphics2D g) {
        var c = card.context;
        int inset = ThemePainting.metric(c, DesignMetrics.Role.DETAIL_GRID_INSET), top = ThemePainting.metric(c, DesignMetrics.Role.DETAIL_GRID_TOP);
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE);
        int width = Math.max(0, card.getWidth() - 2 * inset), height = Math.max(0, card.getHeight() - top - inset);
        g.setColor(ThemePainting.color(c, SemanticColor.SURFACE_CARD));
        g.fillRect(inset, top, width, height);
        g.setColor(ThemePainting.role(c, DerivedColor.BORDER_DIVIDER));
        g.fillRect(inset, top, width, line); g.fillRect(inset, top + height - line, width, line);
        g.fillRect(inset, top, line, height); g.fillRect(inset + width - line, top, line, height);
        g.setColor(ThemePainting.role(c, DerivedColor.BORDER_ROW));
        for (int y : Cards.GridRows.rules(card)) g.fillRect(inset + line, y, Math.max(0, width - 2 * line), line);
    }

    /** A Settings page's rows keep their first line clear for the rule {@link #paintCard} draws there. */
    @Override public int rowRule(KitContext context) { return ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE); }

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
