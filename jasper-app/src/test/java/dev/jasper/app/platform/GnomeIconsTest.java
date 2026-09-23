package dev.jasper.app.platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import dev.jasper.app.testsupport.EdtTestExtension;
import static org.assertj.core.api.Assertions.*;
@ExtendWith(EdtTestExtension.class)
class GnomeIconsTest {
@Test void everyBundledIconHasRealImagesAndPaintsAtBothScales() {
    for (String name : java.util.List.of("square-plus", "app-window", "columns-2", "maximize",
            "search", "settings", "refresh", "command", "history", "bookmark", "close")) {
        var icon = GnomeIcons.icon(name);
        assertThat(icon.getIconWidth()).isEqualTo(16);
        assertThat(icon.getIconHeight()).isEqualTo(16);
        for (int scale : new int[]{1, 2}) {
            var image = new java.awt.image.BufferedImage(16 * scale, 16 * scale,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { graphics.scale(scale, scale); icon.paintIcon(new javax.swing.JLabel(), graphics, 0, 0); }
            finally { graphics.dispose(); }
            boolean painted = false;
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++) painted |= (image.getRGB(x, y) >>> 24) != 0;
            assertThat(painted).as(name + " at " + scale).isTrue();
        }
    }
}

    @Test void resourceManifestAndLicenseTravelWithTheIcons() throws Exception {
        verifyManifest("gnome2", 22, java.util.List.of("LICENSE.txt", "NOTICE.md"));
        verifyManifest("tango", 10, java.util.List.of("COPYING", "AUTHORS", "NOTICE.md"));
    }

    private static void verifyManifest(String family, int count, java.util.List<String> notices) throws Exception {
        String base = "/dev/jasper/app/icons/" + family + "/";
        String manifest;
        try (var input = GnomeIcons.class.getResourceAsStream(base + "assets.tsv")) {
            assertThat(input).isNotNull();
            manifest = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var rows = manifest.lines().skip(1).toList();
        assertThat(rows).hasSize(count);
        for (String row : rows) {
            String[] values = row.split("\t");
            try (var input = GnomeIcons.class.getResourceAsStream(base + values[0])) {
                assertThat(input).isNotNull();
                byte[] bytes = input.readAllBytes();
                assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))).isEqualTo(values[2]);
                var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                int size = Integer.parseInt(values[0].split("/")[0]);
                assertThat(image.getWidth()).isEqualTo(size);
                assertThat(image.getHeight()).isEqualTo(size);
            }
        }
        for (String notice : notices) {
            try (var input = GnomeIcons.class.getResourceAsStream(base + notice)) {
                assertThat(input).isNotNull();
                assertThat(input.readAllBytes()).isNotEmpty();
            }
        }
    }

    @Test void metalDisabledIconsRemainVisibleButDifferFromEnabledIcons() {
        new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.ThemeStyle.RETRO, dev.jasper.app.config.Appearance.LIGHT);
        try {
            for (String name : java.util.List.of("maximize", "command")) for (int size : new int[]{16, 24, 32}) {
                var clicks = new java.util.concurrent.atomic.AtomicInteger();
                var button = new javax.swing.JButton(GnomeIcons.icon(name, size));
                button.addActionListener(event -> clicks.incrementAndGet());
                button.setBorderPainted(false); button.setContentAreaFilled(false); button.setFocusable(false);
                button.setSize(size + 4, size + 4);
                for (int scale : new int[]{1, 2}) {
                    button.setEnabled(true);
                    var enabled = paint(button, scale);
                    button.setEnabled(false);
                    var disabled = paint(button, scale);
                    assertThat(button.getDisabledIcon()).isNotNull();
                    int changed = 0, visible = 0;
                    for (int y = 0; y < enabled.getHeight(); y++) for (int x = 0; x < enabled.getWidth(); x++) {
                        if (enabled.getRGB(x, y) != disabled.getRGB(x, y)) changed++;
                        if ((disabled.getRGB(x, y) >>> 24) != 0) visible++;
                    }
                    assertThat(changed).as(name + " " + size + " at " + scale).isPositive();
                    assertThat(visible).isPositive();
                    button.doClick(); assertThat(clicks.get()).isZero();
                }
            }
        } finally { new dev.jasper.app.appearance.ThemeController(); }
    }
    private static java.awt.image.BufferedImage paint(javax.swing.JButton button, int scale) {
        var image = new java.awt.image.BufferedImage(button.getWidth() * scale, button.getHeight() * scale,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(scale, scale); button.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }
}
