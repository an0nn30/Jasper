package dev.jasper.app.testsupport;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/** A branded look's files for tests (Enterprise plan 11): solid PNG icons of each size and a flat SVG art. */
public final class BrandingFixtures {
    /** Spec 5.1's [branding] table, with the folders {@link #files} writes. */
    public static final String TABLE = "[branding]\nproduct = \"JasperOps\"\nicons = \"icons\"\nsplash_art = \"splash/art.svg\"\n";
    /** The seven icon sizes. */
    public static final List<Integer> SIZES = List.of(16, 32, 48, 64, 128, 256, 1024);

    private BrandingFixtures() { }

    /** A solid blue PNG of the given size; the same bytes on every call. */
    public static byte[] png(int width, int height) { return png(width, height, 0xFF2F65CA); }

    /** A solid PNG of the given size and colour; the same bytes on every call. */
    public static byte[] png(int width, int height, int argb) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, argb);
        var bytes = new ByteArrayOutputStream();
        try { ImageIO.write(image, "png", bytes); }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return bytes.toByteArray();
    }

    /** A 718 by 250 SVG of one flat colour. */
    public static byte[] svg() {
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"718\" height=\"250\" viewBox=\"0 0 718 250\">"
            + "<rect width=\"718\" height=\"250\" fill=\"#1D4596\"/></svg>").getBytes(StandardCharsets.UTF_8);
    }

    /** Every icon and the art under {@code directory} (empty or ending in a slash), by jar path. */
    public static Map<String, byte[]> files(String directory) {
        var files = new LinkedHashMap<String, byte[]>();
        for (int size : SIZES) files.put(directory + "icons/icon-" + size + ".png", png(size, size));
        files.put(directory + "splash/art.svg", svg());
        return files;
    }

    /** A DEV presentation provider at {@code <root>/<id>} whose default-base kit has {@link #TABLE} and its files. */
    public static Path plugin(Path root, String id, String name) throws IOException {
        Path folder = LookFixtures.plugin(root, id, LookFixtures.descriptor(id, name) + LookFixtures.provider("look.kit.toml"),
            Map.of("look.kit.toml", LookFixtures.KIT + TABLE));
        Path jar = folder.resolve("jars").resolve(id + ".jar");
        for (var file : new TreeMap<>(files("")).entrySet()) PluginJars.addResource(jar, file.getKey(), file.getValue());
        return folder;
    }
}
