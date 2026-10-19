package dev.jasper.ui.icons;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BuiltInArtworkTest {
    static int[] pixels(Icon icon,int scale) {
        var image=new BufferedImage(icon.getIconWidth()*scale,icon.getIconHeight()*scale,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();try{g.scale(scale,scale);icon.paintIcon(null,g,0,0);}finally{g.dispose();}
        return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
    /** The bundled file rendered directly by jsvg into a {@code size * scale} square, as the registry's artwork must paint it. */
    static int[] direct(String file,int size,int scale) throws Exception {
        byte[] bytes;try(var in=BuiltInArtworkTest.class.getResourceAsStream(file)){bytes=in.readAllBytes();}
        var direct=new com.github.weisj.jsvg.parser.SVGLoader().load(new java.io.ByteArrayInputStream(bytes),null,com.github.weisj.jsvg.parser.LoaderContext.builder().build());
        var source=new BufferedImage(size*scale,size*scale,BufferedImage.TYPE_INT_ARGB);var g=source.createGraphics();
        try {g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);direct.render(null,g,new com.github.weisj.jsvg.view.ViewBox(0,0,size*scale,size*scale));}finally{g.dispose();}
        return source.getRGB(0,0,source.getWidth(),source.getHeight(),null,0,source.getWidth());
    }
    @Test void preparationIsAtomicIdempotentAndReportsRequiredResourceHashFailures() {
        IconRegistry.prepareBuiltIns();var catalog=BuiltInArtwork.catalog();IconRegistry.prepareBuiltIns();assertThat(BuiltInArtwork.catalog()).isSameAs(catalog).hasSize(9);
        assertThatThrownBy(()->BuiltInArtwork.decodeRequired("missing.svg",null,"expected-hash")).hasMessageContaining("missing.svg").hasMessageContaining("expected-hash");
        assertThatThrownBy(()->BuiltInArtwork.decodeRequired("broken.svg",new byte[]{1},"expected-hash")).hasMessageContaining("broken.svg").hasMessageContaining("expected-hash");
    }
    @Test void originalGeometryLayersAndSiblingColorsMatchDirectSourceAtActualSlots() throws Exception {
        var files=Map.of("settings","general/settings","network","toolwindows/web","tunnel","actions/swapPanels","more.vertical","general/moreVertical","chevron.right","general/chevronRight","search","general/search","file","fileTypes/text");
        try(var registry=new IconRegistry()) {
            for(var entry:files.entrySet())for(var theme:List.of(KitFixtures.light(),KitFixtures.dark())) {
                String file=entry.getValue()+(theme.dark()?"_dark.svg":".svg");
                for(int size:new int[]{12,14,16,20,24,30,40})for(int scale:new int[]{1,2}) {
                    var actual=pixels(registry.icon(entry.getKey(),theme,Nodes.Hue.PURPLE,size),scale);
                    assertThat(actual).as("%s at %s x %s",file,size,scale).isEqualTo(direct(file,size,scale));
                }
            }
        }
    }
    @Test void themeFlagIsAuthoritativeEvenWhenSemanticPalettesAreEqualAndLegacyContextsStaySeparate() {
        var light=KitFixtures.light();var dark=KitFixtures.withDark(light, true);
        try(var registry=new IconRegistry()) {
            assertThat(pixels(registry.icon("settings",light,Nodes.Hue.NONE,16),1)).isNotEqualTo(pixels(registry.icon("settings",dark,Nodes.Hue.NONE,16),1));
            assertThat(pixels(registry.panel("settings",light,SemanticColor.TEXT_PRIMARY,16),1)).isEqualTo(pixels(registry.panel("settings",light.semantic(),SemanticColor.TEXT_PRIMARY,16),1));
            for(String name:registry.names()) {
                assertThatCode(()->{registry.icon(name,light.semantic(),Nodes.Hue.NONE,16);registry.panel(name,light.semantic(),SemanticColor.TEXT_PRIMARY,16);registry.prompt(name,light.semantic(),Nodes.Hue.NONE,24);registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, name,light.semantic(),Nodes.Hue.BLUE,28,false);}).as(name).doesNotThrowAnyException();
            }
        }
    }
    @Test void preparedNativeSnapshotsSurviveCacheEvictionCloseAndNeverRefillTheSizeCacheDuringPaint() throws Exception {
        var registry=new IconRegistry();var prepared=registry.themedIcon("settings",16);
        var pair=BuiltInArtwork.catalog().get("settings");
        var field=DecodedArtwork.class.getDeclaredField("snapshots");field.setAccessible(true);
        for(int size=30;size<42;size++){pair.light().snapshot(size);pair.dark().snapshot(size);}
        var lightCache=(Map<?,?>)field.get(pair.light());var darkCache=(Map<?,?>)field.get(pair.dark());
        assertThat(lightCache.containsKey(16)).isFalse();assertThat(darkCache.containsKey(16)).isFalse();
        var beforeLight=new LinkedHashMap<>(lightCache);var beforeDark=new LinkedHashMap<>(darkCache);
        registry.close();var light=pixels(prepared.icon(KitFixtures.light(),SemanticColor.TEXT_PRIMARY),2);
        for(int n=0;n<50;n++) {
            assertThat(pixels(prepared.icon(KitFixtures.light(),SemanticColor.STATUS_DANGER),2)).isEqualTo(light);
            assertThat(pixels(prepared.icon(KitFixtures.dark(),SemanticColor.STATUS_DANGER),2)).isNotEqualTo(light);
        }
        assertThat(lightCache).isEqualTo(beforeLight);assertThat(darkCache).isEqualTo(beforeDark);
    }
    @Test void nativeChromeContextAloneReceivesOriginalDesktopArtworkForItsDepictedMeanings() throws Exception {
        var files=Map.of("add","general/add","window.add","actions/moveToWindow","split","general/splitVertically","zoom","inline/expand",
            "chevron.down","general/chevronDown","folder","nodes/folder","server","webReferences/server","bell","toolwindows/notifications",
            "lock","general/locked","terminal","terminal/toolwindow/terminal");
        assertThat(BuiltInArtwork.chrome()).hasSize(files.size()).containsOnlyKeys(files.keySet());
        try(var registry=new IconRegistry()) {
            for(var entry:files.entrySet())for(var theme:List.of(KitFixtures.light(),KitFixtures.dark())) {
                String file=entry.getValue()+(theme.dark()?"_dark.svg":".svg");
                for(int size:new int[]{8,16})for(int scale:new int[]{1,2}) {
                    var chrome=pixels(registry.chromeIcon(entry.getKey(),size).icon(theme,SemanticColor.TEXT_PRIMARY),scale);
                    assertThat(chrome).as("%s at %s x %s",file,size,scale).isEqualTo(direct(file,size,scale));
                    // Ordinary native, descriptor and panel contexts keep the engine glyph for the same meaning.
                    assertThat(pixels(registry.themedIcon(entry.getKey(),size).icon(theme,SemanticColor.TEXT_PRIMARY),scale)).as("ordinary %s",file).isNotEqualTo(chrome);
                    assertThat(pixels(registry.icon(entry.getKey(),theme,SemanticColor.TEXT_PRIMARY,size),scale)).as("descriptor %s",file).isNotEqualTo(chrome);
                }
            }
            // Meanings the chrome does not depict, including already-original catalog artwork, are exactly the ordinary preparation.
            for(String name:List.of("settings","search","more.vertical","copy"))
                assertThat(pixels(registry.chromeIcon(name,16).icon(KitFixtures.dark(),SemanticColor.TEXT_PRIMARY),2))
                    .as(name).isEqualTo(pixels(registry.themedIcon(name,16).icon(KitFixtures.dark(),SemanticColor.TEXT_PRIMARY),2));
        }
    }
    /** Rulings 3: the classic chrome set, its six meanings, and only CLASSIC receivers get it; others fall back to New UI. */
    @Test void theClassicChromeSetIsTheVendoredArtworkForItsSixMeanings() throws Exception {
        var files = Map.of("add", "classic/general/add", "split", "classic/actions/splitVertically", "zoom", "classic/general/expandComponent",
            "settings", "classic/general/settings", "window.add", "classic/actions/moveToWindow", "search", "classic/actions/find");
        assertThat(BuiltInArtwork.classicChrome()).containsOnlyKeys(files.keySet());
        try (var registry = new IconRegistry()) {
            for (var entry : files.entrySet()) for (var theme : List.of(KitFixtures.light(), KitFixtures.dark())) {
                boolean single = entry.getKey().equals("zoom");
                String file = entry.getValue() + (theme.dark() && !single ? "_dark.svg" : ".svg");
                for (int scale : new int[] {1, 2}) {
                    var classic = pixels(registry.chromeIcon(entry.getKey(), 16, ChromeArtwork.CLASSIC).icon(theme, SemanticColor.TEXT_PRIMARY), scale);
                    assertThat(classic).as("%s at %s", file, scale).isEqualTo(direct(file, 16, scale));
                    assertThat(pixels(registry.chromeIcon(entry.getKey(), 16, ChromeArtwork.NEW_UI).icon(theme, SemanticColor.TEXT_PRIMARY), scale))
                        .as("New UI %s", entry.getKey()).isNotEqualTo(classic);
                }
            }
            for (String name : List.of("folder", "bell", "terminal", "copy", "more.vertical"))
                assertThat(pixels(registry.chromeIcon(name, 16, ChromeArtwork.CLASSIC).icon(KitFixtures.dark(), SemanticColor.TEXT_PRIMARY), 2))
                    .as("%s falls back", name)
                    .isEqualTo(pixels(registry.chromeIcon(name, 16).icon(KitFixtures.dark(), SemanticColor.TEXT_PRIMARY), 2));
        }
    }

    /** Upstream files that ship without a copyright comment; still intellij-community's Apache-2.0 platform/icons module (plan 13 Rulings 3). */
    static final Set<String> NO_COMMENT = Set.of("classic/toolwindows/toolWindowServer.svg", "classic/toolwindows/toolWindowServer_dark.svg");

    /** Global Constraints: byte-identical upstream files, each with its JetBrains copyright comment where upstream has one. */
    @Test void everyClassicFileIsTheRecordedUpstreamFileWithItsCopyright() throws Exception {
        List<String> lines;
        try (var in = getClass().getResourceAsStream("assets-classic.tsv")) {
            lines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
        assertThat(lines).hasSize(26);
        for (String line : lines) {
            String[] columns = line.split("\t");
            assertThat(columns[1]).startsWith("platform/icons/src/").endsWith(columns[0].substring("classic/".length()));
            byte[] bytes;
            try (var in = getClass().getResourceAsStream(columns[0])) { bytes = in.readAllBytes(); }
            assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))).as(columns[0]).isEqualTo(columns[2]);
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (NO_COMMENT.contains(columns[0])) assertThat(text).as(columns[0] + ", shipped without a comment").startsWith("<svg").doesNotContain("Copyright");
            else assertThat(text).as(columns[0]).startsWith("<!-- Copyright 2000-").contains("JetBrains s.r.o.").contains("Apache 2.0");
        }
    }

    /** Plan 13 Rulings 4 and 5: the stripe table is Set A's six tool-window files, for labelled stripes only. */
    @Test void theClassicStripeIconsAreTheToolWindowFilesForSixNames() throws Exception {
        var files = Map.of("network", "classic/toolwindows/webToolWindow", "folder", "classic/toolwindows/toolWindowProject",
            "tunnel", "classic/toolwindows/toolWindowModuleDependencies", "download", "classic/toolwindows/toolWindowAnalyzeDataflow",
            "server", "classic/toolwindows/toolWindowServer", "bell", "classic/toolwindows/notifications");
        assertThat(BuiltInArtwork.classicStripes()).containsOnlyKeys(files.keySet());
        assertThat(BuiltInArtwork.classicChrome()).as("the header's classic set is unchanged").doesNotContainKeys(files.keySet().toArray(String[]::new));
        try (var registry = new IconRegistry()) {
            for (var entry : files.entrySet()) for (var theme : List.of(KitFixtures.light(), KitFixtures.dark())) {
                String file = entry.getValue() + (theme.dark() ? "_dark.svg" : ".svg");
                for (int scale : new int[] {1, 2}) {
                    var stripe = registry.stripeIcon(entry.getKey(), 13).orElseThrow().icon(theme, SemanticColor.STATUS_DANGER);
                    assertThat(pixels(stripe, scale)).as("%s at %s", file, scale).isEqualTo(direct(file, 13, scale));
                }
            }
            for (String name : List.of("add", "history", "more.vertical", "dev.jasper.sample:flask", "no-such-icon"))
                assertThat(registry.stripeIcon(name, 13)).as(name).isEmpty();
        }
    }

    /** Rulings 4: run and application are authored catalog artwork with vector fallbacks for every other context. */
    @Test void runAndApplicationAreAuthoredClassicArtworkWithVectorFallbacks() throws Exception {
        try (var registry = new IconRegistry()) {
            assertThat(registry.names()).contains("run", "application");
            assertThat(registry.authored("run")).isTrue();
            assertThat(registry.authored("application")).isTrue();
            for (var theme : List.of(KitFixtures.light(), KitFixtures.dark())) {
                assertThat(pixels(registry.icon("run", theme, SemanticColor.TEXT_PRIMARY, 16), 2))
                    .isEqualTo(direct(theme.dark() ? "classic/actions/execute_dark.svg" : "classic/actions/execute.svg", 16, 2));
                assertThat(pixels(registry.icon("application", theme, SemanticColor.TEXT_PRIMARY, 16), 2))
                    .isEqualTo(direct("classic/runConfigurations/application.svg", 16, 2));
            }
            for (String name : List.of("run", "application")) assertThatCode(() -> {
                registry.panel(name, KitFixtures.light().semantic(), SemanticColor.TEXT_PRIMARY, 16);
                registry.prompt(name, KitFixtures.light().semantic(), Nodes.Hue.NONE, 24);
                registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, name, KitFixtures.light().semantic(), Nodes.Hue.GREEN, 28, false);
            }).as(name).doesNotThrowAnyException();
        }
    }
}
