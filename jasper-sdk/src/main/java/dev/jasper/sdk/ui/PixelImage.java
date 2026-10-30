package dev.jasper.sdk.ui;

import java.util.Arrays;
import java.util.Objects;

/**
 * A small PNG the plugin supplies, such as a web page's favicon: the only bitmap the SDK takes. Everywhere else the SDK
 * takes an icon name, whose artwork Jasper draws. Jasper decodes the PNG once and draws it scaled into the box of the
 * icon it stands for, keeping its aspect and its own colours; a {@link ContentPresentation#withImage(PixelImage) content tab} and a
 * {@link Nodes.Field#leadingImage() field} take one. The value holds its own copy of the bytes, and equal bytes make
 * equal images.
 *
 * @param png the PNG file's bytes: at most {@value #MAX_BYTES}, starting with the PNG signature
 * @since 0.27.0
 */
public record PixelImage(byte[] png) {
    /** The most bytes a pixel image holds. */
    public static final int MAX_BYTES = 65_536;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    /**
     * Copies the bytes.
     *
     * @throws IllegalArgumentException when there are more than {@value #MAX_BYTES} bytes, or they do not start with the
     *     PNG signature
     */
    public PixelImage {
        // An array's length never changes, so it is checked before the copy; the signature is checked on the copy, so
        // bytes the caller changes meanwhile cannot slip past the check.
        if (Objects.requireNonNull(png, "png").length > MAX_BYTES) throw new IllegalArgumentException("A pixel image holds at most " + MAX_BYTES + " bytes, not " + png.length);
        png = png.clone();
        if (png.length < SIGNATURE.length || !Arrays.equals(png, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length))
            throw new IllegalArgumentException("A pixel image must be a PNG");
    }

    /**
     * The PNG file's bytes.
     *
     * @return a copy of the bytes
     */
    @Override public byte[] png() { return png.clone(); }

    /** Equal when the bytes are. */
    @Override public boolean equals(Object other) { return other instanceof PixelImage image && Arrays.equals(png, image.png); }

    @Override public int hashCode() { return Arrays.hashCode(png); }

    /** {@code PixelImage[<count> bytes]}. */
    @Override public String toString() { return "PixelImage[" + png.length + " bytes]"; }
}
