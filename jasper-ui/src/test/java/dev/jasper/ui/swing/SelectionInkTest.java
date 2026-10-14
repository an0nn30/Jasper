package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 2: a selected row is a rectangle across the list's full width, its kit text and glyphs in the fill's ink. */
class SelectionInkTest {
    static ListItem item(String id) {
        return new ListItem(id, Optional.of(new Nodes.Icon(id + ".icon", ControlState.READY, "server", Nodes.Hue.NONE, Nodes.IconSize.CONTROL, "Host")),
            List.of(new Nodes.Text(id + ".title", ControlState.READY, "Host " + id, Nodes.TextRole.BODY)),
            List.of(new Nodes.Text(id + ".detail", ControlState.READY, "user@" + id, Nodes.TextRole.CAPTION)), List.of());
    }
    static ViewSpec list(String selected, Nodes.SelectionMode mode) { return list(selected, mode, Nodes.ListKind.DOUBLE); }
    static ViewSpec list(String selected, Nodes.SelectionMode mode, Nodes.ListKind kind) {
        return new ViewSpec(new Nodes.ItemList("list", ControlState.READY, kind, List.of(item("a"), item("b")), Set.of(selected),
            "", List.of(), mode, Optional.empty(), OptionalInt.empty()));
    }
    static final Color ACCENT = new Color(0xCA9EE6), ON_ACCENT = new Color(0x232634), SELECTION = new Color(0x44495D), ON_SELECTION = new Color(0xC6D0F5);
    /** A theme whose accent takes dark text and whose selection takes light text, as many converted themes do. */
    static dev.jasper.ui.theme.ThemePair divergent() {
        var base = KitFixtures.dark();
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, base.semantic().color(role));
        colors.put(SemanticColor.ACCENT, ACCENT);
        colors.put(SemanticColor.TEXT_ONACCENT, ON_ACCENT);
        colors.put(SemanticColor.SELECTION_BG, SELECTION);
        colors.put(SemanticColor.TEXT_ONSELECTION, ON_SELECTION);
        var theme = KitFixtures.withSemantic(base, new dev.jasper.ui.theme.SemanticPalette(colors));
        return new dev.jasper.ui.theme.ThemePair(theme, theme);
    }
    /** The colour of an icon's most opaque pixel. */
    static Color ink(Icon icon) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        int best = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) >>> 24) > (best >>> 24)) best = image.getRGB(x, y);
        return new Color(best & 0xFFFFFF);
    }

    @Test void textOnTheSelectionTakesTheSelectionsInkAndTextOnTheAccentTheAccents() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            context.binding().publish(divergent());
            try (var view = new KitView(context, list("a", Nodes.SelectionMode.SINGLE), e -> { })) {
                view.setSize(320, 200); InteractiveRowsTest.layout(view);
                assertThat(find(view, "a.title", JLabel.class).getForeground()).as("a selected list row").isEqualTo(ON_SELECTION);
                assertThat(ink(find(view, "a.icon", JLabel.class).getIcon())).isEqualTo(ON_SELECTION);
            }
            try (var view = new KitView(context, list("a", Nodes.SelectionMode.SINGLE, Nodes.ListKind.RESULTS), e -> { })) {
                view.setSize(320, 200); InteractiveRowsTest.layout(view);
                assertThat(find(view, "a.title", JLabel.class).getForeground()).as("a selected result on the accent fill").isEqualTo(ON_ACCENT);
            }
            try (var view = new KitView(context, stack(ChoicesTest.choice(Nodes.ChoiceKind.DROPDOWN, "a", ControlState.READY)), e -> { })) {
                var combo = find(view, "choice.input", JComboBox.class);
                var popup = (javax.swing.plaf.basic.BasicComboPopup) combo.getUI().getAccessibleChild(combo, 0);
                assertThat(popup.getList().getSelectionBackground()).isEqualTo(SELECTION);
                assertThat(popup.getList().getSelectionForeground()).isEqualTo(ON_SELECTION);
                @SuppressWarnings("unchecked") var renderer = (ListCellRenderer<Object>) combo.getRenderer();
                var row = renderer.getListCellRendererComponent(popup.getList(), combo.getItemAt(1), 1, true, false);
                assertThat(row.getForeground()).as("a dropdown's highlighted entry").isEqualTo(ON_SELECTION);
            } finally { release(context); }
        });
    }

    @Test void aSelectedRowFillsTheListsWholeBandAsARectangleWithWhiteInk() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, list("a", Nodes.SelectionMode.SINGLE), e -> { })) {
                var rows = (InteractiveRows.RowsControl) view.control("list");
                rows.gutter(8);
                view.setSize(320, 200); InteractiveRowsTest.layout(view);
                var semantic = context.theme().semantic();
                var a = rows.mounted.get("a");
                assertThat(a.getX()).as("the row's content keeps the gutter").isEqualTo(UIScale.scale(8));
                var image = ResultsKitTest.paint(view);
                var top = SwingUtilities.convertPoint(a.getParent(), 0, a.getY(), view);
                Color selection = semantic.color(SemanticColor.SELECTION_BG);
                for (var probe : List.of(new Point(0, top.y), new Point(view.getWidth() - 1, top.y),
                        new Point(0, top.y + a.getHeight() - 1), new Point(view.getWidth() - 1, top.y + a.getHeight() - 1)))
                    assertThat(new Color(image.getRGB(probe.x, probe.y))).as("fill at " + probe).isEqualTo(selection);
                assertThat(find(view, "a.title", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.TEXT_ONSELECTION));
                assertThat(find(view, "a.detail", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.TEXT_ONSELECTION_SECONDARY));
                assertThat(ink(find(view, "a.icon", JLabel.class).getIcon())).isEqualTo(semantic.color(SemanticColor.TEXT_ONSELECTION));
                view.update(list("b", Nodes.SelectionMode.SINGLE)); InteractiveRowsTest.layout(view);
                assertThat(find(view, "a.title", JLabel.class).getForeground()).as("deselected").isEqualTo(semantic.color(SemanticColor.TEXT_PRIMARY));
                assertThat(ink(find(view, "a.icon", JLabel.class).getIcon())).isNotEqualTo(semantic.color(SemanticColor.TEXT_ONSELECTION));
                var after = ResultsKitTest.paint(view);
                assertThat(after.getRGB(0, top.y + 1) >>> 24).as("no fill left on the deselected row's band").isZero();
            } finally { release(context); }
        });
    }

    @Test void aFocusedUnselectedRowShowsTheHoverFillAndNoOutline() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var focus = new SettingsKitTest.Focus();
            try (var view = new KitView(context, list("a", Nodes.SelectionMode.MULTIPLE), e -> { })) {
                var rows = (InteractiveRows.RowsControl) view.control("list");
                view.setSize(320, 200); InteractiveRowsTest.layout(view);
                KeyboardFocusManager.setCurrentKeyboardFocusManager(focus); focus.focus(rows.canvas);
                rows.focusItem = "b"; rows.sync();
                var image = ResultsKitTest.paint(view);
                var b = rows.mounted.get("b");
                var top = SwingUtilities.convertPoint(b.getParent(), 0, b.getY(), view);
                var hover = ResultsKitTest.over(new Color(0, 0, 0, 0), ColorMath.opacity(context.theme().semantic().color(SemanticColor.HOVER_OVERLAY), .05));
                assertThat(new Color(image.getRGB(view.getWidth() / 2, top.y + b.getHeight() / 2), true)).isEqualTo(hover);
                var a = rows.mounted.get("a");
                var aTop = SwingUtilities.convertPoint(a.getParent(), 0, a.getY(), view);
                assertThat(new Color(image.getRGB(0, aTop.y))).as("the selected row's edge is fill, not an outline")
                    .isEqualTo(context.theme().semantic().color(SemanticColor.SELECTION_BG));
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); release(context); }
        });
    }

    @Test void aSelectedSettingsHeadingReadsInBothVariants() {
        Edt.run(() -> {
            for (var theme : List.of(KitFixtures.dark(), KitFixtures.light())) {
                var context = KitFixtures.context(theme);
                var child = new TreeItem("child", "Child", Optional.empty(), true, List.of());
                var group = new TreeItem("group", "Appearance & Behavior", Optional.empty(), false, List.of(child));
                var tree = new Nodes.Tree("tree", ControlState.READY, List.of(group), Set.of("group"), Set.of("group"), "");
                try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("nav", ControlState.READY, Nodes.LayoutRole.SETTINGS_NAVIGATION, List.of(tree))), e -> { })) {
                    view.setSize(236, 300); InteractiveRowsTest.layout(view);
                    var rows = (Trees.TreeControl) view.control("tree");
                    var heading = (JLabel) rows.mounted.get("group").controls.get("@tree/tree/group/title").component;
                    var semantic = context.theme().semantic();
                    assertThat(heading.getForeground()).isEqualTo(semantic.color(SemanticColor.TEXT_ONSELECTION));
                    assertThat(ColorMath.contrast(heading.getForeground(), semantic.color(SemanticColor.SELECTION_BG))).isGreaterThanOrEqualTo(4.5);
                    assertThat(rows.gutter).isEqualTo(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_NAV_INSET));
                } finally { release(context); }
            }
        });
    }

    /** A Plugins-style row: a selected row's ON switch and blue chip stay readable on the accent fill (Light's fill is the accent itself). */
    @Test void aSelectedRowsSwitchAndChipReadOnTheFillInBothVariants() {
        Edt.run(() -> {
            for (var theme : List.of(KitFixtures.dark(), KitFixtures.light())) {
                var context = KitFixtures.context(theme); String variant = theme.dark() ? "Dark" : "Light";
                var row = new ListItem("a", Optional.empty(), List.of(new Nodes.Text("a.title", ControlState.READY, "Remote", Nodes.TextRole.BODY)), List.of(),
                    List.<Nodes.Node>of(new Nodes.Chip("a.kind", ControlState.READY, "Bundled", Nodes.Hue.BLUE),
                        new Nodes.Toggle("a.on", ControlState.READY, "Enabled", true, "toggle", false)));
                java.util.function.Function<Set<String>, ViewSpec> spec = selected -> new ViewSpec(new Nodes.ItemList("list", ControlState.READY,
                    Nodes.ListKind.DOUBLE, List.of(row), selected, "", List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty()));
                try (var view = new KitView(context, spec.apply(Set.of("a")), e -> { })) {
                    view.setSize(420, 120); InteractiveRowsTest.layout(view);
                    var semantic = context.theme().semantic();
                    Color fill = semantic.color(SemanticColor.SELECTION_BG);
                    var toggle = find(view, "a.on", Switches.SwitchButton.class);
                    var image = new BufferedImage(toggle.getWidth(), toggle.getHeight(), BufferedImage.TYPE_INT_RGB);
                    var g = image.createGraphics(); try { g.setColor(fill); g.fillRect(0, 0, image.getWidth(), image.getHeight()); toggle.paint(g); } finally { g.dispose(); }
                    int w = context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SWITCH_WIDTH), h = context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SWITCH_HEIGHT);
                    int left = (toggle.getWidth() - UIScale.scale(w)) / 2, middle = toggle.getHeight() / 2;
                    var track = new Color(image.getRGB(left + UIScale.scale(h) / 2, middle));
                    var knob = new Color(image.getRGB(left + UIScale.scale(w) - UIScale.scale(h) / 2, middle));
                    assertThat(ColorMath.contrast(track, fill)).as(variant + " ON track on the fill").isGreaterThanOrEqualTo(3);
                    assertThat(ColorMath.contrast(knob, track)).as(variant + " knob on the ON track").isGreaterThanOrEqualTo(3);
                    var chip = find(view, "a.kind", Indicators.ChipLabel.class);
                    assertThat(chip.getForeground()).as(variant + " chip text").isEqualTo(semantic.color(SemanticColor.TEXT_ONSELECTION));
                    assertThat(chip.fill).as(variant + " chip tile").isEqualTo(semantic.color(SemanticColor.TEXT_ONSELECTION));
                    view.update(spec.apply(Set.of())); InteractiveRowsTest.layout(view);
                    assertThat(find(view, "a.kind", Indicators.ChipLabel.class).getForeground()).as(variant + " deselected chip").isEqualTo(semantic.color(SemanticColor.HUE_BLUE));
                    assertThat(find(view, "a.on", Switches.SwitchButton.class).onSelection).as(variant + " deselected switch").isFalse();
                } finally { release(context); }
            }
        });
    }

    @Test void aPanelListTakesThePanelsInsetAsItsGutter() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, KitFixtures.panel("sessions"), e -> { })) {
                view.setSize(300, 400); InteractiveRowsTest.layout(view);
                var rows = (InteractiveRows.RowsControl) view.control("sessions-body");
                assertThat(rows.gutter).isEqualTo(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_ROW_SIDE));
                assertThat(SwingUtilities.getAncestorOfClass(JScrollPane.class, rows.canvas).getX()).as("the list reaches the panel's edges").isZero();
            } finally { release(context); }
        });
    }
}
