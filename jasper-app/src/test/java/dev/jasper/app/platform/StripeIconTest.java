package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.icons.ChromeArtwork;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

/** Plan 13: the icon a labelled stripe's button draws for a panel's or action's icon (spec 10.7). */
@ExtendWith(EdtTestExtension.class)
class StripeIconTest {
    static final java.util.function.Function<Component, Color> INK = c -> new Color(0xD0, 0x30, 0x30);
    static final Map<String, String> SET_A = Map.of("network", "network", "folder", "folder", "tunnel", "tunnel",
        "download", "download", "server", "server", "bell", "bell");
    private static final String MARK_PATH = "M 2 2 L 14 14 M 2 14 L 14 2";

    @Test void underClassicSetANamesDrawTheirToolWindowFileUntinted() {
        var themes = ThemeTestSupport.controller(true);
        try (var registry = new IconRegistry()) {
            for (boolean dark : new boolean[] {false, true}) {
                var theme = ThemeTestSupport.generated(dark);
                var label = zone(theme);
                for (var entry : SET_A.entrySet()) {
                    var icon = AppIcons.stripeIcon(new PresentationIcon(entry.getKey(), null), ChromeArtwork.CLASSIC, 13, INK).orElseThrow();
                    assertThat(icon.getIconWidth()).isEqualTo(UIScale.scale(13));
                    assertThat(icon.getIconHeight()).isEqualTo(UIScale.scale(13));
                    assertThat(pixels(icon, label)).as("%s dark=%s", entry.getKey(), dark)
                        .isEqualTo(pixels(registry.stripeIcon(entry.getValue(), 13).orElseThrow().icon(theme, SemanticColor.TEXT_PRIMARY), label));
                }
                for (var named : Map.of("bell", AppIcons.semantic("bell"), "folder", AppIcons.named("FOLDER")).entrySet()) {
                    var icon = AppIcons.stripeIcon(named.getValue(), ChromeArtwork.CLASSIC, 13, INK).orElseThrow();
                    assertThat(pixels(icon, label)).as("%s by its kit name, dark=%s", named.getKey(), dark)
                        .isEqualTo(pixels(registry.stripeIcon(named.getKey(), 13).orElseThrow().icon(theme, SemanticColor.TEXT_PRIMARY), label));
                }
            }
        } finally { themes.close(); ThemeTestSupport.controller(true); }
    }

    /** Review Focus 2. */
    @Test void theClassicArtFollowsTheReceivingTheme() {
        var themes = ThemeTestSupport.controller(true);
        try (var registry = new IconRegistry()) {
            var light = ThemeTestSupport.generated(false);
            var dark = ThemeTestSupport.generated(true);
            var icon = AppIcons.stripeIcon(new PresentationIcon("server", null), ChromeArtwork.CLASSIC, 13, INK).orElseThrow();
            var inLight = pixels(icon, zone(light));
            var inDark = pixels(icon, zone(dark));
            var prepared = registry.stripeIcon("server", 13).orElseThrow();
            assertThat(inLight).isEqualTo(pixels(prepared.icon(light, SemanticColor.TEXT_PRIMARY), zone(light)));
            assertThat(inDark).isEqualTo(pixels(prepared.icon(dark, SemanticColor.TEXT_PRIMARY), zone(dark)));
            assertThat(inLight).isNotEqualTo(inDark);
        } finally { themes.close(); ThemeTestSupport.controller(true); }
    }

    /** Review Focus 1. */
    @Test void aPluginPathDrawsItsOwnGlyphInTheLabelInk() {
        var themes = ThemeTestSupport.controller(true);
        try {
            var mark = new PresentationIcon("test:mark", MARK_PATH);
            for (boolean dark : new boolean[] {false, true}) {
                var theme = ThemeTestSupport.generated(dark);
                var label = zone(theme);
                var classic = AppIcons.stripeIcon(mark, ChromeArtwork.CLASSIC, 13, INK).orElseThrow();
                assertThatCode(() -> pixels(classic, label)).doesNotThrowAnyException();
                assertThat(pixels(classic, label)).as("classic dark=%s", dark)
                    .isEqualTo(pixels(mark.glyph(theme.semantic(), INK.apply(label), UIScale.scale(13)), label));
                var newUi = AppIcons.stripeIcon(mark, ChromeArtwork.NEW_UI, 13, INK).orElseThrow();
                assertThat(pixels(newUi, label)).as("New UI dark=%s", dark)
                    .isEqualTo(pixels(mark.ordinary(theme, UIScale.scale(13)), label));
            }
        } finally { themes.close(); ThemeTestSupport.controller(true); }
    }

    /** Review Focus 1: a header strip's wrapper around a plugin glyph is unwrapped, never resolved by its name alone. */
    @Test void aStripGlyphAroundAPluginPathDrawsThatGlyph() {
        var themes = ThemeTestSupport.controller(true);
        try {
            var mark = new PresentationIcon("test:mark", MARK_PATH);
            var theme = ThemeTestSupport.generated(true);
            var label = zone(theme);
            var wrapped = AppIcons.stripeIcon(AppIcons.stripGlyph(mark, 10, INK), ChromeArtwork.CLASSIC, 13, INK).orElseThrow();
            assertThat(wrapped.getIconWidth()).isEqualTo(UIScale.scale(13));
            assertThatCode(() -> pixels(wrapped, label)).doesNotThrowAnyException();
            assertThat(pixels(wrapped, label))
                .isEqualTo(pixels(AppIcons.stripeIcon(mark, ChromeArtwork.CLASSIC, 13, INK).orElseThrow(), label))
                .isEqualTo(pixels(mark.glyph(theme.semantic(), INK.apply(label), UIScale.scale(13)), label));
        } finally { themes.close(); ThemeTestSupport.controller(true); }
    }

    @Test void aNameWithoutStripeArtDrawsItsMonochromeGlyphUnderClassic() {
        var themes = ThemeTestSupport.controller(true);
        try (var registry = new IconRegistry()) {
            for (boolean dark : new boolean[] {false, true}) {
                var theme = ThemeTestSupport.generated(dark);
                var label = zone(theme);
                for (String name : List.of("history", "key", "more.vertical")) {
                    var icon = AppIcons.stripeIcon(new PresentationIcon(name, null), ChromeArtwork.CLASSIC, 13, INK).orElseThrow();
                    assertThat(pixels(icon, label)).as("%s dark=%s", name, dark)
                        .isEqualTo(pixels(registry.panel(name, theme.semantic(), INK.apply(label), UIScale.scale(13)), label));
                }
            }
        } finally { themes.close(); ThemeTestSupport.controller(true); }
    }

    @Test void underNewUiTheStripeDrawsWhatTheRailsDraw() {
        var themes = ThemeTestSupport.controller(true);
        try (var registry = new IconRegistry()) {
            for (boolean dark : new boolean[] {false, true}) {
                var theme = ThemeTestSupport.generated(dark);
                var label = zone(theme);
                assertThat(pixels(AppIcons.stripeIcon(new PresentationIcon("folder", null), ChromeArtwork.NEW_UI, 13, INK).orElseThrow(), label))
                    .as("folder dark=%s", dark)
                    .isEqualTo(pixels(registry.chromeIcon("folder", 13).icon(theme, SemanticColor.TEXT_SECONDARY), label));
                assertThat(pixels(AppIcons.stripeIcon(AppIcons.semantic("bell"), ChromeArtwork.NEW_UI, 13, INK).orElseThrow(), label))
                    .as("bell dark=%s", dark)
                    .isEqualTo(pixels(registry.chromeIcon("bell", 13).icon(theme, SemanticColor.TEXT_PRIMARY), label));
                assertThat(pixels(AppIcons.stripeIcon(new PresentationIcon("download", null), ChromeArtwork.NEW_UI, 13, INK).orElseThrow(), label))
                    .as("download dark=%s", dark)
                    .isEqualTo(pixels(registry.chromeIcon("download", 13).icon(theme, SemanticColor.TEXT_SECONDARY), label));
            }
        } finally { themes.close(); ThemeTestSupport.controller(true); }
    }

    @Test void noIconForNullOrAnEmptyIcon() {
        for (var artwork : ChromeArtwork.values()) {
            assertThat(AppIcons.stripeIcon(null, artwork, 13, INK)).isEmpty();
            assertThat(AppIcons.stripeIcon(new javax.swing.ImageIcon(), artwork, 13, INK)).isEmpty();
        }
    }

    @Test void anotherIconIsDrawnAsGivenScaledIntoTheBox() {
        int blue = 0xFF0000FF;
        var label = zone(ThemeTestSupport.generated(true));
        for (var artwork : ChromeArtwork.values()) {
            var large = AppIcons.stripeIcon(solid(26, blue), artwork, 13, INK).orElseThrow();
            assertThat(large.getIconWidth()).isEqualTo(13);
            assertThat(large.getIconHeight()).isEqualTo(13);
            assertThat(pixels(large, label)).as("%s: the whole box", artwork).containsOnly(blue);
            // Painted at (3, 3) on a larger canvas, nothing lands outside the box.
            var canvas = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            var g = canvas.createGraphics();
            try { large.paintIcon(label, g, 3, 3); } finally { g.dispose(); }
            for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) {
                boolean inside = x >= 3 && x < 16 && y >= 3 && y < 16;
                assertThat(canvas.getRGB(x, y)).as("%s (%d, %d)", artwork, x, y).isEqualTo(inside ? blue : 0);
            }
            var small = AppIcons.stripeIcon(solid(9, blue), artwork, 13, INK).orElseThrow();
            assertThat(small.getIconWidth()).isEqualTo(13);
            var painted = pixels(small, label);
            for (int y = 0; y < 13; y++) for (int x = 0; x < 13; x++) {
                boolean inside = x >= 2 && x < 11 && y >= 2 && y < 11;
                assertThat(painted[y * 13 + x]).as("%s centred (%d, %d)", artwork, x, y).isEqualTo(inside ? blue : 0);
            }
        }
    }

    /** A label inside a workspace zone painting with {@code theme}. */
    private static JLabel zone(GeneratedTheme theme) {
        var zone = new JPanel();
        zone.putClientProperty("Jasper.zone", "workspace");
        zone.putClientProperty("Jasper.workspaceTheme", theme);
        var label = new JLabel();
        zone.add(label);
        return label;
    }

    private static ImageIcon solid(int size, int argb) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) image.setRGB(x, y, argb);
        return new ImageIcon(image);
    }

    private static int[] pixels(Icon icon, Component component) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { icon.paintIcon(component, graphics, 0, 0); } finally { graphics.dispose(); }
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
