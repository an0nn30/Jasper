package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ReadOnlyShareException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginRequest;
import org.apache.ftpserver.usermanager.impl.WriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareViewTest {
    @TempDir Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();

    @BeforeEach void files() throws IOException {
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(shared.resolve("b.txt"), "bee");
    }

    ShareView view(boolean writable) throws IOException {
        return new ShareView(new ShareRoot(shared, !writable), new ServerEvents(new TestClock(), events::add), "10.0.0.2");
    }

    List<String> docs() throws IOException {
        try (var listing = Files.list(shared.resolve("docs"))) { return listing.map(p -> p.getFileName().toString()).sorted().toList(); }
    }

    List<ServerEvent.Transferred> transferred() {
        return events.stream().filter(e -> e instanceof ServerEvent.Transferred).map(e -> (ServerEvent.Transferred) e).toList();
    }

    @Test void theWorkingDirectoryMovesAndStopsAtTheRoot() throws Exception {
        var view = view(false);
        assertThat(view.getHomeDirectory().getAbsolutePath()).isEqualTo("/");
        assertThat(view.getHomeDirectory().getName()).isEqualTo("/");
        assertThat(view.changeWorkingDirectory("docs")).isTrue();
        assertThat(view.getWorkingDirectory().getAbsolutePath()).isEqualTo("/docs");
        assertThat(view.changeWorkingDirectory("missing")).isFalse();
        assertThat(view.changeWorkingDirectory("a.txt")).as("a file is not a folder").isFalse();
        assertThat(view.changeWorkingDirectory("CON")).isFalse();
        assertThat(view.getWorkingDirectory().getAbsolutePath()).isEqualTo("/docs");
        assertThat(view.changeWorkingDirectory("../../..")).isTrue();
        assertThat(view.getWorkingDirectory().getAbsolutePath()).isEqualTo("/");
        assertThat(view.isRandomAccessible()).isTrue();
        assertThatThrownBy(() -> view.getFile("a:b")).isInstanceOf(FtpException.class);
    }

    @Test void filesDescribeTheShare() throws Exception {
        var view = view(false);
        FtpFile a = view.getFile("/docs/a.txt");
        assertThat(a.doesExist()).isTrue();
        assertThat(a.isFile()).isTrue();
        assertThat(a.isDirectory()).isFalse();
        assertThat(a.isReadable()).isTrue();
        assertThat(a.isWritable()).as("read-only share").isFalse();
        assertThat(a.isRemovable()).isFalse();
        assertThat(a.isHidden()).isFalse();
        assertThat(a.getSize()).isEqualTo(5);
        assertThat(a.getName()).isEqualTo("a.txt");
        assertThat(a.getLastModified()).isEqualTo(Files.getLastModifiedTime(shared.resolve("docs/a.txt")).toMillis());
        assertThat(a.getPhysicalFile()).isNull();
        assertThat(view.getFile("/nope").doesExist()).isFalse();
        assertThat(view.getHomeDirectory().listFiles()).extracting(FtpFile::getAbsolutePath, FtpFile::isDirectory)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("/docs", true), org.assertj.core.groups.Tuple.tuple("/b.txt", false));
        assertThat(view.getFile("docs").listFiles()).extracting(FtpFile::getAbsolutePath).containsExactly("/docs/a.txt");
    }

    @Test void aDownloadReadsFromTheOffsetAndFinishesWithTheReply() throws Exception {
        var view = view(false);
        try (InputStream in = view.getFile("/docs/a.txt").createInputStream(2)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("llo");
        }
        assertThat(transferred()).as("nothing ends before the reply").isEmpty();
        view.finish(true);
        var started = (ServerEvent.TransferStarted) events.stream().filter(e -> e instanceof ServerEvent.TransferStarted).findFirst().orElseThrow();
        assertThat(started.total()).isEqualTo(3);
        assertThat(started.remote()).isEqualTo("10.0.0.2");
        assertThat(transferred()).singleElement().satisfies(done -> {
            assertThat(done.path()).isEqualTo("/docs/a.txt");
            assertThat(done.direction()).isEqualTo(ServerEvent.Direction.DOWNLOAD);
            assertThat(done.bytes()).isEqualTo(3);
            assertThat(done.complete()).isTrue();
        });
        view.finish(true);
        assertThat(transferred()).as("finishing twice reports once").hasSize(1);
    }

    @Test void anUploadAppearsOnlyAfterASuccessfulReply() throws Exception {
        var view = view(true);
        try (OutputStream out = view.getFile("/docs/new.txt").createOutputStream(0)) {
            out.write("abc".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(shared.resolve("docs/new.txt")).as("not before the 226").doesNotExist();
        view.finish(true);
        assertThat(shared.resolve("docs/new.txt")).hasContent("abc");
        assertThat(docs()).containsExactly("a.txt", "new.txt");
        assertThat(transferred()).singleElement().satisfies(done -> {
            assertThat(done.direction()).isEqualTo(ServerEvent.Direction.UPLOAD);
            assertThat(done.bytes()).isEqualTo(3);
            assertThat(done.complete()).isTrue();
        });
    }

    @Test void aFailedOrAbandonedUploadLeavesTheOriginal() throws Exception {
        var view = view(true);
        try (OutputStream out = view.getFile("/docs/a.txt").createOutputStream(0)) { out.write('x'); }
        view.finish(false);
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(docs()).containsExactly("a.txt");
        try (OutputStream out = view.getFile("/docs/a.txt").createOutputStream(0)) { out.write('y'); }
        view.getFile("/b.txt").createInputStream(0).close();
        assertThat(shared.resolve("docs/a.txt")).as("a new transfer abandons the pending upload").hasContent("hello");
        assertThat(docs()).containsExactly("a.txt");
        view.dispose();
        assertThat(transferred()).extracting(ServerEvent.Transferred::complete).containsExactly(false, false, false);
    }

    @Test void onlyAnAppendAtTheEndResumes() throws Exception {
        var view = view(true);
        FtpFile a = view.getFile("/docs/a.txt");
        assertThatThrownBy(() -> a.createOutputStream(2)).isInstanceOf(IOException.class)
            .hasMessage("Resuming an upload part-way through a file is not supported");
        try (OutputStream out = a.createOutputStream(5)) { out.write("!".getBytes(StandardCharsets.UTF_8)); }
        view.finish(true);
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello!");
    }

    @Test void changesGoThroughTheShareAndAreReported() throws Exception {
        var view = view(true);
        assertThat(view.getFile("/docs/sub").mkdir()).isTrue();
        assertThat(view.getFile("/docs/sub").mkdir()).as("already there").isFalse();
        assertThat(view.getFile("/b.txt").move(view.getFile("/docs/c.txt"))).isTrue();
        assertThat(view.getFile("/docs/c.txt").isRemovable()).isTrue();
        assertThat(view.getFile("/docs/c.txt").delete()).isTrue();
        assertThat(view.getHomeDirectory().isWritable()).as("the root is never a file target").isFalse();
        assertThat(view.getHomeDirectory().isRemovable()).isFalse();
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Changed).extracting(e -> ((ServerEvent.Changed) e).description())
            .containsExactly("created /docs/sub", "renamed /b.txt to /docs/c.txt", "deleted /docs/c.txt");
    }

    @Test void aReadOnlyShareRefusesEveryChange() throws Exception {
        var view = view(false);
        assertThat(view.getFile("/docs/sub").mkdir()).isFalse();
        assertThat(view.getFile("/b.txt").delete()).isFalse();
        assertThat(view.getFile("/b.txt").move(view.getFile("/c.txt"))).isFalse();
        assertThatThrownBy(() -> view.getFile("/c.txt").createOutputStream(0)).isInstanceOf(ReadOnlyShareException.class);
        assertThat(events).isEmpty();
    }

    @Test void filesWithTheSamePathAreEqual() throws Exception {
        var view = view(false);
        assertThat(view.getFile("/docs")).isEqualTo(view.getFile("/docs")).hasSameHashCodeAs(view.getFile("/docs"));
        assertThat(view.getFile("/docs")).isNotEqualTo(view.getFile("/b.txt")).isNotEqualTo("/docs");
        view.changeWorkingDirectory("docs");
        assertThat(view.getWorkingDirectory()).isEqualTo(view.getFile("/docs"));
    }

    @Test void cancellingClosesTheStream() throws Exception {
        var view = view(false);
        InputStream in = view.getFile("/docs/a.txt").createInputStream(0);
        var started = (ServerEvent.TransferStarted) events.getFirst();
        started.cancel().run();
        assertThatThrownBy(in::read).isInstanceOf(IOException.class);
        view.finish(false);
        assertThat(transferred()).singleElement().extracting(ServerEvent.Transferred::complete).isEqualTo(false);
    }

    @Test void theFileSystemGivesEachLoginAViewWithItsAddress() throws Exception {
        var system = new ShareFileSystem(new ShareRoot(shared, true), new ServerEvents(new TestClock(), events::add));
        var view = (ShareView) system.createFileSystemView(new ShareUser("dustin", "192.168.1.9", false));
        assertThat(view.remote()).isEqualTo("192.168.1.9");
        var reader = new ShareUser("dustin", "192.168.1.9", false);
        var writer = new ShareUser("dustin", "192.168.1.9", true);
        assertThat(reader.getName()).isEqualTo("dustin");
        assertThat(reader.getHomeDirectory()).isEqualTo("/");
        assertThat(reader.getEnabled()).isTrue();
        assertThat(reader.authorize(new WriteRequest())).isNull();
        assertThat(writer.authorize(new WriteRequest())).isNotNull();
        assertThat(reader.authorize(new ConcurrentLoginRequest(5, 5))).isNotNull();
    }
}
