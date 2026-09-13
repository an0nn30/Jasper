package dev.moray.app;

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
                assertThat(entries.stream().map(e -> e.command().id())).contains("select_tab_1", "view.tab_height", "view.appearance.system");
                assertThat(owner.menuBar().getMenu(2).getItem(owner.menuBar().getMenu(2).getItemCount()-1).getAction())
                    .isSameAs(entries.stream().map(CommandSearch.Entry::command).filter(c -> c.id().equals("view.tab_height")).findFirst().orElseThrow().action());
            }
        });
    }
}
