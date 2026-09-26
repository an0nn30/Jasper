package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.UiFontConfig;
import java.awt.Font;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiTypographyTest {
    @Test void configuredPointSizesAreExactAcrossTheWholeSupportedRange() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            try {
                for (float size : new float[]{8, 12, 14, 18.5f, 24, 28, 32}) {
                    themes.configure(Appearance.DARK, new UiFontConfig("system", size));
                    assertThat(new JLabel().getFont().getSize2D()).as("configured %s", size).isEqualTo(size);
                    assertThat(new JButton().getFont().getSize2D()).isEqualTo(size);
                }
            } finally { themes.configure(Appearance.DARK, UiFontConfig.defaults()); }
        });
    }

    @Test void missingFamiliesFallBackAndFontChangesDoNotDisturbTemporaryThemeSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            String platformFamily = UIManager.getFont("Label.font").getFamily();
            try {
                themes.select(Theme.LIGHT);
                themes.configure(Appearance.DARK, new UiFontConfig("NonexistentJasperFont394", 17));
                assertThat(themes.current().chrome()).isEqualTo(Theme.LIGHT);
                assertThat(new JLabel().getFont().getFamily()).isEqualTo(platformFamily);
                assertThat(new JLabel().getFont().getSize2D()).isEqualTo(17);
            } finally { themes.configure(Appearance.DARK, UiFontConfig.defaults()); }
        });
    }

    @Test void liveFontChangesReachControlsAndRetainStyleAcrossThemeChangesAndReset() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            var panel = new JPanel();
            var label = new JLabel("Host");
            var button = new JButton("Connect");
            var field = new JTextField("search");
            panel.add(label); panel.add(button); panel.add(field);
            Font original = label.getFont();
            var subscription = themes.subscribe((theme, delegates) -> { if (delegates) SwingUtilities.updateComponentTreeUI(panel); });
            try {
                themes.configure(Appearance.DARK, new UiFontConfig("Serif", 18));
                for (var component : new JComponent[]{label, button, field}) {
                    assertThat(component.getFont().getFamily()).isEqualTo(Font.SERIF);
                    assertThat(component.getFont().getSize2D()).isEqualTo(18);
                }
                themes.select(Theme.LIGHT);
                assertThat(label.getFont().getFamily()).isEqualTo(Font.SERIF);
                assertThat(label.getFont().getSize2D()).isEqualTo(18);
                themes.configure(Appearance.LIGHT, UiFontConfig.defaults());
                assertThat(label.getFont().getFamily()).isEqualTo(original.getFamily());
                assertThat(label.getFont().getSize2D()).isEqualTo(original.getSize2D());
            } finally { subscription.close(); themes.configure(Appearance.DARK, UiFontConfig.defaults()); }
        });
    }
}
