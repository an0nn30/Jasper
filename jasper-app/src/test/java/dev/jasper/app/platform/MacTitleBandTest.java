package dev.jasper.app.platform;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class MacTitleBandTest {
    static final Color FILL = new Color(0xF2F2F2);

    static JRootPane root(JPanel content) {
        var root = new JRootPane();
        root.setContentPane(content);
        return root;
    }

    static void layout(JRootPane root) { dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root); }

    /** IntelliJ Classic's macOS frame: full-window content under a transparent title, a 28 pt band, macOS's own title. */
    @Test void theBandIsIntelliJClassicsMacOsTitleRow() {
        var content = new JPanel();
        var root = root(content);
        var band = MacTitleBand.install(root, () -> FILL, () -> MacTitleDoubleClick.Action.ZOOM);
        assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isEqualTo(true);
        assertThat(root.getClientProperty("apple.awt.transparentTitleBar")).isEqualTo(true);
        assertThat(root.getClientProperty("apple.awt.windowTitleVisible")).as("macOS draws the frame's title").isNull();
        assertThat(band.getParent()).isSameAs(root.getContentPane());
        assertThat(content.getParent()).isSameAs(root.getContentPane());
        root.setSize(800, 600); layout(root);
        assertThat(band.getBounds()).isEqualTo(new Rectangle(0, 0, 800, MacTitleBand.HEIGHT));
        assertThat(content.getY()).isEqualTo(MacTitleBand.HEIGHT);
        assertThat(TitleInset.of(root)).isEqualTo(MacTitleBand.HEIGHT);
        var image = new BufferedImage(800, 40, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { root.getContentPane().printAll(g); } finally { g.dispose(); }
        for (int y = 0; y < MacTitleBand.HEIGHT; y++)
            assertThat(new Color(image.getRGB(400, y))).as("row %d: flat, with no rule", y).isEqualTo(FILL);
        band.setDark(true);
        assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameVibrantDark");
        band.setDark(false);
        assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameVibrantLight");
    }

    /** Parent tabs spec 6: a child window's ⋮ sits at the band's trailing end, centred on it, and only one does. */
    @Test void aTrailingControlSitsAtTheBandsTrailingEnd() {
        var content = new JPanel();
        var root = root(content);
        var band = MacTitleBand.install(root, () -> FILL, () -> MacTitleDoubleClick.Action.ZOOM);
        var control = new JPanel();
        control.setPreferredSize(new java.awt.Dimension(22, 22));
        band.setTrailing(control);
        band.setTrailing(new JPanel());
        assertThat(band.getComponents()).as("once only").containsExactly(control);
        root.setSize(800, 600); layout(root);
        assertThat(control.getBounds()).isEqualTo(new Rectangle(800 - 8 - 22, 3, 22, 22));
        assertThat(band.getBounds()).isEqualTo(new Rectangle(0, 0, 800, MacTitleBand.HEIGHT));
    }

    @Test void fullScreenRemovesTheBandAndLeavingRestoresIt() {
        var content = new JPanel();
        var root = root(content);
        var band = MacTitleBand.install(root, () -> FILL, () -> MacTitleDoubleClick.Action.ZOOM);
        root.setSize(800, 600);
        var changes = new java.util.ArrayList<String>();
        root.addPropertyChangeListener(TitleInset.CHANGED, event -> changes.add(event.getOldValue() + "->" + event.getNewValue()));
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(0, 0, 0, 0));
        assertThat(changes).as("the inset change is announced without a resize").containsExactly(MacTitleBand.HEIGHT + "->0");
        layout(root);
        assertThat(band.isVisible()).isFalse();
        assertThat(content.getY()).as("the content meets the screen's top edge").isZero();
        assertThat(TitleInset.of(root)).isZero();
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(7, 6, 52, 16));
        layout(root);
        assertThat(band.isVisible()).isTrue();
        assertThat(changes).containsExactly(MacTitleBand.HEIGHT + "->0", "0->" + MacTitleBand.HEIGHT);
        assertThat(content.getY()).isEqualTo(MacTitleBand.HEIGHT);
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, null);
        assertThat(band.isVisible()).as("no native window yet").isTrue();
        band.close();
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(0, 0, 0, 0));
        assertThat(band.isVisible()).as("closed: it stops following the root").isTrue();
    }

    /** Rulings 10: a double-click zooms, minimises or does nothing, as the system setting says. */
    @Test void aDoubleClickZoomsMinimisesOrDoesNothing() {
        assertThat(MacTitleBand.afterDoubleClick(Frame.NORMAL, MacTitleDoubleClick.Action.ZOOM)).isEqualTo(Frame.MAXIMIZED_BOTH);
        assertThat(MacTitleBand.afterDoubleClick(Frame.MAXIMIZED_BOTH, MacTitleDoubleClick.Action.ZOOM)).isEqualTo(Frame.NORMAL);
        assertThat(MacTitleBand.afterDoubleClick(Frame.NORMAL, MacTitleDoubleClick.Action.MINIMIZE)).isEqualTo(Frame.ICONIFIED);
        assertThat(MacTitleBand.afterDoubleClick(Frame.MAXIMIZED_BOTH, MacTitleDoubleClick.Action.MINIMIZE)).isEqualTo(Frame.MAXIMIZED_BOTH | Frame.ICONIFIED);
        assertThat(MacTitleBand.afterDoubleClick(Frame.MAXIMIZED_BOTH, MacTitleDoubleClick.Action.NONE)).isEqualTo(Frame.MAXIMIZED_BOTH);
        assertThat(MacTitleBand.fullScreen(new Rectangle(0, 0, 0, 0))).isTrue();
        assertThat(MacTitleBand.fullScreen(new Rectangle(7, 6, 52, 16))).isFalse();
        assertThat(MacTitleBand.fullScreen(null)).isFalse();
    }

    @Test void aRootWithoutClassicTitleChromeHasNoInset() {
        var root = new JRootPane();
        assertThat(TitleInset.of(root)).isZero();
        TitleInset.publish(root, () -> -3);
        assertThat(TitleInset.of(root)).as("never below 0").isZero();
        TitleInset.publish(root, () -> 29);
        assertThat(TitleInset.of(root)).isEqualTo(29);
    }
}
