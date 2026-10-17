package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's toggle point: the classic face is a checkbox leading its label; the default keeps the switch. */
@SuppressWarnings("try")
class ClassicTogglesTest {
    static ViewSpec row(boolean checked, ControlState state) { return row(checked, state, false); }

    static ViewSpec row(boolean checked, ControlState state, boolean pending) {
        return new ViewSpec(new Nodes.Card("card", ControlState.READY, Nodes.CardKind.SETTINGS, List.of(
            new Nodes.SettingRow("row", ControlState.READY, "Allow changes", "Clients can upload, rename and delete files",
                new Nodes.Toggle("toggle", state, "Allow changes", checked, "toggle", false, "", pending), false))));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    @Test void theClassicToggleIsACheckboxLeadingItsTitle() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise()); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, row(false, ControlState.READY), events::add)) {
                KitFixtures.capture(context, view.spec(), 480, 80, "toggle-checkbox");
                KitFixtures.render(view, 480, 80, 1);
                var box = find(view, "toggle", Switches.SwitchButton.class);
                var title = find(view, "row.title", JComponent.class);
                int ring = ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING), size = ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_BOX);
                assertThat(box.getPreferredSize()).isEqualTo(new java.awt.Dimension(size + 2 * ring, size + 2 * ring));
                var boxAt = SwingUtilities.convertPoint(box.getParent(), box.getLocation(), view);
                var titleAt = SwingUtilities.convertPoint(title.getParent(), title.getLocation(), view);
                assertThat(boxAt.x + ring + size + ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_LABEL_GAP)).as("the box leads").isEqualTo(titleAt.x);
                box.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Toggled("toggle", true));
            } finally { release(context); }
        });
    }

    /** Spec 16.1.2 for the face: the box paints its declared pairs as their guards make them, over every palette. */
    @Test void theBoxPaintsItsDeclaredPairsOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var pairs = context.faces().toggle.pairs();
            try {
                for (boolean checked : new boolean[] {true, false})
                    try (var view = new KitView(context, row(checked, ControlState.READY), e -> {})) {
                        KitFixtures.render(view, 480, 80, 1);
                        var box = find(view, "toggle", Switches.SwitchButton.class);
                        var image = paint(box);
                        int size = ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_BOX);
                        int x = (box.getWidth() - size) / 2, y = (box.getHeight() - size) / 2;
                        Color edge = new Color(image.getRGB(x, y + size / 2), true), fill = new Color(image.getRGB(x + size - 3, y + size - 3), true);
                        String where = theme.name() + (theme.dark() ? " Dark" : " Light") + (checked ? " checked" : " unchecked");
                        assertThat(edge).as(where + " edge").isEqualTo(ThemePainting.painted(context, checked ? pairs.get(0) : pairs.get(2)));
                        assertThat(fill).as(where + " fill").isEqualTo(ThemePainting.color(context, checked ? SemanticColor.ACCENT : SemanticColor.SURFACE_INPUT));
                    }
                // A pending box: status.warning with its dash guarded there; the dash's middle pixel is fully covered.
                try (var view = new KitView(context, row(false, ControlState.READY, true), e -> {})) {
                    KitFixtures.render(view, 480, 80, 1);
                    var box = find(view, "toggle", Switches.SwitchButton.class);
                    var image = paint(box);
                    int size = ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_BOX);
                    int x = (box.getWidth() - size) / 2, y = (box.getHeight() - size) / 2;
                    String where = theme.name() + (theme.dark() ? " Dark" : " Light") + " pending";
                    assertThat(new Color(image.getRGB(x + size - 3, y + size - 3), true)).as(where + " fill")
                        .isEqualTo(ThemePainting.color(context, SemanticColor.STATUS_WARNING));
                    assertThat(new Color(image.getRGB(x + size / 2, y + (int) (6.5 * size / 13)), true)).as(where + " dash").isEqualTo(ThemePainting.painted(context, pairs.get(3)));
                }
            } finally { release(context); }
        });
    }

    /** Review Focus 1: on a selected row's fill the box is an outline in the row's ink; a disabled box is flat and grey. */
    @Test void aBoxOnASelectedRowTakesTheRowsInkAndADisabledBoxIsFlat() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, row(true, new ControlState(true, false, false, "")), e -> {})) {
                KitFixtures.render(view, 480, 80, 1);
                var box = find(view, "toggle", Switches.SwitchButton.class);
                int size = ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_BOX);
                int x = (box.getWidth() - size) / 2, y = (box.getHeight() - size) / 2;
                var image = paint(box);
                assertThat(new Color(image.getRGB(x + size - 3, y + size - 3), true)).as("disabled fill")
                    .isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(new Color(image.getRGB(x, y + size / 2), true)).as("disabled edge")
                    .isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
                box.onSelection = true; box.ink = InteractiveRows.Ink.SELECTION; box.setEnabled(true);
                image = paint(box);
                assertThat(new Color(image.getRGB(x, y + size / 2), true)).as("ink edge")
                    .isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_ONSELECTION));
                assertThat(image.getRGB(x + size - 3, y + size - 3) >>> 24).as("no fill on the row's fill").isZero();
                box.setEnabled(false);
                image = paint(box);
                var ink = ThemePainting.color(context, SemanticColor.TEXT_ONSELECTION);
                assertThat(image.getRGB(x, y + size / 2) >>> 24).as("a disabled box on the row's fill is faded")
                    .isEqualTo((int) Math.round(ink.getAlpha() * ClassicToggleFace.DISABLED_OPACITY));
            } finally { release(context); }
        });
    }

    @Test void aClassicToggleSettlesWithoutMotion() {
        Edt.run(() -> {
            var theme = KitFixtures.light();
            var binding = new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); });
            var context = KitContext.of(KitFixtures.enterprise(), binding, ThemeRoute.WORKSPACE, new IconRegistry(), () -> true, action -> "");
            try (var view = new KitView(context, row(false, ControlState.READY), e -> {})) {
                view.update(row(true, ControlState.READY));
                var control = (Switches.ToggleControl) view.control("toggle");
                assertThat(control.animation.isRunning()).isFalse();
                assertThat(control.button.position).isEqualTo(1);
            } finally { release(context); }
        });
    }

    @Test void theDefaultKitKeepsTheTrailingSwitch() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, row(false, ControlState.READY), e -> {})) {
                KitFixtures.render(view, 480, 80, 1);
                var box = find(view, "toggle", Switches.SwitchButton.class);
                assertThat(context.faces().toggle).isInstanceOf(DefaultToggleFace.class);
                assertThat(box.getPreferredSize()).isEqualTo(box.switchSize());
                var title = find(view, "row.title", JComponent.class);
                assertThat(SwingUtilities.convertPoint(box.getParent(), box.getLocation(), view).x)
                    .as("the switch trails").isGreaterThan(SwingUtilities.convertPoint(title.getParent(), title.getLocation(), view).x);
            } finally { release(context); }
        });
    }
}
