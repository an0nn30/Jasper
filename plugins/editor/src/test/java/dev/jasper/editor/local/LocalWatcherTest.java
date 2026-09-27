package dev.jasper.editor.local;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class LocalWatcherTest {
    @TempDir Path folder;
    private final List<Runnable> background = new ArrayList<>();
    private final SessionEnvironment environment = new SessionEnvironment(background::add, Runnable::run, Question::dismissed, () -> 1 << 20);

    private void drain() { while (!background.isEmpty()) background.removeFirst().run(); }

    private DocumentSession open(Path file, StringBuilder text) {
        var session = new DocumentSession(new LocalDocument(file, background::add), environment);
        session.attach(text::toString, (value, keep) -> { text.setLength(0); text.append(value); });
        session.load();
        return session;
    }

    @Test void aCleanTabReloadsADirtyOneShowsTheBannerAndDeletionIsReported() throws Exception {
        Path clean = Files.writeString(folder.resolve("clean.yaml"), "a: 1\n");
        Path dirty = Files.writeString(folder.resolve("dirty.yaml"), "b: 1\n");
        StringBuilder cleanText = new StringBuilder(), dirtyText = new StringBuilder();
        var cleanSession = open(clean, cleanText);
        var dirtySession = open(dirty, dirtyText);
        drain();
        dirtyText.setLength(0);
        dirtyText.append("b: mine\n");
        dirtySession.textEdited();
        var watcher = new LocalWatcher(background::add, Runnable::run, () -> List.of(cleanSession, dirtySession));
        watcher.tick();
        drain();
        assertThat(cleanSession.banner()).isEqualTo(DocumentSession.Banner.NONE);
        assertThat(cleanSession.notice()).as("nothing changed").isEmpty();
        Files.writeString(clean, "a: 22\n");
        Files.writeString(dirty, "b: 22\n");
        watcher.tick();
        drain();
        assertThat(cleanText).hasToString("a: 22\n");
        assertThat(cleanSession.notice()).contains("Reloaded: changed on disk");
        assertThat(dirtySession.banner()).isEqualTo(DocumentSession.Banner.CHANGED);
        assertThat(dirtyText).hasToString("b: mine\n");
        Files.delete(clean);
        watcher.tick();
        drain();
        assertThat(cleanSession.banner()).isEqualTo(DocumentSession.Banner.DELETED);
    }

    @Test void aTickWhileTheLastIsRunningAndTabsWithoutALocalFileAreSkipped() throws Exception {
        Path file = Files.writeString(folder.resolve("a.toml"), "a = 1\n");
        var session = open(file, new StringBuilder());
        drain();
        var untitled = DocumentSession.untitled("Untitled", environment);
        var watcher = new LocalWatcher(background::add, Runnable::run, () -> List.of(session, untitled));
        watcher.tick();
        watcher.tick();
        assertThat(background).as("one stat pass at a time").hasSize(1);
        drain();
        var none = new LocalWatcher(background::add, Runnable::run, () -> List.of(untitled));
        none.tick();
        assertThat(background).isEmpty();
    }
}
