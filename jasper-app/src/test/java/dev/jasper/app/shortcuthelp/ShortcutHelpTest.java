package dev.jasper.app.shortcuthelp;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class ShortcutHelpTest {
    @Test void catalogIncludesEveryBuiltInAndUsesEffectivePluginBindings() throws Exception {
        edt(() -> {
            var contributions = new Contributions();
            contributions.addAction("dev.test.remote.connect", "Connect", null, List.of("ssh"), Optional.of("cmd+t"), ignored -> {});
            contributions.addAction("dev.test.remote.lost", "Lost binding", null, List.of(), Optional.of("cmd+t"), ignored -> {});
            var base = KeyBindings.withOverrides(true, Map.of("new_tab", "cmd+y", "copy", "none",
                "dev.test.remote.connect", "ctrl+alt+h"));
            var rows = ShortcutCatalog.rows(base, contributions.actions(), Map.of("dev.test.remote", "Remote"), true);
            assertThat(rows.stream().map(ShortcutEntry::id)).containsAll(List.of(ActionId.values()).stream().map(ActionId::id).toList());
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
            var panel = new ShortcutPanel();
            var root = new JRootPane(); root.setContentPane(panel);
            var stroke = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
            panel.setRows(List.of(new ShortcutEntry("remote.hosts", "Remote", "SSH Hosts", stroke, "Terminal window", "connect"),
                new ShortcutEntry("copy", "Clipboard", "Copy", KeyStroke.getKeyStroke("meta C"), "Terminal window", "")));
            panel.search.setText("remote"); assertThat(panel.table.getRowCount()).isEqualTo(1);
            panel.search.setText("shift+command+h"); assertThat(panel.table.getRowCount()).isEqualTo(1);
            panel.search.setText("cmd+h"); assertThat(panel.table.getRowCount()).isZero();
            panel.search.setText("missing"); assertThat(panel.table.getRowCount()).isZero();
            panel.record.doClick();
            var foreign = key(new JButton(), KeyEvent.KEY_PRESSED, KeyEvent.VK_H, InputEvent.META_DOWN_MASK);
            assertThat(panel.dispatch(foreign)).isFalse();
            var press = key(panel.search, KeyEvent.KEY_PRESSED, KeyEvent.VK_H, stroke.getModifiers());
            assertThat(panel.dispatch(press)).isTrue(); assertThat(press.isConsumed()).isTrue();
            assertThat(panel.table.getRowCount()).isEqualTo(1);
            assertThat(panel.table.getValueAt(0, 0)).isEqualTo("SSH Hosts");
            assertThat(panel.dispatch(new KeyEvent(panel.search, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'h'))).isTrue();
            panel.record.doClick();
            assertThat(panel.dispatch(key(panel.search, KeyEvent.KEY_RELEASED, KeyEvent.VK_H, 0))).isTrue();
            assertThat(panel.dispatch(key(panel.search, KeyEvent.KEY_PRESSED, KeyEvent.VK_C, 0))).isFalse();
            panel.clear.doClick(); assertThat(panel.table.getRowCount()).isEqualTo(2);
            panel.close();
        });
    }

    @Test void captureMatchesEscapeAndTabAndNativeMenuShortcutsWithoutExecutingThem() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel();
            var root = new JRootPane(); root.setContentPane(panel);
            panel.record.doClick();
            for (int code : new int[]{KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB, KeyEvent.VK_W, KeyEvent.VK_Q}) {
                var event = key(panel.search, KeyEvent.KEY_PRESSED, code, 0);
                assertThat(panel.dispatch(event)).isTrue();
                assertThat(panel.search.getText()).isEqualTo(ShortcutText.format(KeyStroke.getKeyStroke(code, 0)));
                panel.dispatch(key(panel.search, KeyEvent.KEY_RELEASED, code, 0));
            }
            assertThat(panel.capture(KeyStroke.getKeyStroke("meta W"))).isTrue();
            assertThat(panel.search.getText()).isEqualTo("Cmd+W");
            panel.record.doClick();
            assertThat(panel.capture(KeyStroke.getKeyStroke("meta W"))).isFalse();
            panel.close();
        });
    }

    @Test void windowIsReusedAndRefreshesLiveWithoutLosingSearchAndReleasesItsListener() throws Exception {
        edt(() -> {
            var c = new Contributions();
            var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
                () -> {}, () -> {}, () -> {}, title -> {}, () -> new Rectangle(0, 0, 850, 600), (title, initial) -> Optional.empty()));
            KeyBindings[] base = {KeyBindings.defaults(true)};
            var help = new ShortcutHelp(windows, c, () -> base[0], () -> Map.of("dev.test", "Test Plugin"), true);
            help.open(); var surface = windows.open().getFirst();
            var panel = (ShortcutPanel) surface.holder().getComponent(0);
            panel.search.setText("hello"); assertThat(panel.table.getRowCount()).isZero();
            var action = c.addAction("dev.test.hello", "Hello", null, List.of(), Optional.of("cmd+h"), ignored -> {});
            assertThat(panel.table.getRowCount()).isEqualTo(1);
            base[0] = KeyBindings.withOverrides(true, Map.of("dev.test.hello", "ctrl+h")); help.refresh();
            assertThat(panel.table.getValueAt(0, 1)).isEqualTo("Ctrl+H");
            help.open(); assertThat(windows.open()).containsExactly(surface);
            assertThat(panel.search.getText()).isEqualTo("hello");
            action.close(); assertThat(panel.table.getRowCount()).isZero();
            surface.close();
            c.addAction("dev.test.hello", "Hello", null, List.of(), Optional.of("cmd+h"), ignored -> {});
            assertThat(panel.table.getRowCount()).isZero();
            help.open(); assertThat(windows.open()).hasSize(1).doesNotContain(surface);
            windows.close();
        });
    }

    @Test void mountedRecorderHandlesNativeAcceleratorsAndDetachesOnDisposal() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel();
            var root = new JRootPane(); root.setContentPane(panel);
            panel.addNotify();
            try {
                panel.record.doClick();
                assertThat(dev.jasper.app.platform.WindowInput.captureShortcut(root, KeyStroke.getKeyStroke("meta W"))).isTrue();
                assertThat(panel.search.getText()).isEqualTo("Cmd+W");
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
            var themes = new dev.jasper.app.appearance.ThemeController();
            var panel = new ShortcutPanel();
            try {
                panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
                int normal = panel.table.getRowHeight();
                themes.configure(dev.jasper.app.config.Appearance.DARK, new dev.jasper.app.config.UiFontConfig("system", 18));
                javax.swing.SwingUtilities.updateComponentTreeUI(panel);
                panel.setSize(1000, 640);
                dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(panel);
                assertThat(panel.table.getRowHeight()).isGreaterThan(normal);
                assertThat(panel.search.getWidth()).isGreaterThan(200);
                assertThat(panel.record.getWidth()).isGreaterThanOrEqualTo(panel.record.getPreferredSize().width);
                assertThat(panel.search.getHeight()).isGreaterThanOrEqualTo(panel.search.getPreferredSize().height);
            } finally {
                panel.close();
                themes.configure(dev.jasper.app.config.Appearance.DARK, dev.jasper.app.config.UiFontConfig.defaults());
            }
        });
    }

    @Test void losingWindowFocusReleasesKeysEvenIfTheirReleaseHappenedOutsideJasper() throws Exception {
        edt(() -> {
            var panel = new ShortcutPanel();
            panel.record.doClick();
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_H, InputEvent.META_DOWN_MASK))).isTrue();
            panel.focusLeftWindow();
            var foreign = key(new JButton(), KeyEvent.KEY_PRESSED, KeyEvent.VK_H, 0);
            assertThat(panel.dispatch(foreign)).isFalse();
            assertThat(foreign.isConsumed()).isFalse();
            assertThat(panel.search.isEditable()).isTrue();
            assertThat(panel.dispatch(key(panel, KeyEvent.KEY_PRESSED, KeyEvent.VK_H, 0))).isFalse();
            panel.close();
        });
    }

    private static KeyEvent key(java.awt.Component source, int type, int code, int modifiers) {
        return new KeyEvent(source, type, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }
}
