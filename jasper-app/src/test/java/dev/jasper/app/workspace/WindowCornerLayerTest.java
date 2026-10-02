package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.Arrays;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static dev.jasper.app.testsupport.LayoutTestSupport.layoutTree;
import static dev.jasper.app.workspace.DesktopTestSupport.content;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WindowCornerLayerTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void theLayerSitsFlushInTheContentCornerOnThePaletteLayer() throws Exception {
        var owner = content(launcher(new ArrayDeque<>()));
        var layer = new JPanel();
        layer.setPreferredSize(new Dimension(200, 120));
        owner.mountCornerLayer(layer);
        assertThat(layer.getParent()).as("nothing to mount on before the window has a root").isNull();
        var root = new JRootPane();
        try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
            owner.installRootBindings(root);
            root.setSize(1000, 640);
            layoutTree(root);
            layoutTree(root);
            owner.layoutCornerLayer();
            assertThat(layer.getParent()).isSameAs(root.getLayeredPane());
            assertThat(JLayeredPane.getLayer(layer)).isEqualTo(JLayeredPane.PALETTE_LAYER.intValue());
            Rectangle area = SwingUtilities.convertRectangle(owner.regions().getParent(), owner.regions().getBounds(), root.getLayeredPane());
            assertThat(layer.getBounds()).isEqualTo(new Rectangle(area.x + area.width - 200, area.y + area.height - 120, 200, 120));
            layer.setPreferredSize(new Dimension(300, 50));
            assertThat(layer.getBounds()).isEqualTo(new Rectangle(area.x + area.width - 300, area.y + area.height - 50, 300, 50));
            layer.setPreferredSize(new Dimension(5000, 5000));
            assertThat(layer.getBounds()).as("clipped to the content area").isEqualTo(area);
            owner.rebind();
            assertThat(Arrays.stream(root.getLayeredPane().getComponents()).filter(component -> component == layer)).hasSize(1);
            owner.close();
            assertThat(layer.getParent()).isNull();
        }
    }
}
