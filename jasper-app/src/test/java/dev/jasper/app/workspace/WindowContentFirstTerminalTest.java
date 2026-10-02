package dev.jasper.app.workspace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

class WindowContentFirstTerminalTest {
    @AfterEach void close() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void reportsOnlyTheWindowsFirstConfiguredTerminal() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        List<TerminalPane> seen = new ArrayList<>();
        WindowContent[] content = new WindowContent[1];
        edt(() -> {
            content[0] = DesktopTestSupport.content(DesktopTestSupport.launcher(pending));
            content[0].onFirstTerminal(seen::add);
        });
        pending.poll().run();
        until(() -> seen.size() == 1);
        TerminalPane first = seen.getFirst();
        assertThat(first.view()).isNotNull();
        TerminalTab[] second = new TerminalTab[1];
        edt(() -> second[0] = content[0].openTab(DesktopTestSupport.HOME, null));
        pending.poll().run();
        until(() -> second[0].panes().getFirst().view() != null);
        edt(() -> { });
        assertThat(seen).containsExactly(first);
    }
}
