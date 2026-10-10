package dev.jasper.editor.ui;

import java.util.Optional;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.editor.testsupport.EdtTests.class)
class FindBarTest {
    private final RSyntaxTextArea area = new RSyntaxTextArea("foo bar Foo foo\n");
    private final FindBar bar = new FindBar(() -> Optional.of(area), () -> { }, id -> { });

    private void type(String id, String text) { bar.event(new dev.jasper.sdk.ui.UiEvent.Edited(id, text)); }
    private void press(String id) { bar.event(new dev.jasper.sdk.ui.UiEvent.Action(id)); }

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
