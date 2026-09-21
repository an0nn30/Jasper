package dev.jasper.app.persistence;

import java.awt.Point;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuddyStateFileTest {
    @TempDir Path directory;

    @Test void roundTripWritesExactTomlAndLeavesNoTemporaryFile() throws Exception {
        Path file = directory.resolve("nested/buddy.toml");
        BuddyStateFile.write(file, new Point(-12, 340));
        assertThat(Files.readString(file)).isEqualTo("version = 1\nx = -12\ny = 340\n");
        assertThat(BuddyStateFile.read(file)).contains(new Point(-12, 340));
        try (var listing = Files.list(file.getParent())) { assertThat(listing).containsExactly(file); }
    }

    @Test void missingFileMeansNoSavedPosition() throws Exception {
        assertThat(BuddyStateFile.read(directory.resolve("missing.toml"))).isEmpty();
    }

    @Test void malformedFilesAreRejectedNotRepaired() throws Exception {
        Path file = directory.resolve("buddy.toml");
        for (String text : List.of(
                "version = 2\nx = 1\ny = 2\n",
                "version = 1\nx = 1\n",
                "version = 1\nx = 1\ny = 2\nz = 3\n",
                "version = 1\nx = 1.5\ny = 2\n",
                "version = 1\nx = \"1\"\ny = 2\n",
                "version = 1\nx = 3000000000\ny = 2\n",
                "version = 1\nx = [\n")) {
            Files.writeString(file, text);
            assertThatThrownBy(() -> BuddyStateFile.read(file)).as("reject %s", text).isInstanceOf(IOException.class);
        }
        Files.writeString(file, " ".repeat(4097));
        assertThatThrownBy(() -> BuddyStateFile.read(file)).isInstanceOf(IOException.class);
        byte[] prefix = "version = 1\nx = 1\ny = 2\n# ".getBytes(StandardCharsets.UTF_8);
        byte[] malformed = java.util.Arrays.copyOf(prefix, prefix.length + 1);
        malformed[malformed.length - 1] = (byte) 0x80;
        Files.write(file, malformed);
        assertThatThrownBy(() -> BuddyStateFile.read(file)).isInstanceOf(IOException.class);
    }
}
