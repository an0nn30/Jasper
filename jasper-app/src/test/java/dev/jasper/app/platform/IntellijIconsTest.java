package dev.jasper.app.platform;

import dev.jasper.app.appearance.Theme;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class IntellijIconsTest {
    private static final String BASE = "dev/jasper/app/icons/intellij/";


    @Test void manifestPinsEveryBundledSvgToItsUpstreamPathAndHash() throws Exception {
        List<String> rows;
        try (var in = getClass().getResourceAsStream("/" + BASE + "assets.tsv")) {
            assertThat(in).as("manifest").isNotNull();
            rows = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1).toList();
        }
        Set<String> listed = new HashSet<>();
        for (String row : rows) {
            String[] fields = row.split("\t");
            assertThat(fields).as(row).hasSize(3);
            assertThat(fields[1]).contains("/expui/").endsWith(".svg");
            listed.add(fields[0]);
            try (var asset = getClass().getResourceAsStream("/" + BASE + fields[0])) {
                assertThat(asset).as(fields[0]).isNotNull();
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(asset.readAllBytes()));
                assertThat(hash).as(fields[0]).isEqualTo(fields[2]);
            }
        }
        assertThat(listed).containsExactlyInAnyOrder("terminal/toolwindow/terminal.svg", "nodes/pluginLogo.svg", "nodes/pluginLogo_dark.svg");
        for (String file : new String[]{"LICENSE.txt", "NOTICE.txt", "SOURCE.md"})
            try (var in = getClass().getResourceAsStream("/" + BASE + file)) { assertThat(in).as(file).isNotNull(); }
        try (var in = getClass().getResourceAsStream("/" + BASE + "LICENSE.txt")) {
            String license = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(license.stripLeading()).startsWith("Apache License");
            assertThat(license).contains("Version 2.0, January 2004");
        }
    }

    @Test void flatLafRemapsIntellijPaletteColoursForTheRunningTheme() {
        var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
        try {
            var probe = new FlatSVGIcon("dev/jasper/app/icons/palette-probe.svg", 16, 16);
            assertThat(probe.hasFound()).isTrue();
            int light = centre(probe);
            assertThat(light).isEqualTo(UIManager.getColor("Actions.Blue").getRGB());
            themes.configure(Theme.DARK);
            int dark = centre(probe);
            assertThat(dark).as("the same icon instance re-filters on a live theme change")
                .isEqualTo(UIManager.getColor("Actions.Blue").getRGB());
            assertThat(dark).isNotEqualTo(light);
        } finally { new ThemeController(); }
    }

    static int centre(Icon icon) { return pixels(icon)[8 * 16 + 8]; }

    static int[] pixels(Icon icon) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(new JLabel(), g, 0, 0); } finally { g.dispose(); }
        return image.getRGB(0, 0, 16, 16, null, 0, 16);
    }
}
