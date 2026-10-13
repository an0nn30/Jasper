package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class DockDropOverlayTest {
    private static final String[] KEYS = {"DragAndDrop.areaBackground", "DragAndDrop.borderColor", "Component.accentColor"};
    private final Map<String, Object> saved = new java.util.HashMap<>();
    /** Set by a test that installs another look and feel. */
    private javax.swing.LookAndFeel originalLookAndFeel;

    @BeforeEach void save() throws Exception {
        edt(() -> { for (String key : KEYS) saved.put(key, UIManager.getLookAndFeelDefaults().get(key)); });
    }

    /** The look and feel first, since installing it rebuilds its defaults; then the three keys at both levels. */
    @AfterEach void restore() throws Exception {
        edt(() -> {
            if (originalLookAndFeel != null) {
                try { UIManager.setLookAndFeel(originalLookAndFeel); }
                catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
            for (String key : KEYS) {
                Object lfValue = saved.get(key);
                if (lfValue != null) UIManager.getLookAndFeelDefaults().put(key, lfValue);
                else UIManager.getLookAndFeelDefaults().remove(key);
                UIManager.put(key, null);
            }
        });
    }

    private static void noThemeColours() {
        for (String key : KEYS) {
            UIManager.put(key, null);
            UIManager.getLookAndFeelDefaults().remove(key);
        }
    }

    private static BufferedImage paint(DockDropOverlay overlay) {
        overlay.setSize(400, 300);
        var image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { overlay.paint(g); } finally { g.dispose(); }
        return image;
    }

    /** Within three steps per channel: compositing a translucent colour onto a clear image rounds. */
    private static void assertColour(BufferedImage image, int x, int y, Color expected) {
        var actual = new Color(image.getRGB(x, y), true);
        assertThat(actual.getAlpha()).as("alpha at %s,%s", x, y).isEqualTo(expected.getAlpha());
        for (int[] channel : new int[][]{{actual.getRed(), expected.getRed()}, {actual.getGreen(), expected.getGreen()}, {actual.getBlue(), expected.getBlue()}})
            assertThat(Math.abs(channel[0] - channel[1])).as("colour at %s,%s: %s", x, y, actual).isLessThanOrEqualTo(3);
    }

    /** Records what the overlay marks dirty, before Swing's showing checks, so it works headless. */
    private static final class RecordingRepaintManager extends RepaintManager {
        private final JComponent target;
        final List<Rectangle> dirty = new ArrayList<>();

        RecordingRepaintManager(JComponent target) { this.target = target; }

        @Override public void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
            if (component == target) dirty.add(new Rectangle(x, y, width, height));
            super.addDirtyRegion(component, x, y, width, height);
        }
    }

    /**
     * The rectangles the overlay marks dirty while the action runs. The previous manager is global state shared with
     * every later test, so it comes back in {@code finally}; tests run on the EDT, where Swing expects it to change.
     */
    private static List<Rectangle> dirtied(DockDropOverlay overlay, Runnable action) {
        RepaintManager previous = RepaintManager.currentManager(overlay);
        var recorder = new RecordingRepaintManager(overlay);
        RepaintManager.setCurrentManager(recorder);
        try { action.run(); } finally { RepaintManager.setCurrentManager(previous); }
        return recorder.dirty;
    }

    /** The smallest rectangle holding every rectangle given. */
    private static Rectangle union(Rectangle first, Rectangle... more) {
        Rectangle all = new Rectangle(first);
        for (Rectangle rectangle : more) all = all.union(rectangle);
        return all;
    }

    private static Rectangle grown(Rectangle rectangle, int by) {
        return new Rectangle(rectangle.x - by, rectangle.y - by, rectangle.width + 2 * by, rectangle.height + 2 * by);
    }

    /** Every dirty rectangle lies within the union of the areas that changed, and together they cover each of them. */
    private static void assertDirties(List<Rectangle> dirty, Rectangle... changed) {
        Rectangle allowed = union(changed[0], Arrays.copyOfRange(changed, 1, changed.length));
        assertThat(dirty).as("something was marked dirty").isNotEmpty();
        for (Rectangle rectangle : dirty) assertThat(allowed.contains(rectangle)).as("%s lies within %s", rectangle, allowed).isTrue();
        Rectangle covered = union(dirty.get(0), dirty.subList(1, dirty.size()).toArray(new Rectangle[0]));
        for (Rectangle area : changed) assertThat(covered.contains(area)).as("%s covers %s", covered, area).isTrue();
    }

    private static ImageIcon square(int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); g.setColor(Color.RED); g.fillRect(0, 0, size, size); g.dispose();
        return new ImageIcon(image);
    }

    /** All three guides, far enough apart that no change to one touches another; 400 x 300 in all. */
    private static DockDropOverlay overlayWithAllGuides(ImageIcon icon) {
        var overlay = new DockDropOverlay();
        overlay.setSize(400, 300);
        overlay.show(new Rectangle(50, 40, 200, 100), new Rectangle(10, 160, 22, 22), icon, new Point(300, 200));
        return overlay;
    }

    @Test void movingOnlyTheCursorRepaintsOnlyTheGhostsOldAndNewBounds() {
        var icon = square(16);
        var overlay = overlayWithAllGuides(icon);
        List<Rectangle> dirty = dirtied(overlay,
            () -> overlay.show(new Rectangle(50, 40, 200, 100), new Rectangle(10, 160, 22, 22), icon, new Point(330, 220)));
        assertDirties(dirty, grown(new Rectangle(292, 192, 16, 16), 1), grown(new Rectangle(322, 212, 16, 16), 1));
        assertThat(dirty).as("never the whole overlay").noneMatch(rectangle -> rectangle.contains(new Rectangle(0, 0, 400, 300)));
    }

    @Test void changingTheGhostWithoutMovingItRepaintsItsBounds() {
        var overlay = overlayWithAllGuides(square(16));
        List<Rectangle> dirty = dirtied(overlay,
            () -> overlay.show(new Rectangle(50, 40, 200, 100), new Rectangle(10, 160, 22, 22), square(16), new Point(300, 200)));
        assertDirties(dirty, grown(new Rectangle(292, 192, 16, 16), 1));
    }

    @Test void changingOnlyTheHighlightRepaintsItsOldAndNewRectangles() {
        var icon = square(16);
        var overlay = overlayWithAllGuides(icon);
        List<Rectangle> dirty = dirtied(overlay,
            () -> overlay.show(new Rectangle(70, 40, 200, 100), new Rectangle(10, 160, 22, 22), icon, new Point(300, 200)));
        assertDirties(dirty, new Rectangle(50, 40, 200, 100), new Rectangle(70, 40, 200, 100));
    }

    @Test void changingOnlyThePlaceholderRepaintsItsOldAndNewRectanglesGrownByTheStroke() {
        var icon = square(16);
        var overlay = overlayWithAllGuides(icon);
        List<Rectangle> dirty = dirtied(overlay,
            () -> overlay.show(new Rectangle(50, 40, 200, 100), new Rectangle(10, 190, 22, 22), icon, new Point(300, 200)));
        assertDirties(dirty, grown(new Rectangle(10, 160, 22, 22), 1), grown(new Rectangle(10, 190, 22, 22), 1));
    }

    @Test void aGuideAppearingRepaintsOnlyItsOwnBounds() {
        var overlay = new DockDropOverlay();
        overlay.setSize(400, 300);
        List<Rectangle> dirty = dirtied(overlay, () -> overlay.show(null, new Rectangle(10, 160, 22, 22), null, null));
        assertDirties(dirty, grown(new Rectangle(10, 160, 22, 22), 1));
        assertThat(dirtied(overlay, () -> overlay.show(null, new Rectangle(10, 160, 22, 22), square(16), null)))
            .as("a ghost with no cursor has nowhere to be").isEmpty();
    }

    @Test void anIdenticalShowRepaintsNothing() {
        var icon = square(16);
        var overlay = overlayWithAllGuides(icon);
        assertThat(dirtied(overlay,
            () -> overlay.show(new Rectangle(50, 40, 200, 100), new Rectangle(10, 160, 22, 22), icon, new Point(300, 200)))).isEmpty();
    }

    @Test void clearRepaintsExactlyTheOldGuidesBounds() {
        var overlay = overlayWithAllGuides(square(16));
        List<Rectangle> dirty = dirtied(overlay, overlay::clear);
        assertThat(dirty).containsExactlyInAnyOrder(new Rectangle(50, 40, 200, 100), grown(new Rectangle(10, 160, 22, 22), 1),
            grown(new Rectangle(292, 192, 16, 16), 1));
        assertThat(dirtied(overlay, overlay::clear)).as("already clear").isEmpty();
    }

    @Test void thePlaceholdersBorderDoesNotStackOnItsFill() {
        installDragTheme(new Color(0x2f, 0x80, 0xed), Map.of());
        var overlay = new DockDropOverlay();
        overlay.show(null, new Rectangle(10, 160, 40, 40), null, null);
        BufferedImage image = paint(overlay);
        Color fill = new Color(0x2f, 0x80, 0xed, 64), border = new Color(0x2f, 0x80, 0xed, 153);
        assertColour(image, 30, 160, border);
        assertColour(image, 30, 199, border);
        assertColour(image, 10, 180, border);
        assertColour(image, 49, 180, border);
        assertColour(image, 30, 161, fill);
        assertColour(image, 30, 180, fill);
    }

    @Test void theHighlightAndThePlaceholderLandExactlyInTheirRectangles() {
        installDragTheme(new Color(0x2f, 0x80, 0xed), Map.of());
        var overlay = new DockDropOverlay();
        overlay.show(new Rectangle(50, 40, 200, 100), new Rectangle(10, 160, 22, 22), null, null);
        BufferedImage image = paint(overlay);
        Color fill = new Color(0x2f, 0x80, 0xed, 64), border = new Color(0x2f, 0x80, 0xed, 153);
        assertColour(image, 150, 90, fill);
        assertColour(image, 50, 40, border);
        assertColour(image, 249, 139, border);
        assertThat(new Color(image.getRGB(49, 90), true).getAlpha()).as("left of the highlight").isZero();
        assertThat(new Color(image.getRGB(250, 90), true).getAlpha()).as("right of the highlight").isZero();
        assertThat(new Color(image.getRGB(150, 141), true).getAlpha()).as("under the highlight").isZero();
        assertColour(image, 21, 171, fill);
        assertThat(new Color(image.getRGB(10, 160), true).getAlpha()).as("the placeholder's corner is rounded off").isLessThan(64);
        assertThat(overlay.contains(150, 90)).as("mouse events pass through").isFalse();
    }

    /**
     * Drag guides use the canonical accent with the kit's fill/border alphas. DragAndDrop.* has no canonical role,
     * so an authored value no longer bypasses the canonical route (recorded as an unsupported native override);
     * an explicit canonical accent reaches the guides.
     */
    @Test void dragGuidesFollowTheCanonicalAccentRoute() {
        installDragTheme(new Color(0x3574f0), Map.of("DragAndDrop.areaBackground", "#0a141e28", "DragAndDrop.borderColor", "#c86432"));
        assertThat(DockDropOverlay.fill()).isEqualTo(new Color(0x35, 0x74, 0xf0, 64));
        assertThat(DockDropOverlay.border()).isEqualTo(new Color(0x35, 0x74, 0xf0, 153));
        installDragTheme(new Color(0x2f80ed), Map.of());
        assertThat(DockDropOverlay.fill()).isEqualTo(new Color(0x2f, 0x80, 0xed, 64));
        assertThat(DockDropOverlay.border()).isEqualTo(new Color(0x2f, 0x80, 0xed, 153));
    }

    private void installDragTheme(Color accent, Map<String, Object> colors) {
        if (originalLookAndFeel == null) originalLookAndFeel = UIManager.getLookAndFeel();
        ThemeTestSupport.install(ThemeTestSupport.withUiKeys(ThemeTestSupport.recolored(ThemeTestSupport.generated(true),
            Map.of(dev.jasper.ui.theme.SemanticColor.ACCENT, accent)), colors));
    }

    @Test void theGhostFollowsTheCursorAndClearRemovesEverything() {
        var icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = icon.createGraphics(); g.setColor(Color.RED); g.fillRect(0, 0, 16, 16); g.dispose();
        var overlay = new DockDropOverlay();
        overlay.show(null, null, new ImageIcon(icon), new Point(200, 100));
        BufferedImage image = paint(overlay);
        assertThat(image.getRGB(192, 92)).isEqualTo(Color.RED.getRGB());
        assertThat(image.getRGB(207, 107)).isEqualTo(Color.RED.getRGB());
        assertThat(new Color(image.getRGB(208, 100), true).getAlpha()).isZero();
        overlay.clear();
        assertThat(overlay.highlight()).isNull();
        assertThat(new Color(paint(overlay).getRGB(200, 100), true).getAlpha()).isZero();
    }
}
