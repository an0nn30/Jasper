package dev.jasper.app.commands;

import dev.jasper.app.persistence.TomlStateFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tomlj.Toml;

/** Bounded command-history file codec. Callers select the I/O thread; each operation owns and closes its own file handles. */
public final class CommandHistoryFile {
    private CommandHistoryFile() {}

    /** Both lists a history file holds; version 1 files have no uses. */
    public record Saved(List<String> recent, List<PaletteUse> uses) { }

    public static List<String> read(Path file) throws IOException { return readAll(file).recent(); }

    public static Saved readAll(Path file) throws IOException {
        var textOpt = TomlStateFile.readBounded(file, 131072, "Command history");
        if (textOpt.isEmpty()) return new Saved(List.of(), List.of());
        var parsed = Toml.parse(textOpt.get());
        Object version = parsed.get("version");
        boolean two = Long.valueOf(2).equals(version);
        Set<String> allowed = two ? Set.of("version", "recent", "used") : Set.of("version", "recent");
        if (parsed.hasErrors() || !(two || Long.valueOf(1).equals(version))
                || !parsed.keySet().contains("recent") || !allowed.containsAll(parsed.keySet())) {
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
        var uses = new ArrayList<PaletteUse>();
        Object used = parsed.get("used");
        if (used != null) {
            if (!(used instanceof org.tomlj.TomlArray tables) || tables.size() > CommandHistory.MAX_USES)
                throw new IOException("Command history holds at most " + CommandHistory.MAX_USES + " palette uses");
            for (int i = 0; i < tables.size(); i++) {
                if (!(tables.get(i) instanceof org.tomlj.TomlTable table)
                        || !table.keySet().equals(Set.of("scope", "row", "title", "count", "last"))
                        || !(table.get("scope") instanceof String scope) || !(table.get("row") instanceof String row)
                        || !(table.get("title") instanceof String title) || !(table.get("count") instanceof Long count)
                        || !(table.get("last") instanceof Long last) || count < 1 || count > 1_000_000 || last < 0
                        || !PaletteUse.valid(scope, row, title)) {
                    throw new IOException("Invalid palette use in command history");
                }
                uses.add(new PaletteUse(scope, row, title, count.intValue(), last));
            }
        }
        return new Saved(List.copyOf(ids), List.copyOf(uses));
    }

    static void write(Path file, List<String> ids) throws IOException { write(file, ids, List.of()); }

    static void write(Path file, List<String> ids, List<PaletteUse> uses) throws IOException {
        if (ids.size() > 3 || uses.size() > CommandHistory.MAX_USES
                || ids.stream().anyMatch(id -> id == null
                    || !id.matches("[a-z][a-z0-9_.-]{0,127}"))) {
            throw new IOException("Invalid command history snapshot");
        }
        String values = String.join(", ", ids.stream().map(id -> "\"" + id + "\"").toList());
        var text = new StringBuilder("version = ").append(uses.isEmpty() ? 1 : 2).append("\nrecent = [").append(values).append("]\n");
        for (PaletteUse use : uses)
            text.append("\n[[used]]\nscope = ").append(quote(use.scopeId())).append("\nrow = ").append(quote(use.rowId()))
                .append("\ntitle = ").append(quote(use.title())).append("\ncount = ").append(use.count())
                .append("\nlast = ").append(use.lastUsed()).append('\n');
        TomlStateFile.writeAtomically(file, ".command-history-", text.toString());
    }

    /** A TOML basic string; an unpaired surrogate, which UTF-8 cannot carry, becomes the replacement character. */
    static String quote(String value) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                out.append(c).append(value.charAt(++i));
            } else if (Character.isSurrogate(c)) out.append("\\ufffd");
            else if (c == '"') out.append("\\\"");
            else if (c == '\\') out.append("\\\\");
            else if (c < 0x20 || c == 0x7f) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
