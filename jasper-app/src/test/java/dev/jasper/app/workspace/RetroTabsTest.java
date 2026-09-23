package dev.jasper.app.workspace;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.*;
import org.junit.jupiter.api.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;
class RetroTabsTest {
 @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }
@Test void metalTabsKeepTheirModelAndHeaderIdentityAcrossUpdates() throws Exception {
    edt(() -> {
        var owner = content(launcher(new java.util.ArrayDeque<>()),
            new ThemeController(ThemeStyle.RETRO, Appearance.DARK));
        var deck = owner.tabStrip();
        assertThat(deck.getUI()).isInstanceOf(javax.swing.plaf.metal.MetalTabbedPaneUI.class);
        assertThat(owner.windowTabs()).isNull();
        var first = owner.currentTab();
        var pane = owner.currentPane();
        var header = deck.getTabComponentAt(0);
        assertThat(header).isNotNull();
        first.rename("<html>literal & long title"); owner.update();
        owner.newTab(HOME);
        var second = owner.currentTab();
        owner.reorderTab(0, 1);
        assertThat(deck.getTabComponentAt(1)).isSameAs(header);
        owner.selectTab(first);
        assertThat(owner.currentPane()).isSameAs(pane);
        assertThat(deck.getTitleAt(1)).isEqualTo("<html>literal & long title");
        var label = (javax.swing.JLabel) ((javax.swing.JPanel) header).getComponent(0);
        assertThat(label.getClientProperty("html.disable")).isEqualTo(true);
        assertThat(label.getToolTipText()).isEqualTo(first.title());
        var close = (javax.swing.JButton) ((javax.swing.JPanel) deck.getTabComponentAt(0)).getComponent(2);
        assertThat(close.isBorderPainted()).isFalse();
        assertThat(close.isContentAreaFilled()).isFalse();
        assertThat(close.getPreferredSize().width).isEqualTo(20);
        assertThat(close.getPreferredSize().height).isEqualTo(20);
        close.doClick();
        assertThat(deck.getTabCount()).isEqualTo(1);
        assertThat(owner.currentTab()).isSameAs(first);
        assertThat(deck.indexOfComponent(second)).isEqualTo(-1);
    });
}

@Test void retroTabsUseMouseGesturesAndScrollForOverflow() throws Exception {
    edt(() -> {
        var owner = content(launcher(new java.util.ArrayDeque<>()),
            new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT));
        var first = owner.currentTab();
        owner.newTab(HOME);
        var deck = owner.tabStrip();
        deck.setSize(650, 300); deck.doLayout();
        var header = (javax.swing.JComponent) deck.getTabComponentAt(0);
        var target = deck.getBoundsAt(1);
        var end = javax.swing.SwingUtilities.convertPoint(deck, target.x + target.width / 2,
            target.y + target.height / 2, header);
        header.dispatchEvent(new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_PRESSED,
            1L, 0, 4, 4, 1, false, java.awt.event.MouseEvent.BUTTON1));
        header.dispatchEvent(new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_RELEASED,
            2L, 0, end.x, end.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        assertThat(deck.getComponentAt(1)).isSameAs(first);
        assertThat(owner.currentTab()).isSameAs(first);
        header.dispatchEvent(new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_PRESSED,
            3L, 0, 4, 4, 1, false, java.awt.event.MouseEvent.BUTTON2));
        assertThat(deck.indexOfComponent(first)).isEqualTo(-1);
        for (int i = 0; i < 20; i++) owner.newTab(HOME);
        assertThat(deck.getTabLayoutPolicy()).isEqualTo(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT);
    });
}

    @Test void closingATabDuringDragIgnoresTheLateRelease() throws Exception {
        edt(() -> {
            var owner = content(launcher(new java.util.ArrayDeque<>()), new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT));
            var first = owner.currentTab();
            owner.newTab(HOME); var survivor = owner.currentTab();
            var deck = owner.tabStrip(); deck.setSize(650, 300); deck.doLayout();
            var header = (javax.swing.JComponent) deck.getTabComponentAt(0);
            header.dispatchEvent(new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_PRESSED,
                1L, 0, 4, 4, 1, false, java.awt.event.MouseEvent.BUTTON1));
            owner.closeTab(first);
            header.dispatchEvent(new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_RELEASED,
                2L, 0, 150, 4, 1, false, java.awt.event.MouseEvent.BUTTON1));
            assertThat(deck.getTabCount()).isEqualTo(1);
            assertThat(owner.currentTab()).isSameAs(survivor);
        });
    }
    @Test void rootKeepsNativeDecorationsAndTitleUpdates() throws Exception {
        edt(() -> {
            var owner = content(launcher(new java.util.ArrayDeque<>()), new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT));
            var root = new javax.swing.JRootPane();
            var titles = new java.util.ArrayList<String>();
            assertThat(WindowContent.installTitleBar(root, owner, true, titles::add)).isNull();
            assertThat(root.getContentPane()).isSameAs(owner);
            assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isNull();
            owner.currentTab().rename("Retro title"); owner.update();
            assertThat(titles.getLast()).contains("Retro title");
        });
    }
}
