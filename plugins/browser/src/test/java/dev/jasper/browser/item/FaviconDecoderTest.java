package dev.jasper.browser.item;

import dev.jasper.sdk.ui.PixelImage;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A favicon as a tab and the address field show it (spec section 5; plan 4b Task 2): PNG, GIF and JPEG through
 * ImageIO, an ICO's entry closest to 32 pixels whether a PNG or a BMP, at most 32 pixels a side and always a PNG; and
 * nothing for SVG, a broken ICO, noise or anything over 64 KiB.
 *
 * <p>The ICO fixtures under {@code favicons/} were made with Pillow 12.3, an encoder independent of this decoder. Each
 * image is a plain square whose top-left quadrant is transparent, in a colour per size: 16 pixels red
 * ({@code #FF0000}), 32 green ({@code #00A000}), 48 blue ({@code #0000FF}). {@code png-entries.ico} holds the three
 * sizes as PNG entries and {@code bmp-entries.ico} as 32-bit BMP entries with alpha and no mask. {@code bmp-palette.ico}
 * is one 16-pixel 8-bit entry of 226 palette colours, {@code bmp-rgb.ico} one 32-pixel 24-bit entry and
 * {@code bmp-mono.ico} one 32-pixel 1-bit white entry, each with an AND mask over the quadrant (Pillow reads them so).
 * {@code truncated.ico} is the first 100 bytes of {@code png-entries.ico}, which cut its first entry short.
 */
class FaviconDecoderTest {
    private static final int RED = 0xFFFF0000, GREEN = 0xFF00A000, BLUE = 0xFF0000FF, WHITE = 0xFFFFFFFF;

    @Test void aPngDecodesAtItsOwnSizeAndComesBackAsAPng() throws Exception {
        BufferedImage source = quadrant(24, 24, GREEN);
        BufferedImage shown = decoded(FaviconDecoder.toPng(encode(source, "png")));
        assertThat(shown.getWidth()).isEqualTo(24);
        assertThat(shown.getHeight()).isEqualTo(24);
        assertThat(pixels(shown)).isEqualTo(pixels(source));
    }

    @Test void anImageLargerThan32PixelsIsScaledToFitAndKeepsItsAspect() throws Exception {
        var source = new BufferedImage(64, 40, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 64; x++) source.setRGB(x, y, x < 32 ? RED : BLUE);
        BufferedImage shown = decoded(FaviconDecoder.toPng(encode(source, "png")));
        assertThat(shown.getWidth()).isEqualTo(32);
        assertThat(shown.getHeight()).isEqualTo(20);
        assertThat(shown.getRGB(4, 10)).isEqualTo(RED);
        assertThat(shown.getRGB(27, 10)).isEqualTo(BLUE);
        BufferedImage half = decoded(FaviconDecoder.toPng(encode(quadrant(64, 64, GREEN), "png")));
        assertThat(half.getRGB(4, 4) >>> 24).as("the transparent quadrant").isZero();
        assertThat(half.getRGB(24, 24)).isEqualTo(GREEN);
        assertThat(half.getRGB(15, 4) >>> 24).as("an edge pixel averages whole source pixels").isZero();
        assertThat(half.getRGB(16, 4)).isEqualTo(GREEN);
    }

    @Test void aGifAndAnIndexedPngKeepTheirTransparency() throws Exception {
        var colours = new IndexColorModel(8, 2, new byte[] {0, (byte) 0xFF}, new byte[] {0, 0}, new byte[] {0, 0}, 0);
        var source = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_INDEXED, colours);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) source.getRaster().setSample(x, y, 0, x < 8 && y < 8 ? 0 : 1);
        for (String format : new String[] {"gif", "png"}) {
            BufferedImage shown = decoded(FaviconDecoder.toPng(encode(source, format)));
            assertThat(shown.getWidth()).as(format).isEqualTo(16);
            assertThat(shown.getRGB(2, 2) >>> 24).as(format + ": the transparent index").isZero();
            assertThat(shown.getRGB(12, 12)).as(format).isEqualTo(RED);
        }
    }

    @Test void aJpegDecodes() throws Exception {
        var source = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) source.setRGB(x, y, BLUE);
        BufferedImage shown = decoded(FaviconDecoder.toPng(encode(source, "jpeg")));
        assertThat(shown.getWidth()).isEqualTo(20);
        assertThat(near(shown.getRGB(10, 10), BLUE)).as(Integer.toHexString(shown.getRGB(10, 10))).isTrue();
    }

    /** The 32-pixel entry of three, whether the entries are PNGs or BMPs. */
    @Test void anIcoGivesItsEntryClosestTo32Pixels() throws Exception {
        for (String name : new String[] {"png-entries.ico", "bmp-entries.ico"}) {
            BufferedImage shown = decoded(FaviconDecoder.toPng(fixture(name)));
            assertThat(shown.getWidth()).as(name).isEqualTo(32);
            assertThat(shown.getHeight()).as(name).isEqualTo(32);
            assertThat(shown.getRGB(4, 4) >>> 24).as(name + ": the transparent quadrant").isZero();
            assertThat(shown.getRGB(24, 24)).as(name).isEqualTo(GREEN);
            assertThat(shown.getRGB(24, 4)).as(name + ": the top row is the image's top").isEqualTo(GREEN);
        }
    }

    /** Equally far from 32 pixels, 48 beats 16: scaling down shows more than scaling up. */
    @Test void ofTwoEntriesEquallyFarFrom32PixelsTheLargerOneWins() throws Exception {
        byte[] ico = ico(encode(quadrant(16, 16, RED), "png"), 16, encode(quadrant(48, 48, BLUE), "png"), 48);
        BufferedImage shown = decoded(FaviconDecoder.toPng(ico));
        assertThat(shown.getWidth()).isEqualTo(32);
        assertThat(shown.getRGB(24, 24)).isEqualTo(BLUE);
    }

    @Test void aBmpEntryWithAPaletteOrNoneHonoursItsMask() throws Exception {
        BufferedImage palette = decoded(FaviconDecoder.toPng(fixture("bmp-palette.ico")));
        assertThat(palette.getWidth()).isEqualTo(16);
        assertThat(palette.getRGB(2, 2) >>> 24).as("masked").isZero();
        assertThat(palette.getRGB(12, 12)).isEqualTo(RED);
        assertThat(palette.getRGB(12, 2)).as("the top row is the image's top").isEqualTo(RED);
        BufferedImage rgb = decoded(FaviconDecoder.toPng(fixture("bmp-rgb.ico")));
        assertThat(rgb.getWidth()).isEqualTo(32);
        assertThat(rgb.getRGB(4, 4) >>> 24).as("masked").isZero();
        assertThat(rgb.getRGB(24, 24)).isEqualTo(GREEN);
        BufferedImage mono = decoded(FaviconDecoder.toPng(fixture("bmp-mono.ico")));
        assertThat(mono.getWidth()).isEqualTo(32);
        assertThat(mono.getRGB(4, 4) >>> 24).as("masked").isZero();
        assertThat(mono.getRGB(24, 24)).isEqualTo(WHITE);
        assertThat(mono.getRGB(24, 4)).isEqualTo(WHITE);
    }

    /** A 4-bit entry, two pixels a byte, the high nibble first; its top row is the last in the file. */
    @Test void aFourBitBmpEntryReadsItsNibbles() throws Exception {
        int size = 8, stride = 4;
        var dib = ByteBuffer.allocate(40 + 16 * 4 + stride * size * 2).order(ByteOrder.LITTLE_ENDIAN);
        dib.putInt(40).putInt(size).putInt(size * 2).putShort((short) 1).putShort((short) 4).putInt(0).putInt(0).putInt(0).putInt(0).putInt(0).putInt(0);
        dib.putInt(0).putInt(0x00FF0000).putInt(0x000000FF); // palette as BGRA: black, red, blue
        for (int colour = 3; colour < 16; colour++) dib.putInt(0);
        for (int row = 0; row < size; row++) {
            boolean top = row == size - 1;
            dib.put((byte) (top ? 0x21 : 0x11)).put((byte) 0x11).put((byte) 0x11).put((byte) 0x11);
        }
        for (int row = 0; row < size; row++) dib.putInt(0); // the AND mask: nothing transparent
        BufferedImage shown = decoded(FaviconDecoder.toPng(ico(dib.array(), size)));
        assertThat(shown.getRGB(0, 0)).isEqualTo(BLUE);
        assertThat(shown.getRGB(1, 0)).isEqualTo(RED);
        assertThat(shown.getRGB(0, 7)).isEqualTo(RED);
    }

    @Test void aBrokenIcoGivesNothing() throws Exception {
        assertThat(FaviconDecoder.toPng(fixture("truncated.ico"))).as("its first entry cut short").isEmpty();
        byte[] whole = fixture("png-entries.ico");
        assertThat(FaviconDecoder.toPng(Arrays.copyOf(whole, 30))).as("cut inside its directory").isEmpty();
        assertThat(FaviconDecoder.toPng(Arrays.copyOf(whole, 6))).as("a header alone").isEmpty();
        byte[] noise = new byte[200];
        new Random(7).nextBytes(noise);
        assertThat(FaviconDecoder.toPng(ico(noise, 32))).as("an entry that is neither a PNG nor a BMP").isEmpty();
        byte[] cursor = whole.clone();
        cursor[2] = 2;
        assertThat(FaviconDecoder.toPng(cursor)).as("a cursor").isEmpty();
    }

    @Test void anSvgGivesNothing() throws Exception {
        byte[] svg = fixture("icon.svg");
        assertThat(new String(svg, StandardCharsets.US_ASCII)).startsWith("<svg");
        assertThat(FaviconDecoder.toPng(svg)).isEmpty();
    }

    /** Spec Review Focus 4: nothing over 64 KiB is decoded, whatever it starts with. */
    @Test void seventyKibOfNoiseOrAnythingOver64KibGivesNothing() throws Exception {
        byte[] noise = new byte[70 * 1024];
        new Random(42).nextBytes(noise);
        assertThat(FaviconDecoder.toPng(noise)).isEmpty();
        byte[] png = encode(quadrant(16, 16, GREEN), "png");
        assertThat(FaviconDecoder.toPng(png)).as("the same PNG decodes").isPresent();
        assertThat(FaviconDecoder.toPng(Arrays.copyOf(png, FaviconDecoder.LIMIT + 1))).as("one byte over").isEmpty();
        assertThat(FaviconDecoder.toPng(new byte[0])).isEmpty();
        assertThat(FaviconDecoder.toPng(null)).isEmpty();
    }

    /** A PNG whose header claims 100,000 pixels a side is refused before anything that size is made. */
    @Test void aPngThatClaimsHugeSidesGivesNothing() throws Exception {
        assertThat(FaviconDecoder.toPng(png(100_000, 100_000))).isEmpty();
        assertThat(FaviconDecoder.toPng(png(2_000, 1))).isEmpty();
        assertThat(FaviconDecoder.toPng(png(1, 1))).as("the same file one pixel a side").isPresent();
    }

    /** {@code image} with its top-left quadrant transparent and the rest {@code colour}. */
    private static BufferedImage quadrant(int width, int height, int colour) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, x < width / 2 && y < height / 2 ? 0 : colour);
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(image);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** An ICO of one entry, {@code size} pixels a side. */
    private static byte[] ico(byte[] entry, int size) {
        return ico(new byte[][] {entry}, new int[] {size});
    }

    private static byte[] ico(byte[] first, int firstSize, byte[] second, int secondSize) {
        return ico(new byte[][] {first, second}, new int[] {firstSize, secondSize});
    }

    private static byte[] ico(byte[][] entries, int[] sizes) {
        int length = 6 + 16 * entries.length;
        for (byte[] entry : entries) length += entry.length;
        var ico = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short) 0).putShort((short) 1).putShort((short) entries.length);
        int offset = 6 + 16 * entries.length;
        for (int each = 0; each < entries.length; each++) {
            ico.put((byte) sizes[each]).put((byte) sizes[each]).put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32)
                .putInt(entries[each].length).putInt(offset);
            offset += entries[each].length;
        }
        for (byte[] entry : entries) ico.put(entry);
        return ico.array();
    }

    /** A grey PNG whose header says {@code width} by {@code height} and whose data holds one row of it. */
    private static byte[] png(int width, int height) throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        chunk(out, "IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).array());
        var deflater = new Deflater();
        deflater.setInput(new byte[1 + width]);
        deflater.finish();
        byte[] data = new byte[64 + width];
        int length = deflater.deflate(data);
        deflater.end();
        chunk(out, "IDAT", Arrays.copyOf(data, length));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        var crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(name);
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = FaviconDecoderTest.class.getResourceAsStream("/favicons/" + name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        }
    }

    /** The image a result holds, which must be a PNG of at most 32 pixels a side. */
    private static BufferedImage decoded(Optional<PixelImage> result) {
        assertThat(result).as("a decoded favicon").isPresent();
        byte[] png = result.get().png();
        assertThat(png.length).isLessThanOrEqualTo(PixelImage.MAX_BYTES);
        try {
            BufferedImage image = ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(png)));
            assertThat(image).as("a PNG ImageIO reads").isNotNull();
            assertThat(Math.max(image.getWidth(), image.getHeight())).isLessThanOrEqualTo(32);
            return image;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    /** Whether two opaque colours differ by at most 8 in each channel, as JPEG's loss allows. */
    private static boolean near(int a, int b) {
        for (int shift = 0; shift < 24; shift += 8) if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > 8) return false;
        return a >>> 24 == 0xFF;
    }
}
