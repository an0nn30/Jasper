package dev.jasper.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TomlStateFileTest {
    @TempDir Path directory;

    @Test void absentFileReturnsEmpty() throws Exception {
        assertThat(TomlStateFile.readBounded(directory.resolve("missing.toml"), 100, "test file"))
            .isEmpty();
    }

    @Test void oversizedFileThrowsIoExceptionMentioningTheLabel() throws Exception {
        Path file = directory.resolve("big.toml");
        Files.writeString(file, " ".repeat(101));
        assertThatThrownBy(() -> TomlStateFile.readBounded(file, 100, "custom label"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("custom label")
            .hasMessageContaining("100");
    }

    @Test void writeAtomicallyIntoNestedMissingDirectoryProducesExactTextAndLeavesNoTempFile() throws Exception {
        Path file = directory.resolve("nested/deep/state.toml");
        String content = "key = \"value\"\n";
        TomlStateFile.writeAtomically(file, ".test-", content);
        assertThat(Files.readString(file)).isEqualTo(content);
        try (var listing = Files.list(file.getParent())) {
            assertThat(listing).containsExactly(file);
        }
    }
}
