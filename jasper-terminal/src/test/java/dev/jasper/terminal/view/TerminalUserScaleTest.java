package dev.jasper.terminal.view;

import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.testsupport.Await;
import java.awt.Dimension;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TerminalUserScaleTest {
    @Test void displayScaleKeepsLogicalZoomAndRefitsThePtyAtFractionalAndLargeSizes() throws Exception {
        var connector = new FakeConnector();
        try (var session = EmulationFixture.unstarted(connector, 80, 24, 100)) {
            session.internalAccess().startReading();
            var options = TerminalOptions.defaults().toBuilder().fontFamily("Monospaced").fallbackFonts(List.of()).build();
            var holder = new TerminalView[1];
            SwingUtilities.invokeAndWait(() -> {
                var view = holder[0] = new TerminalView(session, options);
                view.setSize(1000, 800);
                view.resizeSessionToFit();
                assertMetrics(view, 14);
                scale(view, 1.25f);
                assertMetrics(view, 17.5f);
                scale(view, 1.5f);
                assertThat(view.fontSize()).isEqualTo(14);
                assertThat(view.options().fontSize()).isEqualTo(14);
                assertMetrics(view, 21);
                view.setFontSize(15);
                assertMetrics(view, 22.5f);
                scale(view, 2);
                assertThat(view.fontSize()).isEqualTo(15);
                assertMetrics(view, 30);
                view.applyOptions(view.options().toBuilder().fontFamily("Dialog").lineHeight(1.5f).build());
                assertMetrics(view, 30);
                view.setPalette(dev.jasper.terminal.config.Palette.jasperLight());
                assertMetrics(view, 30);
                view.setFontSize(72);
                assertMetrics(view, 144);
                view.resetFontSize();
                assertMetrics(view, 28);
                var image = new java.awt.image.BufferedImage(2000, 1600, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try { graphics.scale(2, 2); view.paint(graphics); assertMetrics(view, 28); }
                finally { graphics.dispose(); }
            });
            var view = holder[0];
            Await.until(() -> connector.lastResize() != null && connector.lastResize().getColumns() == session.columns()
                && connector.lastResize().getRows() == session.rows(), "scaled PTY resize");
            SwingUtilities.invokeAndWait(view::removeNotify);
        }
    }

    @Test void scaleMustBeFiniteAndPositiveAndAnEqualScaleDoesNotInvalidateMetrics() throws Exception {
        try (var session = EmulationFixture.unstarted(new FakeConnector(), 80, 24, 100)) {
            SwingUtilities.invokeAndWait(() -> {
                var view = new TerminalView(session, TerminalOptions.defaults());
                for (float invalid : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
                    assertThatThrownBy(() -> scale(view, invalid)).isInstanceOf(IllegalArgumentException.class);
                var changes = new java.util.concurrent.atomic.AtomicInteger();
                view.addPropertyChangeListener("minimumSize", event -> changes.incrementAndGet());
                scale(view, 1);
                assertThat(changes).hasValue(0);
                assertThat(view.fontSize()).isEqualTo(14);
                view.removeNotify();
            });
        }
    }

    private static void assertMetrics(TerminalView view, float effectiveSize) {
        var o = view.options();
        var fonts = new FontSet(o.fontFamily(), effectiveSize, o.fallbackFonts(), o.ligatures(), o.lineHeight());
        assertThat(view.getMinimumSize()).as("effective size %s", effectiveSize)
            .isEqualTo(new Dimension(5 * fonts.cellWidth(), 2 * fonts.cellHeight()));
        assertThat(view.getPreferredSize()).isEqualTo(new Dimension(view.getWidth() / fonts.cellWidth() * fonts.cellWidth(),
            view.getHeight() / fonts.cellHeight() * fonts.cellHeight()));
    }

    private static void scale(TerminalView view, float value) {
        view.setUserScaleFactor(value);
    }
}
