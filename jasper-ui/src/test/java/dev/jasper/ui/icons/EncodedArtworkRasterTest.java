package dev.jasper.ui.icons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Enterprise plan 11: artwork drawn at a caller's size, and the receiving SVG rules checked without drawing. */
class EncodedArtworkRasterTest {
    static final Path BOOT_ART = Path.of(System.getProperty("jasper.repoRoot"),
        "docs/design/enterprise/reference/10 Boot Splash/extracted/art.svg");

    static byte[] svg(String body) {
        return ("<svg xmlns='http://www.w3.org/2000/svg' width='20' height='10' viewBox='0 0 20 10'>" + body + "</svg>")
            .getBytes(StandardCharsets.UTF_8);
    }

    @Test void aVectorIsDrawnAtTheRequestedSizeAndAPngIsScaled() throws Exception {
        var vector = new EncodedArtwork(EncodedArtwork.Format.SVG,
            svg("<rect width='10' height='10' fill='#ff0000'/><rect x='10' width='10' height='10' fill='#0000ff'/>"));
        BufferedImage drawn = vector.raster(40, 20);
        assertThat(drawn.getWidth()).isEqualTo(40);
        assertThat(drawn.getHeight()).isEqualTo(20);
        assertThat(drawn.getRGB(5, 10)).isEqualTo(0xffff0000);
        assertThat(drawn.getRGB(35, 10)).isEqualTo(0xff0000ff);
        assertThat(vector.raster(40, 20)).as("a new image each time").isNotSameAs(drawn);
        var source = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 20; x++) source.setRGB(x, y, x < 10 ? 0xffff0000 : 0xff0000ff);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        var scaled = new EncodedArtwork(EncodedArtwork.Format.PNG, bytes.toByteArray()).raster(40, 20);
        assertThat(scaled.getRGB(5, 10)).isEqualTo(0xffff0000);
        assertThat(scaled.getRGB(35, 10)).isEqualTo(0xff0000ff);
        assertThatThrownBy(() -> vector.raster(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vector.raster(8193, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void theBootSplashArtPassesTheRulesAndDrawsOpaqueAtItsSize() throws Exception {
        byte[] art = Files.readAllBytes(BOOT_ART);
        EncodedArtwork.checkSvg(art);
        var image = new EncodedArtwork(EncodedArtwork.Format.SVG, art).raster(718, 250);
        for (int[] point : new int[][] {{0, 0}, {717, 0}, {0, 249}, {717, 249}, {359, 125}})
            assertThat(image.getRGB(point[0], point[1]) >>> 24).as("alpha at %s,%s", point[0], point[1]).isEqualTo(255);
    }

    @Test void checkSvgAppliesTheReceivingRulesWithoutDrawing() {
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(svg("<script>alert(1)</script>")))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Active SVG denied");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(svg("<image href='https://example.com/a.png' width='1' height='1'/>")))
            .hasMessage("Nonlocal resource denied");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(svg("<rect width='1' height='1' fill='url(https://example.com/#p)'/>")))
            .hasMessage("Nonlocal paint resource denied");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg("<html/>".getBytes(StandardCharsets.UTF_8))).hasMessage("Not SVG");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg("not xml at all".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(new byte[256 * 1024 + 1])).hasMessage("Artwork must contain 1..262144 bytes");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(("<!DOCTYPE svg [<!ENTITY e 'x'>]>"
            + new String(svg("<rect width='1' height='1'/>"), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
