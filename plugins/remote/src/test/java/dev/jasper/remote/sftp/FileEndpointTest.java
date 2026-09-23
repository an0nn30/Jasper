package dev.jasper.remote.sftp;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FileEndpointTest {
    @TempDir Path directory;

    static void contract(FileEndpoint endpoint, String root) throws Exception {
        String temp = endpoint.child(root, "partial"), target = endpoint.child(root, "final");
        for (String bad : new String[]{"..", ".", "../escape", "/escape", "bad\0name"})
            assertThatThrownBy(() -> endpoint.child(root, bad)).isInstanceOf(IOException.class);
        try (var out = endpoint.write(temp, 0, true)) {
            out.write(new byte[]{0,1,2,3});
            assertThat(out.checkpoint()).isEqualTo(4);
        }
        try (var in = endpoint.read(temp, 2)) { assertThat(in.readAllBytes()).containsExactly(2,3); }
        assertThatThrownBy(() -> endpoint.write(temp, 0, true)).isInstanceOf(IOException.class);
        try (var out = endpoint.write(temp, 4, false)) { out.write(4); assertThat(out.checkpoint()).isEqualTo(5); }
        endpoint.publish(temp, target, false);
        assertThat(endpoint.stat(target).size()).isEqualTo(5);
        assertThatThrownBy(() -> endpoint.stat(temp)).isInstanceOf(NoSuchFileException.class);
        try (var out = endpoint.write(temp, 0, true)) { out.write(9); }
        assertThatThrownBy(() -> endpoint.publish(temp, target, false)).isInstanceOf(IOException.class);
        try (var in = endpoint.read(target, 0)) { assertThat(in.readAllBytes()).containsExactly(0,1,2,3,4); }
        endpoint.publish(temp, target, true);
        assertThat(endpoint.stat(target).size()).isEqualTo(1);
        endpoint.symlink(root + "/dangling", "missing");
        assertThat(endpoint.stat(root + "/dangling").kind()).isEqualTo(FileEntry.Kind.LINK);
        assertThat(endpoint.stat(root + "/dangling").linkTarget()).isEqualTo("missing");
        endpoint.mkdir(root + "/empty");
        endpoint.symlink(root + "/alias", "empty");
        assertThatThrownBy(() -> endpoint.write(root + "/alias/escape", 0, true)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> endpoint.list(root + "/alias", e -> {})).isInstanceOf(IOException.class);
        assertThat(endpoint.resolveDirectory(root+"/alias")).isEqualTo(endpoint.child(root,"empty"));
        assertThat(endpoint.stat(root+"/alias").kind()).isEqualTo(FileEntry.Kind.LINK);
        endpoint.mkdir(root+"/empty/child");
        assertThat(endpoint.resolveDirectory(root+"/alias/child")).isEqualTo(endpoint.child(endpoint.child(root,"empty"),"child"));
        endpoint.remove(root+"/empty/child",true);
        endpoint.remove(root + "/alias", false);
        endpoint.symlink(root + "/link-temp", "final");
        endpoint.publish(root + "/link-temp", root + "/link-final", false);
        assertThat(endpoint.stat(root + "/link-final").kind()).isEqualTo(FileEntry.Kind.LINK);
        assertThat(endpoint.stat(root + "/link-final").linkTarget()).isEqualTo("final");
        assertThatThrownBy(() -> endpoint.metadata(root + "/link-final", 0, 0)).isInstanceOf(IOException.class);
        endpoint.remove(root + "/link-final", false);
        var entries = new ArrayList<FileEntry>(); endpoint.list(root, entries::add);
        assertThat(entries).extracting(FileEntry::name).containsExactlyInAnyOrder("final", "dangling", "empty");
        endpoint.remove(root + "/dangling", false); endpoint.remove(root + "/empty", true);
        endpoint.metadata(target, 1_700_000_000_000L, 0640);
        assertThat(endpoint.stat(target).permissions() & 0777).isEqualTo(0640);
        assertThat(endpoint.stat(target).modifiedMillis()).isEqualTo(1_700_000_000_000L);
    }

    @Test void localContract() throws Exception {
        try (var endpoint = new LocalEndpoint()) { contract(endpoint, directory.toRealPath().toString()); }
    }
    @Test void sparseOffsetsAreLongAndAbortClosesOwnedHandles() throws Exception {
        try (var endpoint = new LocalEndpoint()) {
            String file = directory.toRealPath().resolve("large").toString();
            try (var out = endpoint.write(file, 3L * 1024 * 1024 * 1024, true)) {
                out.write(42); assertThat(out.checkpoint()).isEqualTo(3L * 1024 * 1024 * 1024 + 1);
            }
            try (var in = endpoint.read(file, 3L * 1024 * 1024 * 1024)) { assertThat(in.read()).isEqualTo(42); }
            var out = endpoint.write(file, 0, false); endpoint.abort();
            assertThatThrownBy(() -> out.write(1)).isInstanceOf(IOException.class);
        }
    }
    @Test void rejectsIntermediateLinksWithoutTouchingTheirReferents() throws Exception {
        Path root = directory.toRealPath(); Files.createDirectory(root.resolve("real"));
        Files.createSymbolicLink(root.resolve("alias"), Path.of("real"));
        try (var endpoint = new LocalEndpoint()) {
            assertThatThrownBy(() -> endpoint.write(root.resolve("alias/escape").toString(), 0, true)).isInstanceOf(IOException.class);
            assertThat(Files.exists(root.resolve("real/escape"))).isFalse();
            assertThatThrownBy(() -> endpoint.list(root.resolve("alias").toString(), e -> {})).isInstanceOf(IOException.class);
        }
    }
}
