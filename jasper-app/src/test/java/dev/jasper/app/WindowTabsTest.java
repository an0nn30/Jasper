package dev.jasper.app;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowTabsTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void standardHeadersCloseSelectAndReorderRealTabs() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var first = owner.currentTab(); first.rename("first"); owner.newTab(HOME);
            var second = owner.currentTab(); second.rename("second"); owner.update();
            owner.setSize(800, 500); MockUiTest.layoutTree(owner);
            JComponent one = named(owner.tabStrip(), "select:first");
            JComponent two = named(owner.tabStrip(), "select:second");
            mouse(one, MouseEvent.MOUSE_PRESSED, 2, 2, MouseEvent.BUTTON1);
            assertThat(owner.currentTab()).isSameAs(first);
            Point target = SwingUtilities.convertPoint(two, 2, 2, one);
            mouse(one, MouseEvent.MOUSE_RELEASED, target.x, target.y, MouseEvent.BUTTON1);
            assertThat(owner.tabStrip().getComponentAt(1)).isSameAs(first);
            mouse(two, MouseEvent.MOUSE_PRESSED, 2, 2, MouseEvent.BUTTON2);
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
            assertThat(owner.currentTab()).isSameAs(first);
            ((JButton) named(owner.tabStrip(), "close:first")).doClick();
            assertThat(owner.tabStrip().getTabCount()).isZero();
        });
    }

    @Test void titlesRemainLiteralAndCloseControlsSurviveLafChanges() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var first = owner.currentTab(); first.rename("first"); owner.update();
            var header = owner.tabStrip().getTabComponentAt(0);
            first.rename("<html>literal title"); owner.update();
            owner.selectLaf(UiLookAndFeel.NIMBUS);
            assertThat(owner.tabStrip().getTabComponentAt(0)).isSameAs(header);
            var label = named(owner.tabStrip(), "select:<html>literal title");
            assertThat(label.getClientProperty("html.disable")).isEqualTo(true);
            assertThat(label.getToolTipText()).isEqualTo("<html>literal title");
            ((JButton) named(owner.tabStrip(), "close:<html>literal title")).doClick();
            assertThat(owner.tabStrip().getTabCount()).isZero();
        });
    }

    @Test void nativeTabPaddingAndCloseButtonsRetainMiddleClickAndDrag() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var first = owner.currentTab(); first.rename("first"); owner.newTab(HOME);
            var second = owner.currentTab(); second.rename("second"); owner.update();
            owner.selectTab(first); owner.setSize(800, 500); MockUiTest.layoutTree(owner);
            var tabs = owner.tabStrip();
            var secondBounds = tabs.getBoundsAt(1);
            mouse(tabs, MouseEvent.MOUSE_PRESSED, secondBounds.x + 2, secondBounds.y + 2, MouseEvent.BUTTON2);
            assertThat(tabs.getTabCount()).isEqualTo(1);
            assertThat(owner.currentTab()).isSameAs(first);
            owner.newTab(HOME); owner.currentTab().rename("third"); owner.update(); MockUiTest.layoutTree(owner);
            var from = tabs.getBoundsAt(0); var to = tabs.getBoundsAt(1);
            mouse(tabs, MouseEvent.MOUSE_PRESSED, from.x + 2, from.y + 2, MouseEvent.BUTTON1);
            mouse(tabs, MouseEvent.MOUSE_RELEASED, to.x + 2, to.y + 2, MouseEvent.BUTTON1);
            assertThat(tabs.getComponentAt(1)).isSameAs(first);
            mouse(named(tabs, "close:third"), MouseEvent.MOUSE_PRESSED, 2, 2, MouseEvent.BUTTON2);
            assertThat(tabs.getTabCount()).isEqualTo(1);
            assertThat(owner.currentTab()).isSameAs(first);
        });
    }

    @Test void lafReplacementCannotEnableSelectedTabMouseFocusRequests() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            // BasicTabbedPaneUI requests mouse focus on a selected tab unless this is false.
            for (var laf : new UiLookAndFeel[]{UiLookAndFeel.METAL, UiLookAndFeel.NIMBUS, UiLookAndFeel.MOTIF}) {
                owner.selectLaf(laf);
                assertThat(owner.tabStrip().isRequestFocusEnabled()).isFalse();
            }
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
