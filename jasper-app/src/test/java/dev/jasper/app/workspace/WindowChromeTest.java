package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ToolbarMode;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
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

    @Test void referenceToolbarKeepsLabelsAccessibilityDisabledActionsAndVisibilityModes() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.updateActions();
            List<JButton> buttons = java.util.Arrays.stream(owner.toolbar().getComponents())
                .filter(JButton.class::isInstance).map(JButton.class::cast).toList();

            assertThat(buttons).extracting(JButton::getText)
                .containsExactly("New tab", "New window", "Split", "Zoom pane", "Find", "Settings", "Reload config");
            assertThat(buttons).allSatisfy(button -> {
                assertThat(button.getIcon().getIconWidth()).isEqualTo(16);
                assertThat(button.getIcon().getIconHeight()).isEqualTo(16);
                assertThat(button.getAccessibleContext().getAccessibleName()).isNotBlank();
                assertThat(button.getToolTipText()).isNotBlank();
            });
            assertThat(buttons.get(2).getAccessibleContext().getAccessibleName()).isEqualTo("Split pane");
            assertThat(buttons.get(5).isEnabled()).isFalse();
            assertThat(buttons.get(6).isEnabled()).isFalse();
            List<Icon> icons = buttons.stream().map(AbstractButton::getIcon).toList();

            owner.setToolbarMode(ToolbarMode.ICONS);
            assertThat(buttons).extracting(JButton::getText).containsOnlyNulls();
            assertThat(buttons).extracting(AbstractButton::getIcon).containsExactlyElementsOf(icons);
            owner.setToolbarMode(ToolbarMode.HIDDEN);
            assertThat(owner.toolbar().isVisible()).isFalse();
            owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            assertThat(owner.toolbar().isVisible()).isTrue();
            assertThat(buttons).extracting(JButton::getText)
                .containsExactly("New tab", "New window", "Split", "Zoom pane", "Find", "Settings", "Reload config");
        });
    }

    private static JMenu appearance(WindowContent owner) {
        for (var component : owner.menuBar().getMenu(2).getMenuComponents()) {
            if (component instanceof JMenu menu && menu.getText().equals("Appearance")) return menu;
        }
        throw new AssertionError("Appearance menu is absent");
    }
}
