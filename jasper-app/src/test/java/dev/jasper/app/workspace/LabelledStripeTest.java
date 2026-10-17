package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.theme.DerivedColor;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: a labelled side stripe's labels, order, numbers, reading direction, drag view and actions. */
@ExtendWith(EdtTestExtension.class)
class LabelledStripeTest {
    static ToolStripe.Button window(String id, String title, Slot slot, int number) {
        return new ToolStripe.Button(id, title, null, slot, true, Optional.empty(), number == 0 ? OptionalInt.empty() : OptionalInt.of(number));
    }

    private static LabelledStripe stripe(Side side) {
        EnterpriseLook.install(false);
        return new LabelledStripe(side, EnterpriseLook.kit());
    }

    private static int metric(DesignMetrics.Role role) { return com.formdev.flatlaf.util.UIScale.scale(EnterpriseLook.kit().metrics().value(role)); }

    @Test void itShowsItsSidesWindowsByGroupWithTheirNumbersEdgeToEdge() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            stripe.render(List.of(window("servers", "File Servers", Slot.LEFT_BOTTOM, 2), window("sftp", "SFTP", Slot.LEFT_TOP, 1),
                window("sessions", "Sessions", Slot.RIGHT_TOP, 0), window("log", "Event Log", Slot.BOTTOM_LEFT, 0)), List.of());
            MockCrops.render(stripe, stripe.getPreferredSize().width, 300, 1);
            assertThat(stripe.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("1: SFTP", "2: File Servers");
            Rectangle first = stripe.buttons().get(0).getBounds(), second = stripe.buttons().get(1).getBounds();
            assertThat(first.y).as("RAIL_GAP below the top").isEqualTo(metric(DesignMetrics.Role.RAIL_GAP));
            assertThat(second.y).as("edge to edge").isEqualTo(first.y + first.height);
            assertThat(first.width).as("the fill, without the rule").isEqualTo(stripe.getWidth() - metric(DesignMetrics.Role.DOCK_RULE));
            assertThat(stripe.buttons().get(0).isSelected()).isTrue();
            assertThat(stripe.getPreferredSize().width).as("the 28 px floor").isEqualTo(com.formdev.flatlaf.util.UIScale.scale(28));
            assertThat(stripe.empty()).isFalse();
            assertThat(stripe.emptyWithout("sftp")).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aClickTogglesAndARightClickOffersMoveTo() throws Exception {
        try {
            var stripe = stripe(Side.RIGHT);
            var toggled = new ArrayList<String>();
            stripe.onToggle = toggled::add;
            stripe.render(List.of(window("sessions", "Sessions", Slot.RIGHT_TOP, 0)), List.of());
            stripe.buttons().getFirst().doClick(0);
            assertThat(toggled).containsExactly("sessions");
            var menu = stripe.buttons().getFirst().getComponentPopupMenu();
            assertThat(((JMenu) menu.getComponent(0)).getText()).isEqualTo("Move to");
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * Spec 16.1.2: a closed, a hovered and an open label paint their text in their declared pairs
     * ({@code ChromePainter.STRIPE_LABEL}, {@code HOVERED_STRIPE_LABEL} and {@code OPEN_STRIPE_LABEL}), sampled from the 4x
     * render inside the label's tile, over every palette.
     */
    @Test void eachLabelPaintsItsDeclaredPairOverEveryPalette() throws Exception {
        var palettes = new ArrayList<>(dev.jasper.ui.testsupport.KitFixtures.themes());
        palettes.addAll(dev.jasper.ui.testsupport.KitFixtures.degenerate());
        try {
            for (var theme : palettes) for (String state : List.of("closed", "hovered", "open")) {
                boolean open = state.equals("open");
                dev.jasper.ui.swing.FlatLafInstaller.install(theme, EnterpriseLook.kit());
                var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit());
                stripe.render(List.of(new ToolStripe.Button("sftp", "SFTP", null, Slot.LEFT_TOP, open, Optional.empty(), OptionalInt.empty())), List.of());
                var label = stripe.buttons().getFirst();
                label.getModel().setRollover(state.equals("hovered"));
                var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 120, 4);
                var pair = open ? ChromePainter.OPEN_STRIPE_LABEL : state.equals("hovered") ? ChromePainter.HOVERED_STRIPE_LABEL : ChromePainter.STRIPE_LABEL;
                Color expected = dev.jasper.ui.theme.SurfaceContrast.painted(theme, pair);
                assertThat(inks(image, label.getBounds(), 4, expected))
                    .as(theme.name() + (theme.dark() ? " Dark" : " Light") + ", " + state + " label "
                        + dev.jasper.ui.theme.ColorMath.hex(expected)).isTrue();
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Whether {@code image}, rendered at {@code scale}, holds a pixel of exactly {@code color} inside {@code bounds}. */
    static boolean inks(BufferedImage image, Rectangle bounds, int scale, Color color) {
        for (int y = bounds.y * scale; y < Math.min(image.getHeight(), (bounds.y + bounds.height) * scale); y++)
            for (int x = bounds.x * scale; x < Math.min(image.getWidth(), (bounds.x + bounds.width) * scale); x++)
                if (image.getRGB(x, y) == color.getRGB()) return true;
        return false;
    }

    @Test void theLeftStripeReadsBottomToTopAndTheRightTopToBottom() throws Exception {
        try {
            var badge = Optional.of(new PanelBadge(3, PanelBadge.Tone.ACCENT, "3 unread"));
            Color ground = DerivedColor.INFO_GROUND.color(EnterpriseLook.theme(false));
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = stripe(side);
                stripe.render(List.of(new ToolStripe.Button("log", "Event Log", null, side == Side.LEFT ? Slot.LEFT_TOP : Slot.RIGHT_TOP,
                    false, badge, OptionalInt.empty())), List.of());
                var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 200, 1);
                Rectangle label = stripe.buttons().getFirst().getBounds();
                int rows = 0, sum = 0, across = 0;
                for (int y = label.y; y < label.y + label.height; y++)
                    for (int x = label.x; x < label.x + label.width; x++)
                        if (image.getRGB(x, y) == ground.getRGB()) { rows++; sum += y; across += x; }
                assertThat(rows).as(side + ": the pill is painted").isPositive();
                int middle = label.y + label.height / 2, centre = label.x + label.width / 2;
                if (side == Side.LEFT) assertThat(sum / rows).as("the pill ends the label, read bottom to top").isLessThan(middle);
                else assertThat(sum / rows).as("the pill ends the label, read top to bottom").isGreaterThan(middle);
                // The label's line, and its pill with it, lies along the stripe's outer edge, as the mocks set it.
                if (side == Side.LEFT) assertThat(across / rows).as("on the left stripe's outer half").isLessThan(centre);
                else assertThat(across / rows).as("on the right stripe's outer half").isGreaterThan(centre);
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 4: a 24 pt body thickens the stripe to its line; labels never overlap and keep their pills inside. */
    @Test void aLargeFontThickensTheStripeAndNoLabelOverlaps() throws Exception {
        try {
            EnterpriseLook.install(false);
            Kit kit = EnterpriseLook.kit();
            var big = new Kit(kit.id(), kit.metrics(), kit.type().withBody(24), kit.faces(), kit.controls(), kit.colors(), kit.uiKeys());
            var stripe = new LabelledStripe(Side.LEFT, big);
            var badge = Optional.of(new PanelBadge(12, PanelBadge.Tone.ERROR, "12 errors"));
            stripe.render(List.of(new ToolStripe.Button("a", "A rather long tool window title", null, Slot.LEFT_TOP, true, badge, OptionalInt.of(1)),
                window("b", "Second", Slot.LEFT_TOP, 0), window("c", "Third", Slot.LEFT_BOTTOM, 0)), List.of());
            int thickness = stripe.getPreferredSize().width;
            assertThat(thickness).as("thicker than the 28 px floor").isGreaterThan(com.formdev.flatlaf.util.UIScale.scale(28));
            MockCrops.render(stripe, thickness, 1200, 1);
            var bounds = stripe.buttons().stream().map(AbstractButton::getBounds).toList();
            for (int i = 1; i < bounds.size(); i++) assertThat(bounds.get(i).y).isGreaterThanOrEqualTo(bounds.get(i - 1).y + bounds.get(i - 1).height);
            var painter = new ChromePainter(big);
            var pill = painter.countPill("12");
            assertThat(bounds.getFirst().height).as("the label holds its text and its pill")
                .isGreaterThanOrEqualTo(pill.width + metric(DesignMetrics.Role.STRIPE_PILL_GAP));
            assertThat(bounds.getFirst().width).as("the pill fits across").isGreaterThanOrEqualTo(pill.height);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aDragShowsBothGroupsLiftsTheLabelAndOpensAGap() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            stripe.render(List.of(window("sftp", "SFTP", Slot.LEFT_TOP, 1), window("servers", "File Servers", Slot.LEFT_TOP, 2)), List.of());
            MockCrops.render(stripe, stripe.getPreferredSize().width, 400, 1);
            stripe.beginDrag("servers", "2: File Servers");
            var groups = stripe.dropGroups();
            assertThat(groups).extracting(DockDropResolver.Group::slot).containsExactly(Slot.LEFT_TOP, Slot.LEFT_BOTTOM);
            assertThat(groups.get(0).buttons()).as("the lifted label is out").hasSize(1);
            assertThat(groups.get(1).buttons()).as("an empty group is drop space").isEmpty();
            assertThat(groups.get(1).bounds().height).isPositive();
            stripe.showGap(Slot.LEFT_BOTTOM, 0);
            Rectangle gap = stripe.gapBounds().orElseThrow();
            assertThat(gap.y).isGreaterThanOrEqualTo(groups.get(1).bounds().y);
            stripe.endDrag();
            assertThat(stripe.dragging()).isFalse();
            assertThat(stripe.gapBounds()).isEmpty();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void railActionsFollowTheSecondGroupAndNeverStaySelected() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            var ran = new ArrayList<String>();
            var action = new AbstractAction("About Sample") { @Override public void actionPerformed(ActionEvent event) { ran.add("about"); } };
            stripe.render(List.of(window("sftp", "SFTP", Slot.LEFT_TOP, 1)), List.of(action));
            MockCrops.render(stripe, stripe.getPreferredSize().width, 400, 1);
            var buttons = stripe.buttons();
            assertThat(buttons).extracting(AbstractButton::getToolTipText).containsExactly("1: SFTP", "About Sample");
            assertThat(buttons.get(1).getY()).isGreaterThanOrEqualTo(buttons.get(0).getY() + buttons.get(0).getHeight()
                + metric(DesignMetrics.Role.RAIL_SECTION_GAP));
            buttons.get(1).doClick(0);
            assertThat(ran).containsExactly("about");
            assertThat(buttons.get(1).isSelected()).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void theStripePaintsItsSurfaceAndItsRuleTowardTheWorkspace() throws Exception {
        try {
            var theme = EnterpriseLook.theme(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = stripe(side);
                stripe.render(List.of(), List.of());
                int width = stripe.getPreferredSize().width;
                BufferedImage image = MockCrops.render(stripe, width, 120, 1);
                int inner = side == Side.LEFT ? width - 1 : 0, outer = side == Side.LEFT ? 2 : width - 3;
                assertThat(new Color(image.getRGB(inner, 60))).as(side + " rule").isEqualTo(painter.dockRule(theme));
                assertThat(new Color(image.getRGB(outer, 60))).as(side + " surface").isEqualTo(painter.stripeSurface(theme));
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
