package dev.jasper.app;

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
                assertThat(entries.stream().map(e -> e.command().id())).contains("select_tab_1", "view.tab_height", "view.appearance.dark");
                assertThat(owner.menuBar().getMenu(2).getItem(owner.menuBar().getMenu(2).getItemCount()-1).getAction())
                    .isSameAs(entries.stream().map(CommandSearch.Entry::command).filter(c -> c.id().equals("view.tab_height")).findFirst().orElseThrow().action());
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
                var viewMenu = owner.menuBar().getMenu(2);
                var item = java.util.Arrays.stream(viewMenu.getMenuComponents())
                    .filter(javax.swing.JCheckBoxMenuItem.class::isInstance).map(javax.swing.JCheckBoxMenuItem.class::cast)
                    .filter(box -> box.getAction() == command.action()).findFirst().orElseThrow();
                assertThat(item.isSelected()).isTrue();
                owner.setActive(true);
                command.action().actionPerformed(new java.awt.event.ActionEvent(owner, 0, "test"));
                assertThat(toggles[0]).isEqualTo(1);
                assertThat(command.title()).isEqualTo("Show Jasper");
                assertThat(item.isSelected()).isFalse();
            }
        });
    }
}
