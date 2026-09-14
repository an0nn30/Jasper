package dev.jasper.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tomlj.Toml;

final class CommandHistoryFile {
    private CommandHistoryFile() {}

    static List<String> read(Path file) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(16385);
        } catch (NoSuchFileException absent) {
            return List.of();
        }
        if (bytes.length > 16384) throw new IOException("Command history exceeds 16 KiB");
        var parsed = Toml.parse(decode(bytes));
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
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".command-history-", ".tmp");
        try {
            String values = String.join(", ", ids.stream().map(id -> "\"" + id + "\"").toList());
            Files.writeString(temporary, "version = 1\nrecent = [" + values + "]\n",
                StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String decode(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Invalid UTF-8 in command history", invalid);
        }
    }
}
