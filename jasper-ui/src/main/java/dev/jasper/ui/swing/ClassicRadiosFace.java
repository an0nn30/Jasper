package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * The classic exclusive choice (spec 5.2, DESIGN_LANGUAGE §6): a left-aligned row of 14 px radios, 18 apart, wrapping
 * line by line; a selected ring in {@code accent.edge} round a 6 px accent dot, an unselected one in {@code border.hover};
 * an option's hue as a 7 px swatch before its label; PREVIEWS as thumbnails over their radios, the selected one in a 2 px
 * accent frame.
 */
final class ClassicRadiosFace implements ChoiceFace {
    private final PaintPair label, selectedRing, dot, ring, frame, plainFrame;
    private final List<PaintPair> swatches;

    /** A face whose radios stand on {@code surface}, the kit's dialog surface. */
    ClassicRadiosFace(SemanticColor surface) {
        label = new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT);
        selectedRing = new PaintPair(DerivedColor.ACCENT_EDGE, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.INDICATOR);
        dot = new PaintPair(SemanticColor.ACCENT, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.INDICATOR);
        ring = new PaintPair(DerivedColor.BORDER_HOVER, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.CONTROL);
        frame = new PaintPair(SemanticColor.ACCENT, surface, SurfaceContrast.Guard.INDICATOR);
        plainFrame = new PaintPair(SemanticColor.BORDER_WINDOW, surface, SurfaceContrast.Guard.STRUCTURAL);
        swatches = List.of(swatch(SemanticColor.HUE_BLUE, surface), swatch(SemanticColor.HUE_GREEN, surface), swatch(SemanticColor.HUE_AMBER, surface),
            swatch(SemanticColor.HUE_PURPLE, surface), swatch(SemanticColor.HUE_NEUTRAL, surface));
    }

    private static PaintPair swatch(SemanticColor hue, SemanticColor surface) { return new PaintPair(hue, surface, SurfaceContrast.Guard.INDICATOR); }

    @Override public List<PaintPair> pairs() {
        var all = new ArrayList<>(List.of(label, selectedRing, dot, ring, frame, plainFrame));
        all.addAll(swatches);
        return List.copyOf(all);
    }

    private PaintPair swatchOf(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> swatches.get(0); case GREEN -> swatches.get(1); case AMBER -> swatches.get(2); case PURPLE -> swatches.get(3);
            case NEUTRAL, NONE -> swatches.get(4);
        };
    }

    @Override public LayoutManager layout(KitContext context) { return new RowLayout(context, DesignMetrics.Role.RADIO_GAP); }
    @Override public int trackPadding(KitContext context, boolean form) { return 0; }
    @Override public void paintTrack(Choices.SegmentTrack track, Graphics2D g) { }

    @Override public void style(Choices.SegmentButton option, Option model, boolean settings, boolean identitySection, int track, int count) {
        var c = option.context;
        option.setFont(ThemePainting.font(c, Typography.Role.LABEL));
        option.setForeground(ThemePainting.painted(c, label));
        option.setIcon(new Radio(c, option, model.hue())); option.setDisabledIcon(option.getIcon());
        option.setIconTextGap(ThemePainting.metric(c, DesignMetrics.Role.RADIO_LABEL_GAP));
        option.setBorder(new EmptyBorder(0, 0, 0, 0));
        option.setHorizontalAlignment(SwingConstants.LEADING);
    }

    @Override public Dimension optionSize(Choices.SegmentButton option) {
        var c = option.context; var icon = option.getIcon();
        int text = ThemePainting.measureText(option.getFont(), option.getText() == null ? "" : option.getText());
        int width = (icon == null ? 0 : icon.getIconWidth() + option.getIconTextGap()) + text;
        int ring = ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING) + 2 * ThemePainting.metric(c, DesignMetrics.Role.FOCUS_RING);
        return new Dimension(width, Math.max(ring, option.getFontMetrics(option.getFont()).getHeight()));
    }

    @Override public void paintOption(Choices.SegmentButton option, Graphics graphics, Runnable label) {
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            var icon = option.getIcon();
            int x = 0;
            if (icon != null) { icon.paintIcon(option, g, 0, (option.getHeight() - icon.getIconHeight()) / 2); x = icon.getIconWidth() + option.getIconTextGap(); }
            if (!option.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            g.setFont(option.getFont()); g.setColor(option.getForeground());
            var fm = g.getFontMetrics();
            g.drawString(option.getText() == null ? "" : option.getText(), x, (option.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        } finally { g.dispose(); }
    }

    @Override public Color selectedText(KitContext context) { return ThemePainting.painted(context, label); }

    /**
     * The card's padding beside the tiles (a classic Settings box already holds its content inside its stroke), and the
     * classic card's row gap above and below them (plan 9b Task 1, mock 01).
     */
    @Override public Insets previewPadding(KitContext context) {
        int pad = ThemePainting.metric(context, DesignMetrics.Role.CARD_PADDING), gap = ThemePainting.metric(context, DesignMetrics.Role.ROW_GAP);
        int stroke = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        return new Insets(gap + stroke, pad, gap, pad);
    }

    @Override public LayoutManager previewLayout(KitContext context, int count) { return new RowLayout(context, DesignMetrics.Role.THEME_PREVIEW_GAP); }

    @Override public Dimension previewSize(PreviewChoices.Tile tile) {
        var c = tile.context();
        int line = Math.max(ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING), tile.getFontMetrics(tile.getFont()).getHeight());
        return new Dimension(ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_WIDTH), ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_HEIGHT)
            + ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_CAPTION_GAP) + line);
    }

    @Override public void paintPreview(PreviewChoices.Tile tile, Graphics2D g) {
        var c = tile.context();
        ThemePainting.antialias(g);
        int h = Math.min(tile.getHeight(), ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_HEIGHT)), w = tile.getWidth();
        int width = ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_RING), inset = ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_INSET) / 2;
        if (!tile.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
        // The frame: 2 px of accent round the selected thumbnail, of the window border round the others.
        g.setColor(ThemePainting.painted(c, tile.isSelected() ? frame : plainFrame));
        g.fillRect(0, 0, w, width); g.fillRect(0, h - width, w, width); g.fillRect(0, 0, width, h); g.fillRect(w - width, 0, width, h);
        if (tile.option != null && tile.option.preview().isPresent()) {
            var preview = tile.option.preview().get();
            var clip = g.getClip();
            g.clipRect(inset, inset, Math.max(0, w - 2 * inset), Math.max(0, h - 2 * inset));
            tile.miniature(g, preview.primary(), inset, inset, w - 2 * inset, h - 2 * inset);
            preview.alternate().ifPresent(other -> { g.clipRect(w / 2, 0, w, h); tile.miniature(g, other, inset, inset, w - 2 * inset, h - 2 * inset); });
            g.setClip(clip);
        }
        int top = h + ThemePainting.metric(c, DesignMetrics.Role.THEME_PREVIEW_CAPTION_GAP), ringSize = ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING);
        paintRadio(g, c, 0, top, tile.isSelected(), tile.isEnabled());
        if (tile.focus != null && tile.focus.visible()) {
            g.setColor(ThemePainting.color(c, SemanticColor.BORDER_FOCUS));
            g.draw(new Ellipse2D.Double(-1, top - 1, ringSize + 1, ringSize + 1));
        }
        g.setFont(tile.getFont()); g.setColor(ThemePainting.painted(c, label));
        var fm = g.getFontMetrics();
        int x = ringSize + ThemePainting.metric(c, DesignMetrics.Role.RADIO_LABEL_GAP);
        g.drawString(tile.option == null ? "" : tile.option.label(), x, top + (ringSize - fm.getHeight()) / 2 + fm.getAscent());
    }

    /** A radio at ({@code x}, {@code y}): the ring, its inside and, when selected, the dot. */
    void paintRadio(Graphics2D g, KitContext c, double x, double y, boolean selected, boolean enabled) {
        int size = ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING), dotSize = ThemePainting.metric(c, DesignMetrics.Role.RADIO_DOT);
        int line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE);
        g.setColor(!enabled ? ThemePainting.role(c, DerivedColor.BORDER_DIVIDER) : ThemePainting.painted(c, selected ? selectedRing : ring));
        g.fill(new Ellipse2D.Double(x, y, size, size));
        g.setColor(!enabled ? ThemePainting.role(c, DerivedColor.SURFACE_TABLE_HEADER) : ThemePainting.color(c, SemanticColor.SURFACE_INPUT));
        g.fill(new Ellipse2D.Double(x + line, y + line, size - 2.0 * line, size - 2.0 * line));
        if (selected) {
            g.setColor(enabled ? ThemePainting.painted(c, dot) : ThemePainting.color(c, SemanticColor.TEXT_PLACEHOLDER));
            double offset = (size - dotSize) / 2.0;
            g.fill(new Ellipse2D.Double(x + offset, y + offset, dotSize, dotSize));
        }
    }

    /** An option's glyph: its radio, then its hue's swatch when it has one. */
    private final class Radio implements Icon {
        private final KitContext c; private final AbstractButton option; private final Optional<Nodes.Hue> hue;
        Radio(KitContext c, AbstractButton option, Optional<Nodes.Hue> hue) { this.c = c; this.option = option; this.hue = hue; }
        private int ring() { return ThemePainting.metric(c, DesignMetrics.Role.RADIO_RING); }
        @Override public int getIconWidth() {
            return ring() + hue.map(h -> ThemePainting.metric(c, DesignMetrics.Role.RADIO_LABEL_GAP) + ThemePainting.metric(c, DesignMetrics.Role.OPTION_SWATCH)).orElse(0);
        }
        @Override public int getIconHeight() { return ring(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                if (option instanceof Choices.SegmentButton button && button.focus != null && button.focus.visible()) {
                    int halo = ThemePainting.metric(c, DesignMetrics.Role.FOCUS_RING);
                    g.setColor(ColorMath.opacity(ThemePainting.color(c, SemanticColor.BORDER_FOCUS), ThemePainting.focusHaloShare(c, false)));
                    g.fill(new Ellipse2D.Double(x - halo, y - halo, ring() + 2.0 * halo, ring() + 2.0 * halo));
                }
                paintRadio(g, c, x, y, option.isSelected(), option.isEnabled());
                hue.ifPresent(h -> {
                    int size = ThemePainting.metric(c, DesignMetrics.Role.OPTION_SWATCH);
                    g.setColor(ThemePainting.painted(c, swatchOf(h)));
                    g.fill(new RoundRectangle2D.Double(x + ring() + ThemePainting.metric(c, DesignMetrics.Role.RADIO_LABEL_GAP), y + (ring() - size) / 2.0, size, size, 0, 0));
                });
            } finally { g.dispose(); }
        }
    }

    /** Children left to right, {@code gapRole} apart (none when null), each at its own width, wrapping line by line. */
    static final class RowLayout implements Composition.WidthLayout {
        private final KitContext context;
        private final DesignMetrics.Role gapRole;
        RowLayout(KitContext context, DesignMetrics.Role gapRole) { this.context = context; this.gapRole = gapRole; }
        int gap() { return gapRole == null ? 0 : ThemePainting.metric(context, gapRole); }
        List<List<Component>> lines(Container parent, int width) {
            Insets i = parent.getInsets(); int room = Math.max(0, width - i.left - i.right), used = 0;
            var lines = new ArrayList<List<Component>>(); var line = new ArrayList<Component>();
            for (var child : parent.getComponents()) if (child.isVisible()) {
                int w = child.getPreferredSize().width;
                if (!line.isEmpty() && used + gap() + w > room) { lines.add(line); line = new ArrayList<>(); used = 0; }
                used += (line.isEmpty() ? 0 : gap()) + w; line.add(child);
            }
            if (!line.isEmpty()) lines.add(line);
            return lines;
        }
        private static int lineHeight(List<Component> line) { return line.stream().mapToInt(c -> c.getPreferredSize().height).max().orElse(0); }
        private int between() { return gapRole == null ? 0 : ThemePainting.metric(context, DesignMetrics.Role.ROW_GAP); }
        @Override public int height(Container parent, int width) {
            Insets i = parent.getInsets(); var lines = lines(parent, width);
            return i.top + i.bottom + lines.stream().mapToInt(RowLayout::lineHeight).sum() + Math.max(0, lines.size() - 1) * between();
        }
        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets i = parent.getInsets(); int w = 0, h = 0, n = 0;
            for (var child : parent.getComponents()) if (child.isVisible()) {
                var size = child.getPreferredSize(); w += (n++ > 0 ? gap() : 0) + size.width; h = Math.max(h, size.height);
            }
            return new Dimension(i.left + i.right + w, i.top + i.bottom + h);
        }
        @Override public void layoutContainer(Container parent) {
            Insets i = parent.getInsets(); int y = i.top;
            for (var line : lines(parent, parent.getWidth())) {
                int x = i.left, h = lineHeight(line);
                for (var child : line) {
                    var size = child.getPreferredSize();
                    child.setBounds(x, y + (h - size.height) / 2, Math.min(size.width, Math.max(0, parent.getWidth() - i.right - x)), size.height);
                    x += size.width + gap();
                }
                y += h + between();
            }
        }
    }
}
