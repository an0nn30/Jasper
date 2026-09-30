package dev.jasper.app.files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** The local disk. Blocking; the model runs it on its worker. Not final so tests can observe closing. */
public class LocalBrowser implements Browser {
    private final Path home;

    public LocalBrowser() { this(Path.of(System.getProperty("user.home"))); }

    public LocalBrowser(Path home) { this.home = Objects.requireNonNull(home).toAbsolutePath().normalize(); }

    public Path homePath() { return home; }

    @Override public String home() { return home.toString(); }

    @Override public void list(String folder, Consumer<ChooserEntry> visitor) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(folder))) {
            for (Path child : stream) visitor.accept(entry(child));
        }
    }

    @Override public Optional<ChooserEntry> stat(String path) {
        Path target = Path.of(path);
        return Files.exists(target, LinkOption.NOFOLLOW_LINKS) ? Optional.of(entry(target)) : Optional.empty();
    }

    /** One entry; links are followed, and an entry whose details cannot be read is {@code OTHER}. */
    public static ChooserEntry entry(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        String name = absolute.getFileName() == null ? absolute.toString() : absolute.getFileName().toString();
        boolean hidden = name.startsWith(".") || hiddenAttribute(absolute);
        try {
            BasicFileAttributes attributes = Files.readAttributes(absolute, BasicFileAttributes.class);
            ChooserEntry.Kind kind = attributes.isDirectory() ? ChooserEntry.Kind.DIRECTORY
                : attributes.isRegularFile() ? ChooserEntry.Kind.FILE : ChooserEntry.Kind.OTHER;
            return new ChooserEntry(absolute.toString(), name, kind, kind == ChooserEntry.Kind.DIRECTORY ? -1 : attributes.size(),
                attributes.lastModifiedTime().toMillis(), hidden);
        } catch (IOException | RuntimeException unreadable) {
            return new ChooserEntry(absolute.toString(), name, ChooserEntry.Kind.OTHER, -1, -1, hidden);
        }
    }

    private static boolean hiddenAttribute(Path path) {
        try { return Files.isHidden(path); } catch (IOException | RuntimeException unknown) { return false; }
    }

    @Override public byte[] read(String path, int maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(Path.of(path))) { return input.readNBytes(maxBytes); }
    }

    @Override public void mkdir(String path) throws IOException { Files.createDirectory(Path.of(path)); }

    @Override public Optional<String> parent(String path) { return Optional.ofNullable(Path.of(path).getParent()).map(Path::toString); }

    @Override public String name(String path) {
        Path target = Path.of(path);
        return target.getFileName() == null ? target.toString() : target.getFileName().toString();
    }

    @Override public String resolve(String folder, String text) {
        try {
            Path resolved;
            if (text.equals("~")) resolved = home;
            else if (text.startsWith("~/") || text.startsWith("~" + File.separator)) resolved = home.resolve(text.substring(2));
            else {
                Path typed = Path.of(text);
                resolved = typed.isAbsolute() ? typed : Path.of(folder).resolve(typed);
            }
            return resolved.toAbsolutePath().normalize().toString();
        } catch (InvalidPathException invalid) {
            throw new IllegalArgumentException(invalid.getMessage(), invalid);
        }
    }

    @Override public void abort() { }

    @Override public void close() { }
}
