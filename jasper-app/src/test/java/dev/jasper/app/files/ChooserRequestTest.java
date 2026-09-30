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

    private static ChooserRequest of(ChooserRequest.Mode mode, Optional<Location> initial, Optional<Location> terminal, Path home) {
        return ChooserRequest.of("Choose", mode, false, initial, terminal, Optional.empty(), Optional.empty(), Optional.empty(), home);
    }

    @Test void opensTheInitialFolderElseTheTerminalFolderElseHome() throws Exception {
        Path home = Files.createDirectory(dir.resolve("home"));
        Location work = Location.local(Files.createDirectory(dir.resolve("work")));
        Location other = Location.local(Files.createDirectory(dir.resolve("other")));
        assertThat(of(OPEN, Optional.of(other), Optional.of(work), home).start()).isEqualTo(other);
        assertThat(of(OPEN, Optional.empty(), Optional.of(work), home).start()).isEqualTo(work);
        assertThat(of(OPEN, Optional.empty(), Optional.empty(), home).start()).isEqualTo(Location.local(home));
        var request = of(OPEN, Optional.empty(), Optional.of(work), home);
        assertThat(request.terminalFolder()).contains(work);
        assertThat(request.suggestedName()).as("the terminal folder never names a file").isEmpty();
    }

    @Test void aLocalFileOpensItsFolderAndSuggestsItsNameAndASourcedStartIsKept() throws Exception {
        Path work = Files.createDirectory(dir.resolve("work"));
        var request = of(SAVE, Optional.of(Location.local(work.resolve("notes.md"))), Optional.empty(), dir);
        assertThat(request.start()).isEqualTo(Location.local(work));
        assertThat(request.suggestedName()).contains("notes.md");
        var remote = new Location("dev.x.hosts", "box", "/srv");
        assertThat(of(OPEN, Optional.empty(), Optional.of(remote), dir).start()).isEqualTo(remote);
        var named = ChooserRequest.of("Save", SAVE, false, Optional.of(remote), Optional.empty(), Optional.of("x.txt"), Optional.empty(), Optional.empty(), dir);
        assertThat(named.suggestedName()).contains("x.txt");
    }

    @Test void onlyOpenSelectsSeveralFiles() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ChooserRequest("Save", SAVE, true, Location.local(dir),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
