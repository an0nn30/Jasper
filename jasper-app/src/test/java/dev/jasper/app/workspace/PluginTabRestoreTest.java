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
            var store = new WorkspaceStore(file(), windows.manager::snapshot, (Duration delay, Runnable task) -> () -> { }, Runnable::run, kept -> { });
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
        return new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0, new ParentTabs(List.of(tabs), tabs[0].id()), items);
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
        var full = new WorkspaceFile.WindowRecord(WorkspaceFile.Role.CHILD, Optional.empty(), 0,
            ParentTabs.of(new ParentTab(ParentTabId.random(), Layout.of(away))), Map.of(away, note("away")));
        var saved = new WorkspaceFile.Snapshot(List.of(mainRecord, full));
        windows(saved, Map.of(NOTES, "Notes"));
        notesRestorer();
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().windowLayout()).as("the notes in the hidden tab's two panes, under their saved ids")
                .isEqualTo(mainRecord.layout());
            for (TabId note : List.of(left, right, away))
                assertThat(windows.model.tabs().tab(note)).as("rebuilt by its plugin")
                    .hasValueSatisfying(tab -> assertThat(tab.kind()).isEqualTo(Tab.Kind.HOSTED));
            List<ChildWindow> children = windows.manager.children();
            assertThat(children).hasSize(1);
            assertThat(children.getFirst().content().windowLayout()).as("a full window").isEqualTo(full.layout());
            assertThat(windows.model.tabs().tab(away).orElseThrow().window()).isEqualTo(children.getFirst().id());
            assertThat(windows.manager.snapshot()).as("saved again, it is what was read").contains(saved);
        });
        windows.launch();
    }

    @Test void anItemWhosePluginIsNotRunningComesBackAsAPlaceholderAndIsSavedAgainAsItWas() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of());
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            assertThat(placeholder.id()).isEqualTo(saved);
            assertThat(placeholder.kind()).isEqualTo(Tab.Kind.PLACEHOLDER);
            assertThat(placeholder.title()).isEqualTo("todo.md");
            var content = (PlaceholderContent) placeholder.content();
            assertThat(content.reason()).isEqualTo("The plugin dev.example.notes is not installed.");
            assertThat(content.retryable()).as("no Retry without its plugin").isFalse();
            assertThat(content.closeButton().isFocusable()).as("reached by keyboard").isTrue();
            assertThat(content.action()).as("its focus target, with no Retry").isSameAs(content.closeButton());
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(saved)).as("saved again as it was").isEqualTo(NOTE);
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
        edt(() -> windows.model.tabs().restorer(NOTES, "note", restoring -> {
            if (!works[0]) return Optional.of("the note is locked");
            Tab opened = windows.model.tabs().open(NOTES, "note:todo", restoring.title(), restoring.window(), new javax.swing.JPanel());
            opened.restore(Optional.of(restoring.state()));
            return Optional.empty();
        }));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            GroupId pane = main.content().workspace().model().groupOf(saved).orElseThrow();
            var content = (PlaceholderContent) placeholder.content();
            assertThat(content.reason()).isEqualTo("Notes could not restore this tab: the note is locked.");
            assertThat(content.retryable()).isTrue();
            assertThat(content.retryButton().isFocusable()).isTrue();
            assertThat(content.action()).as("its focus target while Retry shows").isSameAs(content.retryButton());
            content.retryButton().doClick();
            assertThat(placeholder.closed()).as("still failing: the placeholder stays").isFalse();
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
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(note.id())).isEqualTo(NOTE);
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
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            var content = (PlaceholderContent) main.content().selectedTab().content();
            assertThat(content.reason()).isEqualTo("Notes is turned off.");
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
            assertThat(content.reason()).isEqualTo("Notes waits for your review in Manage Plugins.");
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
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            ChildWindow full = windows.manager.moveToNewWindow(placeholder).orElseThrow();
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

    @Test void aPlaceholderInAFullWindowClosesFromTheWindowThatHoldsItNow() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of());
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab placeholder = main.content().selectedTab();
            ChildWindow full = windows.manager.moveToNewWindow(placeholder).orElseThrow();
            ((PlaceholderContent) placeholder.content()).closeButton().doClick();
            assertThat(placeholder.closed()).isTrue();
            assertThat(full.closed()).as("its last item closed").isTrue();
            assertThat(main.content().tabs()).extracting(Tab::id).containsExactly(shell);
        });
        windows.launch();
    }

    @Test void aWorkspaceTooLargeToReadBackLeavesOutPluginsItemsLargestFirst() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, NOTE), Map.of(NOTES, "Notes"));
        notesRestorer();
        edt(() -> windows.model.tabs().restorer(NOTES, "big", restoring -> Optional.empty()));
        // The window manager's warnings are recorded, not printed: each item left out is logged once (Rulings 16).
        var log = java.util.logging.Logger.getLogger(WindowManager.class.getName());
        var warned = new java.util.ArrayList<String>();
        var recorder = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { warned.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        boolean parents = log.getUseParentHandlers();
        log.setUseParentHandlers(false);
        log.addHandler(recorder);
        try {
            edt(() -> {
                MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
                for (int index = 0; index < 80; index++) {
                    Tab big = windows.model.tabs().open(NOTES, "big:" + index, "Big " + index, main.id(), new javax.swing.JPanel());
                    big.restore(Optional.of(new TabRestore("big", Map.of("text", "x".repeat(4000 + index)))));
                }
                windows.manager.snapshot().orElseThrow();
                WorkspaceFile.Snapshot snapshot = windows.manager.snapshot().orElseThrow();
                assertThat(WorkspaceFile.encodedBytes(snapshot)).as("a read never sets it aside").isLessThanOrEqualTo(WorkspaceFile.MAX_BYTES);
                assertThat(snapshot.main().tabs()).as("terminals and the smaller plugins' items stay").containsKeys(shell, saved);
                assertThat(snapshot.main().tabs()).hasSizeLessThan(82).hasSizeGreaterThan(40);
                assertThat(snapshot.main().tabs().values()).noneSatisfy(record -> assertThat(record.title()).isEqualTo("Big 79"));
                assertThat(snapshot.main().layout().parentTabs()).as("a tab its only item leaves goes with it")
                    .hasSize(snapshot.main().tabs().size() - 1);
                assertThat(warned).as("two saves: each item left out logged once").doesNotHaveDuplicates()
                    .hasSize(82 - snapshot.main().tabs().size()).allSatisfy(line -> assertThat(line).startsWith("Not saving the tab Big "));
            });
        } finally {
            log.removeHandler(recorder);
            log.setUseParentHandlers(parents);
        }
        windows.launch();
    }

    @Test void aPluginsItemWithoutRestoreStateOrRestorerIsNotSavedAndJaspersOwnItemsNeverAre() throws Exception {
        TabId shell = TabId.random(), saved = TabId.random();
        windows(mainWith(shell, saved, SHELL), Map.of(NOTES, "Notes"));
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab plain = windows.model.tabs().open(NOTES, "plain", "Plain", main.id(), new javax.swing.JPanel());
            Tab unknown = windows.model.tabs().open(NOTES, "unknown", "Unknown", main.id(), new javax.swing.JPanel());
            unknown.restore(Optional.of(new TabRestore("unknown", Map.of())));
            Tab errors = windows.model.tabs().open(TabRegistry.APP_OWNER, "errors", "Errors", main.id(), new javax.swing.JPanel());
            errors.restore(Optional.of(new TabRestore("errors", Map.of())));
            windows.model.tabs().restorer(TabRegistry.APP_OWNER, "errors", restoring -> Optional.empty());
            var tabs = windows.manager.snapshot().orElseThrow().main().tabs();
            assertThat(tabs).containsOnlyKeys(shell, saved);
            assertThat(plain.closed() || unknown.closed() || errors.closed()).isFalse();
        });
        windows.launch();
    }

    /** Workspace plan 6b Rulings 22: a saved item naming Jasper itself, which only a file this build did not write holds, is left out. */
    @Test void aSavedItemNamingJasperItselfIsLeftOut() throws Exception {
        TabId shell = TabId.random(), errors = TabId.random();
        windows(mainWith(shell, errors, new WorkspaceFile.TabRecord.Content("Errors", TabRegistry.APP_OWNER, "errors", Map.of())), Map.of());
        edt(() -> {
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.content().tabs()).extracting(Tab::id).as("no placeholder").containsExactly(shell);
            assertThat(windows.model.tabs().tab(errors)).isEmpty();
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs()).containsOnlyKeys(shell);
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
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            Tab session = main.content().tabs().get(1);
            assertThat(session.id()).isEqualTo(saved);
            assertThat(session.kind()).isEqualTo(Tab.Kind.TERMINAL);
            assertThat(refused[0]).as("a second session in the window being restored").isTrue();
            assertThat(main.content().tabs()).hasSize(2);
            assertThat(windows.manager.snapshot().orElseThrow().main().tabs().get(saved)).as("saved again through its plugin")
                .isEqualTo(new WorkspaceFile.TabRecord.Content(session.title(), "dev.example.ssh", "ssh", Map.of("host", "build-host")));
        });
        edt(() -> {
            assertThat(attempts).as("its tab hidden: it has not connected").isEmpty();
            WindowContent content = windows.manager.main().orElseThrow().content();
            content.selectTab(content.tabs().get(1));
        });
        edt(() -> assertThat(attempts).as("its tab shown, it connects once that EDT turn ends").hasSize(1));
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
            MainWindow main = windows.manager.ensureMain("cold", null).orElseThrow();
            assertThat(during).as("the saved item's window at once; none for the other while it waits").containsExactly(main.id(), null);
            assertThat(other[0].window()).isEqualTo(main.id());
            assertThat(main.content().tabs()).as("the other in a new tab after the restored one")
                .extracting(Tab::id).containsExactly(shell, saved, other[0].id());
            assertThat(main.content().parentTabOf(other[0])).isNotEqualTo(main.content().parentTabOf(main.content().tabs().getFirst()));
        });
        windows.launch();
    }
}
