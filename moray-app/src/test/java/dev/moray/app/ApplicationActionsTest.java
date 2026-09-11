package dev.moray.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import javax.swing.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static dev.moray.app.DesktopTestSupport.*;

@DisabledOnOs(OS.WINDOWS)
class ApplicationActionsTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void sharedActionsResolveFocusedPaneAndDoNotOverrideTextEditing() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> { first[0] = owner[0].currentPane(); owner[0].invoke(ActionId.SPLIT_RIGHT); });
        pending.remove().run();
        edt(() -> {
            WindowContent ui = owner[0];
            ui.invoke(ActionId.FONT_BIGGER);
            assertThat(ui.currentPane().view().fontSize()).isEqualTo(15);
            assertThat(first[0].view().fontSize()).isEqualTo(14);
            ui.invoke(ActionId.FOCUS_PANE_LEFT); ui.invoke(ActionId.FIND);
            assertThat(first[0].findBar().isVisible()).isTrue();
            assertThat(ui.action(ActionId.OPEN_SETTINGS).isEnabled()).isFalse();
            assertThat(ui.action(ActionId.RELOAD_CONFIG).isEnabled()).isFalse();
            assertThat(ui.dispatchShortcut(ui.bindings().strokeFor(ActionId.PASTE).orElseThrow(), new JTextField())).isFalse();
            ui.setToolbarMode(WindowContent.ToolbarMode.HIDDEN);
            assertThat(ui.toolbar().isVisible()).isFalse();
            ui.setStatusVisible(false); assertThat(ui.status().isVisible()).isFalse();
            ui.close();
        });
    }
    @Test void focusingFindFieldRetargetsGlobalPaneActions() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending))); pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> {
            first[0] = owner[0].currentPane(); owner[0].invoke(ActionId.FIND);
            owner[0].invoke(ActionId.SPLIT_RIGHT);
        });
        pending.remove().run();
        edt(() -> {
            JTextField field = first[0].findBar().queryField();
            java.awt.event.FocusEvent event = new java.awt.event.FocusEvent(field, java.awt.event.FocusEvent.FOCUS_GAINED);
            for (var listener : field.getFocusListeners()) listener.focusGained(event);
            owner[0].invoke(ActionId.FONT_BIGGER);
            assertThat(first[0].view().fontSize()).isEqualTo(15);
            owner[0].close();
        });
    }
}
