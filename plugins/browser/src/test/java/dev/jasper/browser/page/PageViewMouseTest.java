package dev.jasper.browser.page;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mouse's back and forward buttons go to the page's item, never to the page; every other button is the page's.
 * The view's buttons here are Windows', whatever the host is. Headless AWT makes no event of a button above 3, so each
 * event here reports its button itself.
 */
class PageViewMouseTest {
    private final List<NavigationButton> told = new ArrayList<>();

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

    private PageView view() {
        var view = new PageView(event -> false, shown -> { }, rate -> { }, key -> { },
            button -> NavigationButton.of(button, NavigationButtonTest.WINDOWS, NavigationButtonTest.W_TOOLKIT));
        view.onNavigationButton(told::add);
        return view;
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
