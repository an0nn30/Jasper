package dev.jasper.editor.local;

import dev.jasper.editor.api.TooLargeException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

class LocalDocumentTest {
    @TempDir Path folder;

    @Test void resolvesAgainstItsParentAndKeepsTheBackgroundExecutor() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        Path source = folder.resolve("docs/readme.md");
        Path image = Files.writeString(folder.resolve("a+b caf\u00e9%20.txt"), "resource");
        var document = new LocalDocument(source, queued::add);
        var relative = document.resolveRelative("../a+b caf\u00e9%20.txt").orElseThrow();
        assertThat(relative.key()).isEqualTo(LocalDocument.key(image));
        var read = relative.read(100);
        assertThat(read).isNotDone();
        assertThat(queued).hasSize(1);
        queued.removeFirst().run();
        assertThat(new String(read.get().bytes(), UTF_8)).isEqualTo("resource");
        assertThat(document.resolveRelative("../../../../../../../../../../root.txt").orElseThrow().key())
            .isEqualTo(LocalDocument.key(source.getRoot().resolve("root.txt")));
    }

    @Test void rejectsMalformedRelativePathsBeforeQueuingWork() {
        List<Runnable> queued = new ArrayList<>();
        var document = new LocalDocument(folder.resolve("readme.md"), queued::add);
        for (String path : List.of("", "/root", "//host/file", "C:/file", "C:file", "file:a", "a\\b", "a?b", "a#b",
                "a\u0000b", "a\u001Fb", "a\u007Fb", "a\u0085b", "a\uD800b"))
            assertThatIllegalArgumentException().as(path).isThrownBy(() -> document.resolveRelative(path));
        assertThatIllegalArgumentException().isThrownBy(() -> document.resolveRelative(null));
        assertThat(queued).isEmpty();
    }

    @Test void describesItselfAndDoesItsIoOnTheBackgroundExecutor() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        Path file = Files.writeString(folder.resolve("app.py"), "print(1)\n");
        var document = new LocalDocument(file, queued::add);
        assertThat(document.key()).isEqualTo("local:" + file.toAbsolutePath().normalize()).isEqualTo(LocalDocument.key(file));
        assertThat(document.name()).isEqualTo("app.py");
        assertThat(document.location()).isEqualTo(file.toAbsolutePath().normalize().toString());
        assertThat(document.host()).isEmpty();
        var read = document.read(1024);
        assertThat(read).as("nothing runs on the caller's thread").isNotDone();
        queued.removeFirst().run();
        assertThat(new String(read.get().bytes(), UTF_8)).isEqualTo("print(1)\n");
        var written = document.write("print(2)\n".getBytes(UTF_8));
        queued.removeFirst().run();
        var revision = document.revision();
        queued.removeFirst().run();
        assertThat(revision.get()).contains(written.get());
        var tooBig = document.read(1);
        queued.removeFirst().run();
        assertThatThrownBy(tooBig::get).hasCauseInstanceOf(TooLargeException.class);
        var cancelled = document.read(1024);
        cancelled.cancel(true);
        queued.removeFirst().run();
        assertThat(cancelled).isCancelled();
    }
}
