package dev.jasper.app.plugins;

import dev.jasper.app.config.ConfigLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/**
 * Each plugin's settings file, {@code plugins/<id>/<id>.toml}: seeded once when missing, read into the
 * map {@code PluginSettings} serves, re-read when its size or modification time changes. A file that
 * fails to parse keeps its last good values and is reported under {@code plugins.<id>}.
 */
final class PluginSettingsFiles {
    static final String TEMPLATE = "settings.toml";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final System.Logger LOG = System.getLogger(PluginSettingsFiles.class.getName());

    private record Fingerprint(FileTime modified, long size) { }
    private static final class Known { Fingerprint fingerprint; Map<String, Object> values = Map.of(); boolean broken; }

    private final Path userDirectory;
    private final BiConsumer<String, String> report;
    private final Map<String, Known> known = new HashMap<>();

    PluginSettingsFiles(Path userDirectory, BiConsumer<String, String> report) { this.userDirectory = userDirectory; this.report = report; }

    static Path folder(Path userDirectory, String id) { return userDirectory.resolve(id); }
    static Path file(Path userDirectory, String id) { return folder(userDirectory, id).resolve(id + ".toml"); }
    static Path data(Path userDirectory, String id) { return folder(userDirectory, id).resolve("data"); }

    /** Creates the folder and data directory and seeds a missing settings file; an existing file is never touched. */
    static void prepare(Path userDirectory, PluginCandidate candidate, Map<String, Object> legacyTableOrNull) throws IOException {
        String id = candidate.id();
        Files.createDirectories(data(userDirectory, id));
        Path file = file(userDirectory, id);
        if (Files.exists(file)) return;
        Files.writeString(file, seed(candidate, legacyTableOrNull), StandardCharsets.UTF_8);
    }

    /** The old {@code [plugins."<id>"]} table when it has keys, else the jar's {@code settings.toml}, else a header. */
    static String seed(PluginCandidate candidate, Map<String, Object> legacyTableOrNull) {
        PluginDescriptor descriptor = candidate.descriptor();
        String header = "# Settings for " + descriptor.name() + " " + descriptor.version() + " (" + descriptor.id() + "). Jasper reads this file live.\n";
        if (legacyTableOrNull != null && !legacyTableOrNull.isEmpty()) return header + "# Moved here from config.toml.\n\n" + TomlText.write(legacyTableOrNull);
        return template(candidate).orElse(header);
    }

    /** The plugin's own example, a {@code settings.toml} resource beside {@code plugin.toml} in any of its jars. */
    static Optional<String> template(PluginCandidate candidate) {
        for (Path jar : candidate.jars()) {
            try (var file = new JarFile(jar.toFile())) {
                var entry = file.getEntry(TEMPLATE);
                if (entry == null) continue;
                try (var input = file.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                    if (bytes.length > MAX_BYTES) return Optional.empty();
                    return Optional.of(new String(bytes, StandardCharsets.UTF_8));
                }
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Could not read " + TEMPLATE + " from " + jar, failure);
            }
        }
        return Optional.empty();
    }

    /** The last good values for {@code id}; reads the file on first use. Safe from any thread. */
    synchronized Map<String, Object> current(String id) {
        if (!known.containsKey(id)) { known.put(id, new Known()); read(id, known.get(id), true); }
        return known.get(id).values;
    }

    /** Re-reads every known file whose fingerprint changed ({@code force}: all of them); the ids whose values changed. */
    synchronized Set<String> refresh(boolean force) {
        Set<String> changed = new LinkedHashSet<>();
        for (var entry : known.entrySet()) if (read(entry.getKey(), entry.getValue(), force)) changed.add(entry.getKey());
        return changed;
    }

    private boolean read(String id, Known state, boolean force) {
        Path file = file(userDirectory, id);
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            var next = new Fingerprint(attributes.lastModifiedTime(), attributes.size());
            if (!force && next.equals(state.fingerprint)) return false;
            state.fingerprint = next;
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("larger than 1 MiB");
            TomlParseResult parsed = Toml.parse(new String(bytes, StandardCharsets.UTF_8));
            if (parsed.hasErrors()) throw new IOException(parsed.errors().getFirst().toString());
            Map<String, Object> values = ConfigLoader.freeze(parsed);
            state.broken = false;
            if (values.equals(state.values)) return false;
            state.values = values;
            return true;
        } catch (NoSuchFileException absent) {
            state.fingerprint = null;
            boolean had = !state.values.isEmpty();
            state.values = Map.of();
            return had;
        } catch (IOException failure) {
            if (!state.broken) report.accept("plugins." + id, file.getFileName() + " could not be read: " + failure.getMessage() + "; the last good settings stay in force.");
            state.broken = true;
            return false;
        }
    }
}
