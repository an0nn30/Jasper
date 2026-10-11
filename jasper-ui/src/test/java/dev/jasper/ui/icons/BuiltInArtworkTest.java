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
    @Test void preparationIsAtomicIdempotentAndReportsRequiredResourceHashFailures() {
        IconRegistry.prepareBuiltIns();var catalog=BuiltInArtwork.catalog();IconRegistry.prepareBuiltIns();assertThat(BuiltInArtwork.catalog()).isSameAs(catalog).hasSize(5);
        assertThatThrownBy(()->BuiltInArtwork.decodeRequired("missing.svg",null,"expected-hash")).hasMessageContaining("missing.svg").hasMessageContaining("expected-hash");
        assertThatThrownBy(()->BuiltInArtwork.decodeRequired("broken.svg",new byte[]{1},"expected-hash")).hasMessageContaining("broken.svg").hasMessageContaining("expected-hash");
    }
    @Test void originalGeometryLayersAndSiblingColorsMatchDirectSourceAtActualSlots() throws Exception {
        var files=Map.of("settings","general/settings","network","toolwindows/web","tunnel","actions/swapPanels","more.vertical","general/moreVertical","chevron.right","general/chevronRight");
        try(var registry=new IconRegistry()) {
            for(var entry:files.entrySet())for(var theme:List.of(KitFixtures.light(),KitFixtures.dark())) {
                String file=entry.getValue()+(theme.dark()?"_dark.svg":".svg");
                byte[] bytes;try(var in=getClass().getResourceAsStream(file)){bytes=in.readAllBytes();}
                var direct=new com.github.weisj.jsvg.parser.SVGLoader().load(new java.io.ByteArrayInputStream(bytes),null,com.github.weisj.jsvg.parser.LoaderContext.builder().build());
                for(int size:new int[]{12,14,16,20,24,30,40})for(int scale:new int[]{1,2}) {
                    var actual=pixels(registry.icon(entry.getKey(),theme,Nodes.Hue.PURPLE,size),scale);
                    var source=new BufferedImage(size*scale,size*scale,BufferedImage.TYPE_INT_ARGB);var g=source.createGraphics();
                    try {g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);direct.render(null,g,new com.github.weisj.jsvg.view.ViewBox(0,0,size*scale,size*scale));}finally{g.dispose();}
                    assertThat(actual).as("%s at %s x %s",file,size,scale).isEqualTo(source.getRGB(0,0,source.getWidth(),source.getHeight(),null,0,source.getWidth()));
                }
            }
        }
    }
    @Test void themeFlagIsAuthoritativeEvenWhenSemanticPalettesAreEqualAndLegacyContextsStaySeparate() {
        var light=KitFixtures.light();var dark=new GeneratedTheme(light.name(),true,light.author(),light.tokens(),light.syntax(),light.terminal(),light.uiKeys(),light.icons(),light.glass(),light.semantic());
        try(var registry=new IconRegistry()) {
            assertThat(pixels(registry.icon("settings",light,Nodes.Hue.NONE,16),1)).isNotEqualTo(pixels(registry.icon("settings",dark,Nodes.Hue.NONE,16),1));
            assertThat(pixels(registry.panel("settings",light,SemanticColor.TEXT_PRIMARY,16),1)).isEqualTo(pixels(registry.panel("settings",light.semantic(),SemanticColor.TEXT_PRIMARY,16),1));
            for(String name:registry.names()) {
                assertThatCode(()->{registry.icon(name,light.semantic(),Nodes.Hue.NONE,16);registry.panel(name,light.semantic(),SemanticColor.TEXT_PRIMARY,16);registry.hero(name,light.semantic(),Nodes.Hue.NONE,24);registry.tile(name,light.semantic(),Nodes.Hue.BLUE,28,false);}).as(name).doesNotThrowAnyException();
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
}
