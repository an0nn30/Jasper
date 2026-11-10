package dev.jasper.app.platform;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The system's downloaded-file mark ({@code Platform.markDownloaded}): macOS's quarantine attribute, which Gatekeeper
 * reads, written with libc's {@code setxattr}; Windows' {@code Zone.Identifier} stream, the mark of the web, written
 * through {@link UserDefinedFileAttributeView}; nothing on Linux and other systems. A mark already there is kept.
 */
public final class DownloadMark {
    /** macOS's quarantine attribute. Java's own attribute view would write {@code user.com.apple.quarantine} instead. */
    static final String QUARANTINE = "com.apple.quarantine";
    /** Windows' mark of the web, an alternate data stream that {@link UserDefinedFileAttributeView} writes on NTFS. */
    static final String ZONE = "Zone.Identifier";
    /** Why a volume without alternate data streams (FAT32, exFAT, some shares) refuses the mark. */
    static final String NO_STREAMS = "This volume cannot keep a downloaded-file mark";
    private static final String NO_ADDRESS = "about:internet";
    private static final int EPERM = 1, ENOENT = 2, EACCES = 13, EEXIST = 17;

    private DownloadMark() { }

    /**
     * Gives a file the mark of the system {@code osName} names. Blocks on file work; never on the UI thread.
     *
     * @param file the downloaded file, which must exist; a symbolic link is marked itself, never its target
     * @param source the address it came from
     * @param referrer the page that led to it, if any
     * @param osName the {@code os.name} property
     * @throws NoSuchFileException when the file does not exist
     * @throws IOException when the mark cannot be written, with the system's reason
     */
    public static void apply(Path file, URI source, Optional<URI> referrer, String osName) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(referrer, "referrer");
        // Every system's missing file is a NoSuchFileException, before any native call.
        Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) {
            quarantine(file, quarantine(System.currentTimeMillis() / 1000, UUID.randomUUID()), Setxattr.SYSTEM);
        }
        else if (os.startsWith("windows")) {
            byte[] value = zoneIdentifier(source, referrer).getBytes(StandardCharsets.US_ASCII);
            if (!Files.getFileStore(file).supportsFileAttributeView(UserDefinedFileAttributeView.class))
                throw new FileSystemException(file.toString(), null, NO_STREAMS);
            zone(file, Files.getFileAttributeView(file, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS), value);
        }
    }

    /**
     * macOS's quarantine value, in the form Chrome and Firefox write: flags {@code 0081}, the time in hex seconds, the
     * agent's name and an upper-case UUID.
     *
     * @param epochSeconds when the file was downloaded
     * @param uuid the event's id
     * @return {@code 0081;<hex seconds>;Jasper;<UUID>}
     */
    public static String quarantine(long epochSeconds, UUID uuid) {
        return String.format(Locale.ROOT, "0081;%08x;Jasper;%s", epochSeconds, uuid.toString().toUpperCase(Locale.ROOT));
    }

    /**
     * Windows' {@code Zone.Identifier} text, in the order Windows' attachment services write it: the Internet zone, the
     * referrer when there is one, then the source. Addresses lose their user name and password, are written in ASCII,
     * and one that is not an {@code http} or {@code https} address with a host is written as {@code about:internet}.
     *
     * @param source the address the file came from
     * @param referrer the page that led to it, if any
     * @return the stream's text, each line ended by CR LF
     */
    public static String zoneIdentifier(URI source, Optional<URI> referrer) {
        StringBuilder text = new StringBuilder("[ZoneTransfer]\r\nZoneId=3\r\n");
        referrer.ifPresent(page -> text.append("ReferrerUrl=").append(address(page)).append("\r\n"));
        return text.append("HostUrl=").append(address(source)).append("\r\n").toString();
    }

    /** An address as a mark keeps it: http(s) with a host, without user info, in ASCII; anything else about:internet. */
    static String address(URI uri) {
        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        if (scheme == null || uri.isOpaque() || authority == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return NO_ADDRESS;
        String host = authority.substring(authority.lastIndexOf('@') + 1);
        if (host.isEmpty() || host.startsWith(":")) return NO_ADDRESS;
        StringBuilder kept = new StringBuilder(scheme).append("://").append(host);
        if (uri.getRawPath() != null) kept.append(uri.getRawPath());
        if (uri.getRawQuery() != null) kept.append('?').append(uri.getRawQuery());
        if (uri.getRawFragment() != null) kept.append('#').append(uri.getRawFragment());
        try {
            return new URI(kept.toString()).toASCIIString();
        } catch (java.net.URISyntaxException unexpected) {
            return NO_ADDRESS;
        }
    }

    /**
     * Writes the {@code Zone.Identifier} stream unless the file already has a non-empty one.
     *
     * @param view the file's attribute view; null, or one whose calls are unsupported, where the volume keeps no streams
     */
    static void zone(Path file, UserDefinedFileAttributeView view, byte[] value) throws IOException {
        if (view == null) throw new FileSystemException(file.toString(), null, NO_STREAMS);
        try {
            if (view.list().contains(ZONE) && view.size(ZONE) > 0) return;
            view.write(ZONE, ByteBuffer.wrap(value));
        } catch (UnsupportedOperationException unsupported) {
            var failure = new FileSystemException(file.toString(), null, NO_STREAMS);
            failure.initCause(unsupported);
            throw failure;
        }
    }

    /**
     * Sets {@code com.apple.quarantine} in one {@code setxattr} call that creates it only when it is missing, on the file
     * itself rather than a link's target, without a trailing NUL, so {@code xattr -p} prints exactly the value. A
     * native library that cannot load fails the mark with a {@link FileSystemException} whose cause is the error.
     */
    static void quarantine(Path file, String value, Setxattr setxattr) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        int options = com.sun.jna.platform.mac.XAttr.XATTR_NOFOLLOW | com.sun.jna.platform.mac.XAttr.XATTR_CREATE;
        int result;
        try {
            result = setxattr.set(file.toAbsolutePath().toString(), bytes, options);
        } catch (LinkageError unloadable) {
            // libc or JNA could not load: a failed mark with a reason, as every other failure is, never an Error.
            var failure = new FileSystemException(file.toString(), null, String.valueOf(unloadable.getMessage()));
            failure.initCause(unloadable);
            throw failure;
        }
        if (result == 0) return;
        int errno = Native.getLastError();
        if (errno == EEXIST) return; // Chromium's own mark, or an earlier one: kept.
        String reason = reason(errno);
        throw switch (errno) {
            case ENOENT -> new NoSuchFileException(file.toString(), null, reason);
            case EACCES, EPERM -> new AccessDeniedException(file.toString(), null, reason);
            default -> new FileSystemException(file.toString(), null, reason);
        };
    }

    /** libc's {@code setxattr} of {@code com.apple.quarantine}: 0 when it was set, else -1 with {@code errno} set. */
    @FunctionalInterface
    interface Setxattr {
        /** The system's call through JNA, loaded at the first mark. */
        Setxattr SYSTEM = (path, value, options) -> {
            Memory memory = new Memory(value.length);
            memory.write(0, value, 0, value.length);
            return com.sun.jna.platform.mac.XAttr.INSTANCE.setxattr(path, QUARANTINE, memory, value.length, 0, options);
        };

        int set(String path, byte[] value, int options);
    }

    /** The system's text for an {@code errno}, such as "Permission denied"; the number when libc gives none. */
    private static String reason(int errno) {
        byte[] buffer = new byte[256];
        try {
            if (Strings.LIBC.strerror_r(errno, buffer, buffer.length) == 0) {
                int end = 0;
                while (end < buffer.length && buffer[end] != 0) end++;
                if (end > 0) return new String(buffer, 0, end, StandardCharsets.UTF_8);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // The number alone, below.
        }
        return "error " + errno;
    }

    /**
     * libc's {@code strerror_r}, which, unlike {@code strerror}, is safe from several threads at once; loaded at the first
     * failure. JNA Platform declares neither. This is the XSI and Darwin form, which answers an {@code int}; glibc's
     * default form answers a {@code char*} instead, so only the macOS branch may reach it.
     */
    private interface Strings extends Library {
        Strings LIBC = Native.load(null, Strings.class);

        int strerror_r(int errnum, byte[] buffer, long size);
    }
}
