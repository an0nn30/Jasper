package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Browser plan 4c, Review Focus 2: the local file a {@code file:} URL names, in each form RFC 8089 gives it. */
class FileUrlsTest {
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    @TempDir Path dir;

    @Test void everyLocalFormNamesTheFileItsQueryAndFragmentIgnored() {
        Path page = dir.resolve("a b.html");
        String raw = page.toUri().getRawPath();
        for (String text : List.of(page.toUri().toString(), "file:" + raw, "file://localhost" + raw, "FILE://LocalHost" + raw,
                page.toUri() + "?a=1", page.toUri() + "#top", "file://localhost" + raw + "?a=1#top"))
            assertThat(FileUrls.localFile(URI.create(text))).as(text).contains(page);
    }

    @Test void anotherSchemeAnOpaqueUrlAndOneWithNoPathNameNoFile() {
        for (String text : List.of("https://example.com/a.html", "file:a.html", "file://localhost", "mailto:someone@example.com", "/a.html"))
            assertThat(FileUrls.localFile(URI.create(text))).as(text).isEmpty();
    }

    @Test void anotherHostIsAShareOnWindowsAndNoFileElsewhere() {
        var share = FileUrls.localFile(URI.create("file://server/share/a.html"));
        if (WINDOWS) assertThat(share).contains(Path.of("\\\\server\\share\\a.html"));
        else assertThat(share).isEmpty();
    }
}
