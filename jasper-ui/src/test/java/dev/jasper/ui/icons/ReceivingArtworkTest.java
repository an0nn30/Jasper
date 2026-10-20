package dev.jasper.ui.icons;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReceivingArtworkTest {
    static EncodedArtwork svg(String body) {
        return new EncodedArtwork(EncodedArtwork.Format.SVG, ("<svg xmlns='http://www.w3.org/2000/svg' width='20' height='10'>"+body+"</svg>").getBytes(StandardCharsets.UTF_8));
    }
    @Test void svgValidationDoesNotRetainAMegapixelMasterPerDeclaredIcon() {
        var artwork=svg("<rect width='20' height='10' fill='#ff0000'/>");
        artwork.prepare();
        var decoded=artwork.decoded();
        assertThat((long)decoded.image.getWidth()*decoded.image.getHeight())
            .as("validation storage per immutable vector value; receiving snapshots render from vector")
            .isLessThanOrEqualTo(64L*64);
    }
    @Test void preparedPngRemainsAnImmutableValueAndRejectsOversizedOrMalformedInput() throws Exception {
        var source=new BufferedImage(20,10,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<10;y++)for(int x=0;x<20;x++)source.setRGB(x,y,x<10?0xffff0000:0x800000ff);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(source,"png",bytes);
        var artwork=new EncodedArtwork(EncodedArtwork.Format.PNG,bytes.toByteArray());var equal=new EncodedArtwork(EncodedArtwork.Format.PNG,bytes.toByteArray());int hash=artwork.hashCode();
        java.util.concurrent.CompletableFuture.runAsync(artwork::prepare).get();
        assertThat(artwork).isEqualTo(equal);assertThat(artwork.hashCode()).isEqualTo(hash);assertThat(artwork.toString()).doesNotContain("PNG\r");
        byte[] copy=artwork.data();copy[0]=0;assertThat(artwork).isEqualTo(equal);
        try(var icons=new IconRegistry();var registration=icons.register("test:png",artwork)) {
            var icon=icons.icon("test:png",KitFixtures.light().semantic(),Nodes.Hue.GREEN,40);
            for(int scale:new int[]{1,2}) {
                var actual=new BufferedImage(40*scale,40*scale,BufferedImage.TYPE_INT_ARGB);var g=actual.createGraphics();g.scale(scale,scale);icon.paintIcon(null,g,0,0);g.dispose();
                assertThat(actual.getRGB(10*scale,20*scale)).isEqualTo(0xffff0000);assertThat(actual.getRGB(30*scale,20*scale)).isEqualTo(0x800000ff);assertThat(actual.getRGB(10*scale,2*scale)).isZero();
            }
            assertThatThrownBy(()->icons.register("test:bad",new EncodedArtwork(EncodedArtwork.Format.PNG,new byte[]{1,2,3}))).isInstanceOf(IllegalArgumentException.class);
            var excessive=new EncodedArtwork(EncodedArtwork.Format.SVG,"<svg xmlns='http://www.w3.org/2000/svg' width='4097' height='1'/>".getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(()->icons.register("test:bad",excessive)).isInstanceOf(IllegalArgumentException.class);assertThat(icons.names()).doesNotContain("test:bad");
        }
    }
    @Test void encodedStrokeMatchesDirectSvgAtOneAndTwoDevicePixels() {
        String svg="<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 16 16'><path d='M3 12 L12 3 L14 5 L5 14 Z' fill='none' stroke='#ced1d8' stroke-width='1.5'/></svg>";
        var encoded=new EncodedArtwork(EncodedArtwork.Format.SVG,svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try(var icons=new IconRegistry();var registration=icons.register("fixture:diagonal",encoded)) {
            var palette=KitFixtures.light().semantic();
            var icon=icons.icon("fixture:diagonal",palette,dev.jasper.ui.model.Nodes.Hue.NEUTRAL,17);
            for(int scale:new int[]{1,2}) {
                int size=17*scale;var actual=new java.awt.image.BufferedImage(size,size,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g=actual.createGraphics();g.scale(scale,scale);icon.paintIcon(null,g,0,0);g.dispose();
                var direct=new java.awt.image.BufferedImage(size,size,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var document=new com.github.weisj.jsvg.parser.SVGLoader().load(new java.io.ByteArrayInputStream(encoded.data()),null,com.github.weisj.jsvg.parser.LoaderContext.builder().build());
                var reference=direct.createGraphics();reference.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);document.render(null,reference,new com.github.weisj.jsvg.view.ViewBox(0,0,size,size));reference.dispose();
                long error=0;for(int y=0;y<size;y++)for(int x=0;x<size;x++)error+=Math.abs((actual.getRGB(x,y)>>>24)-(direct.getRGB(x,y)>>>24));
                assertThat(error).as("alpha coverage at device scale %s",scale).isLessThanOrEqualTo(20);
            }
        }
    }

    @Test void authoredColorsAlphaAndAspectSurviveThemesAndRegistrationRetirement() {
        try(var registry=new IconRegistry()) {
            var art=svg("<g transform='translate(2 0)'><rect width='8' height='10' fill='#ff0000'/><rect x='8' width='8' height='10' fill='#0000ff' opacity='.5'/></g>");
            var registration=registry.register("test:color",art);
            var icon=registry.icon("test:color",KitFixtures.dark().semantic(),Nodes.Hue.GREEN,40);
            registration.close();
            var image=paint(icon);
            assertThat(image.getRGB(10,20)).isEqualTo(0xffff0000);
            assertThat(image.getRGB(24,20)>>>24).isBetween(127,128);
            assertThat(image.getRGB(24,20)&0xffffff).isEqualTo(0x0000ff);
            assertThat(image.getRGB(10,2)>>>24).isZero();
        }
    }
    @Test void rejectsExternalResourcesBeforeAnyRegistrationAndKeepsLocalFragments() {
        try(var registry=new IconRegistry()) {
            for(String body:new String[]{"<image href='http://127.0.0.1:9/no.svg'/>","<image href='file:///etc/passwd'/>","<image href='data:image/png;base64,AAAA'/>","<style>@import url(https://example.invalid/x.css);</style>","<script>alert(1)</script>","<rect style='fill:url(https://example.invalid/x)'/>"}) {
                assertThatThrownBy(()->registry.register("test:rejected",svg(body))).isInstanceOf(IllegalArgumentException.class);
                assertThat(registry.names()).doesNotContain("test:rejected");
            }
            registry.register("test:fragment",svg("<defs><linearGradient id='g'><stop stop-color='#ff0000'/><stop offset='1' stop-color='#0000ff'/></linearGradient><rect id='r' width='20' height='10' fill='url(#g)'/></defs><use href='#r'/>") );
            var image=paint(registry.icon("test:fragment",KitFixtures.light().semantic(),Nodes.Hue.GREEN,20));
            assertThat(image.getRGB(4,8)>>>24).isEqualTo(255);
        }
    }
    @Test void externalEntityAndStylesheetNeverOpenALocalResource() throws Exception {
        try(var listener=new java.net.ServerSocket(0);var registry=new IconRegistry()) {
            listener.setSoTimeout(100);
            String url="http://127.0.0.1:"+listener.getLocalPort()+"/external";
            for(String source:new String[]{
                "<!DOCTYPE svg [<!ENTITY ext SYSTEM '"+url+"'>]><svg xmlns='http://www.w3.org/2000/svg' width='16' height='16'><text>&ext;</text></svg>",
                "<?xml-stylesheet href='"+url+"'?><svg xmlns='http://www.w3.org/2000/svg' width='16' height='16'/>",
                "<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16'><image href='"+url+"'/></svg>"}) {
                assertThatThrownBy(()->registry.register("test:external",new EncodedArtwork(EncodedArtwork.Format.SVG,source.getBytes(StandardCharsets.UTF_8)))).isInstanceOf(IllegalArgumentException.class);
            }
            assertThatThrownBy(listener::accept).isInstanceOf(java.net.SocketTimeoutException.class);
            assertThat(registry.names()).doesNotContain("test:external");
        }
    }
    @Test void failedPublicationRetainsBothGenerationsUntilNextSuccessfulPublication() {
        Edt.run(()->{
            try(var registry=new IconRegistry();var receiver=new ReceivingArtwork(registry)) {
                var red=svg("<rect width='20' height='10' fill='red'/>");
                var blue=svg("<rect width='20' height='10' fill='blue'/>");
                String[] names=new String[2];
                receiver.reconcile(()->names[0]=receiver.name(red));
                assertThatThrownBy(()->receiver.reconcile(()->{names[1]=receiver.name(blue);throw new IllegalStateException("publish");})).isInstanceOf(IllegalStateException.class);
                assertThat(registry.names()).contains(names);
                receiver.reconcile(()->assertThat(receiver.name(blue)).isEqualTo(names[1]));
                assertThat(registry.names()).contains(names[1]).doesNotContain(names[0]);
                receiver.close();
                assertThat(registry.names()).contains("plugin").doesNotContain(names);
            }
        });
    }
    static BufferedImage paint(Icon icon) {
        var image=new BufferedImage(icon.getIconWidth(),icon.getIconHeight(),BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics();try{icon.paintIcon(null,graphics,0,0);}finally{graphics.dispose();}return image;
    }
}
