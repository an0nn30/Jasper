package dev.jasper.editor.session;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.syntax.Languages;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;

class DocumentSessionTest {
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<Question> asked = new ArrayList<>();
    private final SessionEnvironment environment = new SessionEnvironment(Runnable::run, Runnable::run,
        question -> { asked.add(question); return answers.isEmpty() ? question.dismissed() : answers.removeFirst(); }, () -> 1024);
    private final StringBuilder buffer = new StringBuilder();
    private final List<Boolean> keeps = new ArrayList<>();
    private int changes;

    private DocumentSession attach(DocumentSession session) {
        session.attach(buffer::toString, (text, keep) -> { buffer.setLength(0); buffer.append(text); keeps.add(keep); });
        session.addListener(() -> changes++);
        return session;
    }

    private DocumentSession open(FakeDocument document) {
        DocumentSession session = attach(new DocumentSession(document, environment));
        session.load();
        return session;
    }

    private void type(DocumentSession session, String text) {
        buffer.setLength(0);
        buffer.append(text);
        session.textEdited();
    }

    @Test void loadsDecodesDetectsAndIsCleanUntilEdited() {
        var document = new FakeDocument("config.toml", "prod-web").text("name = \"x\"\n");
        var session = open(document);
        assertThat(session.state()).isEqualTo(DocumentSession.State.READY);
        assertThat(buffer).hasToString("name = \"x\"\n");
        assertThat(keeps).containsExactly(false);
        assertThat(session.style()).isEqualTo(Languages.TOML);
        assertThat(session.format().label()).isEqualTo("UTF-8");
        assertThat(session.revision()).contains(document.now());
        assertThat(session.dirty()).isFalse();
        int before = changes;
        type(session, "name = \"y\"\n");
        assertThat(session.dirty()).isTrue();
        assertThat(changes).as("listeners hear the change").isGreaterThan(before);
        type(session, "name = \"x\"\n");
        assertThat(session.dirty()).as("undoing back to the saved text clears it").isFalse();
    }

    @Test void aLoadFailureIsShownWithRetryUnlessTheFileIsBinary() {
        var document = new FakeDocument("config.toml", null);
        document.readFailure = new AccessDeniedException(document.location);
        var session = open(document);
        assertThat(session.state()).isEqualTo(DocumentSession.State.FAILED);
        assertThat(session.failure()).isEqualTo("Permission denied: config.toml.");
        assertThat(session.retryable()).isTrue();
        document.readFailure = null;
        session.load();
        assertThat(session.state()).isEqualTo(DocumentSession.State.READY);
        var binary = new FakeDocument("image.png", null);
        binary.bytes = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0};
        var refused = open(binary);
        assertThat(refused.failure()).isEqualTo("image.png looks like a binary file.");
        assertThat(refused.retryable()).isFalse();
        var large = new FakeDocument("big.log", null);
        large.bytes = new byte[4096];
        assertThat(open(large).failure()).isEqualTo("big.log is 4.0 KiB; the limit is 1.0 KiB (max_file_mib).");
    }

    @Test void cancellingALoadIgnoresItsLateResult() {
        var document = new FakeDocument("app.py", null).text("print(1)\n");
        document.holdReads = true;
        var session = open(document);
        assertThat(session.state()).isEqualTo(DocumentSession.State.LOADING);
        session.cancelLoad();
        document.heldRead.complete(new Content("late".getBytes(StandardCharsets.UTF_8), document.now()));
        assertThat(session.state()).isEqualTo(DocumentSession.State.LOADING);
        assertThat(buffer).isEmpty();
    }

    @Test void savingWritesTheSnapshotAndAdoptsTheNewRevision() {
        var document = new FakeDocument("config.toml", "prod-web").text("a = 1\n");
        var session = open(document);
        type(session, "a = 2\n");
        var saved = session.save();
        assertThat(saved).isCompletedWithValue(true);
        assertThat(document.text()).isEqualTo("a = 2\n");
        assertThat(session.revision()).contains(document.now());
        assertThat(session.dirty()).isFalse();
        assertThat(session.saving()).isFalse();
        assertThat(asked).isEmpty();
    }

    @Test void typingDuringASaveKeepsTheTabDirtyAndASecondSaveJoinsTheFirst() {
        var document = new FakeDocument("config.toml", null).text("a = 1\n");
        var session = open(document);
        type(session, "a = 2\n");
        document.holdWrites = true;
        var first = session.save();
        assertThat(session.saving()).isTrue();
        assertThat(session.save()).isSameAs(first);
        assertThat(session.pendingSave()).contains(first);
        type(session, "a = 3\n");
        document.heldWrite.complete(new Revision(9_000, 6));
        assertThat(first).isCompletedWithValue(true);
        assertThat(session.dirty()).as("edits after the snapshot are still unsaved").isTrue();
        type(session, "a = 2\n");
        assertThat(session.dirty()).as("the snapshot became the saved text").isFalse();
    }

    @Test void aChangedFileAsksOverwriteReloadOrCancel() {
        var document = new FakeDocument("config.toml", "prod-web").text("a = 1\n");
        var session = open(document);
        type(session, "mine\n");
        document.change("theirs\n");
        answers.add("Cancel");
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(asked.getLast()).isEqualTo(new Question("File changed", "config.toml changed on prod-web since you opened it.",
            List.of(DocumentSession.OVERWRITE, DocumentSession.RELOAD, "Cancel")));
        assertThat(document.text()).isEqualTo("theirs\n");
        assertThat(session.dirty()).isTrue();
        answers.add(DocumentSession.OVERWRITE);
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(document.text()).isEqualTo("mine\n");
        document.change("theirs again\n");
        type(session, "mine again\n");
        answers.add(DocumentSession.RELOAD);
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(buffer).hasToString("theirs again\n");
        assertThat(keeps.getLast()).as("a reload keeps the caret's place").isTrue();
        assertThat(session.dirty()).isFalse();
    }

    @Test void aDeletedFileAsksSaveAnyway() {
        var document = new FakeDocument("config.toml", "prod-web").text("a = 1\n");
        var session = open(document);
        type(session, "a = 2\n");
        document.exists = false;
        answers.add("Cancel");
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(asked.getLast().message()).isEqualTo("config.toml no longer exists on prod-web.");
        answers.add(DocumentSession.SAVE_ANYWAY);
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(document.exists).isTrue();
        assertThat(document.text()).isEqualTo("a = 2\n");
    }

    @Test void aFailedWriteLeavesTheTabDirtyAndTheNextSaveRetries() {
        var document = new FakeDocument("config.toml", null).text("a = 1\n");
        var session = open(document);
        type(session, "a = 2\n");
        document.writeFailure = new AccessDeniedException(document.location);
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(asked.getLast()).isEqualTo(new Question("Save failed", "Could not save config.toml: permission denied.", List.of("OK")));
        assertThat(document.text()).isEqualTo("a = 1\n");
        assertThat(session.dirty()).isTrue();
        document.writeFailure = null;
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(document.text()).isEqualTo("a = 2\n");
    }

    @Test void textTheCharsetCannotHoldOffersUtf8() {
        var document = new FakeDocument("notes.txt", null);
        document.bytes = new byte[] {'c', 'a', 'f', (byte) 0xE9, '\n'};
        var session = open(document);
        assertThat(session.format().label()).isEqualTo("ISO-8859-1");
        type(session, "café \uD83D\uDE00\n");
        answers.add("Cancel");
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(asked.getLast().message()).isEqualTo("Line 1 of notes.txt has a character that ISO-8859-1 cannot store.");
        assertThat(document.writes).isZero();
        answers.add(DocumentSession.CONVERT);
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(document.text()).isEqualTo("café \uD83D\uDE00\n");
        assertThat(session.format().label()).isEqualTo("UTF-8");
    }

    @Test void outsideChangesReloadACleanTabAndBannerADirtyOne() {
        var document = new FakeDocument("app.py", null).text("a\n");
        var session = open(document);
        document.change("b\n");
        session.externalChange(Optional.of(document.now()));
        assertThat(buffer).hasToString("b\n");
        assertThat(session.notice()).contains("Reloaded: changed on disk");
        session.clearNotice();
        assertThat(session.notice()).isEmpty();
        type(session, "mine\n");
        document.change("c\n");
        session.externalChange(Optional.of(document.now()));
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.CHANGED);
        assertThat(buffer).hasToString("mine\n");
        session.externalChange(Optional.of(document.now()));
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.CHANGED);
        session.keepMine();
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.NONE);
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(asked).as("Keep Mine adopted the new revision").isEmpty();
        assertThat(document.text()).isEqualTo("mine\n");
    }

    @Test void aDeletedFileShowsItsBannerAndKeepRecreatesItOnSave() {
        var document = new FakeDocument("app.py", null).text("a\n");
        var session = open(document);
        document.exists = false;
        session.externalChange(Optional.empty());
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.DELETED);
        session.keepDeleted();
        assertThat(session.dirty()).as("the tab stays unsaved").isTrue();
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(asked).isEmpty();
        assertThat(document.exists).isTrue();
    }

    @Test void ownSavesAreNotOutsideChanges() {
        var document = new FakeDocument("app.py", null).text("a\n");
        var session = open(document);
        type(session, "b\n");
        session.save();
        int reads = document.reads;
        session.externalChange(Optional.of(document.now()));
        assertThat(document.reads).isEqualTo(reads);
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.NONE);
    }

    @Test void revertAsksBeforeDiscardingEditsAndAFailedReloadKeepsTheText() {
        var document = new FakeDocument("app.py", null).text("saved\n");
        var session = open(document);
        type(session, "mine\n");
        answers.add("Cancel");
        session.revert();
        assertThat(buffer).hasToString("mine\n");
        answers.add(DocumentSession.DISCARD);
        session.revert();
        assertThat(buffer).hasToString("saved\n");
        type(session, "mine\n");
        document.readFailure = new NoSuchFileException(document.location);
        answers.add(DocumentSession.DISCARD);
        session.revert();
        assertThat(asked.getLast().title()).isEqualTo("Reload failed");
        assertThat(buffer).hasToString("mine\n");
        assertThat(session.state()).isEqualTo(DocumentSession.State.READY);
    }

    @Test void saveAsWritesToTheTargetAndAdoptsIt() {
        var session = attach(DocumentSession.untitled("Untitled", environment));
        assertThat(session.untitled()).isTrue();
        assertThat(session.key()).isEqualTo("untitled:Untitled");
        type(session, "print(1)\n");
        assertThat(session.dirty()).isTrue();
        var target = new FakeDocument("app.py", null);
        target.exists = false;
        assertThat(session.saveAs(target)).isCompletedWithValue(true);
        assertThat(target.text()).isEqualTo("print(1)\n");
        assertThat(session.name()).isEqualTo("app.py");
        assertThat(session.key()).isEqualTo(target.key());
        assertThat(session.style()).isEqualTo(SYNTAX_STYLE_PYTHON);
        assertThat(session.dirty()).isFalse();
        assertThat(asked).isEmpty();
    }
}
