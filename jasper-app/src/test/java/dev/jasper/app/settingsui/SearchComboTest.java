package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchComboTest {
    private final SearchCombo combo = new SearchCombo(1, "No matching themes");
    {
        for (String label : List.of("Same as UI theme", "Catppuccin Mocha", "Darcula", "Jasper Dark", "Jasper Light", "Nord"))
            combo.addItem(label);
    }
    private final SearchCombo.Popup popup = combo.searchPopup();

    private void type(String text) {
        for (char c : text.toCharArray()) popup.getKeyListener().keyTyped(new KeyEvent(combo, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c));
    }
    private boolean press(int code) {
        var event = new KeyEvent(combo, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
        popup.getKeyListener().keyPressed(event);
        return event.isConsumed();
    }

    @Test void typingShowsTheSearchBarAndNarrowsTheListToNamesHoldingEveryWord() {
        popup.show();
        assertThat(popup.searchBar().isVisible()).isFalse();
        assertThat(popup.shown()).hasSize(6);
        type("dark");
        assertThat(popup.searchBar().isVisible()).isTrue();
        assertThat(popup.query()).isEqualTo("dark");
        assertThat(popup.shown()).as("the pinned entry stays first").containsExactly("Same as UI theme", "Jasper Dark");
        press(KeyEvent.VK_ESCAPE);
        type("MOCHA cat");
        assertThat(popup.shown()).containsExactly("Same as UI theme", "Catppuccin Mocha");
    }

    @Test void theHighlightStartsOnTheChoiceAndMovesToTheFirstMatch() {
        combo.setSelectedItem("Nord");
        popup.show();
        assertThat(popup.highlighted()).isEqualTo("Nord");
        type("jasper");
        assertThat(popup.highlighted()).as("the choice no longer matches").isEqualTo("Jasper Dark");
        assertThat(press(KeyEvent.VK_DOWN)).isTrue();
        assertThat(popup.highlighted()).isEqualTo("Jasper Light");
        press(KeyEvent.VK_DOWN);
        assertThat(popup.highlighted()).as("the end stops").isEqualTo("Jasper Light");
        press(KeyEvent.VK_UP);
        press(KeyEvent.VK_UP);
        assertThat(popup.highlighted()).as("the pinned entry is reachable").isEqualTo("Same as UI theme");
    }

    @Test void enterPicksTheHighlightedNameAndClearsTheSearch() {
        popup.show();
        type("nord");
        assertThat(press(KeyEvent.VK_ENTER)).as("Enter does not reach the dialog's OK").isTrue();
        assertThat(combo.getSelectedItem()).isEqualTo("Nord");
        assertThat(popup.query()).isEmpty();
        assertThat(popup.searchBar().isVisible()).isFalse();
    }

    @Test void backspaceEditsTheSearchAndEscapeEndsIt() {
        popup.show();
        type("nordx");
        assertThat(popup.shown()).as("a line says so").containsExactly("Same as UI theme", "No matching themes");
        assertThat(popup.highlighted()).as("nothing matched, so nothing is highlighted").isNull();
        press(KeyEvent.VK_DOWN);
        press(KeyEvent.VK_DOWN);
        assertThat(popup.highlighted()).as("the line is not an entry").isEqualTo("Same as UI theme");
        assertThat(press(KeyEvent.VK_BACK_SPACE)).isTrue();
        assertThat(popup.shown()).containsExactly("Same as UI theme", "Nord");
        assertThat(press(KeyEvent.VK_ESCAPE)).as("Escape does not close Settings").isTrue();
        assertThat(popup.query()).isEmpty();
        assertThat(combo.getSelectedItem()).as("nothing was picked").isEqualTo("Same as UI theme");
    }

    @Test void typingNeverPicksByFirstLetterAndShortcutsAreLeftAlone() {
        assertThat(combo.selectWithKeyChar('n')).isFalse();
        assertThat(combo.getSelectedItem()).isEqualTo("Same as UI theme");
        var shortcut = new KeyEvent(combo, KeyEvent.KEY_TYPED, 0, InputEvent.META_DOWN_MASK, KeyEvent.VK_UNDEFINED, 'w');
        popup.getKeyListener().keyTyped(shortcut);
        assertThat(shortcut.isConsumed()).isFalse();
        assertThat(popup.query()).isEmpty();
        assertThat(press(KeyEvent.VK_DOWN)).as("closed and not searching: the combo's own keys").isFalse();
    }

    @Test void enterAfterASearchWithNoMatchesPicksNothing() {
        combo.setSelectedItem("Nord");
        popup.show();
        type("xyz");
        assertThat(press(KeyEvent.VK_ENTER)).isTrue();
        assertThat(combo.getSelectedItem()).isEqualTo("Nord");
    }

    @Test void homeEndAndTheKeypadArrowsMoveTheHighlightWithoutPicking() {
        popup.show();
        type("jasper");
        assertThat(press(KeyEvent.VK_END)).isTrue();
        assertThat(popup.highlighted()).isEqualTo("Jasper Light");
        assertThat(press(KeyEvent.VK_HOME)).isTrue();
        assertThat(popup.highlighted()).isEqualTo("Same as UI theme");
        assertThat(press(KeyEvent.VK_KP_DOWN)).isTrue();
        assertThat(popup.highlighted()).isEqualTo("Jasper Dark");
        assertThat(press(KeyEvent.VK_KP_UP)).isTrue();
        assertThat(popup.highlighted()).isEqualTo("Same as UI theme");
        assertThat(combo.getSelectedItem()).as("only Enter picks").isEqualTo("Same as UI theme");
    }

    @Test void aNewLookAndFeelKeepsTheSearchAndNeverPicksByFirstLetter() {
        combo.updateUI();
        var fresh = combo.searchPopup();
        assertThat(fresh).isNotSameAs(popup);
        assertThat(combo.selectWithKeyChar('n')).isFalse();
        assertThat(combo.getSelectedItem()).isEqualTo("Same as UI theme");
        fresh.show();
        fresh.getKeyListener().keyTyped(new KeyEvent(combo, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'n'));
        assertThat(fresh.shown()).containsExactly("Same as UI theme", "Catppuccin Mocha", "Nord");
    }

    @Test void spaceOpensTheListAndAltGrCharactersAreSearchText() {
        var space = new KeyEvent(combo, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, ' ');
        popup.getKeyListener().keyTyped(space);
        assertThat(space.isConsumed()).isTrue();
        assertThat(popup.query()).isEmpty();
        var altGr = new KeyEvent(combo, KeyEvent.KEY_TYPED, 0, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_UNDEFINED, '@');
        popup.getKeyListener().keyTyped(altGr);
        assertThat(popup.query()).isEqualTo("@");
    }

    @Test void aListChangeWhileSearchingRefilters() {
        popup.show();
        type("gruv");
        assertThat(popup.shown()).containsExactly("Same as UI theme", "No matching themes");
        combo.addItem("Gruvbox Dark");
        assertThat(popup.shown()).containsExactly("Same as UI theme", "Gruvbox Dark");
        assertThat(popup.highlighted()).isEqualTo("Gruvbox Dark");
    }
}
