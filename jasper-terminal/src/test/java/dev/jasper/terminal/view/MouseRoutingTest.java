package dev.jasper.terminal.view;

import dev.jasper.terminal.internal.text.MouseInput.Type;
import dev.jasper.terminal.view.TerminalLink.Gesture;
import org.junit.jupiter.api.Test;

import static dev.jasper.terminal.view.MouseRouting.Action;
import static dev.jasper.terminal.internal.text.MouseInput.Button;
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

    /** The split gesture on every platform, whichever one runs the build: Shift with the link modifier. */
    @Test
    void shiftWithTheLinkModifierIsASplitOnMacOsWhetherOrNotTheProgramAsksForReports() {
        assertThat(linkGesture(true, true, true, false)).isEqualTo(Gesture.SPLIT);
        assertThat(linkGesture(true, true, true, true)).isEqualTo(Gesture.SPLIT);
    }

    @Test
    void shiftWithTheLinkModifierIsASplitOnLinuxAndWindowsOnlyWithoutMouseReports() {
        assertThat(linkGesture(true, true, false, false)).isEqualTo(Gesture.SPLIT);
        assertThat(linkGesture(true, true, false, true)).as("Shift only keeps the click local there").isEqualTo(Gesture.OPEN);
    }

    @Test
    void theLinkModifierWithoutShiftIsAnOpenEverywhere() {
        for (boolean macOs : new boolean[] {true, false}) {
            for (boolean reporting : new boolean[] {true, false}) {
                assertThat(linkGesture(false, true, macOs, reporting)).isEqualTo(Gesture.OPEN);
            }
        }
    }

    private static Gesture linkGesture(boolean shift, boolean linkModifier, boolean macOs, boolean reporting) {
        return MouseRouting.linkGesture(shift, linkModifier, macOs, reporting);
    }

    private static Action decide(Type type, Button button, int clicks, boolean shift, boolean linkModifier,
                                 boolean reporting, boolean alternate) {
        return MouseRouting.decide(type, button, clicks, shift, linkModifier, reporting, alternate);
    }
}
