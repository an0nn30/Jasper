package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class OldGnomeCatalogTest {
    @Test void allCatalogChoicesPaintAtBothSizesAndScales() {
        assertThat(OldGnomeCatalog.NAMES).containsExactlyInAnyOrderElementsOf(NamedIcons.NAMES);
        for (String key : OldGnomeCatalog.NAMES) for (int size : new int[]{16, 28}) {
            var icon = OldGnomeCatalog.icon(key, size);
            assertThat(OldGnomeCatalog.icon(key, size)).isSameAs(icon);
            assertThat(icon.getIconWidth()).isEqualTo(size);
            assertThat(icon.getIconHeight()).isEqualTo(size);
            for (int scale : new int[]{1, 2}) {
                var image = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { g.scale(scale, scale); icon.paintIcon(new JLabel(), g, 0, 0); } finally { g.dispose(); }
                assertThat(visible(image)).as(key + " size " + size + " scale " + scale).isPositive();
            }
        }
        assertThatIllegalArgumentException().isThrownBy(() -> OldGnomeCatalog.icon("not-a-choice", 16));
        assertThatIllegalArgumentException().isThrownBy(() -> OldGnomeCatalog.icon("LOCK", 0));
    }

    @Test void sourceBytesMatchManifestAndCarryLicense() throws Exception {
        String base = "/dev/jasper/app/icons/oldgnome-sdk/";
        try (var in = getClass().getResourceAsStream(base + "assets.tsv")) {
            assertThat(in).isNotNull();
            var lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1).toList();
            assertThat(lines.size()).isGreaterThanOrEqualTo(44);
            for (String line : lines) {
                String[] fields = line.split("\t");
                try (var png = getClass().getResourceAsStream(base + fields[0])) {
                    assertThat(png).as(fields[0]).isNotNull();
                    assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png.readAllBytes())))
                        .isEqualTo(fields[2]);
                }
            }
        }
        for (String name : new String[]{"LICENSE.txt", "NOTICE.md"})
            try (var in = getClass().getResourceAsStream(base + name)) { assertThat(in).isNotNull(); }
    }

    @Test void managedVariantsKeepDimensionsAndMetalDisabledArtwork() {
        new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT);
        try {
            var small = new SkinIcon("LOCK");
            var large = small.toolbar();
            assertThat(small.getIconWidth()).isEqualTo(16);
            assertThat(large.getIconWidth()).isEqualTo(28);
            assertThat(large).isInstanceOf(ImageIcon.class);
            for (var icon : new javax.swing.Icon[]{small, large}) for (int scale : new int[]{1, 2}) {
                JButton button = new JButton(icon);
                button.setBorderPainted(false); button.setContentAreaFilled(false); button.setOpaque(false);
                button.setSize(icon.getIconWidth() + 20, icon.getIconHeight() + 20);
                var enabled = paint(button, scale);
                button.setEnabled(false);
                var disabled = paint(button, scale);
                assertThat(button.getDisabledIcon()).isNotNull();
                assertThat(visible(disabled)).isPositive();
                assertThat(java.util.Arrays.equals(enabled.getRGB(0,0,enabled.getWidth(),enabled.getHeight(),null,0,enabled.getWidth()),
                    disabled.getRGB(0,0,disabled.getWidth(),disabled.getHeight(),null,0,disabled.getWidth()))).isFalse();
            }
        } finally { new ThemeController(); }
    }

    private static int visible(BufferedImage image) {
        int count = 0;
        for (int y=0; y<image.getHeight(); y++) for(int x=0; x<image.getWidth(); x++)
            if ((image.getRGB(x,y) >>> 24) != 0) count++;
        return count;
    }
    private static BufferedImage paint(JButton button, int scale) {
        var image = new BufferedImage(button.getWidth()*scale,button.getHeight()*scale,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        try { g.scale(scale,scale); button.paint(g); } finally { g.dispose(); }
        return image;
    }
}
