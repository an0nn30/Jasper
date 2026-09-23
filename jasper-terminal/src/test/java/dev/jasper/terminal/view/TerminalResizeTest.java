package dev.jasper.terminal.view;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;
import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalResizeTest {
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;
    private int cellWidth, cellHeight;

    @BeforeEach void start() throws Exception {
        connector = new FakeConnector();
        session = EmulationFixture.unstarted(connector, 100, 30, 100);
        SwingUtilities.invokeAndWait(() -> {
            view = new TerminalView(session, TerminalOptions.defaults());
            Dimension size = view.getPreferredSize();
            cellWidth = size.width / 100; cellHeight = size.height / 30;
            view.setSize(size);
            view.addNotify();
        });
        SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterEach void stop() throws Exception {
        SwingUtilities.invokeAndWait(view::removeNotify);
        session.close();
    }

    private void resize(int columns, int rows) {
        view.setSize(columns * cellWidth, rows * cellHeight);
        // Deliver every intermediate layout event, as separate turns of an interactive drag would.
        for (var listener : view.getComponentListeners())
            listener.componentResized(new ComponentEvent(view, ComponentEvent.COMPONENT_RESIZED));
    }

    @Test void aResizeBurstDoesNotReflowOrNotifyTheShellAtIntermediateSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int columns = 98; columns >= 40; columns -= 2) resize(columns, 24);
            assertThat(session.columns()).as("keep old grid until the drag settles").isEqualTo(100);
            assertThat(session.internalAccess().snapshot().width()).isEqualTo(100);
            assertThat(connector.lastGridSize()).isNull();
        });
        Await.until(() -> new GridSize(40, 24).equals(connector.lastGridSize()), "settled resize reaches shell");
        assertThat(session.internalAccess().snapshot().width()).isEqualTo(40);
        assertThat(session.internalAccess().snapshot().height()).isEqualTo(24);
    }

    @Test void zeroSizedLayoutDoesNotDestroyTheExistingGrid() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            resize(40, 24);
            resize(0, 0);
            view.resizeSessionToFit();
            assertThat(session.columns()).isEqualTo(100);
            assertThat(session.rows()).isEqualTo(30);
            assertThat(connector.lastGridSize()).isNull();
        });
    }

    @Test void removingTheViewCancelsPendingResizeUntilItIsAttachedAgain() throws Exception {
        SwingUtilities.invokeAndWait(() -> resize(40, 24));
        // Drain the component event before removing, so it cannot hide a missed cancellation.
        SwingUtilities.invokeAndWait(view::removeNotify);
        var elapsed = new java.util.concurrent.CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            var barrier = new javax.swing.Timer(300, event -> elapsed.countDown());
            barrier.setRepeats(false); barrier.start();
        });
        assertThat(elapsed.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(session.columns()).as("detached view must not apply its queued size").isEqualTo(100);
        assertThat(connector.lastGridSize()).isNull();
        SwingUtilities.invokeAndWait(() -> {
            // A component event already queued before removal must not schedule more work.
            resize(35, 20);
            assertThat(session.columns()).isEqualTo(100);
            assertThat(connector.lastGridSize()).isNull();
        });
        SwingUtilities.invokeAndWait(view::addNotify);
        Await.until(() -> new GridSize(35, 20).equals(connector.lastGridSize()), "reattached view fits its current area");
    }
}
