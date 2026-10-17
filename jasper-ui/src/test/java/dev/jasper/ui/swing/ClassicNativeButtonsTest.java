package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.Test;

/**
 * Plan 4a's residual (plan 4b Execution notes X9, plan 4c Execution notes E9): a classic native FlatLaf button keeps its
 * gradient while hovered or focused and darkens to the gradient's end while pressed; the default kit's keys are unchanged.
 */
class ClassicNativeButtonsTest {
    static BufferedImage paint(JButton button) {
        var image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { button.paint(g); } finally { g.dispose(); }
        return image;
    }

    static void installed(Kit kit, Runnable check) {
        var prior = UIManager.getLookAndFeel();
        try {
            FlatLafInstaller.install(KitFixtures.light(), kit);
            check.run();
        } finally {
            try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
        }
    }

    @Test void aClassicNativeButtonKeepsItsGradientWhenHoveredAndDarkensWhenPressed() {
        Edt.run(() -> installed(KitFixtures.enterprise(), () -> {
            assertThat(UIManager.get("Button.focusedBackground")).as("no focused fill").isNull();
            assertThat(UIManager.get("Button.hoverBackground")).as("no hover fill").isNull();
            var button = new JButton("Open");
            button.setSize(120, 30);
            int inset = UIManager.getInt("Component.focusWidth") + 3;
            var rest = paint(button);
            button.getModel().setRollover(true);
            var hovered = paint(button);
            assertThat(hovered.getRGB(60, inset)).as("the hovered gradient's top").isEqualTo(rest.getRGB(60, inset));
            assertThat(hovered.getRGB(60, 29 - inset)).as("the hovered gradient's foot").isEqualTo(rest.getRGB(60, 29 - inset));
            assertThat(hovered.getRGB(60, inset)).as("a gradient, not a flat fill").isNotEqualTo(hovered.getRGB(60, 29 - inset));
            button.getModel().setArmed(true);
            button.getModel().setPressed(true);
            var pressed = paint(button);
            var end = DerivedColor.BUTTON_END.color(KitFixtures.light());
            assertThat(new Color(pressed.getRGB(60, inset))).isEqualTo(end);
            assertThat(new Color(pressed.getRGB(60, 29 - inset))).isEqualTo(end);
        }));
    }

    @Test void theDefaultKitKeepsItsStateFills() {
        Edt.run(() -> installed(Kit.DEFAULT, () -> {
            var theme = KitFixtures.light();
            assertThat(UIManager.getColor("Button.focusedBackground")).isEqualTo(theme.semantic().color(SemanticColor.BUTTON_SECONDARY_BG));
            assertThat(UIManager.getColor("Button.hoverBackground")).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_CONTROL_HOVER));
            assertThat(UIManager.getColor("Button.pressedBackground")).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_CONTROL_SELECTED));
        }));
    }
}
