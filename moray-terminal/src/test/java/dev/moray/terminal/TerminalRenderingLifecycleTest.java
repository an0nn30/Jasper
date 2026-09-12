package dev.moray.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalRenderingLifecycleTest {
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;
    private KeyboardFocusManager oldFocus;
    private RepaintManager oldRepaint;
    private boolean focused;
    private final AtomicInteger repaints = new AtomicInteger();

    @BeforeEach void setUp() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
        onEdt(() -> {
            view = new TerminalView(session, TerminalOptions.defaults());
            view.setSize(view.getPreferredSize());
            oldFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                @Override public Component getFocusOwner() { return focused ? view : null; }
            });
            oldRepaint = RepaintManager.currentManager(view);
            RepaintManager.setCurrentManager(new RepaintManager() {
                @Override public void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
                    if (component == view) repaints.incrementAndGet();
                }
            });
            view.addNotify();
        });
        onEdt(() -> {});
    }

    @AfterEach void tearDown() throws Exception {
        onEdt(() -> {
            view.removeNotify();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(oldFocus);
            RepaintManager.setCurrentManager(oldRepaint);
        });
        session.close();
    }

    @Test void idleUnfocusedViewStopsSchedulingAfterItsDirtyFrame() throws Exception {
        onEdt(() -> {
            tick("frameTimer");
            repaints.set(0);
            tick("blinkTimer");
            assertThat(repaints).hasValue(0);
            assertThat(timer("frameTimer").isRunning()).isFalse();
            assertThat(timer("blinkTimer").isRunning()).isFalse();
        });
    }

    @Test void hiddenOutputDoesNotRepaintButShowingRendersTheLatestText() throws Exception {
        onEdt(() -> view.setVisible(false));
        onEdt(() -> repaints.set(0));
        connector.feed("hidden-output");
        Await.until(() -> session.snapshot().lineText(0).equals("hidden-output"), "hidden output parsed");
        onEdt(() -> {
            tick("frameTimer");
            tick("blinkTimer");
            assertThat(repaints).hasValue(0);
            view.setVisible(true);
        });
        onEdt(() -> {
            tick("frameTimer");
            assertThat(repaints.get()).isPositive();
            assertThat(timer("frameTimer").isRunning()).isFalse();
            assertThat(session.snapshot().lineText(0)).isEqualTo("hidden-output");
        });
    }

    @Test void focusedCursorOnlyBlinksWhileVisibleAndRestoresOnFocusLoss() throws Exception {
        onEdt(() -> {
            focus(true);
            int on = cursorPixel();
            tick("blinkTimer");
            assertThat(cursorPixel()).isNotEqualTo(on);
            focus(false);
            int unfocused = cursorPixel();
            repaints.set(0);
            tick("blinkTimer");
            assertThat(cursorPixel()).isEqualTo(unfocused);
            assertThat(repaints).hasValue(0);
            assertThat(timer("blinkTimer").isRunning()).isFalse();
            focus(true);
        });
        connector.feed("\033[?25l");
        Await.until(() -> !session.snapshot().cursorVisible(), "cursor hidden by application");
        onEdt(() -> {
            tick("frameTimer");
            repaints.set(0);
            tick("blinkTimer");
            assertThat(repaints).hasValue(0);
            assertThat(timer("blinkTimer").isRunning()).isFalse();
        });
        connector.feed("\033[?25h\033[2 q");
        Await.until(() -> session.snapshot().cursorVisible()
            && session.snapshot().cursorShape() == com.jediterm.terminal.CursorShape.STEADY_BLOCK, "steady cursor");
        onEdt(() -> { tick("frameTimer"); assertThat(timer("blinkTimer").isRunning()).isFalse(); });
        connector.feed("\033[1 q");
        Await.until(() -> session.snapshot().cursorShape() == com.jediterm.terminal.CursorShape.BLINK_BLOCK, "blinking cursor");
        onEdt(() -> { tick("frameTimer"); assertThat(timer("blinkTimer").isRunning()).isTrue(); });
        connector.finish();
        Await.until(view::exited, "view exit");
        onEdt(() -> { tick("frameTimer"); assertThat(timer("blinkTimer").isRunning()).isFalse(); });
    }

    @Test void outputBurstsCoalesceAndLaterOutputSchedulesAnotherFrame() throws Exception {
        for (String text : new String[] {"a".repeat(200) + "FIRST", "\r\nSECOND"}) {
            onEdt(() -> {
                tick("frameTimer");
                repaints.set(0);
                try {
                    connector.feed(text);
                    Await.until(() -> session.snapshot().lineText(3).endsWith(text.endsWith("FIRST") ? "FIRST" : "SECOND"),
                        "reader finished burst while EDT is held");
                } catch (Exception failure) { throw new AssertionError(failure); }
                assertThat(repaints).hasValue(0);
            });
            onEdt(() -> {
                tick("frameTimer");
                assertThat(repaints).hasValue(1);
                tick("frameTimer");
                assertThat(repaints).hasValue(1);
                assertThat(timer("frameTimer").isRunning()).isFalse();
            });
        }
    }

    @Test void pendingWrapCursorAtLastColumnStillBlinks() throws Exception {
        connector.feed("x".repeat(20));
        Await.until(() -> session.snapshot().lineText(0).length() == 20, "last column written");
        onEdt(() -> {
            focus(true);
            assertThat(timer("blinkTimer").isRunning()).isTrue();
            assertThat(session.snapshot().cursorColumn()).isEqualTo(20);
            int x = view.getWidth() - view.getWidth() / 20 + 1;
            int on = pixelAt(x, 1);
            tick("blinkTimer");
            assertThat(pixelAt(x, 1)).isNotEqualTo(on);
        });
    }

    @Test void scrolledOutCursorStopsBlinkingAndReturningToLiveOutputResumesIt() throws Exception {
        connector.feed("old\r\n2\r\n3\r\n4\r\n5\r\n6");
        Await.until(() -> session.snapshot().lineText(3).equals("6"), "history available");
        onEdt(() -> {
            focus(true);
            assertThat(timer("blinkTimer").isRunning()).isTrue();
            view.find("old", false, true);
            cursorPixel(); // repaint reconciles the visible cursor after find navigation
            assertThat(timer("blinkTimer").isRunning()).isFalse();
            view.paste("back to live");
            cursorPixel();
            assertThat(timer("blinkTimer").isRunning()).isTrue();
        });
    }

    @Test void detachedCallbacksCannotRepaintOrDeliverRowInvalidations() throws Exception {
        var invalidations = new AtomicInteger();
        onEdt(() -> {
            view.setFindResultListener(result -> invalidations.incrementAndGet());
            session.clearScrollback(); // queues the old attachment's reset callback
            view.removeNotify();
            repaints.set(0);
        });
        onEdt(() -> {
            tick("frameTimer");
            tick("blinkTimer");
            assertThat(invalidations).hasValue(0);
            assertThat(repaints).hasValue(0);
        });
    }

    private void focus(boolean value) {
        focused = value;
        var event = new FocusEvent(view, value ? FocusEvent.FOCUS_GAINED : FocusEvent.FOCUS_LOST);
        for (var listener : view.getFocusListeners()) {
            if (value) listener.focusGained(event); else listener.focusLost(event);
        }
    }

    private int cursorPixel() { return pixelAt(3, 3); }

    private int pixelAt(int x, int y) {
        var image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { view.paint(graphics); } finally { graphics.dispose(); }
        return image.getRGB(x, y);
    }

    private Timer timer(String name) {
        try {
            var field = TerminalView.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Timer) field.get(view);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private void tick(String name) {
        var timer = timer(name);
        for (var listener : timer.getActionListeners()) listener.actionPerformed(new ActionEvent(timer, 0, ""));
    }

    private static void onEdt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
}
