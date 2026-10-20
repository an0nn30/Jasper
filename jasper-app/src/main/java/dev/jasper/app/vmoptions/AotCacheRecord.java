package dev.jasper.app.vmoptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The remembered start-up cache failure, {@code <home>/jasper-aot.failed} (start-up recovery spec section 6.2): the AOT
 * cache that made Java fail at launch, as it was on disk, and the custom options its failing check ran with. The launcher
 * skips the cache while the record matches it, wherever the cache now is; the application writes the record and never
 * deletes it. Parsing and matching follow the rules the launcher's native code shares through
 * {@code packaging/launcher/test/aot-record-vectors.txt}. Values and pure functions, plus blocking file I/O on the
 * caller's thread.
 *
 * @param cache the cache's path as the launcher expands its {@code -XX:AOTCache=} option; written for information, never
 *              compared
 * @param size the cache file's size in bytes, read as an unsigned 64-bit number
 * @param modified the cache file's modification time in whole seconds since the epoch, read as an unsigned 64-bit number
 * @param options the digest of the custom VM options the failing check ran with; empty when it ran with the bundled
 *                options alone
 */
public record AotCacheRecord(String cache, long size, long modified, Optional<String> options) {
    /** The record file's first line. */
    public static final String HEADER = "# Jasper skips its start-up cache while this file matches it. Delete this file to try the cache again.";

    private static final String CACHE_OPTION = "-XX:AOTCache=";
    private static final List<String> KEYS = List.of("cache", "size", "modified", "options");
    private static final Pattern NUMBER = Pattern.compile("[0-9]{1,19}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{16}");

    /** {@code cache} is non-empty and holds no line feed; {@code options} is 16 lowercase hex digits when present. */
    public AotCacheRecord {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(options, "options");
        if (cache.isEmpty() || cache.indexOf('\n') >= 0) throw new IllegalArgumentException("A cache path is one non-empty line");
        if (options.isPresent() && !DIGEST.matcher(options.get()).matches()) {
            throw new IllegalArgumentException("A VM options digest is 16 lowercase hex digits");
        }
    }

    /**
     * What the match compares about the file at the cache's path: a regular file's size and its modification time in
     * whole seconds since the epoch (rounded down, as POSIX {@code st_mtime}).
     */
    public record CacheFile(long size, long modified) {
        /** The regular file at {@code path}, following links; empty when nothing, or something else, is there. Blocking. */
        public static Optional<CacheFile> at(Path path) throws IOException {
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (NoSuchFileException absent) {
                return Optional.empty();
            }
            if (!attributes.isRegularFile()) return Optional.empty();
            return Optional.of(new CacheFile(attributes.size(), attributes.lastModifiedTime().toInstant().getEpochSecond()));
        }
    }

    /** The path of the cache {@code options} name: the value of their last non-empty {@code -XX:AOTCache=}. */
    public static Optional<String> cachePath(List<String> options) {
        for (int i = options.size() - 1; i >= 0; i--) {
            String option = options.get(i);
            if (option.startsWith(CACHE_OPTION) && option.length() > CACHE_OPTION.length()) {
                return Optional.of(option.substring(CACHE_OPTION.length()));
            }
        }
        return Optional.empty();
    }

    /**
     * The record of the cache {@code options} name, as it is on disk now; {@code digest} is the custom options' digest
     * when the failing check ran with them. Blocking.
     *
     * @throws IOException when the options name no cache, its path would not read back (a line break in it, or a
     *                     carriage return at its end), no regular file is there, or its modification time is before 1970
     */
    public static AotCacheRecord capture(List<String> options, Optional<String> digest) throws IOException {
        String cache = cachePath(options).orElseThrow(() -> new IOException("The options name no AOT cache"));
        if (cache.indexOf('\n') >= 0 || cache.endsWith("\r")) throw new IOException("The AOT cache's path cannot be recorded");
        CacheFile file = CacheFile.at(Path.of(cache)).orElseThrow(() -> new NoSuchFileException(cache));
        if (file.modified() < 0) throw new IOException("The AOT cache's modification time is before 1970");
        return new AotCacheRecord(cache, file.size(), file.modified(), digest);
    }

    /**
     * Reads a record (spec 6.2): lines split on {@code \n}, one trailing {@code \r} removed from each; empty lines and
     * lines starting with {@code #} skipped; every other line {@code key=value}, split at the first {@code =}, with each
     * of {@code cache}, {@code size}, {@code modified} and {@code options} exactly once and no other key; {@code cache}
     * non-empty, {@code size} and {@code modified} 1 to 19 ASCII digits, {@code options} empty or 16 lowercase hex
     * digits. Anything else is malformed and reads as empty.
     */
    public static Optional<AotCacheRecord> parse(String text) {
        var values = new HashMap<String, String>();
        for (String raw : text.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int equals = line.indexOf('=');
            if (equals < 0) return Optional.empty();
            String key = line.substring(0, equals);
            if (!KEYS.contains(key) || values.putIfAbsent(key, line.substring(equals + 1)) != null) return Optional.empty();
        }
        if (values.size() != KEYS.size()) return Optional.empty();
        String cache = values.get("cache");
        String size = values.get("size");
        String modified = values.get("modified");
        String options = values.get("options");
        if (cache.isEmpty() || !NUMBER.matcher(size).matches() || !NUMBER.matcher(modified).matches()
            || !(options.isEmpty() || DIGEST.matcher(options).matches())) {
            return Optional.empty();
        }
        return Optional.of(new AotCacheRecord(cache, Long.parseUnsignedLong(size), Long.parseUnsignedLong(modified),
            options.isEmpty() ? Optional.empty() : Optional.of(options)));
    }

    /** The record file's text: the header, then the four keys in order, UTF-8 with LF line ends. */
    public String format() {
        return HEADER + "\n"
            + "cache=" + cache + "\n"
            + "size=" + Long.toUnsignedString(size) + "\n"
            + "modified=" + Long.toUnsignedString(modified) + "\n"
            + "options=" + options.orElse("") + "\n";
    }

    /**
     * Whether the launcher skips the cache (spec 6.2, as amended): {@code file}, the regular file at the expanded cache
     * path, has this record's size and modification time (a time before 1970 never matches), and its options are empty
     * or equal {@code digest}, the start's {@code jasper.vmOptionsDigest} (a start without one never matches recorded
     * options). The path is not compared.
     */
    public boolean matches(Optional<CacheFile> file, Optional<String> digest) {
        return file.isPresent() && file.get().size() == size && file.get().modified() >= 0 && file.get().modified() == modified
            && (options.isEmpty() || options.equals(digest));
    }

    /** Whether the record file's {@code text} matches; a malformed record matches nothing. */
    public static boolean matches(String text, Optional<CacheFile> file, Optional<String> digest) {
        return parse(text).filter(record -> record.matches(file, digest)).isPresent();
    }

    /** The record in {@code file}; empty when it is missing, unreadable, not UTF-8 or malformed. Blocking. */
    public static Optional<AotCacheRecord> read(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /** Replaces {@code file} with this record atomically (a temporary file beside it, then a move). Blocking. */
    public void write(Path file) throws IOException {
        VmOptionsFile.write(file, format());
    }
}
