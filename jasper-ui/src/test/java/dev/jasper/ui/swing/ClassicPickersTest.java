package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/**
 * Plan 4b's classic picker faces as painted (plan 4c Execution notes E8): a plain picker, a rich picker and a rich dropdown are
 * the classic gradient ending with the drawn ▼ and lead with their text, no tile; picker menu rows draw no tiles.
 */
@SuppressWarnings("try")
class ClassicPickersTest {
    static final Nodes.PickerFace VIA = new Nodes.PickerFace("Mac", "dustin@192.168.1.119:22", "server", Nodes.Hue.BLUE, Nodes.PickerDensity.DEFAULT, true);

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y), true); }

    static int distance(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue());
    }

    /** The first column, from {@code from}, holding a pixel within 60 of {@code ink} between rows {@code top} and {@code bottom}. */
    static int firstInk(BufferedImage image, Color ink, int from, int top, int bottom) {
        for (int x = from; x < image.getWidth(); x++) for (int y = top; y < bottom; y++)
            if (at(image, x, y).getAlpha() > 0 && distance(at(image, x, y), ink) < 60) return x;
        return image.getWidth();
    }

    /** Whether the drawn ▼ in its guarded colour lies in the last 24 px. */
    static boolean arrow(BufferedImage image, Color colour) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = Math.max(0, image.getWidth() - 24); x < image.getWidth(); x++)
            if (at(image, x, y).equals(colour)) return true;
        return false;
    }

    static ViewSpec pickers() {
        return new ViewSpec(new Nodes.Stack("pickers", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Choice("credential", ControlState.READY, Nodes.ChoiceKind.PICKER, List.of(), "", "Credential", "credential", ""),
            new Nodes.Choice("via", ControlState.READY, Nodes.ChoiceKind.PICKER, List.of(), "", "Via host", "via", "", Optional.empty(), Optional.of(VIA)),
            new Nodes.Choice("jump", ControlState.READY, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("mac", "Mac", Optional.empty()),
                new Option("box", "build-box", Optional.empty())), "mac", "Jump host", "jump", "", Optional.empty(), Optional.of(VIA)))));
    }

    @Test void classicPickersAndRichDropdownsAreGradientsLedByTheirTextAndEndedByTheirArrow() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var pairs = context.faces().buttons.pairs();
            try (var view = new KitView(context, pickers(), e -> {})) {
                KitFixtures.capture(context, view.spec(), 320, 140, "pickers");
                KitFixtures.render(view, 320, 140, 1);
                int pad = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
                for (var id : List.of("credential.input", "via.input", "jump.input")) {
                    var face = find(view, id, JComponent.class);
                    var image = paint(face);
                    int h = face.getHeight();
                    assertThat(distance(at(image, face.getWidth() / 2, 2), ThemePainting.color(context, SemanticColor.BUTTON_SECONDARY_BG))).as(id + " top").isLessThan(12);
                    assertThat(distance(at(image, face.getWidth() / 2, h - 3), ThemePainting.role(context, DerivedColor.BUTTON_END))).as(id + " foot").isLessThan(12);
                    assertThat(arrow(image, ThemePainting.painted(context, pairs.get(5)))).as(id + " ▼").isTrue();
                    if (!id.equals("credential.input")) {
                        int ink = firstInk(image, ThemePainting.color(context, SemanticColor.TEXT_PRIMARY), 2, 3, h - 3);
                        assertThat(ink).as(id + " leads with its text, no tile").isBetween(pad - 1, pad + 4);
                    }
                }
            } finally { release(context); }
        });
    }

    @Test void theDefaultRichFaceKeepsItsTile() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, pickers(), e -> {})) {
                KitFixtures.render(view, 320, 140, 1);
                var face = find(view, "via.input", JComponent.class);
                var image = paint(face);
                int pad = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
                int ink = firstInk(image, ThemePainting.color(context, SemanticColor.TEXT_PRIMARY), 2, 3, face.getHeight() - 3);
                assertThat(ink).as("the text follows the tile").isGreaterThan(pad + ThemePainting.metric(context, DesignMetrics.Role.PICKER_DEFAULT_FACE_TILE));
            } finally { release(context); }
        });
    }

    /** Plan 4b Rulings 9: picker menu rows draw no tiles under the classic face, so their labels start a tile earlier. */
    @Test void pickerMenuRowsDrawNoTilesUnderTheClassicFace() {
        var items = List.of(new Nodes.PickerItem("login", "Login", "Password", "user", Nodes.Hue.BLUE), new Nodes.PickerItem("key", "Deploy key", "SSH key", "key", Nodes.Hue.GREEN));
        var menu = new Nodes.PickerMenu("menu", ControlState.READY, "", "Search", items, "", "", "No matches", "New Credential…", "create", "cancel");
        var starts = new LinkedHashMap<String, Integer>();
        for (var kit : List.of(Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), kit);
            try (var view = new KitView(context, new ViewSpec(menu), e -> {})) {
                if (kit != Kit.DEFAULT) KitFixtures.capture(context, view.spec(), 260, 140, "picker-menu");
                KitFixtures.render(view, 260, 140, 1);
                var rows = find(view, "menu.rows", JList.class);
                var image = paint(rows);
                var cell = rows.getCellBounds(0, 0);
                starts.put(kit.id(), firstInk(image, ThemePainting.color(context, SemanticColor.TEXT_PRIMARY), 0, cell.y + 3, cell.y + cell.height - 3));
                int pad = ThemePainting.metric(context, DesignMetrics.Role.PICKER_PADDING), gap = ThemePainting.metric(context, DesignMetrics.Role.PICKER_ROW_GAP);
                if (kit != Kit.DEFAULT) assertThat(starts.get(kit.id())).as("the label after the padding and gap").isBetween(pad + gap - 1, pad + gap + 4);
            } finally { release(context); }
        });
        assertThat(starts.get(KitFixtures.enterprise().id())).as("a tile earlier than by default").isLessThan(starts.get(Kit.DEFAULT.id()));
    }
}
