package dev.jasper.app.platform;

import dev.jasper.app.appearance.Theme;
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
            new ThemeController(Theme.LIGHT);
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
        } finally { new ThemeController(); }
    }

    @Test void allSemanticIconsUseTheNewCatalogAndLockStatesFollowLiveThemes() {
        var theme = new ThemeController(Theme.LIGHT);
        try {
            for (String name : NamedIcons.NAMES)
                if (!name.equals("KEY")) assertThat(AppIcons.bundledIds()).contains(NamedIcons.id(name));
            var icon = AppIcons.named("LOCK");
            var before = pixels(icon, 1);
            theme.configure(Theme.DARK);
            assertThat(pixels(icon, 1)).isNotEqualTo(before);
            assertThat(pixels(icon, 1)).isNotEqualTo(pixels(AppIcons.named("UNLOCK"), 1));
        } finally { new ThemeController(); }
    }

    @Test void keyFallbackRetainsMeaningProvenanceAndLiveThemeColor() throws Exception {
        String base = "/dev/jasper/app/icons/standard/";
        try (var manifest = getClass().getResourceAsStream(base + "assets.tsv")) {
            var rows = new String(manifest.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().skip(1).toList();
            assertThat(rows).hasSize(1);
            var fields = rows.getFirst().split("\\t");
            assertThat(fields[0]).isEqualTo("KEY.svg");
            try (var svg = getClass().getResourceAsStream(base + fields[0])) {
                assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(svg.readAllBytes()))).isEqualTo(fields[2]);
            }
        }
        var theme = new ThemeController(Theme.LIGHT);
        try {
            var key = AppIcons.named("KEY");
            var before = pixels(key, 1);
            theme.configure(Theme.DARK);
            assertThat(pixels(key, 1)).isNotEqualTo(before);
        } finally { new ThemeController(); }
    }

    private static int[] pixels(Icon icon, int scale) {
        var image=new BufferedImage(icon.getIconWidth()*scale,icon.getIconHeight()*scale,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        try {g.scale(scale,scale);icon.paintIcon(new JLabel(),g,0,0);}finally{g.dispose();}
        return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
}
