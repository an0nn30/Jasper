package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class SkinIconsTest {
    private static final String SVG = "dev/jasper/app/icons/intellij/find.svg";
    @Test void modernRecolorsLiveWhileRetroSelectionStaysCaptured() {
        var theme = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        try {
            var modern = AppIcons.skin(getClass().getClassLoader(), SVG, "LOCK");
            int[] light = pixels(modern);
            theme.selectAppearance(Appearance.DARK);
            assertThat(pixels(modern)).isNotEqualTo(light);
            assertThat(AppIcons.forToolbar(modern)).isSameAs(modern);
            new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT);
            var retro = AppIcons.skin(getClass().getClassLoader(), SVG, "LOCK");
            int[] colors = pixels(retro);
            new ThemeController(); // simulate a separate process-style lifecycle in this isolated test
            assertThat(pixels(retro)).isEqualTo(colors);
            assertThat(AppIcons.forToolbar(retro).getIconWidth()).isEqualTo(28);
            assertThat(retro.getIconWidth()).isEqualTo(16);
            assertThat(AppIcons.forToolbar(modern)).isSameAs(modern);
            assertThat(AppIcons.forToolbar(new javax.swing.ImageIcon())).isNotNull();
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.skin(getClass().getClassLoader(), SVG, "invalid"));
        } finally { new ThemeController(); }
    }
    private static int[] pixels(Icon icon) {
        var image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        try { icon.paintIcon(new JLabel(),g,0,0); } finally { g.dispose(); }
        return image.getRGB(0,0,16,16,null,0,16);
    }
}
