package dev.jasper.editor.recent;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * {@code recent-files.toml}: an array of {@code [[file]]} tables with {@code key}, {@code name}, {@code location},
 * an optional {@code host} and {@code opened} as an ISO-8601 instant string. Blocking I/O.
 */
final class RecentFilesFile {
    private RecentFilesFile() { }

    /**
     * @return the entries in file order; a missing file is empty, an unreadable or malformed one is renamed to
     *     {@code <name>.bad} (replacing an older one) and is empty, and a malformed entry is skipped
     */
    static List<RecentFile> read(Path file) {
        if (!Files.exists(file)) return List.of();
        TomlParseResult toml;
        try { toml = Toml.parse(Files.readString(file, UTF_8)); }
        catch (IOException | RuntimeException unreadable) { setAside(file); return List.of(); }
        if (toml.hasErrors()) { setAside(file); return List.of(); }
        Object files = toml.get("file");
        if (files == null) return List.of();
        if (!(files instanceof TomlArray array)) { setAside(file); return List.of(); }
        var out = new ArrayList<RecentFile>();
        for (int i = 0; i < array.size(); i++) if (array.get(i) instanceof TomlTable table) entry(table).ifPresent(out::add);
        return out;
    }

    /** Writes a sibling {@code .tmp} file and moves it into place, atomically where the file system can. */
    static void write(Path file, List<RecentFile> entries) throws IOException {
        var out = new StringBuilder("# Files the Editor opened, newest first. Jasper rewrites this file.\n");
        for (RecentFile entry : entries) {
            out.append("\n[[file]]\n");
            out.append("key = ").append(string(entry.key())).append('\n');
            out.append("name = ").append(string(entry.name())).append('\n');
            out.append("location = ").append(string(entry.location())).append('\n');
            entry.host().ifPresent(host -> out.append("host = ").append(string(host)).append('\n'));
            out.append("opened = ").append(string(entry.opened().toString())).append('\n');
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, out, UTF_8);
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static Optional<RecentFile> entry(TomlTable table) {
        try {
            String key = table.getString("key"), name = table.getString("name"), location = table.getString("location"),
                opened = table.getString("opened");
            if (key == null || name == null || location == null || opened == null) return Optional.empty();
            Object host = table.get("host");
            if (host != null && !(host instanceof String)) return Optional.empty();
            return Optional.of(new RecentFile(key, name, location, Optional.ofNullable((String) host), Instant.parse(opened)));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static void setAside(Path file) {
        try { Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException ignored) { /* left in place; the next save replaces it */ }
    }

    /** A TOML basic string. */
    private static String string(String text) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20 || c == 0x7f) out.append(String.format(Locale.ROOT, "\\u%04X", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
