package dev.jasper.sdk.files;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.JasperSdk;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FilesValuesTest {
    private final FileBrowser posix = new FileBrowser() {
        @Override public String home() { throw new UnsupportedOperationException(); }
        @Override public void list(String directory, Consumer<FileEntry> visitor) { throw new UnsupportedOperationException(); }
        @Override public Optional<FileEntry> stat(String path) { throw new UnsupportedOperationException(); }
        @Override public byte[] read(String path, int maxBytes) { throw new UnsupportedOperationException(); }
        @Override public void mkdir(String path) { throw new UnsupportedOperationException(); }
        @Override public void abort() { }
        @Override public void close() { }
    };

    @Test void valuesRejectWhatTheChooserCannotShow() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FileEntry("a/b", FileEntry.Kind.FILE, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileEntry("", FileEntry.Kind.FILE, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileRoot("a|b", "A", "", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileRoot("a", " ", "", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileSourceSpec("x|y", "Hosts"));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileSourceSpec("x", " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLocation.Sourced("s", "r", "relative"));
        assertThat(new FileLocation.Sourced("s", "r", FileLocation.Sourced.HOME).path()).as("a root's home").isEqualTo("~");
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLocation.Sourced("s", "r", "~/x"));
        WindowOwner owner = new WindowOwner() { };
        assertThatIllegalArgumentException().isThrownBy(() ->
            new FileChoice(owner, "Save", FileChoice.Mode.SAVE, true, Optional.empty(), Optional.empty(), Set.of()));
        assertThat(new FileLocation.Local(Path.of("a/../b")).path()).isEqualTo(Path.of("b").toAbsolutePath());
    }

    @Test void pathsUseSlashes() {
        assertThat(posix.parent("/home/amp/x.txt")).contains("/home/amp");
        assertThat(posix.parent("/home")).contains("/");
        assertThat(posix.parent("/")).isEmpty();
        assertThat(posix.name("/home/amp/")).isEqualTo("amp");
        assertThat(posix.name("/")).isEqualTo("/");
        assertThat(posix.resolve("/home/amp", "../x/./y", "/home/amp")).isEqualTo("/home/x/y");
        assertThat(posix.resolve("/srv", "~/notes", "/home/amp")).isEqualTo("/home/amp/notes");
        assertThat(posix.resolve("/srv", "~", "/home/amp")).isEqualTo("/home/amp");
        assertThat(posix.resolve("/srv", "/etc//hosts", "/home/amp")).isEqualTo("/etc/hosts");
        assertThat(posix.resolve("/", "../..", "/")).isEqualTo("/");
    }

    @Test void theSdkIsVersionTenWithTheFileSourceCapability() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.16.2");
        assertThat(Capabilities.ALL).contains(Capabilities.FILES_SOURCE);
        assertThat(Capabilities.FILES_SOURCE).isEqualTo("files.source");
    }
}
