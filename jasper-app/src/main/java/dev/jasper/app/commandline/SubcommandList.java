package dev.jasper.app.commandline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code daemon/subcommands}: what {@code jasper --help} lists without contacting Jasper. One line per subcommand,
 * {@code name<TAB>summary<TAB>plugin}, in the order given; the native client reads it.
 */
public final class SubcommandList {
    private static final System.Logger LOG = System.getLogger(SubcommandList.class.getName());

    /** One subcommand: its name, its one-line summary and its plugin's name. */
    public record Entry(String name, String summary, String plugin) {
        public Entry {
            for (String field : new String[]{name, summary, plugin}) {
                Objects.requireNonNull(field, "field");
                if (field.indexOf('\t') >= 0 || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0)
                    throw new IllegalArgumentException("A subcommand list field is one line without tabs: " + field);
            }
        }
    }

    private SubcommandList() { }

    /** Replaces the file atomically. */
    public static void write(Path file, List<Entry> entries) throws IOException {
        Files.createDirectories(file.getParent());
        var text = new StringBuilder();
        for (Entry entry : entries)
            text.append(entry.name()).append('\t').append(entry.summary()).append('\t').append(entry.plugin()).append('\n');
        Path temporary = Files.createTempFile(file.getParent(), ".subcommands-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** The entries; none when the file is missing or unreadable. A malformed line is skipped. */
    public static List<Entry> read(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            return List.of();
        } catch (IOException | RuntimeException unreadable) {
            LOG.log(System.Logger.Level.DEBUG, "Could not read " + file, unreadable);
            return List.of();
        }
        var entries = new ArrayList<Entry>();
        for (String line : lines) {
            String[] fields = line.split("\t", -1);
            if (fields.length == 3) entries.add(new Entry(fields[0], fields[1], fields[2]));
        }
        return List.copyOf(entries);
    }
}
