package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Plan 4b Task 1 (SDK 0.27.0): a button's hold menu opens under it once a left press has been held for 500 ms, and not
 * before, and the release then reports nothing; a shorter press is the button's own action. A popup trigger and the
 * menu keys open it at once, and choosing an item, a submenu's child included, reports the item's action. The clock is
 * the test's own: it runs the timer's check itself at the times it names.
 */
class HoldMenuTest {
    static final ControlState READY = ControlState.READY;
    static final long MS = 1_000_000L;
    static final MenuItem ENTRY = new MenuItem("entry-1", "Example Domain", "", "go:1", true, false);
    static final MenuItem OLDER = new MenuItem("older", "Older", "", "", true, false, Hue.NEUTRAL, "",
        List.of(new MenuItem("entry-3", "Jasper", "", "go:3", true, false)));

    static Button back(ControlState state, List<MenuItem> history) {
        return new Button("back", state, "", "back", ButtonKind.GHOST, "back", "Back", ButtonPresentation.STANDARD, Optional.empty(), Optional.empty(), history);
    }

    static Buttons.ButtonControl control(KitView view) { return (Buttons.ButtonControl) view.control("back"); }

    /** As if the button were on screen: the menu shows (headless, without placing it) when it opens. */
    static HoldMenu onScreen(HoldMenu hold) {
        hold.show = popup -> { popup.setVisible(true); return true; };
        return hold;
    }

    /** A left press (or a press that is a popup trigger) on the button, as a person makes it. */
    static void press(Buttons.KitButton button, boolean trigger) {
        button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, 4, 4, 1, trigger,
            MouseEvent.BUTTON1));
    }

    static void lift(Buttons.KitButton button) {
        button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 4, 4, 1, false, MouseEvent.BUTTON1));
    }

    @Test void aHeldPressOpensTheMenuAfter500MillisecondsAndNotBeforeUnderBothKits() {
        for (var kit : List.of(Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.dark(), kit);
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(back(READY, List.of(ENTRY, OLDER))), events::add)) {
                var hold = onScreen(control(view).hold);
                long[] now = {0};
                hold.clock = () -> now[0];
                var button = control(view).button;
                button.setSize(28, 28);
                press(button, false);
                now[0] = 499 * MS;
                hold.elapsed();
                assertThat(hold.popup.isVisible()).as("under %s: not before 500 ms", kit.id()).isFalse();
                assertThat(hold.timer.isRunning()).as("it waits for the rest").isTrue();
                now[0] = 500 * MS;
                hold.elapsed();
                assertThat(hold.popup.isVisible()).as("after 500 ms").isTrue();
                assertThat(button.getModel().isPressed()).isFalse();
                lift(button);
                assertThat(events).as("the release reports nothing").isEmpty();
                assertThat(hold.popup.getComponentCount()).isEqualTo(2);
                ((JMenuItem) hold.popup.getComponent(0)).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("go:1"));
                assertThat(hold.popup.isVisible()).as("choosing closes the menu").isFalse();
            } finally { release(context); }
        });
    }

    @Test void aShorterPressIsTheButtonsOwnAction() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(back(READY, List.of(ENTRY))), events::add)) {
                var hold = control(view).hold;
                long[] now = {0};
                hold.clock = () -> now[0];
                var button = control(view).button;
                button.setSize(28, 28);
                press(button, false);
                now[0] = 200 * MS;
                lift(button);
                assertThat(events).containsExactly(new UiEvent.Action("back"));
                assertThat(hold.timer.isRunning()).as("the release stops the wait").isFalse();
                now[0] = 900 * MS;
                hold.elapsed();
                assertThat(hold.popup.isVisible()).as("a released press opens nothing").isFalse();
            } finally { release(context); }
        });
    }

    @Test void aPopupTriggerAndTheMenuKeysOpenTheMenuAtOnce() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(back(READY, List.of(ENTRY, OLDER))), events::add)) {
                var hold = onScreen(control(view).hold);
                var button = control(view).button;
                button.setSize(28, 28);
                // A Control-click on macOS: the left button, and a popup trigger.
                press(button, true);
                assertThat(hold.popup.isVisible()).isTrue();
                lift(button);
                assertThat(events).as("the trigger's release reports nothing").isEmpty();
                // After a change of look and feel the button's own listener runs after the menu's and arms the button
                // again: the release still reports nothing while the menu shows.
                button.getModel().setArmed(true);
                button.getModel().setPressed(true);
                lift(button);
                assertThat(events).as("whichever listener ran first").isEmpty();
                hold.dismiss();
                for (var stroke : List.of(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK), KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0))) {
                    var key = button.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
                    assertThat(key).as("%s is bound", stroke).isNotNull();
                    button.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(button, 0, ""));
                    assertThat(hold.popup.isVisible()).isTrue();
                    hold.dismiss();
                }
                var older = (Menus.Submenu) hold.popup.getComponent(1);
                ((JMenuItem) older.getMenuComponent(0)).doClick(0);
                assertThat(events).as("a submenu's child").containsExactly(new UiEvent.Action("go:3"));
                assertThat(button.getClientProperty(KitOwnership.CONTEXT_MENU)).isSameAs(hold.popup);
            } finally { release(context); }
        });
    }

    @Test void aButtonWithoutAHoldMenuOrThatTakesNoInputOpensNone() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(back(READY, List.of())), events::add)) {
                assertThat(control(view).hold).as("made the first time there is a hold menu").isNull();
                view.update(stack(back(READY, List.of(ENTRY))));
                var hold = control(view).hold;
                long[] now = {0};
                hold.clock = () -> now[0];
                var button = control(view).button;
                button.setSize(28, 28);
                view.update(stack(back(new ControlState(true, false, false, ""), List.of(ENTRY))));
                press(button, false);
                now[0] = 600 * MS;
                hold.elapsed();
                press(button, true);
                assertThat(hold.popup.isVisible()).as("a disabled button").isFalse();
                view.update(stack(back(READY, List.of())));
                assertThat(button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0)))
                    .as("no menu key without a menu").isNull();
                assertThat(button.getClientProperty(KitOwnership.CONTEXT_MENU)).isNull();
                press(button, false);
                now[0] = 1200 * MS;
                hold.elapsed();
                assertThat(hold.popup.isVisible()).isFalse();
                lift(button);
                assertThat(events).as("a long press without a hold menu is the button's own").containsExactly(new UiEvent.Action("back"));
            } finally { release(context); }
        });
    }

    /**
     * Review C1 (ruling P19): the timer opens nothing for a press that ended without a release reaching the button (a
     * focus loss clears its pressed state), one whose pointer left the button (no longer armed), or a button not on
     * screen, where no popup shows at all.
     */
    @Test void aPressThatEndedLeftTheButtonOrIsOffScreenOpensNothing() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(back(READY, List.of(ENTRY))), events::add)) {
                var hold = control(view).hold;
                long[] now = {0};
                hold.clock = () -> now[0];
                var button = control(view).button;
                button.setSize(28, 28);
                assertThat(button.isShowing()).isFalse();
                press(button, false);
                now[0] = 600 * MS;
                hold.elapsed();
                assertThat(hold.popup.isVisible()).as("off screen: no popup at all").isFalse();
                assertThat(button.getModel().isArmed()).as("and the press is the button's still").isTrue();
                lift(button);
                events.clear();
                onScreen(hold);
                now[0] = 0;
                press(button, false);
                // A focus loss ends the press as BasicButtonListener.focusLost does (Swing's own click, if any, aside).
                button.getModel().setPressed(false);
                button.getModel().setArmed(false);
                now[0] = 600 * MS;
                hold.elapsed();
                assertThat(hold.popup.isVisible()).as("the press ended (a focus loss)").isFalse();
                events.clear();
                now[0] = 0;
                press(button, false);
                // The pointer leaving the button disarms it, as BasicButtonListener.mouseExited does.
                button.getModel().setArmed(false);
                now[0] = 600 * MS;
                hold.elapsed();
                assertThat(hold.popup.isVisible()).as("the pointer left the button").isFalse();
                lift(button);
                assertThat(events).as("a release off the button is no click").isEmpty();
            } finally { release(context); }
        });
    }

    /**
     * Plan 4b's W14: a press whose pointer leaves the button opens nothing, even should it come back before the hold
     * is up, as the button, armed again, is then no longer held where it was pressed.
     */
    @Test void aPressThatLeavesTheButtonAndComesBackOpensNothing() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(back(READY, List.of(ENTRY))), events::add)) {
                var hold = onScreen(control(view).hold);
                long[] now = {0};
                hold.clock = () -> now[0];
                var button = control(view).button;
                button.setSize(28, 28);
                press(button, false);
                assertThat(hold.timer.isRunning()).isTrue();
                now[0] = 200 * MS;
                button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                    40, 4, 0, false));
                assertThat(hold.timer.isRunning()).as("leaving ends the hold").isFalse();
                now[0] = 300 * MS;
                button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                    4, 4, 0, false));
                assertThat(button.getModel().isArmed()).as("Swing arms the button again").isTrue();
                now[0] = 600 * MS;
                hold.elapsed();
                assertThat(hold.popup.isVisible()).as("came back within the hold").isFalse();
            } finally { release(context); }
        });
    }

    /** The menu opens while the press is held and its content changes in place as the history does. */
    @Test void anUpdateChangesTheItemsAndClosingTheViewHidesTheMenu() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var view = new KitView(context, stack(back(READY, List.of(ENTRY))), e -> {});
            try {
                var hold = onScreen(control(view).hold);
                hold.open();
                assertThat(hold.popup.isVisible()).isTrue();
                int[] shown = MenusTest.shownAgain(hold.popup, () -> view.update(stack(back(READY, List.of(ENTRY, OLDER)))));
                assertThat(hold.popup.getComponentCount()).isEqualTo(2);
                assertThat(shown[0]).as("packed: shown again at its new size").isEqualTo(1);
                shown = MenusTest.shownAgain(hold.popup, () -> view.update(stack(back(READY, List.of(ENTRY, OLDER)))));
                assertThat(shown[0]).as("an update that changes nothing shows nothing again").isZero();
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                view.close();
                assertThat(hold.popup.isVisible()).isFalse();
                assertThat(hold.timer.isRunning()).isFalse();
            } finally { release(context); }
        });
    }
}
