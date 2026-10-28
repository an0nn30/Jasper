package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.terminals.TerminalRegistry;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import javax.swing.JMenu;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Browser plan 4a in a window: a next tab opens right after the shown tab, and a content menu is in the window's menu bar
 * only while one of its plugin's content tabs is the window's current item.
 */
class NextTabAndContentMenuTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private WindowContent owner;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            owner = content(idleLauncher(pending), null, true);
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.connectContributions(model);
            owner.connectTerminals(terminals, () -> { });
        });
    }

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A plugin's view titled {@code title} in the window, placed as {@code placement} says. */
    private Tab view(String plugin, String title, TabRegistry.Placement placement) {
        return model.tabs().open(plugin, title, title, owner.id(), new JPanel(), placement);
    }

    /** The window's tabs in strip order. */
    private List<ParentTabId> strip() { return owner.parentTabs().views().stream().map(WorkspaceView::id).toList(); }

    private ParentTabId tabOf(Tab item) { return owner.parentTabOf(item).orElseThrow(); }

    private List<String> titles() { return owner.chrome().menuBar().menus().stream().map(JMenu::getText).toList(); }

    @Test void aNextTabOpensRightAfterTheShownTab() throws Exception {
        edt(() -> {
            Tab lone = view("dev.example.notes", "lone", TabRegistry.Placement.NEXT_TAB);
            assertThat(strip()).as("an empty window's workspace takes it").containsExactly(tabOf(lone));
            Tab second = view("dev.example.notes", "second", TabRegistry.Placement.NEW_TAB);
            Tab third = view("dev.example.notes", "third", TabRegistry.Placement.NEW_TAB);
            owner.selectTab(second);
            Tab next = view("dev.example.notes", "next", TabRegistry.Placement.NEXT_TAB);
            assertThat(strip()).as("the new tab is third, and the old third is fourth")
                .containsExactly(tabOf(lone), tabOf(second), tabOf(next), tabOf(third));
            assertThat(owner.selectedTab()).as("and shown").isSameAs(next);
        });
    }

    @Test void aContentMenuShowsOnlyWhileItsPluginsTabIsTheCurrentItem() throws Exception {
        edt(() -> {
            model.addAction("dev.example.notes.back", "Back", null, List.of(), Optional.empty(), invocation -> { });
            model.addMenuSection(MenuTarget.content("dev.example.notes.web", "Web", "dev.example.notes"))
                .set(List.of(new MenuEntry.Item("dev.example.notes.back")));
            assertThat(titles()).as("no current item").doesNotContain("Web");
            Tab notes = view("dev.example.notes", "notes", TabRegistry.Placement.NEW_TAB);
            assertThat(titles()).contains("Web");
            view("dev.example.other", "other", TabRegistry.Placement.NEW_TAB);
            assertThat(titles()).as("another plugin's item").doesNotContain("Web");
            owner.selectTab(notes);
            assertThat(titles()).as("its item again").containsOnlyOnce("Web");
            view("dev.example.other", "beside", TabRegistry.Placement.SPLIT_RIGHT);
            assertThat(titles()).as("the focused pane's item, in the same tab").doesNotContain("Web");
            owner.selectTab(notes);
            assertThat(titles()).containsOnlyOnce("Web");
        });
    }
}
