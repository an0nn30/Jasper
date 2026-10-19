package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.MockComparison;
import dev.jasper.ui.gallery.KitGallery;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Plan 8a Rulings 12: the Run window under the shipped Enterprise kit (its IBM Plex faces and 12-point body) and the
 * Enterprise theme against the two 06 Run Dialog mocks, at 2x. The mocks draw
 * the window's frame at x 60-983, y 854-1695; the render is the window alone. The part under the list is compared 12
 * device pixels lower (plan 8a Rulings 5). Images go to {@code jasper-app/build/run-window/}. Every comparison runs on
 * the EDT inside {@code KitGallery.installed}, while the context that drew the render is open (installed closes its
 * binding when its action returns). Plan 4b's classic checkbox and buttons, plan 5b's IBM Plex text and plan 8b's
 * footer buttons, each the mocks' 70 px, are held to the mock (plan 8a R9).
 */
public class RunWindowRenderTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    static final Path OUT = ROOT.resolve("jasper-app/build/run-window");
    static final Path MOCKS = ROOT.resolve("docs/design/enterprise/reference/06 Run Dialog");
    static final int MOCK_X = 60, MOCK_Y = 854, WIDTH = 924, MOCK_HEIGHT = 842, SHIFT = 12, BAND = 632;
    /** The scrollbar's column over the list, and the B mock's "1 of 11" (its fixture count), in window pixels. */
    static final Rectangle SCROLLBAR = new Rectangle(870, 228, 28, 406), COUNT = new Rectangle(800, 232, 98, 38);

    /** A probe: from (x, y) step (dx, dy) to the first pixel off {@code background}; {@code lower} probes under the list. */
    record Feature(String name, int x, int y, int dx, int dy, Function<KitContext, Color> background, boolean lower, int tolerance) { }

    static Color semantic(KitContext c, SemanticColor role) { return c.theme().semantic().color(role); }
    static Color derived(KitContext c, DerivedColor role) { return role.color(c.theme()); }
    static Color dialog(KitContext c) { return semantic(c, c.kit().colors().dialogSurface()); }

    static final List<Feature> FEATURES = List.of(
        new Feature("title band to its rule", 300, 10, 0, 1, c -> derived(c, DerivedColor.SURFACE_HEADER_ACTIVE), false, 1),
        new Feature("title text", 4, 26, 1, 0, c -> derived(c, DerivedColor.SURFACE_HEADER_ACTIVE), false, 3),
        new Feature("shortcut text end", 880, 26, -1, 0, c -> derived(c, DerivedColor.SURFACE_HEADER_ACTIVE), false, 3),
        new Feature("close glyph", 860, 26, 1, 0, c -> derived(c, DerivedColor.SURFACE_HEADER_ACTIVE), false, 3),
        new Feature("help tile top", 58, 54, 0, 1, RunWindowRenderTest::dialog, false, 1),
        new Feature("help tile left", 4, 108, 1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("help text", 92, 95, 1, 0, RunWindowRenderTest::dialog, false, 3),
        // The first line's ink top follows the face's ascent and RUN_WINDOW_HELP_TEXT_TOP: in IBM Plex 1 device px above the mock's.
        new Feature("help text top", 120, 60, 0, 1, RunWindowRenderTest::dialog, false, 3),
        new Feature("open label", 4, 190, 1, 0, RunWindowRenderTest::dialog, false, 3),
        new Feature("field box left", 92, 186, 1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("field box top", 500, 120, 0, 1, RunWindowRenderTest::dialog, false, 1),
        new Feature("field box right", 920, 186, -1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("menu arrow", 860, 186, 1, 0, c -> semantic(c, SemanticColor.SURFACE_INPUT), false, 3),
        new Feature("list box top", 500, 215, 0, 1, RunWindowRenderTest::dialog, false, 1),
        new Feature("list box left", 4, 300, 1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("list box right", 920, 300, -1, 0, RunWindowRenderTest::dialog, false, 1),
        new Feature("heading rule above the selected row", 500, 280, 0, -1, c -> semantic(c, SemanticColor.ACCENT), false, 1),
        new Feature("heading text", 30, 250, 1, 0, c -> derived(c, DerivedColor.SURFACE_TABLE_HEADER), false, 3),
        new Feature("selected row to the next", 500, 275, 0, 1, c -> semantic(c, SemanticColor.ACCENT), false, 1),
        new Feature("hue square left", 30, 290, 1, 0, c -> semantic(c, SemanticColor.ACCENT), false, 1),
        new Feature("hue square top", 47, 272, 0, 1, c -> semantic(c, SemanticColor.ACCENT), false, 1),
        new Feature("row title", 60, 290, 1, 0, c -> semantic(c, SemanticColor.ACCENT), false, 3),
        new Feature("detail label", 4, 671, 1, 0, RunWindowRenderTest::dialog, true, 3),
        new Feature("detail value", 120, 671, 1, 0, RunWindowRenderTest::dialog, true, 3),
        new Feature("checkbox left", 4, 721, 1, 0, RunWindowRenderTest::dialog, true, 1),
        new Feature("checkbox top", 36, 696, 0, 1, RunWindowRenderTest::dialog, true, 1),
        new Feature("checkbox label", 56, 721, 1, 0, RunWindowRenderTest::dialog, true, 3),
        new Feature("footer rule", 200, 740, 0, 1, RunWindowRenderTest::dialog, true, 1),
        // The buttons are right-packed, each RUN_WINDOW_BUTTON_WIDTH or its label plus RUN_WINDOW_BUTTON_PADDING if wider
        // (plan 8b, FOOTER): every label the mocks show fits, so each is the mocks' 70.
        new Feature("primary button left", 300, 796, 1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1),
        new Feature("cancel button left", 596, 796, 1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1),
        new Feature("browse button right", 920, 796, -1, 0, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1),
        new Feature("button top", 520, 765, 0, 1, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1),
        new Feature("window bottom", 200, 800, 0, 1, c -> derived(c, DerivedColor.SURFACE_FOOTER), true, 1),
        new Feature("window right", 900, 150, 1, 0, RunWindowRenderTest::dialog, false, 1));
    /** A only: the second heading (by the list's right edge, clear of row text and the mock's scrollbar) and the selected row's detail. */
    static final List<Feature> OPENED = List.of(
        new Feature("second heading", 893, 320, 0, 1, c -> semantic(c, SemanticColor.SURFACE_CARD), false, 1),
        new Feature("row detail", 118, 290, 1, 0, c -> semantic(c, SemanticColor.ACCENT), false, 3));
    /** B only: the query's first glyph. */
    static final List<Feature> TYPING = List.of(
        new Feature("query text", 125, 186, 1, 0, c -> semantic(c, SemanticColor.SURFACE_INPUT), false, 3));

    /** A fill sample: where, and the colour the render paints there in a state, from the drawing context's theme. */
    record Sample(String name, int x, int y, boolean lower, java.util.function.BiFunction<KitContext, String, Color> colour) { }
    static final List<Sample> SAMPLES = List.of(new Sample("frame", 200, 0, false, (c, state) -> derived(c, DerivedColor.BORDER_HOVER)),
        new Sample("title band", 200, 20, false, (c, state) -> derived(c, DerivedColor.SURFACE_HEADER_ACTIVE)),
        new Sample("body", 20, 400, false, (c, state) -> dialog(c)), new Sample("help tile", 35, 90, false, (c, state) -> semantic(c, SemanticColor.ACCENT)),
        new Sample("field", 500, 186, false, (c, state) -> semantic(c, SemanticColor.SURFACE_INPUT)),
        new Sample("list edge", 26, 300, false, (c, state) -> dev.jasper.ui.theme.SurfaceContrast.painted(c.theme(),
            dev.jasper.ui.swing.RunWindowPresentation.boxEdge(c.kit()))),
        new Sample("heading band", 300, 250, false, (c, state) -> derived(c, DerivedColor.SURFACE_TABLE_HEADER)),
        new Sample("selected row", 600, 290, false, (c, state) -> semantic(c, SemanticColor.ACCENT)),
        new Sample("row", 860, 330, false, (c, state) -> semantic(c, SemanticColor.SURFACE_CARD)),
        // The selected item's hue square, filled with its hue as authored: the snippet's green, then auth's purple.
        new Sample("hue square", 47, 290, false, (c, state) -> semantic(c, state.equals("typing-auth") ? SemanticColor.HUE_PURPLE : SemanticColor.HUE_GREEN)),
        new Sample("footer rule", 200, 758, true, (c, state) -> semantic(c, SemanticColor.BORDER_WINDOW)),
        new Sample("footer", 200, 820, true, (c, state) -> derived(c, DerivedColor.SURFACE_FOOTER)),
        // Inside the primary button's left end, above its label.
        new Sample("primary", 462, 782, true, (c, state) -> semantic(c, SemanticColor.ACCENT)));
    /**
     * A only: the checkbox's inside. B selects a snippet, whose scope has no SPLIT verb, so spec 11.2 disables the box the B
     * mock draws enabled (plan 8a R12): there the render's {@code surface.tableHeader} against the mock's white is a
     * measured residual, printed.
     */
    static final List<Sample> OPENED_SAMPLES = List.of(new Sample("checkbox inside", 38, 721, true, (c, state) -> semantic(c, SemanticColor.SURFACE_INPUT)));

    /** What runs with a render on the EDT, while the context that drew it is still open. */
    @FunctionalInterface interface Drawn { void accept(BufferedImage render, KitContext context) throws Exception; }

    /**
     * {@code state} rendered at 2x under the shipped kit in {@code dark} or Light, written to {@code build/}, and handed
     * with the context that drew it to {@code then} on the EDT before {@code KitGallery.installed} closes that context.
     */
    static BufferedImage render(boolean dark, String state, Drawn then) throws Exception {
        dev.jasper.app.testsupport.EnterpriseResources.assumePlexResolves();
        var kitValue = dev.jasper.app.testsupport.EnterpriseResources.kit();
        var theme = dev.jasper.app.testsupport.EnterpriseResources.theme().generated(dark);
        Path directory = Files.createDirectories(OUT.resolve(dark ? "dark" : "light"));
        var image = new BufferedImage[1];
        var failure = new Exception[1];
        try {
            SwingUtilities.invokeAndWait(() -> KitGallery.installed(theme, kitValue, (kit, focus) -> {
                try (var view = RunWindowFixtures.view(kit, new ArrayList<>())) {
                    if (state.equals("typing-auth")) { view.setQueryText("auth"); view.setEntries(RunWindowFixtures.typingAuth(), null, "Nothing found"); }
                    else view.setEntries(RunWindowFixtures.justOpened(), null, "Nothing found");
                    view.fieldBox().focusShown(true);
                    view.setSize(view.getPreferredSize());
                    for (int i = 0; i < 3; i++) dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(view);
                    var out = new BufferedImage(view.getWidth() * 2, view.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
                    var g = out.createGraphics();
                    try {
                        g.setColor(semantic(kit, SemanticColor.SURFACE_APP)); g.fillRect(0, 0, out.getWidth(), out.getHeight());
                        g.scale(2, 2); view.paint(g);
                    } finally { g.dispose(); }
                    image[0] = out;
                    ImageIO.write(out, "png", directory.resolve(state + "@2x.png").toFile());
                    then.accept(out, kit);
                } catch (Exception thrown) { failure[0] = thrown; }
            }));
        } catch (java.lang.reflect.InvocationTargetException wrapped) {
            // An assertion inside then fails the test with its own message.
            if (wrapped.getCause() instanceof Error error) throw error;
            throw wrapped;
        }
        if (failure[0] != null) throw failure[0];
        return image[0];
    }

    static BufferedImage mock(String state) throws Exception {
        return ImageIO.read(MOCKS.resolve(state.equals("typing-auth") ? "Run Dialog - Typing auth (filtered).png" : "Run Dialog - Just opened (recent + most used).png").toFile());
    }

    /** The probe's result in window pixels: in the mock from its window's origin, in the render the part under the list moved up. */
    static int probe(BufferedImage image, int originX, int originY, Feature feature, int shift, KitContext c) {
        return probe(image, originX, originY, feature, shift, feature.background().apply(c));
    }

    /**
     * The probe in the mock, on the mock's own colour where it starts: since the user's 2026-10-18 direction the theme ports
     * IntelliJ's, so a role need not be the mock's colour; the mock still gives the layout.
     */
    static int mockProbe(BufferedImage mock, Feature feature) {
        return probe(mock, MOCK_X, MOCK_Y, feature, 0, new Color(mock.getRGB(MOCK_X + feature.x(), MOCK_Y + feature.y())));
    }

    static int probe(BufferedImage image, int originX, int originY, Feature feature, int shift, Color background) {
        int steps = feature.dx() != 0 ? WIDTH : MOCK_HEIGHT;
        int found = MockComparison.edge(image, originX + feature.x(), originY + feature.y() + shift, feature.dx(), feature.dy(), steps, background);
        return found < 0 ? -1 : (feature.dx() != 0 ? feature.x() + found * feature.dx() : feature.y() + found * feature.dy());
    }

    void compare(String state) throws Exception { render(false, state, (image, c) -> check(image, state, c, OUT.resolve("light"))); }

    /**
     * {@code render}, the Run window alone at 2x drawn with {@code c}, against {@code state}'s mock; the side image goes to
     * {@code directory}. Call it on the EDT while {@code c} is open: from {@link #render}'s {@code then}, or, as plan 8b's
     * workspace render does for the window it crops from a whole window, inside the theme controller's lifetime.
     */
    public static void check(BufferedImage render, String state, KitContext c, Path directory) throws Exception {
        var mock = mock(state);
        assertThat(render.getWidth()).as("the window's width").isEqualTo(WIDTH);
        assertThat(render.getHeight()).as("the mock's height plus the six pixels of Rulings 5, at 2x").isEqualTo(MOCK_HEIGHT + SHIFT);
        var features = new ArrayList<>(FEATURES);
        features.addAll(state.equals("typing-auth") ? TYPING : OPENED);
        var misses = new ArrayList<String>();
        for (var feature : features) {
            int expected = mockProbe(mock, feature);
            int actual = probe(render, 0, 0, feature, feature.lower() ? SHIFT : 0, c);
            assertThat(expected).as(state + ": the mock shows " + feature.name()).isNotNegative();
            String line = feature.name() + ": mock " + expected + ", render " + actual;
            if (Math.abs(actual - expected) > feature.tolerance()) misses.add(line);
        }
        var colours = new ArrayList<String>();
        boolean typing = state.equals("typing-auth");
        var samples = new ArrayList<>(SAMPLES);
        if (!typing) samples.addAll(OPENED_SAMPLES);
        for (var sample : samples) {
            double distance = distance(render, sample, state, c);
            String line = sampleLine(mock, render, sample, state, c);
            if (distance > 3) colours.add(line);
        }
        if (typing) OPENED_SAMPLES.forEach(sample -> System.out.println("run-window " + state + " (residual, plan 8a R12) " + sampleLine(mock, render, sample, state, c)));
        assertThat(misses).as(state + ": geometry within tolerance").isEmpty();
        assertThat(colours).as(state + ": fills within 3 dE76 of the theme's").isEmpty();
        var masks = new ArrayList<>(List.of(SCROLLBAR));
        if (state.equals("typing-auth")) masks.add(COUNT);
        double top = MockComparison.bestAgreement(mock, new Rectangle(MOCK_X, MOCK_Y, WIDTH, BAND), render, new Rectangle(0, 0, WIDTH, BAND), masks, 2);
        int lower = MOCK_HEIGHT - BAND - 2;
        double bottom = MockComparison.bestAgreement(mock, new Rectangle(MOCK_X, MOCK_Y + BAND + 2, WIDTH, lower), render,
            new Rectangle(0, BAND + 2 + SHIFT, WIDTH, lower), List.of(), 2);
        ImageIO.write(MockComparison.side(mock, new Rectangle(MOCK_X, MOCK_Y, WIDTH, MOCK_HEIGHT), render, new Rectangle(0, 0, WIDTH, MOCK_HEIGHT + SHIFT)),
            "png", Files.createDirectories(directory).resolve(state + "-side.png").toFile());
        System.out.printf("run-window %s: edge agreement top %.3f, bottom %.3f%n", state, top, bottom);
        assertThat(top).as(state + ": edges above the list's foot").isGreaterThanOrEqualTo(0.70);
        assertThat(bottom).as(state + ": edges under the list").isGreaterThanOrEqualTo(0.70);
    }

    static Color mockColour(BufferedImage mock, Sample sample) { return new Color(mock.getRGB(MOCK_X + sample.x(), MOCK_Y + sample.y())); }
    static Color renderColour(BufferedImage render, Sample sample) { return new Color(render.getRGB(sample.x(), sample.y() + (sample.lower() ? SHIFT : 0))); }
    /** The render's sample against the colour the shipped theme paints there (the theme follows IntelliJ, the mocks give the layout). */
    static double distance(BufferedImage render, Sample sample, String state, KitContext c) {
        return MockComparison.deltaE(sample.colour().apply(c, state), renderColour(render, sample));
    }
    static String sampleLine(BufferedImage mock, BufferedImage render, Sample sample, String state, KitContext c) {
        return sample.name() + ": theme " + ColorMath.hex(sample.colour().apply(c, state)) + ", render " + ColorMath.hex(renderColour(render, sample))
            + String.format(" (%.1f)", distance(render, sample, state, c)) + ", mock " + ColorMath.hex(mockColour(mock, sample));
    }

    @Test void justOpenedMatchesItsMock() throws Exception { compare("just-opened"); }

    @Test void typingAuthMatchesItsMock() throws Exception { compare("typing-auth"); }

    /** The horizontal one-pixel probes: the structure Dark keeps. */
    static List<Feature> structure() { return FEATURES.stream().filter(f -> f.dy() == 0 && f.tolerance() <= 1).toList(); }

    /** No Dark mock shows the window: Dark keeps the Light geometry and its declared pairs readable. */
    @Test void enterpriseDarkKeepsTheGeometryAndTheTextReadable() throws Exception {
        var light = new java.util.LinkedHashMap<String, Integer>();
        var lightImage = render(false, "just-opened", (image, c) -> {
            for (var feature : structure()) light.put(feature.name(), probe(image, 0, 0, feature, feature.lower() ? SHIFT : 0, c));
        });
        render(true, "just-opened", (dark, c) -> {
            assertThat(dark.getWidth()).isEqualTo(lightImage.getWidth());
            assertThat(dark.getHeight()).isEqualTo(lightImage.getHeight());
            assertThat(c.theme().dark()).isTrue();
            var palette = c.theme().semantic();
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_PRIMARY), derived(c, DerivedColor.SURFACE_HEADER_ACTIVE))).as("the title").isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_STRONG_SECONDARY), dialog(c))).as("labels and help").isGreaterThanOrEqualTo(4.5);
            assertThat(ColorMath.contrast(palette.color(SemanticColor.TEXT_STRONG_SECONDARY), derived(c, DerivedColor.SURFACE_TABLE_HEADER))).as("headings").isGreaterThanOrEqualTo(4.5);
            // Spec 7.6: Enterprise Dark's accent keeps text.onAccent at 4.5:1, so the selected row paints text.onAccent, unrepaired.
            var selected = dev.jasper.ui.theme.SurfaceContrast.painted(c.theme(), dev.jasper.ui.swing.RunWindowPresentation.SELECTED_TITLE);
            assertThat(palette.color(SemanticColor.ACCENT)).as("the accent").isEqualTo(dev.jasper.app.testsupport.MockProfile.role(true, "accent"));
            assertThat(selected).as("the selected row's text, as painted").isEqualTo(palette.color(SemanticColor.TEXT_ONACCENT));
            assertThat(ColorMath.contrast(selected, palette.color(SemanticColor.ACCENT))).as("the selected row, as painted").isGreaterThanOrEqualTo(4.5);
            for (var feature : structure())
                assertThat(probe(dark, 0, 0, feature, feature.lower() ? SHIFT : 0, c)).as("dark: " + feature.name()).isEqualTo(light.get(feature.name()));
        });
    }
}
