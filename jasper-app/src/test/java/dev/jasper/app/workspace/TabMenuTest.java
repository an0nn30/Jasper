package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.ContentCommand;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Side;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Workspace tabs spec 11: the tab menu, Close Others, Close Group and the menus (workspace plan 3a Task 6). */
class TabMenuTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    /** A connected window under the standard chrome with {@code count} running terminal tabs in one group. */
    private WindowContent window(int count) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner[0].connectContributions(model);
            for (int i = 1; i < count; i++) owner[0].newTab(HOME);
        });
        while (!pending.isEmpty()) pending.remove().run();
        return owner[0];
    }

    private static List<String> titles(JPopupMenu menu) {
        var titles = new ArrayList<String>();
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item) titles.add(item.getText());
        return titles;
    }

    private static JMenuItem item(JPopupMenu menu, String title) {
        for (var child : menu.getComponents()) if (child instanceof JMenuItem item && item.getText().equals(title)) return item;
        throw new AssertionError("no " + title + " in " + titles(menu));
    }

    /** The plain items of the menu bar's menu called {@code title}; its submenus are left out. */
    private static List<String> items(WindowContent window, String title) {
        var bar = window.menuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null || !title.equals(menu.getText())) continue;
            var items = new ArrayList<String>();
            for (var child : menu.getMenuComponents())
                if (child instanceof JMenuItem item && !(child instanceof JMenu)) items.add(item.getText());
            return items;
        }
        throw new AssertionError("no " + title + " menu");
    }

    @Test void aTerminalTabsMenuRenamesAndClosesAndAPluginTabsMenuMovesThenListsItsCommands() throws Exception {
        var window = window(1);
        edt(() -> {
            var keys = window.bindings();
            var menu = window.tabMenu(window.selectedTab());
            assertThat(titles(menu)).containsExactly("Rename Tab", "Close Tab", "Close Others", "Close Group");
            assertThat(item(menu, "Rename Tab").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.RENAME_TAB).orElseThrow());
            assertThat(item(menu, "Close Tab").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.CLOSE_TAB).orElseThrow());
            assertThat(item(menu, "Close Group").getAccelerator()).isEqualTo(keys.strokeFor(ActionId.CLOSE_GROUP).orElseThrow());
            assertThat(item(menu, "Close Others").isEnabled()).as("the group has no other tab").isFalse();
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel());
            int[] saved = {0};
            view.commands(List.of(new ContentCommand("save", "Save", null, true, () -> saved[0]++)));
            var hosted = window.tabMenu(view);
            assertThat(titles(hosted)).containsExactly("Open in Separate Window", "Close Tab", "Close Others", "Close Group", "Save");
            assertThat(item(hosted, "Close Others").isEnabled()).as("the terminal tab shares its group").isTrue();
            item(hosted, "Save").doClick();
            assertThat(saved[0]).isEqualTo(1);
        });
    }

    @Test void closeOthersClosesTheRestOfItsGroupAndLeavesTheOtherGroups() throws Exception {
        var window = window(4);
        edt(() -> {
            var workspace = window.workspace();
            var b1 = window.tabs().get(3);
            workspace.moveToNewGroup(b1, workspace.model().activeGroup(), Side.RIGHT);
            var a1 = window.tabs().get(0); var a2 = window.tabs().get(1); var a3 = window.tabs().get(2);
            item(window.tabMenu(a2), "Close Others").doClick();
            assertThat(window.tabs()).containsExactly(a2, b1);
            assertThat(a1.closed()).isTrue();
            assertThat(a3.closed()).isTrue();
            assertThat(workspace.model().groups()).hasSize(2);
            assertThat(((TerminalContent) b1.content()).pane().running()).isTrue();
        });
    }

    @Test void closeGroupAsksTheGuardsFirstAndARefusalClosesNothing() throws Exception {
        var window = window(2);
        edt(() -> {
            var workspace = window.workspace();
            var other = window.tabs().get(1);
            workspace.moveToNewGroup(other, workspace.model().activeGroup(), Side.RIGHT);
            var terminal = window.tabs().get(0);
            window.selectTab(terminal);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel());
            assertThat(workspace.model().groupOf(view.id())).isEqualTo(workspace.model().groupOf(terminal.id()));
            var asked = new ArrayList<List<Tab>>();
            boolean[] allow = {false};
            model.tabs().guard("dev.example.editor", (tabs, batch) -> {
                asked.add(tabs);
                return CompletableFuture.completedFuture(allow[0]);
            });
            window.invoke(ActionId.CLOSE_GROUP);
            assertThat(asked).containsExactly(List.of(view));
            assertThat(terminal.closed()).as("a refusal closes nothing").isFalse();
            assertThat(window.tabs()).containsExactly(terminal, view, other);
            allow[0] = true;
            window.invoke(ActionId.CLOSE_GROUP);
            assertThat(asked).hasSize(2);
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).isTrue();
            assertThat(window.tabs()).containsExactly(other);
            assertThat(workspace.model().groups()).as("the group went with its tabs").hasSize(1);
        });
    }

    /** Runs {@code body} with the tab registry's log captured: what it logged. */
    private static List<java.util.logging.LogRecord> registryLog(Runnable body) {
        var logger = java.util.logging.Logger.getLogger(dev.jasper.app.contributions.TabRegistry.class.getName());
        var records = new java.util.concurrent.CopyOnWriteArrayList<java.util.logging.LogRecord>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        boolean parents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try { body.run(); }
        finally { logger.removeHandler(handler); logger.setUseParentHandlers(parents); }
        return records;
    }

    @Test void closeGroupClosesEveryTabWhenOneTabsCloseListenerThrows() throws Exception {
        var window = window(2);
        edt(() -> {
            var workspace = window.workspace();
            var terminal = window.tabs().get(0); var other = window.tabs().get(1);
            workspace.moveToNewGroup(other, workspace.model().activeGroup(), Side.RIGHT);
            Tab elsewhere = model.tabs().open("dev.example.editor", "elsewhere", "elsewhere.txt", window.id(), new JPanel());
            window.selectTab(terminal);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel());
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(true));
            terminal.onClosed(() -> { throw new IllegalStateException("close failed"); });
            var logged = registryLog(() -> window.closeGroup(terminal));
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).as("the tab after the failing one closed too").isTrue();
            assertThat(window.tabs()).containsExactly(other, elsewhere);
            assertThat(logged).as("the failure is logged").singleElement()
                .satisfies(record -> assertThat(record.getThrown()).hasMessage("close failed"));
            assertThat(elsewhere.requestClose()).as("a later close still runs").isCompletedWithValue(true);
            assertThat(elsewhere.closed()).isTrue();
        });
    }

    @Test void aBatchCloseThatEmptiesTheWindowRunsItsEmptyHandlingAndKeepsOtherClosesWorking() throws Exception {
        var emptied = new ArrayList<String>();
        WindowContent[] owners = new WindowContent[2];
        edt(() -> {
            owners[0] = track(new WindowContent(idleLauncher(pending), HOME, path -> {}, () -> {}, () -> {
                emptied.add("empty");
                throw new IllegalStateException("window teardown failed");
            }));
            owners[0].connectContributions(model);
            owners[1] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owners[1].connectContributions(model);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            var window = owners[0];
            var terminal = window.tabs().get(0);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel());
            Tab elsewhere = model.tabs().open("dev.example.editor", "elsewhere", "elsewhere.txt", owners[1].id(), new JPanel());
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(true));
            var logged = registryLog(() -> window.closeGroup(terminal));
            assertThat(emptied).as("the window's empty handling ran").containsExactly("empty");
            assertThat(terminal.closed()).isTrue();
            assertThat(view.closed()).isTrue();
            assertThat(logged).singleElement().satisfies(record -> assertThat(record.getThrown()).hasMessage("window teardown failed"));
            assertThat(elsewhere.requestClose()).as("a close in another window still runs").isCompletedWithValue(true);
            assertThat(elsewhere.closed()).isTrue();
        });
    }

    @Test void aTerminalOnlyCloseGroupClosesWhileAnApprovalIsPendingElsewhere() throws Exception {
        var window = window(3);
        edt(() -> {
            var workspace = window.workspace();
            var a1 = window.tabs().get(0); var a2 = window.tabs().get(1); var b1 = window.tabs().get(2);
            workspace.moveToNewGroup(b1, workspace.model().activeGroup(), Side.RIGHT);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel());
            var answer = new CompletableFuture<Boolean>();
            model.tabs().guard("dev.example.editor", (tabs, batch) -> answer);
            var asking = view.requestClose();
            assertThat(asking).as("the guard has not answered").isNotDone();
            window.closeGroup(a1);
            assertThat(a1.closed()).as("terminal tabs need no approval").isTrue();
            assertThat(a2.closed()).isTrue();
            assertThat(window.tabs()).containsExactly(b1, view);
            answer.complete(false);
            assertThat(asking).isCompletedWithValue(false);
            assertThat(view.closed()).isFalse();
        });
    }

    @Test void closeTabAndCloseGroupRunFromTheirShortcuts() throws Exception {
        var window = window(3);
        edt(() -> {
            var workspace = window.workspace();
            var b1 = window.tabs().get(2);
            workspace.moveToNewGroup(b1, workspace.model().activeGroup(), Side.RIGHT);
            var a1 = window.tabs().get(0); var a2 = window.tabs().get(1);
            window.selectTab(a1);
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_TAB).orElseThrow(), window.currentPane())).isTrue();
            assertThat(window.tabs()).containsExactly(a2, b1);
            window.selectTab(a2);
            assertThat(window.dispatchShortcut(window.bindings().strokeFor(ActionId.CLOSE_GROUP).orElseThrow(), window.currentPane())).isTrue();
            assertThat(window.tabs()).containsExactly(b1);
        });
    }

    @Test void theFileViewAndTabMenusNameTabsAndGroups() throws Exception {
        var window = window(1);
        edt(() -> {
            assertThat(items(window, "File")).containsSubsequence("New Tab", "New Window", "Open File…", "Close Tab", "Close Group");
            assertThat(items(window, "View")).contains("Maximize Group");
            assertThat(items(window, "Tab")).containsExactly("Split Right", "Split Down", "Focus Group Left", "Focus Group Right",
                "Focus Group Up", "Focus Group Down", "Previous Prompt", "Next Prompt", "Next Tab", "Previous Tab", "Rename Tab",
                "Select Tab 1", "Select Tab 2", "Select Tab 3", "Select Tab 4", "Select Tab 5", "Select Tab 6", "Select Tab 7",
                "Select Tab 8", "Select Tab 9");
        });
    }
}
