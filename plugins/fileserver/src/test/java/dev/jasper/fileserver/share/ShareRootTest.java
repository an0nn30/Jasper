package dev.jasper.fileserver.share;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

class ShareRootTest {
    @TempDir Path temp;
    Path shared, outside;

    @BeforeEach void layout() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        outside = Files.createDirectories(temp.resolve("outside"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(outside.resolve("secret.txt"), "secret");
    }

    interface ThrowingAction { void run() throws Exception; }

    static void link(Path link, Path target) {
        try { Files.createSymbolicLink(link, target); }
        catch (UnsupportedOperationException | IOException | SecurityException refused) { abort("symbolic links are not available: " + refused); }
    }

    @Test void resolvesInsideAndRefusesEveryClimbOut() throws IOException {
        var share = new ShareRoot(shared, true);
        Path real = shared.toRealPath();
        assertThat(share.root()).isEqualTo(real);
        assertThat(share.resolve("")).isEqualTo(real);
        assertThat(share.resolve("/")).isEqualTo(real);
        assertThat(share.resolve("/docs/a.txt")).isEqualTo(real.resolve("docs/a.txt"));
        assertThat(share.resolve("docs/./../docs//a.txt")).isEqualTo(real.resolve("docs/a.txt"));
        assertThat(share.resolve("docs\\a.txt")).as("a backslash separates too").isEqualTo(real.resolve("docs/a.txt"));
        for (String escape : new String[]{"..", "/../outside/secret.txt", "docs/../../outside", "docs\\..\\..\\outside", "\\\\server\\share"})
            assertThatThrownBy(() -> share.resolve(escape)).as(escape).isInstanceOf(PathRefusedException.class);
        assertThat(ShareRoot.segments("/a/b/../c/")).containsExactly("a", "c");
    }

    @Test void namesThePlatformCannotRepresentAreRefusedNotThrown() throws IOException {
        var share = new ShareRoot(shared, false);
        assertThatThrownBy(() -> share.resolve("x\uD800")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.resolve("docs/x\uD800")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.openWrite("x\uD800", false)).isInstanceOf(PathRefusedException.class);
    }

    @Test void refusesNamesNoPlatformCanShareSafely() throws IOException {
        for (String bad : new String[]{"a:b", "C:", "CON", "con.txt", "Lpt1", "aux .log", "name.", "name ", "tab\tname", "nul\0", "del\177", "csi\u009bx"})
            assertThatThrownBy(() -> ShareRoot.segments("docs/" + bad)).as(bad).isInstanceOf(PathRefusedException.class);
        assertThat(ShareRoot.segments(".hidden/console/com10/lpt.txt")).containsExactly(".hidden", "console", "com10", "lpt.txt");
    }

    @Test void readsAttributesAndListsFoldersFirst() throws IOException {
        Files.writeString(shared.resolve("B.txt"), "bb");
        Files.writeString(shared.resolve("a.txt"), "a");
        Files.createDirectories(shared.resolve("zeta"));
        var share = new ShareRoot(shared, true);
        assertThat(share.list("")).extracting(ShareEntry::name).containsExactly("docs", "zeta", "a.txt", "B.txt");
        ShareEntry file = share.attributes("docs/a.txt");
        assertThat(file.directory()).isFalse();
        assertThat(file.size()).isEqualTo(5);
        assertThat(share.attributes("docs").directory()).isTrue();
        assertThatThrownBy(() -> share.attributes("missing.txt")).isInstanceOf(NoSuchFileException.class);
        try (SeekableByteChannel channel = share.openRead("docs/a.txt")) {
            var buffer = ByteBuffer.allocate(16);
            channel.position(1);
            channel.read(buffer);
            assertThat(new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8)).isEqualTo("ello");
        }
        assertThatThrownBy(() -> share.openRead("docs")).isInstanceOf(IsFolderException.class);
    }

    @Test void linksLeavingTheShareAreRefusedAndHidden() throws IOException {
        link(shared.resolve("out-file"), outside.resolve("secret.txt"));
        link(shared.resolve("out-dir"), outside);
        link(shared.resolve("chain"), shared.resolve("out-file"));
        link(shared.resolve("dangling"), temp.resolve("nowhere"));
        link(shared.resolve("inward"), shared.resolve("docs/a.txt"));
        var share = new ShareRoot(shared, false);
        for (String refused : new String[]{"out-file", "out-dir/secret.txt", "chain", "dangling", "out-dir/new.txt"})
            assertThatThrownBy(() -> share.resolve(refused)).as(refused).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.openWrite("out-dir/new.txt", false)).isInstanceOf(PathRefusedException.class);
        assertThat(share.resolve("inward")).isEqualTo(shared.toRealPath().resolve("docs/a.txt"));
        assertThat(share.attributes("inward").name()).as("a link reports its own name").isEqualTo("inward");
        assertThat(share.list("")).extracting(ShareEntry::name).containsExactly("docs", "inward");
        for (var write : List.<ThrowingAction>of(
                () -> share.openWrite("out-file", false).close(),
                () -> share.openWrite("out-file", true).close(),
                () -> share.mkdir("dangling"),
                () -> share.rename("docs/a.txt", "out-file")))
            assertThatThrownBy(write::run).isInstanceOf(IOException.class);
        assertThat(outside.resolve("secret.txt")).as("writes never follow a final link").hasContent("secret");
        share.delete("inward");
        assertThat(shared.resolve("docs/a.txt")).as("deleting a link removes the link, not its target").exists();
    }

    @Test void aReadOnlyShareRefusesEveryWrite() throws IOException {
        var share = new ShareRoot(shared, true);
        assertThatThrownBy(() -> share.openWrite("new.txt", false)).isInstanceOf(ReadOnlyShareException.class);
        assertThatThrownBy(() -> share.upload("new.txt")).isInstanceOf(ReadOnlyShareException.class);
        assertThatThrownBy(() -> share.mkdir("new")).isInstanceOf(ReadOnlyShareException.class);
        assertThatThrownBy(() -> share.delete("docs/a.txt")).isInstanceOf(ReadOnlyShareException.class);
        assertThatThrownBy(() -> share.rename("docs/a.txt", "b.txt")).isInstanceOf(ReadOnlyShareException.class);
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
    }

    @Test void aWritableShareCreatesAppendsRenamesAndDeletes() throws IOException {
        var share = new ShareRoot(shared, false);
        try (OutputStream out = share.openWrite("docs/new.txt", false)) { out.write("one".getBytes(StandardCharsets.UTF_8)); }
        try (OutputStream out = share.openWrite("docs/new.txt", true)) { out.write("two".getBytes(StandardCharsets.UTF_8)); }
        assertThat(shared.resolve("docs/new.txt")).hasContent("onetwo");
        share.mkdir("made");
        share.rename("docs/new.txt", "made/moved.txt");
        assertThat(shared.resolve("made/moved.txt")).hasContent("onetwo");
        assertThatThrownBy(() -> share.delete("made")).isInstanceOf(DirectoryNotEmptyException.class);
        share.delete("made/moved.txt");
        share.delete("made");
        assertThat(shared.resolve("made")).doesNotExist();
        assertThatThrownBy(() -> share.openWrite("docs", false)).isInstanceOf(IsFolderException.class);
        assertThatThrownBy(() -> share.delete("")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.rename("", "x")).isInstanceOf(PathRefusedException.class);
    }

    List<String> names(Path folder) throws IOException {
        try (var stream = Files.list(folder)) { return stream.map(p -> p.getFileName().toString()).sorted().toList(); }
    }

    @Test void aCommittedUploadReplacesTheTargetOnlyAtTheEnd() throws IOException {
        var share = new ShareRoot(shared, false);
        try (var upload = share.upload("docs/a.txt")) {
            upload.stream().write("brand new content".getBytes(StandardCharsets.UTF_8));
            assertThat(shared.resolve("docs/a.txt")).as("untouched until the commit").hasContent("hello");
            assertThat(names(shared.resolve("docs"))).anyMatch(n -> n.startsWith(".jasper-upload-") && n.endsWith(".part"));
            assertThat(share.list("docs")).extracting(ShareEntry::name).as("a temporary file is not listed").containsExactly("a.txt");
            upload.commit();
        }
        assertThat(shared.resolve("docs/a.txt")).hasContent("brand new content");
        assertThat(names(shared.resolve("docs"))).containsExactly("a.txt");
        try (var upload = share.upload("docs/fresh.txt")) {
            upload.stream().write('x');
            upload.commit();
        }
        assertThat(shared.resolve("docs/fresh.txt")).hasContent("x");
        assertThat(names(shared.resolve("docs"))).containsExactly("a.txt", "fresh.txt");
    }

    @Test void anAbandonedUploadLeavesTheTargetAndNoTemporaryFile() throws IOException {
        var share = new ShareRoot(shared, false);
        try (var upload = share.upload("docs/a.txt")) { upload.stream().write("partial".getBytes(StandardCharsets.UTF_8)); }
        try (var upload = share.upload("docs/never.txt")) { upload.stream().write('x'); }
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(names(shared.resolve("docs"))).containsExactly("a.txt");
    }

    @Test void anUploadKeepsTheSamePathRules() throws IOException {
        var share = new ShareRoot(shared, false);
        assertThatThrownBy(() -> share.upload("docs")).isInstanceOf(IsFolderException.class);
        assertThatThrownBy(() -> share.upload("")).isInstanceOf(IsFolderException.class);
        assertThatThrownBy(() -> share.upload("../outside/x.txt")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.upload("missing/x.txt")).isInstanceOf(NoSuchFileException.class);
        link(shared.resolve("out-dir"), outside);
        assertThatThrownBy(() -> share.upload("out-dir/new.txt")).isInstanceOf(PathRefusedException.class);
        link(shared.resolve("out-file"), outside.resolve("secret.txt"));
        try (var upload = share.upload("out-file")) { upload.stream().write('x'); upload.commit(); }
        assertThat(outside.resolve("secret.txt")).as("an upload never writes through a final link").hasContent("secret");
        assertThat(names(outside)).containsExactly("secret.txt");
    }
}
