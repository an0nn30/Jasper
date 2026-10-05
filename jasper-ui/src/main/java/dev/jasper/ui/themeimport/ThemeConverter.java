package dev.jasper.ui.themeimport;

import com.formdev.flatlaf.json.Json;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.*;
import dev.jasper.ui.theme.TerminalColors;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline, bounded conversion shared by imports, migration and bundle generation. No plugin classes are loaded. */
public final class ThemeConverter {
    /** A source/provider that could not be converted. */
    public record Failure(String sourceEntry, String message) {}
    /** Successful providers remain available even when a sibling provider fails. */
    public record Batch(List<ConversionResult> results, List<Failure> failures) {
        public Batch { results = List.copyOf(results); failures = List.copyOf(failures); }
    }
    /** Maximum archive input size, in bytes (64 MiB). */
    public static final long MAX_ARCHIVE = ThemeArchive.MAX_ARCHIVE;
    /** Maximum individual archive entry or legacy source size, in bytes (4 MiB). */
    public static final int MAX_FILE = ThemeArchive.MAX_FILE;
    /** Maximum declared theme providers inspected per archive (64). */
    public static final int MAX_PROVIDERS = ThemeArchive.MAX_PROVIDERS;
    /** Maximum nested JAR files inspected per archive (16). */
    public static final int MAX_NESTED_JARS = ThemeArchive.MAX_NESTED_JARS;
    /** Authored legacy token keys, for bundle conversion without vendor heuristics. */
    public static Map<Token, java.awt.Color> explicitTokens(Map<String, Object> ui) {
        return WorkspaceTokenKeys.explicitTokens(ui);
    }
    private static final int MAX_NATIVE_FILE = 256 * 1024;
    private ThemeConverter() {}

    /** Strict conversion; callers needing partial results use {@link #convertAll(Path)}. */
    public static List<ConversionResult> convert(Path source) {
        Batch batch = convertAll(source);
        if (!batch.failures().isEmpty()) throw new IllegalArgumentException(batch.failures().toString());
        return batch.results();
    }

    /** Converts every supported local provider, retaining contextual failures and fallback warnings. */
    public static Batch convertAll(Path source) { return convertAll(source, false); }

    /** Converts an archive regardless of filename suffix, including staged {@code .part} downloads.
     * Applies the same bounded validation and partial-failure behavior as {@link #convertAll(Path)}. */
    public static Batch archive(Path source) { return convertAll(source, true); }

    private static Batch convertAll(Path source, boolean archive) {
        var results = new ArrayList<ConversionResult>();
        var failures = new ArrayList<Failure>();
        String entry = source.getFileName().toString();
        try {
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a regular theme source");
            String lower = entry.toLowerCase(Locale.ROOT);
            if (archive || lower.endsWith(".jar") || lower.endsWith(".zip")) {
                ThemeArchive.Contents contents = ThemeArchive.read(source);
                contents.problems().forEach(problem -> failures.add(new Failure(entry, problem)));
                for (ThemeArchive.Found found : contents.themes()) {
                    String id = found.label();
                    int bang = id.indexOf("!/");
                    if (bang >= 0) id = id.substring(bang + 2);
                    try {
                        var names = new LinkedHashMap<>(ThemeParents.FILES);
                        found.parents().keySet().forEach(name -> names.put(name, "archive-parent:" + name));
                        var loader = new ThemeLoader(name -> name.equals(found.label()) ? found.json()
                            : name.startsWith("archive-parent:") ? found.parents().get(name.substring(15)) : ThemeParents.read(name), names);
                        var resolved = loader.load(found.label());
                        EditorScheme.Result scheme = found.schemeXml() != null
                            ? EditorScheme.read(found.schemeXml(), found.schemeLabel(), resolved.dark(), found.schemeParents()::get)
                            : found.schemeName() != null ? EditorScheme.named(found.schemeName(), found.schemeLabel(), resolved.dark())
                            : missingScheme(resolved.dark());
                        results.add(converted(resolved, scheme, id));
                    } catch (RuntimeException failure) { failures.add(new Failure(id, message(failure))); }
                }
            } else if (lower.endsWith(".theme.json")) results.add(standalone(source));
            else if (lower.endsWith(".toml")) results.add(toml(source));
            else throw new IOException("Supported sources are .theme.json, .jar, .zip and .toml (not YAML)");
        } catch (IOException | RuntimeException | StackOverflowError failure) {
            failures.add(new Failure(entry, message(failure)));
        }
        return new Batch(results, failures);
    }

    private static ConversionResult standalone(Path source) throws IOException {
        Path root = source.toAbsolutePath().normalize().getParent();
        var names = new LinkedHashMap<>(ThemeParents.FILES);
        String own = source.getFileName().toString();
        String json = read(source);
        // Resolve only requested local parent names: do not scan unrelated files in a user's folder.
        var local = new LinkedHashMap<String, String>();
        local.put(own, json);
        String current = json;
        for (int depth = 0; depth < 32; depth++) {
            Object parent = object(current).get("parentTheme");
            if (!(parent instanceof String name) || names.containsKey(name)) break;
            String filename = name.endsWith(".theme.json") ? name : name + ".theme.json";
            Path path = child(root, filename);
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) break;
            names.put(name, filename);
            current = read(path); local.put(filename, current);
        }
        var resolved = new ThemeLoader(name -> local.containsKey(name) ? local.get(name) : ThemeParents.read(name), names).load(own);
        Object schemeRef = object(json).get("editorScheme");
        EditorScheme.Result scheme = missingScheme(resolved.dark());
        if (schemeRef instanceof String name) {
            if (name.toLowerCase(Locale.ROOT).endsWith(".xml") || name.toLowerCase(Locale.ROOT).endsWith(".icls")) {
                Path file = child(root, name);
                byte[] xml = read(file).getBytes(StandardCharsets.UTF_8);
                SafeXml.parse(xml); // A rejected XML input is not a successful import with an unnoticed fallback.
                scheme = EditorScheme.read(xml, name, resolved.dark(), parent -> {
                    try {
                        for (String suffix : List.of(".xml", ".icls")) {
                            Path candidate = child(file.getParent(), parent + suffix);
                            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return read(candidate).getBytes(StandardCharsets.UTF_8);
                        }
                        return null;
                    } catch (IOException failure) { throw new UncheckedIOException(failure); }
                });
            } else scheme = EditorScheme.named(name, name, resolved.dark());
        }
        return converted(resolved, scheme, own);
    }

    private static ConversionResult converted(ThemeLoader.Resolved resolved, EditorScheme.Result scheme, String entry) {
        var warnings = new ArrayList<>(resolved.warnings()); warnings.addAll(scheme.warnings());
        ThemeDocument document = IntellijConversion.convert(resolved, scheme, entry);
        // Apply the exact native validation before a conversion can be offered for publication.
        document = ThemeToml.parse(ThemeTomlWriter.write(document), entry);
        return new ConversionResult(document, warnings, entry);
    }

    private static ConversionResult toml(Path source) throws IOException {
        String text = read(source);
        var parsed = org.tomlj.Toml.parse(text);
        if (parsed.hasErrors()) throw new IllegalArgumentException(parsed.errors().getFirst().toString());
        if (parsed.contains("colors.primary")) {
            var palette = AlacrittyTheme.read(text);
            if (palette.palette().isEmpty()) throw new IllegalArgumentException(String.join("; ", palette.problems()));
            return new ConversionResult(AlacrittyConversion.convert(AlacrittyTheme.displayName(source.getFileName().toString()),
                palette.palette().orElseThrow(), source.getFileName().toString()), palette.problems(), source.getFileName().toString());
        }
        ThemeDocument document = ThemeToml.parse(text, source.getFileName().toString());
        var available = new ArrayList<ThemeDocument>();
        // Match catalog semantics: declared names, local filename order, then bundled layers.
        Path root = source.toAbsolutePath().normalize().getParent();
        try (var siblings = Files.list(root)) {
            var paths = siblings.filter(path -> !path.getFileName().toString().startsWith(".")
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".toml")
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .limit(4097)
                .sorted(java.util.Comparator.comparing((Path path) -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(path -> path.getFileName().toString()))
                .toList();
            if (paths.size() > 4096) throw new IOException("Native import folder has more than 4096 theme files");
            long total = 0;
            for (Path path : paths) {
                if (path.equals(source.toAbsolutePath().normalize())) { available.add(document); continue; }
                byte[] content;
                try {
                    if (Files.size(path) > MAX_NATIVE_FILE) continue;
                    content = readBounded(path, MAX_NATIVE_FILE);
                } catch (IOException invalid) { continue; }
                total += content.length;
                if (total > ThemeArchive.MAX_ARCHIVE) throw new IOException("Native import parents exceed 64 MiB");
                try {
                    available.add(ThemeToml.parse(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(content)).toString(),
                        path.getFileName().toString()));
                } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException invalid) { /* Broken siblings do not shadow valid parents. */ }
            }
        }
        if (!available.contains(document)) available.addFirst(document);
        // A bundled layer is matched by name before its document is read, which parses its whole file.
        var flattened = ThemeResolver.resolve(document, name -> java.util.stream.Stream.concat(
            available.stream().filter(item -> item.name().strip().equalsIgnoreCase(name.strip())),
            NativeThemeParents.named(name).stream()).toList());
        var filtered = ImportedColors.filter(flattened.ui());
        flattened = new ThemeDocument(flattened.origin(), flattened.name(), flattened.dark(), flattened.parent(),
            flattened.parentLine(), flattened.uiComplete(), flattened.metadata(), flattened.seeds(), flattened.ansi(),
            flattened.tokens(), flattened.syntax(), filtered.colors(), flattened.icons(), flattened.workspace(), flattened.semanticOverrides());
        return new ConversionResult(flattened, filtered.warnings(), source.getFileName().toString());
    }

    private static EditorScheme.Result missingScheme(boolean dark) {
        return new EditorScheme.Result(dark ? ImportPalettes.dark() : ImportPalettes.light(),
            List.of("no console colours: used Jasper " + (dark ? "Dark" : "Light") + "'s"));
    }

    private static Path child(Path root, String relative) throws IOException {
        Path input = Path.of(relative);
        if (input.isAbsolute() || relative.contains("\\") || input.toString().isEmpty()) throw new IOException("Unsafe theme sidecar path: " + relative);
        for (Path part : input) if (part.toString().equals("..")) throw new IOException("Theme sidecar escapes its source folder: " + relative);
        Path target = root.resolve(input).normalize();
        Path cursor = root;
        for (Path part : root.relativize(target)) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new IOException("Theme sidecar is a symbolic link: " + relative);
        }
        return target;
    }

    private static byte[] readBounded(Path file, int limit) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a regular theme file: " + file);
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException(file.getFileName() + " is larger than " + limit + " bytes");
            return bytes;
        }
    }
    private static String read(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a regular theme file: " + file.getFileName());
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(ThemeArchive.MAX_FILE + 1);
            if (bytes.length > ThemeArchive.MAX_FILE) throw new IOException(file.getFileName() + " is larger than 4 MiB");
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
    }
    private static Map<?, ?> object(String json) {
        try {
            Object value = Json.parse(new StringReader(json));
            if (value instanceof Map<?, ?> map) return map;
            throw new IllegalArgumentException("Theme must be a JSON object");
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static String message(Throwable failure) { return failure.getMessage() == null ? failure.toString() : failure.getMessage(); }
}
