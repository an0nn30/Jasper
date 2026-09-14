package dev.jasper.app;

import java.awt.Point;
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
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;

/** The buddy's saved screen position: strict read, atomic write, never silently repaired. */
final class BuddyStateFile {
    private static final int MAX_BYTES = 4096;

    private BuddyStateFile() { }

    static Optional<Point> read(Path file) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        }
        if (bytes.length > MAX_BYTES) throw new IOException("Buddy state exceeds " + MAX_BYTES + " bytes");
        var parsed = Toml.parse(decode(bytes));
        if (parsed.hasErrors() || !parsed.keySet().equals(Set.of("version", "x", "y"))
                || !Long.valueOf(1).equals(parsed.get("version"))) {
            throw new IOException("Invalid buddy state version or format");
        }
        return Optional.of(new Point(coordinate(parsed.get("x")), coordinate(parsed.get("y"))));
    }

    private static int coordinate(Object value) throws IOException {
        if (value instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) return number.intValue();
        throw new IOException("Buddy state coordinates must be integers");
    }

    static void write(Path file, Point location) throws IOException {
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".buddy-", ".tmp");
        try {
            Files.writeString(temporary, "version = 1\nx = " + location.x + "\ny = " + location.y + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
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
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Invalid UTF-8 in buddy state", invalid);
        }
    }
}
