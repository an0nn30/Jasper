package dev.jasper.editor.ui;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FindBarTest {
    private final RSyntaxTextArea area = new RSyntaxTextArea("foo bar Foo foo\n");
    private final FindBar bar = new FindBar(() -> Optional.of(area));

    @Test void countsMatchesAndHonoursTheToggles() {
        assertThat(bar.isVisible()).isFalse();
        bar.open(false);
        assertThat(bar.isVisible()).isTrue();
        assertThat(bar.replaceVisible()).isFalse();
        bar.findField().setText("foo");
        assertThat(bar.status()).isEqualTo("3 matches");
        bar.matchCase().doClick();
        assertThat(bar.status()).isEqualTo("2 matches");
        bar.findField().setText("fo+");
        assertThat(bar.status()).isEqualTo("No matches");
        bar.regex().doClick();
        assertThat(bar.status()).isEqualTo("2 matches");
        bar.findField().setText("(");
        assertThat(bar.status()).isEqualTo("Invalid pattern");
        bar.regex().doClick();
        bar.matchCase().doClick();
        bar.wholeWord().doClick();
        bar.findField().setText("fo");
        assertThat(bar.status()).as("whole words only").isEqualTo("No matches");
    }

    @Test void wholeWordRegexValidatesThePatternSearchEngineActuallyCompiles() {
        bar.open(false);
        bar.regex().doClick();
        bar.wholeWord().doClick();
        bar.matchCase().doClick();
        bar.findField().setText("*foo");
        assertThat(bar.status()).isEqualTo("2 matches");
        bar.findField().setText("(");
        assertThat(bar.status()).isEqualTo("Invalid pattern");
    }

    @Test void escapeClosesTheBarFromAnyFocusedChild() {
        bar.open(false);
        assertThat(bar.isVisible()).isTrue();
        Object key = bar.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertThat(key).isNotNull();
        bar.getActionMap().get(key).actionPerformed(new ActionEvent(bar, ActionEvent.ACTION_PERFORMED, ""));
        assertThat(bar.isVisible()).isFalse();
    }

    @Test void nextAndPreviousSelectMatches() {
        area.setCaretPosition(0);
        bar.open(false);
        bar.findField().setText("foo");
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
        assertThat(bar.findField().getText()).isEqualTo("bar");
        assertThat(bar.replaceVisible()).isTrue();
        bar.close();
        assertThat(bar.isVisible()).isFalse();
    }

    @Test void replaceOneAndReplaceAll() {
        area.setText("a1 a2 a3");
        area.setCaretPosition(0);
        bar.open(true);
        bar.findField().setText("a");
        bar.replaceField().setText("b");
        bar.replaceOne();
        assertThat(area.getText()).isEqualTo("b1 a2 a3");
        bar.replaceAll();
        assertThat(area.getText()).isEqualTo("b1 b2 b3");
        assertThat(bar.status()).isEqualTo("Replaced 2");
    }
}
