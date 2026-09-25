package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.appearance.BuiltinTheme;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class TabHeightTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @Test void liveHeightChangesTheTabStripAndMinimumButNotTheTitleRow() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[1];
        edt(() -> owners[0] = content(launcher(pending)));
        pending.remove().run(); until(() -> owners[0].currentPane().view() != null);
        edt(() -> {
            var owner = owners[0]; var pane = owner.currentPane(); var tab = owner.currentTab();
            var session = pane.session(); pane.view().setFontSize(19);
            var root = new JRootPane(); owner.installRootBindings(root);
            try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                root.setSize(958, 958); LayoutTestSupport.layoutTree(root);
                assertThat(header.getHeight()).isEqualTo(28);
                assertThat(owner.windowTabs().getHeight()).isEqualTo(30);
                int originalMinimum = root.getMinimumSize().height;
                var changes = new AtomicInteger(); owner.onMinimumSizeChanged = changes::incrementAndGet;
                owner.setTabHeight(44); LayoutTestSupport.layoutTree(root);
                assertThat(changes.get()).isPositive();
                assertThat(owner.tabHeight()).isEqualTo(44);
                assertThat(header.getHeight()).isEqualTo(28);
                assertThat(owner.windowTabs().getHeight()).isEqualTo(44);
                assertThat(root.getMinimumSize().height).isEqualTo(originalMinimum + 14);
                owner.selectTheme(BuiltinTheme.LIGHT); LayoutTestSupport.layoutTree(root);
                assertThat(header.getHeight()).isEqualTo(28);
                assertThat(owner.windowTabs().getHeight()).isEqualTo(44);
                assertThat(owner.currentTab()).isSameAs(tab);
                assertThat(owner.currentPane().session()).isSameAs(session);
                assertThat(pane.view().fontSize()).isEqualTo(19);
                assertThat(content(launcher(new ArrayDeque<>())).tabHeight()).isEqualTo(30);
            }
        });
    }

    @Test void plainWindowUsesSameHeightAndRejectsValuesOutsideInclusiveRange() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>())); var root = new JRootPane();
            WindowContent.installTitleBar(root, owner, false, title -> {}); root.setSize(958, 958);
            assertThat(owner.windowTabs().isVisible()).isTrue();
            owner.newTab(HOME);
            for (int height : new int[]{20, 72, 30}) {
                owner.setTabHeight(height); LayoutTestSupport.layoutTree(root);
                assertThat(owner.windowTabs().getHeight()).isEqualTo(height);
                assertThat(owner.windowTabs().getMinimumSize().height).isEqualTo(height);
            }
            for (int height : new int[]{19, 73, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThatIllegalArgumentException().isThrownBy(() -> owner.setTabHeight(height));
                assertThat(owner.tabHeight()).isEqualTo(30);
            }
        });
    }

    @Test void viewControlStartsAtCurrentHeightCommitsOnlyOnOkAndCanReset() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>())); owner.setTabHeight(47);
            JMenuItem item = heightItem(owner);
            owner.confirmTabHeight = panel -> {
                JSpinner spinner = spinner(panel);
                assertThat(spinner.getValue()).isEqualTo(47);
                assertThat(((SpinnerNumberModel) spinner.getModel()).getMinimum()).isEqualTo(20);
                assertThat(((SpinnerNumberModel) spinner.getModel()).getMaximum()).isEqualTo(72);
                ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText("52");
                return JOptionPane.OK_OPTION;
            };
            item.doClick(); assertThat(owner.tabHeight()).isEqualTo(52);
            owner.confirmTabHeight = panel -> {
                assertThat(spinner(panel).getValue()).isEqualTo(52);
                spinner(panel).setValue(66); return JOptionPane.CANCEL_OPTION;
            };
            item.doClick(); assertThat(owner.tabHeight()).isEqualTo(52);
            owner.confirmTabHeight = panel -> {
                ((JButton) WindowTabsTest.named(panel, "resetTabHeight")).doClick();
                assertThat(spinner(panel).getValue()).isEqualTo(30);
                return JOptionPane.OK_OPTION;
            };
            item.doClick(); assertThat(owner.tabHeight()).isEqualTo(30);
            owner.confirmTabHeight = panel -> { spinner(panel).setValue(65); return JOptionPane.CLOSED_OPTION; };
            item.doClick(); assertThat(owner.tabHeight()).isEqualTo(30);
            owner.onError = message -> {};
            owner.confirmTabHeight = panel -> {
                ((JSpinner.DefaultEditor) spinner(panel).getEditor()).getTextField().setText("99");
                return JOptionPane.OK_OPTION;
            };
            item.doClick(); assertThat(owner.tabHeight()).isEqualTo(30);
        });
    }

    @Test void closingOwnerClearsGeometryCallbackAndIgnoresLaterAppearanceEdits() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>())); var changes = new AtomicInteger();
            owner.onTabHeightChanged = changes::incrementAndGet;
            owner.setTabHeight(44); assertThat(changes.get()).isEqualTo(1);
            owner.close(); owner.onTabHeightChanged.run(); owner.setTabHeight(60);
            assertThat(changes.get()).isEqualTo(1);
            assertThat(owner.tabHeight()).isEqualTo(44);
        });
    }

    private static JSpinner spinner(java.awt.Container panel) {
        return (JSpinner) WindowTabsTest.named(panel, "tabHeight");
    }
    private static JMenuItem heightItem(WindowContent owner) {
        JMenu view = owner.menuBar().getMenu(2);
        for (int i = 0; i < view.getItemCount(); i++) {
            JMenuItem item = view.getItem(i);
            if (item != null && item.getText().equals("Tab height\u2026")) return item;
        }
        throw new AssertionError("View must expose Tab height");
    }
}
