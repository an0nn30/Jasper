package dev.moray.app;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class MockUiTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void terminalLayoutKeepsCompactInsetsAcrossThemeChanges() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[1];
        edt(() -> owners[0] = content(launcher(pending)));
        pending.remove().run(); until(() -> owners[0].currentPane().view() != null);
        edt(() -> {
            var owner = owners[0]; var pane = owner.currentPane();
            owner.setSize(958, 958); layoutTree(owner);
            assertThat(pane.getInsets()).isEqualTo(new Insets(4, 4, 4, 4));
            assertThat(pane.view().getBounds()).isEqualTo(new Rectangle(4, 4,
                pane.getWidth() - 8, pane.getHeight() - 8));
            assertThat(pane.view().fontSize()).isEqualTo(16);
            owner.action(ActionId.FONT_BIGGER).actionPerformed(null);
            owner.action(ActionId.FONT_RESET).actionPerformed(null);
            assertThat(pane.view().fontSize()).isEqualTo(16);
            owner.selectLaf(UiLookAndFeel.NIMBUS);
            layoutTree(owner);
            assertThat(pane.getBackground()).isEqualTo(pane.view().palette().background());
            assertThat(pane.getInsets()).isEqualTo(new Insets(4, 4, 4, 4));
            assertThat(pane.view().getBounds()).isEqualTo(new Rectangle(4, 4,
                pane.getWidth() - 8, pane.getHeight() - 8));
            owner.selectLaf(UiLookAndFeel.METAL);
            layoutTree(owner);
            assertThat(pane.getInsets()).isEqualTo(new Insets(4, 4, 4, 4));
            assertThat(pane.view().getBounds()).isEqualTo(new Rectangle(4, 4,
                pane.getWidth() - 8, pane.getHeight() - 8));
        });
    }

    static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layoutTree(nested);
    }
}
