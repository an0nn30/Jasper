package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.palette.CommandsScope;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Workspace tabs spec 6.2, 6.3 and 10: the main window's chrome and its empty workspace; a child window's workspace alone. */
class WindowRolesTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    @Test void aChildWindowShowsItsWorkspaceAndNoChrome() throws Exception {
        edt(() -> {
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { });
            assertThat(child.role()).isEqualTo(WindowRole.CHILD);
            assertThat(child.isMain()).isFalse();
            assertThat(child.getComponentCount()).as("its workspace and nothing else").isEqualTo(1);
            assertThat(SwingUtilities.isDescendingFrom(child.workspace(), child)).isTrue();
            assertThat(SwingUtilities.isDescendingFrom(child.chrome().header(), child)).as("no header or toolbar").isFalse();
            assertThat(SwingUtilities.isDescendingFrom(child.status(), child)).as("no status bar").isFalse();
            assertThat(child.area().getParent()).as("the centre surface alone: no dock or stripes around it").isSameAs(child);
            assertThat(SwingUtilities.isDescendingFrom(child.workspace(), child.area())).as("as the main window's").isTrue();
            assertThat(SwingUtilities.isDescendingFrom(child.leftStripe(), child)).as("no stripes").isFalse();
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> { });
            assertThat(main.role()).isEqualTo(WindowRole.MAIN);
            assertThat(SwingUtilities.isDescendingFrom(main.chrome().header(), main)).isTrue();
            assertThat(SwingUtilities.isDescendingFrom(main.status(), main)).isTrue();
            assertThat(SwingUtilities.isDescendingFrom(main.workspace(), main.area())).as("the dock's centre").isTrue();
        });
        launch();
    }

    /** Workspace tabs spec 6.3: a child window's workspace has the same translucency as the main window's. */
    @Test void aChildWindowsWorkspaceIsGlassAsTheMainWindowsIs() throws Exception {
        edt(() -> {
            // An empty child window: nothing but the centre surface paints the middle of its workspace.
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, true, () -> { });
            child.setSize(900, 600);
            LayoutTestSupport.layoutTree(child);
            LayoutTestSupport.layoutTree(child);
            var workspace = child.workspace();
            assertThat(workspace.getWidth()).as("the workspace is laid out").isPositive();
            Point middle = SwingUtilities.convertPoint(workspace, workspace.getWidth() / 2, workspace.getHeight() / 2, child);
            assertThat(alpha(child, middle)).as("opaque").isEqualTo(255);
            child.applyBackgroundOpacity(0.5);
            assertThat(alpha(child, middle)).as("glass of the terminal background, as in the main window").isEqualTo(128);
            child.applyBackgroundOpacity(1);
            assertThat(alpha(child, middle)).isEqualTo(255);
        });
    }

    private static int alpha(JComponent root, Point at) {
        var image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { root.paint(graphics); } finally { graphics.dispose(); }
        return new Color(image.getRGB(at.x, at.y), true).getAlpha();
    }

    @Test void aChildWindowTakesTheKeymapAndThePalette() throws Exception {
        WindowContent[] child = new WindowContent[1];
        edt(() -> child[0] = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { }));
        launch();
        edt(() -> {
            var root = WindowCommandPaletteTest.install(child[0]);
            KeyStroke newTab = child[0].bindings().strokeFor(ActionId.NEW_TAB).orElseThrow();
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(newTab)).as("the Keymap on the child's own root")
                .isEqualTo(ActionId.NEW_TAB.id());
            assertThat(child[0].dispatchShortcut(newTab, child[0])).isTrue();
            assertThat(child[0].tabCount()).isEqualTo(2);
            child[0].commandPalette().toggle();
            assertThat(child[0].commandPalette().isOpen()).as("the palette opens over the child window").isTrue();
            child[0].commandPalette().dismiss();
        });
        launch();
    }

    @Test void aChildWindowLeavesTheMainWindowsChromeAlone() throws Exception {
        edt(() -> {
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { });
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> { });
            for (var window : java.util.List.of(child, main)) {
                window.updateActions();
                boolean expected = window.isMain();
                assertThat(window.action(ActionId.TOGGLE_ZEN_MODE).isEnabled()).as("Zen").isEqualTo(expected);
                assertThat(window.windowCommands().view("view.status_bar").isEnabled()).as("Status Bar").isEqualTo(expected);
                assertThat(window.windowCommands().view("view.rail").isEnabled()).as("Rail").isEqualTo(expected);
                assertThat(window.windowCommands().view("view.toolbar.icons").isEnabled()).as("Toolbar").isEqualTo(expected);
                assertThat(window.action(ActionId.NEW_TAB).isEnabled()).as("its own tabs").isTrue();
            }
        });
        launch();
    }

    @Test void aChildWindowBuildsNoToolbarViewsAndDocksNoPanels() throws Exception {
        edt(() -> {
            var model = new Contributions();
            int[] asked = {0};
            model.addToolbar(new ToolbarEntry.View(window -> { asked[0]++; return Optional.empty(); }));
            model.addPanel("test.panel", "Panel", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("panel"));
            var child = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> { });
            child.connectContributions(model);
            child.connectTerminals(new TerminalRegistry(), () -> { });
            assertThat(asked[0]).as("no toolbar, no toolbar views").isZero();
            assertThat(child.dock().slot("test.panel")).as("panels are the main window's").isEmpty();
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> { });
            main.connectContributions(model);
            main.connectTerminals(new TerminalRegistry(), () -> { });
            assertThat(asked[0]).isEqualTo(1);
            assertThat(main.dock().slot("test.panel")).contains(Slot.LEFT_TOP);
        });
        launch();
    }

    @Test void theMainWorkspaceCanBeEmptyAndOffersANewTerminalTabAndOpenFile() throws Exception {
        WindowContent[] main = new WindowContent[1];
        int[] emptied = {0};
        edt(() -> main[0] = content(idleLauncher(pending), WindowRole.MAIN, HOME, false, () -> emptied[0]++));
        launch();
        edt(() -> {
            main[0].closeTab(main[0].selectedTab());
            assertThat(main[0].tabCount()).isZero();
            assertThat(main[0].closed()).as("the empty workspace stays").isFalse();
            assertThat(emptied[0]).as("its owner hears it").isEqualTo(1);
            EmptyWorkspace empty = main[0].workspace().emptyWorkspace();
            assertThat(empty).as("New Terminal Tab and Open File show").isNotNull();
            assertThat(SwingUtilities.isDescendingFrom(empty, main[0].workspace())).isTrue();
            assertThat(main[0].workspace().groupViews()).allSatisfy(group ->
                assertThat(SwingUtilities.isDescendingFrom(group, main[0].workspace())).as("no empty strip").isFalse());
            assertThat(empty.button(ActionId.NEW_TAB).getText()).isEqualTo("New Terminal Tab");
            assertThat(empty.shortcut(ActionId.NEW_TAB).getText()).isEqualTo(main[0].shortcutText(ActionId.NEW_TAB)).isNotEmpty();
            assertThat(empty.button(ActionId.OPEN_FILE).getText()).isEqualTo("Open File…");
            assertThat(empty.shortcut(ActionId.OPEN_FILE).isVisible()).as("Open File has no default shortcut").isFalse();
            empty.button(ActionId.NEW_TAB).doClick();
            assertThat(main[0].tabCount()).isEqualTo(1);
            assertThat(main[0].workspace().emptyWorkspace()).as("a tab takes its place").isNull();
        });
        launch();
    }

    /** A theme change while a tab hides the empty state reaches it before it shows again. */
    @Test void theEmptyWorkspaceTakesTheThemeThatChangedWhileItWasHidden() throws Exception {
        WindowContent[] main = new WindowContent[1];
        Color[] dark = new Color[1];
        edt(() -> {
            main[0] = content(idleLauncher(pending), WindowRole.MAIN, HOME, true, () -> { });
            dark[0] = main[0].workspace().emptyWorkspace().shortcut(ActionId.NEW_TAB).getForeground();
            main[0].invoke(ActionId.NEW_TAB);
            assertThat(main[0].workspace().emptyWorkspace()).as("a tab takes its place").isNull();
        });
        launch();
        edt(() -> {
            try {
                main[0].selectVariant(false);
                main[0].closeTab(main[0].selectedTab());
                EmptyWorkspace empty = main[0].workspace().emptyWorkspace();
                assertThat(empty).isNotNull();
                JLabel caption = empty.shortcut(ActionId.NEW_TAB);
                assertThat(caption.getForeground()).as("the light variant's placeholder text")
                    .isEqualTo(ThemeZone.color(caption, SemanticColor.TEXT_PLACEHOLDER)).isNotEqualTo(dark[0]);
            } finally { main[0].selectVariant(true); }
        });
    }

    /**
     * The empty state paints over the workspace surface: it is a workspace zone, under a workspace theme unlike the
     * chrome.
     */
    @Test void theEmptyWorkspaceIsAWorkspaceZone() throws Exception {
        edt(() -> {
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, true, () -> { });
            EmptyWorkspace empty = main.workspace().emptyWorkspace();
            assertThat(empty.getClientProperty("Jasper.zone")).as("made as one").isEqualTo("workspace");
            var chrome = ThemeTestSupport.generated(true);
            var workspace = ThemeTestSupport.recolored(ThemeTestSupport.generated(false),
                Map.of(SemanticColor.TEXT_PRIMARY, Color.MAGENTA));
            main.applyTheme(ThemeTestSupport.mixed(chrome, workspace), false);
            assertThat(ThemeZone.theme(empty.button(ActionId.NEW_TAB))).isSameAs(workspace);
        });
    }

    /** Workspace tabs spec 10: each caption shows its command's shortcut as the keymap binds it now. */
    @Test void theEmptyWorkspacesCaptionsFollowTheKeymap() throws Exception {
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        edt(() -> {
            var main = content(idleLauncher(pending), WindowRole.MAIN, HOME, true, () -> { });
            JLabel caption = main.workspace().emptyWorkspace().shortcut(ActionId.NEW_TAB);
            String before = caption.getText();
            main.setBindings(KeyBindings.withOverrides(macOs, Map.of("new_tab", "alt+shift+y")));
            KeyStroke rebound = KeyBindings.parse("alt+shift+y", macOs).orElseThrow();
            assertThat(caption.getText()).as("the new shortcut").isEqualTo(CommandsScope.shortcutText(rebound, macOs))
                .isNotEqualTo(before);
            assertThat(caption.isVisible()).isTrue();
            main.setBindings(KeyBindings.withOverrides(macOs, Map.of("new_tab", "none")));
            assertThat(caption.getText()).isEmpty();
            assertThat(caption.isVisible()).as("an unbound command shows no caption").isFalse();
        });
    }

    @Test void aChildWindowIsNeverEmpty() throws Exception {
        WindowContent[] child = new WindowContent[1];
        int[] emptied = {0};
        edt(() -> child[0] = content(idleLauncher(pending), WindowRole.CHILD, HOME, false, () -> emptied[0]++));
        launch();
        edt(() -> {
            child[0].closeTab(child[0].selectedTab());
            assertThat(emptied[0]).as("its last tab closed: its window closes it").isEqualTo(1);
            assertThat(child[0].workspace().emptyWorkspace()).as("no empty state in a child window").isNull();
        });
    }
}
