package dev.jasper.remote;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.LocationResolver;
import dev.jasper.remote.chooser.RemoteHosts;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileEntry;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.RemoteEditingPluginTest.*;
import static org.assertj.core.api.Assertions.*;

/** Saved hosts in Jasper's file chooser and Remote's Editor resolver, against a loopback SFTP server. POSIX paths and links. */
@DisabledOnOs(OS.WINDOWS)
class RemoteFileSourceTest {
    static String prod(FakePluginHost host) { return host.fileRoots(RemoteHosts.ID).getFirst().split("\\|")[0]; }

    @Test void savedHostsAreRootsAndAnSshPaneIsLocatedOnItsHost(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.empty(), 1, new SftpSubsystemFactory());
            assertThat(host.fileSources()).containsExactly(RemoteHosts.ID + "|Remote");
            String prod = prod(host);
            assertThat(host.fileRoots(RemoteHosts.ID)).containsExactly(prod + "|prod|true");
            assertThat(host.locateFile(RemoteHosts.ID, set.pane()))
                .as("the shell's folder is not followed here, so the pane opens at the login's home")
                .contains(new FileLocation.Sourced(RemoteHosts.ID, prod, FileLocation.Sourced.HOME));
            assertThat(host.actions()).noneSatisfy(line -> assertThat(line).startsWith("dev.jasper.remote.open-file|"));
            host.stopAll();
            assertThat(host.fileSources()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void aHostIsBrowsedOverSftpWithLinksFollowed(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.createDirectory(files.resolve("conf"));
        Files.writeString(files.resolve("conf/app.toml"), "x = 1\n");
        Files.createSymbolicLink(files.resolve("alias.toml"), Path.of("conf/app.toml"));
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.empty(), 2, new SftpSubsystemFactory());
            try (FileBrowser browser = host.connectFileSource(RemoteHosts.ID, prod(host), set.window())) {
                String home = browser.home();
                var entries = new ArrayList<FileEntry>();
                browser.list(home, entries::add);
                assertThat(entries).extracting(FileEntry::name, FileEntry::kind).containsExactlyInAnyOrder(
                    tuple("conf", FileEntry.Kind.DIRECTORY), tuple("alias.toml", FileEntry.Kind.FILE));
                assertThat(new String(browser.read(browser.resolve(home, "alias.toml", home), 100))).isEqualTo("x = 1\n");
            }
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void theEditorOpensChosenRemoteFilesAndKnowsWhereTheyAre(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 1, new SftpSubsystemFactory());
            assertThat(editor.openers).as("Open in Editor… no longer asks Remote first").isEmpty();
            assertThat(host.menu("FILE")).doesNotContain("item:dev.jasper.remote.open-file");
            LocationResolver resolver = editor.locations.get(RemoteHosts.ID);
            String prod = prod(host);
            host.locateFile(RemoteHosts.ID, set.pane());
            WindowHandle window = set.context().terminals().window(set.window()).orElseThrow();
            EditorDocument document = resolver.document(new FileLocation.Sourced(RemoteHosts.ID, prod, "/notes.toml"), window);
            assertThat(document.name()).isEqualTo("notes.toml");
            assertThat(resolver.location(document.key())).contains(new FileLocation.Sourced(RemoteHosts.ID, prod, "/notes.toml"));
            assertThat(resolver.location("local:/tmp/a.txt")).isEmpty();
            EditorDocument created = resolver.document(new FileLocation.Sourced(RemoteHosts.ID, prod, "/new.txt"), window);
            done(host, created.write("fresh\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertThat(Files.readString(files.resolve("new.txt"))).as("Save As to a new remote file creates it").isEqualTo("fresh\n");
            host.stopAll();
            assertThat(editor.locations).as("stopping Remote removes its resolver").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }
}
