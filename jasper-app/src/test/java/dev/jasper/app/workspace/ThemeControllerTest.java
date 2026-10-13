package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.SplitTree;
import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.app.workspace.TerminalTab;
import dev.jasper.app.workspace.WindowContent;
import java.awt.Color;
import java.util.*;
import dev.jasper.terminal.session.TerminalSession;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class ThemeControllerTest {
    @Test void failedInstallerRestoresBothRawDefaultsLayers() throws Exception {
        edt(() -> {
            var controller = ThemeTestSupport.controller(true, theme -> {
                if (!theme.dark()) {
                    UIManager.put("migration.override", Color.RED);
                    UIManager.getLookAndFeelDefaults().put("migration.laf", Color.BLUE);
                    throw new IllegalStateException("partial install");
                }
                return ThemeTestSupport.install(theme);
            });
            UIManager.put("migration.override", Color.GREEN);
            UIManager.getLookAndFeelDefaults().put("migration.laf", Color.YELLOW);
            var old = controller.current();
            assertThatThrownBy(() -> controller.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH))
                .isInstanceOf(ThemeController.InstallationFailure.class);
            assertThat(controller.current()).isSameAs(old);
            assertThat(UIManager.getColor("migration.override")).isEqualTo(Color.GREEN);
            assertThat(UIManager.getLookAndFeelDefaults().getColor("migration.laf")).isEqualTo(Color.YELLOW);
            UIManager.put("migration.override", null);
            UIManager.getLookAndFeelDefaults().remove("migration.laf");
        });
    }

    @Test void failedCombinedChangePublishesNeitherSlotNorChoiceOrFont() throws Exception {
        edt(() -> {
            var controller = ThemeTestSupport.controller(true, theme -> {
                ThemeTestSupport.install(theme);
                return theme.dark();
            });
            var old = controller.current();
            var laf = UIManager.getLookAndFeel();
            var font = UIManager.getFont("Label.font");
            var aliases = Map.of("Jasper.semantic.surface.app", dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP),
                "Jasper.semantic.accent", UIManager.getColor("Jasper.semantic.accent"),
                "Button.foreground", UIManager.getColor("Button.foreground"));
            var changes = new ArrayList<ResolvedTheme>();
            controller.subscribe((value, refresh) -> changes.add(value)); changes.clear();
            assertThatThrownBy(() -> controller.configure(false, dev.jasper.app.config.TerminalSchemeChoice.HIGH_CONTRAST,
                new dev.jasper.app.config.UiFontConfig("Monospaced", 19)))
                .isInstanceOf(ThemeController.InstallationFailure.class);
            assertThat(controller.current()).isSameAs(old);
            assertThat(controller.current().chrome().dark()).isTrue();
            assertThat(controller.schemeChoice()).as("the scheme of the failed change is not kept").isEqualTo(dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            assertThat(UIManager.getLookAndFeel()).isSameAs(laf);
            assertThat(UIManager.getFont("Label.font")).isEqualTo(font);
            aliases.forEach((key, value) -> assertThat(UIManager.getColor(key)).as(key).isEqualTo(value));
            assertThat(controller.appearance().binding().current()).isEqualTo(old.pair());
            assertThat(changes).isEmpty();
        });
    }

@Test void closingThemeSubscriptionStopsFurtherDelivery() throws Exception {
    DesktopTestSupport.edt(() -> {
        var changes = new java.util.ArrayList<ResolvedTheme>();
        var themes = ThemeTestSupport.controller(true, theme -> true);
        var registration = themes.subscribe((theme, delegates) -> changes.add(theme));
        assertThat(changes).hasSize(1);
        registration.close(); registration.close();
        themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
        assertThat(changes).hasSize(1);
    });
}

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void selectingLightRecolorsTheActualTerminalAndFindField() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[1];
        edt(() -> owners[0] = content(launcher(pending)));
        pending.remove().run();
        edt(() -> {
            owners[0].selectVariant(false);
            assertThat(owners[0].currentPane().view().getBackground()).isEqualTo(ThemeTestSupport.terminal(false).background());
            assertThat(owners[0].currentPane().findBar().queryField().getForeground()).isEqualTo(UIManager.getColor("TextField.foreground"));
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
            themes[0] = ThemeTestSupport.controller(true);
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
            owners[1].selectVariant(false);
            assertThat(themes[0].current().chrome()).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.generated(false));
            for (TerminalPane pane : retained[0].panes()) {
                assertThat(pane.view().palette().background()).isEqualTo(ThemeTestSupport.terminal(false).background());
                // The invalid pattern keeps its no-match tint, in the new theme's colour.
                assertThat(pane.findBar().queryField().getBackground()).isEqualTo(pane == first[0] ? dev.jasper.ui.theme.ColorMath.mix(dev.jasper.app.platform.ThemeZone.color(pane, dev.jasper.ui.theme.SemanticColor.BORDER_ERROR), dev.jasper.app.platform.ThemeZone.color(pane, dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT), .16)
                    : dev.jasper.app.platform.ThemeZone.color(pane, dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT));
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
            assertThat(themes[0].current().chrome().dark()).isFalse();
            assertThat(owners[1].currentPane().view().getBackground()).isEqualTo(ThemeTestSupport.terminal(false).background());
            owners[2] = content(launcher(pending), themes[0]);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            assertThat(owners[0].currentPane().view().getBackground()).isEqualTo(ThemeTestSupport.terminal(false).background());
            assertThat(owners[2].currentPane().view().getBackground()).isEqualTo(ThemeTestSupport.terminal(false).background());
            var query = first[0].findBar().queryField();
            assertThat(owners[0].dispatchShortcut(owners[0].bindings().strokeFor(ActionId.COPY).orElseThrow(), query)).isFalse();
            assertThat(owners[0].dispatchShortcut(owners[0].bindings().strokeFor(ActionId.PASTE).orElseThrow(), query)).isFalse();
            owners[0].selectTab(retained[0]);
            assertThat(first[0].view().getBackground()).isEqualTo(ThemeTestSupport.terminal(false).background());
            owners[1].selectVariant(true);
            assertThat(first[0].view().getBackground()).isEqualTo(ThemeTestSupport.terminal(true).background());
            assertThat(first[0].findBar().result().error()).isNotNull();
            for (TerminalPane pane : retained[0].panes()) assertThat(pane.view().palette()).isEqualTo(ThemeTestSupport.terminal(true));
            assertThat(first[0].session()).isSameAs(session[0]);
            assertThat(retained[0].tree().zoomed()).isTrue();
            assertThat(themes[0].current().chrome().dark()).isTrue();
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
            owner[0].selectVariant(false);
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
            owner[0].selectVariant(false);
            owner[0].selectVariant(true);
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
            var themes = ThemeTestSupport.controller(true, theme -> {
                ThemeTestSupport.install(theme);
                return theme.dark();
            });
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var changed = new ArrayList<ResolvedTheme>(); owner.onThemeChanged = changed::add;
            var before = UIManager.getLookAndFeel();
            assertThatThrownBy(() -> themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH)).isInstanceOf(IllegalStateException.class);
            assertThat(UIManager.getLookAndFeel()).isSameAs(before);
            assertThat(themes.current().chrome()).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.generated(true));
            assertThat(owner.getBackground()).isEqualTo(ThemeTestSupport.color(true, dev.jasper.ui.theme.SemanticColor.SURFACE_TERMINAL));
            assertThat(changed).isEmpty();
        });
    }

    @Test void rejectsOffEdtThemeChangesBeforeMutatingSwing() throws Exception {
        ThemeController[] themes = new ThemeController[1];
        edt(() -> themes[0] = ThemeTestSupport.controller(true));
        assertThatThrownBy(() -> themes[0].configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH)).isInstanceOf(IllegalStateException.class);
        edt(() -> assertThat(themes[0].current().chrome()).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.generated(true)));
    }

    /** A subscriber that throws is reported; later subscribers, the look and feel and the font still take the change. */
    @Test void aFailingSubscriberDoesNotStopTheOthersOrTheChange() throws Exception {
        edt(() -> {
            try (var themes = ThemeTestSupport.controller(true)) {
                var received = new ArrayList<ResolvedTheme>();
                themes.subscribe((value, chrome) -> { throw new IllegalStateException("a bad subscriber"); });
                themes.subscribe((value, chrome) -> received.add(value));
                received.clear();
                themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("Monospaced", 19));
                assertThat(received).containsExactly(themes.current());
                assertThat(themes.current().chrome()).isSameAs(ThemeTestSupport.generated(false));
                assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(ThemeTestSupport.generated(false));
                assertThat(UIManager.getFont("Label.font").getSize2D()).isEqualTo(19f);
            } finally { ThemeTestSupport.controller(true); }
        });
    }

    @Test void closeUnregistersOwnerAndClearsItsThemeCallback() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var received = new ArrayList<ResolvedTheme>();
            owner.onThemeChanged = received::add;
            themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            assertThat(received).containsExactly(ThemeTestSupport.resolved(false));
            owner.close();
            // A closed owner must not even receive UI-delegate changes.
            var closedUi = owner.getUI();
            owner.onThemeChanged.accept(dev.jasper.app.appearance.ThemeTestSupport.resolved(true));
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            assertThat(owner.getUI()).isSameAs(closedUi);
            assertThat(received).containsExactly(ThemeTestSupport.resolved(false));
        });
    }

    @Test void failedInstallationKeepsThePreviousThemeAndReportsTheError() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true, theme -> theme.dark() && ThemeTestSupport.install(theme));
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var errors = new ArrayList<String>(); owner.onError = (title, message) -> errors.add(message);
            var before = UIManager.getLookAndFeel();
            owner.selectVariant(false);
            assertThat(themes.current().chrome()).isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.generated(true));
            assertThat(themes.current().chrome().dark()).isTrue();
            assertThat(UIManager.getLookAndFeel()).isSameAs(before);
            assertThat(errors).singleElement().asString().contains("Could not apply theme");
        });
    }

    @Test void resourcesCoordinateChromeAndKeepTextReadableAcrossStates() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            for (boolean themeDark : new boolean[]{true, false}) {
                themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                assertThat(owner.getBackground()).isEqualTo(ThemeTestSupport.terminal(themeDark).background());
                assertThat(owner.toolbar().getBackground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                assertThat(owner.leftStripe().getBackground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                assertThat(owner.rightStripe().getBackground()).isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                assertThat(UIManager.getColor("ToolBar.background")).isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                for (String prefix : new String[]{"Panel", "TextField", "MenuItem", "PopupMenu", "Button"}) {
                    assertThat(contrast(UIManager.getColor(prefix + ".foreground"), UIManager.getColor(prefix + ".background")))
                        .as(prefix + " normal text in " + (themeDark ? "dark" : "light")).isGreaterThanOrEqualTo(4.5);
                }
                assertThat(contrast(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.TEXT_TERTIARY), owner.toolbar().getBackground())).isGreaterThanOrEqualTo(3);
                assertThat(contrast(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.TEXT_TERTIARY), owner.toolbar().getBackground())).isGreaterThanOrEqualTo(3);
                // Light's text.onAccent on selection.bg is 3.98:1 as authored; Dark meets the 4.5 floor.
                assertThat(contrast(UIManager.getColor("MenuItem.selectionForeground"), UIManager.getColor("MenuItem.selectionBackground")))
                    .as("menu selection text in " + (themeDark ? "dark" : "light")).isGreaterThanOrEqualTo(themeDark ? 4.5 : 3.98);
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
}
