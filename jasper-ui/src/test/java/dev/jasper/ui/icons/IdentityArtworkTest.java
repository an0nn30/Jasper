package dev.jasper.ui.icons;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.testsupport.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IdentityArtworkTest {
    @Test void explicitSelectedInkUsesReceivingForegroundOnlyForSelectedTilesAtEveryResolution() throws Exception {
        var svg=ReceivingArtworkTest.svg("<rect x='2' y='1' width='8' height='8' fill='#ff2200'/><circle cx='14' cy='5' r='4' fill='#0022ff' opacity='.5'/>");
        var pngBytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(svg.decoded().image,"png",pngBytes);
        var png=new EncodedArtwork(EncodedArtwork.Format.PNG,pngBytes.toByteArray());
        for(var bytes:java.util.List.of(svg,png))try(var registry=new IconRegistry();var receiver=new ReceivingArtwork(registry)) {
            var legacy=new IdentityArtwork(bytes,Nodes.Hue.BLUE);
            var selected=new IdentityArtwork(bytes,Nodes.Hue.BLUE,IdentityArtwork.SelectedInk.FOREGROUND);
            String a=receiver.name(legacy),b=receiver.name(selected);
            assertThat(a).isNotEqualTo(b);
            var colors=new java.util.EnumMap<dev.jasper.ui.theme.SemanticColor,java.awt.Color>(dev.jasper.ui.theme.SemanticColor.class);
            for(var role:dev.jasper.ui.theme.SemanticColor.values())colors.put(role,KitFixtures.dark().semantic().color(role));
            colors.put(dev.jasper.ui.theme.SemanticColor.TEXT_ONACCENT,new java.awt.Color(0x23c976));
            var palette=new dev.jasper.ui.theme.SemanticPalette(colors);
            var retained=registry.tile(b,palette,Nodes.Hue.BLUE,40,40,20,true);
            for(int scale:new int[]{1,2,3}) {
                assertThat(BuiltInArtworkTest.pixels(registry.tile(a,palette,Nodes.Hue.BLUE,40,40,20,false),scale))
                    .isEqualTo(BuiltInArtworkTest.pixels(registry.tile(b,palette,Nodes.Hue.BLUE,40,40,20,false),scale));
                assertThat(BuiltInArtworkTest.pixels(registry.icon(a,palette,Nodes.Hue.BLUE,20),scale))
                    .isEqualTo(BuiltInArtworkTest.pixels(registry.icon(b,palette,Nodes.Hue.BLUE,20),scale));
                int[] changed=BuiltInArtworkTest.pixels(retained,scale);
                int[] original=BuiltInArtworkTest.pixels(registry.tile(a,palette,Nodes.Hue.BLUE,40,40,20,true),scale);
                assertThat(changed[20*scale*40*scale+15*scale]).isEqualTo(0xff23c976);
                assertThat(original[20*scale*40*scale+15*scale]).isEqualTo(0xffff2200);
                for(int i=0;i<changed.length;i++)assertThat(changed[i]>>>24).as("composited coverage %s",i).isEqualTo(original[i]>>>24);
            }
            var before=BuiltInArtworkTest.pixels(retained,3);
            for(int size=50;size<62;size++)bytes.decoded().snapshot(size);
            var field=DecodedArtwork.class.getDeclaredField("snapshots");field.setAccessible(true);
            var cache=(java.util.Map<?,?>)field.get(bytes.decoded());var cached=new java.util.LinkedHashMap<>(cache);
            Edt.run(()->assertThatThrownBy(()->receiver.reconcile(()->{receiver.name(selected);throw new IllegalStateException("publication");})).isInstanceOf(IllegalStateException.class));
            assertThat(registry.names()).contains(a,b);
            Edt.run(()->receiver.reconcile(()->receiver.name(selected)));assertThat(registry.names()).contains(b).doesNotContain(a);
            receiver.close();registry.close();
            for(int i=0;i<5;i++)assertThat(BuiltInArtworkTest.pixels(retained,3)).isEqualTo(before);
            assertThat(cache).isEqualTo(cached);
        }
    }

    @Test void hueOnlyChangesTileBackdropAndImmutableAliasLifetimeIncludesTheDeclaration() {
        var bytes=ReceivingArtworkTest.svg("<rect x='5' y='2' width='10' height='6' fill='#ff2200'/>");
        var blue=new IdentityArtwork(bytes,Nodes.Hue.BLUE);var green=new IdentityArtwork(bytes,Nodes.Hue.GREEN);
        assertThat(blue).isNotEqualTo(green);assertThatThrownBy(()->new IdentityArtwork(bytes,Nodes.Hue.NONE)).isInstanceOf(IllegalArgumentException.class);
        try(var registry=new IconRegistry();var receiver=new ReceivingArtwork(registry)) {
            String b=receiver.name(blue),g=receiver.name(green);assertThat(b).isNotEqualTo(g);assertThat(receiver.name(new IdentityArtwork(bytes,Nodes.Hue.BLUE))).isEqualTo(b);
            var palette=KitFixtures.light().semantic();
            var old=registry.tile(b,palette,Nodes.Hue.PURPLE,32,32,16,false);
            var bluePixels=BuiltInArtworkTest.pixels(old,1);var greenPixels=BuiltInArtworkTest.pixels(registry.tile(g,palette,Nodes.Hue.PURPLE,32,32,16,false),1);
            assertThat(bluePixels[4*32+16]).isNotEqualTo(greenPixels[4*32+16]);
            assertThat(bluePixels[16*32+16]).isEqualTo(greenPixels[16*32+16]).isEqualTo(0xffff2200);
            assertThat(BuiltInArtworkTest.pixels(registry.icon(b,palette,Nodes.Hue.PURPLE,16),1)).isEqualTo(BuiltInArtworkTest.pixels(registry.icon(g,palette,Nodes.Hue.BLUE,16),1));
            assertThat(BuiltInArtworkTest.pixels(registry.tile(b,palette,Nodes.Hue.BLUE,32,true),1)).isEqualTo(BuiltInArtworkTest.pixels(registry.tile(g,palette,Nodes.Hue.GREEN,32,true),1));
            Edt.run(()->assertThatThrownBy(()->receiver.reconcile(()->{receiver.name(green);throw new IllegalStateException("failed publication");})).isInstanceOf(IllegalStateException.class));
            assertThat(registry.names()).contains(b,g);
            Edt.run(()->receiver.reconcile(()->receiver.name(green)));assertThat(registry.names()).doesNotContain(b).contains(g);
            receiver.close();assertThat(BuiltInArtworkTest.pixels(old,1)).isEqualTo(bluePixels);
        }
    }
}
