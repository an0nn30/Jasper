package dev.jasper.terminal.examples;

import dev.jasper.terminal.config.*;
import dev.jasper.terminal.session.*;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.view.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalExamplesTest {
    @Test void configureATerminalWithoutStartingAProcess() {
        // example:configuration:start
        TerminalOptions options = TerminalOptions.defaults().toBuilder()
            .fontSize(16f).copyOnSelect(true).bell(BellMode.NONE).build();
        SessionLaunchOptions launch = SessionLaunchOptions.builder()
            .command(List.of("example-shell", "-l"))
            .environment(Map.of("LANG", "C.UTF-8"))
            .workingDirectory(Path.of("."))
            .grid(new GridSize(80,24)).scrollback(options.scrollback()).build();
        // example:configuration:end
        assertThat(options.fontSize()).isEqualTo(16f);
        assertThat(launch.grid()).isEqualTo(new GridSize(80,24));
        assertThat(new SearchQuery("hello",false,false).text()).isEqualTo("hello");
    }

    // Compiled documentation example. The caller runs this off the EDT and owns
    // the returned session. In a real app, add the component to its pane on EDT.
    // example:embedding:start
    static TerminalSession startForEmbedding(SessionLaunchOptions launch,
            TerminalOptions options, java.util.function.Consumer<TerminalView> attach)
            throws IOException {
        if (SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Start processes off the EDT");
        TerminalSession session = TerminalSession.start(launch);
        SwingUtilities.invokeLater(() -> {
            try {
                TerminalView view = new TerminalView(session, options);
                attach.accept(view);
            } catch (RuntimeException | Error failure) {
                session.close();
                throw failure;
            }
        });
        return session;
    }
    // example:embedding:end
    // example:events:start
    static TerminalSessionListener observe(TerminalSession session,
            java.util.function.Consumer<String> showTitle, Runnable showExit) {
        TerminalSessionListener listener = new TerminalSessionListener() {
            @Override public void titleChanged(String title) {
                SwingUtilities.invokeLater(() -> showTitle.accept(title));
            }
        };
        session.addListener(listener);
        session.exitFuture().thenRun(() -> SwingUtilities.invokeLater(showExit));
        return listener;
    }
    // example:events:end

    // example:actions:start
    static void updateView(TerminalView view) {
        view.applyOptions(view.options().toBuilder().fontSize(18f).build());
        view.execute(TerminalAction.COPY_SELECTION);
        view.findAsync(new SearchQuery("error", false, false), result -> {
            if (result.error() != null) System.err.println(result.error());
        });
    }
    // example:actions:end

    // example:teardown:start
    static void closePane(javax.swing.JPanel pane, TerminalView view,
            TerminalSession session, TerminalSessionListener listener) {
        // EDT: removing a displayable view invokes removeNotify and stops its workers/timers.
        pane.remove(view);
        session.removeListener(listener);
        session.close(); // Native cleanup is bounded and asynchronous; never wait on the EDT.
        pane.revalidate();
        pane.repaint();
    }
    // example:teardown:end
}
