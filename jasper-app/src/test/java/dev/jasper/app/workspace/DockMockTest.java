package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.app.testsupport.MockProfile;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 16.1.4 for the dock chrome: each surface plan 7 builds, rendered at 2x under the shipped Enterprise kit, IBM Plex and
 * theme ({@link EnterpriseLook}), against its crop of the Workstation (Light), Dark Mode and Unlock Vault mocks. Probes that
 * follow the UI face's glyphs are asserted under IBM Plex and printed only where an installed IBM Plex hides the plugin's
 * files ({@link MockProfile#fonts}, {@link MockCrops#fontsFloor}). Renders, crops and their differences are written under
 * {@code jasper-app/build/dock-renders/}. Mock data (titles, numbers, counts) are fixtures.
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

        /** The open tile is Enterprise Light's #D7D9DC against the mock's #CFD4DC, 8 apart (Rulings 6); Dark's is exact. */
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
    static final double HEADER_FLOOR = 0.94;
    /** Agreement floors (spec 16.1.4): the share of pixels within 24 per channel of the mock. */
    static final double STRIPE_FLOOR = 0.92;

    @Test void theLeftStripeMatchesTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit());
                stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.LEFT_TOP, 1),
                    LabelledStripeTest.window("servers", "File Servers", Slot.LEFT_BOTTOM, 2)), List.of());
                int thickness = stripe.getPreferredSize().width;
                MockProfile.fonts(variant.name() + " left stripe: the stripe fits its labels, the mocks' 22 and the rule", thickness, 23, 0);
                int height = fitted("1: SFTP", "2: File Servers");
                var render = variant.seen(MockCrops.render(stripe, thickness, height, 2));
                assertSecondAtTheEnd(variant.name() + " left stripe", stripe, height);
                compareStripe(variant, "left-stripe", render, variant.crop(2, 180, 44, 2 * height), thickness, Side.LEFT, List.of(29, 109, 151, 301));
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    @Test void theRightStripeMatchesTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var stripe = new LabelledStripe(Side.RIGHT, EnterpriseLook.kit());
                stripe.render(List.of(LabelledStripeTest.window("sessions", "Sessions", Slot.RIGHT_TOP, 0),
                    LabelledStripeTest.window("tunnels", "Tunnels", Slot.RIGHT_BOTTOM, 0)), List.of());
                int thickness = stripe.getPreferredSize().width;
                int height = fitted("Sessions", "Tunnels");
                var render = variant.seen(MockCrops.render(stripe, thickness, height, 2));
                assertSecondAtTheEnd(variant.name() + " right stripe", stripe, height);
                compareStripe(variant, "right-stripe", render, variant.crop(2838, 180, 44, 2 * height), thickness, Side.RIGHT, List.of(29, 121, 164, 247));
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
        var labels = new StripeLabels(EnterpriseLook.kit());
        return 2 * rail() + labels.length(top, java.util.Optional.empty()) + labels.length(second, java.util.Optional.empty());
    }

    private static int rail() {
        return com.formdev.flatlaf.util.UIScale.scale(EnterpriseLook.kit().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.RAIL_GAP));
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
        assertThat(MockCrops.near(MockCrops.at(window, 2, 4), stripeFill, variant.fillTolerance())).as(where + ": the stripe's surface").isTrue();
        assertThat(MockCrops.near(MockCrops.at(window, 2, 40), tile, variant.tileTolerance())).as(where + ": the open tile").isTrue();
        var theirs = MockCrops.runs(mock, true, 120, 20, stripeFill, tile);
        var mine = MockCrops.runs(window, true, 120, 20, stripeFill, tile);
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
            assertThat(MockCrops.near(mine.get(i).ink(), theirs.get(i).ink(), 24)).as(where + " label " + i + " ink").isTrue();
        }
        // Across: the labels' line is centred across the fill (the user's direction of 2026-10-18), where the mocks set it
        // along the outer edge.
        var crop = MockCrops.crop(window, 0, 0, mock.getWidth(), mock.getHeight());
        var theirLine = MockCrops.runs(mock, false, 120, 40, stripeFill, tile);
        var myLine = MockCrops.runs(crop, false, 120, 40, stripeFill, tile);
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
     * ({@link MockProfile#fonts}, {@link MockCrops#fontsFloor}). The render is 330 wide, past the mock's 270.
     */
    @Test void theBottomStripMatchesTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var strip = new BottomStrip(EnterpriseLook.kit());
                var problems = new ToolStripe.Button("problems", "Problems", null, Slot.BOTTOM_LEFT, false,
                    java.util.Optional.of(new dev.jasper.app.contributions.PanelBadge(1, dev.jasper.app.contributions.PanelBadge.Tone.ERROR, "1 problem")),
                    java.util.OptionalInt.empty());
                strip.render(List.of(new ToolStripe.Button("log", "Event Log", null, Slot.BOTTOM_LEFT, false),
                    new ToolStripe.Button("transfers", "Transfers", null, Slot.BOTTOM_LEFT, false), problems), List.of());
                int thickness = strip.getPreferredSize().height;
                assertThat(thickness).as(where(variant) + ": the mocks' 24 and the rule").isEqualTo(25);
                int shift = 2 * (new LabelledStripe(Side.LEFT, EnterpriseLook.kit()).getPreferredSize().width - 23);
                var render = variant.seen(MockCrops.render(strip, 330, thickness, 2));
                // The whole strip, its 2 px rule at 2x and its fill, against the mock's (rule at 1706, fill from 1708).
                MockCrops.write(variant.name() + "-bottom-strip", MockCrops.crop(render, shift, 0, 520, 50), variant.crop(2, 1706, 520, 50));
                var mock = variant.crop(2, 1708, 520, 48);
                // The render's fill (below its 2 px rule at 2x), 48 rows as the mock crop is the mock's fill.
                var rows = MockCrops.crop(render, shift, 2, 600, 48);
                String where = variant.name() + " bottom strip";
                Color surface = MockCrops.at(mock, 10, 4), divider = MockCrops.at(mock, 188, 24), rule = MockCrops.at(variant.crop(2, 1706, 20, 2), 10, 0);
                assertThat(MockCrops.near(MockCrops.at(rows, 10, 4), surface, variant.fillTolerance())).as(where + ": its surface").isTrue();
                for (int y = 0; y < 2; y++)
                    assertThat(MockCrops.near(MockCrops.at(render, shift + 10, y), rule, variant.fillTolerance())).as(where + ": its top rule, row " + y).isTrue();
                // The dividers, found as runs in the fill's top 6 rows (2x), where no text or pill has ink (adjacent
                // columns join: gap 1): the mock's are 2 px wide at 188, 332 and 518 of this crop. border.divider is the
                // shipped theme's #D3D6DA and #4A4D53 (6b's override), the mock's.
                var theirDividers = MockCrops.runs(variant.crop(2, 1708, 525, 6), false, 20, 1, surface);
                var myDividers = MockCrops.runs(MockCrops.crop(rows, 0, 0, 600, 6), false, 20, 1, surface);
                assertThat(theirDividers).as(where + ": the mock's dividers").hasSize(3);
                assertThat(myDividers).as(where + ": a divider after each label").hasSize(3);
                for (int i = 0; i < 3; i++) {
                    MockProfile.fonts(where + " divider " + i, myDividers.get(i).start(), theirDividers.get(i).start(), 2);
                    assertThat(myDividers.get(i).length()).as(where + ": divider " + i + " is 1 px").isEqualTo(theirDividers.get(i).length());
                    assertThat(MockCrops.near(myDividers.get(i).ink(), divider, 8)).as(where + ": divider " + i + "'s colour").isTrue();
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
                var mine = MockCrops.runs(MockCrops.crop(rows, 0, 0, pillStart - 2, 48), false, 120, 20, surface, divider);
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

    /** The 1 px rules between flush frames against the mocks' rules at 2x: the colour and the two-pixel width. */
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
                assertThat(MockCrops.near(MockCrops.at(render, x, 600), theirs, variant.fillTolerance())).as(where + ": the colour").isTrue();
                assertThat(MockCrops.near(MockCrops.at(render, x + 1, 600), theirs, variant.fillTolerance())).as(where + ": two pixels at 2x").isTrue();
                assertThat(MockCrops.near(MockCrops.at(render, x + 2, 600), theirs, variant.fillTolerance())).as(where + ": not three").isFalse();
                assertThat(MockCrops.near(MockCrops.at(render, 200, y), theirs, variant.fillTolerance())).as(where + ": between the left frames").isTrue();
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
                compareHeader(variant, kind + "-sftp-header", render, fixture.header("sftp"), 48, 180, 600);
                compareHeader(variant, kind + "-servers-header", render, fixture.header("servers"), 48, 944, 600);
                compareHeader(variant, kind + "-tunnels-header", render, fixture.header("tunnels"), 2136, 1014, 700);
                fixture.focus("sessions");
                render = variant.seen(fixture.render(2));
                compareHeader(variant, kind + "-sessions-header", render, fixture.header("sessions"), 2136, 180, 700);
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * A header strip against its mock crop (2x, its 24 px strip and its rule): the surface and the rule; the ink runs
     * along it, one per title, dot, detail and icon, the title's start within 2 px, each icon's centre within 2 (the icons
     * stand from the trailing edge, whatever the face), each icon's extent within {@link #GLYPH_EXTENT} and its ink within
     * {@link Variant#glyphInkTolerance} (one glyph box and one ink for every action), and each text run's ink; the other
     * text edges (a text run's end within 3, a later run's start within 2) and the agreeing pixels of the text, left of the
     * icons, follow the title's advance width and glyphs and are asserted under IBM Plex.
     */
    static void compareHeader(Variant variant, String name, BufferedImage render, Rectangle header, int mockX, int mockY, int mockWidth) {
        String where = variant.name() + " " + name;
        assertThat(2 * header.width).as(where + ": the frame's width").isEqualTo(mockWidth);
        assertThat(2 * header.height).as(where + ": 24 and the rule").isEqualTo(50);
        var mock = variant.crop(mockX, mockY, mockWidth, 50);
        var mine = MockCrops.crop(render, 2 * header.x, 2 * header.y, mockWidth, 50);
        MockCrops.write(variant.name() + "-" + name, mine, mock);
        Color surface = MockCrops.at(mock, 4, 4), rule = MockCrops.at(mock, 4, 49);
        assertThat(MockCrops.near(MockCrops.at(mine, 4, 4), surface, variant.fillTolerance())).as(where + ": the strip").isTrue();
        assertThat(MockCrops.near(MockCrops.at(mine, 4, 48), rule, variant.fillTolerance())).as(where + ": the rule").isTrue();
        assertThat(MockCrops.near(MockCrops.at(mine, 4, 49), rule, variant.fillTolerance())).as(where + ": the rule, 1 px").isTrue();
        var theirs = MockCrops.runs(MockCrops.crop(mock, 0, 0, mockWidth, 48), false, 60, 10, surface);
        var ours = MockCrops.runs(MockCrops.crop(mine, 0, 0, mockWidth, 48), false, 60, 10, surface);
        assertThat(ours).as(where + ": one run per title, dot, detail and icon").hasSameSizeAs(theirs);
        int firstIcon = mockWidth;
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
                assertThat(MockCrops.near(run.ink(), mockRun.ink(), variant.glyphInkTolerance()))
                    .as(where + " icon " + i + " ink " + run.ink() + " against " + mockRun.ink()).isTrue();
            } else {
                if (i == 0) assertThat(run.start()).as(where + " title starts").isBetween(mockRun.start() - 2, mockRun.start() + 2);
                else MockProfile.fonts(where + " run " + i + " start", run.start(), mockRun.start(), 2);
                MockProfile.fonts(where + " run " + i + " end", run.end(), mockRun.end(), 3);
                assertThat(MockCrops.near(run.ink(), mockRun.ink(), 24)).as(where + " run " + i + " ink").isTrue();
            }
        }
        int text = Math.max(8, firstIcon - 8);
        MockCrops.fontsFloor(where + ": agreeing pixels",
            MockCrops.agreement(MockCrops.crop(mine, 0, 0, text, 50), MockCrops.crop(mock, 0, 0, text, 50), 24), HEADER_FLOOR);
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
                    var rule = MockCrops.at(variant.crop(648, 1000, 1, 1), 0, 0);
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
