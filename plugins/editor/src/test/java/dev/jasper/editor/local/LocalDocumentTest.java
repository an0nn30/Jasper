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
