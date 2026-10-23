package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.icons.ChromeArtwork;
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
    static ToolStripe.Button window(String id, String title, Slot slot, int number) { return window(id, title, slot, number, null); }

    /** An open window's button with {@code icon}, its toggle ending in {@code number} unless that is 0. */
    static ToolStripe.Button window(String id, String title, Slot slot, int number, javax.swing.Icon icon) {
        return new ToolStripe.Button(id, title, icon, slot, true, Optional.empty(), number == 0 ? OptionalInt.empty() : OptionalInt.of(number));
    }

    private static LabelledStripe stripe(Side side) {
        EnterpriseLook.install(false);
        return new LabelledStripe(side, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
    }

    private static int metric(DesignMetrics.Role role) { return com.formdev.flatlaf.util.UIScale.scale(EnterpriseLook.kit().metrics().value(role)); }

    /**
     * A labelled side stripe's whole thickness: the label's line across it (the face's fractional ascent and descent,
     * rounded) or the 13 px stripe icon, whichever is larger, its two rail edges and its rule.
     */
    private static int fitted(Kit kit) {
        var line = new StripeLabels(kit, ChromeArtwork.CLASSIC).font().getLineMetrics("Mg", new java.awt.font.FontRenderContext(null, true, true));
        return Math.max(Math.round(line.getAscent() + line.getDescent()), metric(DesignMetrics.Role.STRIPE_ICON))
            + 2 * metric(DesignMetrics.Role.RAIL_EDGE) + metric(DesignMetrics.Role.DOCK_RULE);
    }

    @Test void itShowsItsSidesWindowsByGroupWithTheirNumbers() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            stripe.render(List.of(window("servers", "File Servers", Slot.LEFT_BOTTOM, 2), window("sftp", "SFTP", Slot.LEFT_TOP, 1),
                window("sessions", "Sessions", Slot.RIGHT_TOP, 0), window("log", "Event Log", Slot.BOTTOM_LEFT, 0)), List.of());
            MockCrops.render(stripe, stripe.getPreferredSize().width, 300, 1);
            assertThat(stripe.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("1: SFTP", "2: File Servers");
            Rectangle first = stripe.buttons().get(0).getBounds(), second = stripe.buttons().get(1).getBounds();
            assertThat(first.y).as("RAIL_GAP below the top").isEqualTo(metric(DesignMetrics.Role.RAIL_GAP));
            assertThat(second.y + second.height).as("the second group RAIL_GAP above the bottom").isEqualTo(300 - metric(DesignMetrics.Role.RAIL_GAP));
            assertThat(first.width).as("the fill, without the rule").isEqualTo(stripe.getWidth() - metric(DesignMetrics.Role.DOCK_RULE));
            assertThat(stripe.buttons().get(0).isSelected()).isTrue();
            assertThat(stripe.getPreferredSize().width).as("the label's line, its edges and the rule").isEqualTo(fitted(EnterpriseLook.kit()));
            assertThat(stripe.empty()).isFalse();
            assertThat(stripe.emptyWithout("sftp")).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** The user's direction of 2026-10-18: a labelled stripe fits its labels, and window.rail_width does not resize it. */
    @Test void theStripeFitsItsLabelsWhateverThePanelRailWidth() throws Exception {
        try {
            var stripe = stripe(Side.RIGHT);
            stripe.render(List.of(window("sessions", "Sessions", Slot.RIGHT_TOP, 0)), List.of());
            int fitted = stripe.getPreferredSize().width;
            assertThat(fitted).as("narrower than the 28 px rail").isLessThan(com.formdev.flatlaf.util.UIScale.scale(28));
            for (int width : new int[] {28, 40, 64}) {
                stripe.setRailWidth(width);
                assertThat(stripe.getPreferredSize().width).as("window.rail_width " + width).isEqualTo(fitted);
                assertThat(stripe.getMinimumSize().width).isEqualTo(fitted);
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aClickTogglesAndARightClickOffersViewModeAndMoveTo() throws Exception {
        try {
            var stripe = stripe(Side.RIGHT);
            var toggled = new ArrayList<String>();
            stripe.onToggle = toggled::add;
            stripe.render(List.of(window("sessions", "Sessions", Slot.RIGHT_TOP, 0)), List.of());
            stripe.buttons().getFirst().doClick(0);
            assertThat(toggled).containsExactly("sessions");
            var menu = stripe.buttons().getFirst().getComponentPopupMenu();
            assertThat(((JMenu) menu.getComponent(0)).getText()).isEqualTo("View Mode");
            assertThat(((JMenu) menu.getComponent(1)).getText()).isEqualTo("Move to");
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aRightClickMenuChecksTheLabelsModeAndChoosingAnotherSetsIt() throws Exception {
        try {
            var stripe = stripe(Side.RIGHT);
            var events = new ArrayList<String>();
            stripe.onMode = (id, mode) -> events.add(id + ":" + mode);
            stripe.render(List.of(new ToolStripe.Button("sessions", "Sessions", null, Slot.RIGHT_TOP, true, Optional.empty(), OptionalInt.empty(),
                ToolWindowMode.FLOAT)), List.of());
            var menu = stripe.buttons().getFirst().getComponentPopupMenu();
            assertThat(((javax.swing.JCheckBoxMenuItem) ToolWindowHeaderTest.item(menu, "Float")).isSelected()).isTrue();
            stripe.updateBadge("sessions", Optional.of(new PanelBadge(2, PanelBadge.Tone.ACCENT, "2 new")));
            assertThat(((javax.swing.JCheckBoxMenuItem) ToolWindowHeaderTest.item(stripe.buttons().getFirst().getComponentPopupMenu(), "Float")).isSelected())
                .as("a badge change keeps the mode").isTrue();
            ToolWindowHeaderTest.item(menu, "Float").doClick();
            assertThat(events).as("the current mode does nothing").isEmpty();
            ToolWindowHeaderTest.item(menu, "Docked").doClick();
            assertThat(events).containsExactly("sessions:DOCKED");
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
                var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
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
                int middle = label.y + label.height / 2;
                if (side == Side.LEFT) assertThat(sum / rows).as("the pill ends the label, read bottom to top").isLessThan(middle);
                else assertThat(sum / rows).as("the pill ends the label, read top to bottom").isGreaterThan(middle);
                // The label's line, and its pill with it, is centred across the stripe's fill (the user's direction).
                assertThat(across / (double) rows).as(side + ": the pill is centred across").isCloseTo(label.x + (label.width - 1) / 2.0,
                    org.assertj.core.data.Offset.offset(1.0));
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** The user's direction of 2026-10-18: a label's text is centred across the stripe's fill, on both sides. */
    @Test void theLabelsTextIsCentredAcrossTheStripe() throws Exception {
        try {
            var theme = EnterpriseLook.theme(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = stripe(side);
                stripe.render(List.of(new ToolStripe.Button("sftp", "SFTP", null, side == Side.LEFT ? Slot.LEFT_TOP : Slot.RIGHT_TOP,
                    false, Optional.empty(), OptionalInt.empty())), List.of());
                var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 120, 4);
                Rectangle label = stripe.buttons().getFirst().getBounds();
                int surface = painter.stripeSurface(theme).getRGB(), first = Integer.MAX_VALUE, last = -1;
                for (int y = label.y * 4; y < (label.y + label.height) * 4; y++)
                    for (int x = label.x * 4; x < (label.x + label.width) * 4; x++)
                        if (image.getRGB(x, y) != surface) { first = Math.min(first, x); last = Math.max(last, x); }
                assertThat(last).as(side + ": the text is painted").isNotNegative();
                // "SFTP" is capitals, centred by their cap height: their ink's middle stands within half a pixel of the
                // fill's middle.
                assertThat((first + last + 1) / 2.0 / 4).as(side + ": the capitals are centred across")
                    .isCloseTo(label.x + label.width / 2.0, org.assertj.core.data.Offset.offset(0.5));
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 4: a 24 pt body thickens the stripe to its line; labels never overlap and keep their pills inside. */
    @Test void aLargeFontThickensTheStripeAndNoLabelOverlaps() throws Exception {
        try {
            EnterpriseLook.install(false);
            Kit kit = EnterpriseLook.kit();
            var big = new Kit(kit.id(), kit.metrics(), kit.type().withBody(24), kit.faces(), kit.controls(), kit.colors(), kit.uiKeys());
            var stripe = new LabelledStripe(Side.LEFT, big, ChromeArtwork.CLASSIC);
            var badge = Optional.of(new PanelBadge(12, PanelBadge.Tone.ERROR, "12 errors"));
            stripe.render(List.of(new ToolStripe.Button("a", "A rather long tool window title", null, Slot.LEFT_TOP, true, badge, OptionalInt.of(1)),
                window("b", "Second", Slot.LEFT_TOP, 0), window("c", "Third", Slot.LEFT_BOTTOM, 0)), List.of());
            int thickness = stripe.getPreferredSize().width;
            assertThat(thickness).as("thicker than at the kit's own size").isGreaterThan(new LabelledStripe(Side.LEFT, kit, ChromeArtwork.CLASSIC).getPreferredSize().width)
                .isEqualTo(fitted(big));
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

    /** The user's direction of 2026-10-18: the second group stands at the stripe's bottom end, in order, edge to edge. */
    @Test void theSecondGroupStandsAtTheBottomEndInOrder() throws Exception {
        try {
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = stripe(side);
                Slot top = Slot.on(side, Slot.Group.TOP), second = Slot.on(side, Slot.Group.SECOND);
                stripe.render(List.of(window("a", "Alpha", second, 0), window("t", "Top", top, 0), window("b", "Beta", second, 0)), List.of());
                MockCrops.render(stripe, stripe.getPreferredSize().width, 400, 1);
                assertThat(stripe.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Top", "Alpha", "Beta");
                Rectangle t = stripe.buttons().get(0).getBounds(), a = stripe.buttons().get(1).getBounds(), b = stripe.buttons().get(2).getBounds();
                assertThat(t.y).as(side + ": the top group from RAIL_GAP").isEqualTo(metric(DesignMetrics.Role.RAIL_GAP));
                assertThat(b.y + b.height).as(side + ": the second group's last label RAIL_GAP above the bottom")
                    .isEqualTo(400 - metric(DesignMetrics.Role.RAIL_GAP));
                assertThat(a.y + a.height).as(side + ": edge to edge, in order").isEqualTo(b.y);
                assertThat(a.y).as(side + ": below the top group").isGreaterThan(t.y + t.height);
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** A stripe too short for both groups keeps the second group after the top group; labels past its end are cut off. */
    @Test void aShortStripeNeverOverlapsItsGroups() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            stripe.render(List.of(window("t", "A long top group title", Slot.LEFT_TOP, 0), window("s", "A long second title", Slot.LEFT_BOTTOM, 0)),
                List.of());
            MockCrops.render(stripe, stripe.getPreferredSize().width, 120, 1);
            Rectangle t = stripe.buttons().get(0).getBounds(), s = stripe.buttons().get(1).getBounds();
            assertThat(s.y).isEqualTo(t.y + t.height);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * A left stripe too short for its top group, its rail actions and its second group keeps its windows: the second group
     * still ends {@code RAIL_GAP} above the bottom and the actions follow it, where the stripe's end cuts them off; the
     * second group's drop bounds keep their place.
     */
    @Test void aShortStripeGivesUpItsRailActionsBeforeItsWindows() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            var action = new AbstractAction("About Sample") { @Override public void actionPerformed(ActionEvent event) { } };
            stripe.render(List.of(window("t", "A long top group title", Slot.LEFT_TOP, 0), window("s", "Second", Slot.LEFT_BOTTOM, 0)),
                List.of(action));
            var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            int rail = metric(DesignMetrics.Role.RAIL_GAP), between = metric(DesignMetrics.Role.STRIPE_LABEL_GAP);
            int top = labels.length("A long top group title", Optional.empty(), null), second = labels.length("Second", Optional.empty(), null);
            int actions = metric(DesignMetrics.Role.RAIL_SECTION_GAP)
                + labels.length("About Sample", Optional.empty(), labels.icon(ToolStripe.actionIcon(action))) + between;
            int slack = 6, height = 2 * rail + top + between + second + slack;
            assertThat(actions).as("the actions need more than the slack").isGreaterThan(slack);
            MockCrops.render(stripe, stripe.getPreferredSize().width, height, 1);
            assertThat(stripe.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("A long top group title", "About Sample", "Second");
            Rectangle t = stripe.buttons().get(0).getBounds(), about = stripe.buttons().get(1).getBounds(), s = stripe.buttons().get(2).getBounds();
            assertThat(s.y + s.height).as("the second group at the bottom end").isEqualTo(height - rail);
            assertThat(s.y).as("after the top group").isGreaterThanOrEqualTo(t.y + t.height + between);
            assertThat(about.y).as("the actions follow the second group").isGreaterThanOrEqualTo(s.y + s.height);
            stripe.beginDrag("t", "A long top group title", null);
            var groups = stripe.dropGroups();
            assertThat(groups.get(1).bounds().height).as("the second group can take a drop").isPositive();
            assertThat(groups.get(1).bounds().contains(s.x + s.width / 2, s.y + s.height / 2)).isTrue();
            stripe.endDrag();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * A drag over the bottom-anchored second group: its drop bounds start midway between the top group's end and its
     * start, a drop above or below a label's middle opens the gap before or after it, the lifted label's length stays at
     * the group's end (the stripe's bottom), and an empty second group's drop space lies at the bottom end. Both sides.
     */
    @Test void aDragOverTheSecondGroupDropsWhereTheGapOpens() throws Exception {
        try {
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                Slot top = Slot.on(side, Slot.Group.TOP), second = Slot.on(side, Slot.Group.SECOND);
                int gapEdge = metric(DesignMetrics.Role.RAIL_GAP), height = 400;
                var stripe = stripe(side);
                stripe.render(List.of(window("t", "Top", top, 0), window("u", "Under", top, 0), window("f", "File Servers", second, 2)), List.of());
                MockCrops.render(stripe, stripe.getPreferredSize().width, height, 1);
                Rectangle under = stripe.buttons().get(1).getBounds(), servers = stripe.buttons().get(2).getBounds();
                // A drag of a top window: the second group keeps its label at the bottom end.
                stripe.beginDrag("u", "Under", null);
                var groups = stripe.dropGroups();
                assertThat(groups).extracting(DockDropResolver.Group::slot).containsExactly(top, second);
                Rectangle topBounds = groups.get(0).bounds(), secondBounds = groups.get(1).bounds();
                assertThat(topBounds.y + topBounds.height).as(side + ": the groups meet").isEqualTo(secondBounds.y);
                assertThat(secondBounds.y + secondBounds.height).as(side + ": to the stripe's end").isEqualTo(height);
                assertThat(secondBounds.y).as(side + ": after the top group's end (its lifted label's length kept)")
                    .isGreaterThan(under.y + under.height).isLessThan(servers.y);
                assertThat(groups.get(1).buttons()).as(side + ": the second group's label stays").containsExactly(servers);
                var workspace = new Rectangle(500, 0, 500, height);
                int x = servers.x + servers.width / 2;
                assertThat(DockDropResolver.resolve(new java.awt.Point(x, servers.y + 2), groups, workspace))
                    .contains(DockDropResolver.Target.stripe(second, 0));
                assertThat(DockDropResolver.resolve(new java.awt.Point(x, servers.y + servers.height - 2), groups, workspace))
                    .contains(DockDropResolver.Target.stripe(second, 1));
                // Before the label: the gap opens above it and the label stays at the bottom end.
                stripe.showGap(second, 0);
                Rectangle gap = stripe.gapBounds().orElseThrow();
                assertThat(gap.y + gap.height).as(side + ": the gap ends where the label starts").isEqualTo(servers.y);
                assertThat(stripe.buttons().get(2).getBounds()).isEqualTo(servers);
                // After it: the gap takes the bottom end and the label moves up by the gap.
                stripe.showGap(second, 1);
                gap = stripe.gapBounds().orElseThrow();
                assertThat(gap.y + gap.height).as(side + ": the gap at the bottom end").isEqualTo(height - gapEdge);
                assertThat(stripe.buttons().get(2).getY() + stripe.buttons().get(2).getHeight()).isEqualTo(gap.y);
                // Back over the top group: the gap opens there and the second group is where it was.
                stripe.showGap(top, 1);
                assertThat(stripe.gapBounds().orElseThrow().y).isEqualTo(stripe.buttons().get(0).getY() + stripe.buttons().get(0).getHeight());
                assertThat(stripe.buttons().get(2).getBounds()).isEqualTo(servers);
                stripe.endDrag();

                // Dragging the second group's only label: its length stays at the bottom end as the group's drop space.
                stripe.beginDrag("f", "2: File Servers", null);
                groups = stripe.dropGroups();
                assertThat(groups.get(1).buttons()).as(side + ": lifted out").isEmpty();
                assertThat(groups.get(1).bounds().contains(x, servers.y + servers.height / 2)).as(side + ": its place drops back").isTrue();
                assertThat(DockDropResolver.resolve(new java.awt.Point(x, servers.y + servers.height / 2), groups, workspace))
                    .contains(DockDropResolver.Target.stripe(second, 0));
                stripe.showGap(second, 0);
                assertThat(stripe.gapBounds().orElseThrow()).as(side + ": the gap takes its place").isEqualTo(servers);
                stripe.endDrag();

                // An empty second group shows one label's drop space at the bottom end.
                stripe.render(List.of(window("t", "Top", top, 0), window("u", "Under", top, 0)), List.of());
                MockCrops.render(stripe, stripe.getPreferredSize().width, height, 1);
                stripe.beginDrag("u", "Under", null);
                groups = stripe.dropGroups();
                assertThat(groups.get(1).buttons()).isEmpty();
                stripe.showGap(second, 0);
                gap = stripe.gapBounds().orElseThrow();
                assertThat(gap.y + gap.height).as(side + ": an empty group's drop space at the bottom end").isEqualTo(height - gapEdge);
                assertThat(groups.get(1).bounds().contains(x, gap.y + gap.height / 2)).as(side + ": and it drops there").isTrue();
                stripe.endDrag();
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aDragShowsBothGroupsLiftsTheLabelAndOpensAGap() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            stripe.render(List.of(window("sftp", "SFTP", Slot.LEFT_TOP, 1), window("servers", "File Servers", Slot.LEFT_TOP, 2)), List.of());
            MockCrops.render(stripe, stripe.getPreferredSize().width, 400, 1);
            stripe.beginDrag("servers", "2: File Servers", null);
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

    @Test void railActionsFollowTheTopGroupAndNeverStaySelected() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            var ran = new ArrayList<String>();
            var action = new AbstractAction("About Sample") { @Override public void actionPerformed(ActionEvent event) { ran.add("about"); } };
            stripe.render(List.of(window("sftp", "SFTP", Slot.LEFT_TOP, 1), window("servers", "File Servers", Slot.LEFT_BOTTOM, 2)), List.of(action));
            MockCrops.render(stripe, stripe.getPreferredSize().width, 400, 1);
            var buttons = stripe.buttons();
            assertThat(buttons).as("the stripe's order").extracting(AbstractButton::getToolTipText)
                .containsExactly("1: SFTP", "About Sample", "2: File Servers");
            assertThat(List.of(stripe.getComponents())).as("the accessible children's order").containsExactlyElementsOf(buttons);
            var about = buttons.get(1);
            assertThat(about.getY()).as("RAIL_SECTION_GAP after the top group").isEqualTo(buttons.get(0).getY() + buttons.get(0).getHeight()
                + metric(DesignMetrics.Role.RAIL_SECTION_GAP));
            assertThat(about.getY() + about.getHeight()).as("above the second group").isLessThan(buttons.get(2).getY());
            buttons = List.of(buttons.get(0), about);
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
