package dev.jasper.editor.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN;

import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.vim.KeyInput;
import dev.jasper.editor.vim.Vim;
import dev.jasper.editor.vim.VimHost;
import dev.jasper.sdk.testing.FakeKit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pane flexibility spec 6: several views of one document, each an {@link EditorTab}, show one text with one history. */
@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class TextViewsTest {
    @TempDir Path folder;
    private final List<Runnable> background = new ArrayList<>();
    private final List<Question> asked = new ArrayList<>();
    private final SessionEnvironment environment = new SessionEnvironment(background::add, Runnable::run,
        question -> { asked.add(question); return question.choices().getFirst(); }, () -> 1 << 20);
    private final List<EditorTab> views = new ArrayList<>();

    @BeforeEach void languages() { Languages.register(); }

    @AfterEach void close() { views.forEach(EditorTab::dispose); }

    private void drain() { while (!background.isEmpty()) background.removeFirst().run(); }

    private EditorTab first(DocumentSession session) {
        var view = new EditorTab(new FakeKit("dev.jasper.editor"), session, closed -> { }, false);
        views.add(view);
        return view;
    }

    private EditorTab first(Path file) {
        var view = first(new DocumentSession(new LocalDocument(file, background::add), environment));
        view.session().load();
        drain();
        return view;
    }

    private EditorTab second(EditorTab first) {
        var view = new EditorTab(new FakeKit("dev.jasper.editor"), first.session(), closed -> { }, false, first);
        views.add(view);
        return view;
    }

    @Test void aSecondViewShowsTheSameTextAndEveryEditAtOnce() throws Exception {
        var one = first(Files.writeString(folder.resolve("a.txt"), "alpha\n"));
        var two = second(one);
        assertThat(two.area().getDocument()).isSameAs(one.area().getDocument());
        assertThat(two.area().getText()).isEqualTo("alpha\n");
        two.area().setCaretPosition(5);
        two.area().replaceSelection(" beta");
        assertThat(one.area().getText()).isEqualTo("alpha beta\n");
        assertThat(one.session().dirty()).isTrue();
        one.area().undoLastAction();
        assertThat(two.area().getText()).as("one history: an undo in either view undoes the last edit").isEqualTo("alpha\n");
        assertThat(two.session().dirty()).isFalse();
    }

    /** Revert to Saved from one view replaces the text once; each view keeps its caret's line, or the last when it is gone. */
    @Test void aRevertReplacesTheTextOnceAndEachViewKeepsItsCaretLine() throws Exception {
        Path file = Files.writeString(folder.resolve("lines.txt"), "1\n2\n3\n4\n5\n");
        var one = first(file);
        var two = second(one);
        one.area().setCaretPosition(one.area().getLineStartOffset(1));
        two.area().setCaretPosition(two.area().getLineStartOffset(4));
        two.area().replaceSelection("x");
        Files.writeString(file, "a\nb\n");
        two.session().revert();
        drain();
        assertThat(asked).extracting(Question::title).as("asked once, about the document").containsExactly("Revert to Saved");
        assertThat(one.area().getText()).isEqualTo("a\nb\n");
        assertThat(one.area().getCaretLineNumber()).isEqualTo(1);
        assertThat(two.area().getCaretLineNumber()).as("past the new end: the last line").isEqualTo(2);
        assertThat(one.area().canUndo() || two.area().canUndo()).as("the one history starts again").isFalse();
        assertThat(one.session().dirty()).isFalse();
    }

    /** Closing a document's first view leaves the other its text, its history and the session's reads. */
    @Test void closingTheFirstViewLeavesTheOtherTheTextAndItsHistory() throws Exception {
        Path file = Files.writeString(folder.resolve("keep.txt"), "keep\n");
        var one = first(file);
        var two = second(one);
        one.area().setCaretPosition(4);
        one.area().replaceSelection("!");
        var shared = one.area().getDocument();
        one.dispose();
        assertThat(one.area().getDocument()).as("the closed view lets go of the document").isNotSameAs(shared);
        assertThat(two.area().getDocument()).isSameAs(shared);
        assertThat(two.session().dirty()).as("the session reads the text of the view left").isTrue();
        two.area().undoLastAction();
        assertThat(two.area().getText()).isEqualTo("keep\n");
        Files.writeString(file, "changed\n");
        two.session().reload();
        drain();
        assertThat(two.area().getText()).as("a reload reaches the view left").isEqualTo("changed\n");
    }

    /** Cancel in one view of a file that is still loading closes that view alone: the read goes on for the other. */
    @Test void cancelInOneViewOfALoadingFileLeavesTheOtherTheRead() throws Exception {
        Path file = Files.writeString(folder.resolve("slow.txt"), "slow\n");
        var closing = new ArrayList<EditorTab>();
        var one = new EditorTab(new FakeKit("dev.jasper.editor"), new DocumentSession(new LocalDocument(file, background::add), environment),
            closing::add, false);
        views.add(one);
        one.session().load();
        var two = second(one);
        dev.jasper.editor.testsupport.Kits.click(one.view(), EditorTab.DISMISS);
        assertThat(closing).as("the view asks to close").containsExactly(one);
        one.dispose();
        drain();
        assertThat(two.session().state()).isEqualTo(DocumentSession.State.READY);
        assertThat(two.area().getText()).isEqualTo("slow\n");
    }

    /** A closed view leaves nothing behind on its file's session, which outlives it while other views show the file. */
    @Test void aClosedViewLeavesNoListenerOnTheSession() throws Exception {
        var one = first(Files.writeString(folder.resolve("listeners.txt"), "x\n"));
        int baseline = listeners(one.session());
        for (int n = 0; n < 5; n++) new EditorTab(new FakeKit("dev.jasper.editor"), one.session(), closed -> { }, false, one).dispose();
        assertThat(listeners(one.session())).isEqualTo(baseline);
    }

    private static int listeners(DocumentSession session) throws ReflectiveOperationException {
        var field = DocumentSession.class.getDeclaredField("listeners");
        field.setAccessible(true);
        return ((List<?>) field.get(session)).size();
    }

    /** Each view has its own Vim state: Vim in one view and not in the other, whose undo takes Vim's change. */
    @Test void vimInOneViewAndNotInTheOther() throws Exception {
        var one = first(Files.writeString(folder.resolve("vim.txt"), "abc\n"));
        var two = second(one);
        one.vim(Optional.of(new Vim(VimHost.detached())));
        assertThat(one.vimEditor()).isPresent();
        assertThat(two.vimEditor()).as("Vim in the first view only").isEmpty();
        one.area().setCaretPosition(0);
        for (KeyInput key : KeyInput.parse("x")) one.vimEditor().orElseThrow().feed(key);
        assertThat(two.area().getText()).isEqualTo("bc\n");
        two.area().undoLastAction();
        assertThat(one.area().getText()).as("the other view's undo takes Vim's change").isEqualTo("abc\n");
        for (KeyInput key : KeyInput.parse("u")) one.vimEditor().orElseThrow().feed(key);
        assertThat(one.vimEditor().orElseThrow().message()).as("nothing older to undo").isEqualTo("Already at oldest change");
    }

    /** Vim in a second view edits the shared text and groups that view's edits as Vim does: the view shares, then Vim turns on. */
    @Test void aSecondViewsVimGroupsItsOwnEdits() throws Exception {
        var one = first(Files.writeString(folder.resolve("vim-second.txt"), "abc\n"));
        var two = second(one);
        two.vim(Optional.of(new Vim(VimHost.detached())));
        two.area().setCaretPosition(0);
        for (KeyInput key : KeyInput.parse("x")) two.vimEditor().orElseThrow().feed(key);
        assertThat(one.area().getText()).as("the second view's Vim edits the shared text").isEqualTo("bc\n");
        two.area().setCaretPosition(2);
        two.area().replaceSelection("d");
        two.area().replaceSelection("e");
        for (KeyInput key : KeyInput.parse("u")) two.vimEditor().orElseThrow().feed(key);
        assertThat(one.area().getText()).as("with Vim on, an edit outside a Vim change is a step of its own").isEqualTo("bcd\n");
    }

    /** Each view has its own Markdown mode: the preview in one and the source in the other, each switched on its own. */
    @Test void aMarkdownPreviewInOneViewAndTheSourceInTheOther() {
        var one = first(DocumentSession.untitled("Untitled", environment));
        var two = second(one);
        for (EditorTab view : List.of(one, two)) view.previews(background::add, Runnable::run);
        one.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        two.area().setText("# One\n");
        one.markdownMode(MarkdownMode.PREVIEW, false);
        assertThat(one.markdown().mode()).isEqualTo(MarkdownMode.PREVIEW);
        assertThat(two.markdown().mode()).as("each view its own mode").isEqualTo(MarkdownMode.SOURCE);
        assertThat(two.focusedContent()).as("the other view keeps its source").isSameAs(two.area());
        two.markdownMode(MarkdownMode.SPLIT, false);
        assertThat(one.markdown().mode()).isEqualTo(MarkdownMode.PREVIEW);
        assertThat(two.markdown().mode()).isEqualTo(MarkdownMode.SPLIT);
    }
}
