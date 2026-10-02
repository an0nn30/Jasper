// jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalAfterPaintTest.java
package dev.jasper.terminal.view;

import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.session.TerminalSession;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminalAfterPaintTest {
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach void setUp() throws Exception {
        session = EmulationFixture.unstarted(new FakeConnector(), 20, 4, 100);
        session.internalAccess().startReading();
        onEdt(() -> {
            view = new TerminalView(session, TerminalOptions.defaults());
            view.setSize(view.getPreferredSize());
            view.addNotify();
        });
    }

    @AfterEach void tearDown() throws Exception {
        onEdt(() -> view.removeNotify());
        session.close();
    }

    @Test void runsOnceAfterTheNextPaintOnly() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        onEdt(() -> view.afterNextPaint(runs::incrementAndGet));
        onEdt(() -> { });
        assertThat(runs).hasValue(0);
        onEdt(this::paint);
        onEdt(() -> { });
        assertThat(runs).hasValue(1);
        onEdt(this::paint);
        onEdt(() -> { });
        assertThat(runs).hasValue(1);
    }

    @Test void anActionAddedWhileARunningActionExecutesWaitsForTheFollowingPaint() throws Exception {
        AtomicInteger inner = new AtomicInteger();
        onEdt(() -> view.afterNextPaint(() -> view.afterNextPaint(inner::incrementAndGet)));
        onEdt(this::paint);
        onEdt(() -> { });
        assertThat(inner).hasValue(0);
        onEdt(this::paint);
        onEdt(() -> { });
        assertThat(inner).hasValue(1);
    }

    @Test void registeringOffTheEdtIsRefused() {
        assertThatThrownBy(() -> view.afterNextPaint(() -> { })).isInstanceOf(IllegalStateException.class);
    }

    private void paint() {
        var image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { view.paint(graphics); } finally { graphics.dispose(); }
    }

    private static void onEdt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
}
