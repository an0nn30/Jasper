package dev.jasper.editor.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Writes one top-level key of the Editor's settings file, keeping every other line, comments included. */
final class SettingsFile {
    private SettingsFile() { }

    /**
     * Sets {@code key = value}: replaces the key's line, or adds one after its commented-out example, or before the
     * first table, or at the end.
     *
     * @param file the settings file; created when missing
     * @param key a top-level key
     * @param value its new value
     * @throws IOException when the file cannot be read or written
     */
    static void setBoolean(Path file, String key, boolean value) throws IOException {
        String content = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        // The file keeps its own line breaks: Windows ones if it has them, otherwise \n on every platform.
        String separator = content.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(content.lines().toList());
        String setting = key + " = " + value;
        Pattern live = Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*=.*$");
        Pattern example = Pattern.compile("^\\s*#\\s*" + Pattern.quote(key) + "\\s*=.*$");
        int at = find(lines, live);
        if (at >= 0) lines.set(at, setting);
        else {
            int comment = find(lines, example);
            int table = find(lines, Pattern.compile("^\\s*\\[.*$"));
            if (comment >= 0 && (table < 0 || comment < table)) lines.add(comment + 1, setting);
            else if (table >= 0) lines.add(table, setting);
            else lines.add(setting);
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".settings", ".tmp");
        try {
            Files.writeString(temporary, String.join(separator, lines) + separator, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException notAtomic) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static int find(List<String> lines, Pattern pattern) {
        for (int i = 0; i < lines.size(); i++) if (pattern.matcher(lines.get(i)).matches()) return i;
        return -1;
    }
}
