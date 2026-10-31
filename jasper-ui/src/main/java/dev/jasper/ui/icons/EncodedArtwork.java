package dev.jasper.ui.icons;

import java.util.Arrays;
import java.util.Objects;

/** Immutable authored SVG or PNG bytes. Receiving registries validate and decode once. */
public final class EncodedArtwork {
    public enum Format { SVG, PNG }
    private final Format format;
    private final byte[] data;
    private DecodedArtwork prepared;
    private IllegalArgumentException rejected;
    /** The icons {@link #icon(int)} made, by extent: the eight most recent. */
    private final java.util.Map<Integer, javax.swing.Icon> icons = new java.util.LinkedHashMap<>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Integer, javax.swing.Icon> eldest) { return size() > 8; }
    };
    public EncodedArtwork(Format format, byte[] data) {
        this.format=Objects.requireNonNull(format);
        Objects.requireNonNull(data);
        if(data.length==0 || data.length>256*1024) throw new IllegalArgumentException("Artwork must contain 1..262144 bytes");
        this.data=data.clone();
    }
    /** Eagerly validates/decodes on an owner worker. Direct registry users may omit this step. */
    public void prepare() { decoded(); }
    synchronized DecodedArtwork decoded() {
        if(rejected!=null)throw rejected;
        if(prepared==null)try {prepared=new DecodedArtwork(this);}catch(IllegalArgumentException failure){rejected=failure;throw failure;}
        return prepared;
    }
    public Format format(){return format;}
    public byte[] data(){return data.clone();}

    /**
     * The artwork drawn into a new {@code width} by {@code height} ARGB image, decoding it first if needed: an SVG is
     * fitted by its view box, a PNG scaled. For a caller that paints the same artwork at one size many times, such as a
     * splash; never cached.
     *
     * @param width the image's width in pixels, 1 to 8192
     * @param height the image's height in pixels, 1 to 8192
     * @return a new image
     * @throws IllegalArgumentException when the artwork is invalid or the size is out of range
     */
    public java.awt.image.BufferedImage raster(int width, int height) { return decoded().raster(width, height); }

    /**
     * The artwork drawn in an {@code extent} by {@code extent} box, as a registry draws authored artwork: scaled to fit,
     * its aspect and colours kept, centred. It decodes first if needed and prepares every density now, so its paint
     * allocates nothing; the icon for an extent is made once, so a caller that asks again gets the same icon.
     *
     * @param extent the box's side in user-space pixels (already scaled for the user's interface size), 1 to 512
     * @return the icon
     * @throws IllegalArgumentException when the artwork is invalid or the extent is out of range
     */
    public synchronized javax.swing.Icon icon(int extent) {
        if (extent < 1 || extent > 512) throw new IllegalArgumentException("Invalid logical icon size");
        var made = icons.get(extent);
        if (made != null) return made;
        made = fitted(decoded().snapshot(extent), extent);
        icons.put(extent, made);
        return made;
    }

    private static javax.swing.Icon fitted(java.awt.Image snapshot, int extent) {
        int width = snapshot.getWidth(null), height = snapshot.getHeight(null);
        return new javax.swing.Icon() {
            @Override public int getIconWidth() { return extent; }
            @Override public int getIconHeight() { return extent; }
            @Override public void paintIcon(java.awt.Component component, java.awt.Graphics graphics, int x, int y) {
                var g = (java.awt.Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(snapshot, x + (extent - width) / 2, y + (extent - height) / 2, width, height, null);
                } finally { g.dispose(); }
            }
        };
    }

    /**
     * Checks SVG bytes against the receiving rules without decoding or drawing them, so with no AWT: for a reader that
     * must refuse artwork before the desktop starts.
     *
     * @param data the SVG file
     * @throws IllegalArgumentException whose message names the rule the file breaks
     */
    public static void checkSvg(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0 || data.length > 256 * 1024) throw new IllegalArgumentException("Artwork must contain 1..262144 bytes");
        try { SvgPreflight.check(data); }
        catch (Exception refused) {
            String reason = refused.getMessage();
            throw new IllegalArgumentException(reason == null || reason.isBlank() ? refused.getClass().getSimpleName() : reason, refused);
        }
    }
    @Override public boolean equals(Object other){return other instanceof EncodedArtwork art && format==art.format && Arrays.equals(data,art.data);}
    @Override public int hashCode(){return 31*format.hashCode()+Arrays.hashCode(data);}
    @Override public String toString(){return "EncodedArtwork["+format+", "+data.length+" bytes]";}
}
