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
                assertThat(CommandSearch.find(entries, "new tab", java.util.List.of())).isNotEmpty();
                assertThat(CommandSearch.find(entries, "split", java.util.List.of()).stream().map(Command::id)).doesNotContain("split_right", "split_down");
                assertThat(CommandSearch.find(entries, "paste", java.util.List.of()).stream().map(Command::id)).doesNotContain("paste");
                assertThat(owner.action(ActionId.SPLIT_RIGHT).getValue(Action.NAME)).isEqualTo(ActionId.SPLIT_RIGHT.label());
                assertThat(owner.action(ActionId.SPLIT_RIGHT).getValue(Command.TITLE)).isEqualTo("Split Right \u00b7 Vertical");
                assertThat(entries.stream().map(e -> e.command().id())).contains("select_tab_1", "view.toolbar.icons", "view.status_bar");
                var status = entries.stream().map(CommandSearch.Entry::command)
                    .filter(command -> command.id().equals("view.status_bar")).findFirst().orElseThrow();
                var viewMenu = owner.menuBar().getMenu(2);
                assertThat(java.util.Arrays.stream(viewMenu.getMenuComponents())
                    .filter(javax.swing.JMenuItem.class::isInstance).map(javax.swing.JMenuItem.class::cast)
                    .map(javax.swing.JMenuItem::getAction)).contains(status.action());
                status.action().actionPerformed(new java.awt.event.ActionEvent(owner, 0, "test"));
                assertThat(owner.status().isVisible()).isFalse();
                assertThat(status.action().getValue(Command.TITLE)).isEqualTo("Show Status Bar");
            }
        });
    }
}
