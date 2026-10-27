package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.MacTitleBand;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Parent tabs spec 6: every window's ⋮, at the trailing end of its toolbar. */
class WindowMenuButtonTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static JComponent menuIn(Container parent) { return WindowTabsTest.named(parent, WindowMenuButton.NAME); }

    /** What a click on a ⋮ asked its window to show: the menu, under the ⋮. */
    private record Shown(JComponent invoker, JPopupMenu menu) { }

    /** A menu's entries by their text. */
    private static List<String> texts(JPopupMenu menu) {
        return Arrays.stream(menu.getComponents()).map(item -> ((JMenuItem) item).getText()).toList();
    }

    /** A click on {@code button} opens {@code window}'s window menu at the button; headless, the window records it. */
    private static void assertAClickOpensTheWindowMenu(WindowContent window, JComponent button, String... entries) {
        var shown = new ArrayList<Shown>();
        window.windowMenuPopup = (invoker, menu) -> shown.add(new Shown(invoker, menu.get()));
        ((AbstractButton) button).doClick();
        assertThat(shown).as("one menu").singleElement().satisfies(menu -> {
            assertThat(menu.invoker()).as("at the ⋮").isSameAs(button);
            assertThat(texts(menu.menu())).as("that window's menu").containsExactly(entries).isEqualTo(texts(window.windowMenu()));
        });
    }

    /**
     * Workspace plan 7b Rulings 16: the standard toolbar ends with the ⋮, after Settings, and it never gives way; on a
     * narrow toolbar the overflow, a horizontal ellipsis, sits right before it (Spec issues 3).
     */
    @Test void theStandardToolbarEndsWithTheWindowMenu() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var toolbar = owner.chrome().windowToolbar();
            var buttons = java.util.Arrays.stream(toolbar.getComponents()).filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> button != toolbar.overflowButton()).toList();
            assertThat(buttons.getLast()).isInstanceOf(WindowMenuButton.class);
            JButton menu = buttons.getLast();
            assertThat(menu.getName()).isEqualTo("windowMenu");
            assertThat(menu.getToolTipText()).isEqualTo("View Options");
            assertThat(menu.getIcon()).isSameAs(AppIcons.semantic("more.vertical"));
            assertThat(buttons.get(buttons.size() - 2).getToolTipText()).startsWith("Settings");
            toolbar.setSize(130, 30);
            toolbar.doLayout();
            assertThat(toolbar.overflowButton().isVisible()).as("the other buttons give way").isTrue();
            assertThat(menu.isVisible()).as("the ⋮ never does").isTrue();
            assertThat(menu.getText()).as("and never shows a label").isNull();
            JButton overflow = toolbar.overflowButton();
            assertThat(overflow.getIcon()).as("a horizontal ellipsis").isSameAs(AppIcons.semantic("more"));
            for (var child : toolbar.getComponents()) {
                if (child == menu || !child.isVisible() || !(child instanceof JButton)) continue;
                assertThat(child.getX() + child.getWidth()).as("the ⋮ ends the row").isLessThanOrEqualTo(menu.getX());
                if (child != overflow)
                    assertThat(child.getX() + child.getWidth()).as("the overflow right before it").isLessThanOrEqualTo(overflow.getX());
            }
            assertThat(menu.getAccessibleContext().getAccessibleName()).isEqualTo("View Options");
            assertAClickOpensTheWindowMenu(owner, menu, "Move Tab to New Window");
        });
    }

    /** Workspace plan 7b Rulings 16: the classic row ends with Settings…, then the ⋮, a 22 px classic icon button. */
    @Test void theClassicRowEndsWithSettingsThenTheWindowMenu() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(launcher(new ArrayDeque<>()), themes, ClassicTabsTest.classic());
                var trailing = ((ClassicHeader) owner.chrome().composition()).trailing();
                assertThat(((ClassicToolbarButton) trailing.getComponent(0)).getClientProperty("label")).isEqualTo("Settings…");
                var menu = (ClassicToolbarButton) trailing.getComponent(1);
                assertThat(menu.getName()).isEqualTo("windowMenu");
                assertThat(menu.getText()).as("an icon button").isNull();
                assertThat(menu.getToolTipText()).isEqualTo("View Options");
                assertThat(menu.getAccessibleContext().getAccessibleName()).isEqualTo("View Options");
                owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
                assertThat(menu.getText()).as("labels never reach it").isNull();
                assertAClickOpensTheWindowMenu(owner, menu, "Move Tab to New Window");
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

}
