package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's empty, tag and route points under the classic and the default faces. */
@SuppressWarnings("try")
class ClassicIndicatorsTest {
    static final ControlState READY = ControlState.READY;
    static final List<Nodes.Hue> HUES = List.of(Nodes.Hue.NEUTRAL, Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE);

    static ViewSpec empty() {
        return new ViewSpec(new Nodes.Empty("empty", READY, new Nodes.Icon("empty-icon", READY, "plugin", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Tunnels"),
            "No Tunnels", "Forward a local port to a remote host, or use Forward Port from an SSH pane.",
            List.of(new Nodes.Button("new", READY, "New Tunnel…", "", Nodes.ButtonKind.SECONDARY, "new", ""),
                new Nodes.Button("help", READY, "Learn More", "", Nodes.ButtonKind.GHOST, "help", ""))));
    }

    static ViewSpec chips() {
        return new ViewSpec(new Nodes.Stack("chips", READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Chip("http", READY, "http", Nodes.Hue.BLUE), new Nodes.Chip("sftp", READY, "sftp", Nodes.Hue.GREEN),
            new Nodes.Chip("ftp", READY, "ftp", Nodes.Hue.AMBER), new Nodes.Chip("rsync", READY, "rsync", Nodes.Hue.PURPLE),
            new Nodes.Chip("plain", READY, "plain", Nodes.Hue.NEUTRAL), new Nodes.Chip("none", READY, "none", Nodes.Hue.NONE))));
    }

    static ViewSpec route(int steps) {
        var children = new ArrayList<Nodes.Node>();
        for (int i = 0; i < steps; i++) children.add(new Nodes.Stack("step" + i, READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Icon("step" + i + "-icon", READY, "terminal", HUES.get(i), Nodes.IconSize.CONTROL, "Step"),
            new Nodes.Text("step" + i + "-caption", READY, "This computer", Nodes.TextRole.CAPTION),
            new Nodes.Text("step" + i + "-value", READY, "localhost:808" + i, Nodes.TextRole.MONO))));
        return new ViewSpec(new Nodes.Stack("route", READY, Nodes.LayoutRole.ROUTE, children));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y), true); }

    /** Review Focus 5: no tile, a bold line over a sentence, the first action only, still so after an update. */
    @Test void aClassicEmptyStateIsABoldLineASentenceAndOneButton() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var pairs = context.faces().empty.pairs();
            try (var view = new KitView(context, empty(), e -> {})) {
                KitFixtures.capture(context, view.spec(), 360, 220, "empty");
                KitFixtures.render(view, 360, 220, 1);
                var control = (EmptyStates.EmptyControl) view.control("empty");
                assertThat(control.tile.getWidth()).isZero();
                assertThat(control.title.getY()).isLessThan(control.description.getY());
                assertThat(control.title.getForeground()).isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                assertThat(control.description.getForeground()).isEqualTo(ThemePainting.painted(context, pairs.get(1)));
                assertThat(view.control("new").component.getParent()).isSameAs(control.actions);
                assertThat(view.control("help").component.getParent()).as("the second action is not placed").isNull();
                view.update(empty());
                KitFixtures.render(view, 360, 220, 1);
                assertThat(view.control("help").component.getParent()).as("nor after an update").isNull();
            } finally { release(context); }
        });
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, empty(), e -> {})) {
                KitFixtures.render(view, 360, 220, 1);
                var control = (EmptyStates.EmptyControl) view.control("empty");
                assertThat(control.tile.getWidth()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.EMPTY_TILE));
                assertThat(view.control("help").component.getParent()).isSameAs(control.actions);
            } finally { release(context); }
        });
    }

    /** Review Focus 3: the tags' text and boxes as their guards make them, over every palette; NONE is neutral. */
    @Test void aClassicTagIsAnUppercaseLabelInAToneBoxOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var face = (ClassicTagFace) context.faces().tag;
            String where = theme.name() + (theme.dark() ? " Dark" : " Light");
            try (var view = new KitView(context, chips(), e -> {})) {
                KitFixtures.render(view, 420, 40, 1);
                var hues = Map.of("http", Nodes.Hue.BLUE, "sftp", Nodes.Hue.GREEN, "ftp", Nodes.Hue.AMBER, "rsync", Nodes.Hue.PURPLE,
                    "plain", Nodes.Hue.NEUTRAL, "none", Nodes.Hue.NEUTRAL);
                for (var entry : hues.entrySet()) {
                    var label = find(view, entry.getKey(), Indicators.ChipLabel.class);
                    assertThat(label.getText()).isEqualTo(entry.getKey().toUpperCase(Locale.ROOT));
                    assertThat(label.getForeground()).as(where + " " + entry.getKey()).isEqualTo(ThemePainting.painted(context, face.text(entry.getValue())));
                    assertThat(at(paint(label), 0, label.getHeight() / 2)).as(where + " box " + entry.getKey())
                        .isEqualTo(ThemePainting.painted(context, face.edge(entry.getValue())));
                    assertThat(label.getHeight()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.CHIP_HEIGHT));
                }
            } finally { release(context); }
        });
    }

    @Test void theDefaultTagKeepsItsTintAndCase() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, chips(), e -> {})) {
                KitFixtures.render(view, 420, 40, 1);
                var label = find(view, "http", Indicators.ChipLabel.class);
                assertThat(label.getText()).isEqualTo("http");
                assertThat(label.edge).isNull();
            } finally { release(context); }
        });
    }

    /** Review Focus 4: equal cells, steps inside them, every arrow before the next cell's edge, at two widths. */
    @Test void routeCellsShareTheStripAndArrowsEndBeforeTheNextEdge() {
        for (int steps = 2; steps <= 5; steps++) for (int width : new int[] {300, 520}) {
            int count = steps, wide = width;
            Edt.run(() -> {
                var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
                var face = (ClassicRouteFace) context.faces().route;
                try (var view = new KitView(context, route(count), e -> {})) {
                    if (count == 3 && wide == 520) KitFixtures.capture(context, view.spec(), wide, 60, "route");
                    KitFixtures.render(view, wide, 60, 1);
                    var surface = (Stacks.StackSurface) view.control("route").component;
                    String where = count + " steps at " + wide;
                    assertThat(surface.cells).as(where).hasSize(count);
                    assertThat(surface.arrows).as(where).hasSize(count - 1);
                    int arrow = ThemePainting.metric(context, DesignMetrics.Role.ROUTE_ARROW), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                    int cell = (surface.getWidth() - 2 * line) / count;
                    var image = paint(surface);
                    int middle = ThemePainting.metric(context, DesignMetrics.Role.ROUTE_TOP) + (surface.getHeight() - ThemePainting.metric(context, DesignMetrics.Role.ROUTE_TOP)) / 2;
                    for (int i = 0; i < count; i++) {
                        int end = i == count - 1 ? surface.getWidth() - line : surface.cells[i + 1];
                        assertThat(end - surface.cells[i]).as(where + " cell " + i).isBetween(cell - 1, cell + 1);
                        var step = find(view, "step" + i, JComponent.class);
                        assertThat(step.getX()).as(where + " step " + i).isGreaterThanOrEqualTo(surface.cells[i] + ThemePainting.metric(context, DesignMetrics.Role.ROUTE_EDGE));
                        if (i < count - 1) {
                            assertThat(step.getX() + step.getWidth()).as(where + " step " + i + " before its arrow").isLessThanOrEqualTo(surface.arrows[i]);
                            assertThat(surface.arrows[i] + arrow).as(where + " arrow " + i).isLessThanOrEqualTo(surface.cells[i + 1]);
                        }
                        assertThat(at(image, surface.cells[i] + 1, middle)).as(where + " edge " + i).isEqualTo(ThemePainting.painted(context, face.edge(HUES.get(i))));
                        assertThat(find(view, "step" + i + "-icon", JComponent.class).getWidth()).as("the icon gives way to the edge").isZero();
                    }
                    var caption = find(view, "step0-caption", JLabel.class);
                    assertThat(caption.getText()).isEqualTo("THIS COMPUTER");
                    assertThat(caption.getForeground()).isEqualTo(ThemePainting.painted(context, face.pairs().get(1)));
                    view.update(route(count));
                    KitFixtures.render(view, wide, 60, 1);
                    assertThat(find(view, "step0-caption", JLabel.class).getText()).as("after an update").isEqualTo("THIS COMPUTER");
                    assertThat(at(image, 0, middle)).as(where + " box").isEqualTo(ThemePainting.painted(context, face.pairs().get(0)));
                } finally { release(context); }
            });
        }
    }

    @Test void theDefaultRouteKeepsItsTiles() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, route(3), e -> {})) {
                KitFixtures.render(view, 520, 60, 1);
                var surface = (Stacks.StackSurface) view.control("route").component;
                assertThat(surface.cells).isEmpty();
                assertThat(find(view, "step0-icon", JComponent.class).getWidth()).isPositive();
                assertThat(((Labels.IconControl) view.control("step0-icon")).tileSize).isPositive();
                assertThat(find(view, "step0-caption", JLabel.class).getText()).isEqualTo("This computer");
            } finally { release(context); }
        });
    }
}
