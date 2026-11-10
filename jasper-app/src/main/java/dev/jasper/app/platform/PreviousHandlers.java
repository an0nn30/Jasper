package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * The handler each file type had before Jasper claimed it, kept in {@code file-associations.toml} as one table per
 * type: {@code [previous."<type>"]} with {@code id} and {@code name}; an empty {@code id} and {@code name} is "no handler", and an empty {@code id} with a
 * name is an application Windows names without a ProgId. Writes go
 * through a temporary file in the same folder and an atomic move. A file that cannot be read counts as empty and is
 * logged once, and the unreadable file is moved to {@code file-associations.toml.bad} so no write replaces it; a write that fails is logged and swallowed, so the store never blocks a claim.
 */
final class PreviousHandlers {
    private static final System.Logger LOG = System.getLogger(PreviousHandlers.class.getName());

    private final Path file;
    private boolean readFailureLogged;

    PreviousHandlers(Path file) {
        this.file = file;
    }

    synchronized Optional<FileAssociations.Handler> get(String type) {
        return Optional.ofNullable(read().get(type));
    }

    synchronized void put(String type, FileAssociations.Handler handler) {
        Map<String, FileAssociations.Handler> all = read();
        all.put(type, handler);
        write(all);
    }

    synchronized void remove(String type) {
        Map<String, FileAssociations.Handler> all = read();
        if (all.remove(type) != null) {
            write(all);
        }
    }

    private Map<String, FileAssociations.Handler> read() {
        Map<String, FileAssociations.Handler> out = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return out;
        }
        try {
            TomlParseResult parsed = Toml.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (parsed.hasErrors()) {
                throw new IOException(parsed.errors().getFirst().toString());
            }
            TomlTable previous = parsed.getTable("previous");
            if (previous != null) {
                for (String type : previous.keySet()) {
                    TomlTable entry = previous.getTable(java.util.List.of(type));
                    if (entry == null) {
                        continue;
                    }
                    String id = entry.getString("id", () -> "");
                    String name = entry.getString("name", () -> id);
                    out.put(type, id.isEmpty() && name.isEmpty()
                            ? new FileAssociations.Handler.None()
                            : new FileAssociations.Handler.Other(id, name));
                }
            }
            readFailureLogged = false;
        } catch (IOException | RuntimeException e) {
            out.clear();
            Path bad = file.resolveSibling(file.getFileName() + ".bad");
            String kept = "";
            try {
                // Keep the unreadable file: the next write must not wipe what it may still hold.
                Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
                kept = "; moved it to " + bad.getFileName();
            } catch (IOException | RuntimeException moveFailure) {
                e.addSuppressed(moveFailure);
            }
            if (!readFailureLogged) {
                readFailureLogged = true;
                LOG.log(System.Logger.Level.WARNING, "Could not read " + file + "; treating it as empty" + kept, e);
            }
        }
        return out;
    }

    private void write(Map<String, FileAssociations.Handler> all) {
        Path temp = null;
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            temp = Files.createTempFile(dir, "file-associations", ".tmp");
            Files.writeString(temp, render(all), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temp = null;
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not write " + file, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // the temporary file is left behind; nothing more can be done
                }
            }
        }
    }

    private static String render(Map<String, FileAssociations.Handler> all) {
        StringBuilder out = new StringBuilder("# Handlers Jasper replaced when it claimed file types.\n");
        for (Map.Entry<String, FileAssociations.Handler> entry : all.entrySet()) {
            String id = "";
            String name = "";
            if (entry.getValue() instanceof FileAssociations.Handler.Other other) {
                id = other.id();
                name = other.name();
            }
            out.append("\n[previous.").append(quote(entry.getKey())).append("]\n")
                    .append("id = ").append(quote(id)).append('\n')
                    .append("name = ").append(quote(name)).append('\n');
        }
        return out.toString();
    }

    /** A TOML basic string. */
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        out.append(String.format("\\u%04X", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
