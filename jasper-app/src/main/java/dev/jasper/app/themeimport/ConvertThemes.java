package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.ThemeDocument;
import dev.jasper.app.appearance.ThemeResolver;
import dev.jasper.app.appearance.ThemeToml;
import dev.jasper.app.appearance.ThemeTomlWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Offline maintainer entry point. Validates all inputs/results before publishing deterministic native resources. */
public final class ConvertThemes {
    private ConvertThemes() {}
    public static void main(String[] args) throws IOException {
        var options = new LinkedHashMap<String, String>();
        if (args.length != 8) throw new IllegalArgumentException("Expected --manifest, --sources, --output, --notices");
        for (int i = 0; i < args.length; i += 2) {
            if (!List.of("--manifest", "--sources", "--output", "--notices").contains(args[i]) || options.put(args[i], args[i + 1]) != null)
                throw new IllegalArgumentException("Unknown/duplicate option " + args[i]);
        }
        generate(Path.of(options.get("--manifest")), Path.of(options.get("--sources")),
            Path.of(options.get("--output")), Path.of(options.get("--notices")));
    }

    static void generate(Path manifestFile, Path sources, Path output, Path notices) throws IOException {
        var manifest = ThemeSources.read(manifestFile);
        ThemeSources.verify(manifest, sources);
        var generated = new TreeMap<String, byte[]>();
        var notice = new StringBuilder("# Bundled themes\n\nGenerated offline from tools/themes/sources.json. Original authors and licenses are retained.\n\n");
        for (String id : List.of("jasper-dark", "jasper-light")) {
            String resource = "/dev/jasper/app/themes/" + id + ".toml";
            try (var input = ConvertThemes.class.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing Jasper source " + resource);
                generated.put(id + ".toml", input.readAllBytes());
            }
        }
        for (var source : manifest.sources()) {
            Path root = ThemeSources.safe(sources, source.id());
            Map<String, ConversionResult> converted = convert(source, root);
            notice.append("## ").append(source.id()).append("\n\n")
                .append(source.repository()).append(" at `").append(source.commit()).append("`.\n\n")
                .append("License: ").append(source.license()).append(". Credit when author is absent: ").append(source.author()).append(".\n\n");
            for (var variant : source.variants()) {
                if (!variant.included()) { notice.append("- Excluded `").append(variant.entry()).append("`: ").append(variant.exclusion()).append('\n'); continue; }
                ConversionResult result = converted.get(variant.entry());
                if (result == null) throw new IllegalArgumentException("Missing conversion " + source.id() + "/" + variant.entry());
                ThemeDocument document = result.document();
                if (!variant.override().isEmpty()) {
                    var base = document;
                    document = ThemeResolver.resolve(ThemeToml.parse(Files.readString(ThemeSources.safe(root, variant.override())), variant.override()),
                        name -> name.equals(base.name()) ? List.of(base) : List.of());
                }
                var metadata = new LinkedHashMap<>(document.metadata());
                if (metadata.getOrDefault("author", "").isBlank()) metadata.put("author", source.author());
                metadata.put("license", source.license()); metadata.put("source", source.repository()); metadata.put("commit", source.commit());
                var tokens = new java.util.EnumMap<dev.jasper.app.appearance.Token, java.awt.Color>(dev.jasper.app.appearance.Token.class);
                tokens.putAll(document.tokens());
                if (document.uiComplete().orElse(false)) tokens.keySet().retainAll(dev.jasper.app.appearance.WorkspaceTokenKeys
                    .explicitTokens(document.ui(), variant.name(), metadata.get("author"), document.dark().orElse(true)).keySet());
                document = new ThemeDocument(variant.id(), variant.name(), document.dark(), Optional.empty(), 0,
                    document.uiComplete(), metadata, document.seeds(), document.ansi(), tokens, document.syntax(), document.ui(), document.icons());
                byte[] bytes = ThemeTomlWriter.write(document).getBytes(StandardCharsets.UTF_8);
                ThemeResolver.resolve(ThemeToml.parse(new String(bytes, StandardCharsets.UTF_8), variant.id()));
                generated.put(variant.id() + ".toml", bytes);
                notice.append("- ").append(variant.name()).append(" (`").append(variant.id()).append("`); upstream: `").append(variant.entry()).append("`.");
                if (!variant.aliases().equals(List.of(variant.name()))) notice.append(" Previous names: ").append(String.join(", ", variant.aliases())).append('.');
                notice.append('\n');
                for (String warning : result.warnings()) notice.append("  - Conversion: ").append(warning.replace('\n', ' ')).append('\n');
            }
            notice.append('\n');
            for (var asset : source.assets()) {
                String name = Path.of(asset.path()).getFileName().toString();
                if (asset.path().equals(source.licenseFile()) || name.toLowerCase(java.util.Locale.ROOT).startsWith("notice"))
                    generated.put("licenses/" + source.id() + "/" + name, Files.readAllBytes(ThemeSources.safe(root, asset.path())));
            }
        }
        generated.put("legacy-aliases.json", legacyAliases(manifest).getBytes(StandardCharsets.UTF_8));
        String index = String.join("\n", generated.keySet().stream().filter(key -> key.endsWith(".toml")).toList()) + "\n";
        generated.put("index.txt", index.getBytes(StandardCharsets.UTF_8));
        byte[] noticeBytes = (notice.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8);
        generated.put("THIRD-PARTY-THEMES.md", noticeBytes);
        // Never delete an unknown file. Obsolete indexed outputs must be deliberately removed by the maintainer.
        if (Files.exists(output)) try (var files = Files.walk(output)) {
            for (Path file : files.toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Symlink in output: " + file);
                if (!Files.isRegularFile(file)) continue;
                String relative = output.relativize(file).toString().replace('\\', '/');
                if (!generated.containsKey(relative)) throw new IOException("Unknown output file; refusing to overwrite catalog: " + relative);
            }
        }
        for (var entry : generated.entrySet()) {
            Path file = output.resolve(entry.getKey()); Files.createDirectories(file.getParent()); Files.write(file, entry.getValue());
        }
        Files.createDirectories(notices.toAbsolutePath().getParent()); Files.write(notices, noticeBytes);
        System.out.println("Generated " + index.lines().count() + " themes from " + manifest.sources().size() + " locked sources");
    }

    private static Map<String, ConversionResult> convert(ThemeSources.Source source, Path root) throws IOException {
        var results = new LinkedHashMap<String, ConversionResult>();
        if (source.kind().equals("alacritty")) {
            for (var variant : source.variants()) if (variant.included())
                results.put(variant.entry(), ThemeConverter.convert(ThemeSources.safe(root, variant.entry())).getFirst());
            return results;
        }
        if (source.kind().equals("release")) {
            for (var result : ThemeConverter.convert(ThemeSources.safe(root, source.release()))) results.put(result.sourceEntry(), result);
            return results;
        }
        var variants = source.variants().stream().filter(ThemeSources.Variant::included).toList();
        // Keep the public importer's 64-provider limit unchanged even for Material's larger catalog.
        for (int offset = 0; offset < variants.size(); offset += 32) {
            Path archive = Files.createTempFile("jasper-theme-conversion-", ".jar");
            try {
                try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                    StringBuilder plugin = new StringBuilder("<idea-plugin><extensions defaultExtensionNs='com.intellij'>");
                    for (var variant : variants.subList(offset, Math.min(offset + 32, variants.size())))
                        plugin.append("<themeProvider path=\"").append(xml(variant.entry())).append("\"/>");
                    plugin.append("</extensions></idea-plugin>");
                    put(zip, "META-INF/plugin.xml", plugin.toString().getBytes(StandardCharsets.UTF_8));
                    for (var asset : source.assets()) for (String entry : asset.jarPaths()) {
                        if (entry.equals("META-INF/plugin.xml")) continue;
                        byte[] bytes = Files.readAllBytes(ThemeSources.safe(root, asset.path()));
                        var variant = source.variants().stream().filter(value -> value.entry().equals(entry)).findFirst();
                        if (variant.isPresent() && !variant.get().scheme().isEmpty()) {
                            Object parsed = com.formdev.flatlaf.json.Json.parse(new java.io.StringReader(new String(bytes, StandardCharsets.UTF_8)));
                            var json = new LinkedHashMap<String, Object>();
                            ((Map<?, ?>) parsed).forEach((key, value) -> json.put((String) key, value));
                            json.put("editorScheme", variant.get().scheme());
                            bytes = dev.jasper.app.appearance.FlatLafThemeData.write(json).getBytes(StandardCharsets.UTF_8);
                        }
                        put(zip, entry, bytes);
                    }
                }
                for (var result : ThemeConverter.convert(archive)) results.put(result.sourceEntry(), new ConversionResult(result.document(),
                    result.warnings().stream().map(warning -> warning.replace(archive.getFileName().toString(), source.id() + ".jar")).toList(), result.sourceEntry()));
            } finally { Files.deleteIfExists(archive); }
        }
        return results;
    }
    private static String legacyAliases(ThemeSources.Manifest manifest) {
        var names = new TreeMap<String, String>();
        var aliases = new TreeMap<String, String>();
        for (var source : manifest.sources()) for (var variant : source.variants()) if (variant.included()) {
            names.put(variant.name().strip().toLowerCase(java.util.Locale.ROOT), variant.name());
            for (String alias : variant.aliases()) aliases.putIfAbsent(alias.strip().toLowerCase(java.util.Locale.ROOT), variant.name());
        }
        var legacy = new LinkedHashMap<String, Object>();
        for (var source : manifest.sources()) if (source.id().equals("alacritty")) for (var variant : source.variants()) {
            String key = variant.name().strip().toLowerCase(java.util.Locale.ROOT);
            String canonical = names.getOrDefault(key, aliases.get(key));
            if (canonical == null) throw new IllegalArgumentException("Missing native replacement for legacy palette " + variant.name());
            legacy.put(key, canonical);
        }
        return dev.jasper.app.appearance.FlatLafThemeData.write(legacy) + "\n";
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
    private static String xml(String text) { return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;"); }
}
