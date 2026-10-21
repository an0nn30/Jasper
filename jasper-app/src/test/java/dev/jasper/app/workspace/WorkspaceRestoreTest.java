package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.HOME;
import static dev.jasper.app.workspace.DesktopTestSupport.closeOwners;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.terminals.PaneSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Workspace tabs spec 13, tabs and panes spec 12: the main window and its full windows come back as they were saved,
 * with their tabs, splits and panes; only the shown tab's terminals start, the others when their tab first shows.
 */
class WorkspaceRestoreTest {
    @TempDir Path folder;
    private TestWindows windows;
    private WorkspaceStore store;
    private final Queue<Path> setAside = new ArrayDeque<>();
    private final Queue<Runnable> due = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    private Path file() { return folder.resolve("workspace.toml"); }

    private void windows() throws Exception {
        edt(() -> {
            windows = new TestWindows();
            store = new WorkspaceStore(file(), windows.manager::snapshot, (Duration delay, Runnable task) -> { due.add(task); return () -> due.remove(task); },
                Runnable::run, setAside::add);
            windows.manager.workspace(store);
        });
    }

    /** A pane holding {@code tab}, its one item. */
    private static Group pane(TabId tab) {
        return new Group(GroupId.random(), List.of(tab), Optional.of(tab));
    }

    private static WorkspaceFile.TabRecord.Terminal shell(String title, Path directory, String profile) {
        return new WorkspaceFile.TabRecord.Terminal(title, directory.toString(), profile);
    }

    /** A tab of its own id holding {@code layout}. */
    private static ParentTab tab(Layout layout) {
        return new ParentTab(ParentTabId.random(), layout);
    }

    /** A main window saved without a place, holding {@code tabs} with the first showing. */
    private static WorkspaceFile.WindowRecord mainWindow(Map<TabId, WorkspaceFile.TabRecord> items, ParentTab... tabs) {
        return new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0, new ParentTabs(List.of(tabs), tabs[0].id()), items);
    }

    /** A main window saved without a place, holding one tab whose one pane holds {@code item}. */
    private static WorkspaceFile.WindowRecord mainWindow(TabId item, WorkspaceFile.TabRecord record) {
        return mainWindow(Map.of(item, record), tab(Layout.of(item)));
    }

    @Test void theMainWindowAndItsFullWindowsComeBackAsTheyWere() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        TabId build = TabId.random(), tail = TabId.random(), shell = TabId.random(), hidden = TabId.random();
        TabId away = TabId.random(), other = TabId.random();
        Group left = pane(build), upper = pane(tail), lower = pane(shell);
        var rows = new Split(Orientation.VERTICAL, List.of(new Child(upper, 0.4), new Child(lower, 0.6)));
        var split = new Layout(new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.3), new Child(rows, 0.7))), lower.id(), Optional.empty());
        ParentTab first = tab(split), second = tab(Layout.of(hidden));
        var mainTabs = new ParentTabs(List.of(first, second), first.id());
        var main = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6, mainTabs,
            Map.of(build, shell("build", work, "zsh"), tail, shell("tail", logs, ""), shell, shell("", work, ""), hidden, shell("", logs, "")));
        ParentTab fullFirst = tab(Layout.of(away)), fullSecond = tab(Layout.of(other));
        var full = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.of(new UiState.Bounds(900, 100, 800, 600)), 0,
            new ParentTabs(List.of(fullFirst, fullSecond), fullSecond.id()), Map.of(away, shell("", logs, ""), other, shell("", work, "")));
        var saved = new WorkspaceFile.Snapshot(List.of(main, full));
        WorkspaceFile.write(file(), saved);
        windows();
        edt(() -> {
            MainWindow restored = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(restored.content().windowLayout()).as("its tabs in strip order, the one that shows, their splits, panes and active panes")
                .isEqualTo(mainTabs);
            assertThat(restored.placement()).isEqualTo(new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6));
            assertThat(restored.content().tabs()).extracting(Tab::id).containsExactly(build, tail, shell, hidden);
            assertThat(restored.content().selectedTab().id()).as("the active pane's item").isEqualTo(shell);
            var titles = restored.content().tabs().stream().map(item -> ((TerminalContent) item.content()).renamed().orElse("")).toList();
            assertThat(titles).containsExactly("build", "tail", "", "");
            assertThat(((TerminalContent) restored.content().tabs().getFirst().content()).pane().profile()).contains("zsh");
            List<ChildWindow> children = windows.manager.children();
            assertThat(children).hasSize(1);
            assertThat(children.getFirst().content().windowLayout()).as("a full window, the tab it showed shown").isEqualTo(full.layout());
            assertThat(children.getFirst().placement().bounds()).contains(new UiState.Bounds(900, 100, 800, 600));
            assertThat(windows.manager.snapshot()).as("saved again, it is what was read").contains(saved);
            assertThat(windows.events).as("every frame shown, the main window brought to the front last")
                .containsSubsequence("show main", "opened main", "show child", "opened child", "front main");
        });
        windows.launch();
        assertThat(windows.started).as("every pane of each window's shown tab: build, tail and shell, then the full window's other")
            .containsExactly(work, logs, work, work);
    }

    /**
     * Tabs and panes spec 12, workspace plan 6a Rulings 4 and 37: a hidden tab's terminals wait until their tab shows,
     * then every pane of it starts, the one its maximize hides too.
     */
    @Test void aHiddenTabsTerminalsWaitAndStartWhenItFirstShows() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        TabId front = TabId.random(), back = TabId.random(), beside = TabId.random();
        Layout backLayout = Layout.of(back);
        Layout split = backLayout.split(beside, backLayout.activeGroup(), Side.RIGHT);
        ParentTab shown = tab(Layout.of(front)), hidden = tab(split.toggleMaximize(split.activeGroup()));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(Map.of(front, shell("", work, ""), back, shell("", logs, ""),
            beside, shell("", logs, "")), shown, hidden))));
        windows();
        Tab[] waiting = new Tab[1];
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().windowLayout().parentTabs().get(1).layout().maximized()).as("the hidden tab keeps its maximize").isPresent();
            waiting[0] = main.content().tabs().get(1);
            var pane = ((TerminalContent) waiting[0].content()).pane();
            assertThat(pane.snapshot().state()).as("the registry reports it not started").isEqualTo(PaneSnapshot.State.NOT_STARTED);
            var entry = windows.terminals.tab(waiting[0].id().value()).orElseThrow().terminal().get().orElseThrow();
            assertThat(entry.snapshot().get().state()).isEqualTo(PaneSnapshot.State.NOT_STARTED);
        });
        windows.launch();
        assertThat(windows.started).as("only the shown tab's").containsExactly(work);
        edt(() -> windows.manager.main().orElseThrow().content().selectTab(waiting[0]));
        windows.launch();
        assertThat(windows.started).as("shown, both of its panes start, the one the maximize hides too").containsExactly(work, logs, logs);
    }

    @Test void aSavedDirectoryThatIsGoneStartsWhereANewShellStarts() throws Exception {
        TabId item = TabId.random();
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(item, shell("", folder.resolve("deleted"), "")))));
        windows();
        edt(() -> windows.manager.ensureMain("cold", null).orElseThrow());
        windows.launch();
        assertThat(windows.started).containsExactly(HOME);
        edt(() -> assertThat(windows.manager.main().orElseThrow().content().tabCount()).as("the item stays").isEqualTo(1));
    }

    @Test void aFreshStartOpensOneTerminalTabAndAnUnreadableFileIsSetAsideAndTold() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            assertThat(main.content().tabCount()).isEqualTo(1);
            windows.manager.closeAll();
        });
        windows.launch();
        assertThat(windows.started).as("no file: the request's directory").containsExactly(work);
        assertThat(setAside).isEmpty();
        Files.writeString(file(), "version = 1\n[[windows]]\nrole = ");
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().tabCount()).isEqualTo(1);
        });
        assertThat(setAside).containsExactly(folder.resolve("workspace.toml.bad"));
    }

    /** Tabs and panes spec 4 and 12: the main window saved as its empty workspace comes back empty, under its saved ids. */
    @Test void theEmptyWorkspaceComesBack() throws Exception {
        var empty = ParentTabs.of(tab(Layout.empty()));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(),
            0, empty, Map.of()))));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().windowLayout()).isEqualTo(empty);
            assertThat(main.content().tabCount()).isZero();
            assertThat(main.content().workspace().emptyWorkspace()).as("New Terminal Tab and Open File show").isNotNull();
        });
        windows.launch();
        assertThat(windows.started).isEmpty();
    }

    /**
     * Tabs and panes spec 13: a request's directory opens in a new tab after the restored ones, which shows; the restored
     * tab that showed before waits as a hidden tab does (workspace plan 6a Rulings 4 and 31).
     */
    @Test void aRestoredMainWindowOpensTheRequestsDirectoryInANewTab() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        Path asked = Files.createDirectories(folder.resolve("asked"));
        TabId back = TabId.random(), front = TabId.random();
        ParentTab hidden = tab(Layout.of(back)), shown = tab(Layout.of(front));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            new ParentTabs(List.of(hidden, shown), shown.id()), Map.of(back, shell("", logs, ""), front, shell("", work, ""))))));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", asked).orElseThrow();
            assertThat(main.content().tabs()).extracting(Tab::id).as("after the restored ones").startsWith(back, front).hasSize(3);
            assertThat(main.content().parentTabCount()).as("in a tab of its own").isEqualTo(3);
            assertThat(main.content().selectedTab()).as("which shows").isSameAs(main.content().tabs().getLast());
        });
        windows.launch();
        assertThat(windows.started).as("the request's tab alone: the restored tabs wait to be shown").containsExactly(asked);
    }

    @Test void aChildWindowWithNothingToRestoreIsLeftOut() throws Exception {
        TabId item = TabId.random(), document = TabId.random();
        var plugin = (WorkspaceFile.TabRecord) new WorkspaceFile.TabRecord.Content("notes.md", "dev.example.editor", "document", Map.of("path", "/notes.md"));
        var full = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0, ParentTabs.of(tab(Layout.of(document))),
            Map.of(document, plugin));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(item, shell("", folder, "")), full)));
        windows();
        edt(() -> {
            windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(windows.manager.children()).as("a full window holding only a plugin's item (plan 6b restores those)").isEmpty();
            assertThat(windows.events).doesNotContain("show child");
        });
    }

    /** Tabs and panes spec 12: a tab whose entry cannot be read is dropped alone, and the file is kept aside and told. */
    @Test void aTabWhoseEntryCannotBeReadIsDroppedAloneAndTheFileIsKeptAside() throws Exception {
        TabId first = TabId.random(), second = TabId.random();
        ParentTab kept = tab(Layout.of(first)), broken = tab(Layout.of(second));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(Map.of(first, shell("", folder, ""), second, shell("", folder, "")),
            kept, broken))));
        String text = Files.readString(file());
        Files.writeString(file(), text.replace("item = \"" + second.value() + "\"", "item = 3"));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().windowLayout().parentTabs()).extracting(ParentTab::id).containsExactly(kept.id());
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(first);
        });
        assertThat(setAside).as("told once, for the notice").containsExactly(folder.resolve("workspace.toml.bad"));
        windows.launch();
    }

    /** A tab already registered under {@code item}'s id makes a restore that rebuilds it fail, as a bug would. */
    private void taken(TabId item) {
        windows.model.tabs().add(item, Tab.Kind.TERMINAL, new dev.jasper.app.contributions.TabContent() {
            @Override public javax.swing.JComponent component() { return new javax.swing.JPanel(); }
            @Override public void dispose() { }
        }, java.util.UUID.randomUUID());
    }

    private WorkspaceFile.Snapshot keptAside() {
        return ((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(folder.resolve("workspace.toml.bad"))).snapshot();
    }

    @Test void aMainWindowThatCannotBeRestoredOpensANewOneAndTheSavedWindowsAreKept() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        TabId item = TabId.random();
        var saved = new WorkspaceFile.Snapshot(List.of(mainWindow(item, shell("", folder, ""))));
        WorkspaceFile.write(file(), saved);
        windows();
        edt(() -> {
            taken(item);
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            assertThat(main.content().tabs()).hasSize(1);
            assertThat(main.content().tabs().getFirst().id()).isNotEqualTo(item);
            assertThat(windows.events).as("it opens as a new main window, never closed: the last window's closed hook can end Jasper")
                .containsExactly("show main", "opened main");
        });
        assertThat(setAside).as("told, for the notice").containsExactly(folder.resolve("workspace.toml.bad"));
        assertThat(keptAside()).as("not lost to the next save").isEqualTo(saved);
        windows.launch();
        assertThat(windows.started).as("the failed item's shell never started").containsExactly(work);
    }

    @Test void aChildWindowThatCannotBeRestoredIsLeftOutAndTheSavedWindowsAreKept() throws Exception {
        TabId shell = TabId.random(), away = TabId.random();
        var saved = new WorkspaceFile.Snapshot(List.of(mainWindow(shell, shell("", folder, "")),
            new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0, ParentTabs.of(tab(Layout.of(away))),
                Map.of(away, shell("", folder, "")))));
        WorkspaceFile.write(file(), saved);
        windows();
        edt(() -> {
            taken(away);
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
            assertThat(windows.manager.children()).isEmpty();
        });
        assertThat(setAside).containsExactly(folder.resolve("workspace.toml.bad"));
        assertThat(keptAside()).isEqualTo(saved);
        windows.launch();
    }

    /** A main window and a full window, each saved with one terminal in {@code folder}. */
    private WorkspaceFile.Snapshot mainAndFullWindow(TabId shell, TabId away) {
        return new WorkspaceFile.Snapshot(List.of(mainWindow(shell, shell("", folder, "")),
            new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0, ParentTabs.of(tab(Layout.of(away))),
                Map.of(away, shell("", folder, "")))));
    }

    /**
     * The restore of the main window, or of a full window, fails once its items are mounted, as a listener's bug would:
     * its first title change while it holds an item throws {@code failure}, once.
     */
    private void failOnceMounted(boolean main, Throwable failure) {
        java.util.function.Consumer<WindowContent> before = windows.composed;
        windows.composed = content -> {
            before.accept(content);
            if (content.isMain() != main) return;
            boolean[] armed = {true};
            content.onTitle = title -> {
                if (!armed[0] || content.tabCount() == 0) return;
                armed[0] = false;
                if (failure instanceof Error error) throw error;
                throw (RuntimeException) failure;
            };
        };
    }

    /**
     * Workspace plan 6a Task 8's review: a full window whose restore fails once its items are mounted is discarded unshown,
     * never closed as a window that opened is, and the saved windows are kept.
     */
    @Test void aFullWindowWhoseRestoreFailsOnceItsItemsAreMountedIsDiscardedUnshown() throws Exception {
        TabId shell = TabId.random(), away = TabId.random();
        var saved = mainAndFullWindow(shell, away);
        WorkspaceFile.write(file(), saved);
        windows();
        edt(() -> {
            failOnceMounted(false, new IllegalStateException("a listener's bug"));
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
            assertThat(windows.manager.children()).isEmpty();
            assertThat(windows.events).as("no closing hook for a window that never opened")
                .containsExactly("show main", "opened main", "closed child");
            assertThat(windows.model.tabs().tab(away)).as("its item went with it").isEmpty();
        });
        assertThat(setAside).containsExactly(folder.resolve("workspace.toml.bad"));
        assertThat(keptAside()).isEqualTo(saved);
        windows.launch();
        assertThat(windows.started).as("the main window's shell alone").containsExactly(folder);
    }

    /**
     * Workspace plan 6a Task 8's review: an Error from a full window's restore costs only that window; the main window
     * opens, and a tab that waited for it opens there.
     */
    @Test void anErrorFromAFullWindowsRestoreCostsOnlyThatWindow() throws Exception {
        TabId shell = TabId.random(), away = TabId.random();
        var saved = mainAndFullWindow(shell, away);
        WorkspaceFile.write(file(), saved);
        windows();
        Tab[] early = new Tab[1];
        edt(() -> {
            // Asked for while the main window is composed, it waits for it (TabRegistry.mainOpened).
            windows.composed = content -> {
                if (content.isMain()) early[0] = windows.model.tabs().open("dev.example.editor", "early", "early.txt", null, new javax.swing.JPanel());
            };
            failOnceMounted(false, new AssertionError("a bug"));
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(windows.manager.children()).isEmpty();
            assertThat(early[0].closed()).as("the tab that waited for the main window").isFalse();
            assertThat(early[0].window()).as("opened there").isEqualTo(main.id());
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell, early[0].id());
        });
        assertThat(setAside).containsExactly(folder.resolve("workspace.toml.bad"));
        assertThat(keptAside()).isEqualTo(saved);
        windows.launch();
    }

    /**
     * Workspace plan 6a Task 8's review and Rulings 20: an Error from the main window's restore opens it as a new main
     * window, never discarded, and the saved windows are kept aside, so the next start does not meet it again.
     */
    @Test void anErrorFromTheMainWindowsRestoreOpensItAsANewOneAndKeepsTheSavedWindows() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        TabId item = TabId.random();
        var saved = new WorkspaceFile.Snapshot(List.of(mainWindow(item, shell("", folder, ""))));
        WorkspaceFile.write(file(), saved);
        windows();
        edt(() -> {
            failOnceMounted(true, new AssertionError("a bug"));
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            assertThat(main.content().tabs()).hasSize(1);
            assertThat(main.content().tabs().getFirst().id()).isNotEqualTo(item);
            assertThat(main.content().isPreferredSizeSet()).as("sized as a new main window is").isFalse();
            assertThat(windows.events).as("never discarded: the last window's closed hook can end Jasper")
                .containsExactly("show main", "opened main");
        });
        assertThat(setAside).containsExactly(folder.resolve("workspace.toml.bad"));
        assertThat(keptAside()).isEqualTo(saved);
        windows.launch();
        assertThat(windows.started).as("the failed item's shell never started").containsExactly(work);
    }

    /** Workspace plan 6a Task 8's review: a pane whose start throws costs only itself; the other panes of its tab start. */
    @Test void aPaneWhoseStartThrowsLeavesTheOtherPanesOfItsTabStarting() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        TabId first = TabId.random(), second = TabId.random();
        Layout one = Layout.of(first);
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(Map.of(first, shell("", work, ""),
            second, shell("", logs, "")), tab(one.split(second, one.activeGroup(), Side.RIGHT))))));
        windows();
        boolean[] threw = new boolean[1];
        edt(() -> {
            // Only an Error leaves a pane's start: the launcher reports any other failure through the launch's completion.
            windows.launching = () -> {
                if (threw[0]) return;
                threw[0] = true;
                throw new AssertionError("a launch that throws");
            };
            windows.manager.ensureMain("cold", null).orElseThrow();
        });
        windows.launch();
        assertThat(threw[0]).as("the first pane's start threw").isTrue();
        assertThat(windows.started).as("the second pane started all the same").containsExactly(logs);
    }

    /** Workspace plan 6a Task 8's review: a restored window gives up the stand-in size of a window opened empty. */
    @Test void aRestoredWindowIsSizedAsANewWindowIs() throws Exception {
        WorkspaceFile.write(file(), mainAndFullWindow(TabId.random(), TabId.random()));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().isPreferredSizeSet()).isFalse();
            assertThat(windows.manager.children()).singleElement()
                .satisfies(child -> assertThat(child.content().isPreferredSizeSet()).isFalse());
        });
        windows.launch();
    }

    /**
     * Workspace plan 6a Rulings 31: Move to Main Window moves a full window's tabs one at a time, each showing for an
     * instant; only the one that shows when the move ends starts, and the tabs it passed over still wait.
     */
    @Test void movingARestoredFullWindowToTheMainWindowStartsOnlyTheTabThatShowsThere() throws Exception {
        TabId front = TabId.random(), first = TabId.random(), second = TabId.random(), third = TabId.random();
        ParentTab one = tab(Layout.of(first)), two = tab(Layout.of(second)), three = tab(Layout.of(third));
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(List.of(mainWindow(front, shell("", folder, "")),
            new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0, new ParentTabs(List.of(one, two, three), one.id()),
                Map.of(first, shell("", folder, ""), second, shell("", folder, ""), third, shell("", folder, ""))))));
        windows();
        edt(() -> windows.manager.ensureMain("cold", null).orElseThrow());
        windows.launch();
        assertThat(windows.started).as("each window's shown tab").hasSize(2);
        edt(() -> {
            MainWindow main = windows.manager.main().orElseThrow();
            assertThat(windows.manager.moveToMainWindow(windows.manager.children().getFirst().content())).containsSame(main);
            assertThat(main.content().selectedTab().id()).as("the tab the full window showed shows").isEqualTo(first);
        });
        windows.launch();
        assertThat(windows.started).as("the tabs the move passed over never started").hasSize(2);
        edt(() -> assertThat(windows.manager.main().orElseThrow().content().tabs().stream()
            .map(item -> ((TerminalContent) item.content()).pane().waiting()).toList())
            .as("front and first started at the restore; second and third wait to be shown").containsExactly(false, false, true, true));
    }

    /** Workspace plan 6a Rulings 17: only the panes of the tab that shows count, the maximized one alone while one is. */
    @Test void aWindowShowsNoTerminalWhenAMaximizedPaneOrAHiddenTabHidesTheOneThatStarted() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            assertThat(main.content().showsNoTerminal()).isFalse();
            Tab notes = windows.model.tabs().open("dev.example.editor", "notes", "notes.md", main.id(), new javax.swing.JPanel(),
                dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_RIGHT);
            main.content().selectTab(notes);
            assertThat(main.content().showsNoTerminal()).as("the terminal's pane still shows").isFalse();
            main.content().toggleMaximize();
            assertThat(main.content().showsNoTerminal()).as("only the maximized pane shows").isTrue();
            main.content().toggleMaximize();
            windows.model.tabs().open("dev.example.editor", "more", "more.md", main.id(), new javax.swing.JPanel());
            assertThat(main.content().showsNoTerminal()).as("the tab that shows holds a plugin's item alone").isTrue();
        });
        windows.launch();
    }

    @Test void reopeningTheMainWindowRestoresWhatWasSavedWhenItClosed() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        windows();
        TabId[] saved = new TabId[3];
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            main.content().newTab(work);
            main.content().split(Side.RIGHT);
            saved[0] = main.content().tabs().get(0).id();
            saved[1] = main.content().tabs().get(1).id();
            saved[2] = main.content().tabs().get(2).id();
            windows.manager.openTerminalWindow(work);
            windows.manager.closeAll();
            assertThat(store.held()).as("closing every window saved them first").isTrue();
            MainWindow again = windows.manager.ensureMain("reopen", null).orElseThrow();
            assertThat(again.content().tabs()).extracting(Tab::id).containsExactly(saved);
            assertThat(again.content().windowLayout().parentTabs()).as("its tabs, the second split in two").hasSize(2);
            assertThat(windows.manager.children()).as("its full window").hasSize(1);
        });
    }
}
