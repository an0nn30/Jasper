package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.accessibility.AccessibleState;
import javax.swing.JMenuItem;
import javax.swing.JToolTip;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spec 10.5 and the Workstation mock: classic editor tabs. */
class ClassicTabsTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WorkspaceChrome classic() {
        return WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.CLASSIC, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD), new TitleFormat("{context}"));
    }

    static ClassicTabs tabs(WindowContent owner, int width) {
        var strip = (ClassicTabs) owner.tabBar();
        strip.setSize(width, strip.getPreferredSize().height);
        LayoutTestSupport.layoutTree(strip);
        return strip;
    }

    @Test void oneTabStillShowsTheStrip() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.update();
                var strip = tabs(owner, 800);
                assertThat(strip.isVisible()).as("always visible").isTrue();
                assertThat(strip.getPreferredSize().height).as("window.tab_height with its rule").isEqualTo(owner.tabHeight());
                assertThat(strip.entries()).hasSize(1);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void tabsAreContentSizedWithTheMocksGeometry() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.currentTab().rename("Local: ~");
                owner.newTab(DesktopTestSupport.HOME);
                owner.currentTab().rename("Mac: ~");
                owner.update();
                var strip = tabs(owner, 800);
                var first = strip.entries().get(0);
                var second = strip.entries().get(1);
                int text = first.getFontMetrics(first.getFont()).stringWidth("Local: ~");
                assertThat(first.getWidth()).as("12, dot 7, 9, label, 9, x 7, 10").isEqualTo(12 + 7 + 9 + text + 9 + 7 + 10);
                assertThat(second.getX()).as("a 1 px divider between").isEqualTo(first.getWidth() + 1);
                assertThat(strip.add().getX()).as("the + follows the last divider").isEqualTo(second.getX() + second.getWidth() + 1);
                BufferedImage image = MockProfile.print(strip, 800, owner.tabHeight());
                var palette = themes.current().chrome().semantic();
                int bottom = 2 * owner.tabHeight() - 1;
                assertThat(MockProfile.at(image, 2 * (second.getX() + 20), bottom - 3)).as("the active tab's underline")
                    .isEqualTo(palette.color(SemanticColor.TAB_INDICATOR));
                assertThat(MockProfile.at(image, 2 * (second.getX() + 20), 10)).as("the active tab on surface.card")
                    .isEqualTo(palette.color(SemanticColor.SURFACE_CARD));
                assertThat(MockProfile.at(image, 2 * (first.getX() + 2), 10)).as("an inactive tab on the strip")
                    .isEqualTo(palette.color(SemanticColor.SURFACE_TABSTRIP));
                assertThat(MockProfile.at(image, 2 * first.getWidth(), 10)).as("the divider")
                    .isEqualTo(palette.color(SemanticColor.BORDER_WINDOW));
                assertThat(MockProfile.at(image, 2 * (strip.add().getX() + 30), bottom)).as("the strip's rule")
                    .isEqualTo(palette.color(SemanticColor.BORDER_WINDOW));
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void manyTabsShrinkTheirLabelsAndKeepTheirClose() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                for (int i = 0; i < 5; i++) { owner.newTab(DesktopTestSupport.HOME); owner.currentTab().rename("ssh build-" + i + " tail -f /var/log/app.log"); }
                owner.update();
                var strip = tabs(owner, 600);
                var last = strip.entries().getLast();
                assertThat(last.getX() + last.getWidth()).as("every tab fits").isLessThanOrEqualTo(600 - strip.add().getWidth());
                assertThat(strip.add().getX() + strip.add().getWidth()).as("the + stays inside the strip").isLessThanOrEqualTo(600);
                for (var entry : strip.entries()) {
                    var close = entry.closeBounds();
                    assertThat(close.x).as("the x stays past the dot (12, dot 7, 9)").isGreaterThanOrEqualTo(12 + 7 + 9);
                    assertThat(close.x + close.width).as("the x stays inside its tab").isLessThanOrEqualTo(entry.getWidth());
                }
                assertThat(last.getWidth()).as("a long label is drawn shortened").isLessThan(last.chromeWidth() + last.labelWidth());
                assertThat(ClassicTabs.cap(new int[] {40, 200, 200}, 300, 24)).as("the long labels shorten first").isEqualTo(130);
                assertThat(ClassicTabs.cap(new int[] {40, 200, 200}, 30, 24)).as("never below the minimum").isEqualTo(24);
                int before = owner.tabStrip().getTabCount();
                strip.entries().get(0).closeClicked();
                assertThat(owner.tabStrip().getTabCount()).isEqualTo(before - 1);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void tooltipsTheTabListAndAccessibilityStayLiteralAndBounded() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                String title = "<html><img src=http://example.invalid/x.png>" + "x".repeat(1000);
                owner.currentTab().rename(title);
                owner.update();
                var strip = tabs(owner, 800);
                var entry = strip.entries().get(0);
                JToolTip tip = entry.createToolTip();
                tip.setTipText(entry.getToolTipText());
                assertThat(tip.getClientProperty(BasicHTML.propertyKey)).as("the tip renders the title literally").isNull();
                assertThat(entry.getToolTipText()).hasSizeLessThanOrEqualTo(210).startsWith("<html><img").contains("\u2026");
                assertThat(entry.labelWidth()).as("the measured label is bounded")
                    .isEqualTo(entry.getFontMetrics(entry.getFont()).stringWidth(WindowTabs.capped(title)));
                var item = (JMenuItem) strip.tabList().getComponent(0);
                assertThat(item.getText()).hasSizeLessThanOrEqualTo(201).startsWith("<html><img").endsWith("\u2026");
                owner.currentTab().rename("short");
                owner.update();
                String shortcut = owner.tabShortcut(0);
                assertThat(entry.getToolTipText()).as("the tip carries the shortcut")
                    .isEqualTo(shortcut.isEmpty() ? "short" : "short (" + shortcut + ")");
                assertThat(((JMenuItem) strip.tabList().getComponent(0)).getText()).isEqualTo("short");
                var context = entry.getAccessibleContext();
                assertThat(context.getAccessibleName()).isEqualTo("short");
                assertThat(context.getAccessibleStateSet().contains(AccessibleState.SELECTED)).as("the selected tab says so").isTrue();
                owner.newTab(DesktopTestSupport.HOME);
                owner.update();
                assertThat(strip.entries().get(0).getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.SELECTED))
                    .as("an unselected tab does not").isFalse();
                assertThat(strip.entries().get(1).getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.SELECTED)).isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void theDefaultLookKeepsTheEqualTabs() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), dev.jasper.app.appearance.ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            assertThat(owner.tabBar()).isInstanceOf(WindowTabs.class).isSameAs(owner.windowTabs());
        });
    }
}
