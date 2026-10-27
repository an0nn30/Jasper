package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full windows spec 5: one kind of window, any number of them, and their lifecycle, headless. */
class WindowManagerTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final List<Path> started = new ArrayList<>();
    /** What the stand-in frames did and what the application heard, in order. */
    private final List<String> events = new ArrayList<>();
    /** What the closing hooks heard, with whether the window's content was still whole. */
    private final List<String> closings = new ArrayList<>();
    private boolean quitting, failFrames, reenter, failClosing, reenterAlways, failEarly;
    /** How many windows were composed. */
    private int compositions;
    /** The tab a plugin asked for while the saved windows were being restored. */
    private Tab early;
    /** What the closed hook runs once it has recorded the window. */
    private Consumer<JasperWindow> whenClosed = window -> { };
    /** Each window's name, {@code "w0"}, {@code "w1"}, …: its index in {@code windows()} when it was first named. */
    private final Map<JasperWindow, String> names = new IdentityHashMap<>();
    private WindowManager manager;

    @BeforeEach void manager() throws Exception {
        edt(() -> manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame,
            window -> events.add("opened " + name(window)), window -> events.add("activated " + name(window)),
            window -> { events.add("closed " + name(window)); whenClosed.accept(window); }, state -> { },
            window -> {
                closings.add("closing " + name(window) + (window.content().closed() ? " (closed)" : ""));
                events.add("closing " + name(window));
                if (failClosing) throw new IllegalStateException("closing failed");
            },
            () -> closings.add("closing all"))));
    }

    @Test void buddyWatchesOnlyShowingNonIconifiedWindowsEvenBeforeDeactivation() throws Exception {
        edt(() -> {
            var window = manager.ensureWindow("cold", null).orElseThrow();
            var presentations = new ArrayList<WorkspaceActivity.Presentation>();
            window.content().activity(event -> {
                if (event instanceof WorkspaceActivity.Presentation value) presentations.add(value);
            });
            window.events().activated().accept(true);
            window.events().state().accept(true, false);
            assertThat(presentations.getLast().watched()).isTrue();
            window.events().state().accept(true, true);
            assertThat(window.content().isActiveAndOpen()).isTrue();
            assertThat(presentations.getLast().watched()).isFalse();
            window.events().state().accept(true, false);
            assertThat(presentations.getLast().watched()).isTrue();
            window.events().state().accept(false, false);
            assertThat(presentations.getLast().watched()).isFalse();
            window.events().state().accept(true, false);
            assertThat(presentations.getLast().watched()).isTrue();
        });
    }

    @AfterEach void cleanup() throws Exception {
        edt(() -> manager.closeAll());
        closeOwners();
    }

    private String name(JasperWindow window) {
        return names.computeIfAbsent(window, named -> {
            int index = manager.windows().indexOf(named);
            return "w" + (index < 0 ? names.size() : index);
        });
    }

    private WindowContent compose(WindowManager.Opening opening) {
        if (quitting) return null;
        compositions++;
        // A plugin opening its view while the saved windows are still being restored (a panel factory, say).
        if (reenter || reenterAlways) {
            reenter = false;
            early = model.tabs().open("dev.example.editor", "early", "early.txt", null, new JPanel());
            if (failEarly) early.onClosed(() -> { throw new IllegalStateException("early failed to close"); });
            // Workspace plan 4a Rulings 24: the tab waits for the windows, and a nested request for them is still refused.
            assertThatThrownBy(() -> manager.ensureWindow("plugin", null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The windows are still being restored");
        }
        var launcher = new ShellLauncher(pending::add, directory -> { started.add(directory); return IdleTerminalSession.start(); },
            "idle-test-session");
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(launcher, opening.directory(), opening.empty(),
            directory -> manager.openWindow(directory), () -> { }, ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD));
        content.connectContributions(model);
        return content;
    }

    private WindowShell frame(JasperWindow window) {
        if (failFrames) throw new IllegalStateException("no frame");
        String name = name(window);
        return new WindowShell(() -> events.add("show " + name), () -> events.add("front " + name), () -> events.add("dispose " + name),
            () -> events.add("discard " + name), () -> null, component -> SwingUtilities.isDescendingFrom(component, window.content()),
            (bounds, cursor) -> events.add("place " + name + " " + bounds.x + "," + bounds.y + " " + bounds.width + "x"
                + bounds.height + " under " + cursor.x + "," + cursor.y));
    }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    /** Workspace tabs spec 13: a window keeps where its frame last said it is, which workspace.toml saves. */
    @Test void aWindowKeepsWhereItsFrameSaysItIs() throws Exception {
        edt(() -> {
            JasperWindow window = manager.ensureWindow("cold", null).orElseThrow();
            assertThat(window.placement()).isEqualTo(JasperWindow.Placement.NONE);
            var placed = new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6);
            window.placed(placed);
            assertThat(window.placement()).isEqualTo(placed);
            assertThatThrownBy(() -> new JasperWindow.Placement(Optional.empty(), java.awt.Frame.ICONIFIED))
                .as("never saved minimized").isInstanceOf(IllegalArgumentException.class);
            window.requestClose();
            window.placed(JasperWindow.Placement.NONE);
            assertThat(window.placement()).as("a closed window keeps its last place").isEqualTo(placed);
        });
        launch();
    }

    @Test void aWindowClosedByItsUserClosesItsTabsAfterTheirGuards() throws Exception {
        boolean[] allow = {false};
        JasperWindow[] w = new JasperWindow[2];
        Tab[] notes = new Tab[1];
        edt(() -> {
            w[0] = manager.ensureWindow("cold", null).orElseThrow();
            w[1] = manager.openWindow(null).orElseThrow();
            notes[0] = model.tabs().open("dev.example.editor", "notes", "notes.txt", w[1].id(), new JPanel());
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(allow[0]));
        });
        launch();
        edt(() -> {
            w[1].requestClose();
            assertThat(w[1].closed()).as("an unsaved document keeps it open").isFalse();
            allow[0] = true;
            w[1].requestClose();
            assertThat(w[1].closed()).isTrue();
            assertThat(notes[0].closed()).isTrue();
            assertThat(manager.windows()).as("the other window stays").containsExactly(w[0]);
        });
    }

    @Test void aTerminalTabMovedToANewWindowAndBackKeepsItsSession() throws Exception {
        JasperWindow[] first = new JasperWindow[1];
        edt(() -> {
            first[0] = manager.ensureWindow("cold", null).orElseThrow();
            first[0].content().newTab(HOME);
        });
        launch();
        edt(() -> {
            Tab tab = first[0].content().selectedTab();
            var pane = ((TerminalContent) tab.content()).pane();
            var session = pane.session();
            assertThat(session).isNotNull();
            JasperWindow second = manager.moveToNewWindow(tab).orElseThrow();
            assertThat(tab.window()).isEqualTo(second.id());
            assertThat(second.content().selectedTab()).isSameAs(tab);
            assertThat(first[0].content().tabCount()).as("the first window keeps its other tab").isEqualTo(1);
            assertThat(manager.moveToNewWindow(tab)).as("a window's only tab stays where it is").isEmpty();
            assertThat(manager.windows()).containsExactly(first[0], second);
            model.tabs().moveToWindow(tab, first[0].id());
            assertThat(tab.window()).isEqualTo(first[0].id());
            assertThat(second.closed()).as("emptied, it closed").isTrue();
            assertThat(manager.windows()).containsExactly(first[0]);
            assertThat(pane.session()).as("the same session throughout").isSameAs(session);
            assertThat(session.exitFuture()).isNotDone();
        });
    }

    @Test void aPluginsViewWithNoWindowOpensAWindowFirst() throws Exception {
        edt(() -> {
            assertThat(manager.isEmpty()).isTrue();
            Tab tab = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            JasperWindow first = manager.windows().getFirst();
            assertThat(manager.windows()).containsExactly(first);
            assertThat(tab.window()).isEqualTo(first.id());
            manager.closeAll();
            assertThat(manager.isEmpty()).isTrue();
            JasperWindow again = manager.ensureWindow("reopen", null).orElseThrow();
            assertThat(again).as("a fresh window").isNotSameAs(first);
        });
        launch();
    }

    @Test void aPluginsTabMovesThroughTheRegistryToANewWindowAndBack() throws Exception {
        edt(() -> {
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            JasperWindow first = manager.windows().getFirst();
            assertThat(notes.window()).isEqualTo(first.id());
            notes.moveToNewWindow();
            JasperWindow second = manager.windows().getLast();
            assertThat(second).isNotSameAs(first);
            assertThat(notes.window()).isEqualTo(second.id());
            assertThat(second.content().parentTabCount()).as("a full window, the item in a tab of its own (spec 13)").isEqualTo(1);
            assertThat(second.content().selectedTab()).isSameAs(notes);
            notes.moveToNewWindow();
            assertThat(manager.windows()).as("a window's only tab stays").containsExactly(first, second);
            notes.moveToWindow(first.id());
            assertThat(notes.window()).isEqualTo(first.id());
            assertThat(second.closed()).isTrue();
        });
        launch();
    }

    @Test void aPluginsTabPlacedBesideOpensAGroupAndOnePlacedInANewWindowANewWindow() throws Exception {
        edt(() -> {
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            JasperWindow first = manager.windows().getFirst();
            assertThat(first.content().workspace().model().groups()).hasSize(1);
            Tab beside = model.tabs().open("dev.example.editor", "beside", "beside.txt", null, new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            assertThat(first.content().workspace().model().groups()).as("a new group").hasSize(2);
            assertThat(beside.group()).isNotEqualTo(notes.group());
            Tab selected = first.content().selectedTab();
            Tab away = model.tabs().open("dev.example.editor", "away", "away.txt", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
            assertThat(manager.windows()).hasSize(2);
            assertThat(away.window()).isEqualTo(manager.windows().get(1).id());
            assertThat(manager.windows().get(1).content().tabCount()).isEqualTo(1);
            assertThat(first.content().workspace().model().groups()).as("the first window is not touched").hasSize(2);
            assertThat(first.content().selectedTab()).isSameAs(selected);
        });
        launch();
    }

    /** Tabs and panes spec 13: a plugin's tab with no window opens as a new tab of its window; new windows are full. */
    @Test void aPluginsTabOpensANewTabOfItsWindowAndNewWindowsAreFullWindows() throws Exception {
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            var content = first.content();
            ParentTabId shown = content.activeParentTab();
            content.newTab(HOME);
            content.selectParentTab(shown);
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            assertThat(content.parentTabCount()).as("a new tab of the window").isEqualTo(3);
            assertThat(content.activeParentTab()).as("at the end of its strip, shown").isEqualTo(content.parentTabOf(notes).orElseThrow())
                .isEqualTo(content.windowLayout().parentTabs().getLast().id());
            Tab away = model.tabs().open("dev.example.editor", "away", "away.txt", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
            JasperWindow second = manager.windows().get(1);
            assertThat(second.content().windowLayout()).as("TabPlacement.NEW_WINDOW opens a full window").isInstanceOf(ParentTabs.class);
            assertThat(second.content().parentTabOf(away)).isPresent();
            second.content().newTab(HOME);
            assertThat(second.content().parentTabCount()).as("which opens parent tabs of its own").isEqualTo(2);
            JasperWindow terminal = manager.openWindow(null).orElseThrow();
            assertThat(terminal.content().windowLayout()).as("New Window too").isInstanceOf(ParentTabs.class);
            assertThat(content.parentTabCount()).as("the first window is not touched").isEqualTo(3);
        });
        launch();
    }

    @Test void theWindowCommandsOpenCloseAndMoveWindows(@TempDir Path folder) throws Exception {
        Path directory = Files.createDirectories(folder.resolve("work"));
        JasperWindow[] first = new JasperWindow[1];
        edt(() -> {
            first[0] = manager.ensureWindow("cold", null).orElseThrow();
            first[0].content().newTab(directory);
        });
        launch();
        edt(() -> first[0].content().invoke(ActionId.NEW_TERMINAL_WINDOW));
        launch();
        assertThat(started).as("New Window: in the current terminal's directory").last().isEqualTo(directory);
        edt(() -> {
            JasperWindow opened = manager.windows().get(1);
            opened.content().invoke(ActionId.CLOSE_WINDOW);
            assertThat(opened.closed()).as("Close Window").isTrue();
            first[0].content().newTab(directory);
            ParentTabId moving = first[0].content().activeParentTab();
            first[0].content().invoke(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            JasperWindow moved = manager.windows().get(1);
            assertThat(moved.content().activeParentTab()).isEqualTo(moving);
            moved.content().updateActions();
            assertThat(moved.content().action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("its only tab stays").isFalse();
            assertThat(moved.content().action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).as("another window is open").isTrue();
            moved.content().invoke(ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(first[0].content().parentTabs().view(moving)).as("back in the first window").isPresent();
            assertThat(moved.closed()).isTrue();
            first[0].content().updateActions();
            assertThat(first[0].content().action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).as("no other window").isFalse();
            first[0].content().invoke(ActionId.CLOSE_WINDOW);
            assertThat(manager.isEmpty()).as("Close Window on the last window closes it").isTrue();
        });
    }

    /** Full windows spec 5.1: each window has its own dock, and a panel request for a window shows the panel there. */
    @Test void aSecondWindowsPanelRequestShowsThePanelInThatWindow() throws Exception {
        edt(() -> {
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("panel"));
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            JasperWindow second = manager.openWindow(null).orElseThrow();
            events.clear();
            model.requestPanel(new Contributions.PanelRequest(second.id(), "test.panel", Contributions.PanelRequest.Op.SHOW));
            assertThat(second.content().dock().visible("test.panel")).as("in the window it was asked for").isTrue();
            assertThat(first.content().dock().visible("test.panel")).as("not in the other").isFalse();
            assertThat(events).as("no other window comes forward").doesNotContain("front w0");
        });
        launch();
    }

    /** Workspace tabs spec 6.1: while the last window closes, a request opens no other beside it. */
    @Test void aRequestWhileTheLastWindowClosesOpensNoOther() throws Exception {
        List<Optional<JasperWindow>> asked = new ArrayList<>();
        edt(() -> {
            JasperWindow window = manager.ensureWindow("cold", null).orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel());
            notes.onClosed(() -> asked.add(manager.ensureWindow("plugin", null)));
            List<String> refused = new ArrayList<>();
            notes.onClosed(() -> {
                try {
                    model.tabs().open("dev.example.editor", "late", "late.txt", null, new JPanel());
                } catch (IllegalStateException failure) {
                    refused.add(failure.getMessage());
                }
            });
            manager.closeAll();
            assertThat(asked).as("a closing window is not absent").containsExactly(Optional.empty());
            assertThat(refused).as("a plugin's tab with no window, while the last window closes")
                .containsExactly("Every window is closing or Jasper is quitting");
            assertThat(manager.isEmpty()).isTrue();
            assertThat(events.stream().filter(event -> event.startsWith("opened")).count()).as("no other window").isEqualTo(1);
        });
        launch();
    }

    /** For plan 6: the hooks hear of every window while it is whole, and of closing them all once before any closes. */
    @Test void theClosingHooksHearOfEachWindowWhileItIsWholeAndOfClosingThemAllFirst() throws Exception {
        edt(() -> {
            manager.ensureWindow("cold", null).orElseThrow();
            manager.openWindow(null).orElseThrow().requestClose();
            assertThat(closings).containsExactly("closing w1");
            manager.openWindow(null).orElseThrow();
            closings.clear();
            manager.closeAll();
            assertThat(closings).as("the most recently active last").containsExactly("closing all", "closing w1", "closing w0");
            manager.closeAll();
            assertThat(closings).as("nothing is left to close").hasSize(3);
        });
        launch();
    }

    /** For plan 5: a torn-out tab's window opens at its bounds, and the window on top comes first. */
    @Test void aTornOutTabsWindowOpensAtItsBoundsAndTheWindowsListTheLastActiveFirst() throws Exception {
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            first.content().newTab(HOME);
            events.clear();
            JasperWindow second = manager.moveTabToNewWindow(first.content(), first.content().activeParentTab(),
                new java.awt.Rectangle(40, 50, 600, 400), new java.awt.Point(90, 60)).orElseThrow();
            assertThat(second.content().parentTabCount()).as("a torn-out tab's window is a full window").isEqualTo(1);
            assertThat(events).as("placed before it shows").containsSubsequence("place w1 40,50 600x400 under 90,60", "show w1");
            assertThatThrownBy(() -> new WindowManager.Opening(true, null, "menu", Optional.of(new java.awt.Rectangle(40, 50, 600, 400)),
                Optional.empty())).as("its bounds come with the cursor").isInstanceOf(IllegalArgumentException.class);
            assertThat(manager.windowsByActivation()).as("none came forward yet").containsExactly(first, second);
            second.events().activated().accept(true);
            assertThat(manager.windowsByActivation()).containsExactly(second, first);
            first.events().activated().accept(true);
            assertThat(manager.windowsByActivation()).containsExactly(first, second);
            second.requestClose();
            assertThat(manager.windowsByActivation()).containsExactly(first);
        });
        launch();
    }

    @Test void theWindowThatCameToTheFrontLastIsTheLastActive() throws Exception {
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            JasperWindow second = manager.openWindow(null).orElseThrow();
            assertThat(manager.lastActive()).as("none came forward yet: the one opened last").containsSame(second);
            first.events().activated().accept(true);
            assertThat(manager.lastActive()).containsSame(first);
            assertThat(first.content().isActiveAndOpen()).isTrue();
            first.requestClose();
            assertThat(manager.lastActive()).containsSame(second);
            second.requestClose();
            assertThat(manager.lastActive()).isEmpty();
        });
        launch();
    }

    @Test void nothingOpensWhileJasperQuits() throws Exception {
        edt(() -> {
            quitting = true;
            assertThat(manager.ensureWindow("handoff", null)).isEmpty();
            assertThat(manager.openWindow(null)).isEmpty();
            assertThat(manager.opening()).isZero();
            assertThat(events).isEmpty();
        });
    }

    /**
     * Workspace tabs spec 6.4: a plugin's tab asked for while the saved windows are being restored waits for them and
     * opens in the first as soon as it is up, with no later turn.
     */
    @Test void aPluginsTabAskedForWhileTheWindowsAreRestoredOpensInTheFirstOnceItIsUp() throws Exception {
        edt(() -> {
            reenter = true;
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            assertThat(manager.windows()).as("no second window").containsExactly(first);
            assertThat(early.window()).as("the tab waited for the window and opened there").isEqualTo(first.id());
            assertThat(first.content().selectedTab()).isSameAs(early);
        });
        launch();
    }

    /** A tab that waited for a window that failed to open goes with it, its failure under the window's. */
    @Test void aTabThatWaitedForAWindowThatFailedToOpenGoesWithIt() throws Exception {
        edt(() -> {
            reenter = true;
            failEarly = true;
            failFrames = true;
            try {
                assertThatThrownBy(() -> manager.ensureWindow("cold", null)).hasMessage("no frame").satisfies(failure ->
                    assertThat(failure.getSuppressed()).extracting(Throwable::getMessage)
                        .containsExactly("early failed to close"));
            } finally {
                failFrames = false;
                failEarly = false;
            }
            assertThat(early.closed()).as("removed, so disposed").isTrue();
            assertThat(model.tabs().tabs()).doesNotContain(early);
        });
        edt(() -> assertThat(manager.windows()).as("nothing asks for a window again").isEmpty());
    }

    /** Composition that asks for a tab with no window, against a window that fails every time, is tried once. */
    @Test void aWindowThatFailsEveryTimeIsComposedOnceForTheTabThatWaitsForIt() throws Exception {
        edt(() -> {
            reenterAlways = true;
            failFrames = true;
            assertThatThrownBy(() -> manager.ensureWindow("cold", null)).hasMessage("no frame");
        });
        try {
            edt(() -> { });
            edt(() -> {
                assertThat(compositions).as("one composition, not a loop").isEqualTo(1);
                assertThat(early.closed()).as("the waiting tab went with the window").isTrue();
            });
        } finally {
            edt(() -> { reenterAlways = false; failFrames = false; });
        }
    }

    /** While every window closes, a closed hook that asks for a window gets none: no fresh one opens. */
    @Test void noWindowOpensWhileEveryWindowCloses() throws Exception {
        List<Optional<JasperWindow>> asked = new ArrayList<>();
        edt(() -> {
            manager.ensureWindow("cold", null).orElseThrow();
            whenClosed = window -> asked.add(manager.ensureWindow("plugin", null));
            try {
                manager.closeAll();
            } finally {
                whenClosed = window -> { };
            }
            assertThat(asked).containsExactly(Optional.empty());
            assertThat(manager.isEmpty()).isTrue();
            assertThat(events.stream().filter(event -> event.startsWith("opened")).count()).as("no fresh window").isEqualTo(1);
        });
        launch();
    }

    @Test void aWindowWhoseFrameFailsIsDiscardedAndLeavesNoTabBehind() throws Exception {
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            events.clear();
            failFrames = true;
            assertThatThrownBy(() -> manager.openWindow(null)).isInstanceOf(IllegalStateException.class).hasMessage("no frame");
            failFrames = false;
            assertThat(manager.windows()).containsExactly(first);
            assertThat(manager.opening()).isZero();
            assertThat(events).containsExactly("closed w1");
            assertThat(model.tabs().tabs()).as("the first window's tab, and none left without a window").singleElement()
                .satisfies(tab -> assertThat(tab.window()).isEqualTo(first.id()));
        });
        launch();
    }

    /** One window's failing close leaves none of the others open; the first failure propagates, the later ones under it. */
    @Test void closingEveryWindowClosesThemAllWhateverOneOfThemThrows() throws Exception {
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            JasperWindow second = manager.openWindow(null).orElseThrow();
            // Guard-less tabs whose disposing fails.
            model.tabs().open("dev.example.editor", "notes", "notes.txt", first.id(), new JPanel())
                .onClosed(() -> { throw new IllegalStateException("notes failed to close"); });
            model.tabs().open("dev.example.editor", "todo", "todo.txt", second.id(), new JPanel())
                .onClosed(() -> { throw new IllegalStateException("todo failed to close"); });
            first.events().activated().accept(true);
            events.clear();
            assertThatThrownBy(manager::closeAll).as("the most recently active closes last").hasMessage("todo failed to close")
                .satisfies(failure -> assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("notes failed to close"));
            assertThat(manager.isEmpty()).as("every window closed").isTrue();
            assertThat(first.closed() && second.closed()).isTrue();
            assertThat(events).containsExactly("closing w1", "dispose w1", "closed w1", "closing w0", "dispose w0", "closed w0");
        });
        launch();
    }

    /** A window's close keeps the closing hook's failure, with its content's under it, and still forgets the window. */
    @Test void aWindowsCloseKeepsTheFirstFailure() throws Exception {
        edt(() -> {
            JasperWindow window = manager.ensureWindow("cold", null).orElseThrow();
            model.tabs().open("dev.example.editor", "notes", "notes.txt", window.id(), new JPanel())
                .onClosed(() -> { throw new IllegalStateException("notes failed to close"); });
            failClosing = true;
            try {
                assertThatThrownBy(window::requestClose).hasMessage("closing failed").satisfies(failure ->
                    assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("notes failed to close"));
            } finally {
                failClosing = false;
            }
            assertThat(window.closed()).isTrue();
            assertThat(manager.windows()).isEmpty();
        });
        launch();
    }

    /** A failed move to a new window keeps its own failure when closing the window it left empty fails too. */
    @Test void aFailedMoveToANewWindowKeepsTheMovesFailure() throws Exception {
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", first.id(), new JPanel());
            boolean[] fail = {true};
            notes.onActive(active -> { if (!active && fail[0]) throw new IllegalStateException("move failed"); });
            failClosing = true;
            try {
                assertThatThrownBy(() -> manager.moveToNewWindow(notes)).hasMessage("move failed").satisfies(failure ->
                    assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("closing failed"));
            } finally {
                failClosing = false;
                fail[0] = false;
            }
            assertThat(manager.windows()).as("the window it left empty went").containsExactly(first);
            assertThat(notes.window()).isEqualTo(first.id());
        });
        launch();
    }

    /** A change listener that throws as a tab moving to a new window changes owner leaves the tab where it was. */
    @Test void aMoveToANewWindowThatAChangeListenerRefusesKeepsTheTab() throws Exception {
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", first.id(), new JPanel());
            boolean[] fail = {true};
            notes.onChanged(() -> {
                if (fail[0] && !first.id().equals(notes.window())) throw new IllegalStateException("listener failed");
            });
            try {
                assertThatThrownBy(() -> manager.moveToNewWindow(notes)).hasMessage("listener failed");
            } finally {
                fail[0] = false;
            }
            assertThat(notes.closed()).as("the tab is not disposed").isFalse();
            assertThat(notes.window()).isEqualTo(first.id());
            assertThat(first.content().selectedTab()).isSameAs(notes);
            assertThat(manager.windows()).as("the window it left empty went").containsExactly(first);
        });
        launch();
    }

    /** While every window closes, no window opens: a closing tab's listener cannot leave one behind. */
    @Test void noWindowOpensForAClosingTabWhileEveryWindowCloses() throws Exception {
        List<Optional<JasperWindow>> asked = new ArrayList<>();
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            JasperWindow second = manager.openWindow(null).orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", second.id(), new JPanel());
            Tab todo = model.tabs().open("dev.example.editor", "todo", "todo.txt", first.id(), new JPanel());
            notes.onClosed(() -> asked.add(manager.openWindow(null)));
            notes.onClosed(() -> asked.add(manager.moveToNewWindow(todo)));
            manager.closeAll();
            assertThat(asked).containsExactly(Optional.empty(), Optional.empty());
            assertThat(manager.isEmpty()).as("no window outlives the others").isTrue();
            assertThat(events.stream().filter(event -> event.startsWith("opened")).count()).isEqualTo(2);
        });
        launch();
    }

    /** A window's requests and the panel route belong to the EDT. */
    @Test void aWindowsRequestsAndThePanelRouteBelongToTheEdt() throws Exception {
        JasperWindow[] window = new JasperWindow[1];
        edt(() -> window[0] = manager.ensureWindow("cold", null).orElseThrow());
        assertThatThrownBy(() -> model.panelRoute(request -> request)).isInstanceOf(IllegalStateException.class)
            .hasMessage("Contributions belong to the EDT");
        assertThatThrownBy(() -> window[0].events().activated().accept(true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("Windows belong to the EDT");
        assertThatThrownBy(window[0]::requestClose).isInstanceOf(IllegalStateException.class).hasMessage("Windows belong to the EDT");
        edt(() -> assertThat(window[0].closed()).isFalse());
        launch();
    }

    @Test void aWindowWhoseFrameFailsIsDiscardedAndForgotten() throws Exception {
        edt(() -> {
            failFrames = true;
            assertThatThrownBy(() -> manager.ensureWindow("cold", null)).isInstanceOf(IllegalStateException.class).hasMessage("no frame");
            assertThat(manager.windows()).isEmpty();
            assertThat(manager.opening()).isZero();
            assertThat(events).containsExactly("closed w0");
            failFrames = false;
            assertThat(manager.ensureWindow("cold", null)).as("the next request opens one").isPresent();
        });
        launch();
    }

    @Test void ensureWindowOpensOneWindowOnlyWhenNoneIsOpen() throws Exception {
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            assertThat(manager.ensureWindow("handoff", null)).containsSame(first);
            assertThat(manager.windows()).containsExactly(first);
            assertThat(events).containsExactly("show w0", "opened w0");
        });
        launch();
    }

    @Test void newWindowOpensAFullWindowInTheGivenDirectory(@TempDir Path folder) throws Exception {
        Path directory = Files.createDirectories(folder.resolve("work"));
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            JasperWindow second = manager.openWindow(directory).orElseThrow();
            assertThat(manager.windows()).containsExactly(first, second);
            assertThat(SwingUtilities.isDescendingFrom(second.content().status(), second.content())).as("full chrome").isTrue();
            assertThat(second.content().tabCount()).isEqualTo(1);
        });
        launch();
        assertThat(started).containsExactly(HOME, directory);
    }

    @Test void closingOneOfSeveralWindowsLeavesTheOthersOpen() throws Exception {
        JasperWindow[] w = new JasperWindow[2];
        edt(() -> { w[0] = manager.ensureWindow("cold", null).orElseThrow(); w[1] = manager.openWindow(null).orElseThrow(); });
        launch();
        edt(() -> {
            events.clear();
            w[0].requestClose();
            assertThat(w[0].closed()).isTrue();
            assertThat(manager.windows()).containsExactly(w[1]);
            assertThat(events).containsExactly("closing w0", "dispose w0", "closed w0");
        });
    }

    @Test void closingTheLastWindowReachesTheClosedHookWithNoWindowLeft() throws Exception {
        JasperWindow[] w = new JasperWindow[1];
        edt(() -> w[0] = manager.ensureWindow("cold", null).orElseThrow());
        launch();
        List<String> heard = new ArrayList<>();
        edt(() -> {
            whenClosed = window -> heard.add(manager.windows().size() + " left, empty " + manager.isEmpty());
            try {
                w[0].requestClose();
            } finally {
                whenClosed = window -> { };
            }
            assertThat(w[0].closed()).isTrue();
            assertThat(heard).containsExactly("0 left, empty true");
        });
    }

    @Test void everyWindowStaysWithAnEmptyWorkspace() throws Exception {
        JasperWindow[] w = new JasperWindow[2];
        edt(() -> { w[0] = manager.ensureWindow("cold", null).orElseThrow(); w[1] = manager.openWindow(null).orElseThrow(); });
        launch();
        edt(() -> {
            w[1].content().closeTab(w[1].content().selectedTab());
            assertThat(w[1].closed()).isFalse();
            assertThat(manager.windows()).containsExactly(w[0], w[1]);
            assertThat(w[1].content().workspace().emptyWorkspace()).isNotNull();
        });
    }

    /**
     * Full windows spec 5.3: a window a move empties closes once the move has reported itself. The active window reports
     * no active pane as it lets the tab go, then the move, the other window's selection and the close: the order the
     * testkit's FakeWorkspace follows (pre-flight F3).
     */
    @Test void aWindowAMoveEmptiesClosesAfterTheMoveIsReported() throws Exception {
        JasperWindow[] w = new JasperWindow[2];
        dev.jasper.app.terminals.TerminalRegistry[] terminals = new dev.jasper.app.terminals.TerminalRegistry[1];
        edt(() -> {
            w[0] = manager.ensureWindow("cold", null).orElseThrow();
            w[1] = manager.openWindow(null).orElseThrow();
            terminals[0] = new dev.jasper.app.terminals.TerminalRegistry();
            for (JasperWindow window : manager.windows()) window.content().connectTerminals(terminals[0], () -> { });
        });
        launch();
        edt(() -> {
            terminals[0].windowActivated(w[0].id());
            Tab tab = w[0].content().selectedTab();
            Function<UUID, String> window = id -> id.equals(w[0].id()) ? "first" : id.equals(w[1].id()) ? "second" : "another window";
            Function<UUID, String> which = id -> id.equals(tab.id().value()) ? "it" : "another tab";
            List<String> heard = new ArrayList<>();
            // Every event on the topics FakeTerminalsTest records, as it records them.
            terminals[0].onEvent(event -> {
                switch (event) {
                    case dev.jasper.app.terminals.TerminalEvent.ActivePaneChanged changed ->
                        heard.add(changed.paneId().isPresent() ? "active pane" : "no active pane");
                    case dev.jasper.app.terminals.TerminalEvent.TabMoved moved -> heard.add(which.apply(moved.tabId()) + " moved from "
                        + window.apply(moved.fromWindowId()) + " to " + window.apply(moved.toWindowId()));
                    case dev.jasper.app.terminals.TerminalEvent.TabSelected selected ->
                        heard.add(window.apply(selected.windowId()) + " selects " + which.apply(selected.tabId()));
                    case dev.jasper.app.terminals.TerminalEvent.WindowClosed closed -> heard.add(window.apply(closed.windowId()) + " closed");
                    default -> { }
                }
            });
            assertThat(manager.moveTabToWindow(w[0].content(), w[0].content().activeParentTab(), w[1].content())).isTrue();
            assertThat(heard).containsSubsequence("it moved from first to second", "first closed");
            assertThat(w[0].closed()).isTrue();
            assertThat(tab.window()).isEqualTo(w[1].id());
        });
        launch();
    }

    @Test void aPluginsTabWithNoOwnerOpensInTheMostRecentlyActiveWindow() throws Exception {
        edt(() -> {
            JasperWindow first = manager.ensureWindow("cold", null).orElseThrow();
            JasperWindow second = manager.openWindow(null).orElseThrow();
            first.events().activated().accept(true);
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            assertThat(notes.window()).as("not the window opened last").isEqualTo(first.id()).isNotEqualTo(second.id());
        });
        launch();
    }

    @Test void twoRequestsWhileNoWindowIsOpenRestoreOnce(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("workspace.toml");
        dev.jasper.app.layout.TabId one = dev.jasper.app.layout.TabId.random(), two = dev.jasper.app.layout.TabId.random();
        WorkspaceFile.write(file, new WorkspaceFile.Snapshot(List.of(savedWindow(one), savedWindow(two))));
        edt(() -> {
            manager.workspace(new WorkspaceStore(file, manager::snapshot, (delay, task) -> () -> { }, Runnable::run, aside -> { }));
            JasperWindow first = manager.ensureWindow("handoff", null).orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            assertThat(manager.windows()).as("the saved windows, each once").hasSize(2);
            assertThat(manager.windows().getFirst()).isSameAs(first);
            assertThat(events.stream().filter(event -> event.startsWith("opened")).toList()).containsExactly("opened w0", "opened w1");
            assertThat(first.content().tabs()).extracting(Tab::id).contains(one);
            assertThat(notes.window()).as("in the first restored").isEqualTo(first.id());
        });
        launch();
    }

    @Test void quitAsksEveryWindowsGuardsAndARefusalKeepsThemAll() throws Exception {
        boolean[] allow = {false};
        JasperWindow[] w = new JasperWindow[2];
        edt(() -> {
            w[0] = manager.ensureWindow("cold", null).orElseThrow();
            w[1] = manager.openWindow(null).orElseThrow();
            model.tabs().open("dev.example.editor", "notes", "notes.txt", w[1].id(), new JPanel());
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(allow[0]));
        });
        launch();
        edt(() -> {
            assertThat(manager.closeEveryWindow()).as("refused").isFalse();
            assertThat(manager.windows()).containsExactly(w[0], w[1]);
            assertThat(closings).isEmpty();
            allow[0] = true;
            assertThat(manager.closeEveryWindow()).isTrue();
            assertThat(manager.isEmpty()).isTrue();
            assertThat(closings).contains("closing w0", "closing w1");
        });
    }

    @Test void moveTabToWindowPutsItAfterTheActiveTabAndRaisesTheTarget() throws Exception {
        JasperWindow[] w = new JasperWindow[2];
        ParentTabId[] a = new ParentTabId[1], b = new ParentTabId[1], c = new ParentTabId[1];
        edt(() -> {
            w[0] = manager.ensureWindow("cold", null).orElseThrow();
            a[0] = w[0].content().activeParentTab();
            w[0].content().newTab(HOME);
            b[0] = w[0].content().activeParentTab();
            w[0].content().selectParentTab(a[0]);
            w[1] = manager.openWindow(null).orElseThrow();
            c[0] = w[1].content().activeParentTab();
        });
        launch();
        edt(() -> {
            events.clear();
            assertThat(manager.moveTabToWindow(w[1].content(), c[0], w[0].content())).isTrue();
            assertThat(w[0].content().windowLayout().parentTabs()).extracting(dev.jasper.app.layout.ParentTab::id).containsExactly(a[0], c[0], b[0]);
            assertThat(w[0].content().activeParentTab()).isEqualTo(c[0]);
            assertThat(events.stream().filter(event -> event.startsWith("front")).toList()).last().isEqualTo("front w0");
            assertThat(w[1].closed()).as("emptied by the move").isTrue();
            assertThat(manager.moveTabToWindow(w[0].content(), a[0], w[0].content())).as("not into its own window").isFalse();
        });
    }

    @Test void savedWindowsComeBackInActivationOrderTheMostRecentInFront(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("workspace.toml");
        WorkspaceFile.Snapshot[] saved = new WorkspaceFile.Snapshot[1];
        JasperWindow[] w = new JasperWindow[3];
        edt(() -> {
            w[0] = manager.ensureWindow("cold", null).orElseThrow();
            w[1] = manager.openWindow(null).orElseThrow();
            w[2] = manager.openWindow(null).orElseThrow();
            w[2].events().activated().accept(true);
            w[1].events().activated().accept(true);
            saved[0] = manager.snapshot().orElseThrow();
            assertThat(saved[0].windows()).hasSize(3);
            assertThat(saved[0].windows().getFirst().layout()).as("the window activated last first").isEqualTo(w[1].content().windowLayout());
            assertThat(saved[0].windows().get(1).layout()).isEqualTo(w[2].content().windowLayout());
        });
        launch();
        WorkspaceFile.write(file, saved[0]);
        TestWindows[] fresh = new TestWindows[1];
        try {
            edt(() -> {
                fresh[0] = new TestWindows();
                fresh[0].manager.workspace(new WorkspaceStore(file, fresh[0].manager::snapshot, (delay, task) -> () -> { }, Runnable::run,
                    aside -> { }));
                JasperWindow first = fresh[0].manager.ensureWindow("cold", null).orElseThrow();
                assertThat(fresh[0].manager.windows()).hasSize(3);
                assertThat(first.content().windowLayout()).isEqualTo(saved[0].windows().getFirst().layout());
                assertThat(fresh[0].events).last().isEqualTo("front w0");
            });
            fresh[0].launch();
        } finally {
            edt(() -> { if (fresh[0] != null) fresh[0].manager.closeAll(); });
        }
    }

    /**
     * Controller Ruling 4: a new window while none is open restores the saved windows first, so its first save cannot
     * replace them; New Window and a plugin's tab placed in a new window alike.
     */
    @Test void aNewWindowWhileNoneIsOpenRestoresTheSavedWindowsFirst(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("workspace.toml");
        dev.jasper.app.layout.TabId one = dev.jasper.app.layout.TabId.random(), two = dev.jasper.app.layout.TabId.random();
        WorkspaceFile.write(file, new WorkspaceFile.Snapshot(List.of(savedWindow(one), savedWindow(two))));
        edt(() -> {
            manager.workspace(new WorkspaceStore(file, manager::snapshot, (delay, task) -> () -> { }, Runnable::run, aside -> { }));
            JasperWindow opened = manager.openWindow(null).orElseThrow();
            assertThat(manager.windows()).as("the two restored, then the new one").hasSize(3).last().isSameAs(opened);
            assertThat(manager.windows().get(0).content().tabs()).extracting(Tab::id).containsExactly(one);
            assertThat(manager.windows().get(1).content().tabs()).extracting(Tab::id).containsExactly(two);
            assertThat(opened.content().tabCount()).isEqualTo(1);
            manager.closeAll();
        });
        launch();
        edt(() -> {
            assertThat(manager.isEmpty()).isTrue();
            Tab away = model.tabs().open("dev.example.editor", "away", "away.txt", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
            assertThat(manager.windows()).as("the three saved as every window closed, then the tab's own").hasSize(4);
            assertThat(manager.windows().get(0).content().tabs()).extracting(Tab::id).containsExactly(one);
            assertThat(away.window()).isEqualTo(manager.windows().getLast().id());
        });
        launch();
    }

    /** A window saved without a place, holding one terminal tab {@code item}. */
    private static WorkspaceFile.WindowRecord savedWindow(dev.jasper.app.layout.TabId item) {
        var parentTab = new dev.jasper.app.layout.ParentTab(ParentTabId.random(), dev.jasper.app.layout.Layout.of(item));
        return new WorkspaceFile.WindowRecord(Optional.empty(), 0, new ParentTabs(List.of(parentTab), parentTab.id()),
            java.util.Map.of(item, new WorkspaceFile.TabRecord.Terminal("", HOME.toString(), "")));
    }
}
