package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Rectangle;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs at the normal scale and, explicitly, with flatlaf.uiScale=2. */
class FolderTabScaleTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void scaledFoldersKeepTheirShouldersFlushAndFixedControlsReachable() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            var strip = owner.windowTabs();
            for (int height : new int[]{20, 30, 72}) {
                owner.setTabHeight(height); WindowTabsTest.layout(strip, UIScale.scale(800), UIScale.scale(height));
                var image = new BufferedImage(strip.getWidth(), strip.getHeight(), BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics();
                try { strip.paint(g); } finally { g.dispose(); }
                var entry = WindowTabsTest.named(strip, "select:second").getParent();
                Color surface = owner.theme().palette().background();
                assertThat(new Color(image.getRGB(entry.getX() + entry.getWidth() / 2, 0)))
                    .as("folder reaches the top of the rail without a dark gap").isEqualTo(surface);
                for (int cornerX : new int[]{entry.getX() + UIScale.scale(5),
                        entry.getX() + entry.getWidth() - UIScale.scale(5) - 1}) {
                    assertThat(new Color(image.getRGB(cornerX, UIScale.scale(1))))
                        .as("upper corner follows the rail's eight-pixel radius at height %s", height).isEqualTo(surface);
                }
                int x = entry.getX() - UIScale.scale(4), y = strip.getHeight() - 1;
                assertThat(new Color(image.getRGB(x, y))).as("scaled shoulder joins the terminal at height %s", height)
                    .isEqualTo(owner.theme().palette().background());
                var add = WindowTabsTest.named(strip, "newTab");
                assertThat(add.getBounds()).isEqualTo(new Rectangle(UIScale.scale(776), 0,
                    UIScale.scale(24), UIScale.scale(height)));
                assertThat(SwingUtilities.getDeepestComponentAt(strip, UIScale.scale(788), UIScale.scale(height / 2)))
                    .isSameAs(add);
                assertThat(WindowTabsTest.named(strip, "close:second").getHeight()).isGreaterThanOrEqualTo(UIScale.scale(16));
            }
        });
    }
}
