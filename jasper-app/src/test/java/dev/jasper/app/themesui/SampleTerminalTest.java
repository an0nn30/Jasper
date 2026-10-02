package dev.jasper.app.themesui;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.config.ConfigSnapshot;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.view.TerminalView;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SampleTerminalTest {
    @Test void cannedOutputArrivesWithoutShellAndClosesIdempotently() throws Exception {
        var sample = new SampleTerminal[1];
        SwingUtilities.invokeAndWait(() -> sample[0] = new SampleTerminal(ConfigSnapshot.defaults().viewOptions(12, Theme.DARK.palette())));
        try {
            var visible = new AtomicBoolean(); long deadline = System.nanoTime() + 5_000_000_000L;
            while (!visible.get() && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> visible.set(((TerminalView)sample[0].component()).find(new SearchQuery("something failed", false, true)).count() == 1));
                if (!visible.get()) Thread.sleep(10);
            }
            assertThat(visible.get()).as("canned output became searchable").isTrue();
        } finally { SwingUtilities.invokeAndWait(() -> { sample[0].close(); sample[0].close(); assertThat(sample[0].closed()).isTrue(); }); }
    }
}
