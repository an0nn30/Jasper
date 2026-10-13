package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VmOptionsFileTest {
    @TempDir Path home;

    @Test void aMissingFileOrADirectoryReadsAsAbsent() throws IOException {
        assertThat(VmOptionsFile.read(home.resolve("jasper.vmoptions"))).isEqualTo(VmOptionsFile.Contents.ABSENT);
        Files.createDirectory(home.resolve("dir.vmoptions"));
        assertThat(VmOptionsFile.read(home.resolve("dir.vmoptions")).exists()).isFalse();
        Files.writeString(home.resolve("jasper.vmoptions"), "-Xmx4g\nXmx1g\n");
        var contents = VmOptionsFile.read(home.resolve("jasper.vmoptions"));
        assertThat(contents.exists()).isTrue();
        assertThat(contents.text()).isEqualTo("-Xmx4g\nXmx1g\n");
        assertThat(contents.parsed().lines()).containsExactly("-Xmx4g");
        assertThat(contents.parsed().ignored()).containsExactly("Xmx1g");
    }

    @Test void theLastXmxLineIsReplacedAndEverythingElseKept() {
        String text = "# mine\n-Xmx1g\n-Dfoo=bar\n   -Xmx2g\n#-Xmx9g\n";
        assertThat(VmOptionsFile.withMaxHeap(text, 4096, 2048)).isEqualTo("# mine\n-Xmx1g\n-Dfoo=bar\n-Xmx4096m\n#-Xmx9g\n");
    }

    @Test void withoutAnXmxLineOneIsAppended() {
        assertThat(VmOptionsFile.withMaxHeap("-Dfoo=bar", 4096, 2048)).isEqualTo("-Dfoo=bar\n-Xmx4096m\n");
        assertThat(VmOptionsFile.withMaxHeap("", 4096, 2048)).isEqualTo(VmOptionsFile.HEADER + "\n\n-Xmx4096m\n");
        assertThat(VmOptionsFile.withMaxHeap("\n\n", 1024, 2048)).isEqualTo(VmOptionsFile.HEADER + "\n\n-Xmx1024m\n");
    }

    @Test void theBundledDefaultRemovesEveryXmxLine() {
        assertThat(VmOptionsFile.withMaxHeap("# mine\n-Xmx1g\n-Dfoo=bar\n -Xmx2g\n", 2048, 2048)).isEqualTo("# mine\n-Dfoo=bar\n");
        assertThat(VmOptionsFile.withMaxHeap("", 2048, 2048)).isEmpty();
    }

    @Test void windowsLineEndingsStay() {
        assertThat(VmOptionsFile.withMaxHeap("# mine\r\n-Xmx1g\r\n", 3072, 2048)).isEqualTo("# mine\r\n-Xmx3072m\r\n");
    }

    @Test void aLeadingByteOrderMarkIsDroppedSoTheHeapLineIsStillFound() {
        assertThat(VmOptionsFile.withMaxHeap("\uFEFF-Xmx1g\n", 2048, 2048)).isEqualTo("");
        assertThat(VmOptionsFile.withMaxHeap("\uFEFF# mine\n-Xmx1g\n", 4096, 2048)).isEqualTo("# mine\n-Xmx4096m\n");
        assertThat(VmOptionsFile.withMaxHeap("\uFEFF-Xmx1g\n", 4096, 2048)).isEqualTo("-Xmx4096m\n");
    }

    @Test void ensureExistsCreatesTheHeaderOnceAndWriteLeavesNoTemporaryFile() throws IOException {
        Path file = home.resolve("nested/jasper.vmoptions");
        VmOptionsFile.ensureExists(file);
        assertThat(Files.readString(file)).isEqualTo(VmOptionsFile.HEADER + "\n\n");
        Files.writeString(file, "-Xmx1g\n");
        VmOptionsFile.ensureExists(file);
        assertThat(Files.readString(file)).isEqualTo("-Xmx1g\n");
        VmOptionsFile.setMaxHeap(file, 8192, 2048);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("-Xmx8192m\n");
        try (Stream<Path> entries = Files.list(file.getParent())) {
            assertThat(entries.map(path -> path.getFileName().toString())).containsExactly("jasper.vmoptions");
        }
    }
}
