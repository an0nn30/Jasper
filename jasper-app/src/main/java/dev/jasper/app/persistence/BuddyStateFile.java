package dev.jasper.app.persistence;

import java.awt.Point;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;

/** The buddy's saved screen position: strict read, atomic write, never silently repaired. */
public final class BuddyStateFile {
    private static final int MAX_BYTES = 4096;

    private BuddyStateFile() { }

    public static Optional<Point> read(Path file) throws IOException {
        var textOpt = TomlStateFile.readBounded(file, MAX_BYTES, "Buddy state");
        if (textOpt.isEmpty()) return Optional.empty();
        var parsed = Toml.parse(textOpt.get());
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

    public static void write(Path file, Point location) throws IOException {
        TomlStateFile.writeAtomically(file, ".buddy-", "version = 1\nx = " + location.x + "\ny = " + location.y + "\n");
    }
}
