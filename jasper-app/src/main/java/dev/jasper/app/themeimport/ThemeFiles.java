package dev.jasper.app.themeimport;
import dev.jasper.ui.themeimport.ThemeSources;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.ui.theme.ThemeDocument;
import dev.jasper.ui.theme.ThemeResolver;
import dev.jasper.ui.theme.ThemeGenerator;
import dev.jasper.ui.themeio.ThemeToml;
import dev.jasper.ui.themeio.ThemeTomlWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Safe native-theme publication. A home lock coordinates Jasper writers (including cooperating processes).
 * Replacement rechecks observed identity and bytes immediately before atomic rename; Java cannot make that rename
 * a compare-and-swap against an arbitrary external editor racing the final syscall. Creation never replaces a file.
 */
public final class ThemeFiles {
    private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
    private final Path home;
    private final BiConsumer<String, Path> fault;

    public ThemeFiles(Path home) { this(home, (stage, path) -> {}); }
    ThemeFiles(Path home, BiConsumer<String, Path> fault) {
        this.home = Objects.requireNonNull(home).toAbsolutePath().normalize(); this.fault = Objects.requireNonNull(fault);
    }

    /** Creates a new direct-child native file with a safe, unoccupied ASCII filename. */
    public Path create(ThemeDocument document) throws IOException {
        worker();
        byte[] bytes = serialized(document);
        return locked(() -> {
            Path directory = directory();
            String stem = fileId(document.name());
            Path target = unoccupied(directory, stem);
            publish(target, bytes, null);
            return target;
        });
    }

    /** Journal publication: the pre-recorded path must remain free; never allocates another name on retry. */
    Path createAt(Path requested, ThemeDocument document) throws IOException {
        worker(); byte[] bytes = serialized(document);
        return locked(() -> {
            Path directory = directory();
            Path absolute = requested.toAbsolutePath();
            if (!absolute.equals(absolute.normalize()) || absolute.getParent() == null
                    || !absolute.getParent().toRealPath().equals(directory)
                    || !absolute.getFileName().toString().matches("[a-z0-9][a-z0-9-]*\\.toml"))
                throw new IOException("Unsafe planned theme path");
            Path target = directory.resolve(absolute.getFileName());
            try (var children = Files.list(directory)) {
                if (children.anyMatch(path -> path.getFileName().toString().equalsIgnoreCase(target.getFileName().toString())))
                    throw new IOException("Planned theme path is occupied");
            }
            publish(target, bytes, null); return target;
        });
    }

    /** Replaces only the canonical regular user file whose observed bytes still match the expected hash. */
    public void replace(Path target, String expectedSha256, ThemeDocument document) throws IOException {
        worker();
        byte[] bytes = serialized(document);
        locked(() -> {
            Path checked = target(target);
            if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")) throw new IOException("Missing expected theme fingerprint");
            var before = Files.readAttributes(checked, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            requireHash(checked, expectedSha256);
            publish(checked, bytes, () -> {
                Path again = target(checked);
                var after = Files.readAttributes(again, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!Objects.equals(before.fileKey(), after.fileKey())) throw new IOException("Theme file identity changed; replan the import");
                requireHash(again, expectedSha256);
                return null;
            });
            return null;
        });
    }

    public String fingerprint(Path target) throws IOException { worker(); return locked(() -> hash(target(target))); }

    /** Shared reentrant lock for importer and migration; no nested OS file-lock acquisition. */
    <T> T locked(Callable<T> action) throws IOException {
        worker();
        if (Files.isSymbolicLink(home)) throw new IOException("Theme home is a symbolic link");
        Files.createDirectories(home);
        Path canonical = home.toRealPath();
        ReentrantLock lock = LOCKS.computeIfAbsent(canonical, ignored -> new ReentrantLock());
        lock.lock();
        try {
            if (lock.getHoldCount() > 1) return call(action);
            Path lockFile = canonical.resolve(".theme-files.lock");
            if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Unsafe theme lock file");
            try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 var ignored = channel.lock()) {
                return call(action);
            }
        } finally { lock.unlock(); }
    }

    Path directory() throws IOException {
        if (Files.isSymbolicLink(home)) throw new IOException("Theme home is a symbolic link");
        Path directory = home.toRealPath().resolve("themes");
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Themes directory is not a regular directory");
        } else Files.createDirectory(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("Themes directory is a symbolic link");
        return directory;
    }

    static byte[] serialized(ThemeDocument document) throws IOException {
        try {
            String text = ThemeTomlWriter.write(document);
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length > ThemeCatalog.MAX_THEME_FILE) throw new IOException("Theme exceeds native file limit");
            var parsed = ThemeToml.parse(text, document.origin());
            ThemeGenerator.generate(ThemeResolver.resolve(parsed));
            return bytes;
        } catch (RuntimeException invalid) { throw new IOException("Invalid native theme: " + invalid.getMessage(), invalid); }
    }

    static String fileId(String name) {
        String result = Normalizer.normalize(name, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
            .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (result.isEmpty()) result = "theme";
        if (result.matches("con|prn|aux|nul|com[0-9]|lpt[0-9]")) result = "theme-" + result;
        if (result.length() > 80) result = result.substring(0, 80).replaceAll("-+$", "");
        return result;
    }

    private Path unoccupied(Path directory, String stem) throws IOException {
        var occupied = new HashSet<String>();
        try (var children = Files.list(directory)) {
            children.forEach(path -> occupied.add(path.getFileName().toString().toLowerCase(Locale.ROOT)));
        }
        String candidate = stem + ".toml";
        for (int suffix = 2; occupied.contains(candidate.toLowerCase(Locale.ROOT)); suffix++) candidate = stem + "-" + suffix + ".toml";
        return directory.resolve(candidate);
    }

    private Path target(Path requested) throws IOException {
        Path absolute = requested.toAbsolutePath();
        if (!absolute.equals(absolute.normalize()) || absolute.getParent() == null) throw new IOException("Unsafe theme target path");
        Path directory = directory();
        if ((!absolute.getParent().equals(home.resolve("themes")) && !absolute.getParent().equals(directory))
                || !absolute.getParent().toRealPath().equals(directory)) throw new IOException("Theme target is outside the themes directory");
        String name = absolute.getFileName().toString();
        if (name.startsWith(".") || !name.toLowerCase(Locale.ROOT).endsWith(".toml")) throw new IOException("Target is not a native user theme");
        Path result = directory.resolve(name);
        if (!Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Theme target is not a regular file");
        try (var children = Files.list(directory)) {
            if (children.noneMatch(path -> path.getFileName().toString().equals(name))) throw new IOException("Noncanonical case alias for theme target");
        }
        return result;
    }

    private static String hash(Path target) throws IOException {
        try (var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(ThemeCatalog.MAX_THEME_FILE + 1);
            if (bytes.length > ThemeCatalog.MAX_THEME_FILE) throw new IOException("Theme exceeds native file limit");
            return ThemeSources.hash(bytes);
        }
    }
    private static void requireHash(Path target, String expected) throws IOException {
        if (!hash(target).equals(expected)) throw new IOException("Theme changed since planning; replan the import");
    }

    private void publish(Path target, byte[] bytes, Callable<Void> replacementCheck) throws IOException {
        publishIn(directory(), target, bytes, replacementCheck, fault, this::directory);
    }

    /** Same publication/owned-cleanup guarantees for bounded journal and marker bytes. */
    static void publishState(Path target, byte[] bytes, String expectedHash) throws IOException {
        Path parent = target.getParent();
        Callable<Path> guard = () -> {
            for (Path cursor = target; cursor != null; cursor = cursor.getParent())
                if (Files.isSymbolicLink(cursor)) throw new IOException("Symbolic link in migration state path");
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe migration state directory");
            return parent;
        };
        guardChecked(guard);
        Object before = expectedHash == null ? null : identity(target);
        Callable<Void> check = expectedHash == null ? null : () -> {
            if (!Objects.equals(before, identity(target))
                    || !ThemeSources.hash(MigrationJournal.readBounded(target, MigrationJournal.MAX_BYTES)).equals(expectedHash))
                throw new IOException("Migration state changed unexpectedly");
            return null;
        };
        publishIn(parent, target, bytes, check, (stage, path) -> {}, guard);
    }
    private static Path guardChecked(Callable<Path> guard) throws IOException { return call(guard); }

    private static void publishIn(Path directory, Path target, byte[] bytes, Callable<Void> replacementCheck,
                                  BiConsumer<String, Path> fault, Callable<Path> guard) throws IOException {
        publishIn(directory, target, bytes, replacementCheck, fault, guard, null);
    }

    private static void publishIn(Path directory, Path target, byte[] bytes, Callable<Void> replacementCheck,
                                  BiConsumer<String, Path> fault, Callable<Path> guard, Callable<Void> creationCheck) throws IOException {
        Object directoryKey = identity(directory);
        var handle = Files.newDirectoryStream(directory);
        Path temporary = null; Object temporaryKey = null;
        try {
            temporary = Files.createTempFile(directory, ".jasper-theme-", ".tmp");
            temporaryKey = identity(temporary);
            fault.accept("temp", temporary);
            if (!guardChecked(guard).equals(directory) || !Objects.equals(directoryKey, identity(directory)))
                throw new IOException("Themes directory changed before temporary write");
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            fault.accept("publish", target);
            if (!guardChecked(guard).equals(directory) || !Objects.equals(directoryKey, identity(directory)))
                throw new IOException("Themes directory changed during publication");
            if (!Files.isRegularFile(temporary, LinkOption.NOFOLLOW_LINKS) || !Objects.equals(temporaryKey, identity(temporary)))
                throw new IOException("Theme temporary changed during publication");
            if (creationCheck != null) call(creationCheck);
            if (replacementCheck == null) {
                // Hard-link is an atomic no-replace primitive; unsupported filesystems fail without a fallback move.
                Files.createLink(target, temporary);
            } else {
                call(replacementCheck);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
        finally {
            // Cleanup must neither follow a replaced parent nor delete a new file occupying the temporary's name.
            // Its failure cannot turn an already committed publication into a reported failure.
            if (temporary != null) cleanup(handle, directory, directoryKey, temporary, temporaryKey);
            try { handle.close(); } catch (IOException failure) { cleanupWarning(failure); }
        }
    }

    private static Object identity(Path path) throws IOException {
        Object key = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
        if (key == null) throw new IOException("Filesystem does not expose stable file identities");
        return key;
    }
    private static void cleanup(java.nio.file.DirectoryStream<Path> handle, Path directory, Object directoryKey,
                                Path temporary, Object temporaryKey) {
        try {
            if (handle instanceof java.nio.file.SecureDirectoryStream<Path> secure) {
                var view = secure.getFileAttributeView(temporary.getFileName(), java.nio.file.attribute.BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (Objects.equals(temporaryKey, view.readAttributes().fileKey())) secure.deleteFile(temporary.getFileName());
            } else if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    && Objects.equals(directoryKey, identity(directory))
                    && Files.isRegularFile(temporary, LinkOption.NOFOLLOW_LINKS)
                    && Objects.equals(temporaryKey, identity(temporary))) Files.delete(temporary);
        } catch (java.nio.file.NoSuchFileException alreadyMoved) {
            // Atomic replacement consumed the temporary.
        } catch (IOException | RuntimeException failure) { cleanupWarning(failure); }
    }
    private static void cleanupWarning(Exception failure) {
        java.util.logging.Logger.getLogger(ThemeFiles.class.getName()).log(java.util.logging.Level.WARNING,
            "Could not clean up owned theme temporary", failure);
    }
    private static void worker() {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme file operations require a worker thread");
    }
    private static <T> T call(Callable<T> action) throws IOException {
        try { return action.call(); }
        catch (IOException failure) { throw failure; }
        catch (UncheckedIOException failure) { throw failure.getCause(); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IOException(failure); }
    }
}
