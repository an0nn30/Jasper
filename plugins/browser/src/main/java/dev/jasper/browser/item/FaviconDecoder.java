package dev.jasper.browser.item;

import dev.jasper.sdk.ui.PixelImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * A web page's favicon as its tab and address field show it (spec section 5): the PNG of at most
 * {@value #SIZE}&times;{@value #SIZE} pixels that the SDK's {@link PixelImage} takes. PNG, GIF and JPEG go through
 * ImageIO, a GIF's first frame; an ICO gives its entry closest to {@value #SIZE} pixels, a PNG or a BMP. A larger image
 * is scaled down to fit, its aspect kept. SVG, WebP and anything else give nothing, and so does a file over
 * {@value #LIMIT} bytes or one that claims more than {@value #LONGEST} pixels a side, before anything is decoded. The
 * bytes come from the web, so nothing in them makes this throw, and every buffer it makes is bounded first. ImageIO runs
 * on memory streams only, leaving no temporary file. Any thread.
 */
public final class FaviconDecoder {
    /** The most bytes it decodes: what the Browser fetches of a favicon. */
    public static final int LIMIT = 65_536;
    /** The longest side, in pixels, of what it gives. */
    static final int SIZE = 32;
    // The longest side it reads; a larger image is read every n-th pixel, so that what it decodes has at most DECODED.
    private static final int LONGEST = 1_024, DECODED = 256;
    // The longest side of an ICO's BMP entry, which ICO's directory cannot exceed either.
    private static final int ICO_SIDE = 256;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, GIF87 = {'G', 'I', 'F', '8', '7', 'a'},
        GIF89 = {'G', 'I', 'F', '8', '9', 'a'}, JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, ICO = {0, 0, 1, 0};

    private FaviconDecoder() { }

    /**
     * {@code data}, a favicon file, as a PNG of at most {@value #SIZE} pixels a side.
     *
     * @param data the file's bytes, at most {@value #LIMIT}; null gives nothing
     * @return the PNG, or empty for a file it does not read
     */
    public static Optional<PixelImage> toPng(byte[] data) {
        if (data == null || data.length > LIMIT) return Optional.empty();
        try {
            return pixels(data).map(FaviconDecoder::fit).flatMap(FaviconDecoder::png);
        } catch (IOException | RuntimeException unreadable) {
            // ImageIO's readers throw unchecked exceptions for some broken files too; a broken favicon shows the globe.
            return Optional.empty();
        }
    }

    /** An image's pixels, row by row, as non-premultiplied ARGB. */
    private record Pixels(int width, int height, int[] argb) { }

    private static Optional<Pixels> pixels(byte[] data) throws IOException {
        if (starts(data, PNG)) return imageIo(data, "png");
        if (starts(data, GIF87) || starts(data, GIF89)) return imageIo(data, "gif");
        if (starts(data, JPEG)) return imageIo(data, "jpeg");
        if (starts(data, ICO)) return ico(data);
        return Optional.empty();
    }

    /**
     * A PNG, GIF or JPEG through ImageIO: its sides read from its header first, then at most {@value #DECODED} pixels a
     * side of it decoded. Metadata is skipped, as a PNG's compressed text can inflate to far more than the file; the
     * readers still apply a PNG's or GIF's transparency.
     */
    private static Optional<Pixels> imageIo(byte[] data, String format) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) return Optional.empty();
        ImageReader reader = readers.next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0), height = reader.getHeight(0);
            if (width < 1 || height < 1 || width > LONGEST || height > LONGEST) return Optional.empty();
            ImageReadParam parameters = reader.getDefaultReadParam();
            int step = (Math.max(width, height) + DECODED - 1) / DECODED;
            if (step > 1) parameters.setSourceSubsampling(step, step, 0, 0);
            BufferedImage image = reader.read(0, parameters);
            int w = image.getWidth(), h = image.getHeight();
            return Optional.of(new Pixels(w, h, image.getRGB(0, 0, w, h, null, 0, w)));
        } finally {
            reader.dispose();
        }
    }

    /**
     * An ICO's entry closest to {@value #SIZE} pixels, by the sides its directory gives: of two equally close, the larger,
     * then the one with more bits a pixel. Only entries whose bytes lie within the file count. The entry is a PNG or a
     * BMP without its file header; if it does not decode, nothing does.
     */
    private static Optional<Pixels> ico(byte[] data) throws IOException {
        if (data.length < 6) return Optional.empty();
        ByteBuffer file = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int count = Short.toUnsignedInt(file.getShort(4));
        long directory = 6 + 16L * count;
        if (count == 0 || directory > data.length) return Optional.empty();
        int best = -1, bestSide = 0, bestBits = 0;
        for (int entry = 0; entry < count; entry++) {
            int at = 6 + 16 * entry;
            int width = side(data[at]), height = side(data[at + 1]), bits = Short.toUnsignedInt(file.getShort(at + 6));
            long size = Integer.toUnsignedLong(file.getInt(at + 8)), offset = Integer.toUnsignedLong(file.getInt(at + 12));
            if (size == 0 || offset < directory || offset + size > data.length) continue;
            int side = Math.max(width, height);
            if (best < 0 || closer(side, bits, bestSide, bestBits)) {
                best = entry;
                bestSide = side;
                bestBits = bits;
            }
        }
        if (best < 0) return Optional.empty();
        int at = 6 + 16 * best;
        int offset = file.getInt(at + 12);
        byte[] image = Arrays.copyOfRange(data, offset, offset + file.getInt(at + 8));
        return starts(image, PNG) ? imageIo(image, "png") : dib(image);
    }

    /** An ICO directory's side: 0 is 256. */
    private static int side(byte value) {
        int side = Byte.toUnsignedInt(value);
        return side == 0 ? ICO_SIDE : side;
    }

    /** Whether an entry of {@code side} pixels and {@code bits} beats the best so far. */
    private static boolean closer(int side, int bits, int bestSide, int bestBits) {
        int distance = Math.abs(side - SIZE), bestDistance = Math.abs(bestSide - SIZE);
        if (distance != bestDistance) return distance < bestDistance;
        if (side != bestSide) return side > bestSide;
        return bits > bestBits;
    }

    /**
     * An ICO's BMP entry: a {@code BITMAPINFOHEADER} whose height counts the image twice, an uncompressed image of 1, 4,
     * 8, 24 or 32 bits a pixel, bottom row first (with a palette up to 8 bits), then a 1-bit AND mask that makes a set
     * pixel transparent. A 32-bit image's own alpha wins over the mask unless all of it is zero; a missing mask leaves
     * every pixel opaque.
     */
    private static Optional<Pixels> dib(byte[] data) {
        if (data.length < 40) return Optional.empty();
        ByteBuffer dib = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long header = Integer.toUnsignedLong(dib.getInt(0));
        int width = dib.getInt(4), doubled = dib.getInt(8), bits = Short.toUnsignedInt(dib.getShort(14));
        long compression = Integer.toUnsignedLong(dib.getInt(16)), used = Integer.toUnsignedLong(dib.getInt(32));
        if (header < 40 || header > data.length || compression != 0) return Optional.empty();
        if (width < 1 || width > ICO_SIDE || doubled < 2 || doubled % 2 != 0 || doubled / 2 > ICO_SIDE) return Optional.empty();
        if (bits != 1 && bits != 4 && bits != 8 && bits != 24 && bits != 32) return Optional.empty();
        int height = doubled / 2;
        long colours = bits > 8 ? 0 : used == 0 ? 1L << bits : used;
        if (colours > 1L << bits) return Optional.empty();
        long xor = header + 4 * colours, xorStride = (width * (long) bits + 31) / 32 * 4;
        long mask = xor + xorStride * height, maskStride = (width + 31L) / 32 * 4;
        if (mask > data.length) return Optional.empty();
        boolean masked = mask + maskStride * height <= data.length;
        int[] argb = new int[width * height];
        boolean alpha = false;
        for (int y = 0; y < height; y++) {
            int row = (int) (xor + (height - 1 - y) * xorStride);
            for (int x = 0; x < width; x++) {
                int pixel;
                switch (bits) {
                    case 32 -> {
                        pixel = dib.getInt(row + 4 * x);
                        alpha |= pixel >>> 24 != 0;
                    }
                    case 24 -> pixel = 0xFF000000 | Byte.toUnsignedInt(data[row + 3 * x + 2]) << 16
                        | Byte.toUnsignedInt(data[row + 3 * x + 1]) << 8 | Byte.toUnsignedInt(data[row + 3 * x]);
                    default -> {
                        int index = index(data, row, x, bits);
                        if (index >= colours) return Optional.empty();
                        pixel = 0xFF000000 | dib.getInt((int) header + 4 * index) & 0xFFFFFF;
                    }
                }
                argb[y * width + x] = pixel;
            }
        }
        if (bits == 32 && alpha) return Optional.of(new Pixels(width, height, argb));
        for (int y = 0; y < height; y++) {
            int row = (int) (mask + (height - 1 - y) * maskStride);
            for (int x = 0; x < width; x++) {
                boolean transparent = masked && (data[row + x / 8] >> (7 - x % 8) & 1) == 1;
                argb[y * width + x] = transparent ? 0 : argb[y * width + x] | 0xFF000000;
            }
        }
        return Optional.of(new Pixels(width, height, argb));
    }

    /** The palette index of pixel {@code x} in a row of 1, 4 or 8 bits a pixel, the high bits first. */
    private static int index(byte[] data, int row, int x, int bits) {
        int perByte = 8 / bits, value = Byte.toUnsignedInt(data[row + x / perByte]);
        int shift = 8 - bits * (x % perByte + 1);
        return value >> shift & (1 << bits) - 1;
    }

    /**
     * {@code image} scaled down to fit in {@value #SIZE}&times;{@value #SIZE}, its aspect kept: each pixel the average of
     * the area of the image it covers, weighted by alpha so that transparent pixels lend it no colour. An image that fits
     * stays as it is.
     */
    private static Pixels fit(Pixels image) {
        int longest = Math.max(image.width(), image.height());
        if (longest <= SIZE) return image;
        int width = Math.max(1, Math.round(image.width() * (float) SIZE / longest));
        int height = Math.max(1, Math.round(image.height() * (float) SIZE / longest));
        double across = (double) image.width() / width, down = (double) image.height() / height;
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            double top = y * down, bottom = top + down;
            for (int x = 0; x < width; x++) {
                double left = x * across, right = left + across;
                double a = 0, r = 0, g = 0, b = 0;
                for (int sy = (int) top; sy < Math.min(image.height(), (int) Math.ceil(bottom)); sy++) {
                    double high = Math.min(bottom, sy + 1) - Math.max(top, sy);
                    for (int sx = (int) left; sx < Math.min(image.width(), (int) Math.ceil(right)); sx++) {
                        double weight = high * (Math.min(right, sx + 1) - Math.max(left, sx));
                        int pixel = image.argb()[sy * image.width() + sx];
                        double opacity = (pixel >>> 24) * weight;
                        a += opacity;
                        r += (pixel >> 16 & 0xFF) * opacity;
                        g += (pixel >> 8 & 0xFF) * opacity;
                        b += (pixel & 0xFF) * opacity;
                    }
                }
                int alpha = channel(a / (across * down));
                argb[y * width + x] = alpha == 0 ? 0 : alpha << 24 | channel(r / a) << 16 | channel(g / a) << 8 | channel(b / a);
            }
        }
        return new Pixels(width, height, argb);
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    /** The pixels as a PNG, written to memory. */
    private static Optional<PixelImage> png(Pixels pixels) {
        var image = new BufferedImage(pixels.width(), pixels.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, pixels.width(), pixels.height(), pixels.argb(), 0, pixels.width());
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) return Optional.empty();
        ImageWriter writer = writers.next();
        var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(image);
        } catch (IOException unwritable) {
            return Optional.empty();
        } finally {
            writer.dispose();
        }
        return Optional.of(new PixelImage(bytes.toByteArray()));
    }

    private static boolean starts(byte[] data, byte[] signature) {
        return data.length >= signature.length && Arrays.equals(data, 0, signature.length, signature, 0, signature.length);
    }
}
