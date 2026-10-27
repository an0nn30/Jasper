package dev.jasper.app.workspace;

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

    @Test void viewOmitsAppearanceAndBuddyEntriesWhileCommandsRemainAvailable() throws Exception {
        edt(() -> {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                for (boolean buddyShown : List.of(false, true)) {
                    owner.buddyEnabled = () -> buddyShown;
                    owner.updateActions();
                    assertThat(menuLabels(owner.menuBar().getMenu(2)))
                        .doesNotContain("Show Jasper", "Hide Jasper", "UI Theme", "Workspace Scheme",
                            "Themes\u2026", "Window Settings\u2026", "Appearance")
                        .contains("View Modes", "Show/Hide", "Font Size", "Toolbar", "Status Bar", "Rail");
                }
                assertThat(owner.commands().entries().stream().map(entry -> entry.command().id()))
                    .contains("view.buddy", "view.themes", "view.window_settings")
                    .noneMatch(id -> id.startsWith("view.appearance."));
                assertThat(owner.action(ActionId.OPEN_SETTINGS)).isNotNull();
            }
        });
    }

    static List<String> menuLabels(JMenu menu) {
        var labels = new java.util.ArrayList<String>();
        for (var component : menu.getMenuComponents()) {
            if (component instanceof JMenuItem item) labels.add(item.getText());
            if (component instanceof JMenu child) labels.addAll(menuLabels(child));
        }
        return labels;
    }

    @Test void viewThemesOpensSettingsAtAppearance() throws Exception {
        edt(() -> {
            try(var owner=content(launcher(new ArrayDeque<>()))) {
                var opened=new java.util.ArrayList<String>();
                owner.connectSettings(edit->java.util.concurrent.CompletableFuture.completedFuture(null), opened::add);
                owner.windowCommands().view("view.themes").actionPerformed(null);
                assertThat(opened).containsExactly("appearance");
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
                .containsExactly(null, null, "Split", null, null, null, null, null);
            assertThat(buttons).allSatisfy(button -> {
                assertThat(button.getIcon().getIconWidth()).isEqualTo(16);
                assertThat(button.getIcon().getIconHeight()).isEqualTo(16);
                assertThat(button.getAccessibleContext().getAccessibleName()).isNotBlank();
                assertThat(button.getToolTipText()).isNotBlank();
            });
            assertThat(buttons.get(2).getAccessibleContext().getAccessibleName()).isEqualTo("Split");

            List<Icon> icons = buttons.stream().map(AbstractButton::getIcon).toList();

            owner.setToolbarMode(ToolbarMode.ICONS);
            assertThat(buttons).extracting(JButton::getText).containsOnlyNulls();
            assertThat(buttons).extracting(AbstractButton::getIcon).containsExactlyElementsOf(icons);
            owner.setToolbarMode(ToolbarMode.HIDDEN);
            assertThat(owner.toolbar().isVisible()).isFalse();
            owner.setToolbarMode(ToolbarMode.ICONS_AND_LABELS);
            assertThat(owner.toolbar().isVisible()).isTrue();
            assertThat(buttons).extracting(JButton::getText)
                .containsExactly(null, null, "Split", null, null, null, null, null);
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
            }
            // Workspace plan 7b Rulings 16: the window's ⋮ follows Settings; the overflow button, last in the row's order,
            // lays out before the ⋮.
            assertThat(shape).containsExactly("New tab", "New window", "|", "Split", "Maximize pane", "(|)", "|", "Find", "Settings",
                "View Options", "More actions");
            var find = (JButton) java.util.Arrays.stream(toolbar.getComponents())
                .filter(child -> child instanceof JButton button && "Find".equals(button.getClientProperty("label"))).findFirst().orElseThrow();
            var settings = toolbar.getComponent(toolbar.getComponentCount() - 3);
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
