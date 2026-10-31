package dev.jasper.ui.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * A small PNG the owner supplies, such as a favicon, which the kit decodes once and draws scaled into the box of the
 * glyph it stands for. It holds its own copy of the bytes, and equal bytes make equal images.
 *
 * @param png the PNG file's bytes: at most {@value #MAX_BYTES}, starting with the PNG signature
 */
public record PixelImage(byte[] png) {
    /** The most bytes a pixel image holds. */
    public static final int MAX_BYTES = 65_536;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    /** Copies the bytes; refuses more than {@value #MAX_BYTES} of them and bytes without the PNG signature. */
    public PixelImage {
        // An array's length never changes, so it is checked before the copy; the signature is checked on the copy, so
        // bytes the caller changes meanwhile cannot slip past the check.
        if (Objects.requireNonNull(png, "png").length > MAX_BYTES) throw new IllegalArgumentException("A pixel image holds at most " + MAX_BYTES + " bytes, not " + png.length);
        png = png.clone();
        if (png.length < SIGNATURE.length || !Arrays.equals(png, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length))
            throw new IllegalArgumentException("A pixel image must be a PNG");
    }

    /** @return a copy of the PNG file's bytes */
    @Override public byte[] png() { return png.clone(); }

    @Override public boolean equals(Object other) { return other instanceof PixelImage image && Arrays.equals(png, image.png); }

    @Override public int hashCode() { return Arrays.hashCode(png); }

    /** {@code PixelImage[<count> bytes]}, as the SDK's value reads. */
    @Override public String toString() { return "PixelImage[" + png.length + " bytes]"; }
}
