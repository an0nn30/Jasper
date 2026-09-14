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
    void lafChangesRefreshSharedHiddenZoomedAndLatePanesWithoutChangingTheirState() throws Exception {
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
            owners[1].selectLaf(UiLookAndFeel.NIMBUS);
            assertThat(UIManager.getLookAndFeel().getID()).isEqualTo("Nimbus");
            assertThat(owners[1].currentPane().findBar().queryField().getUI().getClass().getName()).contains("Synth");
            for (TerminalPane pane : retained[0].panes()) {
                assertThat(pane.view().palette().background()).isEqualTo(Color.BLACK);
                assertThat(pane.view().palette().foreground()).isEqualTo(Color.WHITE);
                assertThat(pane.findBar().queryField().getUI().getClass().getName()).contains("Synth");
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
            assertThat(owners[1].currentPane().view().getBackground()).isEqualTo(Color.BLACK);
            owners[2] = content(launcher(pending), themes[0]);
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            assertThat(owners[0].currentPane().view().getBackground()).isEqualTo(Color.BLACK);
            assertThat(owners[2].currentPane().view().getBackground()).isEqualTo(Color.BLACK);
            var query = first[0].findBar().queryField();
            assertThat(owners[0].dispatchShortcut(owners[0].bindings().strokeFor(ActionId.COPY).orElseThrow(), query)).isFalse();
            assertThat(owners[0].dispatchShortcut(owners[0].bindings().strokeFor(ActionId.PASTE).orElseThrow(), query)).isFalse();
            owners[0].selectTab(retained[0]);
            assertThat(first[0].view().getBackground()).isEqualTo(Color.BLACK);
            owners[1].selectLaf(UiLookAndFeel.METAL);
            assertThat(first[0].view().getBackground()).isEqualTo(Color.BLACK);
            assertThat(first[0].findBar().result().error()).isNotNull();
            owners[1].selectLaf(UiLookAndFeel.NIMBUS);
            for (TerminalPane pane : retained[0].panes()) {
                assertThat(pane.view().palette().background()).isEqualTo(Color.BLACK);
                assertThat(pane.view().palette().foreground()).isEqualTo(Color.WHITE);
                assertThat(pane.findBar().queryField().getUI().getClass().getName()).contains("Synth");
            }
            assertThat(first[0].session()).isSameAs(session[0]);
            assertThat(first[0].findBar().result().error()).isNotNull();
            assertThat(retained[0].tree().zoomed()).isTrue();
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
            owner[0].selectLaf(UiLookAndFeel.NIMBUS);
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
            owner[0].selectLaf(UiLookAndFeel.NIMBUS);
            owner[0].selectLaf(UiLookAndFeel.METAL);
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

    @Test void rejectsOffEdtLafChangesBeforeMutatingSwing() throws Exception {
        ThemeController[] themes = new ThemeController[1];
        edt(() -> themes[0] = new ThemeController());
        assertThatThrownBy(() -> themes[0].selectLaf(UiLookAndFeel.NIMBUS)).isInstanceOf(IllegalStateException.class);
        edt(() -> assertThat(themes[0].current().palette().background()).isEqualTo(Color.BLACK));
    }

    @Test void closeUnregistersOwnerBeforeLaterDelegateChanges() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            themes.selectLaf(UiLookAndFeel.METAL);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var received = new ArrayList<ResolvedTheme>();
            owner.onThemeChanged = received::add;
            owner.close();
            var closedUi = owner.getUI();
            themes.selectLaf(UiLookAndFeel.NIMBUS);
            assertThat(owner.getUI()).isSameAs(closedUi);
            assertThat(received).isEmpty();
        });
    }
}
