package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.closeOwners;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 8 and 13, parent tabs spec 9: what {@code workspace.toml} keeps of the open windows. */
class WorkspaceSnapshotTest {
    @TempDir Path folder;
    private TestWindows windows;

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    private void windows() throws Exception { edt(() -> windows = new TestWindows()); }

    @Test void aSnapshotKeepsEachWindowsPlaceParentTabsChildWindowsAndTerminalTabs() throws Exception {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path logs = Files.createDirectories(folder.resolve("logs"));
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", work).orElseThrow();
            Tab first = main.content().selectedTab();
            main.content().openTab(logs, null, Optional.of("zsh"));
            Tab tail = main.content().selectedTab();
            ((TerminalContent) tail.content()).rename("tail");
            main.content().split(Side.RIGHT);
            ParentTabId firstParent = main.content().activeParentTab();
            main.content().newParentTab(work);
            main.placed(new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6));
            ChildWindow full = windows.manager.openTerminalWindow(work).orElseThrow();
            ChildWindow moved = windows.manager.moveToNewWindow(tail).orElseThrow();
            WorkspaceFile.Snapshot snapshot = windows.manager.snapshot().orElseThrow();
            assertThat(snapshot.windows()).hasSize(3);
            WorkspaceFile.WindowRecord saved = snapshot.main();
            assertThat(saved.role()).isEqualTo(WorkspaceFile.Role.MAIN);
            assertThat(saved.bounds()).contains(new UiState.Bounds(40, 60, 1400, 900));
            assertThat(saved.extendedState()).isEqualTo(6);
            assertThat(saved.layout()).as("its parent tabs in bar order, the one that shows, their splits, groups, order and selection")
                .isEqualTo(main.content().windowLayout());
            assertThat(((ParentTabs) saved.layout()).parentTabs()).extracting(ParentTab::id).first().isEqualTo(firstParent);
            assertThat(saved.tabs().get(first.id())).isEqualTo(new WorkspaceFile.TabRecord.Terminal("", work.toString(), ""));
            assertThat(snapshot.children()).extracting(WorkspaceFile.WindowRecord::layout)
                .as("two full windows, in the order they opened")
                .containsExactly(full.content().windowLayout(), moved.content().windowLayout());
            WorkspaceFile.WindowRecord popped = snapshot.children().get(1);
            assertThat(popped.role()).isEqualTo(WorkspaceFile.Role.CHILD);
            assertThat(popped.tabs().get(tail.id())).as("renamed, in its directory, with its profile")
                .isEqualTo(new WorkspaceFile.TabRecord.Terminal("tail", logs.toString(), "zsh"));
            assertThat(popped.bounds()).as("its frame has not said where it is").isEmpty();
        });
        windows.launch();
    }

    @Test void tabsThatCannotComeBackAreTakenOutAsClosingThemWouldLeaveTheirWindows() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            Tab terminal = main.content().selectedTab();
            ParentTabId first = main.content().activeParentTab();
            Tab notes = windows.model.tabs().open("dev.example.notes", "todo", "Todo", main.id(), new javax.swing.JPanel());
            // Jasper's own Errors tab, alone in a full window (workspace plan 4b's app-owned tab).
            Tab errors = windows.model.tabs().open(TabRegistry.APP_OWNER, "errors", "Errors", main.id(), new javax.swing.JPanel());
            errors.moveToNewWindow();
            assertThat(windows.manager.children()).hasSize(1);
            main.content().newParentTab(folder);
            Tab other = main.content().selectedTab();
            Tab away = windows.model.tabs().open("dev.example.notes", "away", "Away", main.id(), new javax.swing.JPanel());
            main.content().closeTab(other);
            assertThat(main.content().parentTabCount()).as("the parent tab that shows holds a plugin's tab alone").isEqualTo(2);
            WorkspaceFile.Snapshot snapshot = windows.manager.snapshot().orElseThrow();
            var saved = (ParentTabs) snapshot.main().layout();
            assertThat(saved.parentTabs()).as("a parent tab holding only such tabs is left out").extracting(ParentTab::id).containsExactly(first);
            assertThat(saved.active()).as("and the one used before it shows, as closing its tabs would").isEqualTo(first);
            assertThat(saved.tabs()).as("a plugin's tab with no restorer is not saved").containsExactly(terminal.id());
            assertThat(snapshot.main().tabs()).containsOnlyKeys(terminal.id());
            assertThat(snapshot.children()).as("a child window holding only Jasper's own tab is left out").isEmpty();
            assertThat(List.of(notes, away, errors)).as("taking them out of what is saved closes nothing").noneMatch(Tab::closed);
            terminal.close();
            assertThat(((ParentTabs) windows.manager.snapshot().orElseThrow().main().layout()).isEmpty())
                .as("the main window may be saved empty").isTrue();
        });
        windows.launch();
    }

    /** A window that is closing, its content closed and its closed hook still to run, keeps nothing (Task 4's review). */
    @Test void aWindowThatIsClosingIsNotInTheSnapshot() throws Exception {
        windows();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            ChildWindow child = windows.manager.openTerminalWindow(folder).orElseThrow();
            var during = new ArrayList<WorkspaceFile.Snapshot>();
            child.content().selectedTab().onClosed(() -> during.add(windows.manager.snapshot().orElseThrow()));
            child.requestClose();
            assertThat(windows.manager.children()).isEmpty();
            assertThat(during).singleElement().as("taken as the closing window's tab closed")
                .satisfies(snapshot -> assertThat(snapshot.children()).isEmpty());
            var closing = new ArrayList<Optional<WorkspaceFile.Snapshot>>();
            main.content().selectedTab().onClosed(() -> closing.add(windows.manager.snapshot()));
            windows.manager.closeAll();
            assertThat(closing).as("a main window that is closing is no main window").containsExactly(Optional.empty());
        });
        windows.launch();
    }

    @Test void aProgramTabIsSavedAsAShellAndNothingIsSavedWithoutAMainWindow() throws Exception {
        windows();
        edt(() -> {
            assertThat(windows.manager.snapshot()).as("no main window").isEmpty();
            MainWindow main = windows.manager.ensureMain("cold", folder).orElseThrow();
            main.content().openTab(folder, null, Optional.empty(), Optional.of(List.of("top")));
            Tab program = main.content().selectedTab();
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(program.id()))
                .as("the program is not saved: the tab comes back as a plain shell")
                .isEqualTo(new WorkspaceFile.TabRecord.Terminal("", folder.toString(), ""));
            windows.manager.closeAll();
            assertThat(windows.manager.snapshot()).isEmpty();
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void aShellOnAnotherHostIsSavedAsWhereANewShellStarts() throws Exception {
        // The shell reports a directory on another host, as one does after ssh: that host's directory means nothing here.
        edt(() -> windows = new TestWindows(directory -> {
            try {
                return TerminalSession.start(SessionLaunchOptions.builder()
                    .command(List.of("/bin/sh", "-c", "printf '\\033]7;file://build-host.example/srv/app\\007'; read answer"))
                    .environment(System.getenv()).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        }));
        Tab[] tab = new Tab[1];
        edt(() -> tab[0] = windows.manager.ensureMain("cold", folder).orElseThrow().content().selectedTab());
        windows.launch();
        until(() -> ((TerminalContent) tab[0].content()).pane().snapshot().remoteDirectory().isPresent());
        edt(() -> assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(tab[0].id()))
            .isEqualTo(new WorkspaceFile.TabRecord.Terminal("", "", "")));
    }
}
