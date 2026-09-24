package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
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
    static final List<String> REQUIRED = List.of("add", "moveToWindow", "splitVertically", "expandComponent", "find",
        "gearPlain", "refresh", "execute", "history", "bookmark", "close", "closeHovered", "exit", "arrowDown", "console",
        "server", "previousOccurence", "nextOccurence", "matchCase", "regex", "searchWithHistory", "folder",
        "menu-saveall", "copy", "menu-paste", "remove", "web", "information", "help");

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
            assertThat(fields[1]).startsWith("platform/icons/src/").endsWith(".svg");
            listed.add(fields[0]);
            try (var asset = getClass().getResourceAsStream("/" + BASE + fields[0])) {
                assertThat(asset).as(fields[0]).isNotNull();
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(asset.readAllBytes()));
                assertThat(hash).as(fields[0]).isEqualTo(fields[2]);
            }
        }
        for (String name : REQUIRED) assertThat(listed).contains(name + ".svg");
        for (String file : new String[]{"LICENSE.txt", "NOTICE.txt", "SOURCE.md"})
            try (var in = getClass().getResourceAsStream("/" + BASE + file)) { assertThat(in).as(file).isNotNull(); }
    }

    @Test void everyRequiredIconParsesAndPaintsAtSixteenPixels() {
        new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        try {
            for (String name : REQUIRED) {
                var icon = new FlatSVGIcon(BASE + name + ".svg", 16, 16);
                assertThat(icon.hasFound()).as(name).isTrue();
                assertThat(icon.getIconWidth()).isEqualTo(16);
                int[] pixels = pixels(icon);
                assertThat(java.util.Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) != 0)).as(name + " has ink").isTrue();
            }
        } finally { new ThemeController(); }
    }

    @Test void flatLafRemapsIntellijPaletteColoursForTheRunningTheme() {
        var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        try {
            var probe = new FlatSVGIcon("dev/jasper/app/icons/palette-probe.svg", 16, 16);
            assertThat(probe.hasFound()).isTrue();
            int light = centre(probe);
            assertThat(light).isEqualTo(UIManager.getColor("Actions.Blue").getRGB());
            themes.selectAppearance(Appearance.DARK);
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
