package dev.jasper.app.workspace;

import dev.jasper.app.appearance.GtkTestThemes;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class GtkChromeTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }

    @Test void gtkBuildsNativeChromeWithoutMetalStyling() throws Exception {
        edt(() -> {
            var owner = content(launcher(new java.util.ArrayDeque<>()), GtkTestThemes.themes(GtkTestThemes.LIGHT));
            assertThat(owner.nativeChrome()).isTrue();
            assertThat(owner.gtk()).isTrue();
            assertThat(owner.retro()).isFalse();
            assertThat(owner.windowTabs()).isNull();
            assertThat(owner.toolbar()).isInstanceOf(RetroToolbar.class);
            assertThat(owner.tabStrip().getTabLayoutPolicy()).isEqualTo(JTabbedPane.SCROLL_TAB_LAYOUT);
            assertThat(owner.menuBar().getBorder()).isNotInstanceOf(javax.swing.border.MatteBorder.class);
            var actions = java.util.Arrays.stream(owner.toolbar().getComponents()).filter(JButton.class::isInstance)
                .map(c -> ((JButton) c).getAction()).toList();
            assertThat(actions).contains(owner.action(ActionId.OPEN_SETTINGS), owner.action(ActionId.QUIT));
            assertThat(owner.windowCommands().view("view.appearance.dark").isEnabled()).isFalse();
            assertThat(owner.windowCommands().view("view.tab_height").isEnabled()).isFalse();
            assertThat(menuTexts(owner.menuBar())).contains("GTK follows the desktop theme; change style in Settings and restart.");
            assertThat(owner.theme().palette().background()).isEqualTo(Color.WHITE);
            assertThat(owner.status().getBackground()).isEqualTo(javax.swing.UIManager.getColor("Panel.background"));
        });
    }

    private static List<String> menuTexts(javax.swing.JMenuBar bar) {
        var texts = new ArrayList<String>();
        for (int i = 0; i < bar.getMenuCount(); i++) collect(bar.getMenu(i), texts);
        return texts;
    }
    private static void collect(JMenu menu, List<String> texts) {
        for (var child : menu.getMenuComponents()) {
            if (child instanceof JMenu sub) collect(sub, texts);
            else if (child instanceof JMenuItem item) texts.add(item.getText());
        }
    }
}
