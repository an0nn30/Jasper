package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.Command;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.PresentationIcon;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.icons.ChromeArtwork;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Plan 13 (the user's direction of 2026-10-19, as IntelliJ's classic StripeButton): a labelled side stripe's label leads,
 * in reading order, with its window's or action's 13 px icon, upright, then a 4 px gap; the stripe keeps its thickness.
 * The helpers here cut each label's icon slice out of a render, which leaves the text-only stripe (Rulings 17).
 */
@ExtendWith(EdtTestExtension.class)
class StripeIconsTest {
    /** A metric at the interface scale, from the Enterprise kit. */
    static int metric(DesignMetrics.Role role) { return UIScale.scale(EnterpriseLook.kit().metrics().value(role)); }

    /** An iconed label's extra length: its icon and the gap to its text (Execution notes R9: read after the look installs). */
    static int slice() { return metric(DesignMetrics.Role.STRIPE_ICON) + metric(DesignMetrics.Role.STRIPE_ICON_GAP); }

    /** IntelliJ's classic tool-window file for {@code name}, as Enterprise Light draws it. */
    static Icon classic(String name) {
        return new IconRegistry().stripeIcon(name, metric(DesignMetrics.Role.STRIPE_ICON)).orElseThrow()
            .icon(EnterpriseLook.theme(false), SemanticColor.TEXT_PRIMARY);
    }

    /**
     * A label's icon and its gap, across the whole label, in the stripe's coordinates: at the label's reading start, past
     * its padding (the bottom end on the left, the top end on the right, the left end on the bottom strip).
     */
    static Rectangle iconSlice(Side side, Rectangle label) {
        int padding = metric(DesignMetrics.Role.STRIPE_LABEL_PADDING), size = metric(DesignMetrics.Role.STRIPE_ICON);
        int gap = metric(DesignMetrics.Role.STRIPE_ICON_GAP);
        return switch (side) {
            case LEFT -> new Rectangle(label.x, label.y + label.height - padding - size - gap, label.width, size + gap);
            case RIGHT -> new Rectangle(label.x, label.y + padding, label.width, size + gap);
            case BOTTOM -> new Rectangle(label.x + padding, label.y, size + gap, label.height);
        };
    }

    /**
     * {@code render}, made at {@code scale}, with every iconed label's slice cut out: as rows on a side stripe, as columns on
     * the bottom strip.
     */
    static BufferedImage withoutIcons(BufferedImage render, DockStripe stripe, int scale) {
        Side side = stripe.side();
        boolean rows = side != Side.BOTTOM;
        int extent = rows ? render.getHeight() : render.getWidth();
        var cut = new boolean[extent];
        for (AbstractButton button : stripe.buttons()) {
            if (((StripeLabels.Label) button).icon == null) continue;
            Rectangle slice = iconSlice(side, button.getBounds());
            int from = scale * (rows ? slice.y : slice.x), to = from + scale * (rows ? slice.height : slice.width);
            for (int i = Math.max(0, from); i < Math.min(extent, to); i++) cut[i] = true;
        }
        var kept = new ArrayList<Integer>();
        for (int i = 0; i < extent; i++) if (!cut[i]) kept.add(i);
        var out = new BufferedImage(rows ? render.getWidth() : kept.size(), rows ? kept.size() : render.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < kept.size(); k++) {
            int i = kept.get(k);
            if (rows) for (int x = 0; x < render.getWidth(); x++) out.setRGB(x, k, render.getRGB(x, i));
            else for (int y = 0; y < render.getHeight(); y++) out.setRGB(k, y, render.getRGB(i, y));
        }
        return out;
    }

    /** {@code icon} painted alone, upright, at {@code scale} over {@code ground}, as {@link MockCrops#render} paints. */
    static BufferedImage referenceImage(Icon icon, Component receiver, Color ground, int scale) {
        int size = icon.getIconWidth() * scale;
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(ground);
            g.fillRect(0, 0, size, size);
            g.scale(scale, scale);
            icon.paintIcon(receiver, g, 0, 0);
        } finally { g.dispose(); }
        return image;
    }

    /** The pixels of {@link #referenceImage}. */
    static int[] reference(Icon icon, Component receiver, Color ground, int scale) {
        var image = referenceImage(icon, receiver, ground, scale);
        return box(image, 0, 0, image.getWidth());
    }

    /** The {@code size} square of {@code image}'s pixels from ({@code x}, {@code y}), row by row. */
    static int[] box(BufferedImage image, int x, int y, int size) { return image.getRGB(x, y, size, size, null, 0, size); }

    /** {@code square}, {@code size} wide, turned a quarter, clockwise or counter-clockwise. */
    static int[] rotated(int[] square, int size, boolean clockwise) {
        var out = new int[square.length];
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int pixel = square[y * size + x];
                if (clockwise) out[x * size + (size - 1 - y)] = pixel;
                else out[(size - 1 - x) * size + y] = pixel;
            }
        return out;
    }

    private static ToolStripe.Button window(String id, String title, Slot slot, int number, Icon icon, boolean selected) {
        return new ToolStripe.Button(id, title, icon, slot, selected, Optional.empty(), number == 0 ? OptionalInt.empty() : OptionalInt.of(number));
    }

    /** The icon box of {@code label}, in the stripe's coordinates. */
    private static Rectangle iconBox(Side side, StripeLabels.Label label) {
        Rectangle bounds = label.getBounds();
        Point at = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC).iconAt(side, bounds.width, bounds.height);
        int size = metric(DesignMetrics.Role.STRIPE_ICON);
        return new Rectangle(bounds.x + at.x, bounds.y + at.y, size, size);
    }

    /** The icon box's pixels in a render at {@code scale}. */
    private static int[] painted(BufferedImage image, Rectangle box, int scale) {
        return box(image, scale * box.x, scale * box.y, scale * box.width);
    }

    /** A copy of the Enterprise kit with its body at {@code size}. */
    private static Kit withBody(int size) {
        Kit kit = EnterpriseLook.kit();
        return new Kit(kit.id(), kit.metrics(), kit.type().withBody(size), kit.faces(), kit.controls(), kit.colors(), kit.uiKeys());
    }

    @Test void eachSideLabelDrawsItsIconUprightAtItsReadingStart() throws Exception {
        try {
            EnterpriseLook.install(false);
            var theme = EnterpriseLook.theme(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            int size = metric(DesignMetrics.Role.STRIPE_ICON);
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = new LabelledStripe(side, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.on(side, Slot.Group.TOP), 2,
                    new PresentationIcon("folder", null))), List.of());
                var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 200, 2);
                var label = (StripeLabels.Label) stripe.buttons().getFirst();
                Rectangle bounds = label.getBounds();
                var at = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC).iconAt(side, bounds.width, bounds.height);
                assertThat(at.x).as(side + ": centred across, IntelliJ's 4").isEqualTo((bounds.width - size) / 2);
                assertThat(at.y).as(side + ": at the reading start")
                    .isEqualTo(side == Side.LEFT ? bounds.height - metric(DesignMetrics.Role.STRIPE_LABEL_PADDING) - size
                        : metric(DesignMetrics.Role.STRIPE_LABEL_PADDING));
                int[] expected = reference(classic("folder"), label, painter.stripeTile(theme, true, false), 2);
                int[] painted = box(image, 2 * (bounds.x + at.x), 2 * (bounds.y + at.y), 2 * size);
                assertThat(painted).as(side + ": Project's folder, upright").isEqualTo(expected);
                assertThat(painted).as(side + ": not turned with the text").isNotEqualTo(rotated(expected, 2 * size, side == Side.RIGHT));
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Rulings 17's premise: cutting each label's icon slice out of the iconed stripe leaves the text-only stripe exactly. */
    @Test void cuttingTheIconSlicesLeavesTheTextOnlyStripe() throws Exception {
        try {
            EnterpriseLook.install(false);
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                Slot top = Slot.on(side, Slot.Group.TOP), second = Slot.on(side, Slot.Group.SECOND);
                var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                int height = 2 * metric(DesignMetrics.Role.RAIL_GAP) + labels.length("1: SFTP", Optional.empty(), null)
                    + labels.length("2: File Servers", Optional.empty(), null) + 40;
                var iconed = new LabelledStripe(side, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                iconed.render(List.of(LabelledStripeTest.window("sftp", "SFTP", top, 1, new PresentationIcon("folder", null)),
                    LabelledStripeTest.window("servers", "File Servers", second, 2, new PresentationIcon("server", null))), List.of());
                var textOnly = new LabelledStripe(side, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                textOnly.render(List.of(LabelledStripeTest.window("sftp", "SFTP", top, 1),
                    LabelledStripeTest.window("servers", "File Servers", second, 2)), List.of());
                int width = iconed.getPreferredSize().width;
                assertThat(textOnly.getPreferredSize().width).as(side + ": one thickness").isEqualTo(width);
                var cut = withoutIcons(MockCrops.render(iconed, width, height + 2 * slice(), 2), iconed, 2);
                var plain = MockCrops.render(textOnly, width, height, 2);
                assertThat(cut.getHeight()).as(side + ": the slices' rows").isEqualTo(plain.getHeight());
                assertThat(cut.getRGB(0, 0, cut.getWidth(), cut.getHeight(), null, 0, cut.getWidth()))
                    .as(side + ": the cut is the text-only stripe").isEqualTo(plain.getRGB(0, 0, plain.getWidth(), plain.getHeight(), null, 0, plain.getWidth()));
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void anIconLengthensItsLabelBySeventeenAndKeepsTheThickness() throws Exception {
        try {
            EnterpriseLook.install(false);
            var theme = EnterpriseLook.theme(false);
            var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            int size = metric(DesignMetrics.Role.STRIPE_ICON), gap = metric(DesignMetrics.Role.STRIPE_ICON_GAP);
            assertThat(slice()).isEqualTo(17);
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = new LabelledStripe(side, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.on(side, Slot.Group.TOP), 1,
                    new PresentationIcon("folder", null))), List.of());
                int width = stripe.getPreferredSize().width;
                assertThat(width).as(side + ": the line or the icon, the edges and the rule")
                    .isEqualTo(Math.max(labels.line(), size) + 2 * metric(DesignMetrics.Role.RAIL_EDGE) + metric(DesignMetrics.Role.DOCK_RULE));
                MockProfile.fonts(side + " stripe: 23 under IBM Plex", width, 23, 0);
                var image = MockCrops.render(stripe, width, 200, 2);
                var label = (StripeLabels.Label) stripe.buttons().getFirst();
                Rectangle bounds = label.getBounds();
                assertThat(bounds.height).as(side + ": the text-only length and the slice")
                    .isEqualTo(labels.length("1: SFTP", Optional.empty(), null) + slice());
                // The gap's rows, between the icon box and the text, hold only the open tile.
                Rectangle box = iconBox(side, label);
                int from = side == Side.LEFT ? box.y - gap : box.y + size;
                int tile = labels.painter().stripeTile(theme, true, false).getRGB();
                for (int y = 2 * from; y < 2 * (from + gap); y++)
                    for (int x = 2 * bounds.x; x < 2 * (bounds.x + bounds.width); x++)
                        assertThat(image.getRGB(x, y)).as(side + ": the gap at (%d, %d)", x, y).isEqualTo(tile);
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aNullOrEmptyIconDrawsNoIconAndNoGap() throws Exception {
        try {
            EnterpriseLook.install(false);
            var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            var renders = new ArrayList<int[]>();
            for (Icon icon : java.util.Arrays.asList(null, new ImageIcon())) {
                var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.LEFT_TOP, 1, icon)), List.of());
                int width = stripe.getPreferredSize().width;
                var image = MockCrops.render(stripe, width, 120, 2);
                var label = (StripeLabels.Label) stripe.buttons().getFirst();
                assertThat(label.icon).as(icon == null ? "null" : "empty").isNull();
                assertThat(label.getHeight()).isEqualTo(labels.length("1: SFTP", Optional.empty(), null));
                renders.add(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
            }
            assertThat(renders.get(1)).as("the same text-only label").isEqualTo(renders.get(0));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void theSecondGroupPacksIconedLabelsAtTheBottomEnd() throws Exception {
        try {
            EnterpriseLook.install(false);
            var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            int rail = metric(DesignMetrics.Role.RAIL_GAP);
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = new LabelledStripe(side, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                Slot top = Slot.on(side, Slot.Group.TOP), second = Slot.on(side, Slot.Group.SECOND);
                stripe.render(List.of(LabelledStripeTest.window("t", "Top", top, 0, new PresentationIcon("folder", null)),
                    LabelledStripeTest.window("a", "Alpha", second, 0, new PresentationIcon("server", null)),
                    LabelledStripeTest.window("b", "Beta", second, 0, new PresentationIcon("tunnel", null))), List.of());
                MockCrops.render(stripe, stripe.getPreferredSize().width, 400, 1);
                Rectangle t = stripe.buttons().get(0).getBounds(), a = stripe.buttons().get(1).getBounds(), b = stripe.buttons().get(2).getBounds();
                assertThat(b.y + b.height).as(side + ": Beta ends RAIL_GAP above the bottom").isEqualTo(400 - rail);
                assertThat(a.y + a.height).as(side + ": Alpha ends where Beta starts").isEqualTo(b.y);
                assertThat(t.height).as(side + ": Top's length").isEqualTo(labels.length("Top", Optional.empty(), null) + slice());
                assertThat(a.height).as(side + ": Alpha's length").isEqualTo(labels.length("Alpha", Optional.empty(), null) + slice());
                assertThat(b.height).as(side + ": Beta's length").isEqualTo(labels.length("Beta", Optional.empty(), null) + slice());
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Rulings 14: closed, hovered and open draw the same authored icon; only the tile under it changes. */
    @Test void everyStateDrawsTheSameIcon() throws Exception {
        try {
            EnterpriseLook.install(false);
            var theme = EnterpriseLook.theme(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            for (String state : List.of("closed", "hovered", "open")) {
                boolean open = state.equals("open"), hover = state.equals("hovered");
                var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                stripe.render(List.of(window("sftp", "SFTP", Slot.LEFT_TOP, 1, new PresentationIcon("folder", null), open)), List.of());
                var label = (StripeLabels.Label) stripe.buttons().getFirst();
                label.getModel().setRollover(hover);
                var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 120, 2);
                Color tile = painter.stripeTile(theme, open, hover);
                Color ground = tile != null ? tile : painter.stripeSurface(theme);
                assertThat(painted(image, iconBox(Side.LEFT, label), 2)).as(state).isEqualTo(reference(classic("folder"), label, ground, 2));
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 1: a plugin's own path leads its label in the label's ink, and nothing throws. */
    @Test void aPluginPanelsOwnGlyphLeadsItsLabel() throws Exception {
        try {
            EnterpriseLook.install(false);
            var theme = EnterpriseLook.theme(false);
            var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            var mark = new PresentationIcon("test:mark", "M 2 2 L 14 14 M 2 14 L 14 2");
            assertThatCode(() -> stripe.render(List.of(LabelledStripeTest.window("sample", "Sample", Slot.LEFT_TOP, 0, mark)), List.of()))
                .doesNotThrowAnyException();
            var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 120, 4);
            var label = (StripeLabels.Label) stripe.buttons().getFirst();
            assertThat(label.icon).isNotNull();
            Color ink = SurfaceContrast.painted(theme, ChromePainter.OPEN_STRIPE_LABEL);
            assertThat(LabelledStripeTest.inks(image, iconBox(Side.LEFT, label), 4, ink)).as("the glyph in the open label's ink").isTrue();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Rulings 10: under New UI artwork a labelled stripe draws what the rails draw, at 13 px. */
    @Test void underNewUiArtworkTheStripeDrawsTheOrdinaryIcon() throws Exception {
        try {
            EnterpriseLook.install(false);
            var theme = EnterpriseLook.theme(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit(), ChromeArtwork.NEW_UI);
            stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.LEFT_TOP, 1, new PresentationIcon("folder", null))), List.of());
            var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 120, 2);
            var label = (StripeLabels.Label) stripe.buttons().getFirst();
            var expected = new IconRegistry().chromeIcon("folder", metric(DesignMetrics.Role.STRIPE_ICON)).icon(theme, SemanticColor.TEXT_SECONDARY);
            assertThat(painted(image, iconBox(Side.LEFT, label), 2)).isEqualTo(reference(expected, label, painter.stripeTile(theme, true, false), 2));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 4: a tiny interface font never makes the stripe thinner than the icon and its edges. */
    @Test void aSmallFontKeepsTheIconInsideTheStripe() throws Exception {
        try {
            EnterpriseLook.install(false);
            var stripe = new LabelledStripe(Side.LEFT, withBody(6), ChromeArtwork.CLASSIC);
            stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.LEFT_TOP, 1, new PresentationIcon("folder", null))), List.of());
            int thickness = stripe.getPreferredSize().width;
            assertThat(thickness).as("the icon, the edges and the rule")
                .isEqualTo(metric(DesignMetrics.Role.STRIPE_ICON) + 2 * metric(DesignMetrics.Role.RAIL_EDGE) + metric(DesignMetrics.Role.DOCK_RULE))
                .isEqualTo(20);
            MockCrops.render(stripe, thickness, 120, 1);
            var label = (StripeLabels.Label) stripe.buttons().getFirst();
            assertThat(label.getBounds().contains(iconBox(Side.LEFT, label))).as("the icon box inside the label").isTrue();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 4: a large interface font thickens the stripe and the icon stays centred across it. */
    @Test void aLargeFontCentresTheIconAcross() throws Exception {
        try {
            EnterpriseLook.install(false);
            var stripe = new LabelledStripe(Side.LEFT, withBody(24), ChromeArtwork.CLASSIC);
            stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.LEFT_TOP, 1, new PresentationIcon("folder", null))), List.of());
            MockCrops.render(stripe, stripe.getPreferredSize().width, 200, 1);
            var label = (StripeLabels.Label) stripe.buttons().getFirst();
            Rectangle bounds = label.getBounds(), box = iconBox(Side.LEFT, label);
            assertThat(box.x - bounds.x).as("centred across").isEqualTo((bounds.width - metric(DesignMetrics.Role.STRIPE_ICON)) / 2);
            assertThat(bounds.contains(box)).as("the icon box inside the label").isTrue();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Rulings 15: a rail action leads with its contributed icon, else the rails' command glyph. */
    @Test void railActionsLeadWithTheirIcon() throws Exception {
        try {
            EnterpriseLook.install(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            var theme = EnterpriseLook.theme(false);
            var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            var about = new AbstractAction("About Sample") { @Override public void actionPerformed(ActionEvent event) { } };
            about.putValue(Command.ICON, new PresentationIcon("server", null));
            var plain = new AbstractAction("Plain") { @Override public void actionPerformed(ActionEvent event) { } };
            var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            stripe.render(List.of(), List.of(about, plain));
            var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 300, 2);
            StripeLabels.Label first = (StripeLabels.Label) stripe.buttons().get(0), second = (StripeLabels.Label) stripe.buttons().get(1);
            assertThat(first.icon).as("the contributed icon").isNotNull();
            assertThat(first.getHeight()).isEqualTo(labels.length("About Sample", Optional.empty(), null) + slice());
            assertThat(second.icon).as("the command glyph").isNotNull();
            assertThat(painted(image, iconBox(Side.LEFT, first), 2)).as("the classic server file")
                .isEqualTo(reference(classic("server"), first, painter.stripeSurface(theme), 2));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * Rulings 16: a dragged label's gap is as long as the label that will land, its icon included: an owned label keeps its
     * own icon, a foreign one is resolved from the window's icon, and a window without one gets the text-only length.
     */
    @Test void aDraggedLabelKeepsItsIconAndLength() throws Exception {
        try {
            EnterpriseLook.install(false);
            var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            stripe.render(List.of(LabelledStripeTest.window("t", "Top", Slot.LEFT_TOP, 0, new PresentationIcon("folder", null)),
                LabelledStripeTest.window("s", "Second", Slot.LEFT_BOTTOM, 0, new PresentationIcon("server", null))), List.of());
            MockCrops.render(stripe, stripe.getPreferredSize().width, 400, 1);
            int top = stripe.buttons().getFirst().getHeight();
            assertThat(top).as("Top's iconed length").isEqualTo(labels.length("Top", Optional.empty(), null) + slice());

            stripe.beginDrag("t", "Top", null);
            stripe.showGap(Slot.LEFT_BOTTOM, 0);
            assertThat(stripe.gapBounds().orElseThrow().height).as("an owned label keeps its own icon").isEqualTo(top);
            stripe.endDrag();

            stripe.beginDrag("x", "Foreign", new PresentationIcon("download", null));
            stripe.showGap(Slot.LEFT_BOTTOM, 0);
            assertThat(stripe.gapBounds().orElseThrow().height).as("a foreign label, its window's icon resolved")
                .isEqualTo(labels.length("Foreign", Optional.empty(), null) + slice());
            stripe.endDrag();

            stripe.beginDrag("y", "Foreign", null);
            stripe.showGap(Slot.LEFT_BOTTOM, 0);
            assertThat(stripe.gapBounds().orElseThrow().height).as("a foreign window without an icon: text only")
                .isEqualTo(labels.length("Foreign", Optional.empty(), null));
            stripe.endDrag();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Rulings 16: the labelled stripes' ghost is the window's stripe icon, or none; the rails' is the window's icon itself. */
    @Test void theGhostIsTheLabelsStripeIcon() throws Exception {
        try {
            EnterpriseLook.install(false);
            var set = new StripeSet.LabelledStripes(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            Icon ghost = set.ghost(new PresentationIcon("folder", null));
            assertThat(ghost).isNotNull();
            assertThat(reference(ghost, set.left(), Color.WHITE, 2)).as("Project's folder at 13 px")
                .isEqualTo(reference(classic("folder"), set.left(), Color.WHITE, 2));
            assertThat(set.ghost(null)).as("no icon, no ghost").isNull();
            assertThat(set.ghost(new ImageIcon())).as("an empty icon, no ghost").isNull();
            Icon folder = new PresentationIcon("folder", null);
            assertThat(new StripeSet.IconRails().ghost(folder)).as("the rails' ghost is the icon itself").isSameAs(folder);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** The bottom strip's three windows, closed: Event Log and "3: Transfers" in Bottom Left, Terminal in Bottom Right. */
    private static List<ToolStripe.Button> bottomWindows(Icon log, Icon transfers) {
        return List.of(new ToolStripe.Button("log", "Event Log", log, Slot.BOTTOM_LEFT, false),
            new ToolStripe.Button("transfers", "Transfers", transfers, Slot.BOTTOM_LEFT, false, Optional.empty(), OptionalInt.of(3)),
            new ToolStripe.Button("terminal", "Terminal", null, Slot.BOTTOM_RIGHT, false));
    }

    /** The bottom strip's labels lead with their icon, past the padding, centred down (IntelliJ's classic StripeButton). */
    @Test void theBottomStripLeadsEachLabelWithItsIconCentredDown() throws Exception {
        try {
            EnterpriseLook.install(false);
            var theme = EnterpriseLook.theme(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            int size = metric(DesignMetrics.Role.STRIPE_ICON), padding = metric(DesignMetrics.Role.STRIPE_LABEL_PADDING);
            var strip = new BottomStrip(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            strip.render(bottomWindows(AppIcons.semantic("bell"), new PresentationIcon("download", null)), List.of());
            int height = strip.getPreferredSize().height;
            assertThat(height).as("the strip stays 25 tall").isEqualTo(25);
            var image = MockCrops.render(strip, 900, height, 2);
            Color tile = painter.stripeTile(theme, false, false);
            Color ground = tile != null ? tile : painter.stripeSurface(theme);
            var log = (StripeLabels.Label) strip.buttons().get(0);
            var transfers = (StripeLabels.Label) strip.buttons().get(1);
            var terminal = (StripeLabels.Label) strip.buttons().get(2);
            for (var entry : List.of(java.util.Map.entry(log, "bell"), java.util.Map.entry(transfers, "download"))) {
                var label = entry.getKey();
                Rectangle bounds = label.getBounds();
                assertThat(label.icon).as(label.text() + ": its icon").isNotNull();
                Rectangle box = iconBox(Side.BOTTOM, label);
                assertThat(box.x).as(label.text() + ": past the padding").isEqualTo(bounds.x + padding);
                assertThat(box.y).as(label.text() + ": centred down").isEqualTo(bounds.y + (bounds.height - size) / 2).isEqualTo(6);
                assertThat(painted(image, box, 2)).as(label.text() + ": its classic tool-window file, upright")
                    .isEqualTo(reference(classic(entry.getValue()), label, ground, 2));
                assertThat(bounds.width).as(label.text() + ": the text-only length and the slice")
                    .isEqualTo(labels.length(label.text(), Optional.empty(), null) + slice());
                assertThat(new Color(image.getRGB(2 * (bounds.x + bounds.width), 2 * (height / 2))))
                    .as(label.text() + ": a divider follows it").isEqualTo(painter.stripDivider(theme));
            }
            assertThat(terminal.icon).as("Terminal has no icon").isNull();
            assertThat(terminal.getWidth()).as("Terminal's length is text-only").isEqualTo(labels.length("Terminal", Optional.empty(), null));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Rulings 17's premise on the bottom strip: cutting each label's icon columns out leaves the text-only strip exactly. */
    @Test void cuttingTheBottomStripsIconSlicesLeavesTheTextOnlyStrip() throws Exception {
        try {
            EnterpriseLook.install(false);
            var iconed = new BottomStrip(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            iconed.render(bottomWindows(AppIcons.semantic("bell"), new PresentationIcon("download", null)), List.of());
            var textOnly = new BottomStrip(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
            textOnly.render(bottomWindows(null, null), List.of());
            int height = iconed.getPreferredSize().height;
            assertThat(textOnly.getPreferredSize().height).as("one height").isEqualTo(height);
            var cut = withoutIcons(MockCrops.render(iconed, 900 + 2 * slice(), height, 2), iconed, 2);
            var plain = MockCrops.render(textOnly, 900, height, 2);
            assertThat(cut.getWidth()).as("the slices' columns").isEqualTo(plain.getWidth());
            assertThat(cut.getRGB(0, 0, cut.getWidth(), cut.getHeight(), null, 0, cut.getWidth()))
                .as("the cut is the text-only strip").isEqualTo(plain.getRGB(0, 0, plain.getWidth(), plain.getHeight(), null, 0, plain.getWidth()));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 3: a badge on an iconed label keeps its icon, and the pill follows the text, a slice further along. */
    @Test void aBadgeKeepsTheIconAndThePillFollowsTheText() throws Exception {
        try {
            EnterpriseLook.install(false);
            var theme = EnterpriseLook.theme(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            var badge = Optional.of(new PanelBadge(3, PanelBadge.Tone.ACCENT, "3 unread"));
            int pillGround = DerivedColor.INFO_GROUND.color(theme).getRGB();
            var firstPill = new ArrayList<Integer>();
            for (Icon icon : java.util.Arrays.asList(AppIcons.semantic("bell"), null)) {
                var strip = new BottomStrip(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                strip.render(bottomWindows(icon, null), List.of());
                int height = strip.getPreferredSize().height;
                MockCrops.render(strip, 900, height, 1);
                var label = (StripeLabels.Label) strip.buttons().getFirst();
                Icon before = label.icon;
                int length = label.getWidth();
                strip.updateBadge("log", badge);
                var image = MockCrops.render(strip, 900, height, 1);
                assertThat(strip.buttons().getFirst()).as("the same label").isSameAs(label);
                if (icon != null) {
                    assertThat(before).isNotNull();
                    assertThat(label.icon).as("the badge keeps the icon").isSameAs(before);
                }
                assertThat(label.getWidth()).as("the label grows by the pill")
                    .isEqualTo(length + metric(DesignMetrics.Role.STRIPE_PILL_GAP) + painter.countPill("3").width);
                Rectangle bounds = label.getBounds();
                int first = -1;
                for (int x = bounds.x; x < bounds.x + bounds.width && first < 0; x++)
                    for (int y = bounds.y; y < bounds.y + bounds.height; y++)
                        if (image.getRGB(x, y) == pillGround) { first = x; break; }
                assertThat(first).as("the pill is painted").isNotNegative();
                firstPill.add(first);
            }
            assertThat(firstPill.get(0)).as("the pill, a slice further along than on a text-only label").isEqualTo(firstPill.get(1) + slice());
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
