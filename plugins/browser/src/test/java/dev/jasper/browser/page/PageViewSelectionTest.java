package dev.jasper.browser.page;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copy on select's gestures (spec section 15; F12): the view tells its selection listener once, 100 ms after the last
 * gesture the page got. A gesture is a left release after a drag past the platform's drag threshold, the release of a
 * double or triple click, the release that ends Shift with a key that moves the caret, and the release that ends the
 * platform's Select All. A plain click, any other button, a key without Shift and anything the window took are none, and
 * so is whatever the page's scripts do to the selection. The view's timer is the test's, on a clock the test turns.
 */
class PageViewSelectionTest {
    private final Clock clock = new Clock();
    private int told;

    @Test void aLeftReleaseAfterADragFiresOnce100MsLaterAndARightOneNever() throws Exception {
        onUi(() -> {
            var view = listening(view(event -> false));
            drag(view, MouseEvent.BUTTON1, 40);
            assertThat(clock.pending()).as("one timer, armed by the release").containsExactly(Duration.ofMillis(100));
            clock.advance(99);
            assertThat(told).isZero();
            clock.advance(1);
            assertThat(told).isEqualTo(1);
            clock.advance(1_000);
            assertThat(told).as("once").isEqualTo(1);
            for (int button : new int[] {MouseEvent.BUTTON2, MouseEvent.BUTTON3}) drag(view, button, 40);
            clock.advance(1_000);
            assertThat(told).as("other buttons select nothing").isEqualTo(1);
        });
    }

    /** F12: a plain click, and a press that moves no further than the drag threshold, are no gesture. */
    @Test void aPlainClickOrAPressThatBarelyMovesIsNoGesture() throws Exception {
        onUi(() -> {
            var view = listening(view(event -> false));
            click(view, 1);
            drag(view, MouseEvent.BUTTON1, 1);
            clock.advance(1_000);
            assertThat(told).isZero();
            assertThat(clock.asked).as("no timer armed").isZero();
        });
    }

    @Test void aDoubleAndATripleClickAreGestures() throws Exception {
        onUi(() -> {
            var view = listening(view(event -> false));
            click(view, 1);
            click(view, 2);
            clock.advance(100);
            assertThat(told).as("the double click").isEqualTo(1);
            click(view, 3);
            clock.advance(100);
            assertThat(told).as("the triple click").isEqualTo(2);
        });
    }

    @Test void threeShiftRightReleases30MsApartFireOnceAndAReleaseWithoutShiftNever() throws Exception {
        onUi(() -> {
            var view = listening(view(event -> false));
            for (int at = 0; at < 3; at++) {
                stroke(view, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
                clock.advance(30);
            }
            assertThat(clock.pending()).as("each release restarts the one timer").hasSize(1);
            clock.advance(69);
            assertThat(told).isZero();
            clock.advance(1);
            assertThat(told).isEqualTo(1);
            stroke(view, KeyEvent.VK_RIGHT, 0);
            clock.advance(1_000);
            assertThat(told).as("Right without Shift").isEqualTo(1);
        });
    }

    /** Every key that moves the caret extends the selection with Shift held, alongside the word and line modifiers. */
    @Test void shiftWithAnyKeyThatMovesTheCaretIsAGesture() throws Exception {
        onUi(() -> {
            var view = listening(view(event -> false));
            int[] keys = {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_KP_LEFT,
                KeyEvent.VK_KP_RIGHT, KeyEvent.VK_KP_UP, KeyEvent.VK_KP_DOWN, KeyEvent.VK_HOME, KeyEvent.VK_END,
                KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN};
            for (int key : keys) {
                stroke(view, key, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
                clock.advance(100);
            }
            assertThat(told).isEqualTo(keys.length);
        });
    }

    /**
     * The decision recorded for F12: Shift with a key that types or moves the focus selects nothing, so typing capitals
     * asks nothing of the page, and Shift+Tab into a filled field, which Chromium selects, copies nothing.
     */
    @Test void shiftWithAKeyThatTypesOrMovesTheFocusIsNoGesture() throws Exception {
        onUi(() -> {
            var view = listening(view(event -> false));
            for (int key : new int[] {KeyEvent.VK_A, KeyEvent.VK_TAB, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE, KeyEvent.VK_1})
                stroke(view, key, InputEvent.SHIFT_DOWN_MASK);
            clock.advance(1_000);
            assertThat(told).isZero();
        });
    }

    /** A user who lets go of Shift before the arrow still finished a selection: the first release ends it, once. */
    @Test void releasingShiftBeforeTheArrowFiresOnce() throws Exception {
        onUi(() -> {
            var view = listening(view(event -> false));
            long when = System.currentTimeMillis();
            view.processKeyEvent(key(view, KeyEvent.KEY_PRESSED, when, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_SHIFT));
            view.processKeyEvent(key(view, KeyEvent.KEY_PRESSED, when, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_RIGHT));
            view.processKeyEvent(key(view, KeyEvent.KEY_RELEASED, when + 1, 0, KeyEvent.VK_SHIFT));
            clock.advance(100);
            view.processKeyEvent(key(view, KeyEvent.KEY_RELEASED, when + 2, 0, KeyEvent.VK_RIGHT));
            clock.advance(1_000);
            assertThat(told).isEqualTo(1);
        });
    }

    /** Select All is Cmd+A on macOS and Ctrl+A on Windows and Linux; the other platform's chord is none. */
    @Test void selectAllIsAGestureOnEachPlatform() throws Exception {
        onUi(() -> {
            for (String os : List.of(NavigationButtonTest.MAC, NavigationButtonTest.WINDOWS, NavigationButtonTest.LINUX)) {
                told = 0;
                var view = listening(view(event -> false));
                view.useOsName(os);
                boolean mac = os.equals(NavigationButtonTest.MAC);
                stroke(view, KeyEvent.VK_A, mac ? InputEvent.CTRL_DOWN_MASK : InputEvent.META_DOWN_MASK);
                stroke(view, KeyEvent.VK_A, (mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK) | InputEvent.ALT_DOWN_MASK);
                clock.advance(1_000);
                assertThat(told).as(os + ": not Select All").isZero();
                stroke(view, KeyEvent.VK_A, mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK);
                clock.advance(100);
                assertThat(told).as(os).isEqualTo(1);
            }
        });
    }

    /** What the window took never reached the page, so it selected nothing there. */
    @Test void whatTheWindowTookIsNoGesture() throws Exception {
        onUi(() -> {
            var view = listening(view(event -> event.getID() == KeyEvent.KEY_PRESSED));
            stroke(view, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
            long when = System.currentTimeMillis();
            view.processMouseEvent(mouse(view, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON1_DOWN_MASK, 10, 1, MouseEvent.BUTTON1));
            view.processMouseMotionEvent(mouse(view, MouseEvent.MOUSE_DRAGGED, when + 1, InputEvent.BUTTON1_DOWN_MASK, 50, 1,
                MouseEvent.NOBUTTON));
            MouseEvent taken = mouse(view, MouseEvent.MOUSE_RELEASED, when + 2, 0, 50, 1, MouseEvent.BUTTON1);
            taken.consume();
            view.processMouseEvent(taken);
            view.processMouseEvent(mouse(view, MouseEvent.MOUSE_RELEASED, when + 3, 0, 50, 2, MouseEvent.BUTTON1));
            clock.advance(1_000);
            assertThat(told).as("a key a binding took, a release the window took, and a release whose press it took").isZero();
        });
    }

    /** F34: a canvas nothing listens to, as a Markdown preview's and DevTools', never arms the timer. */
    @Test void aViewWithoutAListenerArmsNoTimer() throws Exception {
        onUi(() -> {
            var view = view(event -> false);
            drag(view, MouseEvent.BUTTON1, 40);
            click(view, 2);
            stroke(view, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
            assertThat(clock.asked).isZero();
        });
    }

    @Test void endingTheListeningDropsAGestureNotYetTold() throws Exception {
        onUi(() -> {
            var view = listening(view(event -> false));
            drag(view, MouseEvent.BUTTON1, 40);
            view.endSelectionGestures();
            assertThat(clock.pending()).isEmpty();
            clock.advance(1_000);
            drag(view, MouseEvent.BUTTON1, 40);
            assertThat(told).isZero();
            assertThat(clock.asked).as("no timer armed after the end").isEqualTo(1);
        });
    }

    private PageView view(Predicate<KeyEvent> taken) {
        var view = new PageView(taken, shown -> { }, rate -> { }, key -> { }, NavigationButton::of, clock::later);
        view.useOsName(NavigationButtonTest.MAC);
        return view;
    }

    private PageView listening(PageView view) {
        view.onSelectionGesture(() -> told++);
        return view;
    }

    /** Presses {@code button} over the view, moves it {@code distance} pixels to the right with it held, and lets go. */
    private static void drag(PageView view, int button, int distance) {
        long when = System.currentTimeMillis();
        view.processMouseEvent(mouse(view, MouseEvent.MOUSE_PRESSED, when, InputEvent.getMaskForButton(button), 10, 1, button));
        view.processMouseMotionEvent(mouse(view, MouseEvent.MOUSE_DRAGGED, when + 1, InputEvent.getMaskForButton(button), 10 + distance,
            1, MouseEvent.NOBUTTON));
        view.processMouseEvent(mouse(view, MouseEvent.MOUSE_RELEASED, when + 2, 0, 10 + distance, 1, button));
        view.processMouseEvent(mouse(view, MouseEvent.MOUSE_CLICKED, when + 2, 0, 10 + distance, 1, button));
    }

    /** The left button's press, release and click over the view, the {@code count}th of a run of clicks. */
    private static void click(PageView view, int count) {
        long when = System.currentTimeMillis();
        view.processMouseEvent(mouse(view, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON1_DOWN_MASK, 10, count, MouseEvent.BUTTON1));
        view.processMouseEvent(mouse(view, MouseEvent.MOUSE_RELEASED, when + 1, 0, 10, count, MouseEvent.BUTTON1));
        view.processMouseEvent(mouse(view, MouseEvent.MOUSE_CLICKED, when + 1, 0, 10, count, MouseEvent.BUTTON1));
    }

    private static MouseEvent mouse(PageView view, int id, long when, int modifiers, int x, int clicks, int button) {
        return new MouseEvent(view, id, when, modifiers, x, 10, clicks, false, button);
    }

    /** {@code code}'s press and release with {@code modifiers} held, as AWT delivers them. */
    private static void stroke(PageView view, int code, int modifiers) {
        long when = System.currentTimeMillis();
        view.processKeyEvent(key(view, KeyEvent.KEY_PRESSED, when, modifiers, code));
        view.processKeyEvent(key(view, KeyEvent.KEY_RELEASED, when + 1, modifiers, code));
    }

    private static KeyEvent key(PageView view, int id, long when, int modifiers, int code) {
        return new KeyEvent(view, id, when, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }

    private static void onUi(Runnable task) throws Exception {
        SwingUtilities.invokeAndWait(task);
    }

    /** A timer on a clock the test turns: each task runs once the clock reaches its time, unless cancelled first. */
    private static final class Clock {
        private final List<Timed> timers = new ArrayList<>();
        private long now;
        int asked;

        private record Timed(long due, Duration delay, Runnable task) { }

        Runnable later(Duration delay, Runnable task) {
            asked++;
            var timed = new Timed(now + delay.toMillis(), delay, task);
            timers.add(timed);
            return () -> timers.remove(timed);
        }

        List<Duration> pending() {
            return timers.stream().map(Timed::delay).toList();
        }

        void advance(long millis) {
            now += millis;
            for (Timed due : List.copyOf(timers)) {
                if (due.due() <= now && timers.remove(due)) due.task().run();
            }
        }
    }
}
