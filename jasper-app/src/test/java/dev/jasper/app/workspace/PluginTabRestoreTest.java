package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.HOME;
import static dev.jasper.app.workspace.DesktopTestSupport.closeOwners;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
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
import dev.jasper.app.layout.TabRestore;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Workspace tabs spec 9.2 and 13, tabs and panes spec 12: a plugin's saved item comes back through its restorer in its
 * saved tab and pane, in the main window and in a full window, or as a placeholder that says why.
 */
class PluginTabRestoreTest {
    private static final String NOTES = "dev.example.notes";
    @TempDir Path folder;
    private TestWindows windows;
    /** How each plugin stands, by id; a plugin not named is not installed. */
    private final Map<String, WindowManager.PluginState> plugins = new java.util.concurrent.ConcurrentHashMap<>();
    /** Where the store kept the saved windows aside, each time it did. */
    private final List<Path> setAside = new java.util.concurrent.CopyOnWriteArrayList<>();

    @AfterEach void cleanup() throws Exception {
        if (windows != null) edt(() -> windows.manager.closeAll());
        closeOwners();
    }

    private Path file() { return folder.resolve("workspace.toml"); }

    /** Windows restored from {@code saved}, where the plugins {@code running} names run, by id and name. */
    private void windows(WorkspaceFile.Snapshot saved, Map<String, String> running) throws Exception {
        running.forEach((id, name) -> plugins.put(id, new WindowManager.PluginState(name, WindowManager.PluginState.Kind.RUNNING)));
        WorkspaceFile.write(file(), saved);
        edt(() -> {
            windows = new TestWindows();
            var store = new WorkspaceStore(file(), windows.manager::snapshot, (Duration delay, Runnable task) -> () -> { }, Runnable::run, setAside::add);
            windows.manager.workspace(store);
            windows.manager.plugins(id -> plugins.getOrDefault(id, new WindowManager.PluginState(id, WindowManager.PluginState.Kind.NOT_INSTALLED)));
        });
    }

    /** The notes plugin's saved note {@code name}: its title is {@code <name>.md}, and it reopens under the key {@code note:<name>}. */
    private static WorkspaceFile.TabRecord.Content note(String name) {
        return new WorkspaceFile.TabRecord.Content(name + ".md", NOTES, "note", Map.of("name", name));
    }

    private static final WorkspaceFile.TabRecord.Content NOTE = note("todo");
    private static final WorkspaceFile.TabRecord.Terminal SHELL = new WorkspaceFile.TabRecord.Terminal("", HOME.toString(), "");

    /** The main window, saved without a place, holding {@code tabs}, the first one shown. */
    private static WorkspaceFile.WindowRecord main(Map<TabId, WorkspaceFile.TabRecord> items, ParentTab... tabs) {
        return new WorkspaceFile.WindowRecord(Optional.empty(), 0, new ParentTabs(List.of(tabs), tabs[0].id()), items);
    }

    /** The main window with one tab of two panes side by side: a terminal, then {@code record}'s item, whose pane is active. */
    private static WorkspaceFile.Snapshot mainWith(TabId shell, TabId saved, WorkspaceFile.TabRecord record) {
        Layout one = Layout.of(shell);
        return new WorkspaceFile.Snapshot(List.of(main(Map.of(shell, SHELL, saved, record),
            new ParentTab(ParentTabId.random(), one.split(saved, one.activeGroup(), Side.RIGHT)))));
    }

    /** The notes plugin's restorer: it opens the note its state names, under the saved title, in the window being restored. */
    private void notesRestorer() throws Exception {
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            Tab opened = windows.model.tabs().open(NOTES, "note:" + restoring.state().state().get("name"), restoring.title(),
                restoring.window(), new javax.swing.JPanel());
            opened.restore(Optional.of(restoring.state()));
            return Optional.empty();
        }));
    }

    @Test void aPluginsItemComesBackInItsSavedTabAndPaneInTheMainWindowAndAFullWindow() throws Exception {
        TabId shell = TabId.random(), left = TabId.random(), right = TabId.random(), away = TabId.random();
        Group leftPane = new Group(GroupId.random(), List.of(left), Optional.of(left));
        Group rightPane = new Group(GroupId.random(), List.of(right), Optional.of(right));
        ParentTab shown = new ParentTab(ParentTabId.random(), Layout.of(shell));
        ParentTab hidden = new ParentTab(ParentTabId.random(), new Layout(new Split(Orientation.HORIZONTAL,
            List.of(new Child(leftPane, 0.5), new Child(rightPane, 0.5))), rightPane.id(), Optional.empty()));
        var mainRecord = main(Map.of(shell, SHELL, left, note("left"), right, note("right")), shown, hidden);
        var full = new WorkspaceFile.WindowRecord(Optional.empty(), 0,
            ParentTabs.of(new ParentTab(ParentTabId.random(), Layout.of(away))), Map.of(away, note("away")));
        var saved = new WorkspaceFile.Snapshot(List.of(mainRecord, full));
        windows(saved, Map.of(NOTES, "Notes"));
        notesRestorer();
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            assertThat(main.content().windowLayout()).as("the notes in the hidden tab's two panes, under their saved ids")
                .isEqualTo(mainRecord.layout());
            for (TabId note : List.of(left, right, away))
                assertThat(windows.model.tabs().tab(note)).as("rebuilt by its plugin")
                    .hasValueSatisfying(tab -> assertThat(tab.kind()).isEqualTo(Tab.Kind.HOSTED));
            List<JasperWindow> children = windows.others();
            assertThat(children).hasSize(1);
            assertThat(children.getFirst().content().windowLayout()).as("a full window").isEqualTo(full.layout());
            assertThat(windows.model.tabs().tab(away).orElseThrow().window()).isEqualTo(children.getFirst().id());
            assertThat(windows.manager.snapshot()).as("saved again, it is what was read").contains(saved);
        });
        windows.launch();
    }

    /**
     * 6b Task 1's review: a saved item whose id is already open is taken as restored elsewhere. It is left out of its
     * window's restore with a warning that names its plugin and id, and no placeholder is registered under that id, which
     * would throw out of the restore.
     */
    @Test void aSavedItemWhoseIdIsAlreadyOpenIsLeftOutOfItsWindowsRestoreWithAWarning() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        notesRestorer();
        try (var log = dev.jasper.app.testsupport.LogRecorder.of(WindowManager.class.getName())) {
            edt(() -> {
                windows.model.tabs().add(saved, Tab.Kind.TERMINAL, new dev.jasper.app.contributions.TabContent() {
                    @Override public javax.swing.JComponent component() { return new javax.swing.JPanel(); }
                    @Override public void dispose() { }
                }, UUID.randomUUID());
                JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
                assertThat(main.content().tabs()).extracting(Tab::id).as("the saved window, without the item").containsExactly(shell);
                assertThat(windows.model.tabs().tab(saved).orElseThrow().kind()).as("the open item, untouched").isEqualTo(Tab.Kind.TERMINAL);
            });
            assertThat(log.warnings()).as("named once").filteredOn(warning -> warning.contains(saved.value().toString()))
                .singleElement().asString().contains(NOTES);
        }
        windows.launch();
    }

    /**
     * The notes plugin's restorer, once {@code works} holds: it opens the note in the window being restored and asks for
     * every move there is, to a new window, the first window, a new tab and each other open window.
     */
    private void movingNotesRestorer(boolean[] works) throws Exception {
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            if (!works[0]) return Optional.of("the note is locked");
            Tab opened = windows.model.tabs().open(NOTES, "note:" + restoring.state().state().get("name"), restoring.title(),
                restoring.window(), new javax.swing.JPanel());
            opened.restore(Optional.of(restoring.state()));
            opened.moveToNewWindow();
            opened.moveToWindow(windows.first().id());
            windows.model.tabs().moveToNewTab(opened);
            for (JasperWindow other : windows.manager.windows())
                if (!other.id().equals(restoring.window())) windows.model.tabs().move(opened, other.id());
            return Optional.empty();
        }));
    }

    /**
     * 6b Task 1's and Task 2's reviews: a restorer that moves its item while the windows come back moves nothing. Each item
     * stays in its saved tab and pane of the window being restored, nothing throws out of the restore, and the saved
     * windows are kept, not set aside.
     */
    @Test void aRestorerThatMovesItsItemAtStartLeavesItWhereItsWindowSavedIt() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random(), away = TabId.random();
        var mainRecord = mainWith(shell, saved, NOTE).windows().getFirst();
        var full = new WorkspaceFile.WindowRecord(Optional.empty(), 0,
            ParentTabs.of(new ParentTab(ParentTabId.random(), Layout.of(away))), Map.of(away, note("away")));
        var snapshot = new WorkspaceFile.Snapshot(List.of(mainRecord, full));
        windows(snapshot, Map.of(NOTES, "Notes"));
        movingNotesRestorer(new boolean[]{true});
        try (var log = dev.jasper.app.testsupport.LogRecorder.of(WindowManager.class.getName())) {
            edt(() -> {
                JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
                assertThat(main.content().windowLayout()).as("restored, not opened fresh").isEqualTo(mainRecord.layout());
                assertThat(windows.model.tabs().tab(saved).orElseThrow().window()).isEqualTo(main.id());
                assertThat(windows.others()).as("no window opened for a move").singleElement().satisfies(child -> {
                    assertThat(child.content().windowLayout()).isEqualTo(full.layout());
                    assertThat(windows.model.tabs().tab(away).orElseThrow().window()).isEqualTo(child.id());
                });
                assertThat(windows.manager.snapshot()).as("saved again, it is what was read").contains(snapshot);
            });
            assertThat(log.warnings()).as("nothing failed").isEmpty();
        }
        assertThat(setAside).as("the saved windows are kept").isEmpty();
        windows.launch();
    }

    /** 6b Task 2's review: on Retry too, a restorer that moves its item moves nothing, and nothing escapes the button. */
    @Test void aRestorerThatMovesItsItemOnRetryLeavesItInThePlaceholdersPane() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        boolean[] works = {false};
        movingNotesRestorer(works);
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            GroupId pane = main.content().workspace().model().groupOf(saved).orElseThrow();
            works[0] = true;
            ((PlaceholderContent) placeholder.content()).retryButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            Tab note = main.content().selectedTab();
            assertThat(note.kind()).isEqualTo(Tab.Kind.HOSTED);
            assertThat(note.window()).isEqualTo(main.id());
            assertThat(main.content().workspace().model().groupOf(note.id())).as("in the placeholder's pane").contains(pane);
            assertThat(main.content().parentTabCount()).as("no tab opened for it").isEqualTo(1);
            assertThat(windows.others()).as("no window opened for it").isEmpty();
        });
        windows.launch();
    }

    @Test void anItemWhosePluginIsNotRunningComesBackAsAPlaceholderAndIsSavedAgainAsItWas() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of());
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            assertThat(placeholder.id()).isEqualTo(saved);
            assertThat(placeholder.kind()).isEqualTo(Tab.Kind.PLACEHOLDER);
            assertThat(placeholder.title()).isEqualTo("todo.md");
            var content = (PlaceholderContent) placeholder.content();
            assertThat(content.reason()).isEqualTo("The plugin dev.example.notes is not installed.");
            assertThat(content.retryable()).as("no Retry without its plugin").isFalse();
            assertThat(content.closeButton().isFocusable()).as("reached by keyboard").isTrue();
            assertThat(content.action()).as("its focus target, with no Retry").isSameAs(content.closeButton());
            assertThat(windows.manager.snapshot().orElseThrow().windows().getFirst().tabs().get(saved)).as("saved again as it was").isEqualTo(NOTE);
            assertThat(windows.terminals.tab(saved.value()).orElseThrow().kind()).as("a content item to plugins")
                .isEqualTo(dev.jasper.app.terminals.TabEntry.Kind.CONTENT);
            content.closeButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    @Test void aRestorerThatFailsLeavesAPlaceholderAndRetryPutsItsItemInThePlaceholdersPane() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        boolean[] works = {false};
        String[] why = {"the note is locked"};
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            if (!works[0]) return Optional.of(why[0]);
            Tab opened = windows.model.tabs().open(NOTES, "note:todo", restoring.title(), restoring.window(), new javax.swing.JPanel());
            opened.restore(Optional.of(restoring.state()));
            return Optional.empty();
        }));
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            GroupId pane = main.content().workspace().model().groupOf(saved).orElseThrow();
            var content = (PlaceholderContent) placeholder.content();
            assertThat(content.reason()).isEqualTo("Notes could not restore this tab: the note is locked.");
            assertThat(content.retryable()).isTrue();
            assertThat(content.retryButton().isFocusable()).isTrue();
            assertThat(content.action()).as("its focus target while Retry shows").isSameAs(content.retryButton());
            why[0] = "the note is still locked by another editor";
            content.retryButton().doClick();
            assertThat(placeholder.closed()).as("still failing: the placeholder stays").isFalse();
            assertThat(content.reason()).as("its new reason in place of the old")
                .isEqualTo("Notes could not restore this tab: the note is still locked by another editor.");
            assertThat(main.content().tabs()).hasSize(2);
            works[0] = true;
            content.retryButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            Tab note = main.content().selectedTab();
            assertThat(note.kind()).isEqualTo(Tab.Kind.HOSTED);
            assertThat(note.title()).isEqualTo("todo.md");
            assertThat(main.content().tabs()).as("in the placeholder's place").extracting(Tab::id).containsExactly(shell, note.id());
            assertThat(main.content().workspace().model().groupOf(note.id())).as("in the placeholder's pane").contains(pane);
            assertThat(main.content().parentTabCount()).as("no tab opened for it").isEqualTo(1);
            assertThat(windows.manager.snapshot().orElseThrow().windows().getFirst().tabs().get(note.id())).isEqualTo(NOTE);
        });
        windows.launch();
    }

    /**
     * Workspace plan 6b's final review M1: an Error a restorer throws, other than a VirtualMachineError, is that plugin's
     * restore failure, as an exception is: the item it opened closes, its pane holds a placeholder that says why, and the
     * rest of the workspace comes back, nothing kept aside.
     */
    @Test void aRestorersErrorLeavesAPlaceholderInItsPaneAndTheRestOfTheWorkspaceComesBack() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        Layout one = Layout.of(shell);
        var snapshot = new WorkspaceFile.Snapshot(List.of(main(Map.of(shell, new WorkspaceFile.TabRecord.Terminal("build", HOME.toString(), ""),
            saved, NOTE), new ParentTab(ParentTabId.random(), one.split(saved, one.activeGroup(), Side.RIGHT)))));
        windows(snapshot, Map.of(NOTES, "Notes"));
        Tab[] opened = new Tab[1];
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            opened[0] = windows.model.tabs().open(NOTES, "note:todo", restoring.title(), restoring.window(), new javax.swing.JPanel());
            throw new AssertionError("the note's index is broken");
        }));
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            assertThat(opened[0].closed()).as("the item it opened closed").isTrue();
            Tab placeholder = windows.model.tabs().tab(saved).orElseThrow();
            assertThat(placeholder.kind()).isEqualTo(Tab.Kind.PLACEHOLDER);
            assertThat(((PlaceholderContent) placeholder.content()).reason()).isEqualTo("Notes could not restore this tab: the note's index is broken.");
            assertThat(main.content().windowLayout()).as("in its saved pane").isEqualTo(snapshot.windows().getFirst().layout());
            assertThat(((TerminalContent) windows.model.tabs().tab(shell).orElseThrow().content()).renamed())
                .as("the renamed terminal came back").contains("build");
            assertThat(setAside).as("nothing kept aside").isEmpty();
        });
        windows.launch();
    }

    @Test void aPlaceholderSaysHowItsPluginStandsAndOffersRetryOnlyWhileItRuns() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.TURNED_OFF));
        windows(mainWith(shell, saved, NOTE), Map.of());
        int[] restored = {0};
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            restored[0]++;
            return Optional.of("the note is locked");
        }));
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            var content = (PlaceholderContent) main.content().selectedTab().content();
            assertThat(content.reason()).isEqualTo("Notes is turned off. Turn it on in Manage Plugins and restart Jasper.");
            assertThat(content.retryable()).isFalse();
            plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.RUNNING));
            content.shown();
            assertThat(content.reason()).as("asked again when it shows").isEqualTo("Notes runs now: Retry brings this tab back.");
            assertThat(content.retryable()).isTrue();
            plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.FAILED));
            content.retryButton().doClick();
            assertThat(restored[0]).as("and again on Retry, before the restorer runs").isZero();
            assertThat(content.reason()).isEqualTo("Notes could not start.");
            assertThat(content.retryable()).isFalse();
            plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.NEEDS_CONSENT));
            content.shown();
            assertThat(content.reason()).isEqualTo("Notes waits for your review in Manage Plugins. Review it there and restart Jasper.");
            plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.SAFE_MODE));
            content.shown();
            assertThat(content.reason()).isEqualTo("Notes is off in safe mode. Restart Jasper normally to restore this tab.");
            assertThat(content.retryable()).isFalse();
            plugins.put(NOTES, new WindowManager.PluginState("Notes", WindowManager.PluginState.Kind.MARKED_FOR_REMOVAL));
            content.shown();
            assertThat(content.reason()).as("no advice to turn it on: it is going").isEqualTo("Notes is marked for removal in Manage Plugins.");
            assertThat(content.retryable()).isFalse();
        });
        windows.launch();
    }

    /** Tabs and panes spec 13: a placeholder moves like any item, and its Retry rebuilds the item in the window that holds it then. */
    @Test void aPlaceholderMovedToAFullWindowRetriesThere() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        boolean[] works = {false};
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            if (!works[0]) return Optional.of("the note is locked");
            windows.model.tabs().open(NOTES, "note:todo", restoring.title(), restoring.window(), new javax.swing.JPanel());
            return Optional.empty();
        }));
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            JasperWindow full = windows.manager.moveToNewWindow(placeholder).orElseThrow();
            assertThat(placeholder.window()).isEqualTo(full.id());
            works[0] = true;
            ((PlaceholderContent) placeholder.content()).retryButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            assertThat(full.closed()).as("it holds the rebuilt item").isFalse();
            assertThat(full.content().selectedTab().kind()).as("rebuilt in the window that held it").isEqualTo(Tab.Kind.HOSTED);
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    @Test void aPlaceholderInAnotherWindowClosesFromTheWindowThatHoldsItNow() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of());
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            JasperWindow full = windows.manager.moveToNewWindow(placeholder).orElseThrow();
            ((PlaceholderContent) placeholder.content()).closeButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            assertThat(full.closed()).as("its last item closed: it keeps its empty workspace").isFalse();
            assertThat(full.content().tabCount()).isZero();
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    /**
     * Workspace plan 6b Rulings 16, 21 and 46 (6b Task 2's review): a workspace too large to read back leaves out plugins'
     * items in one counted pass, the largest by the bytes the file writes for them first, and no more than it must; each is
     * logged once, however often the windows are saved.
     */
    @Test void aWorkspaceTooLargeToReadBackLeavesOutPluginsItemsLargestFirst() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        notesRestorer();
        edt(() -> windows.model.tabs().restorer(NOTES, "big", restoring -> Optional.empty()));
        int[] leftOut = new int[1];
        try (var log = dev.jasper.app.testsupport.LogRecorder.of(WindowManager.class.getName())) {
            edt(() -> {
                JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
                var bigs = new ArrayList<Tab>();
                for (int index = 0; index < 300; index++) {
                    Tab big = windows.model.tabs().open(NOTES, "big:" + index, "Big " + index, main.id(), new javax.swing.JPanel());
                    big.restore(Optional.of(new TabRestore("big", Map.of("text", "x".repeat(3700 + index)))));
                    bigs.add(big);
                }
                // Fewer characters than any of them, but three UTF-8 bytes each: the largest in the file.
                Tab wide = windows.model.tabs().open(NOTES, "wide", "Wide", main.id(), new javax.swing.JPanel());
                wide.restore(Optional.of(new TabRestore("big", Map.of("text", "\u4e2d".repeat(2000)))));
                windows.manager.snapshot().orElseThrow();
                WorkspaceFile.Snapshot snapshot = windows.manager.snapshot().orElseThrow();
                WorkspaceFile.WindowRecord record = snapshot.windows().getFirst();
                assertThat(WorkspaceFile.encodedBytes(snapshot)).as("a read never sets it aside").isLessThanOrEqualTo(WorkspaceFile.MAX_BYTES);
                assertThat(record.tabs()).as("terminals and the smaller plugins' items stay").containsKeys(shell, saved);
                assertThat(record.tabs()).as("ranked by its bytes, not its characters").doesNotContainKey(wide.id());
                int kept = (int) bigs.stream().filter(big -> record.tabs().containsKey(big.id())).count();
                assertThat(kept).isBetween(40, 100);
                assertThat(bigs.subList(0, kept)).as("the smallest stay: the largest left first")
                    .allSatisfy(big -> assertThat(record.tabs()).containsKey(big.id()));
                assertThat(record.layout().parentTabs()).as("a tab its only item leaves goes with it").hasSize(record.tabs().size() - 1);
                // No more than it must: with the smallest one it left out, the file would not fit.
                Tab next = bigs.get(kept);
                var items = new java.util.HashMap<>(record.tabs());
                items.put(next.id(), new WorkspaceFile.TabRecord.Content(next.title(), NOTES, "big", next.restore().orElseThrow().state()));
                var tabs = new ArrayList<>(record.layout().parentTabs());
                tabs.add(new ParentTab(ParentTabId.random(), Layout.of(next.id())));
                var withNext = new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(record.bounds(),
                    record.extendedState(), new ParentTabs(tabs, record.layout().active()), items)));
                assertThat(WorkspaceFile.encodedBytes(withNext)).as("one more would not fit").isGreaterThan(WorkspaceFile.MAX_BYTES);
                leftOut[0] = 301 - kept;
            });
            assertThat(log.warnings()).as("two saves: each item left out logged once").doesNotHaveDuplicates().hasSize(leftOut[0])
                .allSatisfy(line -> assertThat(line).startsWith("Not saving the tab ").endsWith(": the saved windows would pass workspace.toml's size limit"))
                .contains("Not saving the tab Wide: the saved windows would pass workspace.toml's size limit");
        }
        windows.launch();
    }

    @Test void aPluginsItemWithoutRestoreStateOrRestorerIsNotSavedAndJaspersOwnItemsNeverAre() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, SHELL), Map.of(NOTES, "Notes"));
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            Tab plain = windows.model.tabs().open(NOTES, "plain", "Plain", main.id(), new javax.swing.JPanel());
            Tab unknown = windows.model.tabs().open(NOTES, "unknown", "Unknown", main.id(), new javax.swing.JPanel());
            unknown.restore(Optional.of(new TabRestore("unknown", Map.of())));
            Tab errors = windows.model.tabs().open(TabRegistry.APP_OWNER, "errors", "Errors", main.id(), new javax.swing.JPanel());
            errors.restore(Optional.of(new TabRestore("errors", Map.of())));
            windows.model.tabs().restorer(TabRegistry.APP_OWNER, "errors", restoring -> Optional.empty());
            var tabs = windows.manager.snapshot().orElseThrow().windows().getFirst().tabs();
            assertThat(tabs).containsOnlyKeys(shell, saved);
            assertThat(plain.closed() || unknown.closed() || errors.closed()).isFalse();
        });
        windows.launch();
    }

    /** Workspace plan 6b Rulings 22: a saved item naming Jasper itself, which only a file this build did not write holds, is left out. */
    @Test void aSavedItemNamingJasperItselfIsLeftOut() throws Exception {
        TabId shell = TabId.random(), errors = TabId.random();
        windows(mainWith(shell, errors, new WorkspaceFile.TabRecord.Content("Errors", TabRegistry.APP_OWNER, "errors", Map.of())), Map.of());
        try (var log = dev.jasper.app.testsupport.LogRecorder.of(WindowManager.class.getName())) {
            edt(() -> {
                JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
                assertThat(main.content().tabs()).extracting(Tab::id).as("no placeholder").containsExactly(shell);
                assertThat(windows.model.tabs().tab(errors)).isEmpty();
                assertThat(windows.manager.snapshot().orElseThrow().windows().getFirst().tabs()).containsOnlyKeys(shell);
            });
            assertThat(log.warnings()).as("left out with a trace, naming its kind").singleElement().asString().contains("errors");
        }
        windows.launch();
    }

    /** 6b Task 2's review: a long reason wraps within the placeholder's pane and shows whole, and one that asks keeps its mark. */
    @Test void aPlaceholdersReasonWrapsWithinItsPaneAndEndsAsItsRestorerEndedIt() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        String[] why = {"the note is locked by another editor on another machine for now ".repeat(7).strip()};
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> Optional.of(why[0])));
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            var content = (PlaceholderContent) main.content().selectedTab().content();
            assertThat(content.reason()).hasSizeGreaterThan(400).isEqualTo("Notes could not restore this tab: " + why[0] + ".");
            // Laid out as its pane lays it out, 320 wide (validate needs a frame, which the test has not).
            content.setSize(320, 480);
            content.doLayout();
            var reason = named(content, "workspace.placeholder.reason");
            java.awt.Rectangle bounds = reason.getBounds();
            assertThat(bounds.x).as("within the pane").isGreaterThanOrEqualTo(0);
            assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(320);
            assertThat(bounds.height).as("on several lines").isGreaterThan(3 * reason.getFontMetrics(reason.getFont()).getHeight());
            assertThat(reason).as("text that wraps").isInstanceOf(javax.swing.text.JTextComponent.class);
            var text = (javax.swing.text.JTextComponent) reason;
            try {
                var end = text.modelToView2D(text.getDocument().getLength());
                assertThat(end.getMaxY()).as("whole: its last line shows").isLessThanOrEqualTo(bounds.height);
                assertThat(end.getMaxX()).isLessThanOrEqualTo(bounds.width);
            } catch (javax.swing.text.BadLocationException impossible) {
                throw new AssertionError(impossible);
            }
            assertThat(text.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
            why[0] = "Why?";
            content.retryButton().doClick();
            assertThat(content.reason()).as("as it is").isEqualTo("Notes could not restore this tab: Why?");
        });
        windows.launch();
    }

    /** Tabs and panes spec 12 and 13 (6b Task 2's review): a placeholder splits, moves and closes as any item does, and is saved back as it was. */
    @Test void aPlaceholderMovesAsAnyItemDoesAndIsSavedBackAsItWas() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of());
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            WindowContent window = main.content();
            Tab placeholder = window.selectedTab();
            Tab terminal = windows.model.tabs().tab(shell).orElseThrow();
            Runnable savedAsItWas = () -> {
                assertThat(placeholder.closed()).isFalse();
                assertThat(placeholder.kind()).isEqualTo(Tab.Kind.PLACEHOLDER);
                assertThat(windows.manager.snapshot().orElseThrow().windows().getFirst().tabs().get(saved)).as("saved back as it was").isEqualTo(NOTE);
            };
            window.split(Side.DOWN);
            assertThat(window.tabs()).as("a split beside it").hasSize(3);
            assertThat(window.parentTabCount()).isEqualTo(1);
            savedAsItWas.run();
            window.moveToNewTab(placeholder);
            assertThat(window.parentTabCount()).as("Move Pane to New Tab").isEqualTo(2);
            assertThat(window.parentTabOf(placeholder)).isNotEqualTo(window.parentTabOf(terminal));
            savedAsItWas.run();
            GroupId beside = window.parentTabs().holding(terminal).orElseThrow().model().groupOf(shell).orElseThrow();
            window.movePane(placeholder, beside, Side.LEFT);
            assertThat(window.parentTabCount()).as("a pane drag back beside the terminal: its tab goes with it").isEqualTo(1);
            assertThat(window.parentTabOf(placeholder)).isEqualTo(window.parentTabOf(terminal));
            savedAsItWas.run();
            window.selectTab(placeholder);
            window.action(dev.jasper.app.commands.ActionId.CLOSE_PANE).actionPerformed(new java.awt.event.ActionEvent(window, 0, "close"));
            assertThat(placeholder.closed()).as("Close Pane closes it").isTrue();
            assertThat(windows.manager.snapshot().orElseThrow().windows().getFirst().tabs()).doesNotContainKey(saved);
        });
        windows.launch();
    }

    @Test void aSessionsItemComesBackThroughItsPluginAndConnectsWhenItsTabFirstShows() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        var record = new WorkspaceFile.TabRecord.Content("build-host", "dev.example.ssh", "ssh", Map.of("host", "build-host"));
        windows(new WorkspaceFile.Snapshot(List.of(main(Map.of(shell, SHELL, saved, record),
            new ParentTab(ParentTabId.random(), Layout.of(shell)), new ParentTab(ParentTabId.random(), Layout.of(saved))))),
            Map.of("dev.example.ssh", "SSH"));
        Queue<SessionAttempt> attempts = new ArrayDeque<>();
        boolean[] refused = {false};
        edt(() -> windows.model.tabs().restorer("dev.example.ssh", "ssh", restoring -> {
            var request = new SessionRequest("dev.example.ssh", restoring.title(), false, attempts::add, Runnable::run, null,
                Optional.of(restoring.state()));
            WindowContent window = windows.manager.window(restoring.window()).orElseThrow().content();
            window.openTab(HOME, request);
            // The window being restored takes no other item while the restorer runs (workspace plan 6b Rulings 24).
            refused[0] = window.openTab(HOME, new SessionRequest("dev.example.ssh", "other", false, attempts::add, Runnable::run)) == null;
            return Optional.empty();
        }));
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            Tab session = main.content().tabs().get(1);
            assertThat(session.id()).isEqualTo(saved);
            assertThat(session.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(refused[0]).as("a second session in the window being restored").isTrue();
            assertThat(main.content().tabs()).hasSize(2);
            assertThat(windows.manager.snapshot().orElseThrow().windows().getFirst().tabs().get(saved)).as("saved again through its plugin")
                .isEqualTo(new WorkspaceFile.TabRecord.Content(session.title(), "dev.example.ssh", "ssh", Map.of("host", "build-host")));
        });
        edt(() -> {
            assertThat(attempts).as("its tab hidden: it has not connected").isEmpty();
            WindowContent content = windows.first().content();
            content.selectTab(content.tabs().get(1));
        });
        edt(() -> assertThat(attempts).as("its tab shown, it connects once that EDT turn ends").hasSize(1));
        windows.launch();
    }

    /** 6b Task 2's review: Retry rebuilds a session's terminal in the placeholder's pane, which connects once that turn ends. */
    @Test void aSessionsRetryRebuildsItsTerminalWhichConnectsOnceTheTurnEnds() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        var record = new WorkspaceFile.TabRecord.Content("build-host", "dev.example.ssh", "ssh", Map.of("host", "build-host"));
        windows(mainWith(shell, saved, record), Map.of("dev.example.ssh", "SSH"));
        Queue<SessionAttempt> attempts = new ArrayDeque<>();
        boolean[] works = {false};
        edt(() -> windows.model.tabs().restorer("dev.example.ssh", "ssh", restoring -> {
            if (!works[0]) return Optional.of("build-host is not reachable");
            var request = new SessionRequest("dev.example.ssh", restoring.title(), false, attempts::add, Runnable::run, null,
                Optional.of(restoring.state()));
            windows.manager.window(restoring.window()).orElseThrow().content().openTab(HOME, request);
            return Optional.empty();
        }));
        GroupId[] pane = new GroupId[1];
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            pane[0] = main.content().workspace().model().groupOf(saved).orElseThrow();
            works[0] = true;
            ((PlaceholderContent) placeholder.content()).retryButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            Tab session = main.content().selectedTab();
            assertThat(session.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(main.content().workspace().model().groupOf(session.id())).as("in the placeholder's pane").contains(pane[0]);
            assertThat(attempts).as("not yet: it connects once this turn ends").isEmpty();
            assertThat(windows.terminals.tab(session.id().value())).as("a terminal to plugins, in its window")
                .hasValueSatisfying(entry -> assertThat(entry.kind()).isEqualTo(dev.jasper.app.terminals.TabEntry.Kind.TERMINAL));
            assertThat(windows.terminals.tab(saved.value())).as("the placeholder gone from them").isEmpty();
            assertThat(windows.manager.snapshot().orElseThrow().windows().getFirst().tabs().get(session.id())).as("saved through its plugin")
                .isEqualTo(new WorkspaceFile.TabRecord.Content(session.title(), "dev.example.ssh", "ssh", Map.of("host", "build-host")));
        });
        edt(() -> {
            assertThat(attempts).as("shown, it connects once").hasSize(1);
            WindowContent content = windows.first().content();
            content.closeTab(content.selectedTab());
            assertThat(content.tabs()).as("it closes as any item does").extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    /**
     * Workspace plan 6b Rulings 23 and 24: the saved item names the window being restored at once, though the main window
     * is still being composed; another item its restorer opens there waits for that window, naming none, and opens in it,
     * in a new tab, once it shows.
     */
    @Test void aRestoredItemNamesItsWindowAtOnceAndARestorersOtherItemOpensOnceTheWindowShows() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        var during = new ArrayList<UUID>();
        Tab[] other = new Tab[1];
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            Tab note = windows.model.tabs().open(NOTES, "note:todo", restoring.title(), restoring.window(), new javax.swing.JPanel());
            other[0] = windows.model.tabs().open(NOTES, "note:other", "Other", restoring.window(), new javax.swing.JPanel());
            during.add(note.window());
            during.add(other[0].window());
            return Optional.empty();
        }));
        edt(() -> {
            JasperWindow main = windows.manager.ensureWindow("cold", null).orElseThrow();
            assertThat(during).as("the saved item's window at once; none for the other while it waits").containsExactly(main.id(), null);
            assertThat(other[0].window()).isEqualTo(main.id());
            assertThat(main.content().tabs()).as("the other in a new tab after the restored one")
                .extracting(Tab::id).containsExactly(shell, saved, other[0].id());
            assertThat(main.content().parentTabOf(other[0])).isNotEqualTo(main.content().parentTabOf(main.content().tabs().getFirst()));
        });
        windows.launch();
    }

    /** The component named {@code name} under {@code root}. */
    private static javax.swing.JComponent named(java.awt.Container root, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (name.equals(child.getName())) return (javax.swing.JComponent) child;
            if (child instanceof java.awt.Container inner) {
                javax.swing.JComponent found = named(inner, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
