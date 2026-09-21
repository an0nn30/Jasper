package dev.jasper.app;

import dev.jasper.terminal.FindResult;
import dev.jasper.terminal.TerminalOptions;
import dev.jasper.terminal.TerminalSession;
import dev.jasper.terminal.TerminalView;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class FindBarVisibilityTest {
    private TerminalSession session;
    private TerminalView view;
    private FindBar bar;
    private JTabbedPane tabs;

    @BeforeEach void setUp() throws Exception {
        session = TerminalSession.start(List.of("/bin/sh", "-c",
            "printf 'alpha alpha alpha\\n\\033]2;ready\\007'; read answer"),
            System.getenv(), HOME, 80, 24, 100);
        until(() -> session.title().equals("ready"));
        edt(() -> {
            view = new TerminalView(session, TerminalOptions.defaults());
            view.setSize(view.getPreferredSize());
            bar = new FindBar(view);
            // A lightweight headless root has no native screen position for accessibility's caret callback.
            bar.queryField().removeCaretListener((javax.swing.event.CaretListener)
                bar.queryField().getAccessibleContext());
            var pane = new JPanel(new BorderLayout());
            pane.add(view, BorderLayout.CENTER); pane.add(bar, BorderLayout.NORTH);
            tabs = new JTabbedPane();
            tabs.addTab("terminal", pane); tabs.addTab("other", new JPanel());
            tabs.addNotify();
        });
    }

    @AfterEach void tearDown() throws Exception {
        edt(() -> { bar.dispose(); tabs.removeNotify(); });
        session.close();
    }

    @Test void hidingStopsQueuedDebounceAndShowingResumesWithoutAnotherKeystroke() throws Exception {
        edt(() -> {
            prepareQuery();
            assertThat(timer().isRunning()).isTrue();
            tabs.setSelectedIndex(1);
            assertThat(bar.isVisible()).isTrue();
            assertThat(bar.isShowing()).isFalse();
            assertThat(timer().isRunning()).as("hidden debounce stopped").isFalse();
            fireDebounce(); // a timer delivery already queued when the tab hid must also be harmless
            assertThat(field(view, "pendingSearch")).isNull();
            assertThat(bar.queryField().getText()).isEqualTo("alpha");
            tabs.setSelectedIndex(0);
            assertThat(timer().isRunning()).isTrue();
            fireDebounce();
        });
        until(() -> bar.result().count() == 3);
        edt(() -> assertThat(bar.result()).isEqualTo(new FindResult(3, 3, null)));
    }

    @Test void hidingRunningSearchRejectsLateResultAndResumesRetainedNavigation() throws Exception {
        Object buffer = field(field(field(session, "access"), "engine"), "buffer");
        buffer.getClass().getMethod("lock").invoke(buffer);
        ThreadPoolExecutor executor;
        try {
            edt(() -> { prepareQuery(); bar.next(); }); // starts search immediately and queues one navigation step
            executor = (ThreadPoolExecutor) field(view, "searchExecutor");
            until(() -> executor.getActiveCount() == 1);
            Future<?> pending = (Future<?>) field(view, "pendingSearch");
            edt(() -> {
                tabs.setSelectedIndex(1);
                assertThat(pending.isCancelled()).isTrue();
                bar.next(); // preserve navigation while hidden without launching work
                fireDebounce();
                assertThat(field(view, "pendingSearch")).isNull();
            });
        } finally { buffer.getClass().getMethod("unlock").invoke(buffer); }
        until(() -> executor.getActiveCount() == 0);
        edt(() -> {
            assertThat(bar.result()).isEqualTo(new FindResult(0, 0, null));
            assertThat(view.findNext()).isEqualTo(new FindResult(0, 0, null));
            tabs.setSelectedIndex(0);
            fireDebounce();
            assertThat(field(view, "searchExecutor")).isSameAs(executor);
        });
        until(() -> bar.result().count() == 3);
        edt(() -> assertThat(bar.result()).isEqualTo(new FindResult(3, 2, null)));
    }

    @Test void showingPreservesCompletedMatchesAndNavigationWithoutResearching() throws Exception {
        edt(() -> { prepareQuery(); fireDebounce(); });
        until(() -> bar.result().count() == 3);
        edt(() -> {
            bar.next();
            tabs.setSelectedIndex(1);
            assertThat(bar.result()).isEqualTo(new FindResult(3, 1, null));
            tabs.setSelectedIndex(0);
            assertThat(timer().isRunning()).isFalse();
            assertThat(field(view, "pendingSearch")).isNull();
            assertThat(bar.result()).isEqualTo(new FindResult(3, 1, null));
            bar.next();
            assertThat(bar.result()).isEqualTo(new FindResult(3, 2, null));
        });
    }

    private void prepareQuery() { bar.open(); bar.queryField().setText("alpha"); }

    private Timer timer() { return (Timer) field(bar, "debounce"); }

    private void fireDebounce() {
        Timer timer = timer(); timer.stop();
        for (var listener : timer.getActionListeners()) listener.actionPerformed(new ActionEvent(timer, 0, ""));
    }

    private static Object field(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
