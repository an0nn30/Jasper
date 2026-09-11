package dev.moray.app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowChromeTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void openingSiblingAppearanceMenuReflectsTheCurrentGlobalTheme() throws Exception {
        edt(() -> {
            var original = UIManager.getLookAndFeel();
            WindowContent first = null;
            WindowContent second = null;
            try {
                var themes = new ThemeController();
                first = content(launcher(new ArrayDeque<>()), themes);
                second = content(launcher(new ArrayDeque<>()), themes);
                JMenu firstAppearance = appearance(first);
                JMenu secondAppearance = appearance(second);
                firstAppearance.getItem(0).doClick();
                assertThat(UIManager.getLookAndFeel()).isInstanceOf(FlatLightLaf.class);
                secondAppearance.setSelected(true);
                assertThat(secondAppearance.getItem(0).isSelected()).isTrue();
                assertThat(secondAppearance.getItem(1).isSelected()).isFalse();
                secondAppearance.setSelected(false);
                firstAppearance.getItem(1).doClick();
                assertThat(UIManager.getLookAndFeel()).isInstanceOf(FlatDarkLaf.class);
                secondAppearance.setSelected(true);
                assertThat(secondAppearance.getItem(1).isSelected()).isTrue();
                assertThat(secondAppearance.getItem(0).isSelected()).isFalse();
            } finally {
                if (first != null) first.close();
                if (second != null) second.close();
                try { UIManager.setLookAndFeel(original); }
                catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }

    private static JMenu appearance(WindowContent owner) {
        for (var component : owner.menuBar().getMenu(2).getMenuComponents()) {
            if (component instanceof JMenu menu && menu.getText().equals("Appearance")) return menu;
        }
        throw new AssertionError("Appearance menu is absent");
    }
}
