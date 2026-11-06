package dev.jasper.browser.page;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page that does not show is told it is hidden and draws one frame a second; showing again restores 60. The view's
 * page here is a record of what it is told, because a Chromium browser cannot be made without Chromium. Headless, a
 * lightweight parent given a peer shows, so adding the view to it and removing it fire the view's
 * {@code SHOWING_CHANGED} events; nothing is put on screen.
 */
class PageViewHiddenTest {
    private final List<Boolean> visibility = new ArrayList<>();
    private final List<Integer> frameRates = new ArrayList<>();

    @Test void addingTheViewToAShownParentWakesItsPageAndRemovingItQuietensIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view();
            var parent = new JPanel();
            parent.addNotify();
            try {
                parent.add(view);
                assertThat(view.isShowing()).as("the view shows in its shown parent").isTrue();
                parent.remove(view);
                assertThat(view.isShowing()).isFalse();
            } finally {
                parent.removeNotify();
            }
            assertThat(visibility).containsExactly(true, false);
            assertThat(frameRates).containsExactly(60, 1);
        });
    }

    @Test void hidingTheParentQuietensThePageAndShowingItAgainWakesIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view();
            var parent = new JPanel();
            parent.addNotify();
            try {
                parent.add(view);
                parent.setVisible(false);
                assertThat(view.isShowing()).as("its tab hidden").isFalse();
                parent.setVisible(true);
                assertThat(view.isShowing()).isTrue();
            } finally {
                parent.removeNotify();
            }
            assertThat(visibility).containsExactly(true, false, true, false);
            assertThat(frameRates).containsExactly(60, 1, 60, 1);
        });
    }

    /** JCEF drops what a page is told before its native browser exists, so the view tells it again once it does. */
    @Test void onceItsNativeBrowserExistsThePageIsToldAgainWhetherTheViewShows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = view();
            var parent = new JPanel();
            parent.addNotify();
            try {
                parent.add(view);
                view.browserCreated();
                assertThat(visibility).as("shown").containsExactly(true, true);
                assertThat(frameRates).containsExactly(60, 60);
                parent.setVisible(false);
                view.browserCreated();
                assertThat(visibility).as("then hidden").containsExactly(true, true, false, false);
                assertThat(frameRates).containsExactly(60, 60, 1, 1);
            } finally {
                parent.removeNotify();
            }
        });
    }

    private PageView view() {
        return new PageView(event -> false, visibility::add, frameRates::add, event -> { }, NavigationButton::of,
            (delay, task) -> () -> { });
    }
}
