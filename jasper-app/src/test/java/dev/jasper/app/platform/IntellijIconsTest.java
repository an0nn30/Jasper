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
        assertThat(listed).hasSize(1744);
        assertThat(AppIcons.bundledIds()).hasSize(885);
        for (String id : AppIcons.bundledIds()) assertThat(listed).contains(id + ".svg");
        assertThat(listed.stream().filter(path -> !path.endsWith("_dark.svg"))
            .map(path -> path.substring(0, path.length() - 4))).containsExactlyInAnyOrderElementsOf(AppIcons.bundledIds());
        for (String file : new String[]{"LICENSE.txt", "NOTICE.txt", "SOURCE.md"})
            try (var in = getClass().getResourceAsStream("/" + BASE + file)) { assertThat(in).as(file).isNotNull(); }
        try (var in = getClass().getResourceAsStream("/" + BASE + "LICENSE.txt")) {
            String license = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(license.stripLeading()).startsWith("Apache License");
            assertThat(license).contains("Version 2.0, January 2004");
        }
    }

    @Test void completeCatalogParsesAndPaintsInBothThemesAtBothScales() {
        var themes = new ThemeController(Theme.LIGHT);
        try {
            for (var theme : List.of(Theme.LIGHT, Theme.DARK)) {
                themes.configure(theme);
                for (String id : AppIcons.bundledIds()) {
                    var raw = new FlatSVGIcon(BASE + id + ".svg");
                    assertThat(raw.hasFound()).as(id).isTrue();
                    var icon = AppIcons.bundled(id);
                    assertThat(icon.getIconWidth()).isEqualTo(16);
                    assertThat(icon.getIconHeight()).isEqualTo(16);
                    for (int scale : new int[]{1, 2}) {
                        var image = new BufferedImage(16 * scale, 16 * scale, BufferedImage.TYPE_INT_ARGB);
                        var graphics = image.createGraphics();
                        try { graphics.scale(scale, scale); icon.paintIcon(new JLabel(), graphics, 0, 0); }
                        finally { graphics.dispose(); }
                    }
                }
            }
        } finally { new ThemeController(); }
    }

    @Test void nonSquareArtworkIsCenteredWithoutStretching() {
        var themes = new ThemeController(Theme.LIGHT);
        try {
            for (var theme : List.of(Theme.LIGHT, Theme.DARK)) {
                themes.configure(theme);
                var raw = new FlatSVGIcon(BASE + "breakpoints/questionBadge.svg");
                var expected = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                var graphics = expected.createGraphics();
                try {
                    graphics.translate(2.4, 0);
                    graphics.scale(1.6, 1.6);
                    raw.paintIcon(new JLabel(), graphics, 0, 0);
                } finally { graphics.dispose(); }
                assertThat(pixels(AppIcons.bundled("breakpoints/questionBadge")))
                    .isEqualTo(expected.getRGB(0, 0, 16, 16, null, 0, 16));
            }
        } finally { new ThemeController(); }
    }

    @Test void retainedIconKeepsProportionsWhenDarkArtworkHasDifferentDimensions() {
        var theme = new ThemeController(Theme.LIGHT);
        try {
            var icon = AppIcons.bundled("welcome/open");
            pixels(icon);
            theme.configure(Theme.DARK);
            var raw = new FlatSVGIcon(BASE + "welcome/open.svg");
            assertThat(raw.getIconWidth()).isEqualTo(21);
            assertThat(raw.getIconHeight()).isEqualTo(20);
            var expected = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            var graphics = expected.createGraphics();
            try {
                double scale = 16.0 / 21;
                graphics.translate(0, (16 - scale * 20) / 2);
                graphics.scale(scale, scale);
                raw.paintIcon(new JLabel(), graphics, 0, 0);
            } finally { graphics.dispose(); }
            assertThat(pixels(icon)).isEqualTo(expected.getRGB(0, 0, 16, 16, null, 0, 16));
        } finally { new ThemeController(); }
    }

    @Test void catalogRejectsPathsAndUnknownIdsAndCachesSelections() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.bundled("../general/add"));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.bundled("general/add.svg"));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.bundled(null));
        assertThat(AppIcons.bundled("general/add")).isSameAs(AppIcons.bundled("general/add"));
    }

    @Test void flatLafRemapsIntellijPaletteColoursForTheRunningTheme() {
        var themes = new ThemeController(Theme.LIGHT);
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
