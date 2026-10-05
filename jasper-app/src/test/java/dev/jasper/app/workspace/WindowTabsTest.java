package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.commands.ActionId;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowTabsTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void oneTabHidesTheBarAndTwoTabsRestoreIt() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var strip = owner.windowTabs();
            assertThat(strip.isVisible()).isFalse();
            assertThat(strip.getPreferredSize().height).isZero();
            owner.invoke(ActionId.NEW_TAB);
            assertThat(strip.isVisible()).isTrue();
            assertThat(strip.getPreferredSize().height).isEqualTo(owner.tabHeight());
            owner.closeTab(owner.currentTab());
            assertThat(strip.isVisible()).isFalse();
            assertThat(strip.getMinimumSize().height).isZero();
            assertThat(named(strip, "newTab")).isNull();
            assertThat(named(strip, "tabList")).isNull();
        });
    }

    @Test void titlesAreCenteredAndCellsDivideEveryPixelRegardlessOfTitleLength() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 0, 0, 0));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second");
            owner.newTab(HOME); owner.currentTab().rename("a long title ".repeat(20)); owner.update();
            var strip = owner.windowTabs();
            for (int width : new int[]{801, 800, 151, 8}) {
                layout(strip, width, 30);
                int edge = 0;
                for (var tab : owner.terminalTabs()) {
                    var label = (AbstractButton) named(strip, "select:" + tab.title());
                    var entry = label.getParent();
                    assertThat(entry.getX()).isEqualTo(edge);
                    assertThat(entry.getWidth()).isBetween(width / 3, (width + 2) / 3);
                    assertThat(label.getHorizontalAlignment()).isEqualTo(SwingConstants.CENTER);
                    assertThat(label.getIcon()).isNull();
                    assertThat(label.getX() * 2 + label.getWidth()).isEqualTo(entry.getWidth());
                    edge += entry.getWidth();
                }
                assertThat(edge).isEqualTo(width);
            }
        });
    }

    @Test void narrowCellsKeepTitleAndTooltipHitAreaWhenCloseCannotFit() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 0, 0, 0));
            for (int i = 0; i < 16; i++) {
                if (i > 0) owner.newTab(HOME);
                owner.currentTab().rename("tab-" + i);
            }
            owner.update(); var strip = owner.windowTabs(); layout(strip, 800, 30);
            for (int i = 0; i < 16; i++) {
                var label = (AbstractButton) named(strip, "select:tab-" + i);
                assertThat(label.getWidth()).as("text space for 50px tab %s", i).isGreaterThanOrEqualTo(40);
                assertThat(label.getToolTipText()).startsWith("tab-" + i);
                assertThat(SwingUtilities.getDeepestComponentAt(strip, i * 50 + 25, 15)).isSameAs(label);
                mouse(label, MouseEvent.MOUSE_ENTERED, 10, 10, MouseEvent.NOBUTTON);
                assertThat(named(strip, "close:tab-" + i).isVisible()).isFalse();
            }
        });
    }

    @Test void hoverCloseDoesNotShiftTheCenteredTitleAndButtonsStillSelectAndClose() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 0, 0, 0));
            var first = owner.currentTab(); first.rename("first");
            owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            var strip = owner.windowTabs(); layout(strip, 800, 30);
            var label = (AbstractButton) named(strip, "select:first");
            var close = (AbstractButton) named(strip, "close:first");
            var before = label.getBounds();
            assertThat(close.isVisible()).isFalse();
            mouse(label, MouseEvent.MOUSE_ENTERED, 10, 10, MouseEvent.NOBUTTON);
            assertThat(close.isVisible()).isTrue();
            assertThat(label.getBounds()).isEqualTo(before);
            label.doClick(); assertThat(owner.currentTab()).isSameAs(first);
            close.doClick(); assertThat(owner.terminalTabs()).doesNotContain(first);
            assertThat(strip.isVisible()).isFalse();
        });
    }

    @Test void selectedCellHasASeparateSurfaceAndNoAccentUnderline() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 0, 0, 0));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            var strip = owner.windowTabs(); layout(strip, 800, 30);
            var before = render(strip);
            assertThat(before.getRGB(410, 5)).isNotEqualTo(before.getRGB(10, 5));
            assertThat(before.getRGB(410, 5)).isEqualTo(before.getRGB(410, 28));
            assertThat(before.getRGB(399, 5)).isNotEqualTo(before.getRGB(10, 5));
            owner.selectTab(owner.terminalTabs().get(0));
            var after = render(strip);
            assertThat(after.getRGB(10, 5)).isEqualTo(before.getRGB(410, 5));
            assertThat(after.getRGB(410, 5)).isEqualTo(before.getRGB(10, 5));
            assertThat(strip.motionRunning()).isFalse();
        });
    }

    @Test void inactiveCellsKeepReadableTitlesWhateverTheUiTheme() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            owner.currentTab().rename("other"); owner.newTab(HOME); owner.currentTab().rename("selected"); owner.update();
            for (Theme chrome : java.util.List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)) {
                owner.selectTheme(chrome);
                for (var colors : dev.jasper.app.config.TerminalColors.builtIns()) {
                    owner.selectTerminalColors(colors);
                    var strip = owner.windowTabs(); layout(strip, 800, 38);
                    assertThat(strip.getBackground()).as("%s UI, %s terminal", chrome, colors).isEqualTo(owner.theme().palette().background());
                    for (boolean active : new boolean[]{true, false}) {
                        owner.setActive(active);
                        Color text = named(strip, "select:other").getForeground();
                        if (!owner.theme().matching())
                            assertThat(text).as("inactive title uses workspace TEXT_MUTED, active %s", active)
                                .isEqualTo(owner.theme().workspace().tokens().get(dev.jasper.ui.theme.Token.TEXT_MUTED));
                        assertThat(contrast(text, pixel(strip, 10, 4))).as("%s UI, %s terminal, active %s", chrome, colors, active)
                            .isGreaterThanOrEqualTo(3);
                    }
                }
            }
        });
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    private static double luminance(Color color) {
        double[] rgb = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }

    @Test void controlsRetainIdentityAcrossMetadataSelectionReorderAndTheme() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            JComponent strip = named(owner, "windowTabs");
            var first = owner.currentTab(); first.rename("first"); owner.update();
            JComponent control = named(strip, "select:first");
            owner.newTab(HOME); var second = owner.currentTab();
            owner.reorderTab(1, 0);
            assertThat(owner.currentTab()).isSameAs(second);
            first.rename("<html>literal title"); owner.update();
            owner.selectTheme(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            assertThat(named(strip, "select:<html>literal title")).isSameAs(control);
            assertThat(control.getClientProperty("html.disable")).isEqualTo(true);
            assertThat(control.getToolTipText()).startsWith("<html>literal title");
            assertThat(owner.currentTab()).isSameAs(second);
        });
    }

    @Test void tooltipsAndTheTabListStayLiteralAndBounded() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), true);
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            String title = "<html><img src=http://example.invalid/x.png>" + "x".repeat(1000);
            owner.currentTab().rename(title); owner.update();
            var strip = owner.windowTabs();
            var select = named(strip, "select:" + title);
            JToolTip tip = select.createToolTip();
            tip.setTipText(select.getToolTipText());
            assertThat(tip.getClientProperty(BasicHTML.propertyKey)).as("the tip renders the title literally").isNull();
            assertThat(select.getToolTipText()).hasSizeLessThanOrEqualTo(210).startsWith("<html><img").contains("\u2026");
            var item = (JMenuItem) strip.tabList().getComponent(0);
            assertThat(item.getText()).hasSizeLessThanOrEqualTo(201).startsWith("<html><img").endsWith("\u2026");
            owner.currentTab().rename("short"); owner.update();
            assertThat(select.getToolTipText()).isEqualTo("short (\u23181)");
            assertThat(((JMenuItem) strip.tabList().getComponent(0)).getText()).isEqualTo("short");
        });
    }

    @Test void middleClickAndDragUseTheSameSelectionAndOrderModel() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
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
    private static Color pixel(Container entry, int x, int y) { return new Color(render(entry).getRGB(x, y)); }
    private static BufferedImage render(Container entry) {
        // Cell surfaces are painted by the strip; crop the real whole strip.
        var ancestor = SwingUtilities.getAncestorOfClass(WindowTabs.class, entry);
        if (ancestor instanceof WindowTabs strip) {
            Point at = SwingUtilities.convertPoint(entry, 0, 0, strip);
            return render(strip).getSubimage(at.x, at.y, entry.getWidth(), entry.getHeight());
        }
        var image = new BufferedImage(entry.getWidth(), entry.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { entry.paint(g); } finally { g.dispose(); }
        return image;
    }
}
