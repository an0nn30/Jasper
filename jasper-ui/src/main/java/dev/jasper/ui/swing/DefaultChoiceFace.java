package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.List;
import javax.swing.border.EmptyBorder;

/** The default choices: the segment track, its selected segment's soft shadow in Light, and the stretched preview grid. */
final class DefaultChoiceFace implements ChoiceFace {
    /** The selected segment is not an accent surface: primary text, held to the text floor on its fill. */
    static final PaintPair SELECTED_LABEL = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SEGMENT_SELECTED_BG, SurfaceContrast.Guard.TEXT);
    /** A selected preview's caption on the card. */
    static final PaintPair SELECTED_CAPTION = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
    private final List<PaintPair> pairs;

    /** A face declaring {@code pairs}: the exclusive point both repairs, the tabs point the segment label alone. */
    DefaultChoiceFace(List<PaintPair> pairs) { this.pairs = List.copyOf(pairs); }

    @Override public List<PaintPair> pairs() { return pairs; }
    @Override public LayoutManager layout(KitContext context) { return new Choices.SegmentLayout(); }
    @Override public int trackPadding(KitContext context, boolean form) {
        return ThemePainting.metric(context, form ? DesignMetrics.Role.FORM_SEGMENT_PADDING : DesignMetrics.Role.SETTINGS_SEGMENT_INSET);
    }
    @Override public void paintTrack(Choices.SegmentTrack track, Graphics2D g) { track.paintSegments(g); }
    @Override public void style(Choices.SegmentButton button, Option option, boolean settings, boolean identitySection, int track, int count) {
        var context = button.context;
        var font = context.typography().font(Typography.Role.LABEL, !settings && button.isSelected());
        button.setFont(SwingTypography.effective(font));
        button.setForeground(button.textColor(false));
        button.setIcon(option.hue().map(h -> (javax.swing.Icon) new Choices.HueDot(context, h)).orElse(null)); button.setDisabledIcon(button.getIcon());
        // A segment's label keeps its padding from the segment's edge everywhere (spec 4).
        int pad = ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_HORIZONTAL_PADDING);
        if (identitySection) pad = Math.max(pad, ((ThemePainting.metric(context, DesignMetrics.Role.IDENTITY_SECTION_WIDTH) - 2 * track) / Math.max(1, count)
            - button.getFontMetrics(button.getFont()).stringWidth(option.label())) / 2);
        button.setBorder(new EmptyBorder(0, pad, 0, pad));
    }
    @Override public Dimension optionSize(Choices.SegmentButton option) { return option.segmentSize(); }
    @Override public void paintOption(Choices.SegmentButton option, Graphics graphics, Runnable label) { option.paintSegment(graphics, label); }
    @Override public Color selectedText(KitContext context) { return ThemePainting.painted(context, SELECTED_LABEL); }
    @Override public Insets previewPadding(KitContext context) {
        int pad = ThemePainting.metric(context, DesignMetrics.Role.CARD_PADDING), stroke = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        return new Insets(pad + stroke, pad + stroke, pad, pad + stroke);
    }
    @Override public LayoutManager previewLayout(KitContext context, int count) {
        return new GridLayout(1, Math.max(1, count), ThemePainting.metric(context, DesignMetrics.Role.THEME_PREVIEW_GAP), 0);
    }
    @Override public Dimension previewSize(PreviewChoices.Tile tile) { return tile.tileSize(); }
    @Override public void paintPreview(PreviewChoices.Tile tile, Graphics2D g) { tile.paintTile(g); }
}
