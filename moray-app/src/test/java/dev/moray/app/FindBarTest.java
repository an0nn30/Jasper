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
                bar[0] = attachedBar(new TerminalView(session, TerminalOptions.defaults()));
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
                bar[0].dispose(); bar[0].removeNotify();
            });
        }
    }
    @Test void enterDuringDebounceAppliesNextAfterSearchCompletes() throws Exception {
        navigationDuringSearch("ENTER", 1, 1);
    }

    @Test void shiftEnterDuringDebounceAppliesPreviousAfterSearchCompletes() throws Exception {
        navigationDuringSearch("shift ENTER", 1, 2);
    }

    @Test void repeatedEnterWhileSearchIsInflightPreservesEveryStep() throws Exception {
        navigationDuringSearch("ENTER", 2, 2);
    }

    private void navigationDuringSearch(String key, int presses, int expected) throws Exception {
        try (TerminalSession session = TerminalSession.start(java.util.List.of("/bin/sh", "-c",
            "printf 'alpha alpha alpha\\n\\033]2;ready\\007'; read answer"),
            System.getenv(), HOME, 80, 24, 100)) {
            until(() -> session.title().equals("ready"));
            FindBar[] bar = new FindBar[1];
            try {
                edt(() -> {
                    bar[0] = attachedBar(new TerminalView(session, TerminalOptions.defaults()));
                    bar[0].open(); bar[0].queryField().setText("alpha");
                    var field = bar[0].queryField();
                    var binding = field.getInputMap().get(javax.swing.KeyStroke.getKeyStroke(key));
                    // The EDT cannot run completion between presses: subsequent presses are inflight.
                    for (int press = 0; press < presses; press++) field.getActionMap().get(binding)
                        .actionPerformed(new java.awt.event.ActionEvent(field, 0, key));
                });
                until(() -> bar[0].result().count() == 3);
                edt(() -> assertThat(bar[0].result().current()).isEqualTo(expected));
            } finally { if (bar[0] != null) edt(() -> { bar[0].dispose(); bar[0].removeNotify(); }); }
        }
    }

    @Test void invalidRegexErrorSurvivesNavigationUntilQueryChanges() throws Exception {
        try (TerminalSession session = shell(HOME)) {
            FindBar[] bar = new FindBar[1];
            try {
                edt(() -> {
                    bar[0] = attachedBar(new TerminalView(session, TerminalOptions.defaults()));
                    bar[0].open(); bar[0].regexButton().doClick(); bar[0].queryField().setText("[");
                });
                until(() -> bar[0].result().error() != null);
                edt(() -> {
                    String error = bar[0].result().error();
                    bar[0].next(); bar[0].previous();
                    assertThat(bar[0].result().error()).isEqualTo(error);
                    bar[0].queryField().setText("alpha");
                    assertThat(bar[0].result().error()).isNull();
                });
            } finally { if (bar[0] != null) edt(() -> { bar[0].dispose(); bar[0].removeNotify(); }); }
        }
    }
    @Test void reparentDuringSearchRetainsNavigationBeforeAndAfterDetach() throws Exception {
        navigationAcrossReparent(true, 2);
    }

    @Test void reparentRestartsSearchAndAppliesRetainedNavigationWithoutAnotherKey() throws Exception {
        navigationAcrossReparent(false, 1);
    }

    private static FindBar attachedBar(TerminalView view) {
        var bar = new FindBar(view);
        // Search runs only while showing; keep the root lightweight and omit native caret location queries.
        bar.queryField().removeCaretListener((javax.swing.event.CaretListener)
            bar.queryField().getAccessibleContext());
        bar.addNotify();
        return bar;
    }

    private void navigationAcrossReparent(boolean navigateAfterAttach, int expected) throws Exception {
        try (TerminalSession session = TerminalSession.start(java.util.List.of("/bin/sh", "-c",
            "printf 'alpha alpha\\n\\033]2;ready\\007'; read answer"),
            System.getenv(), HOME, 80, 24, 100)) {
            until(() -> session.title().equals("ready"));
            FindBar[] bar = new FindBar[1];
            TerminalView[] view = new TerminalView[1];
            try {
                edt(() -> {
                    view[0] = new TerminalView(session, TerminalOptions.defaults());
                    view[0].setSize(view[0].getPreferredSize());
                    bar[0] = attachedBar(view[0]); bar[0].open(); bar[0].queryField().setText("alpha");
                    bar[0].next();
                    // Real split/tab reparenting calls removeNotify and cancels the view's pending find.
                    bar[0].removeNotify(); view[0].removeNotify();
                    view[0].addNotify(); bar[0].addNotify();
                    if (navigateAfterAttach) bar[0].next();
                });
                until(() -> bar[0].result().count() == 2);
                edt(() -> assertThat(bar[0].result().current()).isEqualTo(expected));
            } finally {
                if (bar[0] != null) edt(() -> { bar[0].dispose(); bar[0].removeNotify(); view[0].removeNotify(); });
            }
        }
    }
}
