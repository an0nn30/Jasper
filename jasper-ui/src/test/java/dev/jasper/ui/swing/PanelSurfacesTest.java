package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static dev.jasper.ui.swing.PanelCompositionTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/**
 * Plan 3 Tasks 6 and 7 kit capabilities measured from the plugin-panel and New Tunnel references: the panel empty
 * state, compact two-line panel rows, settings footers, the ROUTE strip, the DROPDOWN rich face and one-line form-row
 * captions. Each is asserted on actual mounted controls.
 */
class PanelSurfacesTest {
    static int metric(DesignMetrics.Role role) { return s(DesignMetrics.DEFAULT.value(role)); }

    static Nodes.Panel plain(Node body, Optional<Node> footer) {
        return new Nodes.Panel("panel", READY, new Header("header", READY, "Files", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), body, footer);
    }

    @Test void aPanelEmptyStateTakesThePanelGeometryWhileOtherEmptyStatesKeepTheirs() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            try (var view = new KitView(ctx, new ViewSpec(plain(empty(), Optional.empty())), e -> {});
                 var window = new KitView(ctx, new ViewSpec(new Stack("window", READY, LayoutRole.VERTICAL, List.of(empty()))), e -> {})) {
                KitFixtures.render(view, s(260), s(395), 1);
                KitFixtures.render(window, s(400), s(300), 1);
                assertThat(find(view, "empty.tile", JComponent.class).getWidth()).isEqualTo(metric(DesignMetrics.Role.PANEL_EMPTY_TILE));
                assertThat(find(window, "empty.tile", JComponent.class).getWidth()).as("a window's empty state").isEqualTo(metric(DesignMetrics.Role.EMPTY_TILE));
                assertThat(find(view, "new", JButton.class).getHeight()).as("the panel references' smaller action").isEqualTo(metric(DesignMetrics.Role.PANEL_EMPTY_ACTION_HEIGHT));
                assertThat(find(window, "new", JButton.class).getHeight()).isGreaterThanOrEqualTo(metric(DesignMetrics.Role.BUTTON_HEIGHT));
                var description = find(view, "empty.description", JComponent.class);
                assertThat(description.getWidth()).as("the narrower panel text width").isLessThanOrEqualTo(metric(DesignMetrics.Role.PANEL_EMPTY_TEXT_WIDTH));
                var body = find(view, "empty", JComponent.class);
                var content = description.getParent();
                assertThat(content.getY() + content.getHeight() / 2).as("centered in the whole body, a little lifted")
                    .isCloseTo(body.getHeight() / 2 - metric(DesignMetrics.Role.PANEL_EMPTY_LIFT), within(1));
            } finally { release(ctx); }
        });
    }

    @Test void anEmptyStateUnderADocumentSplitStillFillsTheRemainingBody() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var split = new Split("body", READY, SplitKind.DOCUMENT, List.of(new Text("note", READY, "servers.toml: line 3", TextRole.BODY), empty()));
            try (var view = new KitView(ctx, new ViewSpec(plain(split, Optional.empty())), e -> {})) {
                KitFixtures.render(view, s(260), s(395), 1);
                var note = find(view, "note", JComponent.class);
                var empty = find(view, "empty", JComponent.class);
                assertThat(bounds(empty, view).y).isGreaterThanOrEqualTo(bounds(note, view).y + note.getHeight());
                assertThat(bounds(empty, view).y + empty.getHeight()).as("to the bottom of the body").isCloseTo(view.getHeight(), within(s(2)));
                assertThat(find(view, "empty.tile", JComponent.class).getWidth()).isEqualTo(metric(DesignMetrics.Role.PANEL_EMPTY_TILE));
            } finally { release(ctx); }
        });
    }

    static ListItem server(String id, String name) {
        return new ListItem(id, Optional.of(new Nodes.Icon(id + ".folder", READY, "folder.filled", Hue.BLUE, IconSize.CONTROL, "Folder")),
            List.of(new Text(id + ".name", READY, name, TextRole.BODY)),
            List.of(new Chip(id + ".chip", READY, "HTTP", Hue.BLUE), new Text(id + ".address", READY, "127.0.0.1:8080", TextRole.MONO_SMALL)),
            List.of(new Toggle(id + ".switch", READY, "Start server", false, id + ".switch", true)));
    }

    @Test void twoLinePanelRowsAreCompactInsetRowsWithTheSwitchAtTheirEnd() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var list = new ItemList("rows", READY, ListKind.DOUBLE, List.of(server("a", "tester"), server("b", "other")), Set.of(), "open", List.of());
            try (var view = new KitView(ctx, new ViewSpec(plain(list, Optional.empty())), e -> {})) {
                KitFixtures.render(view, s(260), s(395), 1);
                var rows = (InteractiveRows.RowsControl) view.control("rows");
                assertThat(rows.panelCompact).isTrue();
                // Two lines with their padding outgrow the one-line compact row.
                assertThat(rows.rowHeight).isEqualTo(Math.max(metric(DesignMetrics.Role.PANEL_COMPACT_ROW_HEIGHT), rows.compactRowHeight())).isGreaterThan(metric(DesignMetrics.Role.PANEL_COMPACT_ROW_HEIGHT));
                var scroll = bounds(rows.component, view);
                assertThat(scroll.x).as("the list reaches the panel edge").isZero();
                assertThat(rows.gutter).as("its rows keep the panel row inset").isEqualTo(ctx.metrics().value(DesignMetrics.Role.PANEL_ROW_SIDE));
                assertThat(scroll.y - metric(DesignMetrics.Role.PANEL_HEADER)).as("a compact gap below the header").isEqualTo(metric(DesignMetrics.Role.PANEL_COMPACT_LIST_TOP));
                var icon = find(view, "a.folder", JLabel.class);
                assertThat(icon.getIcon().getIconWidth()).isEqualTo(metric(DesignMetrics.Role.PANEL_COMPACT_ROW_GLYPH));
                var toggle = find(view, "a.switch", JComponent.class);
                var placed = SwingUtilities.convertRectangle(toggle.getParent(), toggle.getBounds(), rows.component);
                int track = placed.x + placed.width - metric(DesignMetrics.Role.FOCUS_RING);
                assertThat(rows.component.getWidth() - track).as("the switch track ends the compact inset, inside the gutter")
                    .isEqualTo(metric(DesignMetrics.Role.PANEL_COMPACT_ROW_TRAILING) + metric(DesignMetrics.Role.PANEL_ROW_SIDE));
                assertThat(find(view, "a.chip", JLabel.class).getFont().getSize2D())
                    .isEqualTo(ThemePainting.font(ctx, dev.jasper.ui.metrics.Typography.Role.PANEL_ROW_CHIP).getSize2D());
            } finally { release(ctx); }
        });
    }

    @Test void aNarrowCompactRowKeepsItsChipAndNameWholeBeforeTheTextsBesideThem() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var row = new ListItem("t", Optional.empty(),
                List.of(new Text("t.name", READY, "Metrics", TextRole.BODY), new Text("t.problem", READY, "Port 9090 is in use by another process", TextRole.DESCRIPTION)),
                List.of(new Chip("t.chip", READY, "HTTP", Hue.BLUE), new Text("t.address", READY, "127.0.0.1:8080", TextRole.MONO_SMALL)),
                List.of(new Toggle("t.switch", READY, "Start", false, "t.switch", true)));
            var list = new ItemList("rows", READY, ListKind.DOUBLE, List.of(row), Set.of(), "open", List.of());
            try (var view = new KitView(ctx, new ViewSpec(plain(list, Optional.empty())), e -> {})) {
                KitFixtures.render(view, s(200), s(395), 1);
                var name = find(view, "t.name", JLabel.class);
                assertThat(name.getWidth()).as("the name keeps its width while it fits in half the line").isEqualTo(name.getPreferredSize().width);
                assertThat(find(view, "t.problem", JLabel.class).getWidth()).as("the problem beside it is cut short")
                    .isLessThan(find(view, "t.problem", JLabel.class).getPreferredSize().width);
                var chip = find(view, "t.chip", JLabel.class);
                assertThat(chip.getWidth()).as("a chip keeps its label whole").isEqualTo(chip.getPreferredSize().width);
            } finally { release(ctx); }
        });
    }

    @Test void theImportGlyphIsItsOwnArrowIntoACornerNotTheDownloadTray() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            try {
                var icons = ctx.icons();
                assertThat(icons.names()).contains("import", "download");
                int size = s(16), x = (int) (3.5 * size / 16), y = (int) (9.5 * size / 16);
                var imported = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var download = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = imported.createGraphics(); icons.icon("import", ctx.theme().semantic(), SemanticColor.TEXT_PRIMARY, size).paintIcon(null, g, 0, 0); g.dispose();
                g = download.createGraphics(); icons.icon("download", ctx.theme().semantic(), SemanticColor.TEXT_PRIMARY, size).paintIcon(null, g, 0, 0); g.dispose();
                assertThat(imported.getRGB(x, y) >>> 24).as("the corner bracket's upright").isGreaterThan(100);
                assertThat(download.getRGB(x, y) >>> 24).as("where the tray has no ink").isLessThan(60);
            } finally { release(ctx); }
        });
    }

    @Test void aSettingsFooterStaysOnThePanelSurfaceWithItsRuleAboveThePersistentRow() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var footer = new Stack("footer", READY, LayoutRole.VERTICAL, List.of(new Text("status", READY, "Cancelled", TextRole.DESCRIPTION),
                new SettingRow("follow-row", READY, "Follow terminal folder", "", new Toggle("follow", READY, "Follow terminal folder", true, "follow", true), false)));
            try (var view = new KitView(ctx, new ViewSpec(plain(empty(), Optional.of(footer))), e -> {})) {
                var image = KitFixtures.render(view, s(260), s(395), 1);
                var surface = (PanelFooters.Footer) find(view, "panel.footer-surface", JComponent.class);
                assertThat(surface.arranged).isTrue();
                var row = find(view, "follow-row", JComponent.class);
                var status = find(view, "status", JComponent.class);
                var rowBounds = bounds(row, view);
                assertThat(bounds(status, view).y + status.getHeight()).as("the status line sits above the rule").isLessThanOrEqualTo(rowBounds.y);
                assertThat(rowBounds.y + rowBounds.height).as("the persistent row is flush with the bottom").isEqualTo(view.getHeight());
                assertThat(rowBounds.height).isEqualTo(metric(DesignMetrics.Role.HAIRLINE) + metric(DesignMetrics.Role.PANEL_FOOTER_ROW_HEIGHT));
                assertThat(rowBounds.x).isEqualTo(metric(DesignMetrics.Role.PANEL_FOOTER_SIDE));
                int rule = KitFixtures.over(ctx.theme().semantic().color(SemanticColor.BORDER_SUBTLE), ctx.theme().semantic().color(SemanticColor.SURFACE_PANEL));
                assertThat(image.getRGB(s(2), rowBounds.y)).as("a full-width rule at the row's top").isEqualTo(rule);
                assertThat(image.getRGB(s(2), rowBounds.y + metric(DesignMetrics.Role.HAIRLINE) - 1)).as("filling the logical hairline the row reserves").isEqualTo(rule);
                assertThat(image.getRGB(s(2), rowBounds.y + metric(DesignMetrics.Role.HAIRLINE))).as("and no more").isNotEqualTo(rule);
                int raised = ctx.theme().semantic().color(SemanticColor.SURFACE_PANEL_RAISED).getRGB();
                assertThat(image.getRGB(s(180), rowBounds.y + rowBounds.height / 2)).as("no raised settings footer").isNotEqualTo(raised);
            } finally { release(ctx); }
        });
    }

    static Node step(String id, String icon, Hue hue, String what, String where) {
        return new Stack(id, READY, LayoutRole.INLINE, List.of(new Nodes.Icon(id + ".icon", READY, icon, hue, IconSize.CONTROL, what),
            new Text(id + ".what", READY, what, TextRole.DESCRIPTION), new Text(id + ".where", READY, where, TextRole.MONO_SMALL)));
    }

    @Test void aRouteStripLaysItsStepsInEqualColumnsWithTiledArtworkAndArrows() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var route = new Stack("route", READY, LayoutRole.ROUTE, List.of(step("from", "laptop", Hue.NEUTRAL, "This computer", "localhost:8080"),
                step("via", "server", Hue.BLUE, "Via", "amp-amp"), step("to", "target", Hue.GREEN, "Target on amp-amp", "localhost:5432")));
            try (var view = new KitView(ctx, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(route))), e -> {})) {
                for (int width : new int[] {s(462), s(240)}) {
                    KitFixtures.render(view, width, s(120), 1);
                    var strip = find(view, "route", JComponent.class);
                    assertThat(strip.getHeight()).isEqualTo(metric(DesignMetrics.Role.ROUTE_TOP) + Math.max(metric(DesignMetrics.Role.ROUTE_HEIGHT), find(view,"from",JComponent.class).getPreferredSize().height + 2 * (metric(DesignMetrics.Role.HAIRLINE) + metric(DesignMetrics.Role.ROUTE_VERTICAL_PADDING))));
                    assertThat(((Stacks.StackSurface) strip).arrows).as("an arrow before each later step").hasSize(2);
                    var from = find(view, "from", JComponent.class); var via = find(view, "via", JComponent.class); var to = find(view, "to", JComponent.class);
                    int column = (strip.getWidth() - 2 * (metric(DesignMetrics.Role.HAIRLINE) + metric(DesignMetrics.Role.ROUTE_PADDING))) / 3;
                    var arrows = ((Stacks.StackSurface) strip).arrows;
                    assertThat(arrows[1] - arrows[0]).as("equal columns at %s", width).isCloseTo(column, within(s(1)));
                    assertThat(to.getX() - via.getX()).as("each later step follows its arrow at %s", width).isCloseTo(column, within(s(1)));
                    assertThat(via.getX()).as("the step follows its arrow").isGreaterThan(arrows[0] + metric(DesignMetrics.Role.ROUTE_ARROW));
                    assertThat(from.getX() + from.getWidth()).as("no overlap at %s", width).isLessThanOrEqualTo(((Stacks.StackSurface) strip).arrows[0]);
                    assertThat(via.getX() + via.getWidth()).isLessThanOrEqualTo(((Stacks.StackSurface) strip).arrows[1]);
                    assertThat(to.getX() + to.getWidth()).isLessThanOrEqualTo(strip.getWidth());
                    assertThat(find(view, "via.icon", JLabel.class).getIcon().getIconWidth()).as("the step's tile").isEqualTo(metric(DesignMetrics.Role.ROUTE_TILE));
                }
            } finally { release(ctx); }
        });
    }

    @Test void aDropdownWithARichFacePaintsTheFaceAndStillSelectsOptions() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var events = new java.util.ArrayList<UiEvent>();
            var options = List.of(new Option("a", "amp-amp", "server", "amp@100.110.65.45:22"), new Option("b", "pve", "server", "root@100.73.226.91:22"));
            var face = new PickerFace("amp-amp", "amp@100.110.65.45:22", "server", Hue.BLUE, PickerDensity.DEFAULT);
            var choice = new Nodes.Choice("host", READY, ChoiceKind.DROPDOWN, options, "a", "Via Host", "", "", Optional.empty(), Optional.of(face));
            try (var view = new KitView(ctx, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(choice))), events::add)) {
                KitFixtures.render(view, s(360), s(80), 1);
                var combo = (Choices.ChoiceCombo) find(view, "host.input", JComboBox.class);
                assertThat(combo.face).isEqualTo(face);
                assertThat(combo.getHeight()).isEqualTo(ThemePainting.height(ctx, DesignMetrics.Role.PICKER_DEFAULT_HEIGHT, dev.jasper.ui.metrics.Typography.Role.BODY));
                combo.setSelectedIndex(1);
                assertThat(events).contains(new UiEvent.Selected("host", Set.of("b")));
            } finally { release(ctx); }
            assertThatThrownBy(() -> new Nodes.Choice("bad", READY, ChoiceKind.EXCLUSIVE, options, "a", "Bad", "", "", Optional.empty(), Optional.of(face)))
                .hasMessageContaining("PICKER or DROPDOWN");
        });
    }

    @Test void aFormRowCaptionsTooltipIsPlainText() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var row = new FormRow("row", READY, "Forward To", "<html><b>x</b>",
                new Field("host", READY, FieldKind.TEXT, "localhost", "", "Target host", ""), Optional.empty());
            try (var view = new KitView(ctx, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(row))), e -> {})) {
                var caption = find(view, "row.caption", JComponent.class);
                var tip = caption.createToolTip(); tip.setTipText(caption.getToolTipText());
                assertThat(tip.getTipText()).isEqualTo("<html><b>x</b>");
                assertThat(tip.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)).as("a plugin's caption builds no HTML view").isNull();
            } finally { release(ctx); }
        });
    }

    @Test void aFormRowCaptionIsOneLineShortenedAtItsEnd() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var row = new FormRow("row", READY, "Forward To", "as seen from a-very-long-synthetic-host-name",
                new Field("host", READY, FieldKind.TEXT, "localhost", "", "Target host", ""), Optional.empty());
            try (var view = new KitView(ctx, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(row))), e -> {})) {
                KitFixtures.render(view, s(492), s(80), 1);
                var caption = (Composition.WrappedText) find(view, "row.caption", JComponent.class);
                assertThat(caption.lines(caption.getWidth())).singleElement().satisfies(line -> assertThat(line).endsWith("…"));
                assertThat(caption.getToolTipText()).isEqualTo("as seen from a-very-long-synthetic-host-name");
                var input = find(view, "host", JComponent.class);
                assertThat(find(view, "row", JComponent.class).getHeight()).as("beside an input the row holds its label and caption, no more")
                    .isEqualTo(Math.max(input.getHeight(), caption.getY() + caption.getHeight()));
            } finally { release(ctx); }
        });
    }
}
