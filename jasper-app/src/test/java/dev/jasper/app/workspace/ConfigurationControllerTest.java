package dev.jasper.app.workspace;

import dev.jasper.app.application.ApplicationTestSupport;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.application.ConfigurationTestSupport;
import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.appearance.BuiltinTheme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.FontConfig;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.workspace.TerminalTab;
import dev.jasper.app.workspace.WindowContent;
import dev.jasper.app.config.ToolbarMode;

import dev.jasper.terminal.config.BellMode;
import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.config.OptionAsMeta;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.view.TerminalView;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class ConfigurationControllerTest {
    @TempDir Path directory;
    ConfigService service;
    ConfigurationTestSupport controller;
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
        edt(() -> { themes = new ThemeController(service.initialState().snapshot().style(), service.initialState().snapshot().variant()); controller = new ConfigurationTestSupport(themes, service); });
    }
    WindowContent owner() { return owner(launcher(pending)); }
    WindowContent owner(ShellLauncher launcher) {
        var result = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {}, themes);
        owners.add(result); controller.register(result); return result;
    }
    void reload(String text) throws Exception {
        Files.writeString(directory.resolve("config.toml"), text);
        service.reload().get(); edt(() -> {});
    }
    void launchAll() throws Exception { while (!pending.isEmpty()) pending.remove().run(); edt(() -> {}); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void savedFieldsApplyAcrossOwnersAndPendingHiddenViewsWithoutReplacingSessions() throws Exception {
        start("[window]\ntab_height=44\ntoolbar='icons'\nstatus_bar=false\n[font]\nsize=19\n[ui.theme]\nvariant='light'\n");
        edt(() -> { owner(); owner(); }); launchAll();
        var first = owners.getFirst(); var second = owners.get(1);
        var retained = first.currentPane(); var session = retained.session();
        edt(() -> {
            assertThat(first.tabHeight()).isEqualTo(44);
            assertThat(((JButton) first.toolbar().getComponent(0)).getText()).isNull();
            assertThat(first.status().isVisible()).isFalse();
            assertThat(first.theme().chrome()).isEqualTo(BuiltinTheme.LIGHT);
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
            first.setTabHeight(60); first.setToolbarMode(ToolbarMode.HIDDEN);
            first.setStatusVisible(false); first.selectTheme(BuiltinTheme.LIGHT); first.currentPane().view().setFontSize(28);
        });
        reload("# comment\n[font]\nsize=19\nunknown=1\n");
        edt(() -> {
            assertThat(first.tabHeight()).isEqualTo(60); assertThat(first.toolbar().isVisible()).isFalse();
            assertThat(first.status().isVisible()).isFalse(); assertThat(first.theme().chrome()).isEqualTo(BuiltinTheme.LIGHT);
            assertThat(first.currentPane().view().fontSize()).isEqualTo(28);
            var next = owner(); assertThat(next.tabHeight()).isEqualTo(38);
            assertThat(next.toolbar().isVisible()).isTrue(); assertThat(next.status().isVisible()).isTrue();
            assertThat(first.theme().chrome()).isEqualTo(BuiltinTheme.LIGHT);
        }); launchAll();
        edt(() -> assertThat(owners.get(1).currentPane().view().fontSize()).isEqualTo(19));
        reload("[window]\ntab_height=45\ntoolbar='icons'\nstatus_bar=false\n[font]\nsize=20\n[ui.theme]\nvariant='light'\n");
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
            owner.status().setMetadata("<html>shell", "<html>directory", "80 × 24", true, false);
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
            controller = new ConfigurationTestSupport(themes, service, path -> {
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
            var late = new ConfigService.State(new ConfigSnapshot(55, ToolbarMode.ICONS, false, FontConfig.defaults().withSize(22), BuiltinTheme.DARK.appearance(), Map.of(), 150, 45, TerminalConfig.defaults()), List.of(), file, true);
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
            controller = new ConfigurationTestSupport(themes, service, path -> { throw new IllegalStateException("Editor unavailable"); });
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
            themes = new ThemeController(theme -> theme != BuiltinTheme.LIGHT && ThemeTestSupport.install(theme));
            controller = new ConfigurationTestSupport(themes, service);
            owner().onError = errors::add;
            var state = new ConfigService.State(new ConfigSnapshot(53, ToolbarMode.ICONS, true, FontConfig.defaults().withSize(18), BuiltinTheme.LIGHT.appearance(), Map.of(), 150, 45, TerminalConfig.defaults()), List.of(), directory.resolve("config.toml"), true);
            controller.accept(state);
            assertThat(owners.getFirst().tabHeight()).isEqualTo(53);
            assertThat(owners.getFirst().theme().chrome()).isEqualTo(BuiltinTheme.DARK);
            assertThat(owners.getFirst().status().getText()).contains("Config loaded");
            assertThat(errors).singleElement().asString().contains("Could not apply theme");
        });
    }

    @Test void applicationCallbackFailuresAreNotReportedAsInstallationFailures() throws Exception {
        start("");
        edt(() -> {
            var owner = owner();
            owner.onThemeChanged = ignored -> { throw new IllegalStateException("application callback"); };
            var next = new ConfigService.State(new ConfigSnapshot(38, ToolbarMode.ICONS_AND_LABELS, true, FontConfig.defaults().withSize(16), BuiltinTheme.LIGHT.appearance(), Map.of(), 150, 45, TerminalConfig.defaults()), List.of(), directory.resolve("config.toml"), true);
            assertThatThrownBy(() -> controller.accept(next)).isInstanceOf(IllegalStateException.class)
                .hasMessage("application callback");
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

    @Test @DisabledOnOs(OS.WINDOWS)
    void fullLiveOptionsReachEveryRetainedAndPendingPaneWithStableSessions() throws Exception {
        start(""); edt(() -> { owner(); owner(); }); launchAll();
        var first = owners.getFirst(); var retained = first.currentPane(); var tab = first.currentTab();
        var session = retained.session();
        edt(() -> first.invoke(ActionId.SPLIT_RIGHT)); launchAll();
        var sibling = first.currentPane();
        edt(() -> { tab.toggleZoom(); first.newTab(HOME); });
        reload(liveSettings(18));
        launchAll();
        edt(() -> {
            assertThat(tab.tree().zoomed()).isTrue();
            assertThat(retained.session()).isSameAs(session);
            for (var pane : List.of(retained, sibling, first.currentPane(), owners.get(1).currentPane())) {
                var options = pane.view().options();
                assertThat(options.fontFamily()).isEqualTo("Monospaced");
                assertThat(options.fontSize()).isEqualTo(18);
                assertThat(options.fallbackFonts()).containsExactly("Dialog");
                assertThat(options.ligatures()).isFalse();
                assertThat(options.lineHeight()).isEqualTo(1.5f);
                assertThat(options.optionAsMeta()).isEqualTo(dev.jasper.terminal.config.OptionAsMeta.NONE);
                assertThat(options.cursorStyle()).isEqualTo(dev.jasper.terminal.config.CursorStyle.BEAM);
                assertThat(options.cursorBlink()).isFalse();
                assertThat(options.copyOnSelect()).isTrue();
                assertThat(options.bell()).isEqualTo(dev.jasper.terminal.config.BellMode.NONE);
                var fonts = new dev.jasper.terminal.rendering.FontSet("Monospaced", 18, List.of("Dialog"), false, 1.5f);
                assertThat(pane.view().getMinimumSize()).isEqualTo(
                    new java.awt.Dimension(5 * fonts.cellWidth(), 2 * fonts.cellHeight()));
            }
            assertThat(field(retained.view(), "inactiveDim")).isEqualTo(.65f);
            first.setActive(false);
            assertThat(field(first.currentPane().view(), "inactiveDim")).isEqualTo(.65f);
            first.selectTheme(BuiltinTheme.LIGHT);
            assertThat(field(retained.view(), "inactiveDim")).isEqualTo(.65f);
            first.setActive(true);
            assertThat(field(first.currentPane().view(), "inactiveDim")).isEqualTo(0f);
            first.selectTab(tab); tab.toggleZoom(); tab.focus(retained);
            assertThat(field(sibling.view(), "inactiveDim")).isEqualTo(.65f);
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void typographyAndBehaviorChangesPreserveManualSizeAndThemeUntilSavedSizeChanges() throws Exception {
        start("[font]\nsize=18\n"); edt(this::owner); launchAll();
        var owner = owners.getFirst(); var pane = owner.currentPane();
        edt(() -> { pane.view().setFontSize(27); owner.selectTheme(BuiltinTheme.LIGHT); });
        reload(liveSettings(18));
        edt(() -> {
            assertThat(pane.view().fontSize()).isEqualTo(27);
            assertThat(pane.view().options().fontFamily()).isEqualTo("Monospaced");
            assertThat(pane.view().options().lineHeight()).isEqualTo(1.5f);
            assertThat(pane.view().palette()).isEqualTo(BuiltinTheme.LIGHT.palette());
            owner.newTab(HOME);
        }); launchAll();
        edt(() -> {
            assertThat(owner.currentPane().view().fontSize()).isEqualTo(18);
            owner.selectTab((TerminalTab) owner.tabStrip().getComponentAt(0));
            owner.invoke(ActionId.FONT_RESET);
            assertThat(pane.view().fontSize()).isEqualTo(18);
            pane.view().setFontSize(29);
        });
        reload(liveSettings(20));
        edt(() -> {
            assertThat(pane.view().fontSize()).isEqualTo(20);
            assertThat(pane.view().palette()).isEqualTo(BuiltinTheme.LIGHT.palette());
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void chromeBindingsSessionAndDimOnlyReloadsDoNotReapplyViewOptions() throws Exception {
        start(liveSettings(18)); edt(this::owner); launchAll();
        var pane = owners.getFirst().currentPane();
        var retained = pane.view().options();
        reload(liveSettings(18).replace("dim_inactive_panes=0.65", "dim_inactive_panes=0.8") + """
            [window]
            columns=90
            lines=30
            tab_height=50
            toolbar='icons'
            status_bar=false
            [terminal.shell]
            program='unused-executable'
            args=['literal argument']
            [terminal.env]
            JASPER_TEST='temporary'
            [keybindings]
            new_tab='Ctrl+F12'
            """);
        // Scrollback is also a session default, so changing it alone must leave view options untouched.
        var text = Files.readString(directory.resolve("config.toml"));
        reload(text.replace("[terminal]", "[terminal]\nscrollback=200"));
        edt(() -> assertThat(pane.view().options()).isSameAs(retained));
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void savedCopyOnSelectRunsActualSelectionAndPreservesNativeEditingAndShortcuts() throws Exception {
        start(""); edt(this::owner); launchAll();
        var owner = owners.getFirst(); var view = owner.currentPane().view();
        var copied = new java.util.concurrent.atomic.AtomicReference<String>();
        edt(() -> call(view, "setClipboard", new Class<?>[] {java.util.function.Supplier.class, java.util.function.Consumer.class},
            (java.util.function.Supplier<String>) () -> null, (java.util.function.Consumer<String>) copied::set));
        until(() -> { selectWord(view); return view.selectedText().orElse("").equals("alpha"); });
        assertThat(copied).hasNullValue();
        reload("[terminal]\ncopy_on_select=true\noption_as_meta='none'\n[keybindings]\nnew_tab='Ctrl+F12'\n");
        edt(() -> {
            selectWord(view); assertThat(copied).hasValue("alpha");
            var field = owner.currentPane().findBar().queryField();
            field.setText("native text"); field.selectAll();
            assertThat(owner.dispatchShortcut(owner.bindings().strokeFor(ActionId.COPY).orElseThrow(), field)).isFalse();
            assertThat(owner.dispatchShortcut(owner.bindings().strokeFor(ActionId.PASTE).orElseThrow(), field)).isFalse();
            assertThat(field.getSelectedText()).isEqualTo("native text");
            var event = new java.awt.event.KeyEvent(view, java.awt.event.KeyEvent.KEY_PRESSED, 0,
                java.awt.event.InputEvent.CTRL_DOWN_MASK, java.awt.event.KeyEvent.VK_F12, java.awt.event.KeyEvent.CHAR_UNDEFINED);
            terminalKey(view, event);
            assertThat(event.isConsumed()).isTrue();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void configuredDimmingSurvivesPendingLaunchFocusAndThemeChanges() throws Exception {
        start("[terminal]\ndim_inactive_panes=0.7\n");
        edt(() -> { owner().setActive(false); });
        launchAll();
        var owner = owners.getFirst(); var pane = owner.currentPane();
        edt(() -> {
            assertThat(field(pane.view(), "inactiveDim")).isEqualTo(.7f);
            owner.setActive(true);
            assertThat(field(pane.view(), "inactiveDim")).isEqualTo(0f);
            owner.setActive(false); owner.selectTheme(BuiltinTheme.LIGHT);
            assertThat(field(pane.view(), "inactiveDim")).isEqualTo(.7f);
        });
        reload("[terminal]\ndim_inactive_panes=0.2\n");
        edt(() -> assertThat(field(pane.view(), "inactiveDim")).isEqualTo(.2f));
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void queuedLaunchKeepsSessionAndGridCaptureWhileReadyViewUsesLatestLiveSettings() throws Exception {
        start("""
            [window]
            columns=80
            lines=24
            [terminal]
            scrollback=111
            [terminal.shell]
            program='first-shell'
            args=['one argument']
            [terminal.env]
            JASPER_TEST='first'
            """);
        var launches = new ArrayList<LaunchSettings>();
        edt(() -> owner(ApplicationTestSupport.windowLauncher(pending::add, controller::snapshot, (path, settings) -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
            launches.add(settings); return shell(path);
        })));
        var first = owners.getFirst(); var pane = first.currentPane();
        var initialArea = pane.getPreferredSize();
        reload(liveSettings(22) + """
            [window]
            columns=100
            lines=32
            [terminal.shell]
            program='second-shell'
            args=['two arguments', '']
            [terminal.env]
            JASPER_TEST='second'
            """);
        launchAll();
        edt(() -> {
            assertThat(pane.shellLabel()).isEqualTo("first-shell");
            assertThat(pane.view().options().lineHeight()).isEqualTo(1.5f);
            assertThat(pane.view().fontSize()).isEqualTo(22);
            assertThat(pane.getPreferredSize()).isEqualTo(initialArea);
            first.newTab(HOME);
            owner(ApplicationTestSupport.windowLauncher(pending::add, controller::snapshot, (path, settings) -> {
                launches.add(settings); return shell(path);
            }));
        }); launchAll();
        assertThat(launches).hasSize(3);
        assertThat(launches.get(0).command()).containsExactly("first-shell", "one argument");
        assertThat(launches.get(0).environment()).containsEntry("JASPER_TEST", "first");
        assertThat(launches.get(0).scrollback()).isEqualTo(111);
        assertThat(launches.get(1).command()).containsExactly("second-shell", "two arguments", "");
        assertThat(launches.get(1).environment()).containsEntry("JASPER_TEST", "second");
        assertThat(launches.get(1).scrollback()).isEqualTo(10000);
        assertThat(launches.get(1).columns()).isEqualTo(80);
        assertThat(launches.get(1).lines()).isEqualTo(24);
        assertThat(launches.get(2).columns()).isEqualTo(100);
        assertThat(launches.get(2).lines()).isEqualTo(32);
        edt(() -> {
            assertThat(first.currentPane().shellLabel()).isEqualTo("second-shell");
            assertThat(first.currentPane().view().fontSize()).isEqualTo(22);
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void windowLauncherPassesTheIntegrationDirectoryIntoResolvedLaunchSettings() throws Exception {
        start("");
        var captured = new AtomicReference<LaunchSettings>();
        edt(() -> owner(ApplicationTestSupport.windowLauncher(pending::add, controller::snapshot, (path, settings) -> {
            captured.set(settings); return shell(path);
        }, Path.of("/opt/si"))));
        launchAll();
        assertThat(captured.get().environment())
            .containsEntry("JASPER_SHELL_INTEGRATION", "/opt/si")
            .containsEntry("TERM_PROGRAM", "Jasper");
    }

    @Test void reloadActionRetriesFailedSavedInstallationWithUnchangedFiles() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "ui.theme.variant='dark'");
        var configWorker = new java.util.concurrent.ScheduledThreadPoolExecutor(1) {
            @Override public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(
                    Runnable command, long initialDelay, long delay, java.util.concurrent.TimeUnit unit) {
                return super.scheduleWithFixedDelay(command, 1, 1, java.util.concurrent.TimeUnit.DAYS);
            }
        };
        var broken = new java.util.concurrent.atomic.AtomicBoolean(false);
        var attempts = new ArrayList<BuiltinTheme>();
        var errors = new ArrayList<String>();
        service = new ConfigService(file, false, configWorker, SwingUtilities::invokeLater);
        edt(() -> {
            themes = new ThemeController(theme -> { attempts.add(theme); return !broken.get() && ThemeTestSupport.install(theme); });
            controller = new ConfigurationTestSupport(themes, service);
            owner().onError = errors::add;
            assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.DARK);
            assertThat(attempts).containsExactly(BuiltinTheme.DARK);
            broken.set(true);
        });
        Files.writeString(file, "ui.theme.variant='light'");
        edt(() -> owners.getFirst().invoke(ActionId.RELOAD_CONFIG));
        configWorker.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
        edt(() -> {
            assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.DARK);
            assertThat(themes.choice()).isEqualTo(Appearance.DARK);
            assertThat(attempts).containsExactly(BuiltinTheme.DARK, BuiltinTheme.LIGHT);
            assertThat(errors).hasSize(1);
            broken.set(false);
        });
        String acceptedText = Files.readString(file);
        var acceptedTime = Files.getLastModifiedTime(file);
        edt(() -> owners.getFirst().invoke(ActionId.RELOAD_CONFIG));
        configWorker.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
        edt(() -> {
            assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.LIGHT);
            assertThat(owners.getFirst().theme()).isEqualTo(themes.current());
            assertThat(themes.choice()).isEqualTo(Appearance.LIGHT);
            assertThat(themes.current().palette()).isEqualTo(BuiltinTheme.LIGHT.palette());
            assertThat(attempts).containsExactly(BuiltinTheme.DARK, BuiltinTheme.LIGHT, BuiltinTheme.LIGHT);
            assertThat(errors).hasSize(1);
            owners.getFirst().selectAppearance(Appearance.DARK);
            owners.getFirst().invoke(ActionId.RELOAD_CONFIG);
        });
        configWorker.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(Files.readString(file)).isEqualTo(acceptedText);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(acceptedTime);
        edt(() -> {
            assertThat(themes.choice()).isEqualTo(Appearance.DARK);
            assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.DARK);
            assertThat(attempts).containsExactly(BuiltinTheme.DARK, BuiltinTheme.LIGHT,
                BuiltinTheme.LIGHT, BuiltinTheme.DARK);
        });
    }

    private static String liveSettings(int size) {
        return """
            [font]
            family='Monospaced'
            size=%d
            fallback=['Dialog']
            ligatures=false
            line_height=1.5
            [terminal]
            option_as_meta='none'
            dim_inactive_panes=0.65
            copy_on_select=true
            bell='none'
            [terminal.cursor]
            shape='beam'
            blink=false
            """.formatted(size);
    }

    private static void selectWord(dev.jasper.terminal.view.TerminalView view) {
        for (int id : new int[] {java.awt.event.MouseEvent.MOUSE_PRESSED, java.awt.event.MouseEvent.MOUSE_RELEASED}) {
            var event = new java.awt.event.MouseEvent(view, id, 0, 0, 1, 1, 1, 1, 2, false, java.awt.event.MouseEvent.BUTTON1);
            call(view, "handleMouse", new Class<?>[] {java.awt.event.MouseEvent.class}, event);
        }
    }

    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true); return field.get(target);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void call(Object target, String name, Class<?>[] parameters, Object... args) {
        try {
            var method = target.getClass().getDeclaredMethod(name, parameters);
            method.setAccessible(true); method.invoke(target, args);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void terminalKey(dev.jasper.terminal.view.TerminalView view, java.awt.event.KeyEvent event) {
        try {
            var method = dev.jasper.terminal.view.TerminalView.class.getDeclaredMethod("handleKey", java.awt.event.KeyEvent.class);
            method.setAccessible(true); method.invoke(view, event);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Test void applicationSnapshotListenerReceivesTheCurrentAndEveryLaterSnapshot() throws Exception {
        start("[buddy]\nenabled=false\n");
        List<Boolean> seen = new ArrayList<>();
        edt(() -> controller.onSnapshot(snapshot -> seen.add(snapshot.buddyEnabled())));
        assertThat(seen).containsExactly(false);
        reload("[buddy]\nenabled=true\n");
        assertThat(seen).containsExactly(false, true);
        reload("[buddy]\nenabled='no'\n"); // rejected: the last good snapshot is redelivered
        assertThat(seen).containsExactly(false, true, true);
        edt(() -> controller.onSnapshot(snapshot -> {})); // a later registration replaces the earlier listener
        reload("[buddy]\nenabled=false\n");
        assertThat(seen).containsExactly(false, true, true);
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
@Test void changingDesiredStyleKeepsExistingAndNewOwnersUntilRestart() throws Exception {
    start("ui.theme.style='retro'\n");
    edt(() -> owner());
    var first = owners.getFirst();
    var retained = first.currentPane();
    reload("ui.theme.style='modern'\nfont.size=21\n");
    edt(() -> {
        owner();
        assertThat(themes.style()).isEqualTo(dev.jasper.app.config.ThemeStyle.RETRO);
        assertThat(first.currentPane()).isSameAs(retained);
        assertThat(owners).allSatisfy(w -> assertThat(w.theme().chrome()).isEqualTo(BuiltinTheme.RETRO));
        assertThat(controller.shown().diagnostics()).filteredOn(d -> d.key().equals("ui.theme.style"))
            .singleElement().satisfies(d -> assertThat(d.message()).contains("Restart Jasper"));
        assertThat(controller.snapshot().fontSize()).isEqualTo(21);
    });
    reload("ui.theme.style='retro'\n");
    edt(() -> assertThat(controller.shown().diagnostics()).noneMatch(d -> d.key().equals("ui.theme.style")));
}

@Test void modernVariantRemainsLiveWhileRetroIsPending() throws Exception {
    start("ui.theme.style='modern'\nui.theme.variant='dark'\n");
    reload("ui.theme.style='retro'\nui.theme.variant='light'\n");
    edt(() -> {
        assertThat(themes.style()).isEqualTo(dev.jasper.app.config.ThemeStyle.MODERN);
        assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.LIGHT);
        assertThat(controller.shown().diagnostics()).anyMatch(d -> d.key().equals("ui.theme.style"));
    });
}

@Test @DisabledOnOs(OS.WINDOWS)
void retroStyleReloadKeepsALiveSessionAndAppliesFontChanges() throws Exception {
    start("ui.theme.style='retro'\n");
    edt(() -> owner()); launchAll();
    var pane = owners.getFirst().currentPane();
    var session = pane.session();
    reload("ui.theme.style='modern'\nfont.size=22\n");
    edt(() -> {
        assertThat(pane.session()).isSameAs(session);
        assertThat(pane.view().fontSize()).isEqualTo(22);
        assertThat(pane.view().palette().background()).isEqualTo(java.awt.Color.BLACK);
        assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.RETRO);
    });
}

}
