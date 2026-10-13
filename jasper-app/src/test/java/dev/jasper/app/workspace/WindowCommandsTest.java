package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.commands.CommandSearch;
import java.util.ArrayDeque;
import javax.swing.Action;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WindowCommandsTest {
    @Test void pendingPanesExposeGlobalsAndMetadataPreservesMenus() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()))) {
                var entries = owner.commands().entries();
                assertThat(CommandSearch.find(entries, "new tab", java.util.List.of(), 5)).isNotEmpty();
                assertThat(CommandSearch.find(entries, "split", java.util.List.of(), 5).stream().map(Command::id)).doesNotContain("split_right", "split_down");
                assertThat(CommandSearch.find(entries, "paste", java.util.List.of(), 5).stream().map(Command::id)).doesNotContain("paste");
                assertThat(owner.action(ActionId.SPLIT_RIGHT).getValue(Action.NAME)).isEqualTo(ActionId.SPLIT_RIGHT.label());
                assertThat(owner.action(ActionId.SPLIT_RIGHT).getValue(Command.TITLE)).isEqualTo("Split Right \u00b7 Vertical");
                assertThat(entries.stream().map(e -> e.command().id())).contains("select_tab_1", "view.terminal_scheme.slate-steel.dark", "view.window_settings")
                    .doesNotContain("view.tab_height", "view.rail_width", "view.toolbar_height", "view.status_bar_height");
                var windowSettings = entries.stream().map(CommandSearch.Entry::command)
                    .filter(c -> c.id().equals("view.window_settings")).findFirst().orElseThrow();
                assertThat(windowSettings.title()).isEqualTo("Window Settings\u2026");
                owner.updateActions();
                assertThat(windowSettings.action().isEnabled()).as("nothing to open without a configuration").isFalse();
            }
        });
    }

    @Test void showJasperCommandTogglesThroughTheOwnerHookAndReportsItsState() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()))) {
                boolean[] shown = {true}; int[] toggles = {0};
                owner.buddyEnabled = () -> shown[0];
                owner.onToggleBuddy = () -> { shown[0] = !shown[0]; toggles[0]++; };
                owner.updateActions();
                var command = owner.commands().entries().stream().map(CommandSearch.Entry::command)
                    .filter(c -> c.id().equals("view.buddy")).findFirst().orElseThrow();
                assertThat(command.title()).isEqualTo("Hide Jasper");
                assertThat(command.action().getValue(Action.SELECTED_KEY)).isEqualTo(true);
                assertThat(CommandSearch.find(owner.commands().entries(), "jasper", java.util.List.of(), 5).stream().map(Command::id))
                    .contains("view.buddy");
                owner.setActive(true);
                command.action().actionPerformed(new java.awt.event.ActionEvent(owner, 0, "test"));
                assertThat(toggles[0]).isEqualTo(1);
                assertThat(command.title()).isEqualTo("Show Jasper");
                assertThat(command.action().getValue(Action.SELECTED_KEY)).isEqualTo(false);
            }
        });
    }
}
