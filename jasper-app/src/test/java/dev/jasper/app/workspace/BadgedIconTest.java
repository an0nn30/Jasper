package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class BadgedIconTest {
    @AfterEach void resetTheme() throws Exception { SwingUtilities.invokeAndWait(ThemeController::new); }

    @Test void theDotTakesItsToneColourInDarkAndLightThemes() {
        for (Theme theme : List.of(Theme.DARK, Theme.INTELLIJ_LIGHT)) {
            new ThemeController(theme);
            for (PanelBadge.Tone tone : PanelBadge.Tone.values()) {
                var icon = new BadgedIcon(new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)), tone);
                var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { icon.paintIcon(new JLabel(), g, 0, 0); } finally { g.dispose(); }
                int dot = UIScale.scale(6);
                Color expected = UIManager.getColor(tone == PanelBadge.Tone.ERROR ? "Jasper.railBadgeError" : "Jasper.railBadgeAccent");
                assertThat(new Color(image.getRGB(icon.getIconWidth() - dot / 2 - 1, dot / 2), true)).as("%s %s", theme, tone)
                    .isEqualTo(new Color(expected.getRGB()));
            }
        }
    }
}
