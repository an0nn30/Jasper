package dev.jasper.terminal;

import com.jediterm.terminal.model.TerminalTextBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TerminalAppIntegrationTest {
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    private static final int PRIMARY = MAC
        ? InputEvent.META_DOWN_MASK
        : InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;

    private final TerminalOptions options = TerminalOptions.defaults();
    private final FontSet fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(),
        options.ligatures());
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
        view = new TerminalView(session, options);
        view.setSize(20 * fonts.cellWidth(), 4 * fonts.cellHeight());
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void selectionPresenceDoesNotWaitForBufferOrExtractSelectedText() throws Exception {
        show("hello", 0, "hello");
        selectByDragging(0, 0, 4, 0, 0);
        TerminalTextBuffer buffer = terminalBuffer();
        buffer.lock();
        try {
            var presence = new java.util.concurrent.FutureTask<>(view::hasSelection);
            SwingUtilities.invokeLater(presence);
            assertThat(presence.get(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally { buffer.unlock(); }
        onEdt(() -> view.handleKey(typed('a', 0)));
        assertThat(view.hasSelection()).isFalse();
    }

    @Test
    void smallPixelAreasAndDirectResizeKeepPtyAndEmulatorAtTheSameMinimum() throws Exception {
        onEdt(() -> {
            view.setFontSize(72);
            java.awt.Dimension minimum = view.getMinimumSize();
            view.setSize(minimum);
            view.resizeSessionToFit();
            assertThat(session.columns()).isEqualTo(5);
            assertThat(session.rows()).isEqualTo(2);
            view.setSize(1, 1);
            view.resizeSessionToFit();
            assertThat(session.columns()).isEqualTo(5);
            assertThat(session.rows()).isEqualTo(2);
            session.resize(1, 1);
            assertThat(connector.lastResize().getColumns()).isEqualTo(5);
            assertThat(connector.lastResize().getRows()).isEqualTo(2);
            assertThat(session.snapshot().width()).isEqualTo(5);
            assertThat(session.snapshot().height()).isEqualTo(2);
        });
    }

    @Test
    void appShortcutsRunBeforeTerminalEncodingAndSuppressTheTypedEvent() {
        view.setShortcutHandler(event -> event.getKeyCode() == KeyEvent.VK_D);

        view.handleKey(pressed(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK, (char) 4));
        view.handleKey(typed((char) 4, InputEvent.CTRL_DOWN_MASK));
        view.handleKey(pressed(KeyEvent.VK_A, 0, KeyEvent.CHAR_UNDEFINED));
        view.handleKey(typed('a', 0));

        assertThat(connector.written()).isEqualTo("a");
    }

    @Test
    void installingAnAppHandlerDisablesStandaloneShortcutsEvenWhenItDeclinesThem() throws Exception {
        AtomicReference<String> copied = new AtomicReference<>();
        view.setClipboard(() -> null, copied::set);
        show("hello", 0, "hello");
        selectByDragging(0, 0, 4, 0, 0);
        view.setShortcutHandler(event -> false);

        view.handleKey(pressed(KeyEvent.VK_C, PRIMARY, KeyEvent.CHAR_UNDEFINED));

        assertThat(copied).hasNullValue();
    }

    @Test
    void aNullAppHandlerRestoresStandaloneShortcuts() throws Exception {
        AtomicReference<String> copied = new AtomicReference<>();
        view.setClipboard(() -> null, copied::set);
        show("hello", 0, "hello");
        selectByDragging(0, 0, 4, 0, 0);
        view.setShortcutHandler(event -> false);
        view.setShortcutHandler(null);

        view.handleKey(pressed(KeyEvent.VK_C, PRIMARY, KeyEvent.CHAR_UNDEFINED));

        assertThat(copied.get()).isEqualTo("hello");
    }

    @Test
    void clearScrollbackPreservesTheLiveScreen() throws Exception {
        connector.feed("1\r\n2\r\n3\r\n4\r\n5\r\n6");
        Await.until(() -> "6".equals(session.snapshot().lineText(3)), "six lines on a four-row screen");
        assertThat(session.snapshot().historyLines()).isPositive();

        view.clearScrollback();

        assertThat(session.snapshot().historyLines()).isZero();
        assertThat(session.snapshot().lineText(3)).isEqualTo("6");
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void popupCallbacksRunOnlyForLocallyOwnedRightClicks() throws Exception {
        AtomicInteger popups = new AtomicInteger();
        view.setContextMenuHandler(event -> popups.incrementAndGet());
        enableSgrMouse();

        view.handleMouse(popupPress(2, 1, 0));
        view.handleMouse(popupRelease(2, 1, 0));
        assertThat(popups).hasValue(0);
        assertThat(connector.written()).isEqualTo("\033[<2;3;2M\033[<2;3;2m");

        view.handleMouse(popupPress(2, 1, InputEvent.SHIFT_DOWN_MASK));
        view.handleMouse(popupRelease(2, 1, 0));
        assertThat(popups).hasValue(1);
        assertThat(connector.written()).isEqualTo("\033[<2;3;2M\033[<2;3;2m");
    }

    @Test
    void enteringTheAlternateScreenInvalidatesSelectionFindAndCount() throws Exception {
        AtomicReference<FindResult> invalidation = new AtomicReference<>();
        show("hello", 0, "hello");
        selectByDragging(0, 0, 4, 0, 0);
        assertThat(view.find("hello", false, false).count()).isOne();
        view.setFindResultListener(invalidation::set);
        view.addNotify();
        try {
            connector.feed("\033[?1049h");

            Await.until(() -> {
                drainEventQueue();
                return view.selectedText().isEmpty() && invalidation.get() != null;
            }, "alternate screen invalidated absolute-row state");
            assertThat(view.findNext()).isEqualTo(new FindResult(0, 0, null));
            assertThat(invalidation.get()).isEqualTo(new FindResult(0, 0, null));
        } finally {
            view.removeNotify();
        }
    }

    @Test
    void alternateBufferTransitionWhileDetachedIsReconciledOnReattach() throws Exception {
        show("hello", 0, "hello");
        selectByDragging(0, 0, 4, 0, 0);
        assertThat(view.find("hello", false, false).count()).isOne();
        onEdt(() -> {
            view.addNotify();
            view.removeNotify();
        });

        connector.feed("\033[?1049h");
        Await.until(session::usingAlternateBuffer, "alternate screen while detached");
        onEdt(view::addNotify);
        try {
            assertThat(view.selectedText()).isEmpty();
            assertThat(view.findNext()).isEqualTo(new FindResult(0, 0, null));
        } finally {
            onEdt(view::removeNotify);
        }
    }

    @Test
    void historyResetWhileDetachedIsReconciledOnReattach() throws Exception {
        connector.feed("1\r\n2\r\n3\r\n4\r\n5\r\n6");
        Await.until(() -> "6".equals(session.snapshot().lineText(3)), "six lines on a four-row screen");
        selectByDragging(0, 0, 0, 1, 0);
        assertThat(view.find("1", false, false).count()).isOne();
        onEdt(() -> {
            view.addNotify();
            view.removeNotify();
        });

        session.clearScrollback();
        onEdt(view::addNotify);
        try {
            assertThat(view.selectedText()).isEmpty();
            assertThat(view.findNext()).isEqualTo(new FindResult(0, 0, null));
        } finally {
            onEdt(view::removeNotify);
        }
    }

    @Test
    void aClearedAsyncFindCannotRestoreStaleResults() throws Exception {
        show("alpha alpha", 0, "alpha alpha");
        AtomicBoolean callbackRan = new AtomicBoolean();
        TerminalTextBuffer buffer = terminalBuffer();
        buffer.lock();
        ThreadPoolExecutor executor;
        try {
            onEdt(() -> view.findAsync("alpha", false, false, result -> callbackRan.set(true)));
            executor = searchExecutor();
            Await.until(() -> executor.getActiveCount() == 1, "asynchronous find blocked on the buffer");
            onEdt(view::clearFind);
        } finally {
            buffer.unlock();
        }
        Await.until(() -> executor.getActiveCount() == 0, "cleared asynchronous find finished");
        drainEventQueue();

        assertThat(callbackRan).isFalse();
        assertThat(view.findNext()).isEqualTo(new FindResult(0, 0, null));
    }

    @Test
    void hidingAnAncestorCancelsRunningSearchAndRejectsItsLateCompletion() throws Exception {
        show("alpha alpha", 0, "alpha alpha");
        var parent = new javax.swing.JPanel();
        onEdt(() -> { parent.add(view); parent.addNotify(); });
        try {
            AtomicBoolean callbackRan = new AtomicBoolean();
            TerminalTextBuffer buffer = terminalBuffer();
            ThreadPoolExecutor executor;
            buffer.lock();
            try {
                onEdt(() -> view.findAsync("alpha", false, false, result -> callbackRan.set(true)));
                executor = searchExecutor();
                Await.until(() -> executor.getActiveCount() == 1, "search blocked on buffer");
                var field = TerminalView.class.getDeclaredField("pendingSearch");
                field.setAccessible(true);
                var pending = (java.util.concurrent.Future<?>) field.get(view);
                onEdt(() -> {
                    parent.setVisible(false);
                    assertThat(view.isVisible()).isTrue();
                    assertThat(view.isShowing()).isFalse();
                    assertThat(pending.isCancelled()).as("hidden running search cancelled best effort").isTrue();
                });
            } finally { buffer.unlock(); }
            Await.until(() -> executor.getActiveCount() == 0, "hidden search finished");
            drainEventQueue();
            assertThat(callbackRan).isFalse();
            assertThat(view.findNext()).isEqualTo(new FindResult(0, 0, null));
        } finally { onEdt(parent::removeNotify); }
    }

    @Test
    void repeatedTemporaryDetachesKeepBlockedSearchWorkerAllocationBounded() throws Exception {
        TerminalTextBuffer buffer = terminalBuffer();
        List<ThreadPoolExecutor> seenExecutors = new ArrayList<>();
        buffer.lock();
        try {
            onEdt(view::addNotify);
            for (int i = 0; i < 4; i++) {
                onEdt(() -> view.findAsync("blocked", false, false, result -> { }));
                ThreadPoolExecutor executor = searchExecutor();
                seenExecutors.add(executor);
                Await.until(() -> executor.getActiveCount() == 1, "search worker blocked on the buffer");
                onEdt(() -> {
                    view.removeNotify();
                    view.addNotify();
                });
            }

            Set<ThreadPoolExecutor> distinct = new HashSet<>(seenExecutors);
            int liveWorkers = distinct.stream().mapToInt(ThreadPoolExecutor::getActiveCount).sum();
            assertThat(liveWorkers).isOne();
        } finally {
            buffer.unlock();
            onEdt(view::removeNotify);
        }
        Set<ThreadPoolExecutor> distinct = new HashSet<>(seenExecutors);
        Await.until(() -> distinct.stream().allMatch(executor -> executor.getActiveCount() == 0),
            "blocked search workers finished");
        Await.until(() -> distinct.stream().allMatch(executor -> executor.getPoolSize() == 0),
            "detached search worker reached its idle timeout");
    }

    @Test
    void onlyTheLatestAsyncFindAppliesAndItsCallbackRunsOnTheEdt() throws Exception {
        show("alpha alpha beta", 0, "alpha alpha beta");
        AtomicBoolean staleCallbackRan = new AtomicBoolean();
        AtomicReference<FindResult> latest = new AtomicReference<>();
        AtomicBoolean latestOnEdt = new AtomicBoolean();

        onEdt(() -> {
            view.findAsync("alpha", false, false, result -> staleCallbackRan.set(true));
            view.findAsync("beta", false, false, result -> {
                latestOnEdt.set(SwingUtilities.isEventDispatchThread());
                latest.set(result);
            });
        });

        Await.until(() -> {
            drainEventQueue();
            return latest.get() != null;
        }, "latest asynchronous find result");
        assertThat(staleCallbackRan).isFalse();
        assertThat(latestOnEdt).isTrue();
        assertThat(latest.get()).isEqualTo(new FindResult(1, 1, null));
        assertThat(view.findNext()).isEqualTo(new FindResult(1, 1, null));
    }

    @Test
    void asyncFindCanBeUsedAgainAfterTemporaryDetach() throws Exception {
        show("alpha beta", 0, "alpha beta");
        AtomicBoolean detachedCallbackRan = new AtomicBoolean();
        AtomicReference<FindResult> afterReattach = new AtomicReference<>();

        onEdt(() -> {
            view.addNotify();
            view.findAsync("alpha", false, false, result -> detachedCallbackRan.set(true));
            view.removeNotify();
            view.addNotify();
            view.findAsync("beta", false, false, afterReattach::set);
        });
        try {
            Await.until(() -> {
                drainEventQueue();
                return afterReattach.get() != null;
            }, "asynchronous find after reattach");
            assertThat(detachedCallbackRan).isFalse();
            assertThat(afterReattach.get()).isEqualTo(new FindResult(1, 1, null));
        } finally {
            onEdt(view::removeNotify);
        }
    }

    @Test
    void aNewReportedPressClearsACommandClickWhoseReleaseWasLost() throws Exception {
        assumeTrue(MAC);
        view.setLinkOpener(uri -> { });
        enableSgrMouse();
        show("go https://m.dev x", 0, "go https://m.dev x");
        view.handleMouse(press(8, 0, 1, InputEvent.META_DOWN_MASK));

        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK,
            x(2), y(1), 1, false, MouseEvent.BUTTON3));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, 0,
            x(2), y(1), 1, false, MouseEvent.BUTTON3));

        assertThat(connector.written()).isEqualTo("\033[<2;3;2M\033[<2;3;2m");
    }

    @Test
    void focusLossClearsACommandClickWhoseReleaseWasLost() throws Exception {
        assumeTrue(MAC);
        view.setLinkOpener(uri -> { });
        enableSgrMouse();
        show("go https://m.dev x", 0, "go https://m.dev x");
        view.handleMouse(press(8, 0, 1, InputEvent.META_DOWN_MASK));

        for (var listener : view.getFocusListeners()) {
            listener.focusLost(new FocusEvent(view, FocusEvent.FOCUS_LOST));
        }
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, 0,
            x(2), y(1), 1, false, MouseEvent.BUTTON3));

        assertThat(connector.written()).isEqualTo("\033[<2;3;2m");
    }

    private void show(String output, int row, String expectedStart) throws Exception {
        connector.feed(output);
        Await.until(() -> session.snapshot().lineText(row).startsWith(expectedStart), "\"" + expectedStart + "\"");
    }

    private void enableSgrMouse() throws Exception {
        connector.feed("\033[?1000h\033[?1006h");
        Await.until(session::mouseReporting, "mouse reporting on");
    }

    private void selectByDragging(int fromColumn, int fromRow, int toColumn, int toRow, int modifiers) {
        view.handleMouse(press(fromColumn, fromRow, 1, modifiers));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_DRAGGED, 0,
            modifiers | InputEvent.BUTTON1_DOWN_MASK, x(toColumn), y(toRow), 1, false, MouseEvent.NOBUTTON));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, 0,
            x(toColumn), y(toRow), 1, false, MouseEvent.BUTTON1));
    }

    private MouseEvent press(int column, int row, int clicks, int modifiers) {
        return new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, modifiers | InputEvent.BUTTON1_DOWN_MASK,
            x(column), y(row), clicks, false, MouseEvent.BUTTON1);
    }

    private MouseEvent popupPress(int column, int row, int modifiers) {
        return new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, modifiers | InputEvent.BUTTON3_DOWN_MASK,
            x(column), y(row), 1, true, MouseEvent.BUTTON3);
    }

    private MouseEvent popupRelease(int column, int row, int modifiers) {
        return new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, modifiers,
            x(column), y(row), 1, true, MouseEvent.BUTTON3);
    }

    private KeyEvent pressed(int keyCode, int modifiers, char keyChar) {
        return new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, keyChar);
    }

    private KeyEvent typed(char keyChar, int modifiers) {
        return new KeyEvent(view, KeyEvent.KEY_TYPED, 0, modifiers, KeyEvent.VK_UNDEFINED, keyChar);
    }

    private int x(int column) {
        return column * fonts.cellWidth() + 1;
    }

    private int y(int row) {
        return row * fonts.cellHeight() + 1;
    }

    private static void onEdt(Runnable action) throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(action);
    }

    private static void drainEventQueue() {
        try {
            onEdt(() -> { });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        }
    }

    private TerminalTextBuffer terminalBuffer() throws ReflectiveOperationException {
        var field = TerminalSession.class.getDeclaredField("buffer");
        field.setAccessible(true);
        return (TerminalTextBuffer) field.get(session);
    }

    private ThreadPoolExecutor searchExecutor() throws ReflectiveOperationException {
        var field = TerminalView.class.getDeclaredField("searchExecutor");
        field.setAccessible(true);
        return (ThreadPoolExecutor) field.get(view);
    }
}
