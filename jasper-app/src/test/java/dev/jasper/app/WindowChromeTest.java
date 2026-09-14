package dev.jasper.app;

import java.util.ArrayDeque;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowChromeTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

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

            owner.setToolbarMode(WindowContent.ToolbarMode.ICONS);
            assertThat(buttons).extracting(JButton::getText).containsOnlyNulls();
            assertThat(buttons).extracting(AbstractButton::getIcon).containsExactlyElementsOf(icons);
            owner.setToolbarMode(WindowContent.ToolbarMode.HIDDEN);
            assertThat(owner.toolbar().isVisible()).isFalse();
            owner.setToolbarMode(WindowContent.ToolbarMode.ICONS_AND_LABELS);
            assertThat(owner.toolbar().isVisible()).isTrue();
            assertThat(buttons).extracting(JButton::getText)
                .containsExactly("New tab", "New window", "Split", "Zoom pane", "Find", "Settings", "Reload config");
        });
    }

}
