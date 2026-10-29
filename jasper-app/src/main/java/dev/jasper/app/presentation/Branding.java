package dev.jasper.app.presentation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * A look's branding (spec 5.1, 9.1 and 10.11): the product name its splash shows, its application icons by pixel size
 * and its splash art. {@link #NONE} is a look without {@code [branding]}, which keeps Jasper's icon and uses the default Protean splash;
 * the default look and the fallback presentation have it. Holds the files' bytes, which {@link KitParser} checks without
 * decoding them, so the bootstrap pre-pass touches no AWT; the splash and the application icon decode them. Immutable;
 * compared by content.
 */
public final class Branding {
    /** The icon sizes a branded look ships, in pixels, smallest first. */
    public static final List<Integer> SIZES = List.of(16, 32, 48, 64, 128, 256, 1024);
    /** No branding: Jasper's icon and the default Protean splash. */
    public static final Branding NONE = new Branding("", new TreeMap<>(), new byte[0]);

    private final String product;
    private final TreeMap<Integer, byte[]> icons;
    private final byte[] splashArt;

    private Branding(String product, TreeMap<Integer, byte[]> icons, byte[] splashArt) {
        this.product = product;
        this.icons = icons;
        this.splashArt = splashArt;
    }

    /**
     * A look's branding.
     *
     * @param product the name the splash shows, 1 to 32 characters
     * @param icons one PNG file for each of {@link #SIZES}, or empty to use Jasper's platform icons
     * @param splashArt the splash art's SVG file
     * @return the branding, holding copies
     * @throws IllegalArgumentException when a part is missing
     */
    public static Branding of(String product, Map<Integer, byte[]> icons, byte[] splashArt) {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(icons, "icons");
        Objects.requireNonNull(splashArt, "splashArt");
        if (product.isBlank() || product.codePointCount(0, product.length()) > 32)
            throw new IllegalArgumentException("A product name has 1 to 32 characters: " + product);
        if (!icons.isEmpty() && !new TreeSet<>(icons.keySet()).equals(new TreeSet<>(SIZES)))
            throw new IllegalArgumentException("Branding needs one icon of each size " + SIZES + ", not " + new TreeSet<>(icons.keySet()));
        if (splashArt.length == 0) throw new IllegalArgumentException("Branding needs its splash art");
        var copies = new TreeMap<Integer, byte[]>();
        icons.forEach((size, file) -> copies.put(size, Objects.requireNonNull(file, "icon " + size).clone()));
        return new Branding(product, copies, splashArt.clone());
    }

    /** Whether the look is branded. */
    public boolean present() { return !product.isEmpty(); }

    /** The name the splash shows; empty for {@link #NONE}. */
    public String product() { return product; }

    /**
     * The PNG file of one icon size.
     *
     * @param size one of {@link #SIZES}
     * @return a copy of the file
     * @throws IllegalArgumentException for {@link #NONE} or a size the look does not ship
     */
    public byte[] icon(int size) {
        byte[] file = icons.get(size);
        if (file == null) throw new IllegalArgumentException("No " + size + " px icon" + (present() ? "" : ": the look has no branding"));
        return file.clone();
    }

    /** Every custom icon's PNG file, smallest first; empty when using Jasper's platform icons. */
    public List<byte[]> iconFiles() { return icons.values().stream().map(byte[]::clone).toList(); }

    /** The splash art's SVG file; empty for {@link #NONE}. */
    public byte[] splashArt() { return splashArt.clone(); }

    /** The files in the order the look's digest reads them: the icons smallest first, then the splash art. */
    public List<byte[]> files() {
        return present() ? Stream.concat(iconFiles().stream(), Stream.of(splashArt())).toList() : List.of();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof Branding branding) || !product.equals(branding.product) || !Arrays.equals(splashArt, branding.splashArt)
            || !icons.keySet().equals(branding.icons.keySet())) return false;
        for (var icon : icons.entrySet()) if (!Arrays.equals(icon.getValue(), branding.icons.get(icon.getKey()))) return false;
        return true;
    }

    @Override public int hashCode() {
        int hash = Objects.hash(product, Arrays.hashCode(splashArt));
        for (var icon : icons.entrySet()) hash = 31 * hash + 31 * icon.getKey() + Arrays.hashCode(icon.getValue());
        return hash;
    }

    @Override public String toString() {
        return present() ? "Branding[" + product + ", icons " + icons.keySet() + ", art " + splashArt.length + " bytes]" : "Branding.NONE";
    }
}
