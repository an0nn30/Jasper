package dev.jasper.app.files;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LocalFilesTest {
    @TempDir Path dir;
    private final LocalFiles files = new LocalFiles();

    @Test void listsFoldersFilesAndHiddenEntriesWithDetails() throws Exception {
        Files.createDirectory(dir.resolve("docs"));
        Files.writeString(dir.resolve("notes.txt"), "hello");
        Files.writeString(dir.resolve(".env"), "x");
        var byName = files.list(dir).stream().collect(Collectors.toMap(ChooserEntry::name, entry -> entry));
        assertThat(byName).containsOnlyKeys("docs", "notes.txt", ".env");
        assertThat(byName.get("notes.txt")).satisfies(notes -> {
            assertThat(notes.kind()).isEqualTo(ChooserEntry.Kind.FILE);
            assertThat(notes.size()).isEqualTo(5);
            assertThat(notes.modified()).isPositive();
            assertThat(notes.hidden()).isFalse();
            assertThat(notes.path()).isEqualTo(dir.resolve("notes.txt"));
        });
        assertThat(byName.get("docs").directory()).isTrue();
        assertThat(byName.get("docs").size()).isEqualTo(-1);
        assertThat(byName.get(".env").hidden()).isTrue();
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void aLinkToAFolderIsAFolderAndADanglingLinkIsStillListed() throws Exception {
        Files.createDirectory(dir.resolve("real"));
        Files.createSymbolicLink(dir.resolve("link"), dir.resolve("real"));
        Files.createSymbolicLink(dir.resolve("gone"), dir.resolve("missing"));
        var byName = files.list(dir).stream().collect(Collectors.toMap(ChooserEntry::name, entry -> entry));
        assertThat(byName.get("link").directory()).isTrue();
        assertThat(byName.get("gone").kind()).isEqualTo(ChooserEntry.Kind.OTHER);
    }

    @Test void listingAMissingFolderFails() {
        assertThatThrownBy(() -> files.list(dir.resolve("missing"))).isInstanceOf(NoSuchFileException.class);
    }

    @Test void previewsTextBinaryAndFolders() throws Exception {
        Path text = Files.writeString(dir.resolve("a.txt"), "line one\r\nline\007two\n");
        assertThat(files.preview(files.entry(text), 8192)).isEqualTo(new LocalFiles.Preview(Optional.of("line one\nline�two\n"), false, 0));
        Path binary = Files.write(dir.resolve("b.bin"), new byte[] {1, 0, 2});
        assertThat(files.preview(files.entry(binary), 8192)).isEqualTo(new LocalFiles.Preview(Optional.empty(), true, 0));
        Path folder = Files.createDirectory(dir.resolve("f"));
        Files.writeString(folder.resolve("x"), "");
        Files.writeString(folder.resolve("y"), "");
        assertThat(files.preview(files.entry(folder), 8192)).isEqualTo(new LocalFiles.Preview(Optional.empty(), false, 2));
        Path longer = Files.writeString(dir.resolve("long.txt"), "abcdef");
        assertThat(files.preview(files.entry(longer), 3).text()).contains("abc");
    }

    @Test void createsFoldersOnce() throws Exception {
        files.mkdir(dir.resolve("new"));
        assertThat(dir.resolve("new")).isDirectory();
        assertThatThrownBy(() -> files.mkdir(dir.resolve("new"))).isInstanceOf(FileAlreadyExistsException.class);
    }
}
