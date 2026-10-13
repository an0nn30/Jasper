package dev.jasper.app.vmoptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reading and editing {@code jasper.vmoptions} the way IntelliJ edits its file (spec section 5.1). Blocking file I/O. */
public final class VmOptionsFile {
    /** The first line of a file Jasper creates. */
    public static final String HEADER = "# custom Jasper VM options (expand/override the bundled options)";

    /**
     * The file as it is now.
     *
     * @param exists whether it is a regular file
     * @param text its text, decoded leniently for editing; empty when absent
     * @param parsed the launcher's reading of it
     */
    public record Contents(boolean exists, String text, VmOptionsSyntax.Parsed parsed) {
        /** No file. */
        public static final Contents ABSENT = new Contents(false, "", VmOptionsSyntax.Parsed.EMPTY);
    }

    private VmOptionsFile() { }

    /** Reads {@code file}; a missing file, or a directory, reads as absent. */
    public static Contents read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return Contents.ABSENT;
        byte[] bytes = Files.readAllBytes(file);
        return new Contents(true, new String(bytes, StandardCharsets.UTF_8), VmOptionsSyntax.parse(bytes));
    }

    /**
     * {@code text} with the heap limit set as IntelliJ's {@code VMOptions.setOption} does: the last line starting with
     * {@code -Xmx} becomes {@code -Xmx<mib>m} (losing its indentation), or one is appended, after the header when the
     * text holds nothing yet. {@code bundledMib} instead removes every {@code -Xmx} line, so a later change to the
     * bundled default reaches the user. Keeps the text's line separator.
     */
    public static String withMaxHeap(String text, long mib, long bundledMib) {
        String separator = text.contains("\r\n") ? "\r\n" : "\n";
        var lines = new ArrayList<String>(text.isEmpty() ? List.of() : Arrays.asList(text.split("\r?\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
        if (mib == bundledMib) {
            lines.removeIf(line -> VmOptionsSyntax.trim(line).startsWith("-Xmx"));
        } else {
            String option = "-Xmx" + mib + "m";
            int last = -1;
            for (int i = lines.size() - 1; i >= 0 && last < 0; i--) {
                if (VmOptionsSyntax.trim(lines.get(i)).startsWith("-Xmx")) last = i;
            }
            if (last >= 0) {
                lines.set(last, option);
            } else {
                if (lines.stream().allMatch(line -> VmOptionsSyntax.trim(line).isEmpty())) {
                    lines.clear();
                    lines.add(HEADER);
                    lines.add("");
                }
                lines.add(option);
            }
        }
        return lines.isEmpty() ? "" : String.join(separator, lines) + separator;
    }

    /** Reads {@code file}, sets the heap limit as {@link #withMaxHeap} does, and writes it back. */
    public static void setMaxHeap(Path file, long mib, long bundledMib) throws IOException {
        write(file, withMaxHeap(read(file).text(), mib, bundledMib));
    }

    /** Creates {@code file} with the header and a blank line when it does not exist; returns it. */
    public static Path ensureExists(Path file) throws IOException {
        if (!Files.exists(file)) write(file, HEADER + "\n\n");
        return file;
    }

    /** Replaces {@code file} with {@code text} (UTF-8) atomically, creating its directory. */
    public static void write(Path file, String text) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".jasper.vmoptions", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
