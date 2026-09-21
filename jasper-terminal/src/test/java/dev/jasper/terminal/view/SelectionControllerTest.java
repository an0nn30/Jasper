package dev.jasper.terminal.view;

import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.internal.text.Selection;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class SelectionControllerTest {
    @Test void overwrittenLiveSelectionCannotBeCopied() throws Exception {
        var connector = new FakeConnector();
        try (var session = EmulationFixture.unstarted(connector, 20, 4, 100)) {
            session.internalAccess().startReading();
            var access = session.internalAccess();
            connector.feed("first");
            Await.until(() -> access.snapshot().lineText(0).equals("first"), "first text");
            var owner = new AtomicReference<SelectionController>();
            SwingUtilities.invokeAndWait(() -> {
                var selection = new SelectionController(access);
                selection.set(new Selection(0, 0, 0, 4, false));
                owner.set(selection);
            });
            connector.feed("\rother");
            Await.until(() -> access.snapshot().lineText(0).equals("other"), "overwrite");
            SwingUtilities.invokeAndWait(() -> {
                assertThat(owner.get().selectedText()).isEmpty();
                assertThat(owner.get().hasSelection()).isFalse();
            });
        }
    }
}
