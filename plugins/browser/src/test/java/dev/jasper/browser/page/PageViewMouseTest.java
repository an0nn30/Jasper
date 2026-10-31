package dev.jasper.browser.page;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mouse's back and forward buttons go to the page's item, never to the page; every other button is the page's.
 * The view's buttons here are Windows', whatever the host is. Headless AWT makes no event of a button above 3, so each
 * event here reports its button itself. Ctrl+wheel magnifies the page through its item, and never reaches the page.
 */
class PageViewMouseTest {
    private final List<NavigationButton> told = new ArrayList<>();
    private final List<Integer> magnified = new ArrayList<>();

    @Test void theBackAndForwardButtonsTellTheItemOnceOnReleaseAndNoneOfTheirEventsReachThePage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view();
            for (int button : new int[] { 4, 5 }) {
                var events = click(view, button);
                for (MouseEvent event : events) {
                    view.processMouseEvent(event);
                    assertThat(event.isConsumed()).as(event.paramString()).isTrue();
                }
            }
            assertThat(told).containsExactly(NavigationButton.BACK, NavigationButton.FORWARD);
            assertThat(view.lastPress()).as("the page got no press").isEqualTo(LastPress.NONE);
            assertThat(view.activation().active(System.currentTimeMillis())).as("nor an activation").isFalse();
        });
    }

    @Test void theOtherButtonsAreThePages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view();
            for (int button : new int[] { MouseEvent.BUTTON1, MouseEvent.BUTTON2, MouseEvent.BUTTON3 }) {
                for (MouseEvent event : click(view, button)) view.processMouseEvent(event);
                assertThat(view.lastPress().button()).isEqualTo(button);
                long pressed = view.lastPress().whenMillis();
                assertThat(view.activation().consume(pressed)).as("each press activates the page, for one popup").isTrue();
                assertThat(view.activation().consume(pressed)).isFalse();
            }
            assertThat(told).isEmpty();
        });
    }

    @Test void aPressTheWindowTookIsNeitherThePagesNorTheItems() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view();
            for (MouseEvent event : click(view, 4)) {
                event.consume();
                view.processMouseEvent(event);
            }
            assertThat(told).isEmpty();
        });
    }

    /**
     * Spec section 10: Ctrl+wheel, which touchpad pinch sends on Windows, magnifies the page a step for each whole notch,
     * away from the user bigger, and is never the page's, so Chromium's own Ctrl+wheel zoom never applies. A touchpad
     * turns a notch in parts, which add up; turning back starts afresh.
     */
    @Test void ctrlWheelMagnifiesAStepForEachWholeNotchAndNeverReachesThePage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view();
            var turns = new ArrayList<MouseWheelEvent>();
            for (double notches : new double[] { -1, 1 }) turns.add(turn(view, notches));
            assertThat(magnified).containsExactly(1, -1);
            magnified.clear();
            for (double notches : new double[] { -0.4, -0.4 }) turns.add(turn(view, notches));
            assertThat(magnified).isEmpty();
            turns.add(turn(view, -0.4));
            assertThat(magnified).as("1.2 notches: one step").containsExactly(1);
            turns.add(turn(view, -2.9));
            assertThat(magnified).as("3.1 notches in all: three more").containsExactly(1, 1, 1, 1);
            magnified.clear();
            turns.add(turn(view, 0.6));
            assertThat(magnified).as("turning back starts afresh").isEmpty();
            turns.add(turn(view, 0.5));
            assertThat(magnified).containsExactly(-1);
            turns.add(turn(view, 0));
            assertThat(magnified).hasSize(1);
            assertThat(turns).allSatisfy(turn -> assertThat(turn.isConsumed()).as(turn.paramString()).isTrue());
        });
    }

    @Test void aWheelWithoutCtrlIsThePagesAndOneTheWindowTookIsNeither() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view();
            for (int modifiers : new int[] { 0, InputEvent.SHIFT_DOWN_MASK, InputEvent.META_DOWN_MASK, InputEvent.ALT_DOWN_MASK }) {
                var turn = wheel(view, modifiers, -3);
                view.processMouseWheelEvent(turn);
                assertThat(turn.isConsumed()).as(turn.paramString()).isFalse();
            }
            var taken = wheel(view, InputEvent.CTRL_DOWN_MASK, -3);
            taken.consume();
            view.processMouseWheelEvent(taken);
            assertThat(magnified).isEmpty();
        });
    }

    private PageView view() {
        var view = new PageView(event -> false, shown -> { }, rate -> { }, key -> { },
            button -> NavigationButton.of(button, NavigationButtonTest.WINDOWS, NavigationButtonTest.W_TOOLKIT));
        view.onNavigationButton(told::add);
        view.onMagnify(magnified::add);
        return view;
    }

    /** Turns the wheel over {@code view} by {@code notches} with Ctrl held, and answers the event. */
    private static MouseWheelEvent turn(PageView view, double notches) {
        var turn = wheel(view, InputEvent.CTRL_DOWN_MASK, notches);
        view.processMouseWheelEvent(turn);
        return turn;
    }

    /** A turn of the wheel over {@code view}, of {@code notches}: away from the user when negative. */
    private static MouseWheelEvent wheel(PageView view, int modifiers, double notches) {
        return new MouseWheelEvent(view, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiers, 10, 10, 10, 10, 0, false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, (int) notches, notches);
    }

    /** A button's press, release and click over the view. */
    private static List<MouseEvent> click(PageView view, int button) {
        long when = System.currentTimeMillis();
        int down = button == MouseEvent.BUTTON1 ? InputEvent.BUTTON1_DOWN_MASK : 0;
        return List.of(event(view, MouseEvent.MOUSE_PRESSED, when, down, button), event(view, MouseEvent.MOUSE_RELEASED, when + 1, 0, button),
            event(view, MouseEvent.MOUSE_CLICKED, when + 1, 0, button));
    }

    private static MouseEvent event(PageView view, int id, long when, int modifiers, int button) {
        return new MouseEvent(view, id, when, modifiers, 10, 10, 1, false, MouseEvent.NOBUTTON) {
            @Override public int getButton() { return button; }
        };
    }
}
