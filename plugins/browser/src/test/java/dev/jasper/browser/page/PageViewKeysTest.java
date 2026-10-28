package dev.jasper.browser.page;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jasper's shortcuts are key bindings, which see each key before the page does: a press a binding takes reaches the
 * page with none of the typed and released events that follow it, and every other key reaches the page whole. Headless,
 * no window holds the bindings, so the test says which events a binding took; the page is a record of what it is handed.
 */
class PageViewKeysTest {
    private static final int MENU = InputEvent.META_DOWN_MASK;
    private final List<KeyEvent> page = new ArrayList<>();

    @Test void aPressABindingTakesAndItsTypedAndReleasedTailNeverReachThePage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view(event -> event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_L);
            var keys = stroke(view, KeyEvent.VK_L, 'l', MENU);
            keys.forEach(view::processKeyEvent);
            assertThat(page).isEmpty();
            assertThat(keys).allSatisfy(event -> assertThat(event.isConsumed()).as(event.paramString()).isTrue());
        });
    }

    @Test void aKeyNoBindingTakesReachesThePageWithItsTypedAndReleasedEvents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view(event -> false);
            var keys = stroke(view, KeyEvent.VK_A, 'a', 0);
            keys.forEach(view::processKeyEvent);
            assertThat(page).containsExactlyElementsOf(keys);
        });
    }

    @Test void theNextPressAfterATakenOneIsThePagesAgain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view(event -> event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_R
                && (event.getModifiersEx() & MENU) != 0);
            var reload = stroke(view, KeyEvent.VK_R, 'r', MENU);
            var typing = stroke(view, KeyEvent.VK_R, 'r', 0);
            reload.forEach(view::processKeyEvent);
            typing.forEach(view::processKeyEvent);
            assertThat(page).as("a plain R after Cmd+R").containsExactlyElementsOf(typing);
        });
    }

    @Test void aTakenKeysReleaseAfterAnotherKeysPressIsStillTheBindings() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view(event -> event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_L);
            var taken = stroke(view, KeyEvent.VK_L, 'l', MENU);
            var other = stroke(view, KeyEvent.VK_B, 'b', 0);
            view.processKeyEvent(taken.get(0));
            view.processKeyEvent(taken.get(1));
            other.forEach(view::processKeyEvent);
            view.processKeyEvent(taken.get(2));
            assertThat(page).as("B pressed while L is held, then L released").containsExactlyElementsOf(other);
        });
    }

    private PageView view(Predicate<KeyEvent> taken) {
        return new PageView(taken, shown -> { }, rate -> { }, page::add);
    }

    /** A key's press, typed character and release, as AWT delivers them. */
    private static List<KeyEvent> stroke(PageView view, int code, char typed, int modifiers) {
        long when = System.currentTimeMillis();
        return List.of(new KeyEvent(view, KeyEvent.KEY_PRESSED, when, modifiers, code, typed),
            new KeyEvent(view, KeyEvent.KEY_TYPED, when, modifiers, KeyEvent.VK_UNDEFINED, typed),
            new KeyEvent(view, KeyEvent.KEY_RELEASED, when + 1, modifiers, code, typed));
    }
}
