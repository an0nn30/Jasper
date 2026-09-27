package dev.jasper.editor.session;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EditorModelTest {
    private final SessionEnvironment environment = new SessionEnvironment(Runnable::run, Runnable::run, Question::dismissed, () -> 1024);

    @Test void detailsShowTheHostAndTheFolderWhenNamesClash() {
        var model = new EditorModel();
        var local = new DocumentSession(new FakeDocument("config.toml", null), environment);
        var remote = new DocumentSession(new FakeDocument("config.toml", "prod-web"), environment);
        var other = new DocumentSession(new FakeDocument("app.py", "prod-web"), environment);
        model.add(local);
        assertThat(model.detail(local)).isEmpty();
        model.add(remote);
        model.add(other);
        assertThat(model.detail(local)).isEqualTo("app");
        assertThat(model.detail(remote)).isEqualTo("app · prod-web");
        assertThat(model.detail(other)).isEqualTo("prod-web");
        assertThat(model.find(remote.key())).containsSame(remote);
        assertThat(model.indexOf(other)).isEqualTo(2);
        model.remove(remote);
        assertThat(model.sessions()).containsExactly(local, other);
    }

    @Test void untitledNamesReuseTheLowestFreeNumber() {
        var model = new EditorModel();
        var first = DocumentSession.untitled(model.nextUntitledName(), environment);
        model.add(first);
        var second = DocumentSession.untitled(model.nextUntitledName(), environment);
        model.add(second);
        assertThat(List.of(first.name(), second.name())).containsExactly("Untitled", "Untitled 2");
        model.remove(first);
        assertThat(model.nextUntitledName()).isEqualTo("Untitled");
    }

    @Test void unsavedListsDirtyAndSavingSessions() {
        var model = new EditorModel();
        var session = DocumentSession.untitled("Untitled", environment);
        StringBuilder text = new StringBuilder();
        session.attach(text::toString, (value, keep) -> { });
        model.add(session);
        assertThat(model.unsaved()).isEmpty();
        text.append("x");
        assertThat(model.unsaved()).containsExactly(session);
    }
}
