package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowTabsTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void stripIsAlwaysVisibleAndSelectsAndClosesRealTabs() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            JComponent strip = named(owner, "windowTabs");
            assertThat(strip).isNotNull();
            assertThat(strip.getPreferredSize().height).isEqualTo(30);
            assertThat(strip.isVisible()).as("a lone tab still shows, as in IntelliJ").isTrue();
            var first = owner.currentTab(); first.rename("first"); owner.newTab(HOME);
            var second = owner.currentTab(); second.rename("second"); owner.update();
            ((AbstractButton) named(strip, "select:first")).doClick();
            assertThat(owner.currentTab()).isSameAs(first);
            ((AbstractButton) named(strip, "close:second")).doClick();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
            assertThat(strip.isVisible()).isTrue();
            assertThat(named(strip, "newTab")).as("New tab stays reachable at the strip end").isNotNull();
            owner.tabStrip().setSize(600, 300); owner.tabStrip().doLayout();
            assertThat(owner.tabStrip().getComponentAt(0).getY()).isLessThanOrEqualTo(1);
        });
    }

    @Test void widerStartingSizeDoesNotDoubleLongerTitles() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 80, 100, 130));
            var strip = owner.windowTabs();
            owner.currentTab().rename("~"); owner.update(); layout(strip, 800, 30);
            assertThat(named(strip, "select:~").getParent().getWidth()).as("short titles use the larger starting size")
                .isEqualTo(160);
            String sshTitle = "ubuntu@amp-mc-1: ~";
            owner.currentTab().rename(sshTitle); owner.update(); layout(strip, 800, 30);
            assertThat(named(strip, "select:" + sshTitle).getParent().getWidth())
                .as("SSH titles grow normally instead of multiplying their measured width").isBetween(160, 240);
            String longTitle = "a very long terminal title ".repeat(4);
            owner.currentTab().rename(longTitle); owner.update(); layout(strip, 800, 30);
            assertThat(named(strip, "select:" + longTitle).getParent().getWidth()).isEqualTo(240);
            owner.currentTab().rename("~"); owner.update(); layout(strip, 800, 30);
            assertThat(named(strip, "select:~").getParent().getWidth()).isEqualTo(160);
        });
    }

    @Test void tabsAreContentSizedLeftAlignedWithIconTitleAndTrailingClose() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second");
            var strip = owner.windowTabs(); layout(strip, 800, 38);
            var first = (AbstractButton) named(strip, "select:first");
            var second = (AbstractButton) named(strip, "select:second");
            assertThat(first.getIcon()).as("inactive tabs retain their identifying icons").isNotNull();
            assertThat(second.getIcon()).isNotNull();
            assertThat(second.getIcon().getIconWidth()).isEqualTo(16);
            assertThat(first.getHorizontalAlignment()).isEqualTo(SwingConstants.LEADING);
            Container one = first.getParent(), two = second.getParent();
            assertThat(one.getX()).isZero();
            assertThat(two.getX()).isEqualTo(one.getWidth());
            assertThat(one.getWidth()).isBetween(160, 240);
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
            assertThat(named(strip, "tabList").getBounds()).isEqualTo(new Rectangle(752, 0, 24, 38));
        });
    }

    @Test void hostedTabsKeepAnIconWhenPluginArtworkIsAbsentOrCleared() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var terminal = owner.currentTab();
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            var document = model.content().open("preview.editor", "file", "config.yml", owner.id(), new JPanel());
            var button = (AbstractButton) named(owner.windowTabs(), "select:config.yml");
            assertThat(button.getIcon()).as("new documents start with a generic icon").isNotNull();
            var custom = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            document.presentation("config.yml", "", custom, false, "config.yml", "");
            assertThat(button.getIcon()).as("plugin artwork takes precedence").isSameAs(custom);
            document.presentation("config.yml", "", null, false, "config.yml", "");
            owner.selectTab(terminal);
            assertThat(button.getIcon()).as("clearing artwork retains an icon on inactive tabs").isNotNull();
            assertThat(button.getIcon()).isNotSameAs(custom);
        });
    }

    @Test void tooltipsCarryTheTitleAndTheLiveSelectShortcut() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), true);
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            var strip = owner.windowTabs();
            assertThat(named(strip, "select:second").getToolTipText()).isEqualTo("second (⌘2)");
            owner.setBindings(KeyBindings.withOverrides(true, Map.of("select_tab_2", "ctrl+alt+2")));
            assertThat(named(strip, "select:second").getToolTipText()).isEqualTo("second (⌃⌥2)");
            owner.setBindings(KeyBindings.withOverrides(true, Map.of("select_tab_2", "none")));
            assertThat(named(strip, "select:second").getToolTipText()).isEqualTo("second");
        });
    }

    @Test void flatStripSharesItsSurfaceAndSeparatesContentWithAnUnderline() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 80, 100, 130));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            for (var theme : java.util.List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)) {
                owner.selectTheme(theme);
                var strip = owner.windowTabs(); layout(strip, 800, 30);
                var selected = named(strip, "select:second").getParent();
                int x = selected.getX(), right = x + selected.getWidth();
                Color surface = owner.theme().palette().background();
                Color accent = owner.theme().workspace().tokens().get(dev.jasper.app.appearance.Token.ACCENT);
                Color divider = owner.theme().workspace().tokens().get(dev.jasper.app.appearance.Token.BORDER);
                for (int at : new int[]{0, x, x + 40, right, 650})
                    assertThat(pixel(strip, at, 4)).as("one flat surface across every tab").isEqualTo(surface);
                for (int at : new int[]{x + 4, x + 40, right - 4}) {
                    assertThat(pixel(strip, at, 28)).as("underline spans the selected tab").isEqualTo(accent);
                    assertThat(pixel(strip, at, 29)).as("underline meets the content edge").isEqualTo(accent);
                    assertThat(pixel(strip, at, 26)).as("underline stays thin").isEqualTo(surface);
                }
                assertThat(pixel(strip, x - 4, 29)).as("no folder shoulders beside the underline").isEqualTo(divider);
                assertThat(pixel(strip, right + 4, 29)).isEqualTo(divider);
                assertThat(pixel(strip, 790, 29)).as("divider continues beneath the fixed controls").isEqualTo(divider);
            }
        });
    }

    @Test void liveDotAppearsOnlyAfterLaunchAndDisappearsWhenTheSessionExits() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[1];
        edt(() -> {
            var owner = content(launcher(pending)); owners[0] = owner;
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 80, 100, 130));
            owner.currentTab().rename("zsh"); owner.update(); layout(owner.windowTabs(), 800, 30);
            assertThat(successPixels(owner)).as("pending launch is not a live session").isZero();
        });
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> owners[0].currentPane().running());
        edt(() -> {
            var owner = owners[0]; layout(owner.windowTabs(), 800, 30);
            assertThat(successPixels(owner)).as("running session shows a green dot").isGreaterThan(4);
            assertThat(named(owner.windowTabs(), "select:zsh").getAccessibleContext().getAccessibleDescription())
                .isEqualTo("Session running");
            var terminal = owner.currentTab();
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            var document = model.content().open("preview.editor", "config", "config.yml", owner.id(), new JPanel());
            document.presentation("config.yml", "", dev.jasper.app.platform.AppIcons.named("FILE"), false, "config.yml", "");
            layout(owner.windowTabs(), 800, 30);
            assertThat(successPixels(owner, "config.yml")).as("document tabs have no session status").isZero();
            assertThat(successPixels(owner)).as("an inactive running terminal retains its status").isGreaterThan(4);
            owner.selectTab(terminal);
            terminal.rename("very long terminal title ".repeat(20)); layout(owner.windowTabs(), 800, 30);
            assertThat(successPixels(owner, terminal.title())).as("truncated titles leave room for the dot").isGreaterThan(4);
            terminal.rename("zsh"); layout(owner.windowTabs(), 800, 30);
            owner.currentPane().write(new byte[]{'\n'});
        });
        until(() -> !owners[0].currentPane().running());
        until(() -> successPixels(owners[0]) == 0);
    }

    private static int successPixels(WindowContent owner) { return successPixels(owner, "zsh"); }

    private static int successPixels(WindowContent owner, String title) {
        var label = named(owner.windowTabs(), "select:" + title);
        var image = new BufferedImage(label.getWidth(), label.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { label.paint(graphics); } finally { graphics.dispose(); }
        int success = owner.theme().workspace().tokens().get(dev.jasper.app.appearance.Token.SUCCESS).getRGB();
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if (image.getRGB(x, y) == success) count++;
        return count;
    }

    @Test void newTabAtTheFarRightOpensAndSelectsATabAndStopsAfterClosure() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            owner.currentTab().rename("first"); owner.update();
            var strip = owner.windowTabs(); layout(strip, 330, 30);
            var add = (AbstractButton) named(strip, "newTab");
            assertThat(add).isNotNull();
            assertThat(add.getBounds()).isEqualTo(new Rectangle(306, 0, 24, 30));
            assertThat(named(strip, "tabList").getBounds()).isEqualTo(new Rectangle(282, 0, 24, 30));
            assertThat(SwingUtilities.getDeepestComponentAt(strip, 318, 15)).isSameAs(add);
            var first = owner.currentTab();
            add.doClick(); layout(strip, 330, 30);
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
            assertThat(owner.currentTab()).isNotSameAs(first);
            owner.close();
            assertThat(add.isEnabled()).isFalse();
            add.doClick();
            assertThat(owner.tabStrip().getTabCount()).isZero();
        });
    }

    @Test void underlineAndControlsFitAtTheMinimumConfiguredHeight() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            var strip = owner.windowTabs();
            for (int height : new int[]{20, 30, 72}) {
                owner.setTabHeight(height); layout(strip, 800, height);
                var label = named(strip, "select:second");
                var close = named(strip, "close:second");
                int middle = label.getParent().getX() + label.getParent().getWidth() / 2;
                assertThat(pixel(strip, middle, height - 1)).as("underline meets content at height %s", height)
                    .isEqualTo(owner.theme().workspace().tokens().get(dev.jasper.app.appearance.Token.ACCENT));
                assertThat(close.getHeight()).isGreaterThanOrEqualTo(16);
                assertThat(label.getY() + label.getHeight()).isEqualTo(height);
                assertThat(close.getY() + close.getHeight()).isEqualTo(height);
            }
        });
    }

    @Test void selectedTabUsesActualWorkspaceColorsWithIndependentTerminalPalettes() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            owner.newTab(HOME); owner.currentTab().rename("selected"); owner.update();
            for (Theme chrome : java.util.List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)) {
                owner.selectTheme(chrome);
                for (var colors : dev.jasper.app.config.TerminalColors.builtIns()) {
                    owner.selectTerminalColors(colors);
                    var strip = owner.windowTabs(); layout(strip, 800, 38);
                    JComponent label = named(strip, "select:selected");
                    assertThat(pixel(label.getParent(), label.getParent().getWidth() / 2, 6)).isEqualTo(owner.theme().palette().background());
                    assertThat(label.getForeground()).isEqualTo(owner.theme().palette().foreground());
                    owner.setActive(false);
                    assertThat(label.getForeground()).isEqualTo(owner.theme().palette().foreground());
                    owner.setActive(true);
                }
            }
        });
    }

    @Test void flatStripKeepsReadableTitlesWhateverTheUiTheme() throws Exception {
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
                                .isEqualTo(owner.theme().workspace().tokens().get(dev.jasper.app.appearance.Token.TEXT_MUTED));
                        assertThat(contrast(text, strip.getBackground())).as("%s UI, %s terminal, active %s", chrome, colors, active)
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

    @Test void aGrowingSelectedTitleStaysVisibleInOverflow() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            var strip = owner.windowTabs();
            owner.currentTab().rename("a");
            for (String name : new String[] {"b", "c", "d", "e"}) { owner.newTab(HOME); owner.currentTab().rename(name); }
            owner.invoke(ActionId.SELECT_TAB_3); owner.update();
            layout(strip, 330, 38);
            var selected = owner.currentTab();
            assertThat(named(strip, "select:c").getParent().isVisible()).as("c is the rightmost visible tab").isTrue();
            assertThat(named(strip, "select:d").getParent().isVisible()).as("the next tab is partially visible").isTrue();
            selected.rename("vim somefile.txt with a much longer title than before"); owner.update();
            layout(strip, 330, 38);
            assertThat(named(strip, "select:" + selected.title()).getParent().isVisible()).isTrue();
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

    @Test void overflowScrollsKeepsSelectionVisibleAndTheListReachesEveryLiteralTitle() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
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
            wheel(tabs, 12); layout(tabs, 330, 38);
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
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            JComponent strip = named(owner, "windowTabs");
            for (int i = 0; i < 4; i++) owner.newTab(HOME);
            owner.currentTab().rename("selected"); owner.update();
            layout(strip, 850, 38);
            assertThat(named(strip, "select:selected").getParent().isVisible()).isTrue();
            layout(strip, 250, 38);
            assertThat(named(strip, "select:selected").getParent().isVisible()).isTrue();
        });
    }

    @Test void preciseWheelScrollsPartialTabsOverLabelsAndCloseButtonsAndClampsAtEnds() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            for (int i = 0; i < 8; i++) {
                if (i > 0) owner.newTab(HOME);
                owner.currentTab().rename("Tab " + i + " with a longer title");
            }
            owner.invoke(ActionId.SELECT_TAB_1); owner.update();
            var strip = owner.windowTabs(); layout(strip, 430, 38);
            var first = named(strip, "select:" + owner.currentTab().title());
            var entry = first.getParent();
            int naturalWidth = entry.getWidth();
            wheel(first, 0.25); layout(strip, 430, 38);
            assertThat(entry.getX()).as("fractional wheel input moves tabs without snapping").isBetween(-30, -1);
            assertThat(entry.getWidth()).as("edge tabs clip instead of shrinking").isEqualTo(naturalWidth);
            int before = entry.getX();
            wheel(named(strip, "close:" + owner.currentTab().title()), 0.25); layout(strip, 430, 38);
            assertThat(entry.getX()).isLessThan(before);
            assertThat(owner.tabStrip().getSelectedIndex()).as("scrolling does not select another tab").isZero();
            wheel(strip, -1000); layout(strip, 430, 38);
            assertThat(entry.getX()).isZero();
            wheel(strip, 1000); layout(strip, 430, 38);
            var last = named(strip, "select:Tab 7 with a longer title").getParent();
            assertThat(last.getX() + last.getWidth()).isEqualTo(last.getParent().getWidth());
            assertThat(named(strip, "tabList").getBounds()).isEqualTo(new Rectangle(382, 0, 24, 38));
            assertThat(SwingUtilities.getDeepestComponentAt(strip, 394, 19)).isSameAs(named(strip, "tabList"));
            ((JMenuItem) strip.tabList().getComponent(0)).doClick(); layout(strip, 430, 38);
            assertThat(entry.getX()).as("choosing the already-selected tab reveals it again").isZero();
            layout(strip, 4400, 38);
            assertThat(entry.getX()).isZero();
            wheel(first, 1000); layout(strip, 4400, 38);
            assertThat(entry.getX()).as("no movement when all tabs fit").isZero();
        });
    }

    @Test void overflowFadesOnlyClippedEdgesIntoTheRecessedStrip() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            for (int i = 0; i < 5; i++) {
                if (i > 0) owner.newTab(HOME);
                owner.currentTab().rename("wide selected tab " + i);
            }
            var strip = owner.windowTabs();
            for (var theme : java.util.List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)) {
                owner.selectTheme(theme); owner.invoke(ActionId.SELECT_TAB_1); owner.update();
                layout(strip, 380, 38);
                var first = named(strip, "select:wide selected tab 0");
                wheel(first, 0.5); layout(strip, 380, 38);
                var viewport = first.getParent().getParent();
                Color surface = strip.getBackground();
                assertThat(pixel(viewport, 0, 6)).as("left edge fades into the tab surface").isEqualTo(surface);
                assertThat(pixel(viewport, 24, 6)).as("selection beyond the fade is unchanged")
                    .isEqualTo(owner.theme().palette().background());
                var output = java.nio.file.Path.of("build/render-preview");
                try {
                    java.nio.file.Files.createDirectories(output);
                    javax.imageio.ImageIO.write(render(strip), "png", output.resolve("scrolling-tabs-" + (theme.dark() ? "dark" : "light") + ".png").toFile());
                } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                // Scroll near the end so the selected final tab is clipped at the right edge.
                ((JMenuItem) strip.tabList().getComponent(4)).doClick(); layout(strip, 380, 38);
                wheel(strip, -0.5); layout(strip, 380, 38);
                assertThat(pixel(viewport, viewport.getWidth() - 1, 6)).isEqualTo(surface);
                assertThat(pixel(viewport, viewport.getWidth() - 25, 6)).isEqualTo(owner.theme().palette().background());
            }
        });
    }

    @Test void scrolledTabsKeepDragCloseAndSelectionGeometry() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            for (int i = 0; i < 6; i++) {
                if (i > 0) owner.newTab(HOME);
                owner.currentTab().rename("tab-" + i);
            }
            var strip = owner.windowTabs(); layout(strip, 612, 38);
            var selected = owner.currentTab();
            var source = named(strip, "select:tab-5");
            var target = named(strip, "select:tab-4");
            Point end = SwingUtilities.convertPoint(target, 10, 10, source);
            mouse(source, MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON1);
            mouse(source, MouseEvent.MOUSE_RELEASED, end.x, end.y, MouseEvent.BUTTON1);
            assertThat(owner.tabStrip().getComponentAt(4)).isSameAs(selected);
            layout(strip, 612, 38);
            var bounds = SwingUtilities.convertRectangle(source.getParent(), source.getBounds(), source.getParent().getParent());
            assertThat(bounds.x).isGreaterThanOrEqualTo(0);
            assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(564);
            ((AbstractButton) named(strip, "close:tab-5")).doClick(); layout(strip, 612, 38);
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(5);
            var remaining = named(strip, "select:" + owner.currentTab().title()).getParent();
            assertThat(remaining.isVisible()).isTrue();
            assertThat(remaining.getX()).isGreaterThanOrEqualTo(0);
            assertThat(remaining.getX() + remaining.getWidth()).isLessThanOrEqualTo(564);
        });
    }

    @Test void droppingOverFixedControlsDoesNotTargetAClippedTab() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
            for (int i = 0; i < 5; i++) {
                if (i > 0) owner.newTab(HOME);
                owner.currentTab().rename("long tab title number " + i);
            }
            owner.invoke(ActionId.SELECT_TAB_1); owner.update();
            var first = owner.currentTab();
            var strip = owner.windowTabs(); layout(strip, 330, 38);
            var source = named(strip, "select:" + first.title());
            for (int x : new int[] {294, 318, 345, -8}) {
                Point end = SwingUtilities.convertPoint(strip, x, 19, source);
                mouse(source, MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON1);
                mouse(source, MouseEvent.MOUSE_RELEASED, end.x, end.y, MouseEvent.BUTTON1);
                assertThat(owner.tabStrip().getComponentAt(0)).as("drop outside viewport at %d", x).isSameAs(first);
            }
        });
    }

    private static void wheel(Component component, double rotation) {
        var event = new java.awt.event.MouseWheelEvent(component, MouseEvent.MOUSE_WHEEL, 1, 0,
            10, 10, 10, 10, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3,
            (int) rotation, rotation);
        component.dispatchEvent(event);
        assertThat(event.isConsumed()).as("the tab strip owns its scroll gesture").isTrue();
    }

    @Test void closedOwnerDisablesTheTabList() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
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
    private static Color pixel(Container entry, int x, int y) { return new Color(render(entry).getRGB(x, y)); }
    private static BufferedImage render(Container entry) {
        // Background and underline are painted by the strip; crop the real whole strip.
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
