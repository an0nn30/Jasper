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
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JLabel;

/**
 * The Run window's presentation (Enterprise presentation spec 11.2, the 06 Run Dialog mocks), applied to the controls of
 * a view whose host called {@link KitView#runWindow(boolean)}: its text colours and faces, its RESULTS rows, heading
 * bands and counts, and its help row's accent tile. Every value is a kit role; nothing here is read for a view without
 * the flag. Every text colour is a declared pair (spec 7.3 and 16.1.1): {@link #pairs(Kit)} painted through their
 * guards, and {@link #authored()} painted as authored (plan 8a Rulings 8); the tile's edge is {@link #tileEdge(Kit)}.
 * Public for those tables, which jasper-app's PaintedContrastTest paints.
 */
public final class RunWindowPresentation {
    private RunWindowPresentation() { }

    /**
     * A pair the Run window paints as authored, with no guard (plan 8a Rulings 8).
     *
     * @param slot the role painted
     * @param against the role it is painted on
     */
    public record Authored(ColorRole slot, ColorRole against) {
        /** Both roles are required. */
        public Authored { Objects.requireNonNull(slot, "slot"); Objects.requireNonNull(against, "against"); }
    }

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
    /** A row's detail and tag in the list box, as authored. */
    public static final Authored ROW_DETAIL = new Authored(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_CARD);
    /** The matches count on a heading band, as authored. */
    public static final Authored COUNT = new Authored(SemanticColor.TEXT_PLACEHOLDER, DerivedColor.SURFACE_TABLE_HEADER);
    /** A selected row's detail and tag in the selection's supporting ink, as the card paints them. */
    public static final Authored SELECTED_DETAIL = new Authored(SemanticColor.TEXT_ONACCENT_SECONDARY, SemanticColor.ACCENT);

    /** "Open:", the checkbox's label, the help text and the detail line's label on {@code kit}'s dialog surface. */
    public static PaintPair labelPair(Kit kit) {
        return new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, kit.colors().dialogSurface(), SurfaceContrast.Guard.TEXT);
    }

    /** The detail line's value on {@code kit}'s dialog surface. */
    public static PaintPair valuePair(Kit kit) {
        return new PaintPair(SemanticColor.TEXT_SECONDARY, kit.colors().dialogSurface(), SurfaceContrast.Guard.TEXT);
    }

    /** Every guarded pair the Run window paints under {@code kit}: TITLE, SHORTCUT, the label pair, the value pair, HEADING, SELECTED_TITLE, LIST_TEXT. */
    public static List<PaintPair> pairs(Kit kit) {
        return List.of(TITLE, SHORTCUT, labelPair(kit), valuePair(kit), HEADING, SELECTED_TITLE, LIST_TEXT);
    }

    /** Every pair the Run window paints as authored: ROW_DETAIL, COUNT, SELECTED_DETAIL. */
    public static List<Authored> authored() { return List.of(ROW_DETAIL, COUNT, SELECTED_DETAIL); }

    /**
     * {@code text}'s Run window face and colour, after its control set the role's own: WINDOW_TITLE and the title row's
     * CAPTION through {@link #TITLE} and {@link #SHORTCUT}; LABEL, DESCRIPTION and BODY_STRONG through the label pair,
     * DESCRIPTION at BODY's size on the help's line, BODY_STRONG semibold; MONO in the small data face through the value
     * pair. A list's CAPTION, DESCRIPTION and MONO texts are repainted by {@link #row}. A selected row's ink and a
     * severity keep their colour.
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
                if (!inked) label.setForeground(SurfaceContrast.painted(theme, labelPair(kit)));
            }
            case BODY_STRONG -> {
                label.setFont(ThemePainting.font(context, Typography.Role.PANEL_TITLE));
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
     * only) at the band's end as {@link #COUNT}; a row's title through {@link #SELECTED_TITLE} when selected, or
     * {@link #LIST_TEXT} for a DESCRIPTION title; its detail in the small data face, cut at its end, and its detail and tag
     * as {@link #ROW_DETAIL}, or {@link #SELECTED_DETAIL} when selected.
     */
    static void row(KitContext context, InteractiveRows.Row row, ListItem item, boolean selected) {
        var theme = context.theme();
        if (item.heading()) {
            var title = item.title();
            for (int i = 0; i < title.size(); i++) {
                boolean count = i > 0;
                label(row, title.get(i)).ifPresent(label -> {
                    label.setFont(ThemePainting.font(context, count ? Typography.Role.CAPTION : Typography.Role.RUN_HEADING));
                    label.setForeground(count ? COUNT.slot().color(theme) : SurfaceContrast.painted(theme, HEADING));
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
        var faint = (selected ? SELECTED_DETAIL : ROW_DETAIL).slot().color(theme);
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

    /** The help tile: the accent inside a hairline of {@link #tileEdge(Kit)} at the prompt tile's radius. */
    static void paintTile(Stacks.Tile tile, Graphics2D g) {
        var context = tile.context;
        ThemePainting.antialias(g);
        int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        double radius = ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE_RADIUS), inner = Math.max(0, radius - line);
        var outerShape = new RoundRectangle2D.Double(0, 0, tile.getWidth(), tile.getHeight(), 2 * radius, 2 * radius);
        var innerShape = new RoundRectangle2D.Double(line, line, tile.getWidth() - 2.0 * line, tile.getHeight() - 2.0 * line, 2 * inner, 2 * inner);
        g.setColor(ThemePainting.color(context, SemanticColor.ACCENT)); g.fill(innerShape);
        var ring = new Area(outerShape); ring.subtract(new Area(innerShape));
        g.setColor(SurfaceContrast.painted(context.theme(), tileEdge(context.kit()))); g.fill(ring);
    }

    /** The help row: the tile, then the text beside it; the text block centred on the tile when shorter, and the reverse. */
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
            int textHeight = text(parent, parent.getWidth()), y = Math.max(0, (size.height - textHeight) / 2), width = room(parent.getWidth());
            owner.tile.setBounds(0, Math.max(0, (textHeight - size.height) / 2), size.width, size.height);
            for (var line : lines(parent)) {
                int height = Composition.height(line, width);
                line.setBounds(size.width + gap(), y, width, height);
                y += height + ThemePainting.metric(owner.context(), DesignMetrics.Role.DESCRIPTION_GAP);
            }
        }
    }
}
