package dev.jasper.ui.swing;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.ColorRole;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.JLabel;

/**
 * The Run window's presentation (Enterprise presentation spec 11.2, the 06 Run Dialog mocks), applied to the controls of
 * a view whose host called {@link KitView#runWindow(boolean)}: its text colours and faces, its RESULTS rows, heading
 * bands and counts, and its help row's accent tile. Every value is a kit role; nothing here is read for a view without
 * the flag. Every pair the Run window paints is declared and painted through its guard (spec 7.3 and 16.1.1): the text
 * pairs {@link #pairs(Kit)}, the help glyph {@link #HELP_GLYPH}, the field and list box edges {@link #edges(Kit)}, the hue
 * squares {@link #swatches()} and the tile's edge {@link #tileEdge(Kit)}. Public for those tables, which jasper-app's
 * PaintedContrastTest paints.
 */
public final class RunWindowPresentation {
    private RunWindowPresentation() { }

    /** The help row's most lines: the window keeps one height whatever the chosen scope's description. */
    public static final int HELP_LINES = 2;

    /** The title row's "Run" on the title band. */
    public static final PaintPair TITLE = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT);
    /** The opener's shortcut on the title band. */
    public static final PaintPair SHORTCUT = new PaintPair(SemanticColor.TEXT_TERTIARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT);
    /** A list heading's title on its band. */
    public static final PaintPair HEADING = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
    /** A selected row's title on the accent. */
    public static final PaintPair SELECTED_TITLE = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.TEXT);
    /** A "More in" row's (or a disabled row's) title in the list box. */
    public static final PaintPair LIST_TEXT = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
    /** A row's detail and tag in the list box. */
    public static final PaintPair ROW_DETAIL = new PaintPair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
    /** The matches count on a heading band. */
    public static final PaintPair COUNT = new PaintPair(SemanticColor.TEXT_PLACEHOLDER, DerivedColor.SURFACE_TABLE_HEADER, SurfaceContrast.Guard.TEXT);
    /** A selected row's detail and tag in the selection's supporting ink, as the card paints them. */
    public static final PaintPair SELECTED_DETAIL = new PaintPair(SemanticColor.TEXT_ONACCENT_SECONDARY, SemanticColor.ACCENT, SurfaceContrast.Guard.TEXT);
    /** The help tile's glyph in the accent's ink. */
    public static final PaintPair HELP_GLYPH = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.INDICATOR);
    /** The field box's edge on the field's own surface. */
    public static final PaintPair FIELD_EDGE = new PaintPair(SemanticColor.BORDER_INPUT, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.CONTROL);

    /** "Open:", the checkbox's label, the help text and the detail line's label on {@code kit}'s dialog surface. */
    public static PaintPair labelPair(Kit kit) {
        return new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, kit.colors().dialogSurface(), SurfaceContrast.Guard.TEXT);
    }

    /** The detail line's value on {@code kit}'s dialog surface. */
    public static PaintPair valuePair(Kit kit) {
        return new PaintPair(SemanticColor.TEXT_SECONDARY, kit.colors().dialogSurface(), SurfaceContrast.Guard.TEXT);
    }

    /** The list box's edge on {@code kit}'s dialog surface. */
    public static PaintPair boxEdge(Kit kit) {
        return new PaintPair(SemanticColor.BORDER_CONTROL, kit.colors().dialogSurface(), SurfaceContrast.Guard.CONTROL);
    }

    /**
     * Every text pair the Run window paints under {@code kit}: TITLE, SHORTCUT, the label pair, the value pair, HEADING,
     * SELECTED_TITLE, LIST_TEXT, ROW_DETAIL, COUNT, SELECTED_DETAIL.
     */
    public static List<PaintPair> pairs(Kit kit) {
        return List.of(TITLE, SHORTCUT, labelPair(kit), valuePair(kit), HEADING, SELECTED_TITLE, LIST_TEXT, ROW_DETAIL, COUNT, SELECTED_DETAIL);
    }

    /** The field box's and the list box's edges under {@code kit}: FIELD_EDGE, then {@link #boxEdge(Kit)}. */
    public static List<PaintPair> edges(Kit kit) { return List.of(FIELD_EDGE, boxEdge(kit)); }

    /** The role a hue square of {@code hue} is filled with. */
    public static SemanticColor hueRole(Nodes.Hue hue) {
        return switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL;
        };
    }

    /** A hue square of {@code hue} on the list box, or on the accent when its row is selected, as an indicator. */
    public static PaintPair swatch(Nodes.Hue hue, boolean selected) {
        return new PaintPair(hueRole(hue), selected ? SemanticColor.ACCENT : SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.INDICATOR);
    }

    /** Every hue square pair: each hue's role on the list box, then on the accent. */
    public static List<PaintPair> swatches() {
        var hues = List.of(Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE, Nodes.Hue.NEUTRAL);
        var pairs = new java.util.ArrayList<PaintPair>();
        for (boolean selected : List.of(false, true)) for (var hue : hues) pairs.add(swatch(hue, selected));
        return List.copyOf(pairs);
    }

    /**
     * A Run window hue square: its hue's fill, as authored, and, when that fill misses {@link #swatch}'s guard on what it
     * is painted on, a hairline ring in the guarded colour around it (spec 7.3's checked-box repair), so the square never
     * vanishes into the selection.
     */
    static javax.swing.Icon swatchIcon(KitContext context, Nodes.Hue hue, boolean selected, int extent) {
        var pair = swatch(hue, selected);
        var theme = context.theme();
        Color fill = pair.slot().color(theme), painted = SurfaceContrast.painted(theme, pair);
        int line = Math.max(1, ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE));
        return new javax.swing.Icon() {
            @Override public int getIconWidth() { return extent; }
            @Override public int getIconHeight() { return extent; }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
                graphics.setColor(fill); graphics.fillRect(x, y, extent, extent);
                if (painted.equals(fill)) return;
                graphics.setColor(painted);
                graphics.fillRect(x, y, extent, line); graphics.fillRect(x, y + extent - line, extent, line);
                graphics.fillRect(x, y, line, extent); graphics.fillRect(x + extent - line, y, line, extent);
            }
        };
    }

    /**
     * {@code text}'s Run window face and colour, after its control set the role's own: WINDOW_TITLE and the title row's
     * CAPTION through {@link #TITLE} and {@link #SHORTCUT}; LABEL, DESCRIPTION and BODY_STRONG through the label pair,
     * DESCRIPTION at BODY's size on the help's line, BODY_STRONG in the headings' semibold (the mocks' 11 px "Session:");
     * MONO in the small data face through the value pair. A list's CAPTION, DESCRIPTION and MONO texts are repainted by
     * {@link #row}. A selected row's ink and a severity keep their colour. A DESCRIPTION's lines sit centred in their line
     * box, as the mocks' CSS centres them, and it shows at most {@link #HELP_LINES} lines, the last cut with an ellipsis.
     */
    static void text(KitContext context, Nodes.Text text, Labels.LiteralLabel label, boolean inked) {
        if (text.severity().isPresent()) return;
        var theme = context.theme();
        Kit kit = context.kit();
        switch (text.role()) {
            case WINDOW_TITLE -> { if (!inked) label.setForeground(SurfaceContrast.painted(theme, TITLE)); }
            case CAPTION -> { if (!inked) label.setForeground(SurfaceContrast.painted(theme, SHORTCUT)); }
            case LABEL -> { if (!inked) label.setForeground(SurfaceContrast.painted(theme, labelPair(kit))); }
            case DESCRIPTION -> {
                var font = ThemePainting.font(context, Typography.Role.BODY);
                label.setFont(font);
                label.wrapped.lineHeight = Math.round(context.metrics().value(DesignMetrics.Role.RUN_WINDOW_HELP_LINE) * font.getSize2D() / 12f);
                label.wrapped.centeredLeading = true;
                label.wrapped.maxLines = HELP_LINES;
                if (!inked) label.setForeground(SurfaceContrast.painted(theme, labelPair(kit)));
            }
            case BODY_STRONG -> {
                label.setFont(ThemePainting.font(context, Typography.Role.RUN_HEADING));
                if (!inked) label.setForeground(SurfaceContrast.painted(theme, labelPair(kit)));
            }
            case MONO -> {
                label.setFont(ThemePainting.font(context, Typography.Role.RUN_DETAIL));
                if (!inked) label.setForeground(SurfaceContrast.painted(theme, valuePair(kit)));
            }
            default -> { }
        }
    }

    /** A RESULTS row's height: the row, or a heading's band and its rule. */
    static int rowHeight(KitContext context, boolean heading) {
        return heading ? ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_HEADING_HEIGHT) + ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE)
            : ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_ROW_HEIGHT);
    }

    /**
     * A heading's title semibold through {@link #HEADING} and its count (its second title node: a heading has a title
     * only) at the band's end through {@link #COUNT}; a row's title through {@link #SELECTED_TITLE} when selected, or
     * {@link #LIST_TEXT} for a DESCRIPTION title; its detail in the small data face, cut at its end, and its detail and tag
     * through {@link #ROW_DETAIL}, or {@link #SELECTED_DETAIL} when selected.
     */
    static void row(KitContext context, InteractiveRows.Row row, ListItem item, boolean selected) {
        var theme = context.theme();
        if (item.heading()) {
            var title = item.title();
            for (int i = 0; i < title.size(); i++) {
                boolean count = i > 0;
                label(row, title.get(i)).ifPresent(label -> {
                    label.setFont(ThemePainting.font(context, count ? Typography.Role.CAPTION : Typography.Role.RUN_HEADING));
                    label.setForeground(SurfaceContrast.painted(theme, count ? COUNT : HEADING));
                });
            }
            // The row's slot gave the title panel an inline layout this populate; the heading's replaces it until the next.
            row.title.setLayout(new HeadingLayout(ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_ROW_GAP)));
            return;
        }
        for (var node : item.title()) label(row, node).ifPresent(label -> {
            if (selected) label.setForeground(SurfaceContrast.painted(theme, SELECTED_TITLE));
            else if (((Nodes.Text) node).role() == Nodes.TextRole.DESCRIPTION) label.setForeground(SurfaceContrast.painted(theme, LIST_TEXT));
        });
        var faint = SurfaceContrast.painted(theme, selected ? SELECTED_DETAIL : ROW_DETAIL);
        for (var node : item.subtitle()) label(row, node).ifPresent(label -> {
            label.setFont(ThemePainting.font(context, Typography.Role.RUN_DETAIL));
            label.setForeground(faint);
            // The mocks cut a long detail at its end, as the row's title is cut.
            label.putClientProperty("Jasper.leadingEllipsis", false);
        });
        for (var node : item.trailing()) label(row, node).ifPresent(label -> label.setForeground(faint));
    }

    private static Optional<JLabel> label(InteractiveRows.Row row, Nodes.Node node) {
        var control = row.controls.get(node.id());
        return node instanceof Nodes.Text && control != null && control.component instanceof JLabel label ? Optional.of(label) : Optional.empty();
    }

    /** A heading's title panel across the row, inset on both sides, above the band's rule; its layout places the count. */
    static void headingLayout(InteractiveRows.Row row) {
        var context = row.owner.context();
        int side = ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_ROW_SIDE);
        int band = Math.max(0, row.getHeight() - ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE));
        row.leading.setBounds(0, 0, 0, 0);
        row.subtitle.setBounds(0, 0, 0, 0);
        row.trailing.setBounds(0, 0, 0, 0);
        row.title.setBounds(side, 0, Math.max(0, row.getWidth() - 2 * side), band);
        row.title.doLayout();
    }

    /** A heading's band on {@code surface.tableHeader} over a {@code border.row} rule. */
    static void paintHeading(InteractiveRows.Row row, Graphics g) {
        var theme = row.owner.context().theme();
        int hair = ThemePainting.metric(row.owner.context(), DesignMetrics.Role.HAIRLINE);
        g.setColor(DerivedColor.SURFACE_TABLE_HEADER.color(theme));
        g.fillRect(0, 0, row.getWidth(), Math.max(0, row.getHeight() - hair));
        g.setColor(DerivedColor.BORDER_ROW.color(theme));
        g.fillRect(0, row.getHeight() - hair, row.getWidth(), hair);
    }

    /** A Run window heading's title panel: the heading at its start and the count, its last child, at its end. */
    private static final class HeadingLayout implements LayoutManager {
        private final int gap;

        HeadingLayout(int gap) { this.gap = gap; }

        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = 0, height = 0, n = parent.getComponentCount();
            for (int i = 0; i < n; i++) {
                var size = parent.getComponent(i).getPreferredSize();
                width += size.width; height = Math.max(height, size.height);
            }
            return new Dimension(width + Math.max(0, n - 1) * gap, height);
        }

        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, preferredLayoutSize(parent).height); }

        @Override public void layoutContainer(Container parent) {
            int n = parent.getComponentCount(), w = parent.getWidth(), h = parent.getHeight();
            if (n == 0) return;
            int count = 0;
            if (n > 1) {
                var last = parent.getComponent(n - 1);
                count = Math.min(last.getPreferredSize().width, w);
                last.setBounds(w - count, 0, count, h);
            }
            var heading = parent.getComponent(0);
            heading.setBounds(0, 0, Math.max(0, Math.min(heading.getPreferredSize().width, w - (n > 1 ? count + gap : 0))), h);
        }
    }

    /** The help tile's edge on {@code kit}'s dialog surface: {@code accent.edge}, guarded as an indicator. */
    public static PaintPair tileEdge(Kit kit) {
        return new PaintPair(DerivedColor.ACCENT_EDGE, kit.colors().dialogSurface(), SurfaceContrast.Guard.INDICATOR);
    }

    /**
     * The help row's fixed height in {@code context}: the tile, or {@link #HELP_LINES} lines of the help text, whichever is
     * taller, so the window keeps its height whatever the help row says.
     */
    public static int helpHeight(KitContext context) {
        var font = ThemePainting.font(context, Typography.Role.BODY);
        int line = Math.round(context.metrics().value(DesignMetrics.Role.RUN_WINDOW_HELP_LINE) * font.getSize2D() / 12f);
        int measured = new JLabel().getFontMetrics(font).getHeight();
        return Math.max(ThemePainting.metric(context, DesignMetrics.Role.RUN_WINDOW_TILE), HELP_LINES * Math.max(line, measured));
    }

    /** The help row's layout: the tile, then its text beside it at the Run window's gap, the shorter centred on the other. */
    static LayoutManager helpLayout(Stacks.StackControl owner) { return new HelpLayout(owner); }

    /** The help row's children: its glyph takes the accent's ink, and its text reads from the tile instead of centring. */
    static void helpChildren(List<ControlRegistry.Control> controls) {
        for (var control : controls) {
            if (control.component instanceof Labels.LiteralLabel label) label.wrapped.centered = false;
            if (control instanceof Labels.IconControl icon && !(icon.inked && icon.ink == InteractiveRows.Ink.ACCENT)) {
                icon.inked = true; icon.ink = InteractiveRows.Ink.ACCENT; icon.refresh();
            }
        }
    }

    /** A help row's children once the flag is off again: the glyph {@link #helpChildren} inked returns to its own colours. */
    static void releaseHelpChildren(List<ControlRegistry.Control> controls) {
        for (var control : controls)
            if (control instanceof Labels.IconControl icon && icon.inked && icon.ink == InteractiveRows.Ink.ACCENT) {
                icon.inked = false; icon.ink = InteractiveRows.Ink.SELECTION; icon.refresh();
            }
    }

    /**
     * The help tile: the classic prompt's accent tile (plan 4b), inside a hairline of {@link #tileEdge(Kit)} at the prompt
     * tile's radius, whatever the kit's prompt face.
     */
    static void paintTile(Stacks.Tile tile, Graphics2D g) { ClassicPromptFace.paintAccentTile(tile, g, tileEdge(tile.context.kit())); }

    /**
     * The help row: the tile, then the text beside it; the text block centred on the tile when shorter, and the reverse,
     * and the two centred in a taller row. The text block then sits {@code RUN_WINDOW_HELP_TEXT_TOP} lower, where the
     * mocks set its first line.
     */
    private static final class HelpLayout implements Composition.WidthLayout {
        private final Stacks.StackControl owner;

        HelpLayout(Stacks.StackControl owner) { this.owner = owner; }

        private int gap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.RUN_WINDOW_TILE_GAP); }

        private List<Component> lines(Container parent) {
            return Arrays.stream(parent.getComponents()).filter(c -> c.isVisible() && c != owner.tile).toList();
        }

        private int room(int width) { return Math.max(0, width - owner.tile.getPreferredSize().width - gap()); }

        private int text(Container parent, int width) {
            var lines = lines(parent);
            int height = 0;
            for (int i = 0; i < lines.size(); i++)
                height += (i > 0 ? ThemePainting.metric(owner.context(), DesignMetrics.Role.DESCRIPTION_GAP) : 0) + Composition.height(lines.get(i), room(width));
            return height;
        }

        @Override public int height(Container parent, int width) { return Math.max(owner.tile.getPreferredSize().height, text(parent, width)); }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = owner.tile.getPreferredSize().width + gap() + lines(parent).stream().mapToInt(c -> c.getPreferredSize().width).max().orElse(0);
            return new Dimension(width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }

        @Override public void layoutContainer(Container parent) {
            var size = owner.tile.getPreferredSize();
            // The row's content centred in a taller row (the Run window's fixed help height).
            int textHeight = text(parent, parent.getWidth()), top = Math.max(0, (parent.getHeight() - height(parent, parent.getWidth())) / 2);
            int y = top + Math.max(0, (size.height - textHeight) / 2) + ThemePainting.metric(owner.context(), DesignMetrics.Role.RUN_WINDOW_HELP_TEXT_TOP);
            int width = room(parent.getWidth());
            owner.tile.setBounds(0, top + Math.max(0, (textHeight - size.height) / 2), size.width, size.height);
            for (var line : lines(parent)) {
                int height = Composition.height(line, width);
                line.setBounds(size.width + gap(), y, width, height);
                y += height + ThemePainting.metric(owner.context(), DesignMetrics.Role.DESCRIPTION_GAP);
            }
        }
    }
}
