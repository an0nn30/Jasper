package dev.jasper.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandHistoryFileTest {
    @TempDir Path directory;

    @Test void roundTripKeepsNewestOrderAndNoQueryData() throws Exception {
        Path file = directory.resolve("nested/command-history.toml");

        CommandHistoryFile.write(file, List.of("split_right", "new_tab", "open_settings"));

        assertThat(CommandHistoryFile.read(file))
            .containsExactly("split_right", "new_tab", "open_settings");
        assertThat(Files.readString(file)).isEqualTo(
            "version = 1\nrecent = [\"split_right\", \"new_tab\", \"open_settings\"]\n");
    }

    @Test void missingHistoryIsEmpty() throws Exception {
        assertThat(CommandHistoryFile.read(directory.resolve("missing.toml"))).isEmpty();
    }

    @Test void oversizedOrUnsupportedHistoryIsRejected() throws Exception {
        Path file = directory.resolve("command-history.toml");
        Files.writeString(file, " ".repeat(16385));
        assertThatThrownBy(() -> CommandHistoryFile.read(file)).isInstanceOf(IOException.class);
        Files.writeString(file, "version = 2\nrecent = []\n");
        assertThatThrownBy(() -> CommandHistoryFile.read(file)).isInstanceOf(IOException.class);
    }

    @Test void malformedHistoryIsRejectedWithoutSilentlyRepairingIt() throws Exception {
        Path file = directory.resolve("command-history.toml");
        for (String text : List.of(
                "version = 1\nrecent = []\nextra = true\n",
                "version = 1\nrecent = [\"one\", \"two\", \"three\", \"four\"]\n",
                "version = 1\nrecent = [\"Bad ID\"]\n",
                "version = 1\nrecent = [1]\n")) {
            Files.writeString(file, text);
            assertThatThrownBy(() -> CommandHistoryFile.read(file))
                .as("reject %s", text)
                .isInstanceOf(IOException.class);
        }
    }

    @Test void malformedUtf8IsRejectedEvenWhenItOnlyAppearsInAComment() throws Exception {
        Path file = directory.resolve("command-history.toml");
        byte[] prefix = "version = 1\nrecent = []\n# ".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] malformed = java.util.Arrays.copyOf(prefix, prefix.length + 1);
        malformed[malformed.length - 1] = (byte) 0x80;
        Files.write(file, malformed);

        assertThatThrownBy(() -> CommandHistoryFile.read(file)).isInstanceOf(IOException.class);
    }

    @Test void invalidWriteSnapshotIsRejected() {
        assertThatThrownBy(() -> CommandHistoryFile.write(directory.resolve("many.toml"),
            List.of("one", "two", "three", "four"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CommandHistoryFile.write(directory.resolve("invalid.toml"),
            List.of("query text"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CommandHistoryFile.write(directory.resolve("null.toml"),
            java.util.Arrays.asList((String) null))).isInstanceOf(IOException.class);
    }
}
