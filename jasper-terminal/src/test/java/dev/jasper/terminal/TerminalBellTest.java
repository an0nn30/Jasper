package dev.jasper.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalBellTest {
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;
    private final AtomicInteger sounds = new AtomicInteger();
    private int baseline;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
        onEdt(() -> {
            view = new TerminalView(session, TerminalOptions.defaults());
            view.setSize(view.getPreferredSize());
            view.setBellSound(() -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                sounds.incrementAndGet();
            });
            view.addNotify();
            baseline = backgroundPixel();
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        onEdt(view::removeNotify);
        session.close();
    }

    @Test
    void visualBellPaintsForegroundOverlayAndActualTimerCallbackSettlesIt() throws Exception {
        onEdt(() -> {
            feedBells(1);
            assertThat(backgroundPixel()).isEqualTo(baseline); // reader only queues EDT work
        });
        onEdt(() -> {
            int flashed = backgroundPixel();
            assertThat(flashed).isNotEqualTo(baseline);
            var palette = view.palette();
            int alpha = Math.round(255 * .15f);
            for (int shift : new int[] {16, 8, 0}) {
                int before = baseline >> shift & 255;
                int foreground = palette.foreground().getRGB() >> shift & 255;
                int expected = Math.round((foreground * alpha + before * (255 - alpha)) / 255f);
                assertThat(flashed >> shift & 255).isBetween(expected - 1, expected + 1);
            }
            Timer timer = timer();
            assertThat(timer.isRunning()).isTrue();
            assertThat(timer.isRepeats()).isFalse();
            assertThat(timer.getInitialDelay()).isEqualTo(150);
            finishTimer(timer);
            assertThat(backgroundPixel()).isEqualTo(baseline);
            assertThat(timer.isRunning()).isFalse();
            assertThat(sounds).hasValue(0);
        });
    }

    @Test
    void soundBurstsCoalesceBeforeEdtAndLaterBurstsStillSound() throws Exception {
        onEdt(() -> {
            mode(BellMode.SOUND);
            feedBells(100);
            assertThat(sounds).hasValue(0);
        });
        onEdt(() -> {
            assertThat(sounds).hasValue(1);
            assertThat(backgroundPixel()).isEqualTo(baseline);
            feedBells(2);
        });
        onEdt(() -> assertThat(sounds).hasValue(2));
    }

    @Test
    void noneAndDetachedViewsIgnoreRealBells() throws Exception {
        onEdt(() -> { mode(BellMode.NONE); feedBells(4); });
        onEdt(() -> {
            assertThat(backgroundPixel()).isEqualTo(baseline);
            assertThat(sounds).hasValue(0);
            mode(BellMode.SOUND);
            view.removeNotify();
            feedBells(4);
        });
        onEdt(() -> {
            assertThat(sounds).hasValue(0);
            assertThat(backgroundPixel()).isEqualTo(baseline);
        });
    }

    @Test
    void detachAndReattachRejectQueuedEventsButAcceptFreshOnes() throws Exception {
        for (BellMode bell : new BellMode[] {BellMode.VISUAL, BellMode.SOUND}) {
            onEdt(() -> {
                mode(bell);
                feedBells(10);
                view.removeNotify();
                view.addNotify();
            });
            onEdt(() -> {
                assertThat(backgroundPixel()).isEqualTo(baseline);
                assertThat(sounds).hasValue(0);
                feedBells(1);
            });
            onEdt(() -> {
                if (bell == BellMode.VISUAL) {
                    assertThat(backgroundPixel()).isNotEqualTo(baseline);
                    view.removeNotify();
                    assertThat(backgroundPixel()).isEqualTo(baseline);
                    assertThat(timer().isRunning()).isFalse();
                    view.addNotify();
                } else {
                    assertThat(sounds).hasValue(1);
                }
            });
        }
    }

    @Test
    void readerCallbackAlreadyInFlightCannotAdoptANewAttachment() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch processed = new CountDownLatch(1);
        var blocker = new TerminalSessionListener() {
            @Override public void bell() {
                entered.countDown();
                await(release);
            }
        };
        var probe = new TerminalSessionListener() {
            @Override public void bell() { processed.countDown(); }
        };
        onEdt(() -> {
            mode(BellMode.SOUND);
            view.removeNotify();
            session.addListener(blocker);
            view.addNotify();
            session.addListener(probe);
        });
        try {
            connector.feed("\007");
            await(entered);
            onEdt(() -> {
                view.removeNotify();
                view.addNotify();
            });
        } finally {
            release.countDown();
        }
        await(processed);
        session.removeListener(blocker);
        session.removeListener(probe);
        onEdt(() -> {
            assertThat(sounds).hasValue(0);
            feedBells(1);
        });
        onEdt(() -> assertThat(sounds).hasValue(1));
    }

    @Test
    void modeChangeClearsActiveAndQueuedBellsAndRepeatedVisualEventsReuseTimer() throws Exception {
        onEdt(() -> feedBells(1));
        onEdt(() -> {
            assertThat(backgroundPixel()).isNotEqualTo(baseline);
            Timer timer = timer();
            feedBells(3);
            assertThat(timer()).isSameAs(timer);
        });
        onEdt(() -> {
            assertThat(timer().isRunning()).isTrue();
            assertThat(backgroundPixel()).isNotEqualTo(baseline);
            mode(BellMode.NONE);
            assertThat(backgroundPixel()).isEqualTo(baseline);
            assertThat(timer().isRunning()).isFalse();
            mode(BellMode.SOUND);
            feedBells(1);
            mode(BellMode.NONE);
            mode(BellMode.SOUND);
        });
        onEdt(() -> assertThat(sounds).hasValue(0));
    }

    /** Hold EDT while real reader-thread BEL callbacks finish, so coalescing and stale queues are deterministic. */
    private void feedBells(int count) {
        CountDownLatch received = new CountDownLatch(count);
        var probe = new TerminalSessionListener() {
            @Override public void bell() { received.countDown(); }
        };
        session.addListener(probe);
        try {
            connector.feed("\007".repeat(count));
            assertThat(received.await(5, TimeUnit.SECONDS)).as("reader processed BEL input").isTrue();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        } finally {
            session.removeListener(probe);
        }
    }

    private void mode(BellMode mode) {
        TerminalOptions old = view.options();
        view.applyOptions(new TerminalOptions(old.fontFamily(), old.fontSize(), old.fallbackFonts(), old.ligatures(),
            old.palette(), old.cursorStyle(), old.cursorBlink(), old.optionAsMeta(), old.scrollback(),
            old.copyOnSelect(), old.lineHeight(), mode));
    }

    private int backgroundPixel() {
        var image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { view.paint(g); } finally { g.dispose(); }
        return image.getRGB(view.getWidth() / 2, view.getHeight() / 2) & 0xFFFFFF;
    }

    private Timer timer() {
        try {
            var field = TerminalView.class.getDeclaredField("bellTimer");
            field.setAccessible(true);
            return (Timer) field.get(view);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void finishTimer(Timer timer) {
        for (var listener : timer.getActionListeners()) {
            listener.actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, ""));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).as("reader callback barrier").isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void onEdt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
}
