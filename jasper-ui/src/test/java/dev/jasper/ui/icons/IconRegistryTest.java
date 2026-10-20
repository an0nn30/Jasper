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
    @Test void serverArtworkHasTwoOutlinedRacksAndOnlyLeftIndicators() {
        try(var registry=new IconRegistry()) {
            for(int scale:new int[]{1,2}) {
                var image=paint(registry.icon("server",KitFixtures.dark().semantic(),Nodes.Hue.NEUTRAL,16*scale));
                assertThat(image.getRGB(10*scale,4*scale)>>>24).as("upper rack right interior is clear").isZero();
                assertThat(image.getRGB(10*scale,11*scale)>>>24).as("lower rack right interior is clear").isZero();
                assertThat(image.getRGB(5*scale,4*scale)>>>24).isPositive();
                assertThat(image.getRGB(2*scale,4*scale)>>>24).isPositive();
            }
        }
    }
    @Test void categoryOpticalFloorAndCapFollowUserScaleExactlyOnce() {
        dev.jasper.ui.testsupport.Edt.run(() -> {
            float previous = com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
            try (var registry = new IconRegistry()) {
                var setter = com.formdev.flatlaf.util.UIScale.class.getDeclaredMethod("setUserScaleFactor", float.class, boolean.class);
                setter.setAccessible(true);
                try {
                    for (int logical : new int[]{18, 44}) {
                        int baseline = 0;
                        for (float scale : new float[]{1f, 1.5f}) {
                            setter.invoke(null, scale, false);
                            var icon = registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, "user", KitFixtures.dark().semantic(), Nodes.Hue.BLUE, com.formdev.flatlaf.util.UIScale.scale(logical), false);
                            var image = paint(icon); int left=image.getWidth(), right=-1;
                            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>200){left=Math.min(left,x);right=Math.max(right,x);}
                            int ink=right-left+1;
                            if(scale==1)baseline=ink;
                            else assertThat(ink).as("%s logical tile at UI scale %s",logical,scale).isBetween(Math.round(baseline*scale)-1,Math.round(baseline*scale)+1);
                        }
                    }
                } finally { setter.invoke(null, previous, false); }
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }
    @Test void categoryGlyphInkMatchesItsMeasuredInsetWithinTheTile() {
        try(var registry=new IconRegistry()) {
            for(int size:new int[]{18,20,28,32,44}) {
                var image=paint(registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, "user",KitFixtures.dark().semantic(),Nodes.Hue.BLUE,size,false));
                int left=size,right=-1;
                for(int y=0;y<size;y++)for(int x=0;x<size;x++)if((image.getRGB(x,y)>>>24)>200){left=Math.min(left,x);right=Math.max(right,x);}
                assertThat(right-left+1).isBetween(size<=20?7:size==28?9:size==32?10:13,size<=20?9:size==28?11:size==32?12:16);
            }
        }
    }
    @Test void largeCategoryGlyphUsesMeasuredOpticalCapAtDeviceScaleTwo() {
        try(var registry=new IconRegistry()) {
            var colors=new EnumMap<SemanticColor,Color>(SemanticColor.class);for(var role:SemanticColor.values())colors.put(role,KitFixtures.dark().semantic().color(role));
            colors.put(SemanticColor.HUE_BLUE,new Color(127,178,255));colors.put(SemanticColor.HUE_BLUE_SURFACE,new Color(48,124,243));
            var icon=registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, "user",new SemanticPalette(colors),Nodes.Hue.BLUE,44,false);
            var image=new BufferedImage(88,88,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(new Color(31,35,43));g.fillRect(0,0,88,88);g.scale(2,2);icon.paintIcon(null,g,0,0);g.dispose();
            int left=88,right=-1;for(int y=0;y<88;y++)for(int x=0;x<88;x++){var color=new Color(image.getRGB(x,y));if(color.getGreen()>105&&color.getBlue()>155&&color.getBlue()-color.getRed()>35){left=Math.min(left,x);right=Math.max(right,x);}}
            assertThat(right-left+1).isEqualTo(28);
        }
    }
    @Test void promptOpticalStrokeIsExplicitAndControlArtworkKeepsItsRegisteredStroke() {
        try (var registry = new IconRegistry()) {
            registry.register("test:line", "M 2 8 H 14");
            var palette = KitFixtures.dark().semantic();
            assertThat(strokeAt2x(registry.prompt("test:line", palette, Nodes.Hue.BLUE, 24))).isBetween(3.4, 3.6);
            assertThat(strokeAt2x(registry.icon("test:line", palette, Nodes.Hue.BLUE, 24))).isBetween(4.4, 4.6);
            assertThat(strokeAt2x(registry.icon("test:line", palette, Nodes.Hue.BLUE, 14))).isBetween(2.5, 2.75);
        }
    }
    private static double strokeAt2x(Icon icon) {
        var image = new BufferedImage(icon.getIconWidth()*2, icon.getIconHeight()*2, BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics(); try { g.scale(2,2); icon.paintIcon(null,g,0,0); } finally { g.dispose(); }
        double alpha=0; for(int y=0;y<image.getHeight();y++) alpha += (image.getRGB(image.getWidth()/2,y) >>> 24) / 255.0;
        return alpha;
    }
    @Test void sharedBuiltinArtworkSurvivesIndependentRegistryAndCustomLifetimes() {
        var first=new IconRegistry();
        try(var second=new IconRegistry()) {
            first.register("owner:custom","M 2 2 L 14 14");
            assertThat(second.names()).doesNotContain("owner:custom");
            var before=paint(second.icon("file",KitFixtures.dark().semantic(),Nodes.Hue.NONE,16));
            first.close();
            assertThat(second.names()).contains("file").doesNotContain("owner:custom");
            var after=paint(second.icon("file",KitFixtures.dark().semantic(),Nodes.Hue.NONE,16));
            assertThat(after.getRGB(0,0,16,16,null,0,16)).containsExactly(before.getRGB(0,0,16,16,null,0,16));
        }
    }
    @Test void filledFolderUsesExplicitBuiltinPaintWithoutChangingOutlineRegistrations() {
        try(var registry=new IconRegistry()) {
            registry.register("test:folder","M 2 4 H 6 L 8 6 H 14 V 13 H 2 Z");
            for(var theme:java.util.List.of(KitFixtures.dark(),KitFixtures.light())) for(int scale:new int[]{1,2}) {
                var filled=paint(registry.icon("folder.filled",theme.semantic(),SemanticColor.TEXT_PLACEHOLDER,16*scale));
                var outline=paint(registry.icon("folder",theme.semantic(),SemanticColor.TEXT_PLACEHOLDER,16*scale));
                var custom=paint(registry.icon("test:folder",theme.semantic(),SemanticColor.TEXT_PLACEHOLDER,16*scale));
                assertThat(filled.getRGB(8*scale,9*scale)).isEqualTo(theme.semantic().color(SemanticColor.TEXT_PLACEHOLDER).getRGB());
                assertThat(filled.getRGB(0,0)>>>24).isZero();
                assertThat(outline.getRGB(8*scale,9*scale)>>>24).isZero();
                assertThat(custom.getRGB(0,0,16*scale,16*scale,null,0,16*scale)).containsExactly(outline.getRGB(0,0,16*scale,16*scale,null,0,16*scale));
            }
        }
    }
    @Test void everySourceNameRendersInEverySizeThemeAndSelection() {
        try (var registry = new IconRegistry()) {
            assertThat(registry.names()).contains("none", "add", "more", "collapse", "search", "lock", "edit", "delete", "copy", "reveal",
                "refresh", "upload", "back", "forward", "folder", "file", "server", "key", "login", "note", "tunnel", "terminal",
                "play", "warning", "info", "check", "close", "chevron.down", "chevron.up", "sparkle");
            for (var name : registry.names()) for (int size : new int[]{12, 14, 16, 20, 24})
                for (var theme : java.util.List.of(KitFixtures.light(), KitFixtures.dark())) {
                    assertThat(pixels(registry.icon(name, theme.semantic(), Nodes.Hue.NONE, size))).isPositive();
                    assertThat(pixels(registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, name, theme.semantic(), Nodes.Hue.BLUE, size, true))).isPositive();
                }
        }
    }
    @Test void semanticTintAndSelectedTileUseOneAlphaApplication() {
        try (var registry = new IconRegistry()) {
            var palette = KitFixtures.dark().semantic();
            BufferedImage icon = paint(registry.icon("add", palette, Nodes.Hue.BLUE, 16));
            assertThat(new Color(icon.getRGB(8, 8), true).getRGB() & 0xffffff)
                .isEqualTo(palette.color(SemanticColor.HUE_BLUE).getRGB() & 0xffffff);
            BufferedImage selected = paint(registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, "add", palette, Nodes.Hue.BLUE, 24, true));
            Color tile = new Color(selected.getRGB(4, 4), true);
            assertThat(tile.getRGB() & 0xffffff).isEqualTo(palette.color(SemanticColor.TEXT_ONACCENT).getRGB() & 0xffffff);
            assertThat(tile.getAlpha()).isEqualTo(43); // the Dark tile share, 17%, rounded once
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
        for (String bad : java.util.List.of("M 1 1 Z M 15 15 Z", "M 1 1 Z 2 3", "M 1 1 L 4 4 junk", "M 1 1 A 2 2 0 2 0 3 3", "M 1 1 L 1e999 2", "M 1 1 L NaN 3", "M 1 1 L 2 3 #red", "M,1,1 L 2 2", "M 1,,1 L 2 2", "M 1 1 L 2 2,"))
            assertThatThrownBy(() -> new IconArtwork(bad)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IconArtwork("M 1 1 L 15 15", 24, 1.5f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IconArtwork("M 1 1 L 15 15", 16, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new IconArtwork("m 2 2 h 3 v 3 l 2 2 q 1 1 2 0 t 2 0 c 1 0 1 1 1 2 s 0 1 -1 1 z"))
            .doesNotThrowAnyException();
        assertThatCode(() -> new IconArtwork("M 2 8 A 6 6 0 0114 8 A 6 6 0 012 8"))
            .doesNotThrowAnyException();
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, KitFixtures.dark().semantic().color(role));
        colors.put(SemanticColor.HUE_BLUE_SURFACE, new Color(10, 20, 30, 128));
        try (var registry = new IconRegistry()) {
            assertThat(new Color(paint(registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, "add", new SemanticPalette(colors), Nodes.Hue.BLUE, 24, false)).getRGB(4, 4), true).getAlpha())
                .isEqualTo(22); // 128 * 17% (Dark), once
        }
    }
    @Test void panelOpticsPreserveAuthoredCustomStrokeAndRectangularTileGeometryAtEveryScale() {
        try(var registry=new IconRegistry()) {
            registry.register("owner:line","M2 8 L14 8");
            var palette=KitFixtures.dark().semantic();
            for(int scale:java.util.List.of(1,2,3)) {
                var ordinary=paint(registry.icon("owner:line",palette,SemanticColor.HUE_NEUTRAL,16*scale));
                var panel=paint(registry.panel("owner:line",palette,SemanticColor.HUE_NEUTRAL,16*scale));
                assertThat(panel.getRGB(0,0,16*scale,16*scale,null,0,16*scale)).containsExactly(ordinary.getRGB(0,0,16*scale,16*scale,null,0,16*scale));
                var tile=registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, "server",palette,Nodes.Hue.NEUTRAL,28*scale,30*scale,14*scale,false);
                assertThat(tile.getIconWidth()).isEqualTo(28*scale);assertThat(tile.getIconHeight()).isEqualTo(30*scale);
            }
            assertThat(pixels(registry.panel("add",palette,SemanticColor.HUE_NEUTRAL,18))).isLessThan(pixels(registry.icon("add",palette,SemanticColor.HUE_NEUTRAL,18)));
        }
    }
    /** Enterprise presentation plan 1: a tile's opacity and an icon's size are the passed catalog's, not a fixed one. */
    @Test void sizesAndTileOpacityFollowTheCatalogTheCallerPasses() {
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, KitFixtures.dark().semantic().color(role));
        colors.put(SemanticColor.HUE_BLUE_SURFACE, new Color(10, 20, 30, 128));
        var catalog = dev.jasper.ui.metrics.DesignMetrics.DEFAULT.with(dev.jasper.ui.metrics.DesignMetrics.Role.TILE_PERCENT_DARK, 50)
            .with(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_CONTROL, 18);
        try (var icons = new IconRegistry()) {
            assertThat(new Color(paint(icons.tile(catalog, "add", new SemanticPalette(colors), Nodes.Hue.BLUE, 24, false)).getRGB(4, 4), true).getAlpha())
                .isEqualTo(64); // 128 * 50%, once
            assertThat(icons.size(catalog, Nodes.IconSize.CONTROL)).isEqualTo(18);
            assertThat(icons.size(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, Nodes.IconSize.CONTROL)).isEqualTo(14);
        }
    }

    private static BufferedImage paint(Icon icon) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); } return image;
    }
    @Test void surfaceTintKeepsAReadableForegroundAndLiftsAFaintOneToTheNonTextFloor() {
        var white = new Color(0xfafafa);
        assertThat(IconRegistry.surfaceTint(white, new Color(0x383a42))).isEqualTo(new Color(0x383a42));
        var lifted = IconRegistry.surfaceTint(white, new Color(0xe6edf5));
        assertThat(ColorMath.contrast(lifted, white)).isGreaterThanOrEqualTo(3);
        var dark = new Color(0x18202b);
        assertThat(ColorMath.contrast(IconRegistry.surfaceTint(dark, new Color(0x202734)), dark)).isGreaterThanOrEqualTo(3);
        try (var registry = new IconRegistry()) {
            assertThat(pixels(registry.icon("terminal", white, new Color(0xe6edf5), 16))).isPositive();
        }
    }

    private static int pixels(Icon icon) { var image = paint(icon); int count = 0;
        for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++) if((image.getRGB(x,y) >>> 24) > 0) count++;
        return count;
    }
}
