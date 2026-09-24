package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.swing.Icon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class NamedIconsTest {
    @Test void catalogRendersEveryNameAndDistinctLockStatesInBothSkins() {
        try {
            for (boolean retro : new boolean[]{false, true}) {
                new ThemeController(retro ? ThemeStyle.RETRO : ThemeStyle.MODERN, Appearance.LIGHT);
                assertThat(NamedIcons.NAMES).contains("FILE", "LINK", "UPLOAD", "DOWNLOAD", "UP", "NEW_FOLDER", "PAUSE", "RESUME");
                assertThat(OldGnomeCatalog.NAMES).containsExactlyInAnyOrderElementsOf(NamedIcons.NAMES);
                for (String name : NamedIcons.NAMES) {
                    var icon = AppIcons.named(name);
                    assertThat(icon.getIconWidth()).isEqualTo(16);
                    var toolbar = AppIcons.forToolbar(icon);
                    assertThat(toolbar.getIconWidth()).isEqualTo(retro ? 28 : 16);
                    for (int scale : new int[]{1,2}) {
                        assertThat(java.util.Arrays.stream(pixels(icon, scale)).anyMatch(pixel -> (pixel >>> 24) != 0)).as(name).isTrue();
                        assertThat(java.util.Arrays.stream(pixels(toolbar, scale)).anyMatch(pixel -> (pixel >>> 24) != 0)).isTrue();
                    }
                }
                assertThat(pixels(AppIcons.named("LOCK"),1)).isNotEqualTo(pixels(AppIcons.named("UNLOCK"),1));
                assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.named("invalid"));
            }
        } finally { new ThemeController(); }
    }

    @Test void modernNamedIconRecolorsAndUsesOriginalVaultShapes() throws Exception {
        var theme = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        try {
            var icon = AppIcons.named("LOCK");
            var before = pixels(icon,1);
            theme.selectAppearance(Appearance.DARK);
            assertThat(pixels(icon,1)).isNotEqualTo(before);
            assertThat(AppIcons.forToolbar(icon)).isSameAs(icon);
            Path root = Path.of("").toAbsolutePath();
            while (root != null && !Files.isDirectory(root.resolve("plugins/vault"))) root = root.getParent();
            assertThat(root).isNotNull();
            for (String name : new String[]{"LOCK", "UNLOCK"}) {
                try (var in = getClass().getResourceAsStream("/dev/jasper/app/icons/standard/"+name+".svg")) {
                    assertThat(in).isNotNull();
                    assertThat(in.readAllBytes()).isEqualTo(Files.readAllBytes(root.resolve(
                        "plugins/vault/src/main/resources/dev/jasper/vault/"+(name.equals("LOCK") ? "lock" : "lock-open")+".svg")));
                }
            }
        } finally { new ThemeController(); }
    }

    @Test void manifestAndNoticeCoverAllStandardArtwork() throws Exception {
        String base="/dev/jasper/app/icons/standard/";
        try (var in=getClass().getResourceAsStream(base+"assets.tsv")) {
            assertThat(in).isNotNull();
            var rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().skip(1).toList();
            assertThat(rows).hasSize(NamedIcons.NAMES.size());
            for(String row:rows) {
                var fields=row.split("\\t");
                try(var asset=getClass().getResourceAsStream(base+fields[0])) {
                    assertThat(asset).isNotNull();
                    assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(asset.readAllBytes()))).isEqualTo(fields[2]);
                }
            }
        }
        for(String file:new String[]{"NOTICE.md","LICENSE.txt"})
            try(var in=getClass().getResourceAsStream(base+file)){assertThat(in).isNotNull();}
    }

    private static int[] pixels(Icon icon, int scale) {
        var image=new BufferedImage(icon.getIconWidth()*scale,icon.getIconHeight()*scale,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        try {g.scale(scale,scale);icon.paintIcon(new JLabel(),g,0,0);}finally{g.dispose();}
        return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
}
