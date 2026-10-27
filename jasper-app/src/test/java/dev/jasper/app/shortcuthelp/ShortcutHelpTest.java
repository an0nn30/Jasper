package dev.jasper.app.shortcuthelp;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.shortcuthelp.ShortcutKit.*;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class ShortcutHelpTest {
    /** Tabs and panes spec 10: the tab actions are Tabs, and the pane actions are Panes. */
    @Test void theTabAndPaneActionsHaveTheirCategories() {
        var rows = ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true);
        for (String id : List.of("new_tab", "close_tab", "next_tab", "select_tab_1", "move_tab_to_new_window"))
            assertThat(rows).filteredOn(row -> row.id().equals(id)).singleElement().satisfies(row -> assertThat(row.group()).isEqualTo("Tabs"));
        for (String id : List.of("close_pane", "maximize_pane", "focus_pane_left", "move_pane_to_new_tab", "move_pane_to_tab_1",
                "move_pane_to_tab_9", "move_tab_into_tab_1", "move_tab_into_tab_9"))
            assertThat(rows).filteredOn(row -> row.id().equals(id)).singleElement().satisfies(row -> assertThat(row.group()).isEqualTo("Panes"));
        // Workspace plan 7b Rulings 13: the window commands, with New Window and Close Window.
        for (String id : List.of("move_tab_to_window", "new_window"))
            assertThat(rows).filteredOn(row -> row.id().equals(id)).singleElement().satisfies(row -> assertThat(row.group()).isEqualTo("Application"));
    }

    @Test void catalogIncludesEveryBuiltInAndUsesEffectivePluginBindings() throws Exception {
        edt(() -> {
            var contributions = new Contributions();
            contributions.addAction("dev.test.remote.connect", "Connect", null, List.of("ssh"), Optional.of("cmd+t"), ignored -> {});
            contributions.addAction("dev.test.remote.lost", "Lost binding", null, List.of(), Optional.of("cmd+t"), ignored -> {});
            var base = KeyBindings.withOverrides(true, Map.of("new_tab", "cmd+y", "copy", "none",
                "dev.test.remote.connect", "ctrl+alt+h"));
            var rows = ShortcutCatalog.rows(base, contributions.actions(), Map.of("dev.test.remote", "Remote"), true);
            assertThat(rows.stream().map(ShortcutEntry::id)).containsAll(List.of(ActionId.values()).stream().filter(id -> id.availableOn(System.getProperty("os.name", ""))).map(ActionId::id).toList());
            assertThat(rows).filteredOn(row -> row.id().equals("new_tab")).singleElement()
                .satisfies(row -> assertThat(ShortcutText.format(row.stroke())).isEqualTo("Cmd+Y"));
            assertThat(rows).filteredOn(row -> row.id().equals("copy")).singleElement()
                .satisfies(row -> assertThat(row.stroke()).isNull());
            assertThat(rows).filteredOn(row -> row.id().equals("dev.test.remote.connect")).singleElement().satisfies(row -> {
                assertThat(row.group()).isEqualTo("Remote");
                assertThat(ShortcutText.format(row.stroke())).isEqualTo("Ctrl+Alt+H");
            });
            // The freed default belongs to the second plugin action, rather than showing a stale collision.
            assertThat(rows).filteredOn(row -> row.id().equals("dev.test.remote.lost")).singleElement()
                .satisfies(row -> assertThat(ShortcutText.format(row.stroke())).isEqualTo("Cmd+T"));
            var collision = ShortcutCatalog.rows(KeyBindings.defaults(true), contributions.actions(), Map.of(), true);
            assertThat(collision).filteredOn(row -> row.id().equals("dev.test.remote.lost")).singleElement().satisfies(row -> {
                assertThat(row.stroke()).isNull();
                assertThat(row.context()).contains("already used");
            });
        });
    }

    @Test void theApplicationsOwnActionsShareTheApplicationGroup() throws Exception {
        edt(() -> {
            var contributions = new Contributions();
            for (String id : List.of("plugins.manage", "app.shortcuts", "app.about"))
                contributions.addAction(id, id, null, List.of(), Optional.empty(), ignored -> {});
            var rows = ShortcutCatalog.rows(KeyBindings.defaults(true), contributions.actions(), Map.of(), true);
            assertThat(rows).filteredOn(row -> List.of("plugins.manage", "app.shortcuts", "app.about").contains(row.id()))
                .extracting(ShortcutEntry::group).containsOnly("Application").hasSize(3);
        });
    }

    @Test void catalogDropsResultShortcutsAndDescribesTabAsSwitchingTabs() throws Exception {
        edt(() -> {
            var rows = ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true);
            assertThat(rows).noneMatch(row -> row.id().startsWith("palette.result"));
            assertThat(rows).filteredOn(row -> row.id().equals("palette.tab")).singleElement()
                .satisfies(row -> assertThat(row.name()).isEqualTo("Next tab or next field"));
        });
    }

    @Test void contextualKeysAreLabeledAndBundledPluginKeysDisappearWithTheirActions() throws Exception {
        edt(() -> {
            var c = new Contributions();
            var action = c.addAction("dev.jasper.remote.open", "Hosts", null, List.of(), Optional.empty(), ignored -> {});
            var rows = ShortcutCatalog.rows(KeyBindings.defaults(false), c.actions(), Map.of("dev.jasper.remote", "Remote"), false);
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.name()).contains("Activate host");
                assertThat(row.context()).isEqualTo("SSH Hosts list");
                assertThat(row.stroke()).isEqualTo(KeyStroke.getKeyStroke("ENTER"));
            });
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.id()).isEqualTo("palette.secondary");
                assertThat(ShortcutText.format(row.stroke())).isEqualTo("Ctrl+Enter");
            });
            action.close();
            assertThat(ShortcutCatalog.rows(KeyBindings.defaults(false), c.actions(), Map.of("dev.jasper.remote", "Remote"), false))
                .noneMatch(row -> row.context().equals("SSH Hosts list"));
        });
    }

    @Test void searchFindsNamesPluginsAndChordsAndCaptureConsumesWholeSequenceOnlyInItsWindow() throws Exception {
        edt(() -> {
            var panel = panel(true, changes -> { throw new AssertionError("Unexpected save"); });
            var root = new JRootPane(); root.setContentPane(panel);
            var stroke = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
            panel.setRows(List.of(new ShortcutEntry("remote.hosts", "Remote", "SSH Hosts", stroke, "Jasper window", "connect"),
                new ShortcutEntry("copy", "Clipboard", "Copy", KeyStroke.getKeyStroke("meta C"), "Jasper window", "")));
            search(panel).setText("remote"); assertThat(panel.tree.rowCount()).isEqualTo(2);
            search(panel).setText("shift+command+h"); assertThat(panel.tree.rowCount()).isEqualTo(2);
            search(panel).setText("cmd+h"); assertThat(panel.tree.rowCount()).isZero();
            search(panel).setText("missing"); assertThat(panel.tree.rowCount()).isZero();
            button(panel, "record").doClick(0);
            var foreign = key(new JButton(), KeyEvent.KEY_PRESSED, KeyEvent.VK_H, InputEvent.META_DOWN_MASK);
            assertThat(panel.dispatch(foreign)).isFalse();
            var press = key(search(panel), KeyEvent.KEY_PRESSED, KeyEvent.VK_H, stroke.getModifiers());
            assertThat(panel.dispatch(press)).isTrue(); assertThat(press.isConsumed()).isTrue();
            assertThat(panel.tree.rowCount()).isEqualTo(2);
            assertThat(panel.tree.entryAt(1).name()).isEqualTo("SSH Hosts");
            assertThat(panel.dispatch(new KeyEvent(search(panel), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'h'))).isTrue();
            button(panel, "record").doClick(0);
            assertThat(panel.dispatch(key(search(panel), KeyEvent.KEY_RELEASED, KeyEvent.VK_H, 0))).isTrue();
            assertThat(panel.dispatch(key(search(panel), KeyEvent.KEY_PRESSED, KeyEvent.VK_C, 0))).isFalse();
            button(panel, "clear").doClick(0); assertThat(panel.tree.rowCount()).isEqualTo(2);
            panel.close();
        });
    }

    @Test void captureMatchesEscapeAndTabAndNativeMenuShortcutsWithoutExecutingThem() throws Exception {
        edt(() -> {
            var panel = panel(true, changes -> { throw new AssertionError("Unexpected save"); });
            var root = new JRootPane(); root.setContentPane(panel);
            button(panel, "record").doClick(0);
            for (int code : new int[]{KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB, KeyEvent.VK_SPACE, KeyEvent.VK_W, KeyEvent.VK_Q}) {
                var event = key(search(panel), KeyEvent.KEY_PRESSED, code, 0);
                assertThat(panel.dispatch(event)).isTrue();
                assertThat(search(panel).getText()).isEqualTo(ShortcutText.format(KeyStroke.getKeyStroke(code, 0)));
                panel.dispatch(key(search(panel), KeyEvent.KEY_RELEASED, code, 0));
            }
            assertThat(panel.capture(KeyStroke.getKeyStroke("meta W"))).isTrue();
            assertThat(search(panel).getText()).isEqualTo("Cmd+W");
            button(panel, "record").doClick(0);
            assertThat(panel.capture(KeyStroke.getKeyStroke("meta W"))).isFalse();
            panel.close();
        });
    }

    @Test void mountedRecorderHandlesNativeAcceleratorsAndDetachesOnDisposal() throws Exception {
        edt(() -> {
            var panel = panel(true, changes -> { throw new AssertionError("Unexpected save"); });
            var root = new JRootPane(); root.setContentPane(panel);
            panel.addNotify();
            try {
                button(panel, "record").doClick(0);
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta W"))).isTrue();
                assertThat(search(panel).getText()).isEqualTo("Cmd+W");
                var foreignRoot = new JRootPane();
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(foreignRoot, KeyStroke.getKeyStroke("meta Q"))).isFalse();
                panel.close();
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta W"))).isFalse();
                assertThat(panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_Q, InputEvent.META_DOWN_MASK))).isFalse();
            } finally { panel.removeNotify(); panel.close(); }
        });
    }

    @Test void themedRowsGrowWithTypographyAndSearchControlsRemainUsable() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var panel = panel(true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
                panel.setSize(1000, 640);
                dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(panel);
                int normal = rowHeight(panel);
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system", 18));
                javax.swing.SwingUtilities.updateComponentTreeUI(panel);
                dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(panel);
                assertThat(rowHeight(panel)).isGreaterThan(normal);
                assertThat(search(panel).getWidth()).isGreaterThan(200);
                var record = button(panel, "record");
                assertThat(record.getWidth()).isGreaterThanOrEqualTo(record.getPreferredSize().width);
                assertThat(search(panel).getHeight()).as("the search text is never squeezed").isGreaterThanOrEqualTo(search(panel).getPreferredSize().height);
            } finally {
                panel.close();
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());
            }
        });
    }

    /** The height of the first section's mounted row. */
    private static int rowHeight(ShortcutPanel panel) {
        return title(panel, 0).getParent().getParent().getHeight();
    }

    @Test void losingWindowFocusReleasesKeysEvenIfTheirReleaseHappenedOutsideJasper() throws Exception {
        edt(() -> {
            var panel = panel(true, changes -> { throw new AssertionError("Unexpected save"); });
            button(panel, "record").doClick(0);
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_H, InputEvent.META_DOWN_MASK))).isTrue();
            panel.focusLeftWindow();
            var foreign = key(new JButton(), KeyEvent.KEY_PRESSED, KeyEvent.VK_H, 0);
            assertThat(panel.dispatch(foreign)).isFalse();
            assertThat(foreign.isConsumed()).isFalse();
            assertThat(search(panel).isEditable()).isTrue();
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_H, 0))).isFalse();
            panel.close();
        });
    }

    private static KeyEvent key(java.awt.Component source, int type, int code, int modifiers) {
        return new KeyEvent(source, type, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }

    @Test void catalogNamesWhereEachShortcutApplies() throws Exception {
        edt(() -> {
            var c = new Contributions();
            c.addAction("dev.x.save", "Save", null, List.of(), Optional.of("cmd+s"), Optional.empty(), KeyContext.content("dev.x"), ignored -> {});
            c.addAction("dev.x.hosts.new", "New Host", null, List.of(), Optional.of("cmd+n"), Optional.of("ctrl+n"),
                KeyContext.panel("dev.x.hosts"), ignored -> {});
            var rows = ShortcutCatalog.rows(KeyBindings.defaults(false), c.actions(), Map.of("dev.x", "Tool"),
                Map.of("dev.x.hosts", "Hosts"), false);
            assertThat(rows).filteredOn(row -> row.id().equals("new_tab")).singleElement().satisfies(row -> {
                assertThat(row.where()).isEqualTo("Global");
                assertThat(ShortcutText.matches(row, "global", null)).isTrue();
            });
            assertThat(rows).filteredOn(row -> row.id().equals("clear_scrollback")).singleElement().satisfies(row -> {
                assertThat(row.where()).isEqualTo("Terminal");
                assertThat(row.context()).isEqualTo("In a terminal pane");
            });
            assertThat(rows).filteredOn(row -> row.id().equals("clear_terminal")).singleElement().satisfies(row -> {
                assertThat(row.group()).isEqualTo("Terminal");
                assertThat(row.stroke()).isNull();
            });
            assertThat(rows).filteredOn(row -> row.id().equals("dev.x.save")).singleElement()
                .satisfies(row -> assertThat(row.where()).isEqualTo("Tool tabs"));
            assertThat(rows).filteredOn(row -> row.id().equals("dev.x.hosts.new")).singleElement().satisfies(row -> {
                assertThat(row.where()).isEqualTo("Hosts panel");
                assertThat(row.defaultBinding()).isEqualTo("ctrl+n");
                assertThat(ShortcutText.format(row.stroke())).isEqualTo("Ctrl+N");
                assertThat(row.customized(false)).isFalse();
            });
            assertThat(rows).filteredOn(row -> row.id().equals("palette.primary")).singleElement()
                .satisfies(row -> assertThat(row.where()).isEqualTo("Command palette"));
        });
    }
}
