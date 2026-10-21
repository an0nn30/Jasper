package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.SplitItem;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tabs and panes spec 2.10 and 8: the Split lists of the Tab menu and a terminal's context menu, and what they run. */
class SplitListsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final List<String> opened = new ArrayList<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private WindowContent window() throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            owner[0] = content(idleLauncher(pending), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner[0].connectContributions(model);
        });
        while (!pending.isEmpty()) pending.remove().run();
        return owner[0];
    }

    /**
     * A plugin's split item as {@code HostedUi} registers one: its two actions, with the item's icon, which open a plugin
     * view in a new pane beside the focused one, then the item.
     */
    private Subscription item(String plugin, String name, String label, boolean bundled) {
        String right = plugin + ".split_right_" + name, down = plugin + ".split_down_" + name;
        var rightAction = model.addAction(right, "Split Right with " + label, new ImageIcon(), List.of(), Optional.empty(), invocation -> {
            opened.add(right);
            model.tabs().open(plugin, right + opened.size(), label, invocation.windowId(), new JPanel(), TabRegistry.Placement.SPLIT_RIGHT);
        });
        var downAction = model.addAction(down, "Split Down with " + label, new ImageIcon(), List.of(), Optional.empty(), invocation -> {
            opened.add(down);
            model.tabs().open(plugin, down + opened.size(), label, invocation.windowId(), new JPanel(), TabRegistry.Placement.SPLIT_DOWN);
        });
        var listed = model.addSplitItem(new SplitItem(plugin + "." + name, label, null, right, down, bundled));
        return new Subscription(() -> { listed.close(); rightAction.close(); downAction.close(); });
    }

    /** The Tab menu's Split submenu, from the menu bar. */
    private static JMenu tabSplit(WindowContent window) {
        var bar = window.menuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu == null || !"Tab".equals(menu.getText())) continue;
            for (Component child : menu.getMenuComponents())
                if (child instanceof JMenu split && "Split".equals(split.getText())) return split;
        }
        throw new AssertionError("no Split in the Tab menu");
    }

    /** A terminal's context menu's Split submenu. */
    private static JMenu contextSplit(WindowContent window) {
        for (Component child : window.chrome().contextMenu().getComponents())
            if (child instanceof JMenu split && "Split".equals(split.getText())) return split;
        throw new AssertionError("no Split in the context menu");
    }

    private static List<String> texts(JMenu menu) {
        var texts = new ArrayList<String>();
        for (Component child : menu.getMenuComponents()) if (child instanceof JMenuItem item) texts.add(item.getText());
        return texts;
    }

    private static JMenuItem entry(JMenu menu, String text) {
        for (Component child : menu.getMenuComponents())
            if (child instanceof JMenuItem item && text.equals(item.getText())) return item;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /** Rulings 5, 6 and 22: Terminal Right and Down, then each item's Right and Down, the bundled plugins' first; no icons. */
    @Test void bothListsReadTheTerminalThenEachItemTheBundledFirst() throws Exception {
        var window = window();
        edt(() -> {
            assertThat(texts(tabSplit(window))).containsExactly("Terminal Right", "Terminal Down");
            item("com.example.notes", "notes", "Notes", false);
            item("dev.jasper.editor", "editor", "Editor", true);
            var expected = List.of("Terminal Right", "Terminal Down", "Editor Right", "Editor Down", "Notes Right", "Notes Down");
            assertThat(texts(tabSplit(window))).as("the Tab menu's").containsExactlyElementsOf(expected);
            assertThat(texts(contextSplit(window))).as("a terminal's context menu's").containsExactlyElementsOf(expected);
            var keys = window.bindings();
            assertThat(entry(tabSplit(window), "Terminal Right").getAccelerator()).as("Split Right's shortcut")
                .isEqualTo(keys.strokeFor(ActionId.SPLIT_RIGHT).orElseThrow());
            assertThat(entry(contextSplit(window), "Terminal Down").getAccelerator())
                .isEqualTo(keys.strokeFor(ActionId.SPLIT_DOWN).orElseThrow());
            for (JMenu split : List.of(tabSplit(window), contextSplit(window)))
                for (Component child : split.getMenuComponents())
                    assertThat(((JMenuItem) child).getIcon()).as("%s shows no icon", ((JMenuItem) child).getText()).isNull();
        });
    }

    /** Spec 8 and Rulings 8: each entry runs its own action beside the focused pane, and the palette lists each. */
    @Test void eachEntryRunsItsActionBesideTheFocusedPane() throws Exception {
        var window = window();
        edt(() -> {
            item("dev.jasper.editor", "editor", "Editor", true);
            entry(tabSplit(window), "Terminal Right").doClick();
            assertThat(window.workspace().tabCount()).as("a terminal beside the first").isEqualTo(2);
            entry(contextSplit(window), "Editor Down").doClick();
            assertThat(opened).containsExactly("dev.jasper.editor.split_down_editor");
            assertThat(window.workspace().tabCount()).as("the plugin's view in a new pane of the same tab").isEqualTo(3);
            assertThat(window.parentTabCount()).isEqualTo(1);
            for (String id : List.of("split_right", "split_down", "dev.jasper.editor.split_right_editor", "dev.jasper.editor.split_down_editor"))
                assertThat(window.commands().find(id)).as("%s in the palette", id).isPresent();
        });
    }

    /** Review Focus 2: an item that goes leaves both lists at once, and the palette. */
    @Test void anItemThatGoesLeavesEverySplitListAndThePalette() throws Exception {
        var window = window();
        edt(() -> {
            var notes = item("com.example.notes", "notes", "Notes", false);
            var split = tabSplit(window);
            assertThat(texts(split)).contains("Notes Right", "Notes Down");
            notes.close();
            assertThat(texts(tabSplit(window))).as("the Tab menu's, at once").containsExactly("Terminal Right", "Terminal Down");
            assertThat(tabSplit(window)).as("the same submenu, refilled").isSameAs(split);
            assertThat(texts(contextSplit(window))).containsExactly("Terminal Right", "Terminal Down");
            assertThat(window.commands().find("com.example.notes.split_right_notes")).isEmpty();
        });
    }
}
