package dev.jasper.terminal.view;

import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.internal.emulation.SessionInspection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MouseReportingEfficiencyTest {
    @Test
    void reportsReadNoScreenLinesAndRequestNoRepaint() throws Exception {
        FakeConnector connector = new FakeConnector();
        try (TerminalSession session = EmulationFixture.unstarted(connector, 20, 4, 100)) {
            session.internalAccess().startReading();
            connector.feed("\033[?1002h\033[?1006h\033]0;ready\007");
            Await.until(() -> session.title().equals("ready"), "mouse setup complete");
            connector.finish();
            session.exitFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            TerminalView view = new TerminalView(session, TerminalOptions.defaults());
            try (var reads = SessionInspection.countLineReads(session)) {
            AtomicInteger repaints = new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> {
                RepaintManager previous = RepaintManager.currentManager(view);
                RepaintManager.setCurrentManager(new RepaintManager() {
                    @Override public void addDirtyRegion(JComponent component, int x, int y, int w, int h) {
                        if (component == view) repaints.incrementAndGet();
                    }
                });
                try {
                    view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0,
                        InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false, MouseEvent.BUTTON1));
                    view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_DRAGGED, 0,
                        InputEvent.BUTTON1_DOWN_MASK, 30, 1, 1, false, MouseEvent.NOBUTTON));
                    view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0,
                        0, 30, 1, 1, false, MouseEvent.BUTTON1));
                } finally {
                    RepaintManager.setCurrentManager(previous);
                }
            });

            assertThat(connector.written()).contains("\033[<0;1;1M", "\033[<32;").endsWith("m");
            assertAll(
                () -> assertThat(reads.get()).as("screen line reads").isZero(),
                () -> assertThat(repaints.get()).as("repaint requests").isZero());
            }
        }
    }
}
