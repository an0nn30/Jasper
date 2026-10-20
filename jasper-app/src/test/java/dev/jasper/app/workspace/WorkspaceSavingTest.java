package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.closeOwners;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.terminals.TerminalEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Workspace tabs spec 13, parent tabs spec 9: what is saved, and when: within a second of each change, and at once when a
 * window closes.
 */
class WorkspaceSavingTest {
    @TempDir Path folder;
    private TestWindows windows;
    private WorkspaceStore store;
    /** The saves changes scheduled; {@link #second()} runs them, as their second passing does. */
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
                Runnable::run, kept -> { });
            windows.manager.workspace(store);
        });
    }

    /** One scheduled save is waiting, and it saves now. */
    private void second() {
        assertThat(due).as("a change scheduled one save").hasSize(1);
        due.remove().run();
    }

    private WorkspaceFile.Snapshot saved() { return ((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(file())).snapshot(); }

    /** The saved main window's shown parent tab's layout. */
    private Layout shown() { return saved().main().layout().activeParentTab().layout(); }

    @Test void everyChangeToWhatIsSavedIsSavedWithinASecond() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            second();
            assertThat(saved().main().layout()).as("the window opening").isEqualTo(main.content().windowLayout());
            Tab first = main.content().selectedTab();
            main.content().newTab(folder);
            second();
            assertThat(saved().main().layout().tabs()).as("a tab opening").hasSize(2);
            main.content().split(Side.RIGHT);
            second();
            assertThat(shown().groups()).as("a split").hasSize(2);
            main.content().workspace().resizeSplit(List.of(), List.of(0.25, 0.75));
            second();
            assertThat(shown()).as("a divider drag").isEqualTo(main.content().workspace().model());
            main.content().toggleMaximize();
            second();
            assertThat(shown().maximized()).as("a maximize").isPresent();
            main.content().toggleMaximize();
            second();
            main.content().selectTab(first);
            second();
            assertThat(shown().activeTab()).as("a selection").contains(first.id());
            var terminal = (TerminalContent) first.content();
            terminal.rename("build");
            second();
            assertThat(saved().main().tabs().get(first.id()).title()).as("a rename").isEqualTo("build");
            terminal.pane().onDirectoryChanged.accept(Optional.of(folder), Optional.empty());
            second();
            main.placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(10, 20, 1200, 800)), 0));
            second();
            assertThat(saved().main().bounds()).as("the window moving").contains(new UiState.Bounds(10, 20, 1200, 800));
            main.content().closeTab(first);
            second();
            assertThat(saved().main().layout().tabs()).as("a tab closing").doesNotContain(first.id());
            main.content().newTab(folder);
            main.content().newTab(folder);
            assertThat(due).as("changes within the second join one save").hasSize(1);
            second();
            ParentTabId shown = main.content().activeParentTab();
            int count = main.content().parentTabCount();
            main.content().newTab(folder);
            second();
            assertThat(saved().main().layout().parentTabs()).as("a tab opening").hasSize(count + 1);
            main.content().selectParentTab(shown);
            second();
            assertThat(saved().main().layout().active()).as("a tab showing").isEqualTo(shown);
            ParentTabId opened = saved().main().layout().parentTabs().getLast().id();
            main.content().parentTabs().reorder(shown, count);
            second();
            assertThat(saved().main().layout().parentTabs().get(count).id()).as("a tab moving along its strip").isEqualTo(shown);
            main.content().closeParentTab(opened);
            second();
            assertThat(saved().main().layout().parentTabs()).as("a tab closing").hasSize(count);
            ChildWindow full = windows.manager.moveToNewWindow(main.content().selectedTab()).orElseThrow();
            second();
            assertThat(saved().children()).as("an item moving into a window of its own").singleElement()
                .satisfies(record -> assertThat(record.layout()).isEqualTo(full.content().windowLayout()));
            windows.manager.moveToMainWindow(full.content());
            assertThat(saved().children()).as("Move to Main Window: its window closing is saved at once").isEmpty();
            second();
            assertThat(saved().main().layout()).as("and where its tab came back, within the second").isEqualTo(main.content().windowLayout());
        });
        windows.launch();
    }

    @Test void aChildWindowClosedAloneIsSavedWithoutItAtOnce() throws Exception {
        windows();
        edt(() -> {
            windows.manager.ensureMain("cold", folder).orElseThrow();
            ChildWindow child = windows.manager.openTerminalWindow(folder).orElseThrow();
            second();
            assertThat(saved().children()).hasSize(1);
            child.requestClose();
            assertThat(saved().children()).as("saved at once, its tabs closed with it").isEmpty();
            assertThat(due).isEmpty();
        });
        windows.launch();
    }

    @Test void aSnapshotThatFailsNeverKeepsAWindowFromClosing() throws Exception {
        edt(() -> {
            windows = new TestWindows();
            store = new WorkspaceStore(file(), () -> { throw new IllegalStateException("a window cannot say what it holds"); },
                (Duration delay, Runnable task) -> { due.add(task); return () -> due.remove(task); }, Runnable::run, kept -> { });
            windows.manager.workspace(store);
            windows.manager.ensureMain("cold", folder).orElseThrow();
            windows.manager.openTerminalWindow(folder).orElseThrow().requestClose();
            windows.manager.closeAll();
            assertThat(windows.events).as("the closed hook heard every window").contains("closed child", "closed main");
            assertThat(windows.manager.isEmpty()).isTrue();
            assertThat(store.held()).isTrue();
        });
        assertThat(file()).doesNotExist();
        windows.launch();
    }

    @Test void closingEveryWindowSavesThemFirstAndThenNothing() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            windows.manager.openTerminalWindow(folder).orElseThrow();
            main.content().newTab(folder);
            main.content().newTab(folder);
            windows.manager.moveToNewWindow(main.content().selectedTab()).orElseThrow();
            windows.manager.closeAll();
            assertThat(due).as("the save it waited for is done").isEmpty();
            assertThat(saved().children()).as("both child windows, saved before they closed").hasSize(2);
            assertThat(saved().main().layout().tabs()).hasSize(2);
            assertThat(store.held()).as("closing their tabs is not saved").isTrue();
        });
        windows.launch();
    }

    /** The file's text now. */
    private String text() {
        try {
            return Files.readString(file());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Workspace plan 6a Task 2's review: a frame that says the window is where it already is changes nothing. */
    @Test void aWindowPlacedWhereItAlreadyIsIsNoChange() throws Exception {
        windows();
        var place = new JasperWindow.Placement(Optional.of(new UiState.Bounds(10, 20, 1200, 800)), 0);
        MainWindow[] main = new MainWindow[1];
        edt(() -> {
            main[0] = windows.manager.ensureMain("cold", folder).orElseThrow();
            second();
            main[0].placed(place);
            second();
            main[0].placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(10, 20, 1200, 800)), 0));
            assertThat(due).as("a move or resize to where it is, or showing it again").isEmpty();
        });
        assertThatThrownBy(() -> main[0].placed(place)).as("a frame says where its window is on the EDT")
            .isInstanceOf(IllegalStateException.class).hasMessage("Windows belong to the EDT");
        windows.launch();
    }

    /** Rulings 28: a tab moving to another window is saved; a window that has closed changes nothing that is saved. */
    @Test void aMoveBetweenWindowsIsSavedAndAClosedWindowChangesNothing() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            ChildWindow full = windows.manager.openTerminalWindow(folder).orElseThrow();
            main.content().newTab(folder);
            Tab moving = main.content().selectedTab();
            second();
            windows.model.tabs().move(moving, full.id());
            second();
            assertThat(saved().children()).singleElement().as("the window it moved to")
                .satisfies(record -> assertThat(record.layout()).isEqualTo(full.content().windowLayout()));
            assertThat(saved().main().tabs()).as("the window it left").doesNotContainKey(moving.id());
            full.requestClose();
            assertThat(saved().children()).isEmpty();
            full.content().layoutChanged();
            full.placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(10, 20, 1200, 800)), 0));
            assertThat(due).as("a closed window's layout or place").isEmpty();
        });
        windows.launch();
    }

    /** Workspace plan 6a Task 4's review: a listener that fails after the model took a layout keeps no save from it. */
    @Test void aLayoutChangeIsSavedWhateverAListenerAfterTheModelThrows() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            Tab first = main.content().selectedTab();
            main.content().newTab(folder);
            second();
            var failure = new Error("a terminal listener failed");
            try (var listening = windows.terminals.onEvent(event -> { if (event instanceof TerminalEvent.TabSelected) throw failure; })) {
                assertThatThrownBy(() -> main.content().selectTab(first)).isSameAs(failure);
            }
            second();
            assertThat(shown().activeTab()).as("the selection the model took").contains(first.id());
        });
        windows.launch();
    }

    /**
     * Rulings 22: a snapshot that throws, as one does while a parent tab moving between windows is in both, costs only
     * that save: it is logged, nothing is written, and the next save writes the windows.
     */
    @Test void aSnapshotThatThrowsMidMoveCostsOnlyThatSave() throws Exception {
        windows();
        Logger logger = Logger.getLogger(WorkspaceStore.class.getName());
        var records = new ArrayList<LogRecord>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        boolean parents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            edt(() -> {
                MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
                ChildWindow full = windows.manager.openTerminalWindow(folder).orElseThrow();
                main.content().newTab(folder);
                main.content().split(Side.RIGHT);
                ParentTabId moving = main.content().activeParentTab();
                Tab last = main.content().selectedTab();
                second();
                String before = text();
                String[] during = new String[1];
                // A move listener that saves at once, as the parent tab's second tab leaves: its first is in the full
                // window already, under the parent tab's id, which the main window still holds.
                last.onChanged(() -> {
                    if (during[0] != null || !full.id().equals(last.window())) return;
                    store.save();
                    during[0] = text();
                });
                assertThat(windows.manager.moveParentTab(main.content(), moving, full.content(), 0)).isTrue();
                assertThat(during[0]).as("the save mid-move wrote nothing").isEqualTo(before);
                assertThat(store.held()).isFalse();
                second();
                assertThat(saved()).as("the next save writes the windows").isEqualTo(windows.manager.snapshot().orElseThrow());
                assertThat(saved().children().getFirst().layout().parentTabs()).extracting(p -> p.id()).contains(moving);
            });
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(parents);
        }
        assertThat(records).as("logged, not swallowed").singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.WARNING);
            assertThat(record.getThrown()).isInstanceOf(IllegalArgumentException.class);
        });
        windows.launch();
    }
}
