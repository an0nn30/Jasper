package dev.jasper.editor.ui;

import dev.jasper.browser.api.PreviewFind;
import dev.jasper.editor.testsupport.FakeMarkdownPreviews;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class FindBarTest {
    private final RSyntaxTextArea area = new RSyntaxTextArea("foo bar Foo foo\n");
    private final FindBar bar = new FindBar(() -> Optional.of(new FindTarget.Source(area)), () -> { }, id -> { });
    private final FakeMarkdownPreviews previews = new FakeMarkdownPreviews();
    private final List<AutoCloseable> opened = new ArrayList<>();
    /** The fields the Chromium bar focused, in order. */
    private final List<String> focused = new ArrayList<>();
    private FakeMarkdownPreviews.Recording page;
    private ChromiumPreview chromium;

    private void type(String id, String text) { bar.event(new dev.jasper.sdk.ui.UiEvent.Edited(id, text)); }
    private void press(String id) { bar.event(new dev.jasper.sdk.ui.UiEvent.Action(id)); }
    private static void type(FindBar bar, String text) { bar.event(new dev.jasper.sdk.ui.UiEvent.Edited(FindBar.FIND, text)); }
    private static void press(FindBar bar, String id) { bar.event(new dev.jasper.sdk.ui.UiEvent.Action(id)); }
    private static String query(FindBar bar) {
        return ((dev.jasper.sdk.ui.Nodes.Field) ((dev.jasper.sdk.ui.Nodes.Stack) bar.node().children().getFirst()).children().getFirst()).value();
    }

    /** A bar over the Chromium preview of a new view, whose page is {@link #page}: its answers wait for the test. */
    private FindBar chromiumBar() {
        var view = new DocumentArea(null);
        chromium = new ChromiumPreviews(previews).create(view);
        page = previews.last();
        opened.add(view);
        opened.add(chromium);
        return new FindBar(() -> Optional.of(new FindTarget.Preview(chromium)), () -> { }, focused::add);
    }

    @AfterEach void closeViews() throws Exception { for (var each : opened.reversed()) each.close(); }

    @Test void targetIsCapturedUntilClosedEvenWhenTheSupplierChanges() {
        var other = new RSyntaxTextArea("other");
        var current = new java.util.concurrent.atomic.AtomicReference<FindTarget>(new FindTarget.Source(area));
        var stable = new FindBar(() -> Optional.of(current.get()), () -> {}, id -> {});
        stable.open(false);
        current.set(new FindTarget.Source(other));
        stable.event(new dev.jasper.sdk.ui.UiEvent.Edited(FindBar.FIND, "foo"));
        assertThat(stable.status()).isEqualTo("3 matches");
        stable.next();
        assertThat(area.getSelectedText()).isEqualTo("foo");
        stable.close();
        stable.open(false);
        stable.event(new dev.jasper.sdk.ui.UiEvent.Edited(FindBar.FIND, "other"));
        assertThat(stable.status()).isEqualTo("1 match");
    }

    @Test void closeRestoresCapturedContentEvenWhenTheSupplierNowNamesAnotherPane() {
        class FocusArea extends RSyntaxTextArea {
            int focused;
            @Override public boolean requestFocusInWindow() { focused++;return true; }
        }
        var original=new FocusArea();var other=new FocusArea();
        var current=new java.util.concurrent.atomic.AtomicReference<FindTarget>(new FindTarget.Source(original));
        var stable=new FindBar(() -> Optional.of(current.get()), () -> {}, id -> {});
        stable.open(false);current.set(new FindTarget.Source(other));stable.close();
        assertThat(original.focused).isEqualTo(1);assertThat(other.focused).isZero();
    }

    @Test void closedCapturedTargetRestoresTheCurrentValidContentSafely() {
        class FocusArea extends RSyntaxTextArea {
            int focused;
            @Override public boolean requestFocusInWindow() { focused++;return true; }
        }
        var original=new FocusArea();var fallback=new FocusArea();
        var current=new java.util.concurrent.atomic.AtomicReference<FindTarget>(new FindTarget.Source(original));
        var stable=new FindBar(() -> Optional.of(current.get()), () -> {}, id -> {});
        stable.open(false);original.putClientProperty("jasper.editor.closed",true);current.set(new FindTarget.Source(fallback));
        stable.close();assertThat(original.focused).isZero();assertThat(fallback.focused).isEqualTo(1);
    }

    @Test void dismissingOwnershipDoesNotRestoreFocus() {
        class FocusArea extends RSyntaxTextArea {
            int focused;
            @Override public boolean requestFocusInWindow() {focused++;return true;}
        }
        var content=new FocusArea();
        var model=new FindBar(() -> Optional.of(new FindTarget.Source(content)),() -> {},id -> {});
        model.open(false);model.dismiss();
        assertThat(model.isVisible()).isFalse();assertThat(content.focused).isZero();
    }

    @Test void replacementRevealsThenRevalidatesItsCapturedOwner() {
        var other=new RSyntaxTextArea("foo");
        var current=new java.util.concurrent.atomic.AtomicReference<FindTarget>(new FindTarget.Source(area));
        var reveals=new java.util.concurrent.atomic.AtomicInteger();
        var model=new FindBar(() -> Optional.of(current.get()),() -> {},id -> {},reveals::incrementAndGet);
        model.open(true);model.event(new dev.jasper.sdk.ui.UiEvent.Edited(FindBar.FIND,"foo"));
        model.event(new dev.jasper.sdk.ui.UiEvent.Edited(FindBar.REPLACE,"changed"));
        current.set(new FindTarget.Source(other));model.replaceOne();model.replaceAll();
        assertThat(reveals.get()).isEqualTo(2);
        assertThat(area.getText()).isEqualTo("foo bar Foo foo\n");assertThat(other.getText()).isEqualTo("foo");
        current.set(new FindTarget.Source(area));model.replaceAll();
        assertThat(area.getText()).isEqualTo("changed bar changed changed\n");
    }

    @Test void countsMatchesAndHonoursTheToggles() {
        assertThat(bar.isVisible()).isFalse();
        bar.open(false);
        assertThat(bar.isVisible()).isTrue();
        assertThat(bar.replaceVisible()).isFalse();
        type(FindBar.FIND, "foo");
        assertThat(bar.status()).isEqualTo("3 matches");
        press(FindBar.CASE);
        assertThat(bar.status()).isEqualTo("2 matches");
        type(FindBar.FIND, "fo+");
        assertThat(bar.status()).isEqualTo("No matches");
        press(FindBar.REGEX);
        assertThat(bar.status()).isEqualTo("2 matches");
        type(FindBar.FIND, "(");
        assertThat(bar.status()).isEqualTo("Invalid pattern");
        press(FindBar.REGEX);
        press(FindBar.CASE);
        press(FindBar.WORD);
        type(FindBar.FIND, "fo");
        assertThat(bar.status()).as("whole words only").isEqualTo("No matches");
    }

    @Test void theBarIsOneCompactRowOfIconTogglesAndIconActionsOverAnAlignedReplaceRow() {
        bar.open(true);
        var root = bar.node();
        var row = (dev.jasper.sdk.ui.Nodes.Stack) root.children().getFirst();
        var replace = (dev.jasper.sdk.ui.Nodes.Stack) root.children().getLast();
        assertThat(row.role()).isEqualTo(dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE);
        assertThat(row.children()).extracting(dev.jasper.sdk.ui.Nodes.Node::id).containsExactly(FindBar.FIND, FindBar.CASE, FindBar.WORD,
            FindBar.REGEX, FindBar.STATUS, FindBar.PREVIOUS, FindBar.NEXT, FindBar.CLOSE);
        assertThat(((dev.jasper.sdk.ui.Nodes.Field) row.children().getFirst()).kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.FieldKind.SEARCH);
        for (var child : row.children()) if (child instanceof dev.jasper.sdk.ui.Nodes.Button button) {
            assertThat(button.label()).as("%s is icon-only", button.id()).isEmpty();
            assertThat(button.icon()).isNotEmpty();
            assertThat(button.tooltip()).as("%s names its action for assistive technology", button.id()).isNotBlank();
        }
        java.util.function.Function<String, dev.jasper.sdk.ui.Nodes.Button> option = id -> (dev.jasper.sdk.ui.Nodes.Button)
            ((dev.jasper.sdk.ui.Nodes.Stack) bar.node().children().getFirst()).children().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
        assertThat(option.apply(FindBar.CASE).selected()).as("a match option is a toggle").contains(false);
        press(FindBar.CASE);
        assertThat(option.apply(FindBar.CASE).selected()).contains(true);
        assertThat(replace.state().visible()).isTrue();
        assertThat(replace.children()).extracting(dev.jasper.sdk.ui.Nodes.Node::id).containsExactly(FindBar.REPLACE, FindBar.ONE, FindBar.ALL);
        assertThat(((dev.jasper.sdk.ui.Nodes.Field) replace.children().getFirst()).kind()).as("the replacement is plain text, not a search").isEqualTo(dev.jasper.sdk.ui.Nodes.FieldKind.TEXT);
        assertThat(root.cancelAction()).as("Escape anywhere closes the bar").isEqualTo(FindBar.CLOSE);
    }

    @Test void wholeWordRegexValidatesThePatternSearchEngineActuallyCompiles() {
        bar.open(false);
        press(FindBar.REGEX);
        press(FindBar.WORD);
        press(FindBar.CASE);
        type(FindBar.FIND, "*foo");
        assertThat(bar.status()).isEqualTo("2 matches");
        type(FindBar.FIND, "(");
        assertThat(bar.status()).isEqualTo("Invalid pattern");
    }

    @Test void escapeClosesTheBarFromAnyFocusedChild() {
        bar.open(false);
        assertThat(bar.isVisible()).isTrue();
        assertThat(bar.node().cancelAction()).isEqualTo(FindBar.CLOSE);
        press(bar.node().cancelAction());
        assertThat(bar.isVisible()).isFalse();
    }

    @Test void nextAndPreviousSelectMatches() {
        area.setCaretPosition(0);
        bar.open(false);
        type(FindBar.FIND, "foo");
        bar.next();
        assertThat(area.getSelectionStart()).isEqualTo(0);
        bar.next();
        assertThat(area.getSelectionStart()).isEqualTo(8);
        bar.previous();
        assertThat(area.getSelectionStart()).isEqualTo(0);
    }

    @Test void openingTakesTheSelectionAndClosingHidesTheBar() {
        area.select(4, 7);
        bar.open(true);
        assertThat(((dev.jasper.sdk.ui.Nodes.Field) ((dev.jasper.sdk.ui.Nodes.Stack) bar.node().children().getFirst()).children().getFirst()).value()).isEqualTo("bar");
        assertThat(bar.replaceVisible()).isTrue();
        bar.close();
        assertThat(bar.isVisible()).isFalse();
    }

    /** The Chromium preview answers later: a count for an older query never replaces a newer one's. */
    @Test void aStaleCountNeverReplacesANewerOne() {
        var model = chromiumBar();
        model.open(false);
        type(model, "a");
        type(model, "ab");
        assertThat(model.status()).as("nothing answered yet").isEmpty();
        var marks = page.marks();
        assertThat(marks).hasSize(2);
        marks.get(1).complete(3);
        assertThat(model.status()).isEqualTo("3 matches");
        marks.get(0).complete(7);
        assertThat(model.status()).isEqualTo("3 matches");
        type(model, "abc");
        page.marks().getLast().complete(1);
        assertThat(model.status()).isEqualTo("1 match");
    }

    /** For the Chromium preview, the page's RegExp decides what a valid pattern is, not Java's. */
    @Test void anInvalidReplyShowsInvalidPattern() {
        var model = chromiumBar();
        model.open(false);
        press(model, FindBar.REGEX);
        type(model, "[^]");
        assertThat(model.status()).as("JavaScript's pattern, which Java refuses").isEmpty();
        assertThat(page.finds().getLast()).isEqualTo(new PreviewFind("[^]", false, false, true));
        page.marks().getLast().complete(4);
        assertThat(model.status()).isEqualTo("4 matches");
        type(model, "(?i)x");
        page.marks().getLast().completeExceptionally(new PatternSyntaxException("JavaScript refuses this regular expression", "(?i)x", -1));
        assertThat(model.status()).isEqualTo("Invalid pattern");
        model.next();
        page.steps().getLast().completeExceptionally(new PatternSyntaxException("JavaScript refuses this regular expression", "(?i)x", -1));
        assertThat(model.status()).isEqualTo("Invalid pattern");
    }

    @Test void theChromiumSelectionSeedsTheQueryWhenItArrives() {
        var model = chromiumBar();
        model.open(false);
        assertThat(query(model)).isEmpty();
        assertThat(page.marks()).as("an empty query asks for no count").isEmpty();
        page.selections().getLast().complete("Target");
        assertThat(query(model)).isEqualTo("Target");
        assertThat(page.finds()).containsExactly(new PreviewFind("Target", false, false, false));
        assertThat(focused).as("the seeded query is focused, as on opening").containsExactly(FindBar.FIND, FindBar.FIND);
        page.marks().getLast().complete(2);
        assertThat(model.status()).isEqualTo("2 matches");
        model.close();
        model.open(false);
        page.selections().getLast().complete("two\nlines");
        assertThat(query(model)).as("only one line seeds the query").isEqualTo("Target");
    }

    @Test void aLateSelectionDoesNotOverwriteTyping() {
        var model = chromiumBar();
        model.open(false);
        type(model, "abc");
        page.selections().getLast().complete("Target");
        assertThat(query(model)).isEqualTo("abc");
        assertThat(page.finds()).extracting(PreviewFind::query).containsExactly("abc");
        model.close();
        model.open(false);
        var earlier = page.selections().getLast();
        model.close();
        model.open(false);
        earlier.complete("Older");
        assertThat(query(model)).as("a read from an earlier opening").isEqualTo("abc");
    }

    /** F35: an answer cancelled, as when the preview closes, tells nothing: no "No matches", no "Invalid pattern". */
    @Test void aCancelledAnswerChangesNothing() {
        var model = chromiumBar();
        model.open(false);
        type(model, "a");
        page.marks().getLast().complete(2);
        type(model, "ab");
        model.next();
        chromium.close();
        assertThat(page.marks().getLast()).isCancelled();
        assertThat(page.selections().getLast()).isCancelled();
        assertThat(model.status()).isEqualTo("2 matches");
        assertThat(query(model)).isEqualTo("ab");
        // A page that has gone, as when Chromium stops, cancels what is asked of it at once.
        var other = chromiumBar();
        other.open(false);
        type(other, "a");
        page.marks().getLast().complete(1);
        page.close();
        type(other, "ab");
        other.next();
        other.previous();
        assertThat(other.status()).isEqualTo("1 match");
    }

    /** The page answers one Find request at a time: a step asked while the count waits follows it, keeping the count. */
    @Test void aStepAskedWhileTheCountWaitsFollowsIt() {
        var model = chromiumBar();
        model.open(false);
        type(model, "a");
        model.next();
        assertThat(page.steps()).as("not before the count").isEmpty();
        page.marks().getLast().complete(2);
        assertThat(model.status()).isEqualTo("2 matches");
        assertThat(page.calls()).filteredOn(call -> call.startsWith("select ")).containsExactly("select a true");
        page.steps().getLast().complete(true);
        assertThat(model.status()).isEqualTo("2 matches");
        model.previous();
        assertThat(page.calls().getLast()).as("at once once the count is in").isEqualTo("select a false");
        page.steps().getLast().complete(false);
        assertThat(model.status()).isEqualTo("No matches");
    }

    @Test void aStepAnswerForAnOlderQueryChangesNothing() {
        var model = chromiumBar();
        model.open(false);
        type(model, "a");
        page.marks().getLast().complete(2);
        model.next();
        type(model, "ab");
        page.steps().getLast().complete(false);
        assertThat(model.status()).as("the step was for \"a\"").isEqualTo("2 matches");
        model.next();
        model.next();
        page.marks().getLast().complete(1);
        assertThat(page.steps()).as("one step for the presses that waited").hasSize(2);
        assertThat(model.status()).isEqualTo("1 match");
    }

    /** N7: a page not yet ready answers at once with nothing found. */
    @Test void findBeforeThePageIsReadyAnswersNoMatchesAtOnce() {
        var model = chromiumBar();
        page.ready(false);
        model.open(false);
        type(model, "a");
        assertThat(model.status()).isEqualTo("No matches");
        model.next();
        assertThat(model.status()).isEqualTo("No matches");
    }

    @Test void replaceOneAndReplaceAll() {
        area.setText("a1 a2 a3");
        area.setCaretPosition(0);
        bar.open(true);
        type(FindBar.FIND, "a");
        type(FindBar.REPLACE, "b");
        bar.replaceOne();
        assertThat(area.getText()).isEqualTo("b1 a2 a3");
        bar.replaceAll();
        assertThat(area.getText()).isEqualTo("b1 b2 b3");
        assertThat(bar.status()).isEqualTo("Replaced 2");
    }
}
