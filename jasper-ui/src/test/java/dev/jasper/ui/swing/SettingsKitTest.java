package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemePair;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

/** Kit capabilities the Settings dialog needs: tree counts and emphasis, row search highlight, reveal and number bounds. */
class SettingsKitTest {
    /** Enlarged evidence is claimed only when the run's observed scale is the requested one. */
    @Test void observedScaleMatchesTheRequestedScale() {
        Edt.run(() -> {
            String requested = System.getProperty("flatlaf.uiScale");
            float observed = com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
            System.out.println("SettingsKitTest observed UI scale " + observed);
            if (requested != null) assertThat(observed).isEqualTo(Float.parseFloat(requested));
        });
    }

    static Tree tree(int count, boolean emphasized) {
        return new Tree("pages", ControlState.READY, List.of(
            new TreeItem("terminal", "Terminal", Optional.empty(), false, List.of(
                new TreeItem("font", "Font", Optional.empty(), true, List.of(), count > 0 ? OptionalInt.of(count) : OptionalInt.empty(), emphasized)),
                OptionalInt.empty(), false)),
            Set.of("terminal"), Set.of(), "open");
    }

    @Test void treeItemsShowTheirCountAtTheEndAndEmphasisStrongInTheLinkColor() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(tree(3, true)), e -> {})) {
                view.setSize(300, 200); InteractiveRowsTest.layout(view);
                var count = find(view, "@tree/pages/font/count", JLabel.class);
                assertThat(count).isNotNull();
                assertThat(count.getText()).isEqualTo("3");
                var title = find(view, "@tree/pages/font/title", JLabel.class);
                assertThat(title.getFont()).as("strong").isEqualTo(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.BODY_STRONG));
                assertThat(title.getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_LINK));
                var plain = find(view, "@tree/pages/terminal/title", JLabel.class);
                assertThat(plain.getFont()).isEqualTo(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.BODY));
                assertThat(plain.getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(find(view, "@tree/pages/terminal/count", JLabel.class)).as("no count, no label").isNull();

                var light = KitFixtures.light();
                context.binding().publish(new ThemePair(light, light));
                InteractiveRowsTest.layout(view);
                title = find(view, "@tree/pages/font/title", JLabel.class);
                assertThat(title.getForeground()).as("emphasis follows the theme").isEqualTo(light.semantic().color(SemanticColor.TEXT_LINK));
                assertThat(find(view, "@tree/pages/font/count", JLabel.class).getText()).isEqualTo("3");

                view.update(new ViewSpec(tree(0, false)));
                InteractiveRowsTest.layout(view);
                assertThat(find(view, "@tree/pages/font/count", JLabel.class)).isNull();
                title = find(view, "@tree/pages/font/title", JLabel.class);
                assertThat(title.getFont()).isEqualTo(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.BODY));
                assertThat(title.getForeground()).isEqualTo(light.semantic().color(SemanticColor.TEXT_PRIMARY));
            } finally { release(context); }
        });
    }

    static SettingRow setting(String id, boolean highlighted) {
        return new SettingRow(id, ControlState.READY, "Ligatures " + id, "Combine character sequences",
            new Toggle(id + ".toggle", ControlState.READY, "Ligatures", true, id + ".toggle", false), false, highlighted);
    }

    @Test void highlightedRowsPaintTheSearchHighlightAndKeepItAcrossThemesAndUpdates() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(setting("a", true), setting("b", false)), e -> {})) {
                int w = 400, h = 200;
                var image = KitFixtures.render(view, w, h, 1);
                var a = find(view, "a", JComponent.class); var b = find(view, "b", JComponent.class);
                var highlight = context.theme().semantic().color(SemanticColor.SELECTION_BG);
                assertThat(share(image, view, a, highlight)).isGreaterThan(0.5);
                assertThat(share(image, view, b, highlight)).as("an unmatched row is not highlighted").isLessThan(0.01);
                assertThat(a.getAccessibleContext().getAccessibleDescription()).contains("Matches search");

                var light = KitFixtures.light();
                context.binding().publish(new ThemePair(light, light));
                image = KitFixtures.render(view, w, h, 1);
                assertThat(share(image, view, a, light.semantic().color(SemanticColor.SELECTION_BG))).as("a theme switch keeps the highlight").isGreaterThan(0.5);

                view.update(stack(setting("a", false), setting("b", true)));
                image = KitFixtures.render(view, w, h, 1);
                assertThat(find(view, "a", JComponent.class)).as("identity kept").isSameAs(a);
                assertThat(share(image, view, a, light.semantic().color(SemanticColor.SELECTION_BG))).isLessThan(0.01);
                assertThat(share(image, view, b, light.semantic().color(SemanticColor.SELECTION_BG))).isGreaterThan(0.5);
            } finally { release(context); }
        });
    }

    @Test void formRowsHighlightToo() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var row = new FormRow("row", ControlState.READY, "Name", "", new Field("name", ControlState.READY, FieldKind.TEXT, "", "", "Name", ""), Optional.empty(), true);
            var plain = new FormRow("plain", ControlState.READY, "Host", "", new Field("host", ControlState.READY, FieldKind.TEXT, "", "", "Host", ""), Optional.empty());
            try (var view = new KitView(context, stack(row, plain), e -> {})) {
                var image = KitFixtures.render(view, 400, 160, 1);
                var highlight = context.theme().semantic().color(SemanticColor.SELECTION_BG);
                assertThat(share(image, view, find(view, "row", JComponent.class), highlight)).as("behind the label and around the field").isGreaterThan(0.15);
                assertThat(share(image, view, find(view, "plain", JComponent.class), highlight)).isLessThan(0.01);
            } finally { release(context); }
        });
    }

    /** The share of a row's painted area in exactly {@code color}: the highlight fills the row behind its content. */
    static double share(java.awt.image.BufferedImage image, Component root, JComponent row, java.awt.Color color) {
        var at = SwingUtilities.convertPoint(row, 0, 0, root);
        int hits = 0, total = 0;
        for (int y = at.y; y < Math.min(image.getHeight(), at.y + row.getHeight()); y++)
            for (int x = at.x; x < Math.min(image.getWidth(), at.x + row.getWidth()); x++) { total++; if (image.getRGB(x, y) == color.getRGB()) hits++; }
        return total == 0 ? 0 : (double) hits / total;
    }

    static final class Focus extends DefaultKeyboardFocusManager {
        void focus(Component component) { setGlobalFocusOwner(component); setGlobalPermanentFocusOwner(component); }
    }

    @Test void revealScrollsARowIntoViewWithoutMovingFocusOrTheCaret() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var prior = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var rows = new ArrayList<Node>();
            rows.add(new Field("query", ControlState.READY, FieldKind.SEARCH, "liga", "Search", "Search", ""));
            for (int i = 0; i < 40; i++) rows.add(setting("row" + i, i == 37));
            var form = new Form("page", ControlState.READY, FormKind.SETTINGS, "Font", Optional.empty(),
                List.of(new Section("section", ControlState.READY, "Font", new Nodes.Stack("rows", ControlState.READY, LayoutRole.VERTICAL, rows))),
                Optional.empty(), List.of());
            var focus = new Focus();
            try (var view = new KitView(context, new ViewSpec(form), e -> {})) {
                KitFixtures.render(view, 500, 300, 1);
                JTextComponent query = editor(view, "query");
                KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);
                focus.focus(query);
                query.setCaretPosition(2);
                var target = find(view, "row37", JComponent.class);
                var viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, target);
                assertThat(viewport.getViewRect().intersects(SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), viewport.getView())))
                    .as("precondition: offscreen").isFalse();
                view.reveal("row37");
                for (int i = 0; i < 2; i++) InteractiveRowsTest.layout(view);
                assertThat(viewport.getViewRect().contains(SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), viewport.getView())))
                    .as("the row is entirely visible").isTrue();
                assertThat(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()).isSameAs(query);
                assertThat(query.getCaretPosition()).isEqualTo(2);
                assertThatThrownBy(() -> view.reveal("missing")).isInstanceOf(IllegalArgumentException.class);
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(prior); release(context); }
        });
    }

    static Field number(String value, Numeric bounds, ControlState state) {
        return new Field("size", state, FieldKind.NUMBER, value, "", "Size", "", Optional.of(bounds));
    }

    @Test void numberFieldsAcceptOnlyNumbersInTheirBoundsShapeAndStepWithTheArrowKeys() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var bounds = new Numeric(0.5, 3, 0.05, 2);
            try (var view = new KitView(context, stack(number("1.2", bounds, ControlState.READY)), events::add)) {
                var input = (JTextField) editor(view, "size");
                input.setText("1.2x");
                assertThat(input.getText()).as("letters are not numbers; the value stays").isEqualTo("1.2");
                input.setText("-1");
                assertThat(input.getText()).as("no sign when the minimum is not negative").isEqualTo("1.2");
                input.setText("1.234");
                assertThat(input.getText()).as("no more decimals than the setting has").isEqualTo("1.2");
                assertThat(events).as("rejected text is no edit").isEmpty();
                input.setText("1.25");
                assertThat(input.getText()).isEqualTo("1.25");
                assertThat(events).last().isEqualTo(new UiEvent.Edited("size", "1.25"));
                press(input, KeyEvent.VK_UP);
                assertThat(input.getText()).isEqualTo("1.3");
                assertThat(events).last().isEqualTo(new UiEvent.Edited("size", "1.3"));
                input.setText("2.98");
                press(input, KeyEvent.VK_UP); press(input, KeyEvent.VK_UP);
                assertThat(input.getText()).as("stepping stops at the maximum").isEqualTo("3");
                input.setText("0.52");
                press(input, KeyEvent.VK_DOWN);
                assertThat(input.getText()).as("and at the minimum").isEqualTo("0.5");
                assertThat(input.getAccessibleContext().getAccessibleDescription()).contains("0.5", "3");
                view.update(stack(number("9", bounds, new ControlState(true, true, false, "Must be at most 3."))));
                assertThat(find(view, "size.error", JLabel.class).getText()).isEqualTo("Must be at most 3.");
                assertThat(find(view, "size.error", JLabel.class).isVisible()).isTrue();
            } finally { release(context); }
            var whole = new Numeric(-10, 10, 1, 0);
            context = KitFixtures.context(); events.clear();
            try (var view = new KitView(context, stack(number("0", whole, ControlState.READY)), events::add)) {
                var input = (JTextField) editor(view, "size");
                input.setText("-4");
                assertThat(input.getText()).as("a negative minimum admits a sign").isEqualTo("-4");
                input.setText("1.5");
                assertThat(input.getText()).as("whole numbers have no decimals").isEqualTo("-4");
                input.setText("");
                press(input, KeyEvent.VK_UP);
                assertThat(input.getText()).as("an empty field steps from the nearest bound to zero").isEqualTo("1");
            } finally { release(context); }
        });
    }

    static void press(JComponent input, int code) {
        var stroke = KeyStroke.getKeyStroke(code, 0);
        var key = input.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
        assertThat(key).as("bound: " + KeyEvent.getKeyText(code)).isNotNull();
        input.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(input, 0, ""));
    }
}
