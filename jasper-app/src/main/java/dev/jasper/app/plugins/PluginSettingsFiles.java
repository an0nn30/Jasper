package dev.jasper.app.plugins;

import dev.jasper.app.config.ConfigDiagnostic;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;

/**
 * Each plugin's settings file, {@code plugins/<id>/<id>.toml}: seeded once when missing, read into the
 * map {@code PluginSettings} serves, re-read when its size or modification time changes. A file that fails to
 * parse keeps its last good values and is reported against that file; recovering from that counts as a change.
 */
final class PluginSettingsFiles {
    static final String TEMPLATE = "settings.toml";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final System.Logger LOG = System.getLogger(PluginSettingsFiles.class.getName());

    private record Fingerprint(FileTime modified, long size) { }
    /** A 1-based line and column in a plugin's settings file. */
    record Position(int line, int column) { }

    private static final class Known { Fingerprint fingerprint; Map<String, Object> values = Map.of(); String text = ""; TomlParseResult parsed; boolean broken; }

    private final Path userDirectory;
    private final Consumer<ConfigDiagnostic> report;
    private final Map<String, Known> known = new HashMap<>();

    PluginSettingsFiles(Path userDirectory, Consumer<ConfigDiagnostic> report) { this.userDirectory = userDirectory; this.report = report; }

    /** Where {@code path} is in the plugin's file as last read successfully. */
    synchronized Optional<Position> position(String id, List<String> path) {
        Known state = known.get(id);
        if (state == null || state.parsed == null) return Optional.empty();
        TomlPosition position = state.parsed.inputPositionOf(path);
        return position == null ? Optional.empty() : Optional.of(new Position(position.line(), position.column()));
    }

    static Path folder(Path userDirectory, String id) { return userDirectory.resolve(id); }
    static Path file(Path userDirectory, String id) { return folder(userDirectory, id).resolve(id + ".toml"); }
    static Path data(Path userDirectory, String id) { return folder(userDirectory, id).resolve("data"); }

    /** This store's settings file for {@code id}. */
    Path file(String id) { return file(userDirectory, id); }

    /** The file's last good text for {@code id}, empty when it is absent; reads the file on first use. Safe from any thread. */
    synchronized String text(String id) {
        current(id);
        return known.get(id).text;
    }

    /**
     * Edits the plugin's file: reads it (empty when absent), applies {@code edit}, and writes the result atomically
     * unless nothing changed, then re-reads it here. Refuses a file that grew past 1 MiB or changed during the edit.
     * Off the UI thread; {@code edit} may throw to refuse the change.
     */
    synchronized void save(String id, java.util.function.UnaryOperator<String> edit) throws IOException {
        Path file = file(userDirectory, id);
        var original = dev.jasper.app.persistence.TomlStateFile.readBounded(file, MAX_BYTES, "Plugin settings");
        String updated = edit.apply(original.orElse(""));
        if (original.isPresent() && updated.equals(original.get())) return;
        if (updated.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Plugin settings exceed 1 MiB.");
        if (!dev.jasper.app.persistence.TomlStateFile.readBounded(file, MAX_BYTES, "Plugin settings").equals(original))
            throw new IOException("Settings changed while saving. Try again.");
        dev.jasper.app.persistence.TomlStateFile.writeAtomically(file, ".settings-", updated);
        Known state = known.computeIfAbsent(id, key -> new Known());
        read(id, state, true);
    }

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

    /** Re-reads every known file whose fingerprint changed ({@code force}: all of them); the ids whose values changed or that recovered from a broken file. */
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
            String text = new String(bytes, StandardCharsets.UTF_8);
            TomlParseResult parsed = Toml.parse(text);
            if (parsed.hasErrors()) {
                TomlParseError error = parsed.errors().getFirst();
                broken(state, file, error.position().line(), error.position().column(), error.toString());
                return false;
            }
            Map<String, Object> values = ConfigLoader.freeze(parsed);
            boolean recovered = state.broken;
            state.broken = false;
            state.parsed = parsed;
            state.text = text;
            if (values.equals(state.values) && !recovered) return false;
            state.values = values;
            return true;
        } catch (NoSuchFileException absent) {
            state.fingerprint = null;
            state.parsed = null;
            state.text = "";
            boolean had = !state.values.isEmpty() || state.broken;
            state.broken = false;
            state.values = Map.of();
            return had;
        } catch (IOException failure) {
            broken(state, file, 0, 0, failure.getMessage());
            return false;
        }
    }

    private void broken(Known state, Path file, int line, int column, String why) {
        if (!state.broken) report.accept(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, file, line, column, "",
            file.getFileName() + " could not be read: " + why + "; the last good settings stay in force."));
        state.broken = true;
    }
}
