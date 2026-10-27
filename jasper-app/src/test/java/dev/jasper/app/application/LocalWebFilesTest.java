package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Browser plan 3b, Task 7: which clicked files a browser is offered first; iTerm2's displayable types. */
class LocalWebFilesTest {
    @TempDir Path directory;

    private Path file(String name) throws Exception { return Files.writeString(directory.resolve(name), "x\n"); }

    @Test void everyTypeABrowserDisplaysMatchesInAnyCase() throws Exception {
        for (String name : new String[] {"index.html", "index.htm", "page.xhtml", "feed.xml", "logo.svg", "site.css", "app.js", "app.mjs",
            "paper.pdf", "a.png", "a.jpg", "a.jpeg", "a.gif", "a.webp", "a.heic", "index.HTML", "Photo.JpEg", "REPORT.PDF"})
            assertThat(LocalWebFiles.matches(file(name))).as(name).isTrue();
    }

    @Test void otherFilesAFolderAndAMissingFileDoNotMatch() throws Exception {
        for (String name : new String[] {"notes.md", "Main.java", "archive.tar.gz", "Makefile", "page.html.bak", "page.", "htm"})
            assertThat(LocalWebFiles.matches(file(name))).as(name).isFalse();
        Path folder = Files.createDirectory(directory.resolve("site.html"));
        assertThat(LocalWebFiles.matches(folder)).as("a folder named like a web page").isFalse();
        assertThat(LocalWebFiles.matches(directory.resolve("gone.html"))).as("a file that does not exist").isFalse();
    }

    /** The extension is the text after the last dot of the file name, and a name that is only a dot and a word has none. */
    @Test void aHiddenFileNamedOnlyByItsExtensionHasNone() throws Exception {
        assertThat(LocalWebFiles.matches(file(".html"))).as("no extension").isFalse();
        assertThat(LocalWebFiles.matches(file(".hidden.html"))).as("an extension after a dot-led name").isTrue();
        assertThat(LocalWebFiles.matches(file("v1.2.pdf"))).as("the text after the last dot").isTrue();
        assertThat(LocalWebFiles.matches(file("a.html.txt"))).as("the text after the last dot").isFalse();
    }

    @Test void aPathWithNoFileNameDoesNotMatch() {
        assertThat(LocalWebFiles.matches(Path.of("/"))).isFalse();
    }
}
