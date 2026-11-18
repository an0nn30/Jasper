package dev.jasper.browser.page;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The mouse's back and forward buttons go to the page's item, never to the page; every other button is the page's.
 * The view's buttons here are Windows', whatever the host is. Headless AWT makes no event of a button above 3, so each
 * event here reports its button itself. On Windows and Linux, Ctrl+wheel magnifies the page through its item, and never
 * reaches the page. An Alt-click on a link reaches the page without Alt, so it loads in place (spec section 8).
 */
class PageViewMouseTest {
    /** AWT's old Alt modifier, {@code InputEvent.ALT_MASK}, which Java deprecates. */
    private static final int ALT_MASK = 1 << 3;
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
                assertThat(view.lastPress().x()).as("where, on the canvas (L20)").isEqualTo(10);
                assertThat(view.lastPress().y()).isEqualTo(10);
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
     * Spec sections 7 and 10: on Windows and Linux, Ctrl+wheel, which touchpad pinch sends on Windows, magnifies the page,
     * away from the user bigger, and is never the page's, so Chromium's own Ctrl+wheel zoom never applies. It counts the
     * notches as Chromium does: they add up, a turn gives a step once they round to a whole notch, at most one step a
     * turn, and what is left over carries to the next turn, whichever way it goes.
     */
    @Test void ctrlWheelMagnifiesOnWindowsAndLinuxAsChromiumCountsItAndNeverReachesThePage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (String os : List.of(NavigationButtonTest.WINDOWS, NavigationButtonTest.LINUX)) {
                magnified.clear();
                var view = view(os);
                var turns = new ArrayList<MouseWheelEvent>();
                for (double notches : new double[] { -1, 1 }) turns.add(turn(view, notches));
                assertThat(magnified).as(os).containsExactly(1, -1);
                magnified.clear();
                turns.add(turn(view, -0.4));
                assertThat(magnified).as("under half a notch").isEmpty();
                turns.add(turn(view, -0.4));
                assertThat(magnified).as("0.8 rounds to a notch").containsExactly(1);
                turns.add(turn(view, -3));
                assertThat(magnified).as("at most one step a turn").containsExactly(1, 1);
                turns.add(turn(view, 0.4));
                assertThat(magnified).as("the 0.2 left over carries: 0.6 rounds to a notch back").containsExactly(1, 1, -1);
                turns.add(turn(view, 0));
                assertThat(magnified).hasSize(3);
                assertThat(turns).allSatisfy(turn -> assertThat(turn.isConsumed()).as(turn.paramString()).isTrue());
            }
        });
    }

    /** Plan 4b's W19: a turn of no notches is no turn, even with half a notch left over from the turn before. */
    @Test void aTurnOfNoNotchesMagnifiesNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view(NavigationButtonTest.WINDOWS);
            turn(view, -0.5);
            assertThat(magnified).as("half a notch rounds away from zero").containsExactly(1);
            assertThat(turn(view, 0).isConsumed()).isTrue();
            assertThat(magnified).as("half a notch the other way is left, but no turn").containsExactly(1);
        });
    }

    /**
     * Plan 4b's W11: a canvas that magnifies nothing, as DevTools' does, hands Ctrl+wheel to its browser on Windows and
     * Linux too, so DevTools zoom as Chromium zooms them.
     */
    @Test void aCanvasWithoutAMagnifierLeavesCtrlWheelToItsBrowser() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (String os : List.of(NavigationButtonTest.WINDOWS, NavigationButtonTest.LINUX)) {
                var devTools = new PageView(event -> false, shown -> { }, rate -> { }, key -> { },
                    button -> NavigationButton.of(button, NavigationButtonTest.WINDOWS, NavigationButtonTest.W_TOOLKIT),
                    (delay, task) -> () -> { });
                devTools.useOsName(os);
                assertThat(turn(devTools, -1).isConsumed()).as(os).isFalse();
            }
        });
    }

    /**
     * Spec section 7 and plan 4b's P32: on macOS, where trackpad pinch is the gesture, Ctrl+wheel is the page's like any
     * wheel; Chromium itself does not zoom on it there.
     */
    @Test void onMacOsCtrlWheelIsThePages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view(NavigationButtonTest.MAC);
            for (double notches : new double[] { -1, -3, 1 }) {
                var turn = wheel(view, InputEvent.CTRL_DOWN_MASK, notches);
                view.processMouseWheelEvent(turn);
                assertThat(turn.isConsumed()).as(turn.paramString()).isFalse();
            }
            assertThat(magnified).isEmpty();
        });
    }

    /**
     * A wheel without Ctrl is the page's, and so is one with Ctrl and Shift, as Java reports a horizontal wheel or a
     * tilt: Chromium scrolls the page sideways. A wheel the window took is neither the page's nor the item's.
     */
    @Test void aWheelWithoutCtrlOrWithShiftIsThePagesAndOneTheWindowTookIsNeither() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view(NavigationButtonTest.WINDOWS);
            for (int modifiers : new int[] { 0, InputEvent.SHIFT_DOWN_MASK, InputEvent.META_DOWN_MASK, InputEvent.ALT_DOWN_MASK,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK }) {
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

    /**
     * Spec section 8: Chromium downloads an Alt-click on a link, and Jasper loads it in place, so the press, its drags
     * and its release reach the page without Alt, their other keys, buttons, place and click count unchanged; the menu
     * key is Cmd on macOS and Ctrl elsewhere, so the other one does not count. What comes after the release has Alt again.
     * Neither AWT's extended Alt nor its old one, which only the deprecated {@code getModifiers()} reads, is left.
     */
    @SuppressWarnings("deprecation")
    @Test void anAltClickOnALinkReachesThePageWithoutAlt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var platform : List.of(List.of(NavigationButtonTest.WINDOWS, InputEvent.META_DOWN_MASK),
                    List.of(NavigationButtonTest.LINUX, InputEvent.META_DOWN_MASK),
                    List.of(NavigationButtonTest.MAC, InputEvent.CTRL_DOWN_MASK))) {
                String os = (String) platform.get(0);
                for (int also : new int[] { 0, InputEvent.SHIFT_DOWN_MASK, (Integer) platform.get(1) }) {
                    var sent = new ArrayList<MouseEvent>();
                    var view = view(os);
                    view.useMouse(sent::add);
                    view.setHoverUrl("https://example.com/report.pdf");
                    int keys = InputEvent.ALT_DOWN_MASK | also;
                    long when = System.currentTimeMillis();
                    view.processMouseEvent(event(view, MouseEvent.MOUSE_PRESSED, when, keys | InputEvent.BUTTON1_DOWN_MASK,
                        MouseEvent.BUTTON1, 12, 2));
                    view.processMouseMotionEvent(event(view, MouseEvent.MOUSE_DRAGGED, when + 1, keys | InputEvent.BUTTON1_DOWN_MASK,
                        MouseEvent.NOBUTTON, 14, 0));
                    view.processMouseEvent(event(view, MouseEvent.MOUSE_RELEASED, when + 2, keys, MouseEvent.BUTTON1, 14, 2));
                    view.processMouseMotionEvent(event(view, MouseEvent.MOUSE_MOVED, when + 3, keys, MouseEvent.NOBUTTON, 15, 0));
                    String as = os + " with " + InputEvent.getModifiersExText(also);
                    assertThat(sent).as(as).hasSize(4);
                    assertThat(sent.subList(0, 3)).as(as).allSatisfy(event -> {
                        assertThat(event.getModifiersEx() & InputEvent.ALT_DOWN_MASK).as(event.paramString()).isZero();
                        assertThat(event.getModifiers() & ALT_MASK).as(event.paramString()).isZero();
                    });
                    assertThat(sent.subList(0, 3)).extracting(MouseEvent::getID, MouseEvent::getModifiersEx, MouseEvent::getButton,
                        MouseEvent::getX, MouseEvent::getClickCount).as(as).containsExactly(
                        tuple(MouseEvent.MOUSE_PRESSED, also | InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1, 12, 2),
                        tuple(MouseEvent.MOUSE_DRAGGED, also | InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON, 14, 0),
                        tuple(MouseEvent.MOUSE_RELEASED, also, MouseEvent.BUTTON1, 14, 2));
                    assertThat(sent.getLast().getModifiersEx()).as(as + ": after the release").isEqualTo(keys);
                }
            }
        });
    }

    /**
     * An Alt-click over no link, an Alt-click with the menu key, which opens a new tab, and Alt with another button reach
     * the page as they are: only a plain Alt-click on a link is Chromium's download.
     */
    @Test void anAltClickWithNothingUnderThePointerKeepsAlt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var os : List.of(NavigationButtonTest.WINDOWS, NavigationButtonTest.MAC)) {
                int menuKey = os.equals(NavigationButtonTest.MAC) ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                var sent = new ArrayList<MouseEvent>();
                var view = view(os);
                view.useMouse(sent::add);
                for (MouseEvent event : click(view, MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK)) view.processMouseEvent(event);
                view.setHoverUrl("https://example.com/report.pdf");
                for (MouseEvent event : click(view, MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK | menuKey)) view.processMouseEvent(event);
                for (MouseEvent event : click(view, MouseEvent.BUTTON3, InputEvent.ALT_DOWN_MASK)) view.processMouseEvent(event);
                view.setHoverUrl("");
                for (MouseEvent event : click(view, MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK)) view.processMouseEvent(event);
                assertThat(sent).as(os).hasSize(12).allSatisfy(event ->
                    assertThat(event.getModifiersEx() & InputEvent.ALT_DOWN_MASK).as(event.paramString()).isNotZero());
            }
        });
    }

    /** The last press keeps the keys that were down, Alt included, so 4a's gestures still read an Alt-click as a click. */
    @Test void theLastPressKeepsAlt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var sent = new ArrayList<MouseEvent>();
            var view = view();
            view.useMouse(sent::add);
            view.setHoverUrl("https://example.com/report.pdf");
            for (MouseEvent event : click(view, MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK)) view.processMouseEvent(event);
            assertThat(sent.getFirst().getModifiersEx() & InputEvent.ALT_DOWN_MASK).as("the page's press").isZero();
            assertThat(view.lastPress().button()).isEqualTo(MouseEvent.BUTTON1);
            assertThat(view.lastPress().modifiersEx()).isEqualTo(InputEvent.ALT_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK);
            assertThat(view.activation().active(view.lastPress().whenMillis())).as("and it activates the page").isTrue();
        });
    }

    private PageView view() {
        return view(NavigationButtonTest.WINDOWS);
    }

    /** A view whose buttons are Windows' and whose wheel follows {@code os}'s rule. */
    private PageView view(String os) {
        var view = new PageView(event -> false, shown -> { }, rate -> { }, key -> { },
            button -> NavigationButton.of(button, NavigationButtonTest.WINDOWS, NavigationButtonTest.W_TOOLKIT),
            (delay, task) -> () -> { });
        view.useOsName(os);
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
        return click(view, button, 0);
    }

    /** A button's press, release and click over the view, with {@code keys}, AWT's extended modifiers, held. */
    private static List<MouseEvent> click(PageView view, int button, int keys) {
        long when = System.currentTimeMillis();
        int down = button == MouseEvent.BUTTON1 ? InputEvent.BUTTON1_DOWN_MASK : 0;
        return List.of(event(view, MouseEvent.MOUSE_PRESSED, when, keys | down, button),
            event(view, MouseEvent.MOUSE_RELEASED, when + 1, keys, button), event(view, MouseEvent.MOUSE_CLICKED, when + 1, keys, button));
    }

    private static MouseEvent event(PageView view, int id, long when, int modifiers, int button) {
        return event(view, id, when, modifiers, button, 10, 1);
    }

    /** A mouse event over the view at ({@code x}, 10), whose button is {@code button} whatever headless AWT allows. */
    private static MouseEvent event(PageView view, int id, long when, int modifiers, int button, int x, int clicks) {
        return new MouseEvent(view, id, when, modifiers, x, 10, clicks, false, MouseEvent.NOBUTTON) {
            @Override public int getButton() { return button; }
        };
    }
}
