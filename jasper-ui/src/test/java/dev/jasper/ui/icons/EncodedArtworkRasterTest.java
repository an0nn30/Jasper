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

    /** Plan 4b Task 1: a PNG drawn in a square box keeps its aspect, centred, and a PNG that does not decode is refused. */
    @Test void anIconFitsThePngIntoItsBoxCentredAndRefusesWhatDoesNotDecode() throws Exception {
        var source = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 32; x++) source.setRGB(x, y, 0xff00ff00);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        var icon = new EncodedArtwork(EncodedArtwork.Format.PNG, bytes.toByteArray()).icon(16);
        assertThat(icon.getIconWidth()).isEqualTo(16);
        assertThat(icon.getIconHeight()).isEqualTo(16);
        var painted = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = painted.createGraphics();
        try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        assertThat(painted.getRGB(8, 8)).as("the middle").isEqualTo(0xff00ff00);
        assertThat(painted.getRGB(8, 1) >>> 24).as("above the band of an image twice as wide as it is high").isZero();
        assertThat(painted.getRGB(8, 14) >>> 24).as("below it").isZero();
        byte[] signatureOnly = new byte[64];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, 0, signatureOnly, 0, 8);
        assertThatThrownBy(() -> new EncodedArtwork(EncodedArtwork.Format.PNG, signatureOnly).icon(16)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncodedArtwork(EncodedArtwork.Format.PNG, bytes.toByteArray()).icon(0)).isInstanceOf(IllegalArgumentException.class);
    }

    /** The PNG {@code image} writes, with a zTXt chunk after its header whose compressed text is not deflate data. */
    static byte[] withBrokenText(BufferedImage image) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        byte[] png = bytes.toByteArray();
        int afterHeader = 8 + 4 + 4 + 13 + 4;
        var chunk = new ByteArrayOutputStream();
        byte[] type = {'z', 'T', 'X', 't'};
        byte[] data = {'C', 'o', 'm', 'm', 'e', 'n', 't', 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        var length = java.nio.ByteBuffer.allocate(4).putInt(data.length).array();
        var crc = new java.util.zip.CRC32();
        crc.update(type); crc.update(data);
        chunk.write(length); chunk.write(type); chunk.write(data); chunk.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        var result = new ByteArrayOutputStream();
        result.write(png, 0, afterHeader); result.write(chunk.toByteArray()); result.write(png, afterHeader, png.length - afterHeader);
        return result.toByteArray();
    }

    /**
     * Plan 4b Task 1 (review R8): a PNG's metadata is never read, so a text chunk, however it is compressed, is not
     * inflated: a broken one does not stop the image decoding, whether it has true colour or a palette.
     */
    @Test void aPngsTextChunksAreNotInflated() throws Exception {
        var colour = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) colour.setRGB(x, y, 0xff0000ff);
        var palette = new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_INDEXED);
        var g = palette.createGraphics();
        try { g.setColor(java.awt.Color.BLUE); g.fillRect(0, 0, 4, 4); } finally { g.dispose(); }
        for (var image : java.util.List.of(colour, palette)) {
            var icon = new EncodedArtwork(EncodedArtwork.Format.PNG, withBrokenText(image)).icon(8);
            var painted = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            var paint = painted.createGraphics();
            try { icon.paintIcon(null, paint, 0, 0); } finally { paint.dispose(); }
            assertThat(painted.getRGB(4, 4)).as("type %s", image.getType()).isEqualTo(0xff0000ff);
        }
    }

    /** Plan 4b Task 1 (review R6, R9): the icon for one extent is made once, so repeated requests return it. */
    @Test void theIconForAnExtentIsMadeOnce() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", bytes);
        var artwork = new EncodedArtwork(EncodedArtwork.Format.PNG, bytes.toByteArray());
        assertThat(artwork.icon(16)).isSameAs(artwork.icon(16));
        assertThat(artwork.icon(24)).isNotSameAs(artwork.icon(16));
        assertThat(artwork.icon(24).getIconWidth()).isEqualTo(24);
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
