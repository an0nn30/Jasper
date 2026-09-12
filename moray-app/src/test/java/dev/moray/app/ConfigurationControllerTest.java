package dev.moray.app;

import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class ConfigurationControllerTest {
    @TempDir Path directory;
    ConfigService service;
    ConfigurationController controller;
    ThemeController themes;
    final Queue<Runnable> pending = new ArrayDeque<>();
    final List<WindowContent> owners = new ArrayList<>();

    @AfterEach void cleanup() throws Exception {
        edt(() -> { owners.forEach(WindowContent::close); if (controller != null) controller.close(); });
        if (service != null) service.close();
    }

    void start(String text) throws Exception {
        Files.writeString(directory.resolve("config.toml"), text);
        service = new ConfigService(directory.resolve("config.toml"), false);
        edt(() -> { themes = new ThemeController(); controller = new ConfigurationController(themes, service); });
    }
    WindowContent owner() {
        var result = new WindowContent(launcher(pending), HOME, path -> {}, () -> {}, () -> {}, themes);
        owners.add(result); controller.register(result); return result;
    }
    void reload(String text) throws Exception {
        Files.writeString(directory.resolve("config.toml"), text);
        service.reload().get(); edt(() -> {});
    }
    void launchAll() throws Exception { while (!pending.isEmpty()) pending.remove().run(); edt(() -> {}); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void savedFieldsApplyAcrossOwnersAndPendingHiddenViewsWithoutReplacingSessions() throws Exception {
        start("[window]\ntab_height=44\ntoolbar='icons'\nstatus_bar=false\n[font]\nsize=19\n[colors]\ntheme='moray-light'\n");
        edt(() -> { owner(); owner(); }); launchAll();
        var first = owners.getFirst(); var second = owners.get(1);
        var retained = first.currentPane(); var session = retained.session();
        edt(() -> {
            assertThat(first.tabHeight()).isEqualTo(44);
            assertThat(((JButton) first.toolbar().getComponent(0)).getText()).isNull();
            assertThat(first.status().isVisible()).isFalse();
            assertThat(first.theme()).isEqualTo(BuiltinTheme.LIGHT);
            assertThat(retained.view().fontSize()).isEqualTo(19);
            retained.findBar().open(); retained.findBar().queryField().setText("alpha");
            first.newTab(HOME); // pending and hides the retained terminal
        });
        reload("[window]\ntab_height=50\ntoolbar='hidden'\n[font]\nsize=21\n");
        launchAll();
        edt(() -> {
            assertThat(first.tabHeight()).isEqualTo(50); assertThat(second.tabHeight()).isEqualTo(50);
            assertThat(first.toolbar().isVisible()).isFalse(); assertThat(first.status().isVisible()).isTrue();
            assertThat(retained.session()).isSameAs(session);
            assertThat(retained.findBar().queryField().getText()).isEqualTo("alpha");
            assertThat(retained.findBar().isVisible()).isTrue();
            assertThat(retained.view().fontSize()).isEqualTo(21);
            assertThat(first.currentPane().view().fontSize()).isEqualTo(21);
            assertThat(second.currentPane().view().fontSize()).isEqualTo(21);
            first.currentPane().view().setFontSize(30); first.invoke(ActionId.FONT_RESET);
            assertThat(first.currentPane().view().fontSize()).isEqualTo(21);
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void unrelatedChangesPreserveTemporaryChoicesAndNewOwnersUseSavedDefaults() throws Exception {
        start("[font]\nsize=19\n"); edt(this::owner); launchAll();
        var first = owners.getFirst();
        edt(() -> {
            first.setTabHeight(60); first.setToolbarMode(WindowContent.ToolbarMode.HIDDEN);
            first.setStatusVisible(false); first.selectTheme(BuiltinTheme.LIGHT); first.currentPane().view().setFontSize(28);
        });
        reload("# comment\n[font]\nsize=19\nunknown=1\n");
        edt(() -> {
            assertThat(first.tabHeight()).isEqualTo(60); assertThat(first.toolbar().isVisible()).isFalse();
            assertThat(first.status().isVisible()).isFalse(); assertThat(first.theme()).isEqualTo(BuiltinTheme.LIGHT);
            assertThat(first.currentPane().view().fontSize()).isEqualTo(28);
            var next = owner(); assertThat(next.tabHeight()).isEqualTo(38);
            assertThat(next.toolbar().isVisible()).isTrue(); assertThat(next.status().isVisible()).isTrue();
            assertThat(first.theme()).isEqualTo(BuiltinTheme.LIGHT);
        }); launchAll();
        edt(() -> assertThat(owners.get(1).currentPane().view().fontSize()).isEqualTo(19));
        reload("[window]\ntab_height=45\ntoolbar='icons'\nstatus_bar=false\n[font]\nsize=20\n[colors]\ntheme='moray-light'\n");
        edt(() -> {
            assertThat(first.tabHeight()).isEqualTo(45); assertThat(first.toolbar().isVisible()).isTrue();
            assertThat(first.currentPane().view().fontSize()).isEqualTo(20);
        });
    }

    @Test void reloadReplacesActualRootStrokesClearsAcceleratorsAndUpdatesActionHints() throws Exception {
        start(""); JRootPane root = new JRootPane();
        edt(() -> { var owner = owner(); root.setContentPane(owner); owner.installRootBindings(root); });
        var owner = owners.getFirst(); var old = owner.bindings().strokeFor(ActionId.NEW_TAB).orElseThrow();
        reload("[keybindings]\nnew_tab='Ctrl+F12'\n");
        edt(() -> {
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(old)).isNull();
            var stroke = KeyStroke.getKeyStroke("ctrl F12");
            Object key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke);
            assertThat(key).isEqualTo("new_tab");
            root.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(root, 0, "test"));
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            assertThat(((JButton) owner.toolbar().getComponent(0)).getAction().getValue(Action.ACCELERATOR_KEY)).isEqualTo(stroke);
            assertThat(owner.dispatchShortcut(old, owner)).isFalse();
            var field = new JTextField();
            assertThat(owner.dispatchShortcut(owner.bindings().strokeFor(ActionId.COPY).orElseThrow(), field)).isFalse();
            assertThat(owner.dispatchShortcut(owner.bindings().strokeFor(ActionId.PASTE).orElseThrow(), field)).isFalse();
        });
        reload("[keybindings]\nnew_tab='none'\n");
        edt(() -> {
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("ctrl F12"))).isNull();
            assertThat(root.getActionMap().get("new_tab")).isNull();
            assertThat(owner.action(ActionId.NEW_TAB).getValue(Action.ACCELERATOR_KEY)).isNull();
        });
    }
    @Test void malformedReloadUpdatesAccessiblePlainTextStatusAndRetainsWorkingSettings() throws Exception {
        start("[window]\ntab_height=44\n"); edt(this::owner);
        var owner = owners.getFirst();
        reload("[window]\ntab_height = [\n");
        edt(() -> {
            assertThat(owner.tabHeight()).isEqualTo(44);
            JButton button = owner.status().configButton();
            assertThat(button.getText()).contains("Config error", "line 3");
            assertThat(button.getToolTipText()).isEqualTo(directory.resolve("config.toml").toString());
            assertThat(button.getAccessibleContext().getAccessibleName()).contains("Config error");
            assertThat(button.getClientProperty("html.disable")).isEqualTo(true);
            var shown = new ArrayList<JComponent>(); owner.showConfigDiagnostics = shown::add;
            button.doClick();
            assertThat(shown).hasSize(1);
            JTextArea text = (JTextArea) ((JScrollPane) shown.getFirst()).getViewport().getView();
            assertThat(text.isEditable()).isFalse();
            assertThat(text.getText()).contains(directory.resolve("config.toml").toString(), ":3:");
            text.selectAll(); assertThat(text.getSelectedText()).isEqualTo(text.getText());
            owner.status().setMetadata("<html>shell", "<html>directory", "80 × 24", true);
            assertThat(owner.status().getText()).contains("<html>shell", "80 × 24", "Config error");
        });
        reload("unknown=true\n[window]\ntab_height=46\n");
        edt(() -> {
            assertThat(owner.tabHeight()).isEqualTo(46);
            assertThat(owner.status().configButton().getText()).contains("Config warnings");
        });
    }

    @Test void settingsAndReloadActionsAreConnectedAndClosingUnregistersAndBlocksLateDelivery() throws Exception {
        Path file = directory.resolve("new/config.toml");
        service = new ConfigService(file, false);
        var opened = new java.util.concurrent.LinkedBlockingQueue<Path>();
        edt(() -> {
            themes = new ThemeController();
            controller = new ConfigurationController(themes, service, path -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isFalse(); opened.add(path);
            });
            owner();
        });
        var owner = owners.getFirst();
        edt(() -> {
            assertThat(owner.action(ActionId.OPEN_SETTINGS).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.RELOAD_CONFIG).isEnabled()).isTrue();
            owner.invoke(ActionId.OPEN_SETTINGS);
        });
        assertThat(opened.poll(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(file);
        until(() -> owner.status().getText().contains("Config loaded"));
        Files.writeString(file, "[window]\ntab_height=48\n");
        edt(() -> owner.invoke(ActionId.RELOAD_CONFIG));
        until(() -> owner.tabHeight() == 48);
        edt(() -> {
            owner.close();
            assertThat(owner.action(ActionId.OPEN_SETTINGS).isEnabled()).isFalse();
            var late = new ConfigService.State(new ConfigSnapshot(55, WindowContent.ToolbarMode.ICONS, false,
                22, BuiltinTheme.DARK, Map.of()), List.of(), file, true);
            controller.accept(late);
            assertThat(owner.tabHeight()).isEqualTo(48);
            assertThat(owner.status().configButton().isEnabled()).isFalse();
            controller.close(); controller.accept(late);
        });
        assertThat(service.reload()).isCompletedExceptionally();
    }

    @Test void settingsFailureIsVisibleOnEdtAndDoesNotRollBackCreatedConfiguration() throws Exception {
        service = new ConfigService(directory.resolve("config.toml"), false);
        var errors = new java.util.concurrent.LinkedBlockingQueue<String>();
        edt(() -> {
            themes = new ThemeController();
            controller = new ConfigurationController(themes, service, path -> { throw new IllegalStateException("Editor unavailable"); });
            var owner = owner();
            owner.onError = message -> { assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); errors.add(message); };
            owner.invoke(ActionId.OPEN_SETTINGS);
        });
        assertThat(errors.poll(5, java.util.concurrent.TimeUnit.SECONDS)).contains("Editor unavailable");
        assertThat(Files.exists(directory.resolve("config.toml"))).isTrue();
        until(() -> owners.getFirst().status().getText().contains("Config loaded"));
    }

    @Test void themeInstallFailureReportsToOwnersWhileOtherSettingsAndDiagnosticsStillApply() throws Exception {
        service = new ConfigService(directory.resolve("config.toml"), false);
        var errors = new ArrayList<String>();
        edt(() -> {
            themes = new ThemeController(theme -> theme != BuiltinTheme.LIGHT && ThemeController.install(theme));
            controller = new ConfigurationController(themes, service);
            owner().onError = errors::add;
            var state = new ConfigService.State(new ConfigSnapshot(53, WindowContent.ToolbarMode.ICONS, true,
                18, BuiltinTheme.LIGHT, Map.of()), List.of(), directory.resolve("config.toml"), true);
            controller.accept(state);
            assertThat(owners.getFirst().tabHeight()).isEqualTo(53);
            assertThat(owners.getFirst().theme()).isEqualTo(BuiltinTheme.DARK);
            assertThat(owners.getFirst().status().getText()).contains("Config loaded");
            assertThat(errors).singleElement().asString().contains("Could not apply theme");
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void existingTerminalHandlerUsesReloadedBindingsAndPreservesSelectionAndZoomedHiddenPanes() throws Exception {
        start(""); edt(this::owner); launchAll();
        var owner = owners.getFirst(); var tab = owner.currentTab(); var retained = owner.currentPane();
        until(() -> {
            retained.view().dispatchEvent(new java.awt.event.MouseEvent(retained.view(), java.awt.event.MouseEvent.MOUSE_PRESSED,
                0, 0, 2, 2, 2, false, java.awt.event.MouseEvent.BUTTON1));
            return retained.view().selectedText().orElse("").equals("alpha");
        });
        edt(() -> {
            assertThat(retained.view().selectedText()).contains("alpha");
            owner.invoke(ActionId.SPLIT_RIGHT);
        }); launchAll();
        var sibling = owner.currentPane();
        edt(() -> { tab.toggleZoom(); owner.newTab(HOME); });
        reload("[font]\nsize=20\n[keybindings]\nnew_tab='Ctrl+F12'\n"); launchAll();
        edt(() -> {
            assertThat(tab.tree().zoomed()).isTrue();
            assertThat(retained.view().selectedText()).contains("alpha");
            assertThat(retained.view().fontSize()).isEqualTo(20);
            assertThat(sibling.view().fontSize()).isEqualTo(20);
            var key = new java.awt.event.KeyEvent(retained.view(), java.awt.event.KeyEvent.KEY_PRESSED, 0,
                java.awt.event.InputEvent.CTRL_DOWN_MASK, java.awt.event.KeyEvent.VK_F12, java.awt.event.KeyEvent.CHAR_UNDEFINED);
            terminalKey(retained.view(), key);
            assertThat(key.isConsumed()).isTrue();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(3);
            assertThat(retained.view().selectedText()).contains("alpha");
        });
    }

    private static void terminalKey(dev.moray.terminal.TerminalView view, java.awt.event.KeyEvent event) {
        try {
            var method = dev.moray.terminal.TerminalView.class.getDeclaredMethod("handleKey", java.awt.event.KeyEvent.class);
            method.setAccessible(true); method.invoke(view, event);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Test void applicationUsesTheServicesParsePlatformForSavedBindings() throws Exception {
        start("[keybindings]\nsplit_down='alt+cmd+shift+d'\n");
        edt(() -> {
            var owner = owner();
            assertThat(owner.action(ActionId.SPLIT_DOWN).getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke("ctrl alt shift D"));
            assertThat(owner.action(ActionId.NEW_TAB).getValue(Action.ACCELERATOR_KEY))
                .isEqualTo(KeyStroke.getKeyStroke("ctrl shift T"));
        });
    }

}
