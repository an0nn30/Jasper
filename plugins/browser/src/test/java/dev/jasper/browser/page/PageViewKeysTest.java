package dev.jasper.browser.page;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jasper's shortcuts are key bindings, which see each key before the page does: a press a binding takes reaches the
 * page with none of the typed and released events that follow it, and every other key reaches the page whole. Headless
 * there is no window, so some tests say which events a binding took, and the rest bind a key on a parent panel and let
 * Swing's own pass decide. The page is a record of what it is handed.
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

    /**
     * Swing's own pass decides, as in Jasper's window: an ancestor's binding whose action accepts the key runs and
     * consumes it, so the page gets neither the press nor its tail.
     */
    @Test void aKeyAnAncestorsBindingAcceptsRunsTheBindingAndNoneOfItReachesThePage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ran = new ArrayList<String>();
            var view = view(KeyEvent::isConsumed);
            bindOpenLocation(view, true, ran);
            var keys = stroke(view, KeyEvent.VK_L, 'l', MENU);
            keys.forEach(view::processKeyEvent);
            assertThat(ran).as("the binding ran once, on the press").containsExactly("open location");
            assertThat(page).isEmpty();
        });
    }

    /** A binding whose action declines the key (a Web menu key outside a browser item) leaves it all to the page. */
    @Test void aKeyAnAncestorsBindingDeclinesReachesThePageWithItsTail() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ran = new ArrayList<String>();
            var view = view(KeyEvent::isConsumed);
            bindOpenLocation(view, false, ran);
            var keys = stroke(view, KeyEvent.VK_L, 'l', MENU);
            keys.forEach(view::processKeyEvent);
            assertThat(ran).isEmpty();
            assertThat(page).containsExactlyElementsOf(keys);
        });
    }

    /**
     * A key press the page gets activates it, as a mouse press does: the view records when, for the page's popups. A key
     * Jasper's shortcuts took, Escape, and typed and released events activate nothing, as Chromium counts them.
     */
    @Test void aKeyPressThePageGetsOtherThanEscapeIsRecordedAsItsLastActivatingKey() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view(event -> event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_L);
            assertThat(view.lastKeyMillis()).as("no key yet").isEqualTo(Long.MIN_VALUE);
            view.processKeyEvent(new KeyEvent(view, KeyEvent.KEY_PRESSED, 1_000, 0, KeyEvent.VK_A, 'a'));
            assertThat(view.lastKeyMillis()).isEqualTo(1_000);
            view.processKeyEvent(new KeyEvent(view, KeyEvent.KEY_TYPED, 2_000, 0, KeyEvent.VK_UNDEFINED, 'a'));
            view.processKeyEvent(new KeyEvent(view, KeyEvent.KEY_RELEASED, 3_000, 0, KeyEvent.VK_A, 'a'));
            assertThat(view.lastKeyMillis()).as("typed and released").isEqualTo(1_000);
            view.processKeyEvent(new KeyEvent(view, KeyEvent.KEY_PRESSED, 4_000, MENU, KeyEvent.VK_L, 'l'));
            assertThat(view.lastKeyMillis()).as("a key a shortcut took").isEqualTo(1_000);
            view.processKeyEvent(new KeyEvent(view, KeyEvent.KEY_PRESSED, 5_000, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
            assertThat(view.lastKeyMillis()).as("Escape").isEqualTo(1_000);
            view.processKeyEvent(new KeyEvent(view, KeyEvent.KEY_PRESSED, 6_000, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_ENTER, '\n'));
            assertThat(view.lastKeyMillis()).isEqualTo(6_000);
        });
    }

    /** Puts {@code view} in a panel binding Cmd+L to an action that takes the key only when {@code accepts}. */
    private static void bindOpenLocation(PageView view, boolean accepts, List<String> ran) {
        var parent = new JPanel();
        parent.add(view);
        parent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_L, MENU), "open-location");
        parent.getActionMap().put("open-location", new AbstractAction() {
            @Override public boolean accept(Object sender) { return accepts; }
            @Override public void actionPerformed(ActionEvent event) { ran.add("open location"); }
        });
    }

    private PageView view(Predicate<KeyEvent> taken) {
        return new PageView(taken, shown -> { }, rate -> { }, page::add, NavigationButton::of);
    }

    /** A key's press, typed character and release, as AWT delivers them. */
    private static List<KeyEvent> stroke(PageView view, int code, char typed, int modifiers) {
        long when = System.currentTimeMillis();
        return List.of(new KeyEvent(view, KeyEvent.KEY_PRESSED, when, modifiers, code, typed),
            new KeyEvent(view, KeyEvent.KEY_TYPED, when, modifiers, KeyEvent.VK_UNDEFINED, typed),
            new KeyEvent(view, KeyEvent.KEY_RELEASED, when + 1, modifiers, code, typed));
    }
}
