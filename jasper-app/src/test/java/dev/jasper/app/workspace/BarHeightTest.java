package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ToolbarMode;
import java.awt.Font;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
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
            for (int height : new int[]{28, 30, 64}) {
                owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
                owner.setToolbarHeight(height);
                assertThat(toolbar.getPreferredSize().height).as("height %d", height).isEqualTo(UIScale.scale(height));
                assertThat(toolbar.getMinimumSize().height).isEqualTo(UIScale.scale(height));
                layOut(toolbar);
                int side = UIScale.scale(height - 6);
                JButton newTab = button(toolbar, "New tab");
                assertThat(newTab.getHeight()).as("3 px above and below").isEqualTo(side);
                assertThat(newTab.getY()).isEqualTo((UIScale.scale(height) - side) / 2);
                assertThat(newTab.getWidth()).as("common built-ins stay square in either toolbar mode").isEqualTo(side);
                JButton split = button(toolbar, "Split");
                if (labelledWidth < 0) labelledWidth = split.getWidth();
                assertThat(split.getWidth()).as("a labelled button keeps its side padding").isEqualTo(labelledWidth);
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

    @Test void theHeaderRowStaysWhenTheToolbarIsHidden() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setSize(900, 600);
            var panel = new JPanel();
            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, panel, 200);
            var parent = panel.getParent();
            for (var mode : new ToolbarMode[] {ToolbarMode.ICONS, ToolbarMode.HIDDEN, ToolbarMode.ICONS_AND_LABELS}) {
                owner.setToolbarMode(mode);
                dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
                int expected = owner.chrome().header().getHeight();
                assertThat(expected).isEqualTo(UIScale.scale(30));
                assertThat(SwingUtilities.convertPoint(panel.getParent(), 0, 0, owner).y).as("the frame starts under the header row").isEqualTo(expected);
                assertThat(panel.getParent()).isSameAs(parent);
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
            var original=new java.util.HashMap<Object,Object>();UIManager.getDefaults().forEach(original::put);
            int normal=status.getPreferredSize().height;
            Font font = UIManager.getFont("Label.font");
            try {
                UIManager.put("defaultFont", font.deriveFont(40f));
                status.refreshTheme();
                int text = status.getFontMetrics(dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.CAPTION)).getHeight();
                assertThat(status.getPreferredSize().height).as("a large UI font grows the bar").isGreaterThanOrEqualTo(text)
                    .isGreaterThan(normal);
            } finally {
                UIManager.put("defaultFont",original.get("defaultFont"));
                status.refreshTheme();
            }
            assertThat(status.getPreferredSize().height).isEqualTo(UIScale.scale(20));
        });
    }

    @Test void statusContentsAreCenteredBetweenWorkspaceSurfacesAndWindowBottom() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            for (boolean themeDark : new boolean[]{true, false}) {
                owner.selectVariant(themeDark);
                for (int height : new int[]{20, 24, 48}) {
                    owner.setStatusBarHeight(height); owner.setSize(1000, 600);
                    dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
                    int top = SwingUtilities.convertPoint(owner.status(), 0, 0, owner).y;
                    var grid = owner.tabStrip();
                    int surfaceBottom = SwingUtilities.convertPoint(grid, 0, grid.getHeight(), owner).y;
                    assertThat(surfaceBottom).as("no extra chrome gutter above the status bar").isEqualTo(top);
                    var crumbs = WindowTabsTest.named(owner.status(), "pathBreadcrumbs");
                    JLabel folder = Arrays.stream(crumbs.getComponents()).filter(JLabel.class::isInstance).map(JLabel.class::cast)
                        .filter(label -> label.getIcon() != null).findFirst().orElseThrow();
                    var insets = folder.getInsets();
                    var view = new java.awt.Rectangle(insets.left, insets.top, folder.getWidth() - insets.left - insets.right,
                        folder.getHeight() - insets.top - insets.bottom);
                    var icon = new java.awt.Rectangle(); var text = new java.awt.Rectangle();
                    SwingUtilities.layoutCompoundLabel(folder, folder.getFontMetrics(folder.getFont()), folder.getText(), folder.getIcon(),
                        folder.getVerticalAlignment(), folder.getHorizontalAlignment(), folder.getVerticalTextPosition(),
                        folder.getHorizontalTextPosition(), view, icon, text, folder.getIconTextGap());
                    int iconTop = SwingUtilities.convertPoint(folder, 0, icon.y, owner).y;
                    assertThat(Math.abs((iconTop - surfaceBottom) - (owner.getHeight() - iconTop - icon.height)))
                        .as("breadcrumb icon has even space above and below").isLessThanOrEqualTo(1);
                }
            }
            var left = new JPanel(); var bottom = new JPanel();
            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, left, 250);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            int top = SwingUtilities.convertPoint(owner.status(), 0, 0, owner).y;
            assertThat(SwingUtilities.convertPoint(left, 0, left.getHeight(), owner).y).as("alone, a side reaches the status bar").isEqualTo(top);
            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.BOTTOM_LEFT, bottom, 150);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(SwingUtilities.convertPoint(bottom, 0, bottom.getHeight(), owner).y).isEqualTo(top);
            assertThat(SwingUtilities.convertPoint(left, 0, left.getHeight(), owner).y).as("above the bottom area, a side ends at its divider")
                .isEqualTo(SwingUtilities.convertPoint(bottom.getParent(), 0, 0, owner).y - UIScale.scale(6));
            var parent = bottom.getParent();
            owner.setStatusVisible(false); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(bottom.getParent()).isSameAs(parent);
            assertThat(SwingUtilities.convertPoint(bottom, 0, bottom.getHeight(), owner).y).isEqualTo(owner.getHeight() - UIScale.scale(6));
            owner.setStatusVisible(true); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(SwingUtilities.convertPoint(bottom, 0, bottom.getHeight(), owner).y).isEqualTo(top);
        });
    }

    @Test void theWindowAppliesLiveHeightsAndRejectsValuesOutsideTheRange() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var changes = new AtomicInteger(); owner.onMinimumSizeChanged = changes::incrementAndGet;
            assertThat(owner.toolbarHeight()).isEqualTo(30);
            assertThat(owner.statusBarHeight()).isEqualTo(24);
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
            for (int height : new int[]{27, 65, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
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
            assertThat(fresh.statusBarHeight()).isEqualTo(24);
        });
    }

    @Test void savedHeightsApplyOnEveryReload() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var saved = ConfigSnapshot.defaults().toBuilder().toolbarHeight(40).statusBarHeight(26).build();
            owner.applyConfiguration(saved, true);
            assertThat(owner.toolbarHeight()).isEqualTo(40);
            assertThat(owner.statusBarHeight()).isEqualTo(26);
            owner.setToolbarHeight(50); owner.setStatusBarHeight(34);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).build(), true);
            assertThat(owner.toolbarHeight()).as("the saved height is the only height").isEqualTo(40);
            assertThat(owner.statusBarHeight()).isEqualTo(26);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).toolbarHeight(44).statusBarHeight(22).build(), true);
            assertThat(owner.toolbarHeight()).isEqualTo(44);
            assertThat(owner.statusBarHeight()).isEqualTo(22);
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
        });
    }

    /** Drives one size dialog through OK with a typed value, Cancel, Reset and an out-of-range entry. */
    private static void layOut(JToolBar toolbar) {
        toolbar.setSize(UIScale.scale(1200), toolbar.getPreferredSize().height);
        toolbar.doLayout();
    }

    private static JButton button(JToolBar toolbar, String label) {
        return Arrays.stream(toolbar.getComponents())
            .filter(child -> child instanceof JButton button && label.equals(button.getClientProperty("label")))
            .map(JButton.class::cast).findFirst().orElseThrow();
    }

}
