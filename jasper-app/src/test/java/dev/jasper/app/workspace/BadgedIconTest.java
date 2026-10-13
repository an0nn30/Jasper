package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import com.formdev.flatlaf.util.UIScale;
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
    @AfterEach void resetTheme() throws Exception { SwingUtilities.invokeAndWait(() -> ThemeTestSupport.controller(true)); }

    @Test void theDotTakesItsToneColourInDarkAndLightThemes() {
        for (boolean themeDark : new boolean[]{true, false}) {
            ThemeTestSupport.controller(themeDark);
            for (PanelBadge.Tone tone : PanelBadge.Tone.values()) {
                var icon = new BadgedIcon(new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)), tone);
                var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { icon.paintIcon(new JLabel(), g, 0, 0); } finally { g.dispose(); }
                int dot = UIScale.scale(6);
                // The tone's role as painted on the rail, held to the 3:1 indicator floor there (IntelliJ Light's pale
                // accent is lifted; Jasper Dark's roles already read and are painted as resolved).
                var palette = dev.jasper.app.platform.ThemeZone.theme(null).semantic();
                Color expected = dev.jasper.ui.theme.SurfaceContrast.on(palette, tone == PanelBadge.Tone.ERROR
                    ? dev.jasper.ui.theme.SemanticColor.STATUS_DANGER : dev.jasper.ui.theme.SemanticColor.ACCENT,
                    dev.jasper.ui.theme.SemanticColor.SURFACE_APP, dev.jasper.ui.theme.SurfaceContrast.INDICATOR);
                Color painted = new Color(image.getRGB(icon.getIconWidth() - dot / 2 - 1, dot / 2), true);
                assertThat(painted).as("%s %s", (themeDark ? "dark" : "light"), tone).isEqualTo(new Color(expected.getRGB()));
                assertThat(dev.jasper.ui.theme.ColorMath.contrast(painted, palette.color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP)))
                    .as("%s %s on the rail", (themeDark ? "dark" : "light"), tone).isGreaterThanOrEqualTo(3);
                if (themeDark) assertThat(expected).isEqualTo(palette.color(tone == PanelBadge.Tone.ERROR
                    ? dev.jasper.ui.theme.SemanticColor.STATUS_DANGER : dev.jasper.ui.theme.SemanticColor.ACCENT));
            }
        }
    }

    @Test void theSizeEqualsTheBaseIconsSize() {
        var base = new ImageIcon(new BufferedImage(24, 32, BufferedImage.TYPE_INT_ARGB));
        var badged = new BadgedIcon(base, PanelBadge.Tone.ERROR);
        assertThat(badged.getIconWidth()).isEqualTo(base.getIconWidth());
        assertThat(badged.getIconHeight()).isEqualTo(base.getIconHeight());
    }
}
