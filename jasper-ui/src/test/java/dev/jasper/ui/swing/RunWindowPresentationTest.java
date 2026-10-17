package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;

/** Enterprise presentation plan 8a: KitView.runWindow draws the Run window's list, help tile, query and text. */
class RunWindowPresentationTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Text text(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, READY, value, role); }

    /** A heading with a count (its second title node: a heading has a title only), a selected session row and a snippet row with a tag. */
    static ViewSpec list() {
        var items = List.of(
            new ListItem("h", Optional.empty(), List.of(text("h.title", "Matches", Nodes.TextRole.CAPTION),
                text("h.count", "1 of 2", Nodes.TextRole.CAPTION)), List.of(), List.of(), true),
            new ListItem("a", Optional.of(new Nodes.Icon("a.swatch", READY, "file", Nodes.Hue.GREEN, Nodes.IconSize.SWATCH, "Session")),
                List.of(text("a.title", "Mac", Nodes.TextRole.BODY)), List.of(text("a.detail", "dustin@192.168.1.119:22", Nodes.TextRole.MONO)), List.of()),
            new ListItem("b", Optional.of(new Nodes.Icon("b.swatch", READY, "file", Nodes.Hue.PURPLE, Nodes.IconSize.SWATCH, "Snippet")),
                List.of(text("b.title", "gcloud auth", Nodes.TextRole.BODY)), List.of(text("b.detail", "gcloud auth login", Nodes.TextRole.MONO)),
                List.of(text("b.tag", "K", Nodes.TextRole.CAPTION))));
        return new ViewSpec(new Nodes.ItemList("results", READY, Nodes.ListKind.RESULTS, items, Set.of("a"), "run", List.of(),
            Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(8)));
    }

    static List<Component> rows(KitView view) {
        var canvas = (Container) find(view, "results", JScrollPane.class).getViewport().getView();
        var rows = new ArrayList<Component>();
        for (Component child : canvas.getComponents()) if (child instanceof InteractiveRows.Row && child.isVisible()) rows.add(child);
        rows.sort(java.util.Comparator.comparingInt(Component::getY));
        return rows;
    }

    static <T extends Component> T find(Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) return type.cast(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, name, type);
            if (found != null) return found;
        }
        return null;
    }

    static int m(KitContext context, DesignMetrics.Role role) { return context.metrics().value(role); }

    @Test void theListTakesTheRunWindowsRowsBandsAndHueSquares() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, list(), event -> { })) {
                KitFixtures.render(view, 436, 120, 1);
                int defaultHeading = rows(view).getFirst().getHeight();
                view.runWindow(true);
                BufferedImage image = KitFixtures.render(view, 436, 120, 1);
                var rows = rows(view);
                assertThat(rows).extracting(Component::getHeight).containsExactly(19, 21, 21);
                assertThat(defaultHeading).as("the flag is what changes it").isNotEqualTo(19);
                var theme = context.theme();
                assertThat(new Color(image.getRGB(200, 5))).as("the heading band").isEqualTo(DerivedColor.SURFACE_TABLE_HEADER.color(theme));
                assertThat(new Color(image.getRGB(200, 18))).as("its rule").isEqualTo(DerivedColor.BORDER_ROW.color(theme));
                assertThat(new Color(image.getRGB(300, 19 + 10))).as("the selected row").isEqualTo(theme.semantic().color(SemanticColor.ACCENT));
                int side = m(context, DesignMetrics.Role.RUN_WINDOW_ROW_SIDE), square = m(context, DesignMetrics.Role.RUN_WINDOW_SWATCH);
                assertThat(new Color(image.getRGB(side + square / 2, 19 + 10))).as("the square keeps its hue on the selection")
                    .isEqualTo(theme.semantic().color(SemanticColor.HUE_GREEN));
                assertThat(new Color(image.getRGB(side + square + 1, 19 + 10))).isEqualTo(theme.semantic().color(SemanticColor.ACCENT));
                var count = find(view, "h.count", JLabel.class);
                assertThat(count.getForeground()).as("the muted count, guarded as text").isEqualTo(SurfaceContrast.painted(theme, RunWindowPresentation.COUNT));
                assertThat(javax.swing.SwingUtilities.convertRectangle(count.getParent(), count.getBounds(), rows.getFirst()).getMaxX())
                    .as("the count ends at the row inset").isEqualTo(436 - side);
                var heading = find(view, "h.title", JLabel.class);
                assertThat(heading.getFont().getSize2D()).isEqualTo(11f);
                assertThat(heading.getForeground()).isEqualTo(theme.semantic().color(SemanticColor.TEXT_STRONG_SECONDARY));
                var detail = find(view, "b.detail", JLabel.class);
                assertThat(detail.getFont().getSize2D()).isEqualTo(10.5f);
                assertThat(detail.getForeground()).as("the muted detail, guarded as text").isEqualTo(SurfaceContrast.painted(theme, RunWindowPresentation.ROW_DETAIL));
                assertThat(detail.getClientProperty("Jasper.leadingEllipsis")).as("a long detail is cut at its end, as the mocks cut it").isEqualTo(false);
                assertThat(find(view, "b.tag", JLabel.class).getForeground()).isEqualTo(SurfaceContrast.painted(theme, RunWindowPresentation.ROW_DETAIL));
                var title = find(view, "b.title", JLabel.class);
                assertThat(javax.swing.SwingUtilities.convertPoint(title.getParent(), title.getX(), 0, rows.get(2)).x)
                    .isEqualTo(side + square + m(context, DesignMetrics.Role.RUN_WINDOW_ROW_GAP));
            }
        });
    }

    /**
     * Spec 7.3: a selected Commands row's BLUE square, which Enterprise Light paints in the accent itself, keeps its fill
     * and takes a hairline ring in the guarded colour, so it stays visible on the selection; an unselected one has none.
     */
    @Test void aSelectedSquareInTheAccentsOwnHueKeepsItsFillInsideAGuardedRing() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var items = List.of(
                new ListItem("a", Optional.of(new Nodes.Icon("a.swatch", READY, "file", Nodes.Hue.BLUE, Nodes.IconSize.SWATCH, "Command")),
                    List.of(text("a.title", "New Editor File", Nodes.TextRole.BODY)), List.of(), List.of()),
                new ListItem("b", Optional.of(new Nodes.Icon("b.swatch", READY, "file", Nodes.Hue.BLUE, Nodes.IconSize.SWATCH, "Command")),
                    List.of(text("b.title", "Settings", Nodes.TextRole.BODY)), List.of(), List.of()));
            var spec = new ViewSpec(new Nodes.ItemList("results", READY, Nodes.ListKind.RESULTS, items, Set.of("a"), "run", List.of(),
                Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(2)));
            try (var view = new KitView(context, spec, event -> { })) {
                view.runWindow(true);
                var image = KitFixtures.render(view, 436, 120, 1);
                var rows = rows(view);
                var theme = context.theme();
                var accent = theme.semantic().color(SemanticColor.ACCENT);
                var blue = theme.semantic().color(SemanticColor.HUE_BLUE);
                assertThat(blue).as("the fixture's premise: Enterprise Light's blue is its accent").isEqualTo(accent);
                int side = m(context, DesignMetrics.Role.RUN_WINDOW_ROW_SIDE), square = m(context, DesignMetrics.Role.RUN_WINDOW_SWATCH), x = side + square / 2;
                int top = rows.get(0).getY(), end = top + rows.get(0).getHeight();
                while (top < end && image.getRGB(x, top) == accent.getRGB()) top++;
                var ring = SurfaceContrast.painted(theme, RunWindowPresentation.swatch(Nodes.Hue.BLUE, true));
                assertThat(dev.jasper.ui.theme.ColorMath.contrast(ring, accent)).as("the ring meets the indicator guard").isGreaterThanOrEqualTo(3.0);
                assertThat(new Color(image.getRGB(x, top))).as("the selected square's ring").isEqualTo(ring);
                assertThat(new Color(image.getRGB(x, top + square / 2))).as("its fill, the mock's hue square").isEqualTo(blue);
                assertThat(top + square).as("the ring lies inside the square").isLessThanOrEqualTo(end);
                var card = theme.semantic().color(SemanticColor.SURFACE_CARD);
                top = rows.get(1).getY(); end = top + rows.get(1).getHeight();
                // The list is transparent off the selection: the list box under it paints surface.card.
                while (top < end && (image.getRGB(x, top) == card.getRGB() || image.getRGB(x, top) >>> 24 == 0)) top++;
                assertThat(new Color(image.getRGB(x, top))).as("an unselected square has no ring").isEqualTo(blue);
            }
        });
    }

    /** The Run window's help row: its PROMPT_HEADER stack with the play glyph and the help text. */
    static ViewSpec help() {
        return new ViewSpec(new Nodes.Stack("help", READY, Nodes.LayoutRole.PROMPT_HEADER, List.of(
            new Nodes.Icon("help.icon", READY, "play.filled", Nodes.Hue.BLUE, Nodes.IconSize.PROMPT, "Run"),
            text("help.text", "Type the name of a session, command, file or snippet, and Jasper will open it for you.", Nodes.TextRole.DESCRIPTION)), ""));
    }

    @Test void turningTheFlagOffPaintsTheClassicHelpRowAgain() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, help(), event -> { })) {
                BufferedImage before = KitFixtures.render(view, 436, 60, 1);
                // The enterprise kit's prompt is plan 4b's classic face; the Run window's help row comes ahead of it (plan 8a R2).
                assertThat(((Labels.IconControl) view.control("help.icon")).facePair).as("the classic prompt's glyph pair").isNotNull();
                view.runWindow(true);
                KitFixtures.render(view, 436, 60, 1);
                assertThat(((Labels.IconControl) view.control("help.icon")).inked).as("the Run window inks the glyph").isTrue();
                assertThat(((Labels.IconControl) view.control("help.icon")).facePair).as("and the face leaves it alone").isNull();
                view.runWindow(false);
                BufferedImage after = KitFixtures.render(view, 436, 60, 1);
                assertThat(((Labels.IconControl) view.control("help.icon")).inked).as("and returns it").isFalse();
                assertThat(((Labels.IconControl) view.control("help.icon")).facePair).as("to the classic face").isNotNull();
                for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++)
                    assertThat(after.getRGB(x, y)).as("pixel %d,%d", x, y).isEqualTo(before.getRGB(x, y));
            }
        });
    }

    @Test void aRowKeepsTheRunWindowsDetailAcrossUpdates() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, list(), event -> { })) {
                view.runWindow(true);
                KitFixtures.render(view, 436, 120, 1);
                view.update(list());
                KitFixtures.render(view, 436, 120, 1);
                var placeholder = SurfaceContrast.painted(context.theme(), RunWindowPresentation.ROW_DETAIL);
                var detail = find(view, "b.detail", JLabel.class);
                assertThat(detail.getForeground()).as("the same descriptor").isEqualTo(placeholder);
                assertThat(detail.getClientProperty("Jasper.leadingEllipsis")).isEqualTo(false);
                var items = new ArrayList<>(((Nodes.ItemList) list().root()).items());
                var b = items.get(2);
                items.set(2, new ListItem(b.id(), b.leading(), b.title(), List.of(text("b.detail", "gcloud auth login --brief", Nodes.TextRole.MONO)), b.trailing()));
                view.update(new ViewSpec(new Nodes.ItemList("results", READY, Nodes.ListKind.RESULTS, items, Set.of("a"), "run", List.of(),
                    Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(8))));
                KitFixtures.render(view, 436, 120, 1);
                detail = find(view, "b.detail", JLabel.class);
                assertThat(detail.getText()).isEqualTo("gcloud auth login --brief");
                assertThat(detail.getForeground()).as("a changed detail").isEqualTo(placeholder);
                assertThat(detail.getClientProperty("Jasper.leadingEllipsis")).isEqualTo(false);
                assertThat(detail.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.RUN_DETAIL));
            }
        });
    }

    @Test void theHelpRowIsTheRunTileBesideItsTextInTheRunWindowsColours() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, help(), event -> { })) {
                KitFixtures.render(view, 436, 60, 1);
                int classicTile = find(view, "help.tile", JComponent.class).getWidth();
                view.runWindow(true);
                BufferedImage image = KitFixtures.render(view, 436, 60, 1);
                var tile = find(view, "help.tile", JComponent.class);
                assertThat(tile.getWidth()).isEqualTo(32);
                assertThat(classicTile).as("the classic prompt's tile").isEqualTo(m(context, DesignMetrics.Role.PROMPT_TILE));
                var label = find(view, "help.text", JLabel.class);
                var at = javax.swing.SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), view);
                var tileAt = javax.swing.SwingUtilities.convertRectangle(tile.getParent(), tile.getBounds(), view);
                assertThat(at.x - (tileAt.x + tileAt.width)).isEqualTo(m(context, DesignMetrics.Role.RUN_WINDOW_TILE_GAP));
                assertThat(label.getFont().getSize2D()).as("the help at BODY's size").isEqualTo(12f);
                assertThat(label.getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_STRONG_SECONDARY));
                assertThat(((Labels.LiteralLabel) label).wrapped.lineHeight).isEqualTo(18);
                var palette = context.theme().semantic();
                assertThat(new Color(image.getRGB(tileAt.x + 3, tileAt.y + 16))).as("the accent tile").isEqualTo(palette.color(SemanticColor.ACCENT));
                assertThat(new Color(image.getRGB(tileAt.x + 16, tileAt.y))).as("its edge")
                    .isEqualTo(dev.jasper.ui.theme.SurfaceContrast.painted(context.theme(), RunWindowPresentation.tileEdge(context.kit())));
                var centre = new Color(image.getRGB(tileAt.x + 16, tileAt.y + 16));
                var ink = palette.color(SemanticColor.TEXT_ONACCENT);
                // The glyph's raster covers its interior at 254 of 255, so the ink is matched within two levels a channel.
                assertThat(List.of(centre.getRed() - ink.getRed(), centre.getGreen() - ink.getGreen(), centre.getBlue() - ink.getBlue()))
                    .as("a filled play glyph in the on-accent ink").allMatch(difference -> Math.abs(difference) <= 2);
            }
        });
    }

    @Test void runWindowTextAndTheQuietQueryTakeTheMocksFacesAndColours() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var spec = new ViewSpec(new Nodes.Stack("row", READY, Nodes.LayoutRole.VERTICAL, List.of(
                text("label", "Open:", Nodes.TextRole.LABEL), text("strong", "Session:", Nodes.TextRole.BODY_STRONG),
                text("mono", "dustin@192.168.1.119:22", Nodes.TextRole.MONO),
                new Nodes.Field("query", READY, Nodes.FieldKind.TEXT, "auth", "", "Open", "", Optional.empty(), Nodes.FieldPresentation.TRANSPARENT)), ""));
            try (var view = new KitView(context, spec, event -> { })) {
                view.runWindow(true);
                KitFixtures.render(view, 436, 120, 1);
                var palette = context.theme().semantic();
                assertThat(find(view, "label", JLabel.class).getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_STRONG_SECONDARY));
                var strong = find(view, "strong", JLabel.class);
                assertThat(strong.getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_STRONG_SECONDARY));
                assertThat(strong.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.PANEL_TITLE));
                var mono = find(view, "mono", JLabel.class);
                assertThat(mono.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.RUN_DETAIL));
                assertThat(mono.getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_SECONDARY));
                var input = find(view, "query.input", javax.swing.text.JTextComponent.class);
                assertThat(input.getFont()).as("the query in the data face").isEqualTo(ThemePainting.font(context, Typography.Role.MONO));
                var surface = (TextFields.FieldSurface) input.getParent();
                assertThat(surface.quiet).isTrue();
                assertThat(surface.getInsets()).isEqualTo(new java.awt.Insets(0, 0, 0, 0));
            }
        });
    }
}
