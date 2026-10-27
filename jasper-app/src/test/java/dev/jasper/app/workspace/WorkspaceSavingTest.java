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
import dev.jasper.app.testsupport.LogRecorder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
    /** What the store logged during the test: its warnings are asserted here, never printed. */
    private LogRecorder storeLog;

    @BeforeEach void record() {
        storeLog = LogRecorder.of(WorkspaceStore.class.getName());
    }

    @AfterEach void cleanup() throws Exception {
        try {
            if (windows != null) edt(() -> windows.manager.closeAll());
            closeOwners();
        } finally {
            storeLog.close();
        }
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
    private Layout shown() { return saved().window().layout().activeParentTab().layout(); }

    @Test void everyChangeToWhatIsSavedIsSavedWithinASecond() throws Exception {
        windows();
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", folder).orElseThrow();
            second();
            assertThat(saved().window().layout()).as("the window opening").isEqualTo(main.content().windowLayout());
            Tab first = main.content().selectedTab();
            main.content().newTab(folder);
            second();
            assertThat(saved().window().layout().tabs()).as("a tab opening").hasSize(2);
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
            assertThat(saved().window().tabs().get(first.id()).title()).as("a rename").isEqualTo("build");
            terminal.pane().onDirectoryChanged.accept(Optional.of(folder), Optional.empty());
            second();
            main.placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(10, 20, 1200, 800)), 0));
            second();
            assertThat(saved().window().bounds()).as("the window moving").contains(new UiState.Bounds(10, 20, 1200, 800));
            main.content().closeTab(first);
            second();
            assertThat(saved().window().layout().tabs()).as("a tab closing").doesNotContain(first.id());
            main.content().newTab(folder);
            main.content().newTab(folder);
            assertThat(due).as("changes within the second join one save").hasSize(1);
            second();
            ParentTabId shown = main.content().activeParentTab();
            int count = main.content().parentTabCount();
            main.content().newTab(folder);
            second();
            assertThat(saved().window().layout().parentTabs()).as("a tab opening").hasSize(count + 1);
            main.content().selectParentTab(shown);
            second();
            assertThat(saved().window().layout().active()).as("a tab showing").isEqualTo(shown);
            ParentTabId opened = saved().window().layout().parentTabs().getLast().id();
            main.content().parentTabs().reorder(shown, count);
            second();
            assertThat(saved().window().layout().parentTabs().get(count).id()).as("a tab moving along its strip").isEqualTo(shown);
            main.content().closeParentTab(opened);
            second();
            assertThat(saved().window().layout().parentTabs()).as("a tab closing").hasSize(count);
            JasperWindow full = windows.manager.moveToNewWindow(main.content().selectedTab()).orElseThrow();
            second();
            assertThat(saved().window().layout()).as("an item moving into a window of its own leaves this one")
                .isEqualTo(main.content().windowLayout());
            windows.manager.moveTabToWindow(full.content(), full.content().activeParentTab(), main.content());
            second();
            assertThat(saved().window().layout()).as("and where its tab came back, within the second").isEqualTo(main.content().windowLayout());
        });
        windows.launch();
    }

    @Test void closingAWindowSavesThatWindowAsTheLastState() throws Exception {
        windows();
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", folder).orElseThrow();
            JasperWindow child = windows.manager.openWindow(folder).orElseThrow();
            child.content().newTab(folder);
            second();
            main.events().activated().accept(true);
            second();
            var childLayout = child.content().windowLayout();
            child.requestClose();
            assertThat(saved().window().layout()).as("saved at once, the window closed, though the other is active").isEqualTo(childLayout);
        });
        windows.launch();
    }

    @Test void aSnapshotThatFailsNeverKeepsAWindowFromClosing() throws Exception {
        edt(() -> {
            windows = new TestWindows();
            store = new WorkspaceStore(file(), () -> { throw new IllegalStateException("a window cannot say what it holds"); },
                (Duration delay, Runnable task) -> { due.add(task); return () -> due.remove(task); }, Runnable::run, kept -> { });
            windows.manager.workspace(store);
            windows.manager.ensureWindow("cold", folder).orElseThrow();
            windows.manager.openWindow(folder).orElseThrow().requestClose();
            windows.manager.closeAll();
            assertThat(windows.events).as("the closed hook heard every window").contains("closed w1", "closed w0");
            assertThat(windows.manager.isEmpty()).isTrue();
            assertThat(store.held()).isTrue();
        });
        assertThat(storeLog.warnings()).as("each save that could not take the snapshot says so").isNotEmpty()
            .containsOnly("Could not take the workspace's snapshot to save");
        windows.launch();
    }

    @Test void closingEveryWindowSavesThemFirstAndThenNothing() throws Exception {
        windows();
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", folder).orElseThrow();
            windows.manager.openWindow(folder).orElseThrow();
            main.content().newTab(folder);
            main.content().newTab(folder);
            windows.manager.moveToNewWindow(main.content().selectedTab()).orElseThrow();
            windows.manager.closeAll();
            assertThat(due).as("the save it waited for is done").isEmpty();
            assertThat(saved().window().layout().tabs()).hasSize(2);
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
        JasperWindow[] main = new JasperWindow[1];
        edt(() -> {
            main[0] = windows.manager.ensureWindow("cold", folder).orElseThrow();
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
            JasperWindow main = windows.manager.ensureWindow("cold", folder).orElseThrow();
            JasperWindow full = windows.manager.openWindow(folder).orElseThrow();
            main.content().newTab(folder);
            Tab moving = main.content().selectedTab();
            second();
            full.events().activated().accept(true);
            windows.model.tabs().move(moving, full.id());
            second();
            assertThat(saved().window().layout()).as("the window it moved to, the most recently active")
                .isEqualTo(full.content().windowLayout());
            assertThat(saved().window().tabs()).containsKey(moving.id());
            var fullLayout = full.content().windowLayout();
            full.requestClose();
            assertThat(saved().window().layout()).as("saved at once, the window closed").isEqualTo(fullLayout);
            due.clear();
            full.content().layoutChanged();
            full.placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(30, 40, 1200, 800)), 0));
            assertThat(due).as("a closed window's layout or place").isEmpty();
        });
        windows.launch();
    }

    /** Workspace plan 6a Task 4's review: a listener that fails after the model took a layout keeps no save from it. */
    @Test void aLayoutChangeIsSavedWhateverAListenerAfterTheModelThrows() throws Exception {
        windows();
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", folder).orElseThrow();
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

    @Test void aWindowAMoveEmptiesIsNotSavedAsTheLastState() throws Exception {
        windows();
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", folder).orElseThrow();
            JasperWindow other = windows.manager.openWindow(folder).orElseThrow();
            other.events().activated().accept(true);
            second();
            var before = saved().window().layout();
            assertThat(windows.manager.moveTabToWindow(other.content(), other.content().activeParentTab(), main.content())).isTrue();
            assertThat(other.closed()).isTrue();
            assertThat(saved().window().layout()).as("no write at once").isEqualTo(before);
            second();
            assertThat(saved().window().layout()).as("the debounced write is the window that is left").isEqualTo(main.content().windowLayout());
        });
        windows.launch();
    }

    @Test void aGuardThatRefusesTheCloseSavesNothing() throws Exception {
        windows();
        edt(() -> {
            windows.manager.ensureWindow("cold", folder).orElseThrow();
            JasperWindow other = windows.manager.openWindow(folder).orElseThrow();
            windows.model.tabs().open("dev.example.editor", "notes", "notes.txt", other.id(), new javax.swing.JPanel());
            windows.model.tabs().guard("dev.example.editor", (tabs, batch) -> java.util.concurrent.CompletableFuture.completedFuture(false));
            second();
            var before = saved();
            other.events().activated().accept(true);
            due.clear();
            other.requestClose();
            assertThat(other.closed()).isFalse();
            assertThat(saved()).as("no write").isEqualTo(before);
            assertThat(due).isEmpty();
        });
        windows.launch();
    }

    @Test void activatingAnotherWindowSavesItWithinASecond() throws Exception {
        windows();
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", folder).orElseThrow();
            JasperWindow other = windows.manager.openWindow(folder).orElseThrow();
            other.content().newTab(folder);
            other.events().activated().accept(true);
            second();
            assertThat(saved().window().layout()).isEqualTo(other.content().windowLayout());
            main.events().activated().accept(true);
            second();
            assertThat(saved().window().layout()).as("the file follows the most recently active window").isEqualTo(main.content().windowLayout());
        });
        windows.launch();
    }

    /** A layout of one terminal tab titled {@code title}, the rail hidden. */
    private static WorkspaceFile.WindowRecord oneTabLayout(String title) {
        var item = dev.jasper.app.layout.TabId.random();
        return new WorkspaceFile.WindowRecord(Optional.empty(), 0,
            dev.jasper.app.layout.ParentTabs.of(new dev.jasper.app.layout.ParentTab(ParentTabId.random(), Layout.of(item))),
            java.util.Map.of(item, new WorkspaceFile.TabRecord.Terminal(title, DesktopTestSupport.HOME.toString(), "")),
            new dev.jasper.app.persistence.ToolWindows(false, true, java.util.Map.of()));
    }

    /**
     * Full windows plan 4, final review: applying a layout saves the last state when it applied and when it failed
     * partway, and asks for no save when a guard refused.
     */
    @Test void applyingALayoutSavesTheLastStateUnlessAGuardRefused() throws Exception {
        windows();
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", folder).orElseThrow();
            second();
            assertThat(windows.manager.applyLayout(main, oneTabLayout("C"))).isEqualTo(WindowManager.LayoutApplied.APPLIED);
            second();
            assertThat(saved().window().tabs().values()).extracting(WorkspaceFile.TabRecord::title).containsExactly("C");
            assertThat(saved().window().tools().railVisible()).isFalse();

            Tab plugin = windows.model.tabs().open("dev.example.editor", "b", "B", main.id(), new javax.swing.JPanel());
            while (!due.isEmpty()) due.remove().run();
            windows.model.tabs().guard("dev.example.editor", (tabs, batch) -> java.util.concurrent.CompletableFuture.completedFuture(false));
            assertThat(windows.manager.applyLayout(main, oneTabLayout("D"))).isEqualTo(WindowManager.LayoutApplied.REFUSED);
            assertThat(due).as("a refusal asks for no save").isEmpty();

            windows.model.tabs().guard("dev.example.editor", (tabs, batch) -> java.util.concurrent.CompletableFuture.completedFuture(true));
            main.content().setRailVisible(true);
            while (!due.isEmpty()) due.remove().run();
            plugin.onClosed(() -> { throw new IllegalStateException("closing failed"); });
            assertThat(windows.manager.applyLayout(main, oneTabLayout("E"))).isEqualTo(WindowManager.LayoutApplied.FAILED);
            second();
            assertThat(saved().window().tools().railVisible()).as("the layout's tool windows, saved").isFalse();
        });
        windows.launch();
    }
}
