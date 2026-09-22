package dev.jasper.snippets;

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

/** Bounded reads and atomic writes of the snippets file; a copy of the application's own state-file helper, because a plugin cannot see it. */
public final class StateFile {
    private StateFile() { }

    /** Bounded, strictly decoded read of a small TOML state file; empty when the file is absent. */
    public static Optional<String> readBounded(Path file, int maxBytes, String what) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(maxBytes + 1);
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        }
        if (bytes.length > maxBytes) throw new IOException(what + " exceeds " + maxBytes + " bytes");
        return Optional.of(decode(bytes, what));
    }

    /** Writes text to a temp file beside the target, then moves it into place atomically when the filesystem allows. */
    public static void writeAtomically(Path file, String prefix, String text) throws IOException {
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), prefix, ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String decode(byte[] bytes, String what) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Invalid UTF-8 in " + what, invalid);
        }
    }
}
