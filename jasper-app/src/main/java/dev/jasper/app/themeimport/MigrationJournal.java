package dev.jasper.app.themeimport;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

/** Bounded migration intent/progress; relative paths are validated before any recovery action is permitted. */
final class MigrationJournal {
    static final int MAX_BYTES = 4 << 20;
    record Output(String provider, String name, String path, String hash, boolean published, String chromeKey, String workspaceKey) {
        Output publishedOutput() { return new Output(provider, name, path, hash, true, chromeKey, workspaceKey); }
    }
    record Source(String path, String hash, String fileKey, String legacy, boolean convertible, boolean moved, boolean blocked,
                  List<Output> outputs, List<String> problems) {
        Source { outputs = List.copyOf(outputs); problems = List.copyOf(problems); }
        Source progress(List<Output> values, boolean relocated, boolean stopped, List<String> diagnostics) {
            return new Source(path, hash, fileKey, legacy, convertible, relocated, stopped, values, diagnostics);
        }
    }
    record State(String generation, List<Source> sources, java.util.Map<String, String> workspaceAliases) {
        State { sources = List.copyOf(sources); workspaceAliases = java.util.Map.copyOf(workspaceAliases); }
        State replacing(int index, Source source) { var next = new ArrayList<>(sources); next.set(index, source); return new State(generation, next, workspaceAliases); }
    }
    private final Path home;
    private final Path file;
    private String expectedHash;
    MigrationJournal(Path home) throws IOException { this.home = home.toRealPath(); this.file = this.home.resolve("themes/legacy/migration.toml"); }
    Path file() { return file; }

    State read() throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) { expectedHash = null; return null; }
        byte[] bytes = readBounded(file, MAX_BYTES);
        try {
            var toml = Toml.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
            if (toml.hasErrors()) throw new IOException("Invalid migration journal: " + toml.errors().getFirst());
            if (!Long.valueOf(1).equals(toml.getLong("version"))) throw new IOException("Unsupported migration journal version");
            String generation = required(toml, "generation");
            if (!generation.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")) throw new IOException("Invalid journal generation");
            var sources = new ArrayList<Source>();
            var sourcePaths = new HashSet<String>(); var outputPaths = new HashSet<String>(); var legacyPaths = new HashSet<String>();
            var array = toml.getArray("sources");
            if (array == null || array.size() > 4096) throw new IOException("Missing/oversized source list");
            for (int i = 0; i < array.size(); i++) {
                var source = array.getTable(i); String path = required(source, "path"), legacy = required(source, "legacy");
                sourcePath(path); legacyPath(legacy);
                if (!sourcePaths.add(path) || !legacyPaths.add(legacy.toLowerCase(java.util.Locale.ROOT))) throw new IOException("Duplicate journal source/destination");
                String hash = required(source, "hash"), fileKey = required(source, "file_key"); hash(hash);
                boolean convertible = bool(source, "convertible"), moved = bool(source, "moved"), blocked = bool(source, "blocked");
                var outputs = new ArrayList<Output>(); var providers = new HashSet<String>();
                var out = source.getArray("outputs");
                if (out == null || out.size() > ThemeArchive.MAX_PROVIDERS) throw new IOException("Missing/oversized output list");
                for (int j = 0; j < out.size(); j++) {
                    var output = out.getTable(j); String target = required(output, "path"); outputPath(target);
                    if (!outputPaths.add(target.toLowerCase(java.util.Locale.ROOT))) throw new IOException("Duplicate journal output");
                    String provider = required(output, "provider");
                    if (!providers.add(provider)) throw new IOException("Duplicate provider identity");
                    String digest = required(output, "hash"); hash(digest);
                    String name = required(output, "name");
                    String chrome = string(output, "chrome_key"), workspace = string(output, "workspace_key");
                    if (!chrome.equals(ThemeImporter.key(chrome)) || !workspace.equals(ThemeImporter.key(workspace))) throw new IOException("Unnormalized rename key");
                    outputs.add(new Output(provider, name, target, digest, bool(output, "published"), chrome, workspace));
                }
                if (moved && (!convertible || blocked || outputs.stream().anyMatch(output -> !output.published()))) throw new IOException("Inconsistent moved source progress");
                var problems = new ArrayList<String>(); var problemArray = source.getArray("problems");
                if (problemArray == null || problemArray.size() > 4096) throw new IOException("Invalid diagnostics list");
                for (int j = 0; j < problemArray.size(); j++) problems.add(problemArray.getString(j));
                sources.add(new Source(path, hash, fileKey, legacy, convertible, moved, blocked, outputs, problems));
            }
            expectedHash = ThemeSources.hash(bytes);
            var aliases = new java.util.LinkedHashMap<String, String>();
            var aliasTable = toml.getTable("workspace_aliases");
            if (aliasTable != null) for (String key : aliasTable.keySet()) {
                if (!key.equals(ThemeImporter.key(key))) throw new IOException("Unnormalized workspace alias");
                aliases.put(key, required(aliasTable, key));
            }
            return new State(generation, sources, aliases);
        } catch (RuntimeException invalid) { throw new IOException("Invalid migration journal: " + invalid.getMessage(), invalid); }
    }

    void write(State state) throws IOException {
        StringBuilder text = new StringBuilder("version = 1\ngeneration = ").append(quoted(state.generation())).append('\n');
        if (state.sources().isEmpty()) text.append("sources = []\n");
        if (!state.workspaceAliases().isEmpty()) {
            text.append("\n[workspace_aliases]\n");
            new java.util.TreeMap<>(state.workspaceAliases()).forEach((key, name) -> value(text, quoted(key), name));
        }
        for (Source source : state.sources()) {
            text.append("\n[[sources]]\n");
            value(text, "path", source.path()); value(text, "hash", source.hash()); value(text, "file_key", source.fileKey()); value(text, "legacy", source.legacy());
            text.append("convertible = ").append(source.convertible()).append("\nmoved = ").append(source.moved()).append("\nblocked = ").append(source.blocked()).append('\n');
            text.append("problems = [").append(String.join(", ", source.problems().stream().map(MigrationJournal::quoted).toList())).append("]\n");
            if (source.outputs().isEmpty()) text.append("outputs = []\n");
            for (Output output : source.outputs()) {
                text.append("\n[[sources.outputs]]\n"); value(text, "provider", output.provider()); value(text, "name", output.name());
                value(text, "path", output.path()); value(text, "hash", output.hash());
                text.append("published = ").append(output.published()).append('\n'); value(text, "chrome_key", output.chromeKey()); value(text, "workspace_key", output.workspaceKey());
            }
        }
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Migration journal is larger than 4 MiB");
        atomic(file, bytes, expectedHash);
        expectedHash = ThemeSources.hash(bytes);
    }

    Path resolve(String relative) throws IOException {
        Path path = home.resolve(relative);
        if (!path.normalize().equals(path) || !path.startsWith(home)) throw new IOException("Journal path escapes home");
        Path current = home;
        for (Path part : home.relativize(path)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic link in migration path: " + relative);
        }
        return path;
    }
    static void sourcePath(String path) throws IOException {
        if (!path.matches("(?:themes|terminal-themes)/[^/\\\\]+") || Path.of(path).getFileName().toString().startsWith(".")) throw new IOException("Unsafe journal source path: " + path);
        for (Path part : Path.of(path)) if (part.toString().equals("..")) throw new IOException("Traversing journal source");
    }
    static void outputPath(String path) throws IOException { if (!path.matches("themes/[a-z0-9][a-z0-9-]*\\.toml")) throw new IOException("Unsafe journal output path: " + path); }
    static void legacyPath(String path) throws IOException { if (!path.matches("themes/legacy/[a-z0-9][a-z0-9.-]*") || path.endsWith(".")) throw new IOException("Unsafe journal legacy path: " + path); }
    private static void hash(String hash) throws IOException { if (!hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid journal hash"); }
    private static String required(TomlTable table, String key) throws IOException { String value = string(table, key); if (value.isBlank()) throw new IOException("Missing journal " + key); return value; }
    private static String string(TomlTable table, String key) throws IOException { String value = table.getString(List.of(key)); if (value == null) throw new IOException("Missing journal " + key); return value; }
    private static boolean bool(TomlTable table, String key) throws IOException { Boolean value = table.getBoolean(key); if (value == null) throw new IOException("Missing journal " + key); return value; }
    private static void value(StringBuilder out, String key, String value) { out.append(key).append(" = ").append(quoted(value)).append('\n'); }
    static String quoted(String value) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') out.append('\\').append(c);
            else if (c < 0x20 || c == 0x7f) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
    static byte[] readBounded(Path file, int limit) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a regular migration file: " + file);
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException(file.getFileName() + " is larger than " + (limit == MAX_BYTES ? "4 MiB" : limit + " bytes"));
            return bytes;
        }
    }
    static void atomic(Path target, byte[] bytes, String expected) throws IOException {
        ThemeFiles.publishState(target, bytes, expected);
    }
}
