package dev.moray.terminal;

import com.jediterm.terminal.model.TerminalTextBuffer;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MouseReportingEfficiencyTest {
    @Test
    void reportsReadNoScreenLinesAndRequestNoRepaint() throws Exception {
        FakeConnector connector = new FakeConnector();
        try (TerminalSession session = new TerminalSession(connector, 20, 4, 100)) {
            session.startReading();
            connector.feed("\033[?1002h\033[?1006h\033]0;ready\007");
            Await.until(() -> session.title().equals("ready"), "mouse setup complete");
            connector.finish();
            session.exitFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            TerminalView view = new TerminalView(session, TerminalOptions.defaults());
            var bufferField = TerminalSession.class.getDeclaredField("buffer");
            bufferField.setAccessible(true);
            TerminalTextBuffer buffer = (TerminalTextBuffer) bufferField.get(session);
            var storageField = TerminalTextBuffer.class.getDeclaredField("screenLinesStorage");
            storageField.setAccessible(true);
            Object storage = storageField.get(buffer);
            AtomicInteger reads = new AtomicInteger();
            Object counted = Proxy.newProxyInstance(storageField.getType().getClassLoader(),
                new Class<?>[] {storageField.getType()}, (proxy, method, args) -> {
                    if (method.getName().equals("get")) reads.incrementAndGet();
                    return method.invoke(storage, args);
                });
            storageField.set(buffer, counted);
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
            storageField.set(buffer, storage);
            assertThat(connector.written()).contains("\033[<0;1;1M", "\033[<32;").endsWith("m");
            assertAll(
                () -> assertThat(reads.get()).as("screen line reads").isZero(),
                () -> assertThat(repaints.get()).as("repaint requests").isZero());
        }
    }
}
