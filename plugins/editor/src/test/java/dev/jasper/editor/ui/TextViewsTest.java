package dev.jasper.editor.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        var changes = new ArrayList<String>();
        one.area().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changes.add("insert"); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changes.add("remove"); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { }
        });
        two.session().revert();
        drain();
        assertThat(changes).as("the text replaced once for both views").containsExactly("remove", "insert");
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

    /** Once a loading file's other view has closed, Cancel in the view left stops the read, as one view's Cancel does. */
    @Test void cancelInTheLastViewOfALoadingFileStopsTheRead() throws Exception {
        Path file = Files.writeString(folder.resolve("stop.txt"), "stop\n");
        var closing = new ArrayList<EditorTab>();
        var one = new EditorTab(new FakeKit("dev.jasper.editor"), new DocumentSession(new LocalDocument(file, background::add), environment),
            closing::add, false);
        views.add(one);
        one.session().load();
        second(one).dispose();
        dev.jasper.editor.testsupport.Kits.click(one.view(), EditorTab.DISMISS);
        assertThat(closing).containsExactly(one);
        drain();
        assertThat(one.session().state()).as("the read stopped").isNotEqualTo(DocumentSession.State.READY);
        assertThat(one.area().getText()).isEmpty();
    }

    /** A view is made from an open view of its file: one that has closed is refused, loudly. */
    @Test void aViewIsNotMadeFromAClosedView() throws Exception {
        var one = first(Files.writeString(folder.resolve("closed.txt"), "x\n"));
        var two = second(one);
        one.dispose();
        assertThatThrownBy(() -> new EditorTab(new FakeKit("dev.jasper.editor"), two.session(), closed -> { }, false, one))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** Vim turning on in one view leaves the other view's undo as it was: its neighbouring typing is still one step. */
    @Test void vimTurningOnInOneViewLeavesTheOthersUndoGrouping() throws Exception {
        var one = first(Files.writeString(folder.resolve("group.txt"), "x\n"));
        var two = second(one);
        one.vim(Optional.of(new Vim(VimHost.detached())));
        two.area().setCaretPosition(1);
        two.area().replaceSelection("a");
        two.area().replaceSelection("b");
        two.area().undoLastAction();
        assertThat(one.area().getText()).as("one step, as RSyntaxTextArea groups typing").isEqualTo("x\n");
    }

    /** Lays a view out at a size, as its pane would, so that its text area maps offsets to places. */
    private static void laidOut(EditorTab view) {
        view.surface().setSize(400, 300);
        view.surface().doLayout();
        view.scroll().doLayout();
        view.scroll().getViewport().doLayout();
    }

    /**
     * Vim on in both views of a file, an edit in one below the other's cursor, then the other closes: nothing throws.
     * Its text area lets go of the document while its view's geometry is stale from the edit.
     */
    @Test void closingAViewAfterAVimEditInTheOtherThrowsNothing() throws Exception {
        var one = first(Files.writeString(folder.resolve("both.txt"), "abc\ndef\nghi\n"));
        var two = second(one);
        laidOut(one);
        laidOut(two);
        var vim = new Vim(VimHost.detached());
        one.vim(Optional.of(vim));
        two.vim(Optional.of(vim));
        type(one, "j");
        type(two, "Gox<Esc>");
        one.dispose();
        assertThat(two.area().getText()).isEqualTo("abc\ndef\nghi\nx\n");
        assertThat(one.area().getText()).isEmpty();
    }

    /** The view that edited closes instead, after its own Vim edit: nothing throws, and the other keeps the edit. */
    @Test void closingTheViewThatMadeAVimEditThrowsNothing() throws Exception {
        var one = first(Files.writeString(folder.resolve("editing.txt"), "abc\ndef\nghi\n"));
        var two = second(one);
        laidOut(one);
        laidOut(two);
        var vim = new Vim(VimHost.detached());
        one.vim(Optional.of(vim));
        two.vim(Optional.of(vim));
        type(one, "j");
        type(two, "Gox<Esc>");
        two.dispose();
        assertThat(one.area().getText()).isEqualTo("abc\ndef\nghi\nx\n");
    }

    /** Vim on in the editing view only: the other view, its caret on a later line, closes after the edit, quietly. */
    @Test void closingAViewWithoutVimAfterAVimEditInTheOtherThrowsNothing() throws Exception {
        var one = first(Files.writeString(folder.resolve("one-vim.txt"), "abc\ndef\nghi\n"));
        var two = second(one);
        laidOut(one);
        laidOut(two);
        two.vim(Optional.of(new Vim(VimHost.detached())));
        one.area().setCaretPosition(5);
        type(two, "Gox<Esc>");
        one.dispose();
        assertThat(two.area().getText()).isEqualTo("abc\ndef\nghi\nx\n");
    }

    /** A view showing its Markdown preview closes after the other view's edit, quietly. */
    @Test void closingAViewInPreviewAfterAnEditInTheOtherThrowsNothing() throws Exception {
        var one = first(DocumentSession.untitled("Untitled", environment));
        var two = second(one);
        for (EditorTab view : List.of(one, two)) view.previews(background::add, Runnable::run);
        one.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        two.area().setText("# One\n\ntext\n");
        laidOut(one);
        laidOut(two);
        one.area().setCaretPosition(7);
        one.markdownMode(MarkdownMode.PREVIEW, false);
        two.area().setCaretPosition(two.area().getDocument().getLength());
        two.area().replaceSelection("more\n");
        one.dispose();
        assertThat(two.area().getText()).isEqualTo("# One\n\ntext\nmore\n");
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

    private static void type(EditorTab view, String keys) {
        for (KeyInput key : KeyInput.parse(keys)) view.vimEditor().orElseThrow().feed(key);
    }

    /**
     * Vim's buffer marks belong to the file, as Vim keeps them per buffer: a mark set in one view is the other's too, and
     * {@code '.} and the change list follow an edit made in either view; the jumplist stays each view's own.
     */
    @Test void lowercaseMarksAndTheLastChangeBelongToTheFile() throws Exception {
        var one = first(Files.writeString(folder.resolve("marks.txt"), "one\ntwo\nthree\nfour\n"));
        var two = second(one);
        var vim = new Vim(VimHost.detached());
        one.vim(Optional.of(vim));
        two.vim(Optional.of(vim));
        type(one, "jma");
        type(two, "gg'a");
        assertThat(two.area().getCaretLineNumber()).as("the other view's mark a").isEqualTo(1);
        type(one, "jjx");
        type(two, "gg'.");
        assertThat(two.area().getCaretLineNumber()).as("the last change, made in the other view").isEqualTo(3);
        type(two, "ggg;");
        assertThat(two.area().getCaretLineNumber()).as("the change list, from another line").isEqualTo(3);
        type(one, "gg");
        type(one, "dd");
        type(two, "gg'a");
        assertThat(two.area().getCaretLineNumber()).as("the mark moved with the other view's deletion").isZero();
        assertThat(two.vimEditor().orElseThrow().message()).isEmpty();
        type(one, ":jumps<CR>");
        type(two, ":jumps<CR>");
        assertThat(one.vimEditor().orElseThrow().output()).as("one jump, its gg, between the heading and the end").hasSize(3);
        assertThat(two.vimEditor().orElseThrow().output()).as("the other view's jumps are its own").hasSizeGreaterThan(3);
    }

    /** Two views of a file with Vim on, the mark A set in the second on line 3. */
    private List<EditorTab> markedViews(String name) throws Exception {
        var one = first(Files.writeString(folder.resolve(name), "1\n2\n3\n4\n5\n6\n7\n8\n"));
        var two = second(one);
        var vim = new Vim(VimHost.detached());
        one.vim(Optional.of(vim));
        two.vim(Optional.of(vim));
        type(two, "3GmA");
        return List.of(one, two);
    }

    /** An uppercase mark set in another view of the file is a mark in this file, as Vim's getmark_buf_fnum reads one: an operator reaches it. */
    @Test void anOperatorReachesAnUppercaseMarkSetInAnotherViewOfTheFile() throws Exception {
        var views = markedViews("operator.txt");
        type(views.getFirst(), "6Gd'A");
        assertThat(views.getFirst().area().getText()).isEqualTo("1\n2\n7\n8\n");
    }

    @Test void visualModeReachesAnUppercaseMarkSetInAnotherViewOfTheFile() throws Exception {
        var views = markedViews("visual.txt");
        type(views.getFirst(), "6GV'Ad");
        assertThat(views.getFirst().area().getText()).isEqualTo("1\n2\n7\n8\n");
    }

    /** {@code 'A} to a mark of the same file moves this view's cursor, as in Vim's current window; the view it was set in stays. */
    @Test void anUppercaseMarkOfTheSameFileMovesOnlyThisViewsCursor() throws Exception {
        var views = markedViews("jump.txt");
        type(views.get(1), "8G");
        type(views.getFirst(), "gg'A");
        assertThat(views.getFirst().area().getCaretLineNumber()).as("this view goes to the mark").isEqualTo(2);
        assertThat(views.get(1).area().getCaretLineNumber()).as("the view it was set in stays put").isEqualTo(7);
    }

    /** A view whose Vim starts after changes starts at the end of the file's change list, after the last change. */
    @Test void aNewViewStartsAtTheEndOfTheChangeList() throws Exception {
        var one = first(Files.writeString(folder.resolve("start.txt"), "a\nb\nc\nd\ne\n"));
        var vim = new Vim(VimHost.detached());
        one.vim(Optional.of(vim));
        type(one, "xjjxjjx");
        var two = second(one);
        two.vim(Optional.of(vim));
        type(two, "ggg;");
        assertThat(two.area().getCaretLineNumber()).as("the last change").isEqualTo(4);
        assertThat(two.vimEditor().orElseThrow().message()).isEmpty();
    }

    /**
     * Each view keeps its own place in the file's change list, as each Vim window does (changed_common): an edit moves the
     * view that made it to the end, and another view stays where it was, or at the end when it was there.
     */
    @Test void eachViewKeepsItsPlaceInTheChangeList() throws Exception {
        var one = first(Files.writeString(folder.resolve("places.txt"), "a\nb\nc\nd\ne\nf\n"));
        var two = second(one);
        var vim = new Vim(VimHost.detached());
        one.vim(Optional.of(vim));
        two.vim(Optional.of(vim));
        type(one, "xjjxjjx");
        type(two, "g;");
        assertThat(two.area().getCaretLineNumber()).as("at the end through the other view's changes").isEqualTo(4);
        type(two, "g;");
        assertThat(two.area().getCaretLineNumber()).isEqualTo(2);
        type(one, "Gx");
        type(two, "g;");
        assertThat(two.area().getCaretLineNumber()).as("the other view's change leaves this view's place").isZero();
    }

    /** A full change list drops its oldest entry, and another view's place stays on its entry (changed_common). */
    @Test void aFullChangeListKeepsAnotherViewOnItsEntry() throws Exception {
        var one = first(Files.writeString(folder.resolve("full.txt"), "ab\n".repeat(102)));
        var two = second(one);
        var vim = new Vim(VimHost.detached());
        one.vim(Optional.of(vim));
        two.vim(Optional.of(vim));
        type(one, "xj".repeat(100));
        type(two, "98g;");
        assertThat(two.area().getCaretLineNumber()).as("the third of the list's 100 entries").isEqualTo(2);
        type(one, "x");
        type(two, "g;");
        assertThat(two.area().getCaretLineNumber()).as("the entry before it, now the list's first").isEqualTo(1);
    }

    /** Vim's buffer-local options are the file's: one view's {@code :setlocal} reaches the other, and window-local ones stay. */
    @Test void bufferLocalOptionsBelongToTheFile() throws Exception {
        var one = first(Files.writeString(folder.resolve("options.txt"), "x\n"));
        var two = second(one);
        var vim = new Vim(VimHost.detached());
        one.vim(Optional.of(vim));
        two.vim(Optional.of(vim));
        type(two, ":setlocal sw=2 et nonumber<CR>");
        type(one, ">>");
        assertThat(one.area().getText()).as("'shiftwidth' and 'expandtab' set in the other view").isEqualTo("  x\n");
        type(one, ":setlocal number?<CR>");
        assertThat(one.vimEditor().orElseThrow().message()).as("'number' is each view's own").isEqualTo("  number");
    }

    /** A view in Preview comes back to its source on the text it showed, though another view added lines above it. */
    @Test void aPreviewComesBackToItsSourceOnTheTextItShowed() throws Exception {
        var one = first(DocumentSession.untitled("Untitled", environment));
        var two = second(one);
        for (EditorTab view : List.of(one, two)) view.previews(background::add, Runnable::run);
        one.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        var text = new StringBuilder();
        for (int i = 1; i <= 600; i++) text.append("line ").append(i).append('\n');
        two.area().setText(text.toString());
        one.surface().setSize(400, 300);
        one.surface().doLayout();
        one.scroll().doLayout();
        var port = one.scroll().getViewport();
        port.doLayout();
        port.setViewPosition(new java.awt.Point(0, one.area().yForLine(299) + 3));
        one.markdownMode(MarkdownMode.PREVIEW, false);
        two.area().setCaretPosition(0);
        two.area().replaceSelection("new\n".repeat(100));
        one.markdownMode(MarkdownMode.SOURCE, false);
        one.surface().doLayout();
        assertThat(port.getViewPosition().y).as("the source shows the text it showed").isEqualTo(one.area().yForLine(399) + 3);
    }

    /** Two views of an untitled Markdown file of 600 lines, "line 1" to "line 600", the first laid out with its previews. */
    private List<EditorTab> markdownViews() {
        var one = first(DocumentSession.untitled("Untitled", environment));
        var two = second(one);
        for (EditorTab view : List.of(one, two)) view.previews(background::add, Runnable::run);
        one.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        var text = new StringBuilder();
        for (int i = 1; i <= 600; i++) text.append("line ").append(i).append('\n');
        two.area().setText(text.toString());
        laidOut(one);
        return List.of(one, two);
    }

    /** @return the text of the line the view's source shows first */
    private static String top(EditorTab view) throws Exception {
        var area = view.area();
        int line = area.getLineOfOffset(area.viewToModel2D(new java.awt.Point(0, view.scroll().getViewport().getViewPosition().y)));
        return area.getText(area.getLineStartOffset(line), area.getLineEndOffset(line) - area.getLineStartOffset(line)).strip();
    }

    /** @return the text of the view's caret line */
    private static String caretLine(EditorTab view) throws Exception {
        var area = view.area();
        int line = area.getCaretLineNumber();
        return area.getText(area.getLineStartOffset(line), area.getLineEndOffset(line) - area.getLineStartOffset(line)).strip();
    }

    /**
     * With Vim on, a view in Preview comes back to its source on the text it showed through Vim, as another view's edit
     * puts a view back: Vim's window is that text, so {@code H} goes to its first line, and the cursor kept its own.
     */
    @Test void aVimViewsPreviewComesBackToItsSourceThroughVim() throws Exception {
        var views = markdownViews();
        var one = views.getFirst();
        one.vim(Optional.of(new Vim(VimHost.detached())));
        type(one, "300Gzt305G");
        one.markdownMode(MarkdownMode.PREVIEW, false);
        views.get(1).area().setCaretPosition(0);
        views.get(1).area().replaceSelection("new\n".repeat(100));
        one.markdownMode(MarkdownMode.SOURCE, false);
        one.surface().doLayout();
        assertThat(top(one)).as("the source shows the text it showed").isEqualTo("line 300");
        assertThat(caretLine(one)).as("the cursor kept its text").isEqualTo("line 305");
        type(one, "H");
        assertThat(caretLine(one)).as("Vim's window is the text shown").isEqualTo("line 300");
    }

    private static String numbered(String word, int count) {
        var text = new StringBuilder();
        for (int i = 1; i <= count; i++) text.append(word).append(' ').append(i).append('\n');
        return text.toString();
    }

    /** Puts the view in Preview while its source shows {@code line}, 0-based, first. */
    private static void previewFrom(EditorTab view, int line) throws Exception {
        view.scroll().getViewport().setViewPosition(new java.awt.Point(0, view.area().yForLine(line)));
        view.markdownMode(MarkdownMode.PREVIEW, false);
    }

    /** Back from Preview to the source, laid out as its pane would lay it out. */
    private static void source(EditorTab view) {
        view.markdownMode(MarkdownMode.SOURCE, false);
        view.surface().doLayout();
    }

    /**
     * A view in Preview comes back to its source on its line's number once Revert to Saved has replaced the whole text,
     * as its caret keeps its line, and once a reload has read the file changed on disk: there is no text left to follow.
     */
    @Test void aPreviewComesBackToItsLineAfterARevertOrAReload() throws Exception {
        Path file = Files.writeString(folder.resolve("revert.md"), numbered("line", 600));
        var one = first(file);
        one.previews(background::add, Runnable::run);
        one.session().setStyle(SYNTAX_STYLE_MARKDOWN);
        laidOut(one);
        previewFrom(one, 299);
        one.area().setCaretPosition(one.area().getLineStartOffset(500));
        one.area().replaceSelection("edit ");
        one.session().revert();
        drain();
        assertThat(one.area().getText()).isEqualTo(numbered("line", 600));
        source(one);
        assertThat(top(one)).as("after Revert to Saved").isEqualTo("line 300");
        previewFrom(one, 299);
        Files.writeString(file, numbered("row", 700));
        one.session().reload();
        drain();
        assertThat(one.area().getText()).startsWith("row 1\n");
        source(one);
        assertThat(top(one)).as("after a reload").isEqualTo("row 300");
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
