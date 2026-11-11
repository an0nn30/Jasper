package dev.jasper.browser.downloads;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Download file names (spec section 10, rulings F4 and F5): a safe name for what a server suggests, iTerm2's numbering
 * for a name already taken, and the names downloads under way have reserved, so that two downloads of one name at once
 * never write one file. Names compare with case and Unicode normalization folded, as APFS and NTFS compare them.
 * {@link #safeName} and {@link #numbered} are pure; {@link #reserve}, {@link #reserveFolder}, which holds Save Page
 * As's folder, and {@link #release} are safe on any thread, and the reserves read only whether a name exists in the
 * folder. Each reservation is a {@link Reservation} token, which
 * frees only itself.
 */
public final class DownloadNames {
    /** The name of a download whose suggested name leaves nothing. */
    static final String DEFAULT = "download";
    /** Chromium's suffix for the file a download writes until it completes. */
    static final String INTERMEDIATE = ".crdownload";
    /** The most numbered names {@link #reserve} tries before there is no free name. */
    static final int MOST_NUMBERS = 1000;
    private static final int MOST_BYTES = 200, LONGEST_EXTENSION = 16;
    /** Windows' {@code MAX_PATH}, which counts the terminating null, so a path holds at most 259 characters. */
    private static final int MAX_PATH = 260;
    private static final String RESERVED = "<>:\"|?*";
    private static final Set<String> DEVICES = devices();
    /** Each reserved name's folded path, and the reservation that holds it. */
    private final Map<String, Reservation> reserved = new HashMap<>();

    /**
     * A name {@link #reserve} reserved for one download. It is equal only to itself, so {@link #release} frees the
     * name only while this reservation holds it: a second release, after another download reserved the same name, frees
     * nothing of that one's.
     */
    public static final class Reservation {
        private final Path file;
        private final String key;

        private Reservation(Path file, String key) {
            this.file = file;
            this.key = key;
        }

        /**
         * The file the download goes to.
         *
         * @return the file, whose parent is the folder it was reserved in
         */
        public Path file() {
            return file;
        }

        @Override public String toString() {
            return "Reservation[" + file + "]";
        }
    }

    /**
     * The name a download is saved under, from the one a server or a page suggests (ruling F4): the part after the last
     * {@code /} or {@code \}; each control and format character, bidi controls and zero-width characters among them, each
     * line or paragraph separator and unpaired surrogate, and each of {@code <>:"|?*}, replaced with {@code _} on every
     * platform; leading and trailing dots and spaces trimmed; {@code download} for a name that leaves nothing; a leading
     * {@code _} before a Windows device name, such as {@code CON}, {@code COM0} or {@code COM1}, in any case, before the
     * first dot; and a name over 200 UTF-8 bytes cut on a code point, keeping its extension of at most 16 characters.
     *
     * @param suggested the suggested name; null reads as empty
     * @return a single file name, never empty
     */
    public static String safeName(String suggested) {
        String name = suggested == null ? "" : suggested;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        var replaced = new StringBuilder(name.length());
        name.codePoints().forEach(c -> replaced.appendCodePoint(replaced(c) ? '_' : c));
        name = trimmed(replaced.toString());
        if (name.isEmpty()) return DEFAULT;
        if (device(name)) name = "_" + name;
        return fitted(name, DownloadNames::utf8Bytes, MOST_BYTES).orElse(DEFAULT);
    }

    /**
     * {@code name} numbered as iTerm2 numbers a clash: {@code (n)} before the last extension, so {@code report.pdf}
     * gives {@code report (1).pdf} and {@code archive.tar.gz} gives {@code archive.tar (2).gz}; a name without an
     * extension, or a dot only at its start, takes it at its end: {@code README (1)}, {@code .bashrc (1)}.
     *
     * @param name the name
     * @param n the number, from 1
     * @return the numbered name
     * @throws IllegalArgumentException when {@code n} is less than 1
     */
    public static String numbered(String name, int n) {
        if (n < 1) throw new IllegalArgumentException("Not a number for a name: " + n);
        int dot = name.lastIndexOf('.');
        String number = " (" + n + ")";
        return dot > 0 ? name.substring(0, dot) + number + name.substring(dot) : name + number;
    }

    /**
     * Reserves the first free name for a download in {@code folder}: {@link #safeName(String) safeName(name)}, then
     * {@link #numbered numbered} from 1 to 1,000. A name is free when nothing of that name is in the folder, a link
     * that leads nowhere included, and no download has reserved it, case and normalization folded. Reading the folder
     * is the only I/O; nothing is created. On Windows the stem is shortened first, keeping the extension, so that the
     * path, numbered and with Chromium's {@code .crdownload} added, stays under {@code MAX_PATH}'s 260 characters.
     *
     * @param folder the Downloads folder
     * @param name the suggested name
     * @return the reservation, whose file's parent is {@code folder}; empty when no name is free
     */
    public synchronized Optional<Reservation> reserve(Path folder, String name) {
        return reserve(folder, name, System.getProperty("os.name", ""));
    }

    /**
     * {@link #reserve(Path, String)} on the system {@code osName} names.
     *
     * @param folder the Downloads folder
     * @param name the suggested name
     * @param osName the {@code os.name} system property
     * @return the reservation; empty when no name is free
     */
    synchronized Optional<Reservation> reserve(Path folder, String name, String osName) {
        String safe = safeName(name);
        if (osName.startsWith("Windows")) {
            // The folder's path and its separator, then the name with the longest number and the intermediate suffix.
            int room = MAX_PATH - 1 - (folder.resolve("x").toString().length() - 1) - (" (" + MOST_NUMBERS + ")").length()
                - INTERMEDIATE.length();
            Optional<String> fitted = fitted(safe, Character::charCount, room);
            if (fitted.isPresent() && device(fitted.get())) fitted = fitted("_" + fitted.get(), Character::charCount, room);
            if (fitted.isEmpty()) return Optional.empty();
            safe = fitted.get();
        }
        for (int n = 0; n <= MOST_NUMBERS; n++) {
            Path file = folder.resolve(n == 0 ? safe : numbered(safe, n));
            String key = key(file);
            if (!reserved.containsKey(key) && !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                var reservation = new Reservation(file, key);
                reserved.put(key, reservation);
                return Optional.of(reservation);
            }
        }
        return Optional.empty();
    }

    /**
     * Reserves the first free name for Save Page As's folder in {@code parent} (plan 7's D7 and D43): {@code name}, then
     * {@code name (1)} to {@code name (999)}, numbered at its end as Finder and Explorer number a folder. A name is free
     * as {@link #reserve} reads it: nothing of that name in {@code parent}, a link included, and no download or save holds
     * it, case and normalization folded. Reading {@code parent} is the only I/O; nothing is created. It holds the names'
     * one lock, which {@link #reserve} and {@link #release} take too, for at most 1,000 reads of {@code parent}, so a
     * download that starts meanwhile, on Chromium's thread, waits at most that long (plan 7's D48).
     *
     * @param parent the folder the save's folder would go in
     * @param name the folder's name, a safe one
     * @return the reservation, whose file's parent is {@code parent}; empty when no name is free
     */
    public synchronized Optional<Reservation> reserveFolder(Path parent, String name) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        for (int n = 0; n < MOST_NUMBERS; n++) {
            Path file = parent.resolve(n == 0 ? name : name + " (" + n + ")");
            String key = key(file);
            if (!reserved.containsKey(key) && !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                var reservation = new Reservation(file, key);
                reserved.put(key, reservation);
                return Optional.of(reservation);
            }
        }
        return Optional.empty();
    }

    /**
     * Frees the name {@code reservation} holds: a later download may take it again unless something of that name is in
     * the folder. A reservation already released, and another {@code DownloadNames}'s, free nothing.
     *
     * @param reservation what {@link #reserve} answered
     */
    public synchronized void release(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        reserved.remove(reservation.key, reservation);
    }

    /** {@code file}'s path with case and normalization folded, as APFS and NTFS compare names (ruling F5). */
    private static String key(Path file) {
        return Normalizer.normalize(file.toString(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    /** Whether {@code c} is no character for a file name: a control, format or line character, a lone surrogate, or reserved. */
    private static boolean replaced(int c) {
        return switch (Character.getType(c)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> RESERVED.indexOf(c) >= 0;
        };
    }

    /** {@code name} without its leading and trailing dots and spaces. */
    private static String trimmed(String name) {
        int start = 0, end = name.length();
        while (start < end && trims(name.charAt(start))) start++;
        while (end > start && trims(name.charAt(end - 1))) end--;
        return name.substring(start, end);
    }

    private static boolean trims(char c) {
        return c == '.' || Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    /** Whether Windows reads {@code name} as a device: its part before the first dot, without trailing spaces, in any case. */
    private static boolean device(String name) {
        int dot = name.indexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        int end = base.length();
        while (end > 0 && base.charAt(end - 1) == ' ') end--;
        return DEVICES.contains(base.substring(0, end).toUpperCase(Locale.ROOT));
    }

    /**
     * {@code name} within {@code most} units, each code point counted by {@code size}: as it is when it fits, otherwise
     * its stem cut on a code point and its extension of at most 16 characters kept, and a name without one left with no
     * trailing dot or space.
     *
     * @return the name; empty when not even one code point of the stem fits
     */
    private static Optional<String> fitted(String name, IntUnaryOperator size, int most) {
        if (units(name, size) <= most) return Optional.of(name);
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 && name.codePointCount(dot + 1, name.length()) <= LONGEST_EXTENSION ? name.substring(dot) : "";
        String stem = name.substring(0, name.length() - extension.length());
        int room = most - units(extension, size), used = 0, end = 0;
        while (end < stem.length()) {
            int c = stem.codePointAt(end), width = size.applyAsInt(c);
            if (used + width > room) break;
            used += width;
            end += Character.charCount(c);
        }
        String kept = stem.substring(0, end);
        if (extension.isEmpty()) kept = trimmed(kept);
        return kept.isEmpty() ? Optional.empty() : Optional.of(kept + extension);
    }

    private static int units(String text, IntUnaryOperator size) {
        return text.codePoints().map(size).sum();
    }

    /** The UTF-8 bytes of code point {@code c}. */
    private static int utf8Bytes(int c) {
        return c < 0x80 ? 1 : c < 0x800 ? 2 : c < 0x10000 ? 3 : 4;
    }

    /**
     * Windows' device names, as Microsoft lists them: {@code CON}, {@code PRN}, {@code AUX}, {@code NUL}, {@code COM0} to
     * {@code COM9}, {@code LPT0} to {@code LPT9}, {@code CONIN$}, {@code CONOUT$}, and {@code COM} and {@code LPT} with a
     * superscript one, two or three.
     */
    private static Set<String> devices() {
        var devices = new HashSet<>(Set.of("CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$"));
        for (String port : new String[] { "COM", "LPT" }) {
            for (int n = 0; n <= 9; n++) devices.add(port + n);
            for (char superscript : new char[] { '\u00B9', '\u00B2', '\u00B3' }) devices.add(port + superscript);
        }
        return Set.copyOf(devices);
    }
}
