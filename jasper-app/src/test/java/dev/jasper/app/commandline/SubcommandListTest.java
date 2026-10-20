package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubcommandListTest {
    @TempDir Path dir;

    @Test void writesOneLinePerSubcommandAndReadsThemBack() throws IOException {
        Path file = dir.resolve("daemon").resolve("subcommands");
        var entries = List.of(new SubcommandList.Entry("editor", "Open files in Jasper's Editor", "Editor"),
            new SubcommandList.Entry("vault", "Look up a login", "Credential Vault"));
        SubcommandList.write(file, entries);
        assertThat(Files.readString(file))
            .isEqualTo("editor\tOpen files in Jasper's Editor\tEditor\nvault\tLook up a login\tCredential Vault\n");
        assertThat(SubcommandList.read(file)).isEqualTo(entries);
        SubcommandList.write(file, List.of());
        assertThat(SubcommandList.read(file)).isEmpty();
        try (var listing = Files.list(file.getParent())) {
            assertThat(listing).as("no temporary file is left").containsExactly(file);
        }
    }

    @Test void aMissingFileOrAMalformedLineReadsAsNothing() throws IOException {
        assertThat(SubcommandList.read(dir.resolve("missing"))).isEmpty();
        Path file = dir.resolve("subcommands");
        Files.writeString(file, "editor\tOpen\tEditor\nbroken line\n");
        assertThat(SubcommandList.read(file)).containsExactly(new SubcommandList.Entry("editor", "Open", "Editor"));
    }

    @Test void anEntryIsOneLineOfThreeFields() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SubcommandList.Entry("a\tb", "s", "p"));
        assertThatIllegalArgumentException().isThrownBy(() -> new SubcommandList.Entry("a", "two\nlines", "p"));
        assertThatIllegalArgumentException().isThrownBy(() -> new SubcommandList.Entry("a", "s", "p\r"));
    }
}
