package dev.jasper.ui.icons;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.testsupport.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IdentityArtworkTest {
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
