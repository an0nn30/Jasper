package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ToolbarMode;

import java.util.ArrayDeque;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowChromeTest {
    @Test void narrowToolbarOffersOverflowInsteadOfShrinkingTargets() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var toolbar = owner.toolbar();
            toolbar.setSize(130, 30); toolbar.doLayout();
            var overflow = WindowTabsTest.named(toolbar, "toolbarOverflow");
            assertThat(overflow).isNotNull();
            assertThat(overflow.isVisible()).isTrue();
            for (var child : toolbar.getComponents()) {
                if (!(child instanceof JButton) || !child.isVisible()) continue;
                assertThat(child.getWidth()).isGreaterThanOrEqualTo(24);
                assertThat(child.getX()).isGreaterThanOrEqualTo(0);
                assertThat(child.getX() + child.getWidth()).isLessThanOrEqualTo(130);
            }
        });
    }

    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void openingSiblingTerminalColorsMenuReflectsTheCurrentGlobalChoice() throws Exception {
        edt(() -> {
            WindowContent first = null;
            WindowContent second = null;
            try {
                var themes = new ThemeController();
                first = content(launcher(new ArrayDeque<>()), themes);
                second = content(launcher(new ArrayDeque<>()), themes);
                JMenu firstColors = terminalColors(first);
                JMenu secondColors = terminalColors(second);
                firstColors.getItem(1).doClick();
                secondColors.setSelected(true);
                assertThat(secondColors.getItem(1).isSelected()).isTrue();
                assertThat(secondColors.getItem(0).isSelected()).isFalse();
                secondColors.setSelected(false);
                firstColors.getItem(2).doClick();
                secondColors.setSelected(true);
                assertThat(secondColors.getItem(2).isSelected()).isTrue();
                assertThat(secondColors.getItem(1).isSelected()).isFalse();
            } finally {
                if (first != null) first.close();
                if (second != null) second.close();
            }
        });
    }

    @Test void theViewMenuOffersTerminalColorsButNoThemeChoice() throws Exception {
        edt(() -> {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                var names = new java.util.ArrayList<String>();
                for (var component : owner.menuBar().getMenu(2).getMenuComponents())
                    if (component instanceof JMenuItem item) names.add(item.getText());
                assertThat(names).contains("Terminal Colors").doesNotContain("Appearance");
                assertThat(java.util.Arrays.stream(terminalColors(owner).getMenuComponents())
                    .filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                    .containsExactly("Terminal: Match UI", "Terminal: Light", "Terminal: Dark");
                assertThat(owner.commands().entries().stream().map(entry -> entry.command().id()))
                    .noneMatch(id -> id.startsWith("view.appearance."));
            }
        });
    }

    private static JMenu terminalColors(WindowContent owner) {
        for (var component : owner.menuBar().getMenu(2).getMenuComponents()) {
            if (component instanceof JMenu menu && menu.getText().equals("Terminal Colors")) return menu;
        }
        throw new AssertionError("Terminal Colors menu is absent");
    }

    @Test void referenceToolbarKeepsLabelsAccessibilityDisabledActionsAndVisibilityModes() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.updateActions();
            List<JButton> buttons = java.util.Arrays.stream(owner.toolbar().getComponents())
                .filter(JButton.class::isInstance).map(JButton.class::cast).toList();

            assertThat(buttons).extracting(JButton::getText)
                .containsExactly(null, null, "Split", null, null, null, null);
            assertThat(buttons).allSatisfy(button -> {
                assertThat(button.getIcon().getIconWidth()).isEqualTo(16);
                assertThat(button.getIcon().getIconHeight()).isEqualTo(16);
                assertThat(button.getAccessibleContext().getAccessibleName()).isNotBlank();
                assertThat(button.getToolTipText()).isNotBlank();
            });
            assertThat(buttons.get(2).getAccessibleContext().getAccessibleName()).isEqualTo("Split pane");

            List<Icon> icons = buttons.stream().map(AbstractButton::getIcon).toList();

            owner.setToolbarMode(ToolbarMode.ICONS);
            assertThat(buttons).extracting(JButton::getText).containsOnlyNulls();
            assertThat(buttons).extracting(AbstractButton::getIcon).containsExactlyElementsOf(icons);
            owner.setToolbarMode(ToolbarMode.HIDDEN);
            assertThat(owner.toolbar().isVisible()).isFalse();
            owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            assertThat(owner.toolbar().isVisible()).isTrue();
            assertThat(buttons).extracting(JButton::getText)
                .containsExactly(null, null, "Split", null, null, null, null);
        });
    }

    @Test void modernToolbarGroupsActionsPinsFindAndSettingsRightAndHidesTheEmptyPluginGroup() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var toolbar = owner.toolbar();
            toolbar.setSize(1000, 30); toolbar.doLayout();
            List<String> shape = new java.util.ArrayList<>();
            for (var child : toolbar.getComponents()) {
                if (child instanceof JButton button) shape.add((String) button.getClientProperty("label"));
                else if (child instanceof JSeparator separator) shape.add(separator.isVisible() ? "|" : "(|)");
                else if (child instanceof Box.Filler) shape.add("->");
            }
            assertThat(shape).containsExactly("New tab", "New window", "|", "Split", "Zoom pane", "(|)", "->", "Find", "Settings", "More actions");
            var find = (JButton) java.util.Arrays.stream(toolbar.getComponents())
                .filter(child -> child instanceof JButton button && "Find".equals(button.getClientProperty("label"))).findFirst().orElseThrow();
            var settings = toolbar.getComponent(toolbar.getComponentCount() - 2);
            assertThat(settings.getX() + settings.getWidth()).isGreaterThan(900);
            assertThat(find.getX()).isGreaterThan(700);
            assertThat(find.getHeight()).isEqualTo(24);
            assertThat(toolbar.getPreferredSize().height).isEqualTo(30);
            assertThat(((JButton) settings).getAction()).isSameAs(owner.action(ActionId.OPEN_SETTINGS));
            assertThat(java.util.Arrays.stream(toolbar.getComponents()).filter(JButton.class::isInstance)
                .map(JButton.class::cast)).noneMatch(button -> button.getAction() == owner.action(ActionId.QUIT));
        });
    }
}
