package dev.jasper.app.files;

import static dev.jasper.app.files.ChooserRequest.Mode.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ChooserRequestTest {
    @TempDir Path dir;

    @Test void opensTheInitialFolderElseTheTerminalFolderElseHome() throws Exception {
        Path home = Files.createDirectory(dir.resolve("home"));
        Path work = Files.createDirectory(dir.resolve("work"));
        Path other = Files.createDirectory(dir.resolve("other"));
        assertThat(ChooserRequest.of("Open", OPEN, true, Optional.of(other), Optional.of(work), Optional.empty(), home).start()).isEqualTo(other);
        assertThat(ChooserRequest.of("Open", OPEN, true, Optional.empty(), Optional.of(work), Optional.empty(), home).start()).isEqualTo(work);
        assertThat(ChooserRequest.of("Open", OPEN, true, Optional.empty(), Optional.empty(), Optional.empty(), home).start()).isEqualTo(home);
        var request = ChooserRequest.of("Open", OPEN, false, Optional.empty(), Optional.of(work), Optional.empty(), home);
        assertThat(request.terminalFolder()).contains(work);
        assertThat(request.suggestedName()).as("the terminal folder never names a file").isEmpty();
    }

    @Test void aFileOpensItsFolderAndSuggestsItsName() throws Exception {
        Path work = Files.createDirectory(dir.resolve("work"));
        var request = ChooserRequest.of("Save As", SAVE, false, Optional.of(work.resolve("notes.md")), Optional.empty(), Optional.empty(), dir);
        assertThat(request.start()).isEqualTo(work);
        assertThat(request.suggestedName()).contains("notes.md");
    }

    @Test void onlyOpenSelectsSeveralFiles() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new ChooserRequest("Save", SAVE, true, dir, Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
