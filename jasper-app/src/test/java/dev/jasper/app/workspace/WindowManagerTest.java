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
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.awt.Rectangle;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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

/** Workspace tabs spec 6.1, 6.4, 6.5 and 17: one main window, child windows and their lifecycle, headless. */
class WindowManagerTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final List<Path> started = new ArrayList<>();
    /** What the stand-in frames did and what the application heard, in order. */
    private final List<String> events = new ArrayList<>();
    /** What the closing hooks heard, with whether the window's content was still whole. */
    private final List<String> closings = new ArrayList<>();
    private boolean quitting, failFrames, reenter, failClosing, reenterAlways, failEarly;
    /** The chrome the main window is composed with; child windows always compose with the standard one. */
    private WorkspaceChrome mainChrome = WorkspaceChrome.STANDARD;
    /** How many times the main window was composed. */
    private int mainCompositions;
    /** The tab a plugin asked for while the main window was being composed. */
    private Tab early;
    /** What the closed hook runs once it has recorded the window. */
    private Consumer<JasperWindow> whenClosed = window -> { };
    private WindowManager manager;

    @BeforeEach void manager() throws Exception {
        edt(() -> manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame,
            window -> events.add("opened " + name(window)), window -> events.add("activated " + name(window)),
            window -> { events.add("closed " + name(window)); whenClosed.accept(window); }, state -> { },
            window -> {
                closings.add("closing " + name(window) + (window.content().closed() ? " (closed)" : ""));
                if (failClosing) throw new IllegalStateException("closing failed");
            },
            () -> closings.add("closing all"))));
    }

    @AfterEach void cleanup() throws Exception {
        edt(() -> manager.closeAll());
        closeOwners();
    }

    private static String name(JasperWindow window) { return window.isMain() ? "main" : "child"; }

    private WindowContent compose(WindowManager.Opening opening) {
        if (quitting) return null;
        if (opening.role() == WindowRole.MAIN) mainCompositions++;
        // A plugin opening its view while the main window is still being composed (a panel factory, say).
        if (reenter || reenterAlways) {
            reenter = false;
            early = model.tabs().open("dev.example.editor", "early", "early.txt", null, new JPanel());
            if (failEarly) early.onClosed(() -> { throw new IllegalStateException("early failed to close"); });
            // Workspace plan 4a Rulings 24: the tab waits for the main window, and a nested request for it is still refused.
            assertThatThrownBy(() -> manager.ensureMain("plugin", null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The main window is still opening");
        }
        var launcher = new ShellLauncher(pending::add, directory -> { started.add(directory); return IdleTerminalSession.start(); },
            "idle-test-session");
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(opening.role(), launcher, opening.directory(), opening.empty(),
            directory -> manager.openTerminalWindow(directory), () -> { }, opening.emptied(), ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs,
            opening.role() == WindowRole.MAIN ? mainChrome : WorkspaceChrome.STANDARD));
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

    @Test void theMainWindowIsMadeOnlyWhenNoneIsOpen() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            assertThat(manager.ensureMain("handoff", null)).containsSame(main);
            assertThat(manager.main()).containsSame(main);
            assertThat(manager.windows()).containsExactly(main);
            assertThat(main.role()).isEqualTo(WindowRole.MAIN);
            assertThat(main.content().isMain()).isTrue();
            assertThat(events).containsExactly("show main", "opened main");
            assertThat(manager.opening()).isZero();
        });
        launch();
    }

    /** Workspace tabs spec 13: a window keeps where its frame last said it is, which workspace.toml saves. */
    @Test void aWindowKeepsWhereItsFrameSaysItIs() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            assertThat(main.placement()).isEqualTo(JasperWindow.Placement.NONE);
            var placed = new JasperWindow.Placement(Optional.of(new UiState.Bounds(40, 60, 1400, 900)), 6);
            main.placed(placed);
            assertThat(main.placement()).isEqualTo(placed);
            assertThatThrownBy(() -> new JasperWindow.Placement(Optional.empty(), java.awt.Frame.ICONIFIED))
                .as("never saved minimized").isInstanceOf(IllegalArgumentException.class);
            main.requestClose();
            main.placed(JasperWindow.Placement.NONE);
            assertThat(main.placement()).as("a closed window keeps its last place").isEqualTo(placed);
        });
        launch();
    }

    @Test void theMainWindowsConstructorIsPackagePrivate() {
        assertThat(MainWindow.class.getDeclaredConstructors()).allSatisfy(constructor ->
            assertThat(Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers())).isFalse());
    }

    @Test void aNewTerminalWindowIsAChildWindowInTheGivenDirectoryAfterTheMainWindow(@TempDir Path folder) throws Exception {
        Path directory = Files.createDirectories(folder.resolve("work"));
        edt(() -> {
            ChildWindow child = manager.openTerminalWindow(directory).orElseThrow();
            assertThat(manager.main()).as("restored first").isPresent();
            assertThat(manager.windows()).containsExactly(manager.main().orElseThrow(), child);
            assertThat(child.role()).isEqualTo(WindowRole.CHILD);
            assertThat(child.content().isMain()).isFalse();
            assertThat(child.content().tabCount()).isEqualTo(1);
            assertThat(events).containsExactly("show main", "opened main", "show child", "opened child");
        });
        launch();
        assertThat(started).as("the main window's tab where a new shell starts, then the child's").containsExactly(HOME, directory);
    }

    @Test void aChildWindowClosesWhenItsLastTabCloses() throws Exception {
        ChildWindow[] child = new ChildWindow[1];
        edt(() -> child[0] = manager.openTerminalWindow(null).orElseThrow());
        launch();
        edt(() -> {
            var terminals = new dev.jasper.app.terminals.TerminalRegistry();
            for (JasperWindow window : manager.windows()) window.content().connectTerminals(terminals, () -> { });
            terminals.windowActivated(child[0].id());
            var facts = new ArrayList<dev.jasper.app.terminals.TerminalEvent>();
            terminals.onEvent(facts::add);
            events.clear();
            Tab tab = child[0].content().selectedTab();
            child[0].content().closeTab(tab);
            assertThat(child[0].closed()).isTrue();
            assertThat(manager.children()).isEmpty();
            assertThat(events).containsExactly("dispose child", "closed child");
            // Plan 3a's accepted order (its final review, M3): the window closes inside the tab's close, and the active
            // pane is reported once that change is complete.
            assertThat(facts).as("the last tab's close, then its window's, then no active pane").endsWith(
                new dev.jasper.app.terminals.TerminalEvent.TabClosed(child[0].id(), tab.id().value()),
                new dev.jasper.app.terminals.TerminalEvent.WindowClosed(child[0].id()),
                new dev.jasper.app.terminals.TerminalEvent.ActivePaneChanged(Optional.empty()));
        });
    }

    @Test void theMainWindowStaysWithAnEmptyWorkspace() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> main[0] = manager.ensureMain("cold", null).orElseThrow());
        launch();
        edt(() -> {
            main[0].content().closeTab(main[0].content().selectedTab());
            assertThat(main[0].closed()).isFalse();
            assertThat(manager.main()).containsSame(main[0]);
            assertThat(main[0].content().workspace().emptyWorkspace()).isNotNull();
        });
    }

    @Test void closingTheMainWindowAsksEveryWindowsGuardsThenClosesTheChildWindowsFirst() throws Exception {
        boolean[] allow = {false};
        int[] asked = {0};
        ChildWindow[] child = new ChildWindow[1];
        Tab[] notes = new Tab[1];
        edt(() -> {
            child[0] = manager.openTerminalWindow(null).orElseThrow();
            notes[0] = model.tabs().open("dev.example.editor", "notes", "notes.txt", child[0].id(), new JPanel());
            model.tabs().guard("dev.example.editor", (tabs, batch) -> { asked[0]++; return CompletableFuture.completedFuture(allow[0]); });
        });
        launch();
        edt(() -> {
            MainWindow main = manager.main().orElseThrow();
            events.clear();
            main.requestClose();
            assertThat(asked[0]).as("the child window's tab was asked").isEqualTo(1);
            assertThat(manager.windows()).as("a refusal closes nothing").containsExactly(main, child[0]);
            assertThat(events).isEmpty();
            allow[0] = true;
            main.requestClose();
            assertThat(manager.isEmpty()).isTrue();
            assertThat(notes[0].closed()).isTrue();
            assertThat(events).containsExactly("dispose child", "closed child", "dispose main", "closed main");
        });
    }

    @Test void aChildWindowClosedByItsUserClosesItsTabsAfterTheirGuards() throws Exception {
        boolean[] allow = {false};
        ChildWindow[] child = new ChildWindow[1];
        Tab[] notes = new Tab[1];
        edt(() -> {
            child[0] = manager.openTerminalWindow(null).orElseThrow();
            notes[0] = model.tabs().open("dev.example.editor", "notes", "notes.txt", child[0].id(), new JPanel());
            model.tabs().guard("dev.example.editor", (tabs, batch) -> CompletableFuture.completedFuture(allow[0]));
        });
        launch();
        edt(() -> {
            child[0].requestClose();
            assertThat(child[0].closed()).as("an unsaved document keeps it open").isFalse();
            allow[0] = true;
            child[0].requestClose();
            assertThat(child[0].closed()).isTrue();
            assertThat(notes[0].closed()).isTrue();
            assertThat(manager.main()).as("the main window stays").isPresent();
        });
    }

    @Test void aTerminalTabMovedToANewWindowAndBackKeepsItsSession() throws Exception {
        MainWindow[] main = new MainWindow[1];
        edt(() -> {
            main[0] = manager.ensureMain("cold", null).orElseThrow();
            main[0].content().newTab(HOME);
        });
        launch();
        edt(() -> {
            Tab tab = main[0].content().selectedTab();
            var pane = ((TerminalContent) tab.content()).pane();
            var session = pane.session();
            assertThat(session).isNotNull();
            ChildWindow child = manager.moveToNewWindow(tab).orElseThrow();
            assertThat(tab.window()).isEqualTo(child.id());
            assertThat(child.content().selectedTab()).isSameAs(tab);
            assertThat(main[0].content().tabCount()).as("the main window keeps its other tab").isEqualTo(1);
            assertThat(manager.moveToNewWindow(tab)).as("a child window's only tab stays where it is").isEmpty();
            assertThat(manager.children()).containsExactly(child);
            model.tabs().moveToMainWindow(tab);
            assertThat(tab.window()).isEqualTo(main[0].id());
            assertThat(child.closed()).as("emptied, it closed").isTrue();
            assertThat(manager.children()).isEmpty();
            assertThat(pane.session()).as("the same session throughout").isSameAs(session);
            assertThat(session.exitFuture()).isNotDone();
        });
    }

    @Test void aPluginsViewWithNoWindowOpensTheMainWindowFirst() throws Exception {
        edt(() -> {
            assertThat(manager.isEmpty()).isTrue();
            Tab tab = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            MainWindow main = manager.main().orElseThrow();
            assertThat(tab.window()).isEqualTo(main.id());
            manager.closeAll();
            assertThat(manager.isEmpty()).isTrue();
            MainWindow again = manager.ensureMain("reopen", null).orElseThrow();
            assertThat(again).as("a fresh main window").isNotSameAs(main);
        });
        launch();
    }

    @Test void aPluginsTabMovesThroughTheRegistryToANewWindowAndBack() throws Exception {
        edt(() -> {
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            MainWindow main = manager.main().orElseThrow();
            assertThat(manager.isMain(notes.window())).isTrue();
            notes.moveToNewWindow();
            ChildWindow child = manager.children().getFirst();
            assertThat(notes.window()).isEqualTo(child.id());
            assertThat(manager.isMain(notes.window())).isFalse();
            assertThat(child.content().parentTabCount()).as("a full window, the item in a tab of its own (spec 13)").isEqualTo(1);
            assertThat(child.content().selectedTab()).isSameAs(notes);
            notes.moveToNewWindow();
            assertThat(manager.children()).as("a child window's only tab stays").containsExactly(child);
            notes.moveToMainWindow();
            assertThat(notes.window()).isEqualTo(main.id());
            assertThat(child.closed()).isTrue();
        });
        launch();
    }

    @Test void aPluginsTabPlacedBesideOpensAGroupAndOnePlacedInANewWindowAChildWindow() throws Exception {
        edt(() -> {
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            MainWindow main = manager.main().orElseThrow();
            assertThat(main.content().workspace().model().groups()).hasSize(1);
            Tab beside = model.tabs().open("dev.example.editor", "beside", "beside.txt", null, new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            assertThat(main.content().workspace().model().groups()).as("a new group").hasSize(2);
            assertThat(beside.group()).isNotEqualTo(notes.group());
            Tab selected = main.content().selectedTab();
            Tab away = model.tabs().open("dev.example.editor", "away", "away.txt", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
            assertThat(away.window()).isEqualTo(manager.children().getFirst().id());
            assertThat(manager.children().getFirst().content().tabCount()).isEqualTo(1);
            assertThat(main.content().workspace().model().groups()).as("the main window is not touched").hasSize(2);
            assertThat(main.content().selectedTab()).isSameAs(selected);
        });
        launch();
    }

    /** Tabs and panes spec 13: a plugin's tab with no window opens as a new tab of the main window; new windows are full. */
    @Test void aPluginsTabOpensANewTabOfTheMainWindowAndNewWindowsAreFullWindows() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            var content = main.content();
            ParentTabId first = content.activeParentTab();
            content.newTab(HOME);
            content.selectParentTab(first);
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
            assertThat(content.parentTabCount()).as("a new tab of the main window").isEqualTo(3);
            assertThat(content.activeParentTab()).as("at the end of its strip, shown").isEqualTo(content.parentTabOf(notes).orElseThrow())
                .isEqualTo(content.windowLayout().parentTabs().getLast().id());
            Tab away = model.tabs().open("dev.example.editor", "away", "away.txt", null, new JPanel(), TabRegistry.Placement.NEW_WINDOW);
            ChildWindow child = manager.children().getFirst();
            assertThat(child.content().windowLayout()).as("TabPlacement.NEW_WINDOW opens a full window").isInstanceOf(ParentTabs.class);
            assertThat(child.content().parentTabOf(away)).isPresent();
            child.content().newTab(HOME);
            assertThat(child.content().parentTabCount()).as("which opens parent tabs of its own").isEqualTo(2);
            ChildWindow terminal = manager.openTerminalWindow(null).orElseThrow();
            assertThat(terminal.content().windowLayout()).as("New Terminal Window too").isInstanceOf(ParentTabs.class);
            assertThat(content.parentTabCount()).as("the main window is not touched").isEqualTo(3);
        });
        launch();
    }

    @Test void theWindowCommandsOpenCloseAndMoveWindows(@TempDir Path folder) throws Exception {
        Path directory = Files.createDirectories(folder.resolve("work"));
        MainWindow[] main = new MainWindow[1];
        edt(() -> {
            main[0] = manager.ensureMain("cold", null).orElseThrow();
            main[0].content().newTab(directory);
        });
        launch();
        edt(() -> main[0].content().invoke(ActionId.NEW_TERMINAL_WINDOW));
        launch();
        assertThat(started).as("New Terminal Window: in the current terminal's directory").last().isEqualTo(directory);
        edt(() -> {
            ChildWindow child = manager.children().getFirst();
            child.content().invoke(ActionId.CLOSE_WINDOW);
            assertThat(child.closed()).as("Close Window").isTrue();
            main[0].content().newTab(directory);
            ParentTabId moving = main[0].content().activeParentTab();
            main[0].content().invoke(ActionId.MOVE_TAB_TO_NEW_WINDOW);
            ChildWindow moved = manager.children().getFirst();
            assertThat(moved.content().activeParentTab()).isEqualTo(moving);
            moved.content().updateActions();
            assertThat(moved.content().action(ActionId.MOVE_TAB_TO_NEW_WINDOW).isEnabled()).as("its only tab stays").isFalse();
            assertThat(moved.content().action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isTrue();
            moved.content().invoke(ActionId.MOVE_TO_MAIN_WINDOW);
            assertThat(main[0].content().parentTabs().view(moving)).as("back in the main window").isPresent();
            assertThat(moved.closed()).isTrue();
            main[0].content().updateActions();
            assertThat(main[0].content().action(ActionId.MOVE_TO_MAIN_WINDOW).isEnabled()).isFalse();
            main[0].content().invoke(ActionId.CLOSE_WINDOW);
            assertThat(manager.isEmpty()).as("Close Window on the main window closes it").isTrue();
        });
    }

    /** Workspace plan 8a Rulings 8: a child window's entry menu leaves Move to Main Window to the ⋮. */
    @Test void aChildWindowsTabMenuLeavesMoveToMainWindowToItsWindowMenu() throws Exception {
        edt(() -> {
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            var menu = child.content().tabMenu(child.content().selectedTab());
            var titles = new ArrayList<String>();
            for (var item : menu.getComponents()) if (item instanceof javax.swing.JMenuItem entry) titles.add(entry.getText());
            assertThat(titles).containsExactly("Rename Tab", "Move Tab to New Window", "Move Tab Into", "Close Tab");
            assertThat(child.content().windowMenuItems()).contains(ActionId.MOVE_TO_MAIN_WINDOW);
        });
        launch();
    }

    @Test void aChildWindowsPanelRequestGoesToTheMainWindow() throws Exception {
        edt(() -> {
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("panel"));
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            MainWindow main = manager.main().orElseThrow();
            events.clear();
            model.requestPanel(new Contributions.PanelRequest(child.id(), "test.panel", Contributions.PanelRequest.Op.SHOW));
            assertThat(main.content().dock().visible("test.panel")).as("the main window has the dock").isTrue();
            assertThat(events).as("and comes forward to show it").containsExactly("front main");
        });
        launch();
    }

    /**
     * A tool window toggle from a child window shows the panel in the main window, as IntelliJ's does from a detached
     * editor.
     */
    @Test void aPanelToggleFromAChildWindowShowsThePanel() throws Exception {
        edt(() -> {
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("panel"));
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            MainWindow main = manager.main().orElseThrow();
            model.requestPanel(new Contributions.PanelRequest(main.id(), "test.panel", Contributions.PanelRequest.Op.SHOW));
            assertThat(main.content().dock().visible("test.panel")).isTrue();
            events.clear();
            model.requestPanel(new Contributions.PanelRequest(child.id(), "test.panel", Contributions.PanelRequest.Op.TOGGLE));
            assertThat(main.content().dock().visible("test.panel")).as("still showing").isTrue();
            assertThat(events).as("and the main window comes forward").containsExactly("front main");
        });
        launch();
    }

    /**
     * A user's View toggle from a child window reaches the main window as an activating show: under flush frames the panel
     * becomes the active tool window there, and a second toggle never hides it.
     */
    @Test void anActivatingPanelToggleFromAChildWindowShowsAndActivatesThePanel() throws Exception {
        mainChrome = DockChromeTest.classicDock();
        edt(() -> {
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("panel"));
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            MainWindow main = manager.main().orElseThrow();
            assertThat(main.content().dock().visible("test.panel")).isFalse();
            events.clear();
            model.requestPanel(new Contributions.PanelRequest(child.id(), "test.panel", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE));
            assertThat(main.content().dock().visible("test.panel")).as("shown in the main window").isTrue();
            assertThat(main.content().area().focusedSlot()).as("and the active tool window there").hasValue(Slot.LEFT_TOP);
            assertThat(events).as("the main window comes forward").containsExactly("front main");
            events.clear();
            model.requestPanel(new Contributions.PanelRequest(child.id(), "test.panel", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE));
            assertThat(main.content().dock().visible("test.panel")).as("a routed toggle never hides").isTrue();
            assertThat(main.content().area().focusedSlot()).hasValue(Slot.LEFT_TOP);
            assertThat(events).containsExactly("front main");
        });
        launch();
    }

    /**
     * A child window's request for a tool window detached into a window of its own raises that window, not the main window:
     * the request reaches the main window as an activating show, which the window's own shell raises.
     */
    @Test void aChildWindowsRequestForADetachedPanelRaisesItsWindowNotTheMainWindow() throws Exception {
        edt(() -> {
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("panel"));
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            MainWindow main = manager.main().orElseThrow();
            var windows = main.content().detachedWindows();
            windows.ready = () -> true;
            var screen = new Rectangle(0, 0, 1920, 1080);
            windows.displays = () -> List.of(new WindowPlacement.Display(screen, screen));
            main.content().detachedShells((opening, heard) -> new DetachedToolWindows.Shell(() -> events.add("show panel window"),
                () -> events.add("front panel window"), () -> events.add("dispose panel window"), () -> false, bounds -> { }, frame -> { }));
            main.content().dock().setBounds("test.panel", new Rectangle(100, 100, 400, 300));
            main.content().dock().setMode("test.panel", ToolWindowMode.FLOAT);
            assertThat(main.content().panelDetached("test.panel")).as("detached while it is hidden").isTrue();
            List<Contributions.PanelRequest> heard = new ArrayList<>();
            var probe = model.onPanelRequest(heard::add);
            try {
                events.clear();
                model.requestPanel(new Contributions.PanelRequest(child.id(), "test.panel", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE));
                assertThat(heard).containsExactly(new Contributions.PanelRequest(main.id(), "test.panel", Contributions.PanelRequest.Op.ACTIVATING_SHOW));
                assertThat(main.content().dock().visible("test.panel")).isTrue();
                assertThat(events).as("its own window opens and comes forward; the main window does not").containsExactly("show panel window", "front panel window");

                events.clear();
                model.requestPanel(new Contributions.PanelRequest(child.id(), "test.panel", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE));
                assertThat(main.content().dock().visible("test.panel")).as("a routed toggle never hides").isTrue();
                assertThat(events).as("raised again").containsExactly("front panel window");

                events.clear();
                model.requestPanel(new Contributions.PanelRequest(child.id(), "test.panel", Contributions.PanelRequest.Op.HIDE));
                assertThat(events).containsExactly("dispose panel window");
                model.requestPanel(new Contributions.PanelRequest(child.id(), "test.panel", Contributions.PanelRequest.Op.TOGGLE));
                assertThat(main.content().dock().visible("test.panel")).isTrue();
                assertThat(events).as("a plain toggle shows its window and fronts no main window").doesNotContain("front main");
            } finally { probe.close(); }
        });
        launch();
    }

    /** Workspace tabs spec 6.1: while the main window closes, a request for it opens no second one beside it. */
    @Test void aRequestWhileTheMainWindowClosesOpensNoSecondOne() throws Exception {
        List<Optional<MainWindow>> asked = new ArrayList<>();
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), new JPanel());
            notes.onClosed(() -> asked.add(manager.ensureMain("plugin", null)));
            List<String> refused = new ArrayList<>();
            notes.onClosed(() -> {
                try {
                    model.tabs().open("dev.example.editor", "late", "late.txt", null, new JPanel());
                } catch (IllegalStateException failure) {
                    refused.add(failure.getMessage());
                }
            });
            manager.closeAll();
            assertThat(asked).as("a closing main window is not absent").containsExactly(Optional.empty());
            assertThat(refused).as("a plugin's tab with no window, while the main window closes")
                .containsExactly("The main window is closing or Jasper is quitting");
            assertThat(manager.isEmpty()).isTrue();
            assertThat(events.stream().filter("opened main"::equals).count()).as("no second main window").isEqualTo(1);
        });
        launch();
    }

    /** For plan 6: the hooks hear of every window while it is whole, and of closing them all once before any closes. */
    @Test void theClosingHooksHearOfEachWindowWhileItIsWholeAndOfClosingThemAllFirst() throws Exception {
        edt(() -> {
            manager.openTerminalWindow(null).orElseThrow().requestClose();
            assertThat(closings).containsExactly("closing child");
            manager.openTerminalWindow(null).orElseThrow();
            closings.clear();
            manager.closeAll();
            assertThat(closings).containsExactly("closing all", "closing child", "closing main");
            manager.closeAll();
            assertThat(closings).as("nothing is left to close").hasSize(3);
        });
        launch();
    }

    /** For plan 5: a torn-out tab's window opens at its bounds, and the window on top comes first. */
    @Test void aTornOutTabsWindowOpensAtItsBoundsAndTheWindowsListTheLastActiveFirst() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            main.content().newTab(HOME);
            events.clear();
            ChildWindow child = manager.moveTabToNewWindow(main.content(), main.content().activeParentTab(),
                new java.awt.Rectangle(40, 50, 600, 400), new java.awt.Point(90, 60)).orElseThrow();
            assertThat(child.content().parentTabCount()).as("a torn-out tab's window is a full window").isEqualTo(1);
            assertThat(events).as("placed before it shows").containsSubsequence("place child 40,50 600x400 under 90,60", "show child");
            assertThatThrownBy(() -> new WindowManager.Opening(WindowRole.CHILD, true, null, "menu", () -> { },
                Optional.of(new java.awt.Rectangle(40, 50, 600, 400)), Optional.empty()))
                .as("its bounds come with the cursor").isInstanceOf(IllegalArgumentException.class);
            assertThat(manager.windowsByActivation()).as("none came forward yet").containsExactly(main, child);
            child.events().activated().accept(true);
            assertThat(manager.windowsByActivation()).containsExactly(child, main);
            main.events().activated().accept(true);
            assertThat(manager.windowsByActivation()).containsExactly(main, child);
            child.requestClose();
            assertThat(manager.windowsByActivation()).containsExactly(main);
        });
        launch();
    }

    @Test void theWindowThatCameToTheFrontLastIsTheLastActive() throws Exception {
        edt(() -> {
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            MainWindow main = manager.main().orElseThrow();
            assertThat(manager.lastActive()).as("none came forward yet: the main window").containsSame(main);
            child.events().activated().accept(true);
            assertThat(manager.lastActive()).containsSame(child);
            assertThat(child.content().isActiveAndOpen()).isTrue();
            child.requestClose();
            assertThat(manager.lastActive()).containsSame(main);
        });
        launch();
    }

    @Test void nothingOpensWhileJasperQuits() throws Exception {
        edt(() -> {
            quitting = true;
            assertThat(manager.ensureMain("handoff", null)).isEmpty();
            assertThat(manager.openTerminalWindow(null)).isEmpty();
            assertThat(manager.opening()).isZero();
            assertThat(events).isEmpty();
        });
    }

    /**
     * Workspace tabs spec 6.4: a plugin's tab asked for while the main window is being composed waits for it and opens
     * there as soon as it is up, with no later turn.
     */
    @Test void aPluginsTabAskedForWhileTheMainWindowOpensOpensThereOnceItIsUp() throws Exception {
        edt(() -> {
            reenter = true;
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            assertThat(manager.windows()).as("no second main window").containsExactly(main);
            assertThat(early.window()).as("the tab waited for the main window and opened there").isEqualTo(main.id());
            assertThat(main.content().selectedTab()).isSameAs(early);
        });
        launch();
    }

    /** A tab that waited for a main window that failed to open goes with it, its failure under the main window's. */
    @Test void aTabThatWaitedForAMainWindowThatFailedToOpenGoesWithIt() throws Exception {
        edt(() -> {
            reenter = true;
            failEarly = true;
            failFrames = true;
            try {
                assertThatThrownBy(() -> manager.ensureMain("cold", null)).hasMessage("no frame").satisfies(failure ->
                    assertThat(failure.getSuppressed()).extracting(Throwable::getMessage)
                        .containsExactly("early failed to close"));
            } finally {
                failFrames = false;
                failEarly = false;
            }
            assertThat(early.closed()).as("removed, so disposed").isTrue();
            assertThat(model.tabs().tabs()).doesNotContain(early);
        });
        edt(() -> assertThat(manager.main()).as("nothing asks for the main window again").isEmpty());
    }

    /** Composition that asks for a tab with no window, against a main window that fails every time, is tried once. */
    @Test void aMainWindowThatFailsEveryTimeIsComposedOnceForTheTabThatWaitsForIt() throws Exception {
        edt(() -> {
            reenterAlways = true;
            failFrames = true;
            assertThatThrownBy(() -> manager.ensureMain("cold", null)).hasMessage("no frame");
        });
        try {
            edt(() -> { });
            edt(() -> {
                assertThat(mainCompositions).as("one composition, not a loop").isEqualTo(1);
                assertThat(early.closed()).as("the waiting tab went with the main window").isTrue();
            });
        } finally {
            edt(() -> { reenterAlways = false; failFrames = false; });
        }
    }

    /** While every window closes, a closed hook that asks for the main window gets none: no fresh one opens. */
    @Test void noMainWindowOpensWhileEveryWindowCloses() throws Exception {
        List<Optional<MainWindow>> asked = new ArrayList<>();
        edt(() -> {
            manager.ensureMain("cold", null).orElseThrow();
            whenClosed = window -> asked.add(manager.ensureMain("plugin", null));
            try {
                manager.closeAll();
            } finally {
                whenClosed = window -> { };
            }
            assertThat(asked).containsExactly(Optional.empty());
            assertThat(manager.isEmpty()).isTrue();
            assertThat(events.stream().filter("opened main"::equals).count()).as("no fresh main window").isEqualTo(1);
        });
        launch();
    }

    /**
     * Workspace tabs spec 6.4: a child window a move empties closes once the move has reported itself. The active window
     * reports no active pane as it lets the tab go, then the move, the main window's selection and the close: the order
     * the testkit's FakeWorkspace follows (pre-flight F3).
     */
    @Test void aChildWindowAMoveEmptiesClosesAfterTheMoveIsReported() throws Exception {
        ChildWindow[] child = new ChildWindow[1];
        dev.jasper.app.terminals.TerminalRegistry[] terminals = new dev.jasper.app.terminals.TerminalRegistry[1];
        edt(() -> {
            child[0] = manager.openTerminalWindow(null).orElseThrow();
            terminals[0] = new dev.jasper.app.terminals.TerminalRegistry();
            for (JasperWindow window : manager.windows()) window.content().connectTerminals(terminals[0], () -> { });
        });
        launch();
        edt(() -> {
            terminals[0].windowActivated(child[0].id());
            var mainId = manager.main().orElseThrow().id();
            Tab tab = child[0].content().selectedTab();
            Function<UUID, String> window = id -> id.equals(mainId) ? "main" : id.equals(child[0].id()) ? "child" : "another window";
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
            model.tabs().moveToMainWindow(tab);
            assertThat(heard).containsExactly("no active pane", "it moved from child to main", "main selects it", "child closed");
            assertThat(child[0].closed()).isTrue();
            assertThat(tab.window()).isEqualTo(manager.main().orElseThrow().id());
        });
        launch();
    }

    @Test void aChildWindowWhoseFrameFailsIsDiscardedAndLeavesNoTabBehind() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            events.clear();
            failFrames = true;
            assertThatThrownBy(() -> manager.openTerminalWindow(null)).isInstanceOf(IllegalStateException.class).hasMessage("no frame");
            failFrames = false;
            assertThat(manager.windows()).containsExactly(main);
            assertThat(manager.opening()).isZero();
            assertThat(events).containsExactly("closed child");
            assertThat(model.tabs().tabs()).as("the main window's tab, and none left without a window").singleElement()
                .satisfies(tab -> assertThat(tab.window()).isEqualTo(main.id()));
        });
        launch();
    }

    /** One window's failing close leaves none of the others open; the first failure propagates, the later ones under it. */
    @Test void closingEveryWindowClosesThemAllWhateverOneOfThemThrows() throws Exception {
        edt(() -> {
            ChildWindow first = manager.openTerminalWindow(null).orElseThrow();
            ChildWindow second = manager.openTerminalWindow(null).orElseThrow();
            // Guard-less tabs whose disposing fails.
            model.tabs().open("dev.example.editor", "notes", "notes.txt", first.id(), new JPanel())
                .onClosed(() -> { throw new IllegalStateException("notes failed to close"); });
            model.tabs().open("dev.example.editor", "todo", "todo.txt", second.id(), new JPanel())
                .onClosed(() -> { throw new IllegalStateException("todo failed to close"); });
            events.clear();
            assertThatThrownBy(manager::closeAll).hasMessage("notes failed to close").satisfies(failure ->
                assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("todo failed to close"));
            assertThat(manager.isEmpty()).as("every window closed").isTrue();
            assertThat(first.closed() && second.closed()).isTrue();
            assertThat(events).containsExactly("dispose child", "closed child", "dispose child", "closed child", "dispose main",
                "closed main");
        });
        launch();
    }

    /** A window's close keeps the closing hook's failure, with its content's under it, and still forgets the window. */
    @Test void aWindowsCloseKeepsTheFirstFailure() throws Exception {
        edt(() -> {
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            model.tabs().open("dev.example.editor", "notes", "notes.txt", child.id(), new JPanel())
                .onClosed(() -> { throw new IllegalStateException("notes failed to close"); });
            failClosing = true;
            try {
                assertThatThrownBy(child::requestClose).hasMessage("closing failed").satisfies(failure ->
                    assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("notes failed to close"));
            } finally {
                failClosing = false;
            }
            assertThat(child.closed()).isTrue();
            assertThat(manager.children()).isEmpty();
        });
        launch();
    }

    /** A failed move to a new window keeps its own failure when closing the window it left empty fails too. */
    @Test void aFailedMoveToANewWindowKeepsTheMovesFailure() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), new JPanel());
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
            assertThat(manager.children()).as("the window it left empty went").isEmpty();
            assertThat(notes.window()).isEqualTo(main.id());
        });
        launch();
    }

    /** A change listener that throws as a tab moving to a new window changes owner leaves the tab where it was. */
    @Test void aMoveToANewWindowThatAChangeListenerRefusesKeepsTheTab() throws Exception {
        edt(() -> {
            MainWindow main = manager.ensureMain("cold", null).orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", main.id(), new JPanel());
            boolean[] fail = {true};
            notes.onChanged(() -> {
                if (fail[0] && !main.id().equals(notes.window())) throw new IllegalStateException("listener failed");
            });
            try {
                assertThatThrownBy(() -> manager.moveToNewWindow(notes)).hasMessage("listener failed");
            } finally {
                fail[0] = false;
            }
            assertThat(notes.closed()).as("the tab is not disposed").isFalse();
            assertThat(notes.window()).isEqualTo(main.id());
            assertThat(main.content().selectedTab()).isSameAs(notes);
            assertThat(manager.children()).as("the window it left empty went").isEmpty();
        });
        launch();
    }

    /** While every window closes, no child window opens: a closing tab's listener cannot leave one behind. */
    @Test void noChildWindowOpensWhileEveryWindowCloses() throws Exception {
        List<Optional<ChildWindow>> asked = new ArrayList<>();
        edt(() -> {
            ChildWindow child = manager.openTerminalWindow(null).orElseThrow();
            MainWindow main = manager.main().orElseThrow();
            Tab notes = model.tabs().open("dev.example.editor", "notes", "notes.txt", child.id(), new JPanel());
            Tab todo = model.tabs().open("dev.example.editor", "todo", "todo.txt", main.id(), new JPanel());
            notes.onClosed(() -> asked.add(manager.openTerminalWindow(null)));
            notes.onClosed(() -> asked.add(manager.moveToNewWindow(todo)));
            manager.closeAll();
            assertThat(asked).containsExactly(Optional.empty(), Optional.empty());
            assertThat(manager.isEmpty()).as("no window outlives the main window").isTrue();
            assertThat(events.stream().filter("opened child"::equals).count()).isEqualTo(1);
        });
        launch();
    }

    /** A window's requests and the panel route belong to the EDT. */
    @Test void aWindowsRequestsAndThePanelRouteBelongToTheEdt() throws Exception {
        ChildWindow[] child = new ChildWindow[1];
        edt(() -> child[0] = manager.openTerminalWindow(null).orElseThrow());
        assertThatThrownBy(() -> model.panelRoute(request -> request)).isInstanceOf(IllegalStateException.class)
            .hasMessage("Contributions belong to the EDT");
        assertThatThrownBy(() -> child[0].events().activated().accept(true)).isInstanceOf(IllegalStateException.class)
            .hasMessage("Windows belong to the EDT");
        assertThatThrownBy(child[0]::requestClose).isInstanceOf(IllegalStateException.class).hasMessage("Windows belong to the EDT");
        edt(() -> assertThat(child[0].closed()).isFalse());
        launch();
    }

    @Test void aWindowWhoseFrameFailsIsDiscardedAndForgotten() throws Exception {
        edt(() -> {
            failFrames = true;
            assertThatThrownBy(() -> manager.ensureMain("cold", null)).isInstanceOf(IllegalStateException.class).hasMessage("no frame");
            assertThat(manager.main()).isEmpty();
            assertThat(manager.opening()).isZero();
            assertThat(events).containsExactly("closed main");
            failFrames = false;
            assertThat(manager.ensureMain("cold", null)).as("the next request opens one").isPresent();
        });
        launch();
    }
}
