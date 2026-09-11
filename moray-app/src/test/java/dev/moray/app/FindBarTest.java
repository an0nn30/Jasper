package dev.moray.app;

import dev.moray.terminal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.*;
import static dev.moray.app.DesktopTestSupport.*;

@DisabledOnOs(OS.WINDOWS)
class FindBarTest {
    @Test void searchesNavigatesReportsRegexErrorsAndClosesWithoutLateResults() throws Exception {
        try (TerminalSession session = shell(HOME)) {
            FindBar[] bar = new FindBar[1];
            edt(() -> {
                bar[0] = new FindBar(new TerminalView(session, TerminalOptions.defaults()));
                bar[0].open(); bar[0].queryField().setText("alpha");
            });
            until(() -> bar[0].result().count() == 2);
            edt(() -> {
                assertThat(bar[0].result().current()).isEqualTo(2); // terminal starts at the newest match
                bar[0].next(); assertThat(bar[0].result().current()).isEqualTo(1);
                bar[0].previous(); assertThat(bar[0].result().current()).isEqualTo(2);
                bar[0].regexButton().doClick(); bar[0].queryField().setText("[");
            });
            until(() -> bar[0].result().error() != null);
            edt(() -> {
                bar[0].queryField().setText("alpha"); bar[0].close();
                assertThat(bar[0].isVisible()).isFalse();
                assertThat(bar[0].result().count()).isZero();
                bar[0].dispose();
            });
        }
    }
}
