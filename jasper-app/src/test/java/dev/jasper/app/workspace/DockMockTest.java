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
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 16.1.4 for the dock chrome: each surface plan 7 builds, rendered at 2x under the Enterprise kit and theme (plan
 * 6a's {@link EnterpriseLook} fixtures until plan 5b ships the plugin, Plex and the theme), against its crop of the
 * Workstation (Light), Dark Mode and Unlock Vault mocks. Probes that follow the UI face's glyphs are printed until then
 * ({@link MockProfile#fonts}). Renders, crops and their differences are written under
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

        /** The open tile is Enterprise Light's #D3D9E1 (6a's fixture theme) against the mock's #CFD4DC (Rulings 6); Dark's is exact. */
        int tileTolerance() { return dark ? 3 : 10; }

        /**
         * How near a fill or rule must be: 3, or 5 under the Unlock Vault mock's scrim, which has a cool cast (white reads
         * #C9CACC, 2 to 3 bluer than black at 21 % gives; Execution notes R6).
         */
        int fillTolerance() { return scrim > 0 ? 5 : 3; }
    }

    static final Variant LIGHT = new Variant("light", MockCrops.CONNECTED, false, 0, 0);
    static final Variant DARK = new Variant("dark", MockCrops.DARK, true, 0, 0);
    static final Variant VAULT = new Variant("vault", MockCrops.VAULT, false, MockCrops.VAULT_OFFSET, MockCrops.VAULT_SCRIM);
    static final List<Variant> VARIANTS = List.of(LIGHT, DARK, VAULT);
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
                assertThat(thickness).as("the 28 px floor of window.rail_width").isEqualTo(28);
                compareStripe(variant, "left-stripe", variant.seen(MockCrops.render(stripe, thickness, 165, 2)),
                    variant.crop(2, 180, 44, 330), thickness, Side.LEFT, List.of(29, 109, 151, 301));
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
                compareStripe(variant, "right-stripe", variant.seen(MockCrops.render(stripe, thickness, 135, 2)),
                    variant.crop(2838, 180, 44, 270), thickness, Side.RIGHT, List.of(29, 121, 164, 247));
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    /**
     * A side stripe against its mock crop (the mock's 22 px fill at 2x): the stripe and open-tile fills, one ink run per
     * label with the mock's ink, the first label's leading ink within 2 px, and (printed until plan 5b) the other run
     * edges and the share of agreeing pixels over the render's middle 44 columns of its fill, where the centred labels
     * line up with the mock's.
     */
    static void compareStripe(Variant variant, String name, BufferedImage render, BufferedImage mock, int thickness, Side side,
                              List<Integer> expectedRuns) {
        int rule = 2, fill = 2 * thickness - rule, fillStart = side == Side.LEFT ? 0 : rule;
        var window = MockCrops.crop(render, fillStart + (fill - mock.getWidth()) / 2, 0, mock.getWidth(), render.getHeight());
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
        // every later edge follows the labels' advance widths, and the agreeing share their glyphs: printed until plan
        // 5b's Plex (Execution notes R5), then asserted within 2 px and against STRIPE_FLOOR.
        assertThat(mine.getFirst().start()).as(where + " label 0 starts")
            .isBetween(theirs.getFirst().start() - 2, theirs.getFirst().start() + 2);
        for (int i = 0; i < theirs.size(); i++) {
            if (i > 0) MockProfile.fonts(where + " label " + i + " start", mine.get(i).start(), theirs.get(i).start(), 2);
            MockProfile.fonts(where + " label " + i + " end", mine.get(i).end(), theirs.get(i).end(), 2);
            assertThat(MockCrops.near(mine.get(i).ink(), theirs.get(i).ink(), 24)).as(where + " label " + i + " ink").isTrue();
        }
        MockCrops.fontsFloor(where + ": agreeing pixels",
            MockCrops.agreement(MockCrops.crop(window, 0, 0, mock.getWidth(), mock.getHeight()), mock, 24), STRIPE_FLOOR);
    }

    // Later tasks add their surfaces above this line.
}
