package dev.jasper.app.vmoptions;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The launcher's reading of {@code jasper.vmoptions}, mirrored from packaging/launcher/jasper/JasperVmOptionsCore.cpp
 * (spec sections 3.2 and 3.3): the same trimming, skipped lines, applicable options, merge filters and digest, so the
 * application's view of the file, and its digest, match what the launcher did. Pure.
 */
public final class VmOptionsSyntax {
    /**
     * A file as the launcher reads it.
     *
     * @param lines the lines it applies, in order
     * @param ignored the lines it read but does not apply (no leading {@code -}, or one of libjli's own options)
     * @param corrupted whether it ignored the whole file (a NUL character or invalid UTF-8)
     */
    public record Parsed(List<String> lines, List<String> ignored, boolean corrupted) {
        /** An absent or empty file. */
        public static final Parsed EMPTY = new Parsed(List.of(), List.of(), false);

        public Parsed {
            lines = List.copyOf(lines);
            ignored = List.copyOf(ignored);
        }
    }

    /** The core's whitespace: space, tab, CR, LF, form feed and vertical tab. */
    private static final String WHITESPACE = " \t\r\n\f\u000B";
    /** libjli's own launcher options (spec 3.2). */
    private static final Set<String> LAUNCHER_OPTIONS = Set.of(
        "-version", "--version", "-help", "--help", "-h", "-?", "-X", "--help-extra", "--dry-run", "--list-modules",
        "-d", "--describe-module", "--validate-modules", "-jar", "-m", "--module", "-cp", "-classpath", "--class-path",
        "--source", "--add-opens", "--add-exports", "--add-reads", "--add-modules", "--limit-modules", "--module-path", "-p",
        "--upgrade-module-path", "--patch-module", "--enable-native-access");
    private static final List<String> LAUNCHER_OPTION_PREFIXES = List.of("--describe-module=", "--module=", "--class-path=", "--source=");

    private VmOptionsSyntax() { }

    /** Reads a file's bytes as the launcher does. */
    public static Parsed parse(byte[] bytes) {
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        for (int i = start; i < bytes.length; i++) {
            if (bytes[i] == 0) return new Parsed(List.of(), List.of(), true);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.length - start))
                .toString();
        } catch (CharacterCodingException malformed) {
            return new Parsed(List.of(), List.of(), true);
        }
        var lines = new ArrayList<String>();
        var ignored = new ArrayList<String>();
        for (String raw : text.split("\n", -1)) {
            String line = trim(raw);
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            if (isApplicable(line)) lines.add(line);
            else ignored.add(line);
        }
        return new Parsed(lines, ignored, false);
    }

    /** Whether a line is passed to the JVM: it starts with {@code -} and is not one of libjli's own launcher options. */
    public static boolean isApplicable(String line) {
        if (line.isEmpty() || line.charAt(0) != '-') return false;
        if (LAUNCHER_OPTIONS.contains(line)) return false;
        for (String prefix : LAUNCHER_OPTION_PREFIXES) {
            if (line.startsWith(prefix)) return false;
        }
        return true;
    }

    /** The bundled options without those the user's lines replace, then the user's lines (spec 3.3). */
    public static List<String> merge(List<String> bundled, List<String> user) {
        boolean collector = user.stream().anyMatch(VmOptionsSyntax::isCollector);
        boolean xms = user.stream().anyMatch(line -> line.startsWith("-XX:InitialRAMPercentage="));
        boolean xmx = user.stream().anyMatch(line -> line.startsWith("-XX:MinRAMPercentage=") || line.startsWith("-XX:MaxRAMPercentage="));
        var merged = new ArrayList<String>();
        for (String option : bundled) {
            if ((collector && isCollector(option)) || (xms && option.startsWith("-Xms")) || (xmx && option.startsWith("-Xmx"))) continue;
            merged.add(option);
        }
        merged.addAll(user);
        return List.copyOf(merged);
    }

    /** FNV-1a 64 over each line's UTF-8 bytes followed by {@code \n}, as 16 lowercase hex digits. */
    public static String digest(List<String> lines) {
        long hash = 0xcbf29ce484222325L;
        for (String line : lines) {
            for (byte value : (line + "\n").getBytes(StandardCharsets.UTF_8)) {
                hash ^= value & 0xFF;
                hash *= 0x100000001b3L;
            }
        }
        return String.format("%016x", hash);
    }

    /** The heap limit an {@code -Xmx} option sets, in MiB rounded down; empty when it is no valid {@code -Xmx}. */
    public static OptionalLong maxHeapMiB(String option) {
        if (!option.startsWith("-Xmx") || option.length() == 4) return OptionalLong.empty();
        String size = option.substring(4);
        long scale = switch (Character.toLowerCase(size.charAt(size.length() - 1))) {
            case 'k' -> 1L << 10;
            case 'm' -> 1L << 20;
            case 'g' -> 1L << 30;
            case 't' -> 1L << 40;
            default -> 1L;
        };
        String digits = scale == 1L ? size : size.substring(0, size.length() - 1);
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) return OptionalLong.empty();
        try {
            return OptionalLong.of(Math.multiplyExact(Long.parseLong(digits), scale) >> 20);
        } catch (ArithmeticException | NumberFormatException tooLarge) {
            return OptionalLong.empty();
        }
    }

    /** The last valid {@code -Xmx} among {@code options}, in MiB. */
    public static OptionalLong lastMaxHeapMiB(List<String> options) {
        OptionalLong last = OptionalLong.empty();
        for (String option : options) {
            OptionalLong heap = maxHeapMiB(option);
            if (heap.isPresent()) last = heap;
        }
        return last;
    }

    /** {@code value} without the core's whitespace at either end. */
    static String trim(String value) {
        int first = 0;
        int last = value.length();
        while (first < last && WHITESPACE.indexOf(value.charAt(first)) >= 0) first++;
        while (last > first && WHITESPACE.indexOf(value.charAt(last - 1)) >= 0) last--;
        return value.substring(first, last);
    }

    private static boolean isCollector(String option) { return option.startsWith("-XX:+") && option.endsWith("GC"); }
}
