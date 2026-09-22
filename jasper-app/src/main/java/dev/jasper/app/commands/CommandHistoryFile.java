package dev.jasper.app.commands;

import dev.jasper.app.persistence.TomlStateFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tomlj.Toml;

/** Bounded command-history file codec. Callers select the I/O thread; each operation owns and closes its own file handles. */
public final class CommandHistoryFile {
    private CommandHistoryFile() {}

    public static List<String> read(Path file) throws IOException {
        var textOpt = TomlStateFile.readBounded(file, 16384, "Command history");
        if (textOpt.isEmpty()) return List.of();
        var parsed = Toml.parse(textOpt.get());
        if (parsed.hasErrors() || !parsed.keySet().equals(Set.of("version", "recent"))
                || !Long.valueOf(1).equals(parsed.get("version"))) {
            throw new IOException("Invalid command history version or format");
        }
        Object value = parsed.get("recent");
        if (!(value instanceof org.tomlj.TomlArray array) || array.size() > 3) {
            throw new IOException("Command history needs at most three IDs");
        }
        var ids = new LinkedHashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (!(item instanceof String id) || !id.matches("[a-z][a-z0-9_.-]{0,127}")) {
                throw new IOException("Invalid command history ID");
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    static void write(Path file, List<String> ids) throws IOException {
        if (ids.size() > 3
                || ids.stream().anyMatch(id -> id == null
                    || !id.matches("[a-z][a-z0-9_.-]{0,127}"))) {
            throw new IOException("Invalid command history snapshot");
        }
        String values = String.join(", ", ids.stream().map(id -> "\"" + id + "\"").toList());
        TomlStateFile.writeAtomically(file, ".command-history-", "version = 1\nrecent = [" + values + "]\n");
    }
}
