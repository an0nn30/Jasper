package dev.jasper.app.files;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LocalBrowserTest {
    @TempDir Path dir;

    private LocalBrowser browser() { return new LocalBrowser(dir); }

    private java.util.List<ChooserEntry> list(Path folder) throws Exception {
        var entries = new ArrayList<ChooserEntry>();
        browser().list(folder.toString(), entries::add);
        return entries;
    }

    @Test void listsFoldersFilesAndHiddenEntriesWithDetails() throws Exception {
        Files.createDirectory(dir.resolve("docs"));
        Files.writeString(dir.resolve("notes.txt"), "hello");
        Files.writeString(dir.resolve(".env"), "x");
        var byName = list(dir).stream().collect(Collectors.toMap(ChooserEntry::name, entry -> entry));
        assertThat(byName).containsOnlyKeys("docs", "notes.txt", ".env");
        assertThat(byName.get("notes.txt")).satisfies(notes -> {
            assertThat(notes.kind()).isEqualTo(ChooserEntry.Kind.FILE);
            assertThat(notes.size()).isEqualTo(5);
            assertThat(notes.modified()).isPositive();
            assertThat(notes.hidden()).isFalse();
            assertThat(notes.path()).isEqualTo(dir.resolve("notes.txt").toString());
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
        var byName = list(dir).stream().collect(Collectors.toMap(ChooserEntry::name, entry -> entry));
        assertThat(byName.get("link").directory()).isTrue();
        assertThat(byName.get("gone").kind()).isEqualTo(ChooserEntry.Kind.OTHER);
        assertThat(browser().stat(dir.resolve("gone").toString())).as("a dangling link is there").isPresent();
    }

    @Test void statReadAndMkdir() throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "abcdef");
        assertThat(browser().stat(file.toString())).hasValueSatisfying(entry -> assertThat(entry.kind()).isEqualTo(ChooserEntry.Kind.FILE));
        assertThat(browser().stat(dir.resolve("missing").toString())).isEmpty();
        assertThat(new String(browser().read(file.toString(), 3))).isEqualTo("abc");
        browser().mkdir(dir.resolve("new").toString());
        assertThat(dir.resolve("new")).isDirectory();
        assertThatThrownBy(() -> browser().mkdir(dir.resolve("new").toString())).isInstanceOf(FileAlreadyExistsException.class);
        assertThatThrownBy(() -> browser().list(dir.resolve("missing").toString(), entry -> { })).isInstanceOf(NoSuchFileException.class);
    }

    @Test void pathsFollowThePlatform() {
        var browser = browser();
        assertThat(browser.home()).isEqualTo(dir.toString());
        assertThat(browser.parent(dir.resolve("x").toString())).contains(dir.toString());
        assertThat(browser.name(dir.resolve("x").toString())).isEqualTo("x");
        assertThat(browser.resolve(dir.toString(), "~")).isEqualTo(dir.toString());
        assertThat(browser.resolve(dir.toString(), "~/y")).isEqualTo(dir.resolve("y").toString());
        assertThat(browser.resolve(dir.resolve("a").toString(), "../b")).isEqualTo(dir.resolve("b").toString());
        Path root = dir.getRoot();
        assertThat(browser.parent(root.toString())).isEmpty();
        assertThat(browser.name(root.toString())).isEqualTo(root.toString());
    }

    @Test void previewsTextBinaryAndFolders() {
        assertThat(PreviewInfo.file("line one\r\nline\007two\n".getBytes())).isEqualTo(new PreviewInfo(Optional.of("line one\nline\uFFFDtwo\n"), false, 0));
        assertThat(PreviewInfo.file(new byte[] {1, 0, 2})).isEqualTo(new PreviewInfo(Optional.empty(), true, 0));
        assertThat(PreviewInfo.folder(2)).isEqualTo(new PreviewInfo(Optional.empty(), false, 2));
    }
}
