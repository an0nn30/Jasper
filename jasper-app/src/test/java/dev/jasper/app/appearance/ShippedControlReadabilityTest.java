package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ShippedControlReadabilityTest {
    @Test void offSwitchPaintSeparatesKnobAndTrackThroughShippedLightDarkRefresh() throws Exception {
        var initial = ThemeGenerator.generate(Theme.DARK.source().document());
        var original = UIManager.getLookAndFeel();
        try (var binding = new ThemeBinding(new ThemePair(initial, initial), failure -> { throw new AssertionError(failure); });
             var icons = new IconRegistry()) {
            var kit = new KitContext(binding, ThemeRoute.CHROME, DesignMetrics.DEFAULT, icons, () -> false);
            for (boolean compact : List.of(false, true)) {
                var events = new java.util.ArrayList<UiEvent>();
                try (var view = new KitView(kit, new ViewSpec(new Nodes.Toggle("toggle", ControlState.READY, "Enabled", false, "toggle", compact)), events::add)) {
                    var button = KitTestSupport.require(view, "toggle", JButton.class);
                    for (var source : List.of(Theme.JASPER_LIGHT, Theme.DARK, Theme.JASPER_LIGHT)) {
                        var theme = ThemeGenerator.generate(source.source().document());
                        FlatLafInstaller.install(theme); binding.publish(new ThemePair(theme, theme)); SwingUtilities.updateComponentTreeUI(view);
                        button.setSize(button.getPreferredSize()); var image = paint(button);
                        // Centers of the left knob and right track avoid antialiasing and the shadow.
                        Color knob = new Color(image.getRGB(button.getWidth() / 4, button.getHeight() / 2), true);
                        Color track = new Color(image.getRGB(button.getWidth() * 3 / 4, button.getHeight() / 2), true);
                        assertThat(ColorMath.contrast(knob, track)).as(source.name() + " compact=" + compact).isGreaterThanOrEqualTo(3);
                        assertThat(knob).isEqualTo(theme.semantic().color(SemanticColor.TOGGLE_KNOB));
                        assertThat(track).isEqualTo(theme.semantic().color(SemanticColor.TOGGLE_TRACK_OFF));
                        assertThat(button.isEnabled()).isTrue(); assertThat(button.isSelected()).isFalse();
                    }
                    assertThat(events).isEmpty(); button.doClick(0); assertThat(events).containsExactly(new UiEvent.Toggled("toggle", true));
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void shippedTitleSearchHintsPaintWithTheirSurfaceFloor() {
        var blank = new Icon() {
            public int getIconWidth() { return 16; } public int getIconHeight() { return 16; }
            public void paintIcon(Component c, Graphics g, int x, int y) { }
        };
        for (var source : BuiltInThemes.all()) {
            var theme = ThemeGenerator.generate(source.source().document());
            for (boolean hover : List.of(false, true)) {
                var component = new JPanel(); component.setSize(300, 32);
                var image = new BufferedImage(300, 32, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try { NativeChromePainting.search(component, graphics, theme, hover, false, "", "MMMM", blank, null); }
                finally { graphics.dispose(); }
                var surface = hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_INPUT;
                var expected = SurfaceContrast.on(theme.semantic(), SemanticColor.TEXT_PLACEHOLDER, surface, SurfaceContrast.INDICATOR);
                assertThat(ColorMath.contrast(expected, theme.semantic().color(surface))).isGreaterThanOrEqualTo(3);
                assertThat(hasColor(image, expected)).as(source.name() + " hint paint hover=" + hover).isTrue();
            }
        }
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); try { component.paint(graphics); } finally { graphics.dispose(); } return image;
    }
    private static boolean hasColor(BufferedImage image, Color color) {
        for (int y = 1; y < image.getHeight()-1; y++) for (int x = image.getWidth()/2; x < image.getWidth()-2; x++)
            if (image.getRGB(x,y) == color.getRGB()) return true;
        return false;
    }
}
