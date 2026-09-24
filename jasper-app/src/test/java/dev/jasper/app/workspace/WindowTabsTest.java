package dev.jasper.app.workspace;

import dev.jasper.app.appearance.BuiltinTheme;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowTabsTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void stripIsAlwaysVisibleAndSelectsAndClosesRealTabs() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            assertThat(strip).isNotNull();
            assertThat(strip.getPreferredSize().height).isEqualTo(38);
            assertThat(strip.isVisible()).as("a lone tab still shows, as in IntelliJ").isTrue();
            var first = owner.currentTab(); first.rename("first"); owner.newTab(HOME);
            var second = owner.currentTab(); second.rename("second"); owner.update();
            ((AbstractButton) named(strip, "select:first")).doClick();
            assertThat(owner.currentTab()).isSameAs(first);
            ((AbstractButton) named(strip, "close:second")).doClick();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
            assertThat(strip.isVisible()).isTrue();
            assertThat(named(strip, "newTab")).as("New tab lives in the toolbar").isNull();
            owner.tabStrip().setSize(600, 300); owner.tabStrip().doLayout();
            assertThat(owner.tabStrip().getComponentAt(0).getY()).isLessThanOrEqualTo(1);
        });
    }

    @Test void tabsAreContentSizedLeftAlignedWithIconTitleAndTrailingClose() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second");
            var strip = owner.windowTabs(); layout(strip, 800, 38);
            var first = (AbstractButton) named(strip, "select:first");
            var second = (AbstractButton) named(strip, "select:second");
            assertThat(first.getIcon()).isNotNull();
            assertThat(first.getIcon().getIconWidth()).isEqualTo(16);
            assertThat(first.getHorizontalAlignment()).isEqualTo(SwingConstants.LEADING);
            Container one = first.getParent(), two = second.getParent();
            assertThat(one.getX()).isZero();
            assertThat(two.getX()).isEqualTo(one.getWidth());
            assertThat(one.getWidth()).isBetween(80, 240);
            assertThat(one.getWidth() + two.getWidth()).isLessThan(800 - 24);
            var close = named(strip, "close:second");
            assertThat(close.isVisible()).as("the selected tab always offers close").isTrue();
            assertThat(close.getX()).isGreaterThanOrEqualTo(second.getX() + second.getWidth());
            var otherClose = named(strip, "close:first");
            assertThat(otherClose.isVisible()).isFalse();
            mouse(first, MouseEvent.MOUSE_ENTERED, 10, 10, MouseEvent.NOBUTTON);
            assertThat(otherClose.isVisible()).isTrue();
            mouse(first, MouseEvent.MOUSE_EXITED, -100, -100, MouseEvent.NOBUTTON);
            assertThat(otherClose.isVisible()).isFalse();
            assertThat(named(strip, "tabList").getBounds()).isEqualTo(new Rectangle(776, 0, 24, 38));
        });
    }

    @Test void tooltipsCarryTheTitleAndTheLiveSelectShortcut() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            var strip = owner.windowTabs();
            assertThat(named(strip, "select:second").getToolTipText()).isEqualTo("second (⌘2)");
            owner.setBindings(KeyBindings.withOverrides(true, Map.of("select_tab_2", "ctrl+alt+2")));
            assertThat(named(strip, "select:second").getToolTipText()).isEqualTo("second (⌃⌥2)");
            owner.setBindings(KeyBindings.withOverrides(true, Map.of("select_tab_2", "none")));
            assertThat(named(strip, "select:second").getToolTipText()).isEqualTo("second");
        });
    }

    @Test void selectedTabPaintsAnAccentUnderlineThatGreysWhenInactiveAndFollowsTheTheme() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            var strip = owner.windowTabs(); layout(strip, 800, 38);
            Container selected = named(strip, "select:second").getParent();
            Container other = named(strip, "select:first").getParent();
            assertThat(pixel(selected, 36)).isEqualTo(UIManager.getColor("Jasper.tabUnderline"));
            assertThat(pixel(selected, 4)).isEqualTo(UIManager.getColor("Jasper.tabSelectedBackground"));
            assertThat(pixel(other, 36)).isNotEqualTo(UIManager.getColor("Jasper.tabUnderline"));
            owner.setActive(false);
            assertThat(pixel(selected, 36)).isEqualTo(UIManager.getColor("Jasper.tabUnderlineInactive"));
            owner.setActive(true); owner.selectTheme(BuiltinTheme.LIGHT); layout(strip, 800, 38);
            assertThat(pixel(selected, 36)).isEqualTo(UIManager.getColor("Jasper.tabUnderline"));
        });
    }

    @Test void controlsRetainIdentityAcrossMetadataSelectionReorderAndTheme() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            var first = owner.currentTab(); first.rename("first"); owner.update();
            JComponent control = named(strip, "select:first");
            owner.newTab(HOME); var second = owner.currentTab();
            owner.reorderTab(1, 0);
            assertThat(owner.currentTab()).isSameAs(second);
            first.rename("<html>literal title"); owner.update();
            owner.selectTheme(BuiltinTheme.LIGHT);
            assertThat(named(strip, "select:<html>literal title")).isSameAs(control);
            assertThat(control.getClientProperty("html.disable")).isEqualTo(true);
            assertThat(control.getToolTipText()).startsWith("<html>literal title");
            assertThat(owner.currentTab()).isSameAs(second);
        });
    }

    @Test void middleClickAndDragUseTheSameSelectionAndOrderModel() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            var first = owner.currentTab(); first.rename("first"); owner.newTab(HOME);
            var second = owner.currentTab(); second.rename("second"); owner.update();
            layout(strip, 600, 38);
            JComponent one = named(strip, "select:first"), two = named(strip, "select:second");
            Point target = SwingUtilities.convertPoint(two, 10, 10, one);
            mouse(one, MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON1);
            mouse(one, MouseEvent.MOUSE_RELEASED, target.x, target.y, MouseEvent.BUTTON1);
            assertThat(owner.tabStrip().getComponentAt(1)).isSameAs(first);
            assertThat(owner.currentTab()).isSameAs(first);
            mouse(two, MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON2);
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
            assertThat(owner.currentTab()).isSameAs(first);
        });
    }

    @Test void overflowScrollsKeepsSelectionVisibleAndTheListReachesEveryLiteralTitle() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var tabs = owner.windowTabs();
            Dimension minimum = tabs.getMinimumSize();
            for (int i = 0; i < 12; i++) { owner.currentTab().rename("<b>long name ".repeat(100) + i); owner.newTab(HOME); }
            owner.update(); layout(tabs, 330, 38);
            assertThat(tabs.getMinimumSize()).isEqualTo(minimum);
            for (Component child : tabs.getComponents())
                if (child.isVisible()) assertThat(child.getX() + child.getWidth()).isLessThanOrEqualTo(330);
            owner.invoke(ActionId.SELECT_TAB_1); layout(tabs, 330, 38);
            JComponent selected = named(tabs, "select:" + owner.currentTab().title());
            assertThat(selected.getParent().isVisible()).isTrue();
            tabs.scrollTabs(1); layout(tabs, 330, 38);
            assertThat(selected.getParent().isVisible()).isFalse();
            owner.invoke(ActionId.NEXT_TAB); layout(tabs, 330, 38);
            assertThat(named(tabs, "select:" + owner.currentTab().title()).getParent().isVisible()).isTrue();
            JPopupMenu list = tabs.tabList();
            assertThat(list.getComponentCount()).isEqualTo(13);
            var item = (JMenuItem) list.getComponent(3);
            assertThat(item.getClientProperty("html.disable")).isEqualTo(true);
            assertThat(item.getText()).startsWith("<b>long name");
            ((JMenuItem) list.getComponent(12)).doClick(); layout(tabs, 330, 38);
            assertThat(owner.tabStrip().getSelectedIndex()).isEqualTo(12);
            assertThat(named(tabs, "select:" + owner.currentTab().title()).getParent().isVisible()).isTrue();
        });
    }

    @Test void narrowingTheWindowKeepsTheSelectedTabVisible() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            for (int i = 0; i < 4; i++) owner.newTab(HOME);
            owner.currentTab().rename("selected"); owner.update();
            layout(strip, 850, 38);
            assertThat(named(strip, "select:selected").getParent().isVisible()).isTrue();
            layout(strip, 250, 38);
            assertThat(named(strip, "select:selected").getParent().isVisible()).isTrue();
        });
    }

    @Test void closedOwnerDisablesTheTabList() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            owner.close();
            assertThat(named(strip, "tabList").isEnabled()).isFalse();
        });
    }

    static JComponent named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComponent component && name.equals(component.getName())) return component;
            if (child instanceof Container container) { JComponent found = named(container, name); if (found != null) return found; }
        }
        return null;
    }
    static void layout(Container component, int width, int height) {
        component.setSize(width, height); layoutTree(component);
    }
    static void layoutTree(Container component) {
        component.doLayout();
        for (Component child : component.getComponents()) if (child instanceof Container container) layoutTree(container);
    }
    private static void mouse(Component component, int id, int x, int y, int button) {
        component.dispatchEvent(new MouseEvent(component, id, 1, 0, x, y, 1, false, button));
    }
    private static Color pixel(Container entry, int y) {
        var image = new BufferedImage(entry.getWidth(), entry.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { entry.paint(g); } finally { g.dispose(); }
        return new Color(image.getRGB(2, y));
    }
}
