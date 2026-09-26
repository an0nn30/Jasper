package dev.jasper.app.appearance;

import com.formdev.flatlaf.ui.FlatButtonUI;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ThemeButtonsTest {
    @Test void formControlsAreDrawnByFlatLafInTheThemesColours() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            var themes = new ThemeController();
            var button = new JButton("Generate Key...");
            var parent = new JPanel(); parent.add(button);
            var field = new JTextField("Existing text", 20);
            var password = new JPasswordField(20);
            var formatted = new JFormattedTextField();
            var choices = new JComboBox<>(new String[]{"Password", "Key"});
            var note = new JTextArea("First line\nSecond line");
            parent.add(field); parent.add(password); parent.add(formatted); parent.add(choices); parent.add(note);
            var dialog = new JRootPane(); var confirm = new JButton("OK"); dialog.getContentPane().add(confirm); dialog.setDefaultButton(confirm);
            int[] clicks = {0}; button.addActionListener(event -> clicks[0]++);
            for (var theme : List.of(Theme.DARK, Theme.LIGHT)) {
                themes.select(theme);
                SwingUtilities.updateComponentTreeUI(parent);
                SwingUtilities.updateComponentTreeUI(dialog);
                assertThat(button.getUI()).isInstanceOf(FlatButtonUI.class);
                assertThat(button.getFont().getSize2D()).as("form font").isEqualTo(12f);
                assertThat(button.getPreferredSize().height).isBetween(20, 28);
                assertThat(fill(button)).as(theme.name()).isEqualTo(UIManager.getColor("Button.background"));
                // IntelliJ Light's default button is a slight gradient; sample within a small tolerance.
                assertThat(distance(fill(confirm), UIManager.getColor("Button.default.background"))).as(theme.name()).isLessThanOrEqualTo(8);
                for (var input : List.of(field, password, formatted)) {
                    assertThat(input.getPreferredSize().height).isBetween(20, 30);
                    assertThat(input.isEditable()).isTrue();
                }
                assertThat(field.getBackground()).isEqualTo(UIManager.getColor("TextField.background"));
                assertThat(password.getBackground()).isEqualTo(UIManager.getColor("PasswordField.background"));
                assertThat(formatted.getBackground()).isEqualTo(UIManager.getColor("FormattedTextField.background"));
                assertThat(choices.getBackground()).isEqualTo(UIManager.getColor("ComboBox.background"));
                assertThat(note.getBackground()).isEqualTo(UIManager.getColor("TextArea.background"));
                assertThat(field.getText()).isEqualTo("Existing text");
                assertThat(note.getText()).isEqualTo("First line\nSecond line");
                button.setEnabled(false); button.doClick(); assertThat(clicks[0]).isZero();
                button.setEnabled(true);
            }
            button.doClick(); assertThat(clicks[0]).isEqualTo(1);
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

    private static int distance(Color a, Color b) {
        return Math.max(Math.abs(a.getRed() - b.getRed()), Math.max(Math.abs(a.getGreen() - b.getGreen()), Math.abs(a.getBlue() - b.getBlue())));
    }
}
