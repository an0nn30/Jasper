package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class DockDropOverlayTest {
    private static final String[] KEYS = {"DragAndDrop.areaBackground", "DragAndDrop.borderColor", "Component.accentColor"};
    private final Map<String, Object> saved = new java.util.HashMap<>();

    DockDropOverlayTest() { for (String key : KEYS) saved.put(key, UIManager.get(key)); }

    @AfterEach void restore() { saved.forEach(UIManager::put); }

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

    @Test void theHighlightAndThePlaceholderLandExactlyInTheirRectangles() {
        for (String key : KEYS) UIManager.put(key, null);
        UIManager.put("Component.accentColor", new Color(0x2f, 0x80, 0xed));
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

    @Test void theThemesOwnDragColoursWin() {
        UIManager.put("DragAndDrop.areaBackground", new Color(10, 20, 30, 40));
        UIManager.put("DragAndDrop.borderColor", new Color(200, 100, 50, 255));
        assertThat(DockDropOverlay.fill()).isEqualTo(new Color(10, 20, 30, 40));
        assertThat(DockDropOverlay.border()).isEqualTo(new Color(200, 100, 50, 255));
        for (String key : KEYS) UIManager.put(key, null);
        assertThat(DockDropOverlay.fill()).as("no accent either: IntelliJ's blue").isEqualTo(new Color(0x35, 0x74, 0xf0, 64));
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
