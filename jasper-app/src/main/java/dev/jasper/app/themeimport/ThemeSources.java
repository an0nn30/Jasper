package dev.jasper.app.themeimport;

import com.formdev.flatlaf.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Checked-in, data-only catalog lock. Neither validation nor conversion performs network access. */
public final class ThemeSources {
    public record Asset(String path, String upstream, String sha256, List<String> jarPaths) {
        public Asset { jarPaths = List.copyOf(jarPaths); }
    }
    public record Variant(String entry, String id, String name, List<String> aliases, String exclusion, String override, String scheme) {
        public Variant { aliases = List.copyOf(aliases); }
        public boolean included() { return exclusion.isEmpty(); }
    }
    public record Source(String id, String repository, String commit, String license, String licenseFile,
                         String author, String kind, String plugin, String release, List<Asset> assets, List<Variant> variants) {
        public Source { assets = List.copyOf(assets); variants = List.copyOf(variants); }
    }
    public record Manifest(List<Source> sources) {
        public Manifest { sources = List.copyOf(sources); }
    }
    private ThemeSources() {}

    public static Manifest read(Path path) throws IOException {
        Map<?, ?> root;
        try (var reader = Files.newBufferedReader(path)) { root = map(Json.parse(reader)); }
        if (!"1".equals(text(root, "version"))) throw invalid("Unsupported manifest version");
        var ids = new HashSet<>(List.of("jasper-dark", "jasper-light"));
        var names = new HashSet<>(List.of("jasper dark", "jasper light"));
        var sourceIds = new HashSet<String>();
        var sources = new ArrayList<Source>();
        for (Object item : list(root, "sources")) {
            var source = map(item);
            String id = text(source, "id");
            if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !sourceIds.add(id)) throw invalid("Invalid/duplicate source ID " + id);
            String commit = text(source, "commit");
            if (!commit.matches("[0-9a-f]{40}")) throw invalid("Not a full commit: " + id);
            String repository = text(source, "repository");
            if (!repository.matches("https://github.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw invalid("Invalid repository " + repository);
            var assets = new ArrayList<Asset>(); var paths = new HashSet<String>(); var jarPaths = new HashSet<String>();
            for (Object raw : list(source, "assets")) {
                var a = map(raw); String file = text(a, "path"); safe(Path.of("."), file);
                if (!paths.add(file)) throw invalid("Duplicate asset " + file);
                String hash = text(a, "sha256");
                if (!hash.matches("[0-9a-f]{64}")) throw invalid("Invalid SHA-256 " + file);
                List<String> entries = strings(a, "jarPaths");
                for (String entry : entries) { safe(Path.of("."), entry); if (!jarPaths.add(entry)) throw invalid("Duplicate archive path " + entry); }
                assets.add(new Asset(file, text(a, "upstream"), hash, entries));
            }
            String licenseFile = text(source, "licenseFile");
            if (!paths.contains(licenseFile)) throw invalid("Unrecorded license " + id);
            var variants = new ArrayList<Variant>(); var entries = new HashSet<String>();
            for (Object raw : list(source, "variants")) {
                var v = map(raw); String entry = text(v, "entry"); safe(Path.of("."), entry);
                if (!entries.add(entry)) throw invalid("Duplicate provider " + entry);
                String name = text(v, "name"), exclusion = optional(v, "exclude"), output = optional(v, "id");
                if (exclusion.isEmpty()) {
                    if (!output.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !ids.add(output)) throw invalid("Invalid/duplicate output ID " + output);
                    if (!names.add(name.strip().toLowerCase(Locale.ROOT))) throw invalid("Duplicate output name " + name);
                }
                String override = optional(v, "override");
                if (!override.isEmpty() && !paths.contains(override)) throw invalid("Unrecorded override " + override);
                variants.add(new Variant(entry, output, name, strings(v, "aliases"), exclusion, override, optional(v, "scheme")));
            }
            String kind = text(source, "kind");
            if (!List.of("intellij", "alacritty", "release").contains(kind)) throw invalid("Unknown source kind " + kind);
            String release = source.containsKey("release") ? text(map(source.get("release")), "asset") : "";
            if (!release.isEmpty() && !paths.contains(release)) throw invalid("Unrecorded release " + release);
            sources.add(new Source(id, repository, commit, text(source, "license"), licenseFile, text(source, "author"),
                kind, optional(source, "plugin"), release, assets, variants));
        }
        if (sources.isEmpty()) throw invalid("Empty catalog");
        return new Manifest(sources);
    }

    /** Validates the complete lock before any generated output is touched. */
    public static void verify(Manifest manifest, Path directory) throws IOException {
        for (Source source : manifest.sources()) {
            Path root = safe(directory, source.id());
            for (Asset asset : source.assets()) {
                Path path = safe(root, asset.path());
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 64L << 20)
                    throw invalid("Missing/oversized asset " + path);
                if (!hash(Files.readAllBytes(path)).equals(asset.sha256())) throw invalid("Asset hash mismatch: " + path);
            }
            if (!source.plugin().isEmpty()) {
                Path plugin = safe(root, source.plugin());
                if (source.assets().stream().noneMatch(a -> a.path().equals(source.plugin()))) throw invalid("Unrecorded plugin descriptor");
                verifyProviders(source, Files.readAllBytes(plugin));
            }
            if (!source.release().isEmpty()) try (var zip = new java.util.zip.ZipFile(safe(root, source.release()).toFile())) {
                var entry = zip.getEntry("META-INF/plugin.xml");
                if (entry == null || entry.getSize() > ThemeArchive.MAX_FILE) throw invalid("Missing/oversized release plugin descriptor");
                try (var input = zip.getInputStream(entry)) { verifyProviders(source, input.readNBytes(ThemeArchive.MAX_FILE + 1)); }
            }
        }
    }

    private static void verifyProviders(Source source, byte[] xml) {
        var root = SafeXml.parse(xml); var providers = root.getElementsByTagName("themeProvider");
        var actual = new HashSet<String>();
        for (int i = 0; i < providers.getLength(); i++) {
            String path = ((org.w3c.dom.Element) providers.item(i)).getAttribute("path");
            actual.add(path.startsWith("/") ? path.substring(1) : path);
        }
        var recorded = new HashSet<>(source.variants().stream().map(Variant::entry).toList());
        if (!actual.equals(recorded)) throw invalid("Unrecorded/missing provider variants in " + source.id());
    }

    static Path safe(Path root, String relative) {
        Path path = Path.of(relative);
        if (relative.isBlank() || path.isAbsolute() || relative.contains("\\")) throw invalid("Unsafe path " + relative);
        for (Path part : path) if (part.toString().equals("..") || part.toString().equals(".")) throw invalid("Escaping path " + relative);
        Path resolved = root.resolve(path);
        for (Path cursor = resolved; cursor != null; cursor = cursor.getParent())
            if (Files.isSymbolicLink(cursor)) throw invalid("Symlink in source path " + resolved);
        return resolved;
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static Map<?, ?> map(Object value) { if (value instanceof Map<?, ?> map) return map; throw invalid("Expected object"); }
    private static List<?> list(Map<?, ?> map, String key) { if (map.get(key) instanceof List<?> list) return list; throw invalid("Expected array " + key); }
    private static String text(Map<?, ?> map, String key) { String value = optional(map, key); if (value.isBlank()) throw invalid("Missing " + key); return value; }
    private static String optional(Map<?, ?> map, String key) { Object value = map.get(key); if (value == null) return ""; if (value instanceof String s) return s; throw invalid("Expected string " + key); }
    private static List<String> strings(Map<?, ?> map, String key) {
        if (!map.containsKey(key)) return List.of();
        return list(map, key).stream().map(value -> { if (value instanceof String s) return s; throw invalid("Expected string array " + key); }).toList();
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
