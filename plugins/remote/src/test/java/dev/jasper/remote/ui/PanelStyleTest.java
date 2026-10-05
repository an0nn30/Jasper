package dev.jasper.remote.ui;

import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.KeyboardFocusManager;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PanelStyleTest {
    @Test void primaryActionShowsKeyboardFocusWithoutDependingOnTheLookAndFeelBorder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var button = PanelStyle.primaryButton("Connect");
            button.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            button.setSize(100, 34);
            var idle = render(button);
            var previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            try {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                    @Override public Component getFocusOwner() { return button; }
                });
                assertThat(button.hasFocus()).isTrue();
                var focused = render(button);
                assertThat(focused.getRGB(0, 17)).as("the primary action has a visible outer focus ring")
                    .isNotEqualTo(idle.getRGB(0, 17));
                button.setFocusPainted(false);
                assertThat(render(button).getRGB(0, 17)).isEqualTo(idle.getRGB(0, 17));
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(previous); }
        });
    }

    private static BufferedImage render(JButton button) {
        var image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { button.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }
}
