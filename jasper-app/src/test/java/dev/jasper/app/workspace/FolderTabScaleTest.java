package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs at the normal scale and, explicitly, with flatlaf.uiScale=2. */
class FolderTabScaleTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void equalCellsAndSeparatorsFollowScaleAndConfiguredHeight() throws Exception {
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
                assertThat(entry.getBounds()).isEqualTo(new Rectangle(UIScale.scale(400), 0,
                    UIScale.scale(400), UIScale.scale(height)));
                int body = image.getRGB(entry.getX() + UIScale.scale(10), UIScale.scale(4));
                assertThat(image.getRGB(entry.getX() + UIScale.scale(10), strip.getHeight() - UIScale.scale(2)))
                    .as("selected surface has no underline").isEqualTo(body);
                assertThat(image.getRGB(UIScale.scale(400) - 1, UIScale.scale(4)))
                    .as("vertical separator follows scale").isEqualTo(owner.theme().workspace().tokens()
                        .get(dev.jasper.app.appearance.Token.BORDER).getRGB());
                assertThat(WindowTabsTest.named(strip, "newTab")).isNull();
                assertThat(WindowTabsTest.named(strip, "close:second").getHeight()).isGreaterThanOrEqualTo(UIScale.scale(16));
            }
        });
    }
}
