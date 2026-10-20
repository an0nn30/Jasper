package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import javax.swing.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class CollapsingMenuBarTest {
    @BeforeAll static void theme() throws Exception { SwingUtilities.invokeAndWait(() -> ThemeTestSupport.install(true)); }

    private static JMenu menu(String name, int width) {
        var menu = new JMenu(name);
        menu.setPreferredSize(new Dimension(width, 24));
        menu.add(new JMenuItem(name + " item"));
        return menu;
    }

    /** File 40, Edit 40, View 50, Pane 50, Tab 40 (220 in all); both fold controls 30. */
    private static CollapsingMenuBar bar() {
        var bar = new CollapsingMenuBar();
        for (var menu : new JMenu[] {menu("File", 40), menu("Edit", 40), menu("View", 50), menu("Pane", 50), menu("Tab", 40)}) bar.add(menu);
        bar.overflowMenu().setPreferredSize(new Dimension(30, 24));
        bar.hamburgerMenu().setPreferredSize(new Dimension(30, 24));
        return bar;
    }

    private static boolean[] shown(CollapsingMenuBar bar) {
        var menus = bar.menus();
        var shown = new boolean[menus.size()];
        for (int i = 0; i < shown.length; i++) shown[i] = menus.get(i).isVisible();
        return shown;
    }

    @Test void trailingMenusFoldIntoMoreThenEverythingIntoTheHamburger() {
        var bar = bar();
        assertThat(bar.fit(220)).isEqualTo(220);
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
        assertThat(bar.overflowMenu().isVisible()).isFalse();
        assertThat(bar.fit(219)).isEqualTo(180 + 30);
        assertThat(shown(bar)).containsExactly(true, true, true, true, false);
        assertThat(bar.overflowMenu().isVisible()).isTrue();
        assertThat(bar.fit(100)).as("File always stays").isEqualTo(40 + 30);
        assertThat(shown(bar)).containsExactly(true, false, false, false, false);
        assertThat(bar.fit(60)).isEqualTo(30);
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.ALL);
        assertThat(shown(bar)).containsExactly(false, false, false, false, false);
        assertThat(bar.hamburgerMenu().isVisible()).isTrue();
        assertThat(bar.getMenu(0)).as("F10 opens the first showing menu").isSameAs(bar.hamburgerMenu());
        assertThat(bar.minimumMenusWidth()).isEqualTo(30);
        bar.fit(500);
        assertThat(bar.getMenu(0).getText()).isEqualTo("File");
    }

    @Test void openingAFoldMenuMovesTheSameMenusInAndClosingPutsThemBackInOrder() {
        var bar = bar();
        var menus = bar.menus();
        bar.fit(100);
        bar.overflowMenu().setSelected(true);
        assertThat(bar.overflowMenu().getMenuComponents()).containsExactly(menus.get(1), menus.get(2), menus.get(3), menus.get(4));
        assertThat(menus.get(1).isVisible()).isTrue();
        bar.overflowMenu().setSelected(false);
        bar.restoreMenus();
        assertThat(Arrays.asList(bar.getComponents()).subList(0, 5)).containsExactlyElementsOf(menus);
        assertThat(bar.overflowMenu().getMenuComponentCount()).isZero();
        bar.fit(60);
        bar.hamburgerMenu().setSelected(true);
        assertThat(bar.hamburgerMenu().getMenuComponents()).containsExactlyElementsOf(menus);
        bar.restoreMenus();
        assertThat(bar.menus()).containsExactlyElementsOf(menus);
    }

    @Test void contributedMenusGoBeforeTheFoldControlsAndLeaveEvenWhileFolded() {
        var bar = bar();
        var tool = menu("Tool", 40);
        bar.add(tool);
        assertThat(bar.menus()).endsWith(tool);
        assertThat(bar.getComponentIndex(tool)).isEqualTo(5);
        assertThat(bar.getComponentIndex(bar.overflowMenu())).isEqualTo(6);
        bar.fit(100);
        bar.overflowMenu().setSelected(true);
        bar.remove(tool);
        assertThat(bar.menus()).doesNotContain(tool);
        assertThat(tool.getParent()).isNull();
        bar.restoreMenus();
        assertThat(bar.menus()).hasSize(5);
    }

    @Test void withoutAHeaderTheBarFoldsAgainstItsOwnWidth() {
        var bar = bar();
        bar.setSize(100, 24); bar.doLayout();
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.MORE);
        var insets = bar.getInsets();
        assertThat(bar.menus().getFirst().getBounds())
            .isEqualTo(new Rectangle(insets.left, insets.top, 40, 24 - insets.top - insets.bottom));
        assertThat(bar.overflowMenu().getX()).isEqualTo(insets.left + 40);
    }

    @Test void aHeaderSpansTheBarUnderTheMenusAndDecidesHowManyShow() {
        var bar = bar();
        var toolbar = new WindowToolbar(() -> { });
        for (int i = 0; i < 4; i++) {
            var action = new AbstractAction("Action " + i) { @Override public void actionPerformed(ActionEvent event) { } };
            var button = new JButton(action);
            button.setPreferredSize(new Dimension(60, 24));
            WindowToolbar.entry(button, i, () -> WindowToolbar.menuItem(action));
            toolbar.add(button);
        }
        toolbar.finish();
        var header = new WindowHeader(new TitleSearchField(() -> { }, () -> ""), toolbar);
        bar.setHeader(header);
        assertThat(bar.isOptimizedDrawingEnabled()).as("menus overlap the header").isFalse();
        bar.setSize(1400, 30); bar.doLayout();
        assertThat(header.getBounds()).isEqualTo(new Rectangle(0, 0, 1400, 30));
        assertThat(header.field().getX()).isEqualTo(500);
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
        assertThat(bar.getComponentIndex(header)).as("under every menu").isGreaterThan(bar.getComponentIndex(bar.hamburgerMenu()));
        bar.setSize(740, 30); bar.doLayout();
        assertThat(header.field().getX()).isEqualTo(170);
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.MORE);
        assertThat(shown(bar)).containsExactly(true, true, false, false, false);
        bar.setSize(440, 30); bar.doLayout();
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.ALL);
        assertThat(header.field().getX() + header.field().getWidth() / 2).as("still centred").isEqualTo(220);
    }

    /** Plan 14 (Rulings 13): the Windows classic title follows the menus; they fold before it shrinks past its minimum. */
    @Test void aClassicTitleFollowsTheMenusAndTheMenusFoldBeforeItShrinksPastItsMinimum() {
        var bar = bar();
        bar.setBorder(BorderFactory.createEmptyBorder());
        var title = new ClassicWindowTitle();
        title.setPreferredSize(new Dimension(300, 20));
        bar.setTitle(title);
        int gap = UIScale.scale(ClassicWindowTitle.GAP), minimum = UIScale.scale(ClassicWindowTitle.MINIMUM);
        // FlatLaf sizes its left panel from this and squeezes the window buttons first when its title area drops below 60, so the
        // bar asks only for its folded minimum; FlatLaf's glue widening gives it its real width, and layoutContainer folds.
        assertThat(bar.getPreferredSize().width).as("the ☰ menu, the gap and the title's minimum").isEqualTo(30 + gap + minimum);
        assertThat(bar.getComponent(bar.getComponentCount() - 1)).as("the glue FlatLaf stretches the bar by").isInstanceOf(Box.Filler.class);
        bar.setSize(800, 28); bar.doLayout();
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.NONE);
        assertThat(title.getBounds()).isEqualTo(new Rectangle(220 + gap, 0, 800 - 220 - gap, 28));
        bar.setSize(220 + gap + minimum, 28); bar.doLayout();
        assertThat(bar.fold()).as("the title at its minimum keeps every menu").isEqualTo(CollapsingMenuBar.Fold.NONE);
        assertThat(title.getWidth()).isEqualTo(minimum);
        bar.setSize(220 + gap + minimum - 1, 28); bar.doLayout();
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.MORE);
        assertThat(shown(bar)).containsExactly(true, true, true, true, false);
        assertThat(title.getX()).isEqualTo(180 + 30 + gap);
        assertThat(bar.getPreferredSize().width).as("folding does not change what the bar asks for").isEqualTo(30 + gap + minimum);
        bar.setSize(40, 28); bar.doLayout();
        assertThat(bar.fold()).isEqualTo(CollapsingMenuBar.Fold.ALL);
        assertThat(title.getWidth()).as("never negative").isZero();
    }
}
