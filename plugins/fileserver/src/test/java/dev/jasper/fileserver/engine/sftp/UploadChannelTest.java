package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadChannelTest {
    @TempDir Path shared;

    @BeforeEach void layout() throws IOException { Files.writeString(shared.resolve("a.txt"), "hello"); }

    List<String> names() throws IOException {
        try (Stream<Path> files = Files.list(shared)) { return files.map(p -> p.getFileName().toString()).sorted().toList(); }
    }

    @Test void aCompleteUploadCommits() throws IOException {
        var channel = new UploadChannel(new ShareRoot(shared, false).upload("a.txt"), true);
        channel.write(ByteBuffer.wrap("new".getBytes(StandardCharsets.UTF_8)));
        channel.complete();
        channel.close();
        assertThat(channel.failed()).isFalse();
        assertThat(shared.resolve("a.txt")).hasContent("new");
        assertThat(names()).containsExactly("a.txt");
    }

    @Test void aFailedWriteAbandonsTheUploadEvenAfterANormalClose() throws IOException {
        var upload = new ShareRoot(shared, false).upload("a.txt");
        var channel = new UploadChannel(upload, true);
        channel.write(ByteBuffer.wrap("trunc".getBytes(StandardCharsets.UTF_8)));
        upload.channel().close(); // the next write fails, as a full disk would make it
        assertThatThrownBy(() -> channel.write(ByteBuffer.wrap("ated".getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(ClosedChannelException.class);
        assertThat(channel.failed()).isTrue();
        channel.complete();
        channel.close();
        assertThat(shared.resolve("a.txt")).as("the good file stays").hasContent("hello");
        assertThat(names()).as("no temporary file remains").containsExactly("a.txt");
    }

    @Test void aFailureReportedFromOutsideAbandonsTheUpload() throws IOException {
        var channel = new UploadChannel(new ShareRoot(shared, false).upload("a.txt"), true);
        channel.write(ByteBuffer.wrap("partial".getBytes(StandardCharsets.UTF_8)));
        channel.fail();
        channel.complete();
        channel.close();
        assertThat(shared.resolve("a.txt")).hasContent("hello");
        assertThat(names()).containsExactly("a.txt");
    }

    @Test void anExclusiveUploadDoesNotReplaceAFileCreatedMeanwhile() throws IOException {
        var channel = new UploadChannel(new ShareRoot(shared, false).upload("b.txt"), false);
        channel.write(ByteBuffer.wrap("mine".getBytes(StandardCharsets.UTF_8)));
        Files.writeString(shared.resolve("b.txt"), "theirs");
        channel.complete();
        assertThatThrownBy(channel::close).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(shared.resolve("b.txt")).hasContent("theirs");
        assertThat(names()).containsExactly("a.txt", "b.txt");
    }
}
