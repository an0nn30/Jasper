package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Freedesktop Icon Theme Specification lookup over local directories: the theme, its Inherits chain,
 * then hicolor, then unthemed pixmaps. Reads files only; no native code or network.
 */
final class FreedesktopIcons {
    private static final System.Logger LOG = System.getLogger(FreedesktopIcons.class.getName());
    private static final List<String> EXTENSIONS = List.of("png", "svg");
    private final String theme;
    private final List<Path> bases;
    private final List<Path> pixmaps;
    private final Map<String, Optional<Theme>> themes = new ConcurrentHashMap<>();
    private final Map<String, Optional<Path>> files = new ConcurrentHashMap<>();
    private final Map<String, Optional<Image>> images = new ConcurrentHashMap<>();
    private final Set<Path> broken = ConcurrentHashMap.newKeySet();

    FreedesktopIcons(String theme, List<Path> bases, List<Path> pixmaps) {
        this.theme = IconThemeName.valid(theme) ? theme : null;
        this.bases = List.copyOf(bases);
        this.pixmaps = List.copyOf(pixmaps);
    }

    /** The specification's base directories, from the XDG environment. */
    static FreedesktopIcons fromEnvironment(String theme, Map<String, String> env, Path home) {
        var bases = new ArrayList<Path>();
        String dataHome = env.get("XDG_DATA_HOME");
        bases.add((dataHome == null || dataHome.isBlank() ? home.resolve(".local/share") : Path.of(dataHome)).resolve("icons"));
        bases.add(home.resolve(".icons"));
        String dataDirs = env.get("XDG_DATA_DIRS");
        for (String dir : (dataDirs == null || dataDirs.isBlank() ? "/usr/local/share:/usr/share" : dataDirs).split(":"))
            if (!dir.isBlank()) bases.add(Path.of(dir).resolve("icons"));
        return new FreedesktopIcons(theme, bases, List.of(Path.of("/usr/share/pixmaps")));
    }

    List<Path> bases() { return bases; }

    /** Theme names in search order: the theme, its ancestors depth-first, then hicolor. */
    List<String> chain() {
        var order = new LinkedHashSet<String>();
        if (theme != null) visit(theme, order);
        order.add("hicolor");
        return List.copyOf(order);
    }

    private void visit(String name, Set<String> order) {
        if (!order.add(name)) return;
        theme(name).ifPresent(found -> found.parents().forEach(parent -> visit(parent, order)));
    }

    /** The best file for the first candidate a theme in the chain provides, else an unthemed pixmap. */
    Optional<Path> find(List<String> names, int size, int scale) {
        return files.computeIfAbsent(String.join(",", names) + "@" + size + "x" + scale, key -> {
            for (String name : chain()) {
                var found = theme(name);
                if (found.isEmpty()) continue;
                for (String icon : names) {
                    var file = found.get().find(icon, size, scale);
                    if (file.isPresent()) return file;
                }
            }
            for (String icon : names) for (Path directory : pixmaps) for (String extension : EXTENSIONS) {
                Path file = directory.resolve(icon + "." + extension);
                if (Files.isRegularFile(file)) return Optional.of(file);
            }
            return Optional.empty();
        });
    }

    /** A size-by-size image with a 2x variant when available; symbolic icons take the given colour. */
    Optional<Image> image(List<String> names, int size, Color symbolic) {
        return images.computeIfAbsent(String.join(",", names) + "@" + size + "#" + Integer.toHexString(symbolic.getRGB()), key -> {
            BufferedImage base = find(names, size, 1).map(file -> render(file, size, symbolic)).orElse(null);
            if (base == null) return Optional.empty();
            BufferedImage large = find(names, size, 2).map(file -> render(file, size * 2, symbolic)).orElse(null);
            return Optional.<Image>of(new BaseMultiResolutionImage(large == null ? new Image[]{base} : new Image[]{base, large}));
        });
    }

    private Optional<Theme> theme(String name) { return themes.computeIfAbsent(name, key -> Theme.load(key, bases)); }

    private BufferedImage render(Path file, int pixels, Color symbolic) {
        if (broken.contains(file)) return null;
        try {
            BufferedImage source = file.getFileName().toString().endsWith(".svg") ? svg(file, pixels, symbolic) : ImageIO.read(file.toFile());
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) throw new IOException("unreadable image");
            return source.getWidth() == pixels && source.getHeight() == pixels ? source : scaled(source, pixels);
        } catch (IOException | RuntimeException failure) {
            if (broken.add(file)) LOG.log(System.Logger.Level.WARNING, "Desktop icon " + file + " could not be read; using bundled artwork", failure);
            return null;
        }
    }

    private static BufferedImage svg(Path file, int pixels, Color symbolic) throws IOException {
        var icon = new FlatSVGIcon(file.toUri().toURL()).derive(pixels, pixels);
        if (!icon.hasFound()) throw new IOException("unreadable SVG");
        if (file.getFileName().toString().contains("-symbolic"))
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(color ->
                new Color(symbolic.getRed(), symbolic.getGreen(), symbolic.getBlue(), color.getAlpha())));
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { icon.paintIcon(new javax.swing.JLabel(), graphics, 0, 0); } finally { graphics.dispose(); }
        return image;
    }

    private static BufferedImage scaled(BufferedImage source, int pixels) {
        var image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, pixels, pixels, null);
        } finally { graphics.dispose(); }
        return image;
    }

    record Directory(String path, int size, int scale, String type, int min, int max, int threshold) {
        boolean matches(int want, int wantScale) {
            if (scale != wantScale) return false;
            return switch (type) {
                case "Fixed" -> size == want;
                case "Scalable" -> min <= want && want <= max;
                default -> size - threshold <= want && want <= size + threshold;
            };
        }
        int distance(int want, int wantScale) {
            int low = switch (type) { case "Fixed" -> size; case "Scalable" -> min; default -> size - threshold; };
            int high = switch (type) { case "Fixed" -> size; case "Scalable" -> max; default -> size + threshold; };
            int target = want * wantScale;
            low *= scale; high *= scale;
            return target < low ? low - target : target > high ? target - high : 0;
        }
    }

    record Theme(List<Path> roots, List<String> parents, List<Directory> directories) {
        static Optional<Theme> load(String name, List<Path> bases) {
            if (!IconThemeName.valid(name)) return Optional.empty();
            var roots = bases.stream().map(base -> base.resolve(name)).filter(Files::isDirectory).toList();
            var index = roots.stream().map(root -> root.resolve("index.theme")).filter(Files::isRegularFile).findFirst();
            if (index.isEmpty()) return Optional.empty();
            try {
                var sections = sections(Files.readString(index.get(), StandardCharsets.UTF_8));
                var header = sections.getOrDefault("Icon Theme", Map.of());
                var parents = list(header.get("Inherits")).stream().filter(IconThemeName::valid).toList();
                var names = new LinkedHashSet<>(list(header.get("Directories")));
                names.addAll(list(header.get("ScaledDirectories")));
                var directories = new ArrayList<Directory>();
                for (String path : names) {
                    var values = sections.get(path);
                    Integer size = values == null || path.contains("..") ? null : integer(values.get("Size"));
                    if (size == null) continue;
                    directories.add(new Directory(path, size, or(integer(values.get("Scale")), 1),
                        values.getOrDefault("Type", "Threshold"), or(integer(values.get("MinSize")), size),
                        or(integer(values.get("MaxSize")), size), or(integer(values.get("Threshold")), 2)));
                }
                return Optional.of(new Theme(roots, parents, List.copyOf(directories)));
            } catch (IOException | RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Icon theme index " + index.get() + " could not be read; skipping " + name, failure);
                return Optional.empty();
            }
        }

        Optional<Path> find(String icon, int size, int scale) {
            for (Directory directory : directories) if (directory.matches(size, scale)) {
                Path file = file(directory, icon);
                if (file != null) return Optional.of(file);
            }
            Path best = null;
            int closest = Integer.MAX_VALUE;
            for (Directory directory : directories) {
                int distance = directory.distance(size, scale);
                if (distance >= closest) continue;
                Path file = file(directory, icon);
                if (file != null) { best = file; closest = distance; }
            }
            return Optional.ofNullable(best);
        }

        private Path file(Directory directory, String icon) {
            for (Path root : roots) for (String extension : EXTENSIONS) {
                Path file = root.resolve(directory.path()).resolve(icon + "." + extension);
                if (Files.isRegularFile(file)) return file;
            }
            return null;
        }

        private static Map<String, Map<String, String>> sections(String text) {
            var sections = new LinkedHashMap<String, Map<String, String>>();
            Map<String, String> current = null;
            for (String raw : text.split("\\R")) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    current = sections.computeIfAbsent(line.substring(1, line.length() - 1), key -> new HashMap<>());
                    continue;
                }
                int equals = line.indexOf('=');
                if (current != null && equals > 0) current.putIfAbsent(line.substring(0, equals).strip(), line.substring(equals + 1).strip());
            }
            return sections;
        }
        private static List<String> list(String value) {
            return value == null ? List.of() : Arrays.stream(value.split(",")).map(String::strip).filter(text -> !text.isEmpty()).toList();
        }
        private static Integer integer(String value) {
            try { return value == null ? null : Integer.valueOf(value.strip()); } catch (NumberFormatException malformed) { return null; }
        }
        private static int or(Integer value, int fallback) { return value != null ? value : fallback; }
    }
}
