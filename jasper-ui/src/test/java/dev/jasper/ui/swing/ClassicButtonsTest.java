package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's buttons point and 6.4's classic button and field details. */
@SuppressWarnings("try")
class ClassicButtonsTest {
    static final ControlState DISABLED = new ControlState(true, false, false, "");

    static Nodes.Button button(String id, Nodes.ButtonKind kind, ControlState state) {
        return new Nodes.Button(id, state, "Choose…", "", kind, id, "");
    }

    static ViewSpec stack(Nodes.LayoutRole role, Nodes.Node... children) {
        return new ViewSpec(new Nodes.Stack("stack", ControlState.READY, role, List.of(children)));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y), true); }

    static boolean near(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) <= 6 && Math.abs(a.getGreen() - b.getGreen()) <= 6 && Math.abs(a.getBlue() - b.getBlue()) <= 6;
    }

    /** The guarded edge and label over every palette; the fill runs from button.secondary.bg at the top to button.end. */
    @Test void aStandardButtonIsAGradientInsideItsGuardedEdge() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var pairs = context.faces().buttons.pairs();
            try (var view = new KitView(context, stack(Nodes.LayoutRole.ACTIONS, button("b", Nodes.ButtonKind.SECONDARY, ControlState.READY)), e -> {})) {
                KitFixtures.render(view, 300, 60, 1);
                var b = find(view, "b", Buttons.KitButton.class);
                var image = paint(b);
                int h = b.getHeight();
                String where = theme.name() + (theme.dark() ? " Dark" : " Light");
                assertThat(at(image, 0, h / 2)).as(where + " edge").isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                assertThat(b.getForeground()).as(where + " label").isEqualTo(ThemePainting.painted(context, pairs.get(1)));
                assertThat(near(at(image, 3, 1), ThemePainting.color(context, SemanticColor.BUTTON_SECONDARY_BG))).as(where + " top").isTrue();
                assertThat(near(at(image, 3, h - 2), ThemePainting.role(context, DerivedColor.BUTTON_END))).as(where + " bottom").isTrue();
            } finally { release(context); }
        });
    }

    @Test void aPrimaryButtonIsTheAccentWithAMediumLabelAndNoDrop() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var pairs = context.faces().buttons.pairs();
            try (var view = new KitView(context, stack(Nodes.LayoutRole.ACTIONS, button("ok", Nodes.ButtonKind.PRIMARY, ControlState.READY)), e -> {})) {
                KitFixtures.render(view, 300, 60, 1);
                var b = find(view, "ok", Buttons.KitButton.class);
                var image = paint(b);
                assertThat(at(image, 3, b.getHeight() / 2)).isEqualTo(ThemePainting.color(context, SemanticColor.ACCENT));
                assertThat(at(image, 0, b.getHeight() / 2)).isEqualTo(ThemePainting.painted(context, pairs.get(3)));
                assertThat(b.getForeground()).isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                assertThat(b.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY_STRONG));
                assertThat(b.getClientProperty(ThemePainting.DROP)).as("Review Focus 2: no drop").isNull();
            } finally { release(context); }
        });
    }

    /** Review Focus 2: a disabled button is a flat fill, a lighter edge and the label at 0.42 alpha, not a faded face. */
    @Test void aDisabledButtonIsFlatWithFadedText() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, stack(Nodes.LayoutRole.ACTIONS, button("remove", Nodes.ButtonKind.SECONDARY, DISABLED),
                    button("add", Nodes.ButtonKind.PRIMARY, DISABLED)), e -> {})) {
                KitFixtures.capture(context, view.spec(), 300, 60, "buttons-disabled");
                KitFixtures.render(view, 300, 60, 1);
                var remove = find(view, "remove", Buttons.KitButton.class);
                var add = find(view, "add", Buttons.KitButton.class);
                assertThat(at(paint(remove), 3, remove.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(at(paint(add), 3, add.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_HEADER));
                assertThat(at(paint(remove), 0, remove.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
                assertThat(remove.getForeground().getAlpha()).isEqualTo(Math.round(.42f * 255));
            } finally { release(context); }
        });
    }

    /** A LINK button keeps the default treatment (Rulings 8), so a disabled one still fades as one layer. */
    @Test void aDisabledLinkFadesAsOneLayer() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, stack(Nodes.LayoutRole.ACTIONS, button("on", Nodes.ButtonKind.LINK, ControlState.READY),
                    button("off", Nodes.ButtonKind.LINK, DISABLED)), e -> {})) {
                KitFixtures.render(view, 300, 60, 1);
                var on = find(view, "on", Buttons.KitButton.class);
                var off = find(view, "off", Buttons.KitButton.class);
                assertThat(off.fadesDisabled()).isTrue();
                assertThat(off.getSize()).isEqualTo(on.getSize());
                var enabled = paint(on); var disabled = paint(off);
                int w = enabled.getWidth(), h = enabled.getHeight();
                assertThat(disabled.getRGB(0, 0, w, h, null, 0, w)).isNotEqualTo(enabled.getRGB(0, 0, w, h, null, 0, w));
                assertThat(maxAlpha(disabled)).isLessThanOrEqualTo(Math.round(.42f * 255) + 1);
            } finally { release(context); }
        });
    }

    /** Review Focus 2: a disabled flat ghost's glyph takes the label's 0.42 alpha, as its label does. */
    @Test void aDisabledIconOnlyGhostPaintsItsGlyphFaded() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, stack(Nodes.LayoutRole.INLINE,
                    new Nodes.Button("on", ControlState.READY, "", "search", Nodes.ButtonKind.GHOST, "on", "Search"),
                    new Nodes.Button("off", DISABLED, "", "search", Nodes.ButtonKind.GHOST, "off", "Search")), e -> {})) {
                KitFixtures.render(view, 300, 60, 1);
                var on = find(view, "on", Buttons.KitButton.class);
                var off = find(view, "off", Buttons.KitButton.class);
                assertThat(off.fadesDisabled()).isFalse();
                int enabled = maxAlpha(paint(on)), disabled = maxAlpha(paint(off));
                assertThat(enabled).as("precondition: an inked glyph").isGreaterThan(200);
                assertThat(disabled).isBetween(1, Math.round(enabled * .42f) + 1);
            } finally { release(context); }
        });
    }

    static int maxAlpha(BufferedImage image) {
        int max = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) max = Math.max(max, image.getRGB(x, y) >>> 24);
        return max;
    }

    /** Rulings 7: a ghost in a toolbar row is flat until hovered; among actions it draws as a standard button. */
    @Test void aGhostIsFlatInARowAndStandardAmongActions() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var inline = new KitView(context, stack(Nodes.LayoutRole.INLINE, button("ghost", Nodes.ButtonKind.GHOST, ControlState.READY)), e -> {});
                 var actions = new KitView(context, stack(Nodes.LayoutRole.ACTIONS, button("ghost", Nodes.ButtonKind.GHOST, ControlState.READY)), e -> {})) {
                KitFixtures.render(inline, 300, 60, 1); KitFixtures.render(actions, 300, 60, 1);
                var flat = find(inline, "ghost", Buttons.KitButton.class);
                assertThat(paint(flat).getRGB(3, flat.getHeight() / 2) >>> 24).as("flat at rest").isZero();
                flat.getModel().setRollover(true);
                assertThat(at(paint(flat), 3, flat.getHeight() / 2)).isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_CONTROL_HOVER));
                var standard = find(actions, "ghost", Buttons.KitButton.class);
                assertThat(near(at(paint(standard), 3, 1), ThemePainting.color(context, SemanticColor.BUTTON_SECONDARY_BG))).isTrue();
            } finally { release(context); }
        });
    }

    @Test void aDropdownFollowsTheButtonsAndEndsWithItsDrawnArrow() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var pairs = context.faces().buttons.pairs();
            var choice = new Nodes.Choice("reachable", ControlState.READY, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("local", "This computer only",
                Optional.empty()), new Option("lan", "Local network", Optional.empty())), "local", "Reachable from", "reachable");
            try (var view = new KitView(context, new ViewSpec(choice), e -> {})) {
                KitFixtures.capture(context, view.spec(), 300, 40, "dropdown");
                KitFixtures.render(view, 300, 40, 1);
                var combo = find(view, "reachable.input", Choices.ChoiceCombo.class);
                var image = paint(combo);
                assertThat(at(image, 0, combo.getHeight() / 2)).isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                var arrow = ThemePainting.painted(context, pairs.get(5)); boolean found = false;
                for (int y = 0; y < image.getHeight(); y++) for (int x = image.getWidth() - 20; x < image.getWidth(); x++)
                    found |= at(image, x, y).equals(arrow);
                assertThat(found).as("the ▼ in text.secondary").isTrue();
            } finally { release(context); }
        });
    }

    /**
     * Rulings 6 for dropdowns: a disabled one is a flat fill inside the lighter edge, as a disabled button is; the combo
     * still fades its whole closed face to 0.42 (its own disabled treatment), so both read through that layer.
     */
    @Test void aDisabledDropdownIsFlatWithTheLighterEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var choice = new Nodes.Choice("reachable", DISABLED, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("local", "This computer only",
                Optional.empty()), new Option("lan", "Local network", Optional.empty())), "local", "Reachable from", "reachable");
            try (var view = new KitView(context, new ViewSpec(choice), e -> {})) {
                KitFixtures.render(view, 300, 40, 1);
                var combo = find(view, "reachable.input", Choices.ChoiceCombo.class);
                var image = paint(combo);
                Color fill = ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER), divider = ThemePainting.role(context, DerivedColor.BORDER_DIVIDER);
                var inside = at(image, 3, combo.getHeight() / 2);
                assertThat(near(inside, fill)).as("flat fill " + inside).isTrue();
                assertThat(inside.getAlpha()).isEqualTo(Math.round(.42f * 255));
                // The edge over the fill, each at 0.42: source-over of the divider on the faded fill.
                float a = .42f, out = a + a * (1 - a);
                var edge = new Color(Math.round((divider.getRed() * a + fill.getRed() * a * (1 - a)) / out),
                    Math.round((divider.getGreen() * a + fill.getGreen() * a * (1 - a)) / out), Math.round((divider.getBlue() * a + fill.getBlue() * a * (1 - a)) / out));
                assertThat(near(at(image, 0, combo.getHeight() / 2), edge)).as("divider edge " + at(image, 0, combo.getHeight() / 2) + " vs " + edge).isTrue();
            } finally { release(context); }
        });
    }

    /** A read-only field with an error keeps the error edge under the classic policy. */
    @Test void aReadOnlyFieldWithAnErrorKeepsTheErrorEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var folder = new Nodes.Field("folder", new ControlState(true, true, false, "Choose a folder"), Nodes.FieldKind.TEXT, "No folder selected", "",
                "Folder", "", Optional.empty(), Nodes.FieldPresentation.STANDARD, "", "", "", true);
            try (var view = new KitView(context, new ViewSpec(folder), e -> {})) {
                KitFixtures.render(view, 300, 80, 1);
                var control = (TextFields.FieldControl) view.control("folder");
                assertThat(control.surface.error).as("precondition").isTrue();
                assertThat(at(paint(control.surface), 0, control.surface.getHeight() / 2)).isEqualTo(ThemePainting.color(context, SemanticColor.BORDER_ERROR));
                assertThat(at(paint(control.surface), 3, control.surface.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
            } finally { release(context); }
        });
    }

    /** Rulings 10: SEARCH, PATH and PORT fields lose their leading glyph; a read-only field is grey with muted text. */
    @Test void fieldsLoseTheirLeadingGlyphAndAReadOnlyFieldIsGrey() {
        Edt.run(() -> {
            var classic = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var standard = KitFixtures.context(KitFixtures.light());
            var search = new Nodes.Field("search", ControlState.READY, Nodes.FieldKind.SEARCH, "", "Search", "Search", "", Optional.empty());
            var folder = new Nodes.Field("folder", ControlState.READY, Nodes.FieldKind.TEXT, "No folder selected", "", "Folder", "", Optional.empty(),
                Nodes.FieldPresentation.STANDARD, "", "", "", true);
            try (var view = new KitView(classic, new ViewSpec(new Nodes.Stack("fields", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(search, folder))), e -> {});
                 var plain = new KitView(standard, new ViewSpec(search), e -> {})) {
                KitFixtures.render(view, 300, 120, 1); KitFixtures.render(plain, 300, 40, 1);
                assertThat(((TextFields.FieldControl) view.control("search")).leading.isVisible()).isFalse();
                assertThat(((TextFields.FieldControl) plain.control("search")).leading.isVisible()).isTrue();
                var control = (TextFields.FieldControl) view.control("folder");
                assertThat(at(paint(control.surface), 3, control.surface.getHeight() / 2)).isEqualTo(ThemePainting.role(classic, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(control.input.getForeground()).isEqualTo(classic.faces().fields.readOnlyText(classic));
            } finally { release(classic); release(standard); }
        });
    }

    /** Plan 3c's selected-row ink survives the faces: a bare cell dropdown's arrow takes the row's supporting ink under both kits. */
    @Test void aBareCellDropdownsArrowTakesTheSelectedRowsInkUnderBothFaces() {
        for (var kit : List.of(dev.jasper.ui.kit.Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.dark(), kit);
            try (var view = new KitView(context, new ViewSpec(TableVocabularyTest.groups(Set.of("errors"))), e -> {})) {
                view.setSize(640, 300); InteractiveRowsTest.layout(view);
                var groups = (Tables.TableControl) view.control("groups");
                var selected = TableVocabularyTest.combo(groups, "errors");
                assertThat(selected.bare()).as("precondition: a bare cell dropdown").isTrue();
                assertThat(selected.arrowRole()).as(kit.id()).isEqualTo(groups.ink().supporting);
                assertThat(TableVocabularyTest.combo(groups, "config").arrowRole()).isEqualTo(SemanticColor.TEXT_SECONDARY);
                var arrow = Arrays.stream(selected.getComponents()).filter(JButton.class::isInstance).map(JButton.class::cast).findFirst().orElseThrow();
                int beyond = selected.getWidth() - arrow.getX() - arrow.getWidth();
                var expected = new BufferedImage(arrow.getWidth(), arrow.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = expected.createGraphics();
                try {
                    if (kit == dev.jasper.ui.kit.Kit.DEFAULT) Choices.paintChevron(arrow, g, context, beyond, false, groups.ink().supporting);
                    else ClassicButtonFace.down(arrow, g, context, beyond, ThemePainting.color(context, groups.ink().supporting));
                } finally { g.dispose(); }
                var actual = paint(arrow);
                assertThat(actual.getRGB(0, 0, actual.getWidth(), actual.getHeight(), null, 0, actual.getWidth())).as(kit.id())
                    .isEqualTo(expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()));
            } finally { release(context); }
        });
    }

    @Test void pickerRowsDrawNoTilesUnderTheClassicFace() {
        assertThat(KitFaces.of(dev.jasper.ui.kit.Kit.CLASSIC).buttons.tiles()).isFalse();
        assertThat(KitFaces.of(dev.jasper.ui.kit.Kit.DEFAULT).buttons.tiles()).isTrue();
        assertThat(KitFaces.of(dev.jasper.ui.kit.Kit.CLASSIC).fields.glyph(Nodes.FieldKind.TEXT)).isFalse();
        assertThat(KitFaces.of(dev.jasper.ui.kit.Kit.DEFAULT).fields.glyph(Nodes.FieldKind.PATH)).isTrue();
    }
}
