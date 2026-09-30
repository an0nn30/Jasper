package dev.jasper.remote.chooser;

import static dev.jasper.sdk.files.FileEntry.Kind.*;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.files.FileEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class RemoteBrowserTest {
    @TempDir Path dir;

    @Test void listsWithLinksFollowedStatsReadsAndCreatesFolders() throws Exception {
        Files.createDirectory(dir.resolve("conf"));
        Files.writeString(dir.resolve("conf/app.toml"), "x = 1\n");
        Files.createSymbolicLink(dir.resolve("alias.toml"), Path.of("conf/app.toml"));
        Files.createSymbolicLink(dir.resolve("etc"), Path.of("conf"));
        Files.createSymbolicLink(dir.resolve("gone"), Path.of("missing"));
        String root = dir.toRealPath().toString();
        try (var browser = new RemoteBrowser(new LocalEndpoint())) {
            var entries = new ArrayList<FileEntry>();
            browser.list(root, entries::add);
            assertThat(entries).extracting(FileEntry::name, FileEntry::kind).containsExactlyInAnyOrder(
                tuple("conf", DIRECTORY), tuple("alias.toml", FileEntry.Kind.FILE), tuple("etc", DIRECTORY), tuple("gone", OTHER));
            assertThat(entries.stream().filter(entry -> entry.name().equals("alias.toml")).findFirst().orElseThrow().size())
                .as("a link reports its target's size").isEqualTo(6);
            assertThat(browser.stat(root + "/etc")).hasValueSatisfying(entry -> assertThat(entry.kind()).isEqualTo(DIRECTORY));
            assertThat(browser.stat(root + "/nothing")).isEmpty();
            assertThat(new String(browser.read(root + "/alias.toml", 3))).isEqualTo("x =");
            browser.mkdir(root + "/new");
            assertThat(dir.resolve("new")).isDirectory();
            assertThat(browser.home()).startsWith("/");
        }
    }
}
