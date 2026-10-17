package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/** The default group: a tracked section label above a rounded card. */
final class DefaultGroupFace implements GroupFace {
    @Override public LayoutManager sectionLayout(Cards.SectionControl section) {
        return new Composition.Vertical(ThemePainting.metric(section.context(), DesignMetrics.Role.SECTION_LABEL_GAP));
    }
    @Override public void paintSection(Cards.SectionSurface surface, Graphics2D g) { }
    @Override public void paintCard(Cards.CardSurface card, Graphics2D g) { card.paintDefault(g); }
    @Override public void styleTitle(JLabel title, KitContext context) { Composition.style(title, context, Typography.Role.SECTION_LABEL, SemanticColor.TEXT_TERTIARY); }
    @Override public Border cardBorder(KitContext context, Nodes.CardKind kind, boolean page, EmptyBorder standard) { return standard; }
    @Override public int rowRule(KitContext context) { return 0; }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
