package dev.jasper.app.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Local file-system access for the chooser. Every method may block: callers run it on a worker thread. */
public final class LocalFiles {
    /**
     * What the preview pane shows.
     *
     * @param text the start of a text file, with carriage returns dropped and control characters replaced
     * @param binary the start of the file contains a NUL byte
     * @param items a folder's item count; 0 for files
     */
    public record Preview(Optional<String> text, boolean binary, int items) { }

    /** @return the user's home folder */
    public Path home() { return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize(); }

    /**
     * @param directory the folder to list
     * @return its entries, unsorted
     * @throws IOException when the folder cannot be opened
     */
    public List<ChooserEntry> list(Path directory) throws IOException {
        var entries = new ArrayList<ChooserEntry>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) entries.add(entry(child));
        }
        return entries;
    }

    /** One entry; an entry whose details cannot be read, such as a dangling link, is listed as {@code OTHER}. */
    public ChooserEntry entry(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        String name = absolute.getFileName() == null ? absolute.toString() : absolute.getFileName().toString();
        boolean hidden = name.startsWith(".") || hiddenAttribute(absolute);
        try {
            BasicFileAttributes attributes = Files.readAttributes(absolute, BasicFileAttributes.class);
            ChooserEntry.Kind kind = attributes.isDirectory() ? ChooserEntry.Kind.DIRECTORY
                : attributes.isRegularFile() ? ChooserEntry.Kind.FILE : ChooserEntry.Kind.OTHER;
            return new ChooserEntry(absolute, name, kind, kind == ChooserEntry.Kind.DIRECTORY ? -1 : attributes.size(),
                attributes.lastModifiedTime().toMillis(), hidden);
        } catch (IOException | RuntimeException unreadable) {
            return new ChooserEntry(absolute, name, ChooserEntry.Kind.OTHER, -1, -1, hidden);
        }
    }

    private static boolean hiddenAttribute(Path path) {
        try { return Files.isHidden(path); } catch (IOException | RuntimeException unknown) { return false; }
    }

    /**
     * @param entry a listed entry
     * @param maxBytes how much of a file to read
     * @return a folder's item count, a binary marker, or the start of a text file
     * @throws IOException when the entry cannot be read
     */
    public Preview preview(ChooserEntry entry, int maxBytes) throws IOException {
        if (entry.directory()) {
            int items = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(entry.path())) {
                for (Path ignored : stream) items++;
            }
            return new Preview(Optional.empty(), false, items);
        }
        byte[] head;
        try (InputStream input = Files.newInputStream(entry.path())) { head = input.readNBytes(maxBytes); }
        for (byte b : head) if (b == 0) return new Preview(Optional.empty(), true, 0);
        return new Preview(Optional.of(printable(new String(head, StandardCharsets.UTF_8))), false, 0);
    }

    /** Keeps line breaks and tabs, drops carriage returns, and replaces other control characters with U+FFFD. */
    static String printable(String text) {
        var out = new StringBuilder(text.length());
        text.chars().forEach(c -> {
            if (c == '\r') return;
            if (c == '\n' || c == '\t') out.append((char) c);
            else if (c < 0x20 || c == 0x7f) out.append('�');
            else out.append((char) c);
        });
        return out.toString();
    }

    /**
     * @param folder the folder to create; its parent must exist
     * @throws IOException for example {@link java.nio.file.FileAlreadyExistsException}
     */
    public void mkdir(Path folder) throws IOException { Files.createDirectory(folder); }
}
