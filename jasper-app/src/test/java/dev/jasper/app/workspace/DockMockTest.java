package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.awt.Rectangle;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 16.1.4 for the dock chrome: each surface plan 7 builds, rendered at 2x under the shipped Enterprise kit, IBM Plex and
 * theme ({@link EnterpriseLook}), against its crop of the Workstation (Light), Dark Mode and Unlock Vault mocks. Probes that
 * follow the UI face's glyphs are asserted under IBM Plex and printed only where an installed IBM Plex hides the plugin's
 * files ({@link MockProfile#fonts}, {@link MockCrops#fontsFloor}). Renders, crops and their differences are written under
 * {@code jasper-app/build/dock-renders/}. Mock data (titles, numbers, counts) are fixtures. The render's colours are the shipped
 * theme's roles and painted pairs, seen through the variant's scrim; the mocks give the geometry. Since the user's direction
 * of 2026-10-19 the stripes' labels start with their icons, which the text-only mocks do not draw: each stripe is compared
 * with its icon slices cut out, and the slices are asserted as the icons.
 */
@ExtendWith(EdtTestExtension.class)
class DockMockTest {
    /**
     * One mock: its file, the theme variant the render uses, where the Workstation's geometry starts in it, and the scrim
     * over it.
     *
     * @param name a short name for the written files
     * @param mock the mock's path under the reference folder
     * @param dark whether the render uses Enterprise Dark
     * @param offset where the Workstation's (0, 0) lies in the mock, at 2x
     * @param scrim the black over the window, 0 for none
     */
    record Variant(String name, String mock, boolean dark, int offset, double scrim) {
        /** The mock's rectangle at the Workstation's 2x coordinates. */
        BufferedImage crop(int x, int y, int width, int height) { return MockCrops.crop(MockCrops.mock(mock), x + offset, y + offset, width, height); }

        /** A render as this mock shows it: under its scrim. */
        BufferedImage seen(BufferedImage render) { return scrim > 0 ? MockCrops.scrim(render, scrim) : render; }

        /** {@code colour} as this mock shows it: under its scrim. */
        Color seen(Color colour) {
            var one = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            one.setRGB(0, 0, colour.getRGB());
            return MockCrops.at(seen(one), 0, 0);
        }

        /** The shipped theme's {@code key} in this variant, under its scrim. */
        Color role(String key) { return seen(MockProfile.role(dark, key)); }

        /** {@code pair} as this variant paints it, under its scrim. */
        Color painted(PaintPair pair) { return seen(MockProfile.painted(dark, pair)); }

        /** How near the open tile must be: 8 in Light, where the mock's tile is 8 from the theme's (Rulings 6), else 3. */
        int tileTolerance() { return dark ? 3 : 8; }

        /**
         * How near a fill or rule must be: 3, or 5 under the Unlock Vault mock's scrim, which has a cool cast (white reads
         * #C9CACC, 2 to 3 bluer than black at 21 % gives; Execution notes R6).
         */
        int fillTolerance() { return scrim > 0 ? 5 : 3; }

        /** How near a header action glyph's ink must be: 24, and the scrim's cool cast (2) under it, as for the fills. */
        int glyphInkTolerance() { return 24 + fillTolerance() - 3; }
    }

    static final Variant LIGHT = new Variant("light", MockCrops.CONNECTED, false, 0, 0);
    static final Variant DARK = new Variant("dark", MockCrops.DARK, true, 0, 0);
    static final Variant VAULT = new Variant("vault", MockCrops.VAULT, false, MockCrops.VAULT_OFFSET, MockCrops.VAULT_SCRIM);
    static final List<Variant> VARIANTS = List.of(LIGHT, DARK, VAULT);
    /** The Disconnected Workstation mock (the Unlock Vault mock's window). */
    static final Variant LIGHT_DISCONNECTED = new Variant("light-disconnected", MockCrops.DISCONNECTED, false, 0, 0);
    /** Every mock with the connected or disconnected panels it shows. */
    static final List<Map.Entry<Variant, Boolean>> WORKSTATIONS = List.of(Map.entry(LIGHT, true), Map.entry(LIGHT_DISCONNECTED, false),
        Map.entry(DARK, true), Map.entry(VAULT, false));
    /**
     * How near a header action glyph's extent along the strip must be to the mock's, at 2x: the module's glyphs share one
     * 10 px box where the mock sets text glyphs, so "+" is 14 against 12, the import arrow 12 to 13 against 14, the
     * ellipsis 16 against 19 and the minus 14 to 16 against 12 (its antialiased ends reach the run threshold on the light
     * strips).
     */
    static final int GLYPH_EXTENT = 4;
    /** The header strips' agreement floor over their text, left of their icons. */
    // The user's 2026-10-18 direction: the IntelliJ Light (Dark) port; measured 0.907 at the lowest (Dark's servers headers).
    static final double HEADER_FLOOR = 0.87;
    /** Agreement floors (spec 16.1.4): the share of pixels within 24 per channel of the mock. */
    // The user's 2026-10-18 direction: the IntelliJ Light (Dark) port; measured 0.882 at the lowest (the vault right stripe).
    static final double STRIPE_FLOOR = 0.85;

    @Test void theLeftStripeMatchesTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.LEFT_TOP, 1, new PresentationIcon("folder", null)),
                    LabelledStripeTest.window("servers", "File Servers", Slot.LEFT_BOTTOM, 2, new PresentationIcon("server", null))), List.of());
                int thickness = stripe.getPreferredSize().width;
                int height = fitted("1: SFTP", "2: File Servers"), iconed = height + 2 * StripeIconsTest.slice();
                var render = variant.seen(MockCrops.render(stripe, thickness, iconed, 2));
                MockCrops.write(variant.name() + "-left-stripe", MockCrops.crop(render, 0, 0, 44, 2 * iconed), variant.crop(2, 180, 44, 2 * iconed));
                MockProfile.fonts(variant.name() + " left stripe: the stripe fits its labels, the mocks' 22 and the rule", thickness, 23, 0);
                assertSecondAtTheEnd(variant.name() + " left stripe", stripe, iconed);
                assertIcons(variant, "left-stripe", render, stripe, List.of("folder", "server"));
                compareStripe(variant, "left-stripe-text", StripeIconsTest.withoutIcons(render, stripe, 2), variant.crop(2, 180, 44, 2 * height),
                    thickness, Side.LEFT, List.of(29, 109, 151, 301));
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    @Test void theRightStripeMatchesTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var stripe = new LabelledStripe(Side.RIGHT, EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                stripe.render(List.of(LabelledStripeTest.window("sessions", "Sessions", Slot.RIGHT_TOP, 0, new PresentationIcon("network", null)),
                    LabelledStripeTest.window("tunnels", "Tunnels", Slot.RIGHT_BOTTOM, 0, new PresentationIcon("tunnel", null))), List.of());
                int thickness = stripe.getPreferredSize().width;
                int height = fitted("Sessions", "Tunnels"), iconed = height + 2 * StripeIconsTest.slice();
                var render = variant.seen(MockCrops.render(stripe, thickness, iconed, 2));
                MockCrops.write(variant.name() + "-right-stripe", MockCrops.crop(render, 2 * thickness - 44, 0, 44, 2 * iconed),
                    variant.crop(2838, 180, 44, 2 * iconed));
                assertSecondAtTheEnd(variant.name() + " right stripe", stripe, iconed);
                assertIcons(variant, "right-stripe", render, stripe, List.of("network", "tunnel"));
                compareStripe(variant, "right-stripe-text", StripeIconsTest.withoutIcons(render, stripe, 2), variant.crop(2838, 180, 44, 2 * height),
                    thickness, Side.RIGHT, List.of(29, 121, 164, 247));
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    /**
     * How tall a side stripe holding one label in each group is when its second group, which stands at the stripe's
     * bottom end (the user's direction of 2026-10-18), meets its top group: {@code RAIL_GAP} at both ends and the two
     * labels. The mocks draw the second group straight under the top group, so the stripe comparisons render the stripe
     * this tall (166 and 138, the mocks' crops 332 and 276 tall at 2x) and the mocks' labels compare in place.
     */
    static int fitted(String top, String second) {
        var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
        return 2 * rail() + labels.length(top, java.util.Optional.empty(), null) + labels.length(second, java.util.Optional.empty(), null);
    }

    private static int rail() {
        return com.formdev.flatlaf.util.UIScale.scale(EnterpriseLook.kit().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.RAIL_GAP));
    }

    /**
     * Every iconed label of {@code stripe} in {@code render} (2x, under the variant's scrim): its whole icon slice
     * ({@link StripeIconsTest#iconSlice}) is its tile (the open tile, or the closed stripe's surface) with IntelliJ's
     * classic file for the fixture's name, in {@code art}'s order, painted upright at its icon box, as the variant's mock
     * shows it, so the art, the gap and the margins beside the box are pinned together. Each fixture here gives two of its
     * labels an icon.
     */
    static void assertIcons(Variant variant, String name, BufferedImage render, DockStripe stripe, List<String> art) {
        var theme = EnterpriseLook.theme(variant.dark());
        var labels = new StripeLabels(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
        int iconed = 0;
        for (AbstractButton button : stripe.buttons()) {
            var label = (StripeLabels.Label) button;
            if (label.icon == null) continue;
            String file = art.get(iconed++);
            Rectangle bounds = label.getBounds(), slice = StripeIconsTest.iconSlice(stripe.side(), bounds);
            Point at = labels.iconAt(stripe.side(), bounds.width, bounds.height);
            Color tile = labels.painter().stripeTile(theme, label.isSelected(), false);
            Color ground = tile != null ? tile : labels.painter().stripeSurface(theme);
            var expected = new BufferedImage(2 * slice.width, 2 * slice.height, BufferedImage.TYPE_INT_RGB);
            var g = expected.createGraphics();
            try {
                g.setColor(ground);
                g.fillRect(0, 0, expected.getWidth(), expected.getHeight());
                g.drawImage(StripeIconsTest.referenceImage(StripeIconsTest.classic(file, variant.dark()), label, ground, 2),
                    2 * (bounds.x + at.x - slice.x), 2 * (bounds.y + at.y - slice.y), null);
            } finally { g.dispose(); }
            var seen = variant.seen(expected);
            assertThat(render.getRGB(2 * slice.x, 2 * slice.y, seen.getWidth(), seen.getHeight(), null, 0, seen.getWidth()))
                .as(variant.name() + " " + name + ": " + label.text() + "'s icon slice, " + file + " in its tile")
                .isEqualTo(seen.getRGB(0, 0, seen.getWidth(), seen.getHeight(), null, 0, seen.getWidth()));
        }
        assertThat(iconed).as(variant.name() + " " + name + ": its iconed labels").isEqualTo(art.size());
    }

    /** The stripe's second label ends {@code RAIL_GAP} above its bottom end. */
    static void assertSecondAtTheEnd(String where, LabelledStripe stripe, int height) {
        Rectangle second = stripe.buttons().get(1).getBounds();
        assertThat(second.y + second.height).as(where + ": the second group at the bottom end").isEqualTo(height - rail());
    }

    /**
     * A side stripe against its mock crop (the mock's 22 px fill at 2x; the stripe is the mocks' 23 with its rule): the
     * stripe and open-tile fills, one ink run per label with the mock's ink, the first label's leading ink within 2 px, and
     * under IBM Plex the other run edges within 2, the labels' line centred across the fill (the user's direction of
     * 2026-10-18; the mocks set it along the outer edge, 3 px further out) and the share of agreeing pixels along it.
     */
    static void compareStripe(Variant variant, String name, BufferedImage render, BufferedImage mock, int thickness, Side side,
                              List<Integer> expectedRuns) {
        var window = MockCrops.crop(render, side == Side.LEFT ? 0 : 2 * thickness - mock.getWidth(), 0, mock.getWidth(), render.getHeight());
        MockCrops.write(variant.name() + "-" + name, MockCrops.crop(window, 0, 0, mock.getWidth(), mock.getHeight()), mock);
        String where = variant.name() + " " + name;
        Color stripeFill = MockCrops.at(mock, 2, 4), tile = MockCrops.at(mock, 2, 40);
        Color myFill = variant.role("surface.stripe"), myTile = variant.role("surface.control.selected");
        assertThat(MockCrops.near(MockCrops.at(window, 2, 4), myFill, variant.fillTolerance())).as(where + ": the stripe's surface").isTrue();
        assertThat(MockCrops.near(MockCrops.at(window, 2, 40), myTile, variant.tileTolerance())).as(where + ": the open tile").isTrue();
        var theirs = MockCrops.runs(mock, true, 120, 20, stripeFill, tile);
        var mine = MockCrops.runs(window, true, 120, 20, myFill, myTile);
        assertThat(theirs).as(where + ": the mock's labels").hasSize(expectedRuns.size() / 2);
        for (int i = 0; i < theirs.size(); i++) {
            assertThat(theirs.get(i).start()).as(where + " mock run " + i).isBetween(expectedRuns.get(2 * i) - 2, expectedRuns.get(2 * i) + 2);
            assertThat(theirs.get(i).end()).as(where + " mock run " + i).isBetween(expectedRuns.get(2 * i + 1) - 2, expectedRuns.get(2 * i + 1) + 2);
        }
        assertThat(mine).as(where + ": one ink run per label").hasSameSizeAs(theirs);
        // The first label's leading ink lies RAIL_GAP and STRIPE_LABEL_PADDING below the stripe's top whatever the face;
        // every later edge follows the labels' advance widths, and the agreeing share their glyphs: asserted under IBM
        // Plex within 2 px and against STRIPE_FLOOR.
        assertThat(mine.getFirst().start()).as(where + " label 0 starts")
            .isBetween(theirs.getFirst().start() - 2, theirs.getFirst().start() + 2);
        for (int i = 0; i < theirs.size(); i++) {
            if (i > 0) MockProfile.fonts(where + " label " + i + " start", mine.get(i).start(), theirs.get(i).start(), 2);
            MockProfile.fonts(where + " label " + i + " end", mine.get(i).end(), theirs.get(i).end(), 2);
            // Every label is an open window's, on its tile.
            assertThat(MockCrops.near(mine.get(i).ink(), variant.painted(ChromePainter.OPEN_STRIPE_LABEL), 24)).as(where + " label " + i + " ink").isTrue();
        }
        // Across: the labels' line is centred across the fill (the user's direction of 2026-10-18), where the mocks set it
        // along the outer edge.
        var crop = MockCrops.crop(window, 0, 0, mock.getWidth(), mock.getHeight());
        var theirLine = MockCrops.runs(mock, false, 120, 40, stripeFill, tile);
        var myLine = MockCrops.runs(crop, false, 120, 40, myFill, myTile);
        assertThat(theirLine).as(where + ": the mock's label line").hasSize(1);
        assertThat(myLine).as(where + ": the label line").hasSize(1);
        // The line's ink is the capitals' and lower case's above the baseline, centred by the capitals, so its middle
        // stands within half a pixel (1 at 2x) of the fill's under IBM Plex; doubled to stay whole. Its extent across, the
        // face's cap and ascender height, is the mock's within 1 px.
        MockProfile.fonts(where + ": the label line's middle, doubled", myLine.getFirst().start() + myLine.getFirst().end(),
            mock.getWidth() - 1, 2);
        MockProfile.fonts(where + ": the label line's extent", myLine.getFirst().length(), theirLine.getFirst().length(), 2);
        // The glyphs: the agreeing share over the fill against the mock moved across to the centred line, so the two lines
        // compare letter for letter.
        MockCrops.fontsFloor(where + ": agreeing pixels", MockCrops.agreement(crop,
            across(mock, myLine.getFirst().start() - theirLine.getFirst().start()), 24), STRIPE_FLOOR);
    }

    /** {@code image} moved {@code shift} columns across, the vacated columns repeating its edge column (a label-free fill). */
    static BufferedImage across(BufferedImage image, int shift) {
        var moved = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) moved.setRGB(x, y, image.getRGB(Math.clamp(x - shift, 0, image.getWidth() - 1), y));
        return moved;
    }

    /**
     * The bottom strip's three closed labels, "Event Log", "Transfers" and "Problems" with a count pill (a fixture: the
     * mock's amber pill is the deferred Problems panel; this one is the error tone), against the mocks' bottom strip: the
     * strip is the mocks' 24 and its rule (Rulings 4 as ruled on 2026-10-17) and its labels start at the side stripes'
     * fill, the mock's 22 now that the stripes fit their labels (any extra thickness of theirs would shift the render's
     * ink right of the mock's, and the comparison with it). Asserted: the surface, three dividers in the mock's colour and place, the
     * labels' edges, the pill's start and width, and the share of agreeing pixels under IBM Plex
     * ({@link MockProfile#fonts}, {@link MockCrops#fontsFloor}), on the render with its two icon slices cut out (Event Log's
     * and Transfers' icons, asserted as the icons). The cut render is 330 wide, past the mock's 270.
     */
    @Test void theBottomStripMatchesTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var strip = new BottomStrip(EnterpriseLook.kit(), ChromeArtwork.CLASSIC);
                var problems = new ToolStripe.Button("problems", "Problems", null, Slot.BOTTOM_LEFT, false,
                    java.util.Optional.of(new dev.jasper.app.contributions.PanelBadge(1, dev.jasper.app.contributions.PanelBadge.Tone.ERROR, "1 problem")),
                    java.util.OptionalInt.empty());
                strip.render(List.of(new ToolStripe.Button("log", "Event Log", AppIcons.semantic("bell"), Slot.BOTTOM_LEFT, false),
                    new ToolStripe.Button("transfers", "Transfers", new PresentationIcon("download", null), Slot.BOTTOM_LEFT, false), problems), List.of());
                int thickness = strip.getPreferredSize().height;
                int shift = 2 * (new LabelledStripe(Side.LEFT, EnterpriseLook.kit(), ChromeArtwork.CLASSIC).getPreferredSize().width - 23);
                int slices = 2 * StripeIconsTest.slice();
                var iconed = variant.seen(MockCrops.render(strip, 330 + slices, thickness, 2));
                // The whole strip, its 2 px rule at 2x and its fill, against the mock's (rule at 1706, fill from 1708): as it
                // ships, beside the mock widened by the two slices, and with the slices cut out.
                MockCrops.write(variant.name() + "-bottom-strip", MockCrops.crop(iconed, shift, 0, 520 + 2 * slices, 50),
                    variant.crop(2, 1706, 520 + 2 * slices, 50));
                var render = StripeIconsTest.withoutIcons(iconed, strip, 2);
                MockCrops.write(variant.name() + "-bottom-strip-text", MockCrops.crop(render, shift, 0, 520, 50), variant.crop(2, 1706, 520, 50));
                assertThat(thickness).as(where(variant) + ": the mocks' 24 and the rule").isEqualTo(25);
                assertIcons(variant, "bottom strip", iconed, strip, List.of("bell", "download"));
                var mock = variant.crop(2, 1708, 520, 48);
                // The render's fill (below its 2 px rule at 2x), 48 rows as the mock crop is the mock's fill.
                var rows = MockCrops.crop(render, shift, 2, 600, 48);
                String where = variant.name() + " bottom strip";
                Color surface = MockCrops.at(mock, 10, 4), divider = MockCrops.at(mock, 188, 24);
                Color mySurface = variant.role("surface.stripe"), myDivider = variant.role("border.divider"), rule = variant.painted(ChromePainter.DOCK_RULE);
                assertThat(MockCrops.near(MockCrops.at(rows, 10, 4), mySurface, variant.fillTolerance())).as(where + ": its surface").isTrue();
                for (int y = 0; y < 2; y++)
                    assertThat(MockCrops.near(MockCrops.at(render, shift + 10, y), rule, variant.fillTolerance())).as(where + ": its top rule, row " + y).isTrue();
                // The dividers, found as runs in the fill's top 6 rows (2x), where no text or pill has ink (adjacent
                // columns join: gap 1): the mock's are 2 px wide at 188, 332 and 518 of this crop, the render's border.divider.
                var theirDividers = MockCrops.runs(variant.crop(2, 1708, 525, 6), false, 20, 1, surface);
                var myDividers = MockCrops.runs(MockCrops.crop(rows, 0, 0, 600, 6), false, 20, 1, mySurface);
                assertThat(theirDividers).as(where + ": the mock's dividers").hasSize(3);
                assertThat(myDividers).as(where + ": a divider after each label").hasSize(3);
                for (int i = 0; i < 3; i++) {
                    MockProfile.fonts(where + " divider " + i, myDividers.get(i).start(), theirDividers.get(i).start(), 2);
                    assertThat(myDividers.get(i).length()).as(where + ": divider " + i + " is 1 px").isEqualTo(theirDividers.get(i).length());
                    assertThat(MockCrops.near(myDividers.get(i).ink(), myDivider, 8)).as(where + ": divider " + i + "'s colour").isTrue();
                }
                // The pill: the columns holding its painted ground (the error tone's danger.ground, under the variant's
                // scrim), its round ends' antialiased columns aside; the mock's amber pill is found against the surface.
                var ground = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
                ground.setRGB(0, 0, dev.jasper.ui.theme.DerivedColor.DANGER_GROUND.color(EnterpriseLook.theme(variant.dark())).getRGB());
                int painted = variant.seen(ground).getRGB(0, 0), pillStart = -1, pillEnd = -1;
                for (int x = 0; x < rows.getWidth(); x++)
                    for (int y = 0; y < rows.getHeight(); y++)
                        if (rows.getRGB(x, y) == painted) { if (pillStart < 0) pillStart = x; pillEnd = x; break; }
                assertThat(pillStart).as(where + ": the pill is painted").isNotNegative();
                assertThat(pillEnd - pillStart + 1).as(where + ": the pill is 16 wide").isBetween(28, 34);
                var theirPill = MockCrops.runs(MockCrops.crop(mock, 462, 0, 40, 48), false, 30, 2, surface);
                assertThat(theirPill).as(where + ": the mock's pill").hasSize(1);
                MockProfile.fonts(where + " pill start", pillStart, 462 + theirPill.getFirst().start(), 3);
                var theirs = MockCrops.runs(MockCrops.crop(mock, 0, 0, 465, 48), false, 120, 20, surface, divider);
                var mine = MockCrops.runs(MockCrops.crop(rows, 0, 0, pillStart - 2, 48), false, 120, 20, mySurface, myDivider);
                assertThat(theirs).as(where + ": the mock's labels").hasSize(3);
                assertThat(mine).as(where + ": one ink run per label").hasSize(3);
                assertThat(mine.getFirst().start()).as(where + " label 0 starts")
                    .isBetween(theirs.getFirst().start() - 2, theirs.getFirst().start() + 2);
                for (int i = 0; i < 3; i++) {
                    if (i > 0) MockProfile.fonts(where + " label " + i + " start", mine.get(i).start(), theirs.get(i).start(), 2);
                    MockProfile.fonts(where + " label " + i + " end", mine.get(i).end(), theirs.get(i).end(), 2);
                }
                MockCrops.fontsFloor(where + ": agreeing pixels",
                    MockCrops.agreement(MockCrops.crop(rows, 0, 0, 460, 48), MockCrops.crop(mock, 0, 0, 460, 48), 24), STRIPE_FLOOR);
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    /** The 1 px rules between flush frames against the mocks' rules at 2x: the dock rule's colour and the two-pixel width. */
    @Test void theRulesBetweenFramesMatchTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var area = new ToolWindowArea(new javax.swing.JPanel(), new dev.jasper.app.dock.DockState(), EnterpriseLook.kit(), true);
                DockTestSupport.show(area, Slot.LEFT_TOP, new javax.swing.JLabel("SFTP"), 300);
                DockTestSupport.show(area, Slot.LEFT_BOTTOM, new javax.swing.JLabel("File Servers"), 300);
                var render = variant.seen(MockCrops.render(area, 1000, 763, 2));
                var mock = variant.crop(0, 0, 2884, 1804);
                String where = variant.name() + " rules";
                // The mock's left rule is at x 648-649 and its rule between the left frames at y 942-943 (2x).
                Color theirs = MockCrops.at(mock, 648, 1000);
                assertThat(MockCrops.near(MockCrops.at(mock, 649, 1000), theirs, 0)).isTrue();
                assertThat(MockCrops.near(MockCrops.at(mock, 500, 942), theirs, 3)).isTrue();
                java.awt.Rectangle top = area.content(Slot.LEFT_TOP).getParent().getBounds();
                int x = 2 * (top.x + top.width), y = 2 * (top.y + top.height);
                Color rule = variant.painted(ChromePainter.DOCK_RULE);
                assertThat(MockCrops.near(MockCrops.at(render, x, 600), rule, variant.fillTolerance())).as(where + ": the colour").isTrue();
                assertThat(MockCrops.near(MockCrops.at(render, x + 1, 600), rule, variant.fillTolerance())).as(where + ": two pixels at 2x").isTrue();
                assertThat(MockCrops.near(MockCrops.at(render, x + 2, 600), rule, variant.fillTolerance())).as(where + ": not three").isFalse();
                assertThat(MockCrops.near(MockCrops.at(render, 200, y), rule, variant.fillTolerance())).as(where + ": between the left frames").isTrue();
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    /** The four native header strips (flush frames) against the mocks', the focused one active in its own render. */
    @Test void nativeHeaderStripsMatchTheMocks() throws Exception {
        for (var workstation : WORKSTATIONS) compareHeaders(workstation.getKey(), workstation.getValue(), false);
    }

    /** One fixture per focused frame: SFTP's render for SFTP, File Servers and Tunnels; Sessions' for Sessions. */
    static void compareHeaders(Variant variant, boolean connected, boolean kitHeaders) throws Exception {
        try {
            EnterpriseLook.install(variant.dark());
            String kind = kitHeaders ? "kit" : "native";
            try (var fixture = new DockFixture(EnterpriseLook.kit(), EnterpriseLook.theme(variant.dark()), connected, kitHeaders)) {
                fixture.render(1);
                fixture.focus("sftp");
                var render = variant.seen(fixture.render(2));
                // The fixture's panels: SFTP's dot and detail and the others' counts, which the Disconnected mock drops but Sessions'.
                var counted = connected ? List.of(HeaderText.TITLE, HeaderText.COUNT) : List.of(HeaderText.TITLE);
                compareHeader(variant, kind + "-sftp-header", render, fixture.header("sftp"), fixture.nativeCount("sftp"), 48, 180, 600, true,
                    connected ? List.of(HeaderText.TITLE, HeaderText.DOT, HeaderText.DETAIL) : List.of(HeaderText.TITLE), actions(connected, "sftp"));
                compareHeader(variant, kind + "-servers-header", render, fixture.header("servers"), fixture.nativeCount("servers"), 48, 944, 600, false, counted,
                    actions(connected, "servers"));
                compareHeader(variant, kind + "-tunnels-header", render, fixture.header("tunnels"), fixture.nativeCount("tunnels"), 2136, 1014, 700, false, counted,
                    actions(connected, "tunnels"));
                fixture.focus("sessions");
                render = variant.seen(fixture.render(2));
                compareHeader(variant, kind + "-sessions-header", render, fixture.header("sessions"), fixture.nativeCount("sessions"), 2136, 180, 700, true,
                    List.of(HeaderText.TITLE, HeaderText.COUNT), actions(connected, "sessions"));
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** The fixture panel {@code id}'s own header actions, in their order before Options and Hide. */
    static List<String> actions(boolean connected, String id) {
        return DockFixture.panels(connected).stream().filter(panel -> panel.id().equals(id)).findFirst().orElseThrow().actions();
    }

    /**
     * The ink of IntelliJ's classic {@code name} art on a header strip, as the variant shows it: the art is authored (the
     * classic {@code add} is #6E6E6E in Light), so it is painted in its 10 px glyph box at 2x on the strip's surface and
     * its strongest pixel read as a run's is.
     */
    static Color classicInk(Variant variant, String name, boolean focused) {
        var theme = EnterpriseLook.theme(variant.dark());
        Color strip = new ChromePainter(EnterpriseLook.kit()).headerSurface(theme, focused);
        try (var icons = new IconRegistry()) {
            var icon = icons.classicChromeIcon(name, 20).orElseThrow().icon(theme, SemanticColor.TEXT_PRIMARY);
            var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
            var g = image.createGraphics();
            try { g.setColor(strip); g.fillRect(0, 0, 20, 20); icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
            var runs = MockCrops.runs(image, false, 60, 10, strip);
            assertThat(runs).as("the classic %s art has one run", name).hasSize(1);
            return variant.seen(runs.get(0).ink());
        }
    }

    /** A header strip's text run: its title, connected dot, word detail or count. */
    enum HeaderText { TITLE, DOT, DETAIL, COUNT }

    /** {@code text}'s ink on a header strip, focused or not, as the variant paints it on the strip's own surface. */
    static Color headerInk(Variant variant, HeaderText text, boolean focused) {
        var theme = EnterpriseLook.theme(variant.dark());
        var painter = new ChromePainter(EnterpriseLook.kit());
        return variant.seen(switch (text) {
            case TITLE -> painter.headerTitle(theme, focused);
            case DOT -> SurfaceContrast.painted(theme, focused ? ChromePainter.FOCUSED_HEADER_DOT : ChromePainter.HEADER_DOT,
                painter.headerSurface(theme, focused));
            case DETAIL -> painter.headerDetail(theme, focused, false);
            case COUNT -> painter.headerDetail(theme, focused, true);
        });
    }

    /**
     * A header strip against its mock crop (2x, its 24 px strip and its rule): the surface and the rule; the ink runs
     * along it, one per title, dot, detail and icon, the title's start within 2 px, each icon's centre within 2 (the icons
     * stand from the trailing edge, whatever the face), each icon's extent within {@link #GLYPH_EXTENT} and its ink within
     * {@link Variant#glyphInkTolerance} (one glyph box for every action, and the strip's action ink but for a classic name
     * among {@code actions}, which has its classic art's ink, {@link #classicInk}), and each text run's ink, {@code texts}
     * in order; the other text edges (a text run's end within 3, a later run's start within 2) and the agreeing pixels of
     * the text, left of the icons, follow the title's advance width and glyphs and are asserted under IBM Plex.
     * Native count widths match an independent platform label render.
     */
    static void compareHeader(Variant variant, String name, BufferedImage render, Rectangle header, String nativeCount, int mockX, int mockY,
                              int mockWidth, boolean focused, List<HeaderText> texts, List<String> actions) {
        String where = variant.name() + " " + name;
        assertThat(2 * header.width).as(where + ": the frame's width").isEqualTo(mockWidth);
        assertThat(2 * header.height).as(where + ": 24 and the rule").isEqualTo(50);
        var mock = variant.crop(mockX, mockY, mockWidth, 50);
        var mine = MockCrops.crop(render, 2 * header.x, 2 * header.y, mockWidth, 50);
        MockCrops.write(variant.name() + "-" + name, mine, mock);
        var painter = new ChromePainter(EnterpriseLook.kit());
        var theme = EnterpriseLook.theme(variant.dark());
        Color surface = MockCrops.at(mock, 4, 4), strip = variant.seen(painter.headerSurface(theme, focused)), rule = variant.painted(ChromePainter.DOCK_RULE);
        assertThat(MockCrops.near(MockCrops.at(mine, 4, 4), strip, variant.fillTolerance())).as(where + ": the strip").isTrue();
        assertThat(MockCrops.near(MockCrops.at(mine, 4, 48), rule, variant.fillTolerance())).as(where + ": the rule").isTrue();
        assertThat(MockCrops.near(MockCrops.at(mine, 4, 49), rule, variant.fillTolerance())).as(where + ": the rule, 1 px").isTrue();
        var theirs = MockCrops.runs(MockCrops.crop(mock, 0, 0, mockWidth, 48), false, 60, 10, surface);
        var ours = MockCrops.runs(MockCrops.crop(mine, 0, 0, mockWidth, 48), false, 60, 10, strip);
        assertThat(ours).as(where + ": one run per title, dot, detail and icon").hasSameSizeAs(theirs);
        int firstIcon = mockWidth, icons = 0;
        for (int i = 0; i < theirs.size(); i++) {
            var mockRun = theirs.get(i);
            var run = ours.get(i);
            boolean icon = mockRun.start() > mockWidth * 0.6;
            if (icon) {
                firstIcon = Math.min(firstIcon, mockRun.start());
                assertThat((run.start() + run.end()) / 2.0).as(where + " icon " + i + " centre")
                    .isBetween((mockRun.start() + mockRun.end()) / 2.0 - 2, (mockRun.start() + mockRun.end()) / 2.0 + 2);
                assertThat(run.length()).as(where + " icon " + i + " extent")
                    .isBetween(mockRun.length() - GLYPH_EXTENT, mockRun.length() + GLYPH_EXTENT);
                // The user's 2026-10-19 direction: a classic name (the panels' add) draws IntelliJ's classic art, authored,
                // where the mocks set a glyph in the strip's ink; the other actions keep the strip's ink.
                String named = icons < actions.size() ? actions.get(icons) : "";
                icons++;
                Color action = IconRegistry.classicChromeNames().contains(named) ? classicInk(variant, named, focused)
                    : variant.seen(painter.headerAction(theme, focused));
                assertThat(MockCrops.near(run.ink(), action, variant.glyphInkTolerance()))
                    .as(where + " icon " + i + " ink " + run.ink() + " against " + action).isTrue();
            } else {
                if (i == 0) assertThat(run.start()).as(where + " title starts").isBetween(mockRun.start() - 2, mockRun.start() + 2);
                else MockProfile.fonts(where + " run " + i + " start", run.start(), mockRun.start(), 2);
                if (nativeCount != null && i < texts.size() && texts.get(i) == HeaderText.COUNT) {
                    // Native JLabel uses integer glyph advances: the same Plex face can make "(2)" narrower on
                    // Windows than in the browser mock. Keep the mock's start, and require the exact ink width of
                    // the specified text/COUNT role rendered independently by this platform's label rasterizer.
                    assertThat(run.length()).as(where + " native count width")
                        .isEqualTo(nativeCountWidth(nativeCount, strip, run.ink()));
                } else MockProfile.fonts(where + " run " + i + " end", run.end(), mockRun.end(), 3);
                assertThat(i).as(where + ": a text run the fixture shows").isLessThan(texts.size());
                assertThat(MockCrops.near(run.ink(), headerInk(variant, texts.get(i), focused), 24)).as(where + " run " + i + " ink, " + texts.get(i)).isTrue();
            }
        }
        int text = Math.max(8, firstIcon - 8);
        MockCrops.fontsFloor(where + ": agreeing pixels",
            MockCrops.agreement(MockCrops.crop(mine, 0, 0, text, 50), MockCrops.crop(mock, 0, 0, text, 50), 24), HEADER_FLOOR);
    }

    private static int nativeCountWidth(String text, Color surface, Color ink) {
        var label = new javax.swing.JLabel(text);
        label.setFont(dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.COUNT));
        label.setForeground(ink);
        label.setBackground(surface);
        label.setOpaque(true);
        var runs = MockCrops.runs(MockCrops.render(label, 100, 24, 2), false, 60, 10, surface);
        assertThat(runs).as("one independently rendered count").hasSize(1);
        return runs.getFirst().length();
    }

    /** The four borrowed kit header strips against the mocks' (every mock header is a plugin panel's). */
    @Test void kitHeaderStripsMatchTheMocks() throws Exception {
        for (var workstation : WORKSTATIONS) compareHeaders(workstation.getKey(), workstation.getValue(), true);
    }

    /**
     * The whole dock against the mocks' arrangement (2x): the mock's dock begins at (2, 180), below the toolbar row's rule,
     * and the fixture's at (0, 0); the side stripes are the mocks' 23, so the frames lie in the mocks' places, and the
     * bottom strip starts where the mock's rule above it is.
     */
    @Test void theWholeDockIsArrangedAsTheMocks() throws Exception {
        for (var workstation : WORKSTATIONS) {
            Variant variant = workstation.getKey();
            try {
                EnterpriseLook.install(variant.dark());
                try (var fixture = new DockFixture(EnterpriseLook.kit(), EnterpriseLook.theme(variant.dark()), workstation.getValue(), true)) {
                    fixture.render(1);
                    fixture.focus("sftp");
                    var render = variant.seen(fixture.render(2));
                    MockCrops.write(variant.name() + "-dock", render, variant.crop(2, 180, 2880, 1578));
                    int left = 2 * (fixture.stripes.left().getWidth() - 23), right = -2 * (fixture.stripes.right().getWidth() - 23);
                    String where = variant.name() + " dock";
                    MockProfile.fonts(where + ": the stripes are the mocks' 23, the dock's offset", left, 0, 0);
                    assertPlaced(where + " SFTP header", fixture.header("sftp"), 48 - 2 + left, 0, 600);
                    assertPlaced(where + " File Servers header", fixture.header("servers"), 48 - 2 + left, 944 - 180, 600);
                    assertPlaced(where + " Sessions header", fixture.header("sessions"), 2136 - 2 + right, 0, 700);
                    assertPlaced(where + " Tunnels header", fixture.header("tunnels"), 2136 - 2 + right, 1014 - 180, 700);
                    var strip = fixture.stripes.bottomStrip();
                    assertThat(2 * strip.getY()).as(where + ": the bottom strip's rule").isEqualTo(1706 - 180);
                    assertThat(strip.getWidth()).as(where + ": the bottom strip spans the window").isEqualTo(DockFixture.WIDTH);
                    // File Servers and Tunnels, the second groups, stand at their stripes' bottom ends (the user's direction of
                    // 2026-10-18), where the mocks draw them under the top groups.
                    for (DockStripe side : List.of(fixture.stripes.left(), fixture.stripes.right()))
                        assertSecondAtTheEnd(where + " " + side.side(), (LabelledStripe) side, side.getHeight());
                    var rule = variant.painted(ChromePainter.DOCK_RULE);
                    assertThat(MockCrops.near(MockCrops.at(render, 648 - 2 + left, 1000 - 180), rule, variant.fillTolerance())).as(where + ": the left rule").isTrue();
                    assertThat(MockCrops.near(MockCrops.at(render, 2134 - 2 + right, 1000 - 180), rule, variant.fillTolerance())).as(where + ": the right rule").isTrue();
                    assertThat(MockCrops.near(MockCrops.at(render, 500 - 2 + left, 942 - 180), rule, variant.fillTolerance())).as(where + ": the rule between the left frames").isTrue();
                }
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    private static String where(Variant variant) { return variant.name() + " bottom strip"; }

    /** A header strip's place in the fixture at 2x against the mock's, and its width. */
    private static void assertPlaced(String where, Rectangle header, int x, int y, int width) {
        assertThat(2 * header.x).as(where + " x").isEqualTo(x);
        assertThat(2 * header.y).as(where + " y").isEqualTo(y);
        assertThat(2 * header.width).as(where + " width").isEqualTo(width);
    }
}
