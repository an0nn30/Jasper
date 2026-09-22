package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class BrandedButtonsTest {
    @Test void ordinaryPluginButtonsInheritTheToolbarShapeAndKeepTheirBehavior() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            var themes = new ThemeController();
            var button = new JButton("Generate Key...");
            var parent = new JPanel(); parent.add(button);
            int[] clicks = {0}; button.addActionListener(event -> clicks[0]++);
            for (var theme : BuiltinTheme.values()) {
                themes.select(theme);
                SwingUtilities.updateComponentTreeUI(parent);
                assertThat(button.getPreferredSize().height).isEqualTo(30);
                assertThat(UIManager.getInt("Button.arc")).isEqualTo(12);
                assertThat(button.getMargin().left).isEqualTo(12);
                assertThat(button.isFocusable()).isTrue();
                assertThat(fill(button)).isEqualTo(UIManager.getColor("Jasper.primaryBackground"));
                button.getModel().setRollover(true);
                assertThat(fill(button)).isEqualTo(UIManager.getColor("Button.toolbar.hoverBackground"));
                button.getModel().setArmed(true); button.getModel().setPressed(true);
                assertThat(fill(button)).isEqualTo(UIManager.getColor("Button.toolbar.pressedBackground"));
                button.getModel().setArmed(false); button.getModel().setPressed(false); button.getModel().setRollover(false);
                button.setEnabled(false); button.doClick(); assertThat(clicks[0]).isZero();
                button.setEnabled(true);
            }
            button.doClick(); assertThat(clicks[0]).isEqualTo(1);
            var root = new JRootPane(); root.setContentPane(parent); root.setDefaultButton(button);
            assertThat(button.isDefaultButton()).isTrue();
            assertThat(button.getPreferredSize().height).isEqualTo(30);
            button.setText("Always allow for an unusually long plugin name");
            assertThat(button.getPreferredSize().width).isGreaterThan(200);
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static Color fill(JButton button) {
        button.setSize(button.getPreferredSize());
        var image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); button.paint(graphics); graphics.dispose();
        return new Color(image.getRGB(button.getWidth() / 2, 4), true);
    }
}
