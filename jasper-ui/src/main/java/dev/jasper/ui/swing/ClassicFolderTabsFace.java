package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * The classic tabs (spec 5.2, DESIGN_LANGUAGE §6, Settings › Plugins mock): content-sized folder tabs on a baseline in
 * {@code border.control}; the active tab {@code surface.card} and open below, joining the content; the others
 * {@code surface.stripe}; each with a small top radius.
 */
final class ClassicFolderTabsFace implements ChoiceFace {
    private final PaintPair active, inactive, edge;
    /** PREVIEWS choices ask the exclusive point's face; these tabs never draw one. */
    private final ChoiceFace previews = new DefaultChoiceFace(List.of());

    /** A face whose tabs stand on {@code surface}, the kit's dialog surface. */
    ClassicFolderTabsFace(SemanticColor surface) {
        active = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
        inactive = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_STRIPE, SurfaceContrast.Guard.TEXT);
        edge = new PaintPair(SemanticColor.BORDER_CONTROL, surface, SurfaceContrast.Guard.CONTROL);
    }

    @Override public List<PaintPair> pairs() { return List.of(active, inactive, edge); }
    @Override public LayoutManager layout(KitContext context) { return new ClassicRadiosFace.RowLayout(context, null); }
    @Override public int trackPadding(KitContext context, boolean form) { return 0; }

    /** The baseline runs along the tabs' foot, which is the track's own foot unless a layout stretches the track. */
    @Override public void paintTrack(Choices.SegmentTrack track, Graphics2D g) {
        int line = ThemePainting.metric(track.context, DesignMetrics.Role.HAIRLINE), foot = 0;
        for (var tab : track.getComponents()) if (tab.isVisible()) foot = Math.max(foot, tab.getY() + tab.getHeight());
        g.setColor(ThemePainting.painted(track.context, edge));
        g.fillRect(0, (foot == 0 ? track.getHeight() : foot) - line, track.getWidth(), line);
    }

    @Override public void style(Choices.SegmentButton option, Option model, boolean settings, boolean identitySection, int track, int count) {
        var c = option.context;
        option.setFont(ThemePainting.font(c, Typography.Role.LABEL));
        option.setForeground(ThemePainting.painted(c, option.isSelected() ? active : inactive));
        option.setIcon(null); option.setDisabledIcon(null);
        int pad = ThemePainting.metric(c, DesignMetrics.Role.FOLDER_TAB_PADDING);
        option.setBorder(new EmptyBorder(0, pad, 0, pad));
        option.setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override public Dimension optionSize(Choices.SegmentButton option) {
        var c = option.context;
        int text = ThemePainting.measureText(option.getFont(), option.getText() == null ? "" : option.getText());
        return new Dimension(text + 2 * ThemePainting.metric(c, DesignMetrics.Role.FOLDER_TAB_PADDING), ThemePainting.metric(c, DesignMetrics.Role.FOLDER_TAB_HEIGHT));
    }

    @Override public void paintOption(Choices.SegmentButton option, Graphics graphics, Runnable label) {
        var c = option.context;
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE), r = ThemePainting.metric(c, DesignMetrics.Role.FOLDER_TAB_RADIUS);
            double w = option.getWidth(), h = option.getHeight();
            boolean selected = option.isSelected();
            var outer = top(0, 0, w, h, r);
            var inner = top(line, line, w - 2.0 * line, h - line - (selected ? 0 : line), Math.max(0, r - line));
            var border = new Area(outer); border.subtract(new Area(inner));
            g.setColor(selected ? ThemePainting.color(c, SemanticColor.SURFACE_CARD) : ThemePainting.role(c, DerivedColor.SURFACE_STRIPE));
            g.fill(inner);
            g.setColor(ThemePainting.painted(c, edge));
            g.fill(border);
            if (!option.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            g.setFont(option.getFont()); g.setColor(option.getForeground());
            var fm = g.getFontMetrics(); String text = option.getText() == null ? "" : option.getText();
            g.drawString(text, (float) ((w - ThemePainting.measureText(option.getFont(), text)) / 2), (float) ((h - fm.getHeight()) / 2 + fm.getAscent()));
            if (option.focus != null && option.focus.visible()) {
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(ThemePainting.color(c, SemanticColor.BORDER_FOCUS));
                g.draw(new Rectangle2D.Double(line + .5, line + .5, w - 2.0 * line - 1, h - 2.0 * line - 1));
            }
        } finally { g.dispose(); }
    }

    /** A rectangle with only its top corners rounded by {@code r}. */
    private static Shape top(double x, double y, double w, double h, double r) {
        var path = new Path2D.Double();
        path.moveTo(x, y + h); path.lineTo(x, y + r); path.quadTo(x, y, x + r, y); path.lineTo(x + w - r, y);
        path.quadTo(x + w, y, x + w, y + r); path.lineTo(x + w, y + h); path.closePath();
        return path;
    }

    @Override public Color selectedText(KitContext context) { return ThemePainting.painted(context, active); }
    @Override public Insets previewPadding(KitContext context) { return previews.previewPadding(context); }
    @Override public LayoutManager previewLayout(KitContext context, int count) { return previews.previewLayout(context, count); }
    @Override public Dimension previewSize(PreviewChoices.Tile tile) { return previews.previewSize(tile); }
    @Override public void paintPreview(PreviewChoices.Tile tile, Graphics2D g) { previews.paintPreview(tile, g); }
}
