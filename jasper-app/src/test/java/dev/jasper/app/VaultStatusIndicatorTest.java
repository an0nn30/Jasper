package dev.jasper.app;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class VaultStatusIndicatorTest {
    @Test void padlockReflectsStateAndStaysAtTheRightEdge() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            for (UiLookAndFeel laf : List.of(UiLookAndFeel.METAL, UiLookAndFeel.NIMBUS)) {
                themes.selectLaf(laf);
                var status = new WindowStatusBar();
                JButton padlock = status.vaultButton();
                assertThat(padlock.isEnabled()).isFalse();
                assertThat(padlock.getIcon()).isSameAs(VaultIcons.icon("lock"));
                assertThat(padlock.getToolTipText()).isEqualTo("Credential vault is not available");

                AtomicInteger clicks = new AtomicInteger();
                status.onVaultClick = clicks::incrementAndGet;
                status.setVault(true, true, "Credential vault unlocked. Click to lock.");
                assertThat(padlock.isEnabled()).isTrue();
                assertThat(padlock.getIcon()).isSameAs(VaultIcons.icon("unlock"));
                assertThat(padlock.getToolTipText()).isEqualTo("Credential vault unlocked. Click to lock.");
                assertThat(padlock.getAccessibleContext().getAccessibleName()).isEqualTo("Credential vault unlocked. Click to lock.");
                padlock.doClick();
                assertThat(clicks).hasValue(1);

                status.setVault(true, false, "Credential vault locked. Click to unlock.");
                assertThat(padlock.getIcon()).isSameAs(VaultIcons.icon("lock"));

                status.setMetadata("zsh", "/Users/example", "120 × 36", true);
                for (int width : new int[]{958, 320, 100, 40, 0}) {
                    status.setSize(width, 30); status.doLayout();
                    assertThat(padlock.getX() + padlock.getWidth()).isLessThanOrEqualTo(Math.max(width, padlock.getWidth()));
                    assertThat(padlock.getX()).isGreaterThanOrEqualTo(status.configButton().getParent().getX());
                    var image = new BufferedImage(Math.max(1, width), 30, BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics(); status.paint(g); g.dispose();
                }
            }
        });
    }

    @Test void everyIconIsSixteenPixelsAndPaintsInForeground() throws Exception {
        edt(() -> {
            for (String name : List.of("lock", "unlock", "login", "key", "eye", "copy", "import", "settings", "save", "add")) {
                Icon icon = VaultIcons.icon(name);
                assertThat(icon.getIconWidth()).isEqualTo(16);
                assertThat(icon.getIconHeight()).isEqualTo(16);
                var label = new JLabel(); label.setForeground(java.awt.Color.RED);
                var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); icon.paintIcon(label, g, 0, 0); g.dispose();
                boolean painted = false;
                for (int y = 0; y < 16 && !painted; y++) for (int x = 0; x < 16; x++)
                    if ((image.getRGB(x, y) >>> 24) != 0 && (image.getRGB(x, y) & 0xFF0000) != 0) { painted = true; break; }
                assertThat(painted).as(name).isTrue();
            }
            assertThat(VaultIcons.icon("lock")).isSameAs(VaultIcons.icon("lock"));
        });
    }
}
