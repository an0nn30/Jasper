package dev.jasper.ui.icons;

import dev.jasper.ui.theme.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IconRegistryTest {
    @Test void everySourceNameRendersInEverySizeThemeAndSelection() {
        try (var registry = new IconRegistry()) {
            assertThat(registry.names()).contains("add", "more", "collapse", "search", "lock", "edit", "delete", "copy", "reveal",
                "refresh", "upload", "back", "forward", "folder", "file", "server", "key", "login", "note", "tunnel", "terminal",
                "play", "warning", "info", "check", "close", "chevron.down", "chevron.up", "sparkle");
            for (var name : registry.names()) for (int size : new int[]{12, 14, 16, 20, 24})
                for (var theme : java.util.List.of(KitFixtures.light(), KitFixtures.dark())) {
                    assertThat(pixels(registry.icon(name, theme.semantic(), Nodes.Hue.NONE, size))).isPositive();
                    assertThat(pixels(registry.tile(name, theme.semantic(), Nodes.Hue.BLUE, size, true))).isPositive();
                }
        }
    }
    @Test void semanticTintAndSelectedTileUseOneAlphaApplication() {
        try (var registry = new IconRegistry()) {
            var palette = KitFixtures.dark().semantic();
            BufferedImage icon = paint(registry.icon("add", palette, Nodes.Hue.BLUE, 16));
            assertThat(new Color(icon.getRGB(8, 8), true).getRGB() & 0xffffff)
                .isEqualTo(palette.color(SemanticColor.HUE_BLUE).getRGB() & 0xffffff);
            BufferedImage selected = paint(registry.tile("add", palette, Nodes.Hue.BLUE, 24, true));
            Color tile = new Color(selected.getRGB(4, 4), true);
            assertThat(tile.getRGB() & 0xffffff).isEqualTo(palette.color(SemanticColor.TEXT_ONACCENT).getRGB() & 0xffffff);
            assertThat(tile.getAlpha()).isEqualTo(46); // 18% rounded once
        }
    }
    @Test void registrationValidatesPathNamespaceAndLifetime() throws Exception {
        try (var registry = new IconRegistry()) {
            assertThatThrownBy(() -> registry.register("add", "M 1 1 L 15 15")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registry.register("plugin:test", "M 1 1 L")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registry.register("plugin:test", "<svg fill='red'/>")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registry.register("plugin:test", "M 1 1 L 100 2")).isInstanceOf(IllegalArgumentException.class);
            var handle = registry.register("plugin:test", "M 1 1 C 3 1 4 2 4 4 Q 8 12 12 4 A 2 2 0 0 1 14 6 Z");
            assertThat(registry.names()).contains("plugin:test");
            assertThatThrownBy(() -> registry.register("plugin:test", "M 1 1 L 15 15")).isInstanceOf(IllegalArgumentException.class);
            handle.close(); handle.close();
            assertThat(registry.names()).doesNotContain("plugin:test");
            assertThatThrownBy(() -> registry.icon("plugin:test", KitFixtures.dark().semantic(), Nodes.Hue.NONE, 16))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void strictPathGrammarMetadataAndBaseAlphaAreValidated() {
        for (String bad : java.util.List.of("M 1 1 Z 2 3", "M 1 1 L 4 4 junk", "M 1 1 A 2 2 0 2 0 3 3", "M 1 1 L 1e999 2", "M 1 1 L NaN 3", "M 1 1 L 2 3 #red", "M,1,1 L 2 2", "M 1,,1 L 2 2", "M 1 1 L 2 2,"))
            assertThatThrownBy(() -> new IconArtwork(bad)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IconArtwork("M 1 1 L 15 15", 24, 1.5f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IconArtwork("M 1 1 L 15 15", 16, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new IconArtwork("m 2 2 h 3 v 3 l 2 2 q 1 1 2 0 t 2 0 c 1 0 1 1 1 2 s 0 1 -1 1 z"))
            .doesNotThrowAnyException();
        assertThatCode(() -> new IconArtwork("M 2 8 A 6 6 0 0114 8 A 6 6 0 012 8"))
            .doesNotThrowAnyException();
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, KitFixtures.dark().semantic().color(role));
        colors.put(SemanticColor.HUE_BLUE, new Color(10, 20, 30, 128));
        try (var registry = new IconRegistry()) {
            assertThat(new Color(paint(registry.tile("add", new SemanticPalette(colors), Nodes.Hue.BLUE, 24, false)).getRGB(4, 4), true).getAlpha())
                .isEqualTo(20); // 128 * 16%, once
        }
    }
    private static BufferedImage paint(Icon icon) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); } return image;
    }
    private static int pixels(Icon icon) { var image = paint(icon); int count = 0;
        for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++) if((image.getRGB(x,y) >>> 24) > 0) count++;
        return count;
    }
}
