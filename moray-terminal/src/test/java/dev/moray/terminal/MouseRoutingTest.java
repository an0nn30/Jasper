package dev.moray.terminal;

import com.jediterm.core.input.MouseEvent.Type;
import org.junit.jupiter.api.Test;

import static dev.moray.terminal.MouseRouting.Action;
import static dev.moray.terminal.MouseRouting.Button;
import static org.assertj.core.api.Assertions.assertThat;

class MouseRoutingTest {

    @Test
    void programsThatAskForTheMouseGetIt() {
        assertThat(decide(Type.PRESSED, Button.RIGHT, 1, false, false, true, false)).isEqualTo(Action.REPORT);
        assertThat(decide(Type.WHEEL, Button.NONE, 0, false, false, true, true)).isEqualTo(Action.REPORT);
    }

    @Test
    void shiftKeepsTheMouseLocalEvenWhenReporting() {
        assertThat(decide(Type.PRESSED, Button.LEFT, 1, true, false, true, false)).isEqualTo(Action.START_SELECTION);
    }

    @Test
    void theWheelScrollsTheViewOrSendsArrowsInTheAlternateScreen() {
        assertThat(decide(Type.WHEEL, Button.NONE, 0, false, false, false, false)).isEqualTo(Action.SCROLL_VIEW);
        assertThat(decide(Type.WHEEL, Button.NONE, 0, false, false, false, true)).isEqualTo(Action.SEND_ARROWS);
    }

    @Test
    void clickCountsChooseTheSelectionUnit() {
        assertThat(decide(Type.PRESSED, Button.LEFT, 1, false, false, false, false)).isEqualTo(Action.START_SELECTION);
        assertThat(decide(Type.PRESSED, Button.LEFT, 2, false, false, false, false)).isEqualTo(Action.SELECT_WORD);
        assertThat(decide(Type.PRESSED, Button.LEFT, 3, false, false, false, false)).isEqualTo(Action.SELECT_LINE);
    }

    @Test
    void theLinkModifierOpensLinks() {
        assertThat(decide(Type.PRESSED, Button.LEFT, 1, false, true, false, false)).isEqualTo(Action.OPEN_LINK);
    }

    @Test
    void draggingExtendsAndReleasingEnds() {
        assertThat(decide(Type.DRAGGED, Button.LEFT, 1, false, false, false, false)).isEqualTo(Action.EXTEND_SELECTION);
        assertThat(decide(Type.RELEASED, Button.LEFT, 1, false, false, false, false)).isEqualTo(Action.END_SELECTION);
    }

    @Test
    void otherButtonsAndMovesDoNothingLocally() {
        assertThat(decide(Type.PRESSED, Button.RIGHT, 1, false, false, false, false)).isEqualTo(Action.NONE);
        assertThat(decide(Type.MOVED, Button.NONE, 0, false, false, false, false)).isEqualTo(Action.NONE);
    }

    private static Action decide(Type type, Button button, int clicks, boolean shift, boolean linkModifier,
                                 boolean reporting, boolean alternate) {
        return MouseRouting.decide(type, button, clicks, shift, linkModifier, reporting, alternate);
    }
}
