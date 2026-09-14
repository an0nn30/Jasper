package dev.jasper.app;

import java.awt.Color;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class VanillaSwingTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void defaultControlsAndTerminalAreIndependent() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            assertThat(UIManager.getLookAndFeel().getID()).isEqualTo("Motif");
            owner.setSize(1000, 700); owner.doLayout(); owner.tabStrip().doLayout();
            assertThat(owner.tabStrip().getBoundsAt(0).height).isPositive();
            assertThat(owner.toolbar().getBackground()).isEqualTo(UIManager.getColor("ToolBar.background"));
            assertThat(owner.status().getBackground()).isEqualTo(UIManager.getColor("Panel.background"));
            assertThat(owner.currentPane().getBackground()).isEqualTo(Color.BLACK);
            assertThat(owner.theme().palette().foreground()).isEqualTo(Color.WHITE);
        });
    }
}
