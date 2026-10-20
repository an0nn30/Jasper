package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.accessibility.AccessibleRole;
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
            WorkspaceLook.Tabs.CLASSIC, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD,
            WorkspaceLook.Icons.NEW_UI), new TitleFormat("{context}"));
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
                owner.setSize(800, 600);
                LayoutTestSupport.layoutTree(owner);
                var strip = (ClassicTabs) owner.tabBar();
                assertThat(strip.isVisible()).as("the classic tabs show for a window's only tab").isTrue();
                assertThat(strip.entries()).hasSize(1);
                assertThat(owner.tabBar().component().getHeight()).as("the mounted strip at one tab: window.tab_height with its rule")
                    .isEqualTo(owner.tabHeight());
                assertThat(strip.getWidth()).as("across the terminal column").isPositive();
                assertThat(strip.entries().getFirst().getWidth()).as("its one tab laid out").isPositive();
                owner.newTab(DesktopTestSupport.HOME);
                owner.closeTab(owner.selectedTab());
                assertThat(strip.isVisible()).as("and again when the count drops back to one").isTrue();
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    /** IBM Plex has no "●" and no CJK: such a title takes the platform face (plan 5b, ruling 26); a Latin one keeps Plex. */
    @Test void aTitleIbmPlexCannotDrawTakesThePlatformFace() throws Exception {
        EnterpriseResources.assumePlexResolves();
        String title = "\u25CF \u5831\u544A";
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.currentTerminal().rename("Local: ~");
                owner.newTab(DesktopTestSupport.HOME);
                owner.currentTerminal().rename(title);
                owner.update();
                var strip = tabs(owner, 800);
                assertThat(strip.entries().get(0).getFont().getFontName(java.util.Locale.ROOT)).as("a Latin title").startsWith("IBM Plex Sans");
                assertThat(strip.entries().get(1).getFont()).as("the platform face")
                    .isEqualTo(ThemeZone.font(Typography.Role.BODY, title)).isNotEqualTo(ThemeZone.font(Typography.Role.BODY));
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void tabsAreContentSizedWithTheMocksGeometry() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.currentTerminal().rename("Local: ~");
                owner.newTab(DesktopTestSupport.HOME);
                owner.currentTerminal().rename("Mac: ~");
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
                for (int i = 0; i < 5; i++) { owner.newTab(DesktopTestSupport.HOME); owner.currentTerminal().rename("ssh build-" + i + " tail -f /var/log/app.log"); }
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
                int before = owner.tabCount();
                strip.entries().get(0).closeClicked();
                assertThat(owner.tabCount()).isEqualTo(before - 1);

                // Past the minimum the strip clips its last tabs, and the + stays at its trailing edge, over them.
                for (int i = owner.tabCount(); i < 15; i++) {
                    owner.newTab(DesktopTestSupport.HOME); owner.currentTerminal().rename("ssh build-" + i + " tail -f /var/log/app.log");
                }
                owner.update();
                tabs(owner, 600);
                var plus = strip.add();
                assertThat(strip.entries().getLast().getX()).as("the last tabs clip").isGreaterThan(600);
                assertThat(plus.getX() + plus.getWidth()).as("a crowded strip keeps the + inside, at its trailing edge").isEqualTo(600);
                assertThat(strip.getComponentAt(plus.getX() + plus.getWidth() / 2, plus.getHeight() / 2)).as("the + lies over the clipped tabs")
                    .isSameAs(plus);
                for (var entry : strip.entries()) {
                    if (entry.getX() + entry.getWidth() > plus.getX()) continue;
                    var close = entry.closeBounds();
                    assertThat(strip.getComponentAt(entry.getX() + close.x + close.width / 2, close.y + close.height / 2))
                        .as("a whole tab's x takes the mouse").isSameAs(entry);
                }
                var under = strip.entries().stream().filter(entry -> entry.getBounds().contains(plus.getX() + 3, 4)).findFirst().orElseThrow();
                owner.selectTab(owner.tabs().get(strip.entries().indexOf(under)));
                owner.update();
                BufferedImage image = MockProfile.print(strip, 600, owner.tabHeight());
                var palette = themes.current().chrome().semantic();
                assertThat(MockProfile.at(image, 2 * (under.getX() + 2), 10)).as("the selected tab under the +, where it shows")
                    .isEqualTo(palette.color(SemanticColor.SURFACE_CARD));
                assertThat(MockProfile.at(image, 2 * plus.getX(), 10)).as("the +'s own divider").isEqualTo(palette.color(SemanticColor.BORDER_WINDOW));
                assertThat(MockProfile.at(image, 2 * (plus.getX() + 3), 10)).as("the + hides the clipped tabs under the strip's fill")
                    .isEqualTo(palette.color(SemanticColor.SURFACE_TABSTRIP));
                assertThat(MockProfile.at(image, 2 * (plus.getX() + 1 + 11 + 3), 2 * (plus.getHeight() / 2))).as("the +, 11 after its divider")
                    .isEqualTo(SurfaceContrast.on(palette, SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_TABSTRIP, SurfaceContrast.TEXT));
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void thePlusAndTheCloseActLikeButtons() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.update();
                var strip = tabs(owner, 800);
                var plus = strip.add();
                // A press and a release a pixel apart make no MOUSE_CLICKED; the + still acts.
                mouse(plus, MouseEvent.MOUSE_PRESSED, 5, 5);
                mouse(plus, MouseEvent.MOUSE_RELEASED, 6, 5);
                assertThat(owner.tabCount()).as("the + acts on its release").isEqualTo(2);
                owner.update();
                tabs(owner, 800);
                var first = strip.entries().get(0);
                var close = first.closeBounds();
                int x = close.x + close.width / 2, y = close.y + close.height / 2;
                mouse(first, MouseEvent.MOUSE_PRESSED, x, y);
                assertThat(owner.tabCount()).as("a press on the x closes nothing yet").isEqualTo(2);
                assertThat(owner.selectedTab()).as("nor selects its tab").isSameAs(owner.tabs().get(1));
                mouse(first, MouseEvent.MOUSE_RELEASED, 2, y);
                assertThat(owner.tabCount()).as("dragged off the x before the release, nothing closes").isEqualTo(2);
                mouse(first, MouseEvent.MOUSE_PRESSED, x, y);
                mouse(first, MouseEvent.MOUSE_RELEASED, x + 1, y);
                assertThat(owner.tabCount()).as("released over the x, its tab closes").isEqualTo(1);
                // The x takes the pointer in a square larger than its 7 px glyph, inside its tab.
                owner.newTab(DesktopTestSupport.HOME);
                owner.update();
                tabs(owner, 800);
                var tab = strip.entries().get(0);
                var glyph = tab.closeBounds();
                var target = tab.closeTarget();
                assertThat(target.contains(glyph)).isTrue();
                assertThat(target.width).as("wider than the glyph").isGreaterThan(glyph.width + 8);
                assertThat(target.x + target.width).as("inside the tab").isLessThanOrEqualTo(tab.getWidth());
                int beside = glyph.x + glyph.width + 3;
                mouse(tab, MouseEvent.MOUSE_PRESSED, beside, glyph.y - 3);
                mouse(tab, MouseEvent.MOUSE_RELEASED, beside, glyph.y - 3);
                assertThat(owner.tabCount()).as("a click just beside the glyph closes its tab").isEqualTo(1);

                owner.newTab(DesktopTestSupport.HOME);
                owner.update();
                var action = strip.entries().get(0).getAccessibleContext().getAccessibleAction();
                assertThat(action.getAccessibleActionCount()).isEqualTo(2);
                assertThat(action.getAccessibleActionDescription(1)).isEqualTo("Close tab");
                assertThat(action.doAccessibleAction(0)).isTrue();
                assertThat(owner.selectedTab()).as("the first action selects the tab").isSameAs(owner.tabs().get(0));
                assertThat(action.doAccessibleAction(1)).isTrue();
                assertThat(owner.tabCount()).as("the second is the x").isEqualTo(1);
                var button = plus.getAccessibleContext();
                assertThat(button.getAccessibleRole()).isEqualTo(AccessibleRole.PUSH_BUTTON);
                assertThat(button.getAccessibleName()).isEqualTo("New Tab");
                assertThat(button.getAccessibleAction().getAccessibleActionCount()).as("the + can be pressed").isEqualTo(1);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }

    @Test void tooltipsTheTabListAndAccessibilityStayLiteralAndBounded() throws Exception {
        edt(() -> {
            ThemeController themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                String title = "<html><img src=http://example.invalid/x.png>" + "x".repeat(1000);
                owner.currentTerminal().rename(title);
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
                owner.currentTerminal().rename("short");
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
            owner.setSize(800, 600);
            LayoutTestSupport.layoutTree(owner);
            assertThat(owner.tabBar().component().isVisible()).as("unlike the classic tabs, the equal tabs hide at one tab").isFalse();
            assertThat(owner.tabBar().component().getHeight()).isZero();
        });
    }

    private static void mouse(Component component, int id, int x, int y) {
        component.dispatchEvent(new MouseEvent(component, id, 1, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
