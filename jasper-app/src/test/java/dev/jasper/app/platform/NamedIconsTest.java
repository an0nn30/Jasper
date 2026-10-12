package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeTestSupport;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class NamedIconsTest {
    @Test void catalogRendersEveryNameAndDistinctLockStates() {
        try {
            ThemeTestSupport.controller(false);
            assertThat(NamedIcons.NAMES).contains("FILE", "LINK", "UPLOAD", "DOWNLOAD", "UP", "NEW_FOLDER", "PAUSE", "RESUME",
                "SPLIT", "ZOOM", "TERMINAL", "SERVER");
            for (String name : NamedIcons.NAMES) {
                var icon = AppIcons.named(name);
                assertThat(icon.getIconWidth()).isEqualTo(16);
                for (int scale : new int[]{1,2}) {
                    assertThat(java.util.Arrays.stream(pixels(icon, scale)).anyMatch(pixel -> (pixel >>> 24) != 0)).as(name).isTrue();
                }
            }
            assertThat(pixels(AppIcons.named("LOCK"),1)).isNotEqualTo(pixels(AppIcons.named("UNLOCK"),1));
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.named("invalid"));
        } finally { ThemeTestSupport.controller(true); }
    }

    @Test void allSemanticIconsUseTheNewCatalogAndLockStatesFollowLiveThemes() {
        var theme = ThemeTestSupport.controller(false);
        try {
            for (String name : NamedIcons.NAMES)
                assertThat(new dev.jasper.ui.icons.IconRegistry().names()).contains(NamedIcons.kitName(name));
            var icon = AppIcons.named("LOCK");
            var before = pixels(icon, 1);
            theme.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            assertThat(pixels(icon, 1)).isNotEqualTo(before);
            assertThat(pixels(icon, 1)).isNotEqualTo(pixels(AppIcons.named("UNLOCK"), 1));
        } finally { ThemeTestSupport.controller(true); }
    }

    @Test void keyFallbackRetainsMeaningProvenanceAndLiveThemeColor() throws Exception {
        var theme = ThemeTestSupport.controller(false);
        try {
            var key = AppIcons.named("KEY");
            var before = pixels(key, 1);
            theme.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            assertThat(pixels(key, 1)).isNotEqualTo(before);
        } finally { ThemeTestSupport.controller(true); }
    }

    private static int[] pixels(Icon icon, int scale) {
        var image=new BufferedImage(icon.getIconWidth()*scale,icon.getIconHeight()*scale,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        try {g.scale(scale,scale);icon.paintIcon(new JLabel(),g,0,0);}finally{g.dispose();}
        return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
}
