package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ToolbarMode;
import java.awt.Font;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class BarHeightTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void theToolbarLaysOutItsButtonsFromItsHeight() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JToolBar toolbar = owner.toolbar();
            int labelledWidth = -1;
            for (int height : new int[]{24, 30, 64}) {
                owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
                owner.setToolbarHeight(height);
                assertThat(toolbar.getPreferredSize().height).as("height %d", height).isEqualTo(UIScale.scale(height));
                assertThat(toolbar.getMinimumSize().height).isEqualTo(UIScale.scale(height));
                layOut(toolbar);
                int side = UIScale.scale(height - 6);
                JButton newTab = button(toolbar, "New tab");
                assertThat(newTab.getHeight()).as("3 px above and below").isEqualTo(side);
                assertThat(newTab.getY()).isEqualTo((UIScale.scale(height) - side) / 2);
                if (labelledWidth < 0) labelledWidth = newTab.getWidth();
                assertThat(newTab.getWidth()).as("a labelled button keeps its side padding").isEqualTo(labelledWidth);
                var separator = Arrays.stream(toolbar.getComponents())
                    .filter(child -> child instanceof JSeparator && child.isVisible()).findFirst().orElseThrow();
                assertThat(separator.getHeight()).isEqualTo(UIScale.scale(height - 14));
                assertThat(separator.getY()).isEqualTo((UIScale.scale(height) - UIScale.scale(height - 14)) / 2);

                owner.setToolbarMode(ToolbarMode.ICONS);
                layOut(toolbar);
                assertThat(button(toolbar, "New tab").getSize()).as("icon-only buttons stay square")
                    .isEqualTo(new java.awt.Dimension(side, side));
                assertThat(button(toolbar, "Split").getWidth()).as("room for the menu arrow").isEqualTo(side + UIScale.scale(10));
            }
        });
    }

    @Test void theStatusBarTakesItsConfiguredHeightButNeverClipsItsText() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            WindowStatusBar status = owner.status();
            for (int height : new int[]{20, 30, 48}) {
                owner.setStatusBarHeight(height);
                assertThat(status.getPreferredSize().height).as("height %d", height).isEqualTo(UIScale.scale(height));
                assertThat(status.getMinimumSize().height).isEqualTo(UIScale.scale(height));
            }
            owner.setStatusBarHeight(20);
            Font font = UIManager.getFont("Label.font");
            try {
                UIManager.put("Label.font", font.deriveFont(40f));
                status.refreshTheme();
                int text = status.getFontMetrics(UIManager.getFont("Label.font").deriveFont(40f - UIScale.scale(2f))).getHeight();
                assertThat(status.getPreferredSize().height).as("a large UI font grows the bar").isGreaterThanOrEqualTo(text)
                    .isGreaterThan(UIScale.scale(20));
            } finally {
                UIManager.put("Label.font", null);
                status.refreshTheme();
            }
            assertThat(status.getPreferredSize().height).isEqualTo(UIScale.scale(20));
        });
    }

    @Test void theWindowAppliesLiveHeightsAndRejectsValuesOutsideTheRange() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var changes = new AtomicInteger(); owner.onMinimumSizeChanged = changes::incrementAndGet;
            assertThat(owner.toolbarHeight()).isEqualTo(30);
            assertThat(owner.statusBarHeight()).isEqualTo(30);
            owner.setToolbarHeight(40);
            assertThat(owner.toolbarHeight()).isEqualTo(40);
            assertThat(owner.toolbar().getPreferredSize().height).isEqualTo(UIScale.scale(40));
            assertThat(changes.get()).isEqualTo(1);
            owner.setStatusBarHeight(26);
            assertThat(owner.statusBarHeight()).isEqualTo(26);
            assertThat(owner.status().getPreferredSize().height).isEqualTo(UIScale.scale(26));
            assertThat(changes.get()).isEqualTo(2);
            owner.setToolbarHeight(40); owner.setStatusBarHeight(26);
            assertThat(changes.get()).as("an unchanged height is not a change").isEqualTo(2);
            for (int height : new int[]{23, 65, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThatIllegalArgumentException().isThrownBy(() -> owner.setToolbarHeight(height));
                assertThat(owner.toolbarHeight()).isEqualTo(40);
            }
            for (int height : new int[]{19, 49, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThatIllegalArgumentException().isThrownBy(() -> owner.setStatusBarHeight(height));
                assertThat(owner.statusBarHeight()).isEqualTo(26);
            }
            owner.setToolbarMode(ToolbarMode.HIDDEN); owner.setStatusVisible(false);
            owner.setToolbarHeight(50); owner.setStatusBarHeight(34);
            owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS); owner.setStatusVisible(true);
            assertThat(owner.toolbar().isVisible()).isTrue();
            assertThat(owner.toolbar().getPreferredSize().height).as("a hidden bar keeps its height").isEqualTo(UIScale.scale(50));
            assertThat(owner.status().isVisible()).isTrue();
            assertThat(owner.status().getPreferredSize().height).isEqualTo(UIScale.scale(34));
            var fresh = content(launcher(new ArrayDeque<>()));
            assertThat(fresh.toolbarHeight()).isEqualTo(30);
            assertThat(fresh.statusBarHeight()).isEqualTo(30);
        });
    }

    @Test void savedHeightsApplyLiveAndAnUnrelatedReloadKeepsTheSessionChoice() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var saved = ConfigSnapshot.defaults().toBuilder().toolbarHeight(40).statusBarHeight(26).build();
            owner.applyConfiguration(saved, true);
            assertThat(owner.toolbarHeight()).isEqualTo(40);
            assertThat(owner.statusBarHeight()).isEqualTo(26);
            owner.setToolbarHeight(50); owner.setStatusBarHeight(34);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).build(), true);
            assertThat(owner.toolbarHeight()).as("an unrelated change keeps the session height").isEqualTo(50);
            assertThat(owner.statusBarHeight()).isEqualTo(34);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).toolbarHeight(44).build(), true);
            assertThat(owner.toolbarHeight()).isEqualTo(44);
            assertThat(owner.statusBarHeight()).as("only the key that changed applies").isEqualTo(34);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).toolbarHeight(44).statusBarHeight(22).build(), true);
            assertThat(owner.statusBarHeight()).isEqualTo(22);
        });
    }

    @Test void viewControlsStartAtTheCurrentHeightCommitOnlyOnOkAndCanReset() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            exercise(owner, "Toolbar height…", "toolbarHeight", hook -> owner.confirmToolbarHeight = hook,
                owner::toolbarHeight, owner::setToolbarHeight, 24, 64);
            exercise(owner, "Status bar height…", "statusBarHeight", hook -> owner.confirmStatusBarHeight = hook,
                owner::statusBarHeight, owner::setStatusBarHeight, 20, 48);
            assertThat(owner.commands().entries().stream().map(entry -> entry.command().id()))
                .contains("view.toolbar_height", "view.status_bar_height");
        });
    }

    @Test void closingTheWindowIgnoresLaterHeightChanges() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setToolbarHeight(40); owner.setStatusBarHeight(26);
            owner.close();
            owner.setToolbarHeight(50); owner.setStatusBarHeight(34);
            assertThat(owner.toolbarHeight()).isEqualTo(40);
            assertThat(owner.statusBarHeight()).isEqualTo(26);
            assertThat(owner.confirmToolbarHeight.applyAsInt(new JPanel())).isEqualTo(JOptionPane.CANCEL_OPTION);
            assertThat(owner.confirmStatusBarHeight.applyAsInt(new JPanel())).isEqualTo(JOptionPane.CANCEL_OPTION);
        });
    }

    /** Drives one size dialog through OK with a typed value, Cancel, Reset and an out-of-range entry. */
    private static void exercise(WindowContent owner, String itemText, String name, Consumer<ToIntFunction<JComponent>> confirm,
                                 IntSupplier current, IntConsumer set, int min, int max) {
        String reset = "reset" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        set.accept(40);
        JMenuItem item = item(owner, itemText);
        confirm.accept(panel -> {
            JSpinner spinner = spinner(panel, name);
            assertThat(spinner.getValue()).isEqualTo(40);
            assertThat(((SpinnerNumberModel) spinner.getModel()).getMinimum()).isEqualTo(min);
            assertThat(((SpinnerNumberModel) spinner.getModel()).getMaximum()).isEqualTo(max);
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText("44");
            return JOptionPane.OK_OPTION;
        });
        item.doClick(); assertThat(current.getAsInt()).as(itemText).isEqualTo(44);
        confirm.accept(panel -> { spinner(panel, name).setValue(max); return JOptionPane.CANCEL_OPTION; });
        item.doClick(); assertThat(current.getAsInt()).isEqualTo(44);
        confirm.accept(panel -> {
            ((JButton) WindowTabsTest.named(panel, reset)).doClick();
            assertThat(spinner(panel, name).getValue()).isEqualTo(30);
            return JOptionPane.OK_OPTION;
        });
        item.doClick(); assertThat(current.getAsInt()).isEqualTo(30);
        var errors = new ArrayList<String>(); owner.onError = errors::add;
        confirm.accept(panel -> {
            ((JSpinner.DefaultEditor) spinner(panel, name).getEditor()).getTextField().setText("99");
            return JOptionPane.OK_OPTION;
        });
        item.doClick(); assertThat(current.getAsInt()).isEqualTo(30);
        assertThat(errors).singleElement().asString().contains(String.valueOf(min)).contains(String.valueOf(max));
    }

    private static void layOut(JToolBar toolbar) {
        toolbar.setSize(UIScale.scale(1200), toolbar.getPreferredSize().height);
        toolbar.doLayout();
    }

    private static JButton button(JToolBar toolbar, String label) {
        return Arrays.stream(toolbar.getComponents())
            .filter(child -> child instanceof JButton button && label.equals(button.getClientProperty("label")))
            .map(JButton.class::cast).findFirst().orElseThrow();
    }

    private static JSpinner spinner(java.awt.Container panel, String name) {
        return (JSpinner) WindowTabsTest.named(panel, name);
    }

    private static JMenuItem item(WindowContent owner, String text) {
        JMenu view = owner.menuBar().getMenu(2);
        for (int i = 0; i < view.getItemCount(); i++) {
            JMenuItem item = view.getItem(i);
            if (item != null && text.equals(item.getText())) return item;
        }
        throw new AssertionError("View must expose " + text);
    }
}
