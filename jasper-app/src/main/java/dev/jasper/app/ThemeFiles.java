package dev.jasper.app;

import dev.jasper.terminal.Palette;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Objects;

/** Bounded custom-theme file reader with fingerprint caching and last-good recovery. */
final class ThemeFiles {
    record Result(Palette palette, List<ConfigDiagnostic> diagnostics) {
        Result {
            Objects.requireNonNull(palette, "palette");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record Key(Path requested, Path real, FileTime modified, long size) { }
    private static final int MAX_BYTES = 256 * 1024;

    private final Path directory;
    private Palette lastGood = Palette.jasperDarkPurple();
    private Key lastKey;
    private Result cached;

    ThemeFiles(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    Result refresh(ColorsConfig colors, boolean force) {
        Objects.requireNonNull(colors, "colors");
        if (!colors.custom()) {
            lastKey = null;
            cached = null;
            lastGood = BuiltinTheme.fromId(colors.theme()).palette();
            return new Result(lastGood, List.of());
        }

        Path requested = directory;
        try {
            requested = directory.resolve(colors.theme().endsWith(".toml")
                ? colors.theme() : colors.theme() + ".toml");
            Path root = directory.toRealPath();
            Path real = requested.toRealPath();
            if (!real.startsWith(root)) throw new IOException("Theme must stay inside Jasper's themes directory.");
            BasicFileAttributes attributes = Files.readAttributes(real, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) throw new IOException("Theme must be a readable regular file.");
            if (attributes.size() > MAX_BYTES) throw new IOException("Theme exceeds 256 KiB.");
            Key key = new Key(requested, real, attributes.lastModifiedTime(), attributes.size());
            if (!force && key.equals(lastKey)) return cached;

            byte[] bytes;
            try (var input = Files.newInputStream(real)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("Theme exceeds 256 KiB.");
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
            var parsed = ThemeLoader.parse(requested, text);
            if (!parsed.rejected()) lastGood = parsed.palette();
            lastKey = key;
            cached = new Result(lastGood, parsed.diagnostics());
            return cached;
        } catch (IOException | InvalidPathException | SecurityException failure) {
            lastKey = null;
            cached = error(requested, failure.getMessage());
            return cached;
        }
    }

    private Result error(Path requested, String detail) {
        String message = detail == null || detail.isBlank()
            ? "Cannot read theme; check the file and its permissions, then reload." : detail;
        return new Result(lastGood, List.of(new ConfigDiagnostic(
            ConfigDiagnostic.Severity.ERROR, requested, 0, 0, "", message)));
    }
}
