package dev.moray.app;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowTabsTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void singleVisibleHeaderSelectsClosesAndAddsRealTabs() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            assertThat(strip).as("integrated tab strip").isNotNull();
            assertThat(strip.getPreferredSize().height).isEqualTo(38);
            var first = owner.currentTab();
            first.rename("first"); owner.newTab(HOME);
            var second = owner.currentTab(); second.rename("second"); owner.update();
            ((AbstractButton) named(strip, "select:first")).doClick();
            assertThat(owner.currentTab()).isSameAs(first);
            ((AbstractButton) named(strip, "close:second")).doClick();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
            assertThat(owner.currentTab()).isSameAs(first);
            ((AbstractButton) named(strip, "newTab")).doClick();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            assertThat(owner.currentTab()).isNotSameAs(first);
            assertThat(owner.tabStrip().getTabComponentAt(0)).isNull();
            owner.tabStrip().setSize(600, 300); owner.tabStrip().doLayout();
            assertThat(owner.tabStrip().getComponentAt(1).getY()).isLessThanOrEqualTo(1);
        });
    }

    @Test void controlsRetainIdentityAcrossMetadataSelectionReorderAndTheme() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            assertThat(strip).isNotNull();
            var first = owner.currentTab(); first.rename("first"); owner.update();
            JComponent control = named(strip, "select:first");
            owner.newTab(HOME); var second = owner.currentTab();
            owner.reorderTab(1, 0);
            assertThat(owner.currentTab()).isSameAs(second);
            first.rename("<html>literal title"); owner.update();
            owner.selectTheme(BuiltinTheme.LIGHT);
            assertThat(named(strip, "select:<html>literal title")).isSameAs(control);
            assertThat(control.getClientProperty("html.disable")).isEqualTo(true);
            assertThat(control.getToolTipText()).isEqualTo("<html>literal title");
            assertThat(owner.currentTab()).isSameAs(second);
            owner.tabStrip().setSize(600, 300); owner.tabStrip().doLayout();
            assertThat(second.getY()).isLessThanOrEqualTo(1);
        });
    }

    @Test void middleClickAndDragUseTheSameSelectionAndOrderModel() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            assertThat(strip).isNotNull();
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

    @Test void overflowAndLongTitlesRemainBoundedAndKeyboardRevealsSelection() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            assertThat(strip).isNotNull();
            Dimension minimum = strip.getMinimumSize();
            for (int i = 0; i < 12; i++) {
                owner.currentTab().rename("long name ".repeat(100) + i); owner.newTab(HOME);
            }
            owner.update(); layout(strip, 330, 38);
            assertThat(strip.getMinimumSize()).isEqualTo(minimum);
            assertThat(strip.getPreferredSize().width).isLessThanOrEqualTo(800);
            assertThat(named(strip, "previousTabs").isVisible()).isTrue();
            assertThat(named(strip, "nextTabs").isVisible()).isTrue();
            owner.invoke(ActionId.SELECT_TAB_1); layout(strip, 330, 38);
            JComponent selected = named(strip, "select:" + owner.currentTab().title());
            assertThat(selected.isShowing()).isFalse(); // no native window in this test
            assertThat(selected.getParent().isVisible()).isTrue();
            ((AbstractButton) named(strip, "nextTabs")).doClick(); layout(strip, 330, 38);
            assertThat(selected.getParent().isVisible()).isFalse();
            owner.invoke(ActionId.NEXT_TAB); layout(strip, 330, 38);
            assertThat(named(strip, "select:" + owner.currentTab().title()).getParent().isVisible()).isTrue();
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

    @Test void disposedOwnerDisablesTheRetainedNewTabControl() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            owner.close();
            assertThat(named(strip, "newTab").isEnabled()).isFalse();
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
}
