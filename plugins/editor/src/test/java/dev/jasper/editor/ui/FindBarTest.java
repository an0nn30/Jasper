package dev.jasper.editor.ui;

import java.util.Optional;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class FindBarTest {
    private final RSyntaxTextArea area = new RSyntaxTextArea("foo bar Foo foo\n");
    private final FindBar bar = new FindBar(() -> Optional.of(new FindTarget.Source(area)), () -> { }, id -> { });

    private void type(String id, String text) { bar.event(new dev.jasper.sdk.ui.UiEvent.Edited(id, text)); }
    private void press(String id) { bar.event(new dev.jasper.sdk.ui.UiEvent.Action(id)); }

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
