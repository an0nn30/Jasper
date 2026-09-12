package dev.moray.app;

import java.awt.Color;
import java.util.*;
import dev.moray.terminal.TerminalSession;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class ThemeControllerTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void selectingLightRecolorsTheActualTerminalAndFindField() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[1];
        edt(() -> owners[0] = content(launcher(pending)));
        pending.remove().run();
        edt(() -> {
            appearance(owners[0]).getItem(0).doClick();
            assertThat(owners[0].currentPane().view().getBackground()).isEqualTo(new Color(0xfafafa));
            assertThat(owners[0].currentPane().findBar().queryField().getForeground()).isEqualTo(new Color(0x383a42));
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void sharedOwnersUpdateHiddenZoomedAndLatePanesWithoutChangingTheirState() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[3];
        ThemeController[] themes = new ThemeController[1];
        TerminalTab[] retained = new TerminalTab[1];
        TerminalPane[] first = new TerminalPane[1];
        TerminalSession[] session = new TerminalSession[1];
        edt(() -> {
            themes[0] = new ThemeController();
            owners[0] = content(launcher(pending), themes[0]);
            owners[1] = content(launcher(pending), themes[0]);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            retained[0] = owners[0].currentTab(); first[0] = owners[0].currentPane();
            session[0] = first[0].session();
            first[0].findBar().queryField().removeCaretListener((javax.swing.event.CaretListener)
                first[0].findBar().queryField().getAccessibleContext());
            owners[0].addNotify();
            first[0].view().setFontSize(23);
            first[0].findBar().open(); first[0].findBar().queryField().setText("[");
            first[0].findBar().regexButton().doClick();
            retained[0].rename("retained"); retained[0].split(SplitTree.Axis.RIGHT);
        });
        pending.remove().run();
        until(() -> first[0].findBar().result().error() != null);
        edt(() -> {
            retained[0].toggleZoom();
            owners[0].newTab(HOME); // completion deliberately waits across theme selection
            appearance(owners[1]).getItem(0).doClick();
            assertThat(themes[0].current().chrome()).isEqualTo(BuiltinTheme.LIGHT);
            for (TerminalPane pane : retained[0].panes()) {
                assertThat(pane.view().palette().background()).isEqualTo(new Color(0xfafafa));
                assertThat(pane.findBar().queryField().getBackground()).isEqualTo(new Color(0xfafafa));
            }
            assertThat(first[0].session()).isSameAs(session[0]);
            assertThat(first[0].view().fontSize()).isEqualTo(23);
            assertThat(first[0].findBar().isVisible()).isTrue();
            assertThat(first[0].findBar().queryField().getText()).isEqualTo("[");
            assertThat(first[0].findBar().result().error()).isNotNull();
            assertThat(retained[0].tree().zoomed()).isTrue();
            assertThat(retained[0].focusedPane()).isSameAs(retained[0].panes().get(1));
            assertThat(retained[0].title()).isEqualTo("retained");
            assertThat(owners[0].currentTab()).isNotSameAs(retained[0]);
            assertThat(appearance(owners[0]).getItem(0).isSelected()).isTrue();
            assertThat(owners[1].currentPane().view().getBackground()).isEqualTo(new Color(0xfafafa));
            owners[2] = content(launcher(pending), themes[0]);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            assertThat(owners[0].currentPane().view().getBackground()).isEqualTo(new Color(0xfafafa));
            assertThat(owners[2].currentPane().view().getBackground()).isEqualTo(new Color(0xfafafa));
            var query = first[0].findBar().queryField();
            assertThat(owners[0].dispatchShortcut(owners[0].bindings().strokeFor(ActionId.COPY).orElseThrow(), query)).isFalse();
            assertThat(owners[0].dispatchShortcut(owners[0].bindings().strokeFor(ActionId.PASTE).orElseThrow(), query)).isFalse();
            owners[0].selectTab(retained[0]);
            assertThat(first[0].view().getBackground()).isEqualTo(new Color(0xfafafa));
            appearance(owners[1]).getItem(1).doClick();
            assertThat(first[0].view().getBackground()).isEqualTo(new Color(0x292c34));
            assertThat(first[0].findBar().result().error()).isNotNull();
            var custom = new dev.moray.terminal.Palette(Color.WHITE, new Color(0x101820), Color.YELLOW,
                Color.GRAY, BuiltinTheme.DARK.palette().ansi());
            themes[0].configure(new ColorsConfig(Appearance.SYSTEM, "custom"), custom);
            themes[0].selectAppearance(Appearance.SYSTEM);
            themes[0].systemChanged(BuiltinTheme.LIGHT);
            for (TerminalPane pane : retained[0].panes()) assertThat(pane.view().palette()).isEqualTo(custom);
            assertThat(first[0].session()).isSameAs(session[0]);
            assertThat(first[0].findBar().result().error()).isNotNull();
            assertThat(retained[0].tree().zoomed()).isTrue();
            assertThat(appearance(owners[1]).getItem(2).isSelected()).isTrue();
            owners[0].removeNotify();
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void delegateUpdatesRetainTheExistingSplitComponentsAndModelRatios() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        JSplitPane[] split = new JSplitPane[1];
        javax.swing.plaf.SplitPaneUI[] oldUi = new javax.swing.plaf.SplitPaneUI[1];
        SplitTree.Node[] before = new SplitTree.Node[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT));
        pending.remove().run();
        edt(() -> {
            split[0] = (JSplitPane) owner[0].currentTab().getComponent(0);
            split[0].setSize(1000, 600); split[0].doLayout();
        });
        edt(() -> {});
        edt(() -> {
            split[0].setDividerLocation(0.7);
            // Exercise a divider adjustment at the real UI-delegate replacement boundary.
            // Theme/layout adjustments must not be treated as the user's divider drag.
            split[0].addPropertyChangeListener("UI", event -> split[0].setDividerLocation(.2));
            oldUi[0] = split[0].getUI();
            before[0] = owner[0].currentTab().tree().root().orElseThrow();
            appearance(owner[0]).getItem(0).doClick();
            split[0].doLayout();
        });
        edt(() -> {
            split[0].doLayout();
            assertThat(split[0].getUI()).isNotSameAs(oldUi[0]);
            assertThat(owner[0].currentTab().getComponent(0)).isSameAs(split[0]);
            assertThat(owner[0].currentTab().tree().root().orElseThrow()).isEqualTo(before[0]);
            assertThat(split[0].getDividerLocation()).isBetween(685, 705);
        });
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void nestedSplitsKeepModelRatiosAndDividerPositionsAcrossRootDelegateChanges() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        JRootPane[] root = new JRootPane[1];
        JSplitPane[] split = new JSplitPane[2];
        SplitTree.Node[] before = new SplitTree.Node[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT)); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_DOWN)); pending.remove().run();
        edt(() -> {
            root[0] = new JRootPane(); root[0].setContentPane(owner[0]); root[0].setJMenuBar(owner[0].menuBar());
            owner[0].installRootBindings(root[0]);
            split[0] = (JSplitPane) owner[0].currentTab().getComponent(0);
            split[1] = (JSplitPane) split[0].getRightComponent();
            split[0].setSize(1200, 700); split[0].doLayout(); split[1].doLayout();
        });
        edt(() -> {});
        edt(() -> {
            split[0].setDividerLocation(.63); split[0].doLayout();
            split[1].setDividerLocation(.31); split[1].doLayout();
            before[0] = owner[0].currentTab().tree().root().orElseThrow();
            owner[0].selectTheme(BuiltinTheme.LIGHT);
            owner[0].selectTheme(BuiltinTheme.DARK);
        });
        edt(() -> {
            split[0].doLayout(); split[1].doLayout();
            assertThat(owner[0].currentTab().tree().root().orElseThrow()).isEqualTo(before[0]);
            assertThat(split[0].getDividerLocation() / (double) (split[0].getWidth() - split[0].getDividerSize()))
                .isCloseTo(.63, within(.003));
            assertThat(split[1].getDividerLocation() / (double) (split[1].getHeight() - split[1].getDividerSize()))
                .isCloseTo(.31, within(.003));
            var copy = owner[0].bindings().strokeFor(ActionId.COPY).orElseThrow();
            assertThat(root[0].getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(copy)).isEqualTo(ActionId.COPY.id());
        });
    }

    @Test void failedPartialInstallationRestoresPreviousLafAndDoesNotNotifyOwners() throws Exception {
        edt(() -> {
            var themes = new ThemeController(theme -> {
                ThemeController.install(theme);
                return theme != BuiltinTheme.LIGHT;
            });
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var changed = new ArrayList<ResolvedTheme>(); owner.onThemeChanged = changed::add;
            var before = UIManager.getLookAndFeel();
            assertThatThrownBy(() -> themes.select(BuiltinTheme.LIGHT)).isInstanceOf(IllegalStateException.class);
            assertThat(UIManager.getLookAndFeel()).isSameAs(before);
            assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.DARK);
            assertThat(owner.getBackground()).isEqualTo(new Color(0x292c34));
            assertThat(changed).isEmpty();
        });
    }

    @Test void rejectsOffEdtThemeChangesBeforeMutatingSwing() throws Exception {
        ThemeController[] themes = new ThemeController[1];
        edt(() -> themes[0] = new ThemeController());
        assertThatThrownBy(() -> themes[0].select(BuiltinTheme.LIGHT)).isInstanceOf(IllegalStateException.class);
        edt(() -> assertThat(themes[0].current().chrome()).isEqualTo(BuiltinTheme.DARK));
    }

    @Test void closeUnregistersOwnerAndClearsItsThemeCallback() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var received = new ArrayList<ResolvedTheme>();
            owner.onThemeChanged = received::add;
            themes.select(BuiltinTheme.LIGHT);
            assertThat(received).containsExactly(new ResolvedTheme(BuiltinTheme.LIGHT, BuiltinTheme.LIGHT.palette()));
            owner.close();
            // A closed owner must not even receive UI-delegate changes.
            var closedUi = owner.getUI();
            owner.onThemeChanged.accept(new ResolvedTheme(BuiltinTheme.DARK, BuiltinTheme.DARK.palette()));
            themes.select(BuiltinTheme.DARK);
            assertThat(owner.getUI()).isSameAs(closedUi);
            assertThat(received).containsExactly(new ResolvedTheme(BuiltinTheme.LIGHT, BuiltinTheme.LIGHT.palette()));
        });
    }

    @Test void failedInstallationKeepsPreviousThemeAndResetsMenuSelection() throws Exception {
        edt(() -> {
            var themes = new ThemeController(theme -> theme != BuiltinTheme.LIGHT && ThemeController.install(theme));
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var errors = new ArrayList<String>(); owner.onError = errors::add;
            var before = UIManager.getLookAndFeel();
            appearance(owner).getItem(0).doClick();
            assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.DARK);
            assertThat(UIManager.getLookAndFeel()).isSameAs(before);
            assertThat(appearance(owner).getItem(0).isSelected()).isFalse();
            assertThat(appearance(owner).getItem(2).isSelected()).isTrue();
            assertThat(errors).singleElement().asString().contains("Could not apply theme");
        });
    }

    @Test void failedSystemInstallationRetriesLatestReadingWithoutAnotherEvent() throws Exception {
        edt(() -> {
            var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
            var calls = new ArrayList<BuiltinTheme>();
            var themes = new ThemeController(theme -> {
                calls.add(theme);
                return (theme != BuiltinTheme.LIGHT || !fail.get()) && ThemeController.install(theme);
            });
            var first = content(launcher(new ArrayDeque<>()), themes);
            var second = content(launcher(new ArrayDeque<>()), themes);
            assertThatThrownBy(() -> themes.systemChanged(BuiltinTheme.LIGHT)).isInstanceOf(IllegalStateException.class);
            assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.DARK);
            assertThat(themes.choice()).isEqualTo(Appearance.SYSTEM);
            assertThat(appearance(first).getItem(2).isSelected()).isTrue();
            fail.set(false);
            first.selectAppearance(Appearance.SYSTEM);
            assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.LIGHT);
            assertThat(appearance(second).getItem(2).isSelected()).isTrue();
            var palette = new dev.moray.terminal.Palette(java.awt.Color.WHITE, java.awt.Color.BLACK,
                java.awt.Color.YELLOW, java.awt.Color.GRAY, BuiltinTheme.DARK.palette().ansi());
            int installations = calls.size();
            themes.configure(new ColorsConfig(Appearance.SYSTEM, "custom"), palette);
            assertThat(calls).hasSize(installations);
            first.selectAppearance(Appearance.DARK);
            themes.systemChanged(BuiltinTheme.LIGHT);
            assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.DARK);
            themes.configure(new ColorsConfig(Appearance.SYSTEM, "another"), palette);
            assertThat(themes.choice()).isEqualTo(Appearance.DARK);
            themes.configure(new ColorsConfig(Appearance.LIGHT, "another"), palette);
            assertThat(themes.choice()).isEqualTo(Appearance.LIGHT);
        });
    }

    @Test void resourcesCoordinateChromeAndKeepTextReadableAcrossStates() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            var owner = content(launcher(new ArrayDeque<>()), themes);
            for (BuiltinTheme theme : BuiltinTheme.values()) {
                themes.select(theme);
                assertThat(owner.getBackground()).isEqualTo(theme.palette().background());
                assertThat(owner.toolbar().getBackground()).isEqualTo(theme.palette().background());
                assertThat(UIManager.getColor("TabbedPane.selectedBackground")).isEqualTo(theme.palette().background());
                for (String prefix : new String[]{"Panel", "TextField", "MenuItem", "PopupMenu", "Button"}) {
                    assertThat(contrast(UIManager.getColor(prefix + ".foreground"), UIManager.getColor(prefix + ".background")))
                        .as(prefix + " normal text in " + theme).isGreaterThanOrEqualTo(4.5);
                }
                assertThat(contrast(UIManager.getColor("Moray.titleInactiveForeground"), owner.toolbar().getBackground())).isGreaterThanOrEqualTo(3);
                assertThat(contrast(UIManager.getColor("Button.disabledText"), owner.toolbar().getBackground())).isGreaterThanOrEqualTo(3);
                assertThat(contrast(UIManager.getColor("MenuItem.selectionForeground"), UIManager.getColor("MenuItem.selectionBackground"))).isGreaterThanOrEqualTo(4.5);
            }
        });
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }
    private static double luminance(Color color) {
        double[] rgb = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }

    static JMenu appearance(WindowContent owner) {
        for (var component : owner.menuBar().getMenu(2).getMenuComponents()) {
            if (component instanceof JMenu menu && menu.getText().equals("Appearance")) return menu;
        }
        throw new AssertionError("Appearance menu is absent");
    }
}
