package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
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

    @Test void resizeEventsOnThePaneAndOnTheRegionsEachRelayTheLayerAndTheListenersAreBalanced() throws Exception {
        var owner = content(launcher(new ArrayDeque<>()));
        var layer = new JPanel();
        layer.setPreferredSize(new Dimension(200, 120));
        int preferredSizeListeners = layer.getPropertyChangeListeners("preferredSize").length;
        owner.mountCornerLayer(layer);
        assertThat(layer.getPropertyChangeListeners("preferredSize")).hasSize(preferredSizeListeners + 1);
        // A window without a layer is the baseline: the palette and the title bar add listeners of their own.
        var plain = content(launcher(new ArrayDeque<>()));
        var plainRoot = new JRootPane();
        var root = new JRootPane();
        try (var plainHeader = WindowContent.installTitleBar(plainRoot, plain, true, title -> {});
             var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
            plain.installRootBindings(plainRoot);
            owner.installRootBindings(root);
            var pane = root.getLayeredPane();
            int paneMounted = pane.getComponentListeners().length, regionsMounted = owner.regions().getComponentListeners().length;
            assertThat(paneMounted).as("the layered pane gains one re-layout listener")
                .isEqualTo(plainRoot.getLayeredPane().getComponentListeners().length + 1);
            assertThat(regionsMounted).as("the regions gain one re-layout listener")
                .isEqualTo(plain.regions().getComponentListeners().length + 1);

            root.setSize(1000, 640);
            layoutTree(root);
            layoutTree(root);
            owner.layoutCornerLayer();
            // Resize without calling layoutCornerLayer: only the listeners may move the layer.
            root.setSize(800, 500);
            layoutTree(root);
            layoutTree(root);
            Rectangle area = SwingUtilities.convertRectangle(owner.regions().getParent(), owner.regions().getBounds(), pane);
            Rectangle corner = new Rectangle(area.x + area.width - 200, area.y + area.height - 120, 200, 120);
            assertThat(layer.getBounds()).as("nothing has relaid the layer yet").isNotEqualTo(corner);
            pane.dispatchEvent(new ComponentEvent(pane, ComponentEvent.COMPONENT_RESIZED));
            assertThat(layer.getBounds()).as("the layered pane's resize listener").isEqualTo(corner);

            layer.setBounds(1, 2, 3, 4);
            pane.dispatchEvent(new ComponentEvent(pane, ComponentEvent.COMPONENT_MOVED));
            assertThat(layer.getBounds()).as("the layered pane's move listener").isEqualTo(corner);

            layer.setBounds(1, 2, 3, 4);
            owner.regions().dispatchEvent(new ComponentEvent(owner.regions(), ComponentEvent.COMPONENT_RESIZED));
            assertThat(layer.getBounds()).as("the regions' resize listener").isEqualTo(corner);

            layer.setBounds(1, 2, 3, 4);
            owner.regions().dispatchEvent(new ComponentEvent(owner.regions(), ComponentEvent.COMPONENT_MOVED));
            assertThat(layer.getBounds()).as("the regions' move listener").isEqualTo(corner);

            owner.rebind();
            assertThat(pane.getComponentListeners()).as("rebind installs idempotently").hasSize(paneMounted);
            assertThat(owner.regions().getComponentListeners()).hasSize(regionsMounted);
            assertThat(layer.getPropertyChangeListeners("preferredSize")).hasSize(preferredSizeListeners + 1);

            owner.close();
            plain.close();
            assertThat(pane.getComponentListeners()).as("close removes the layered pane's listener")
                .hasSize(plainRoot.getLayeredPane().getComponentListeners().length);
            assertThat(owner.regions().getComponentListeners()).as("close removes the regions' listener")
                .hasSize(plain.regions().getComponentListeners().length);
            assertThat(layer.getPropertyChangeListeners("preferredSize")).as("close detaches from the layer")
                .hasSize(preferredSizeListeners);
        }
    }
}
