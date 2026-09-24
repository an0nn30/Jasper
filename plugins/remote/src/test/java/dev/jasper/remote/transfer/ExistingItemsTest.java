package dev.jasper.remote.transfer;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.sftp.LocalEndpoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExistingItemsTest {
    @TempDir Path root;

    @Test void reportsOnlyTheSelectedNamesThatExist() throws IOException {
        root = root.toRealPath();
        Files.writeString(root.resolve("report.pdf"), "x");
        Files.createDirectory(root.resolve("photos"));
        try (var endpoint = new LocalEndpoint()) {
            assertThat(ExistingItems.existing(endpoint, root.toString(), List.of("report.pdf", "new.txt", "photos"))).containsExactly("report.pdf", "photos");
            assertThat(ExistingItems.existing(endpoint, root.toString(), List.of("new.txt"))).isEmpty();
        }
    }

    @Test void otherStatFailuresPropagate() throws IOException {
        root = root.toRealPath();
        Path file = Files.writeString(root.resolve("not-a-folder"), "x");
        try (var endpoint = new LocalEndpoint()) {
            assertThatThrownBy(() -> ExistingItems.existing(endpoint, file.toString(), List.of("child"))).isInstanceOf(IOException.class)
                .isNotInstanceOf(java.nio.file.NoSuchFileException.class);
        }
    }
}
