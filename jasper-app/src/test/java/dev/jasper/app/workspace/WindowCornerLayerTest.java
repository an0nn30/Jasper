package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Arrays;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
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

    @Test void theLayerSitsFlushInTheAreaCornerOnThePaletteLayer() throws Exception {
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
            Rectangle area = SwingUtilities.convertRectangle(owner.area().getParent(), owner.area().getBounds(), root.getLayeredPane());
            assertThat(layer.getBounds()).isEqualTo(new Rectangle(area.x + area.width - 200, area.y + area.height - 120, 200, 120));
            layer.setPreferredSize(new Dimension(300, 50));
            assertThat(layer.getBounds()).isEqualTo(new Rectangle(area.x + area.width - 300, area.y + area.height - 50, 300, 50));
            layer.setPreferredSize(new Dimension(5000, 5000));
            assertThat(layer.getBounds()).as("clipped to the tool window area").isEqualTo(area);
            owner.rebind();
            assertThat(Arrays.stream(root.getLayeredPane().getComponents()).filter(component -> component == layer)).hasSize(1);
            owner.close();
            assertThat(layer.getParent()).isNull();
        }
    }

    @Test void resizeEventsOnThePaneAndOnTheAreaEachRelayTheLayerAndTheListenersAreBalanced() throws Exception {
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
            int paneMounted = pane.getComponentListeners().length, areaMounted = owner.area().getComponentListeners().length;
            assertThat(paneMounted).as("the layered pane gains one re-layout listener")
                .isEqualTo(plainRoot.getLayeredPane().getComponentListeners().length + 1);
            assertThat(areaMounted).as("the area gains one re-layout listener")
                .isEqualTo(plain.area().getComponentListeners().length + 1);

            root.setSize(1000, 640);
            layoutTree(root);
            layoutTree(root);
            owner.layoutCornerLayer();
            // Resize without calling layoutCornerLayer: only the listeners may move the layer.
            root.setSize(800, 500);
            layoutTree(root);
            layoutTree(root);
            Rectangle area = SwingUtilities.convertRectangle(owner.area().getParent(), owner.area().getBounds(), pane);
            Rectangle corner = new Rectangle(area.x + area.width - 200, area.y + area.height - 120, 200, 120);
            assertThat(layer.getBounds()).as("nothing has relaid the layer yet").isNotEqualTo(corner);
            pane.dispatchEvent(new ComponentEvent(pane, ComponentEvent.COMPONENT_RESIZED));
            assertThat(layer.getBounds()).as("the layered pane's resize listener").isEqualTo(corner);

            layer.setBounds(1, 2, 3, 4);
            pane.dispatchEvent(new ComponentEvent(pane, ComponentEvent.COMPONENT_MOVED));
            assertThat(layer.getBounds()).as("the layered pane's move listener").isEqualTo(corner);

            layer.setBounds(1, 2, 3, 4);
            owner.area().dispatchEvent(new ComponentEvent(owner.area(), ComponentEvent.COMPONENT_RESIZED));
            assertThat(layer.getBounds()).as("the area's resize listener").isEqualTo(corner);

            layer.setBounds(1, 2, 3, 4);
            owner.area().dispatchEvent(new ComponentEvent(owner.area(), ComponentEvent.COMPONENT_MOVED));
            assertThat(layer.getBounds()).as("the area's move listener").isEqualTo(corner);

            owner.rebind();
            assertThat(pane.getComponentListeners()).as("rebind installs idempotently").hasSize(paneMounted);
            assertThat(owner.area().getComponentListeners()).hasSize(areaMounted);
            assertThat(layer.getPropertyChangeListeners("preferredSize")).hasSize(preferredSizeListeners + 1);

            owner.close();
            plain.close();
            assertThat(pane.getComponentListeners()).as("close removes the layered pane's listener")
                .hasSize(plainRoot.getLayeredPane().getComponentListeners().length);
            assertThat(owner.area().getComponentListeners()).as("close removes the area's listener")
                .hasSize(plain.area().getComponentListeners().length);
            assertThat(layer.getPropertyChangeListeners("preferredSize")).as("close detaches from the layer")
                .hasSize(preferredSizeListeners);
        }
    }

    @Test void theLayerStaysBetweenTheStripesOverDockedWindowsAndUnderADockDrag() throws Exception {
        var owner = content(launcher(new ArrayDeque<>()));
        var model = new Contributions();
        owner.connectContributions(model, UiState.inMemory());
        model.addPanel("dev.x.sessions", "Sessions", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("sessions"));
        var notes = new JLabel("notes");
        model.addPanel("dev.x.notes", "Notes", new ImageIcon(), Slot.RIGHT_TOP, site -> notes);
        var layer = new JPanel();
        layer.setPreferredSize(new Dimension(200, 120));
        owner.mountCornerLayer(layer);
        var root = new JRootPane();
        try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
            owner.installRootBindings(root);
            var pane = root.getLayeredPane();
            owner.dock().show("dev.x.notes");
            root.setSize(1200, 800);
            layoutTree(root);
            layoutTree(root);
            // Component events are queued; the listener test pins that they relay the layer.
            owner.layoutCornerLayer();
            assertThat(owner.rightStripe().isVisible()).isTrue();
            Rectangle area = in(pane, owner.area()), stripe = in(pane, owner.rightStripe()), docked = in(pane, notes);
            assertThat(layer.getBounds()).as("flush with the area's corner")
                .isEqualTo(new Rectangle(area.x + area.width - 200, area.y + area.height - 120, 200, 120));
            assertThat(layer.getX() + layer.getWidth()).as("inside the right stripe").isEqualTo(stripe.x);
            assertThat(layer.getBounds().intersects(docked)).as("over the docked tool window").isTrue();
            assertThat(layer.getBounds().intersects(in(pane, owner.status()))).as("above the status bar").isFalse();

            // A dock drag's guides go on the drag layer above the cards, and a release over the cards still drops.
            AbstractButton sessions = owner.leftStripe().buttons().getFirst();
            Point start = center(owner, sessions);
            Point over = new Point(layer.getX() + layer.getWidth() / 2, layer.getY() + layer.getHeight() / 2);
            over = SwingUtilities.convertPoint(pane, over, owner);
            mouse(owner, sessions, MouseEvent.MOUSE_PRESSED, start);
            mouse(owner, sessions, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 10, start.y));
            mouse(owner, sessions, MouseEvent.MOUSE_DRAGGED, over);
            assertThat(owner.dockDrag().active()).isTrue();
            assertThat(owner.dockDrag().overlay().getParent()).isSameAs(pane);
            assertThat(JLayeredPane.getLayer(owner.dockDrag().overlay())).isGreaterThan(JLayeredPane.getLayer(layer));
            assertThat(layer.getParent()).as("the drag leaves the cards where they are").isSameAs(pane);
            assertThat(owner.dockDrag().target()).hasValueSatisfying(target -> assertThat(target.slot()).isEqualTo(Slot.BOTTOM_RIGHT));
            mouse(owner, sessions, MouseEvent.MOUSE_RELEASED, over);
            assertThat(owner.dock().slot("dev.x.sessions")).hasValue(Slot.BOTTOM_RIGHT);
            assertThat(owner.dockDrag().overlay().getParent()).isNull();
            assertThat(layer.getParent()).isSameAs(pane);

            // View > Rail hides the stripes: the area widens and the cards follow its corner.
            owner.setRailVisible(false);
            layoutTree(root);
            layoutTree(root);
            owner.layoutCornerLayer();
            area = in(pane, owner.area());
            assertThat(layer.getBounds()).isEqualTo(new Rectangle(area.x + area.width - 200, area.y + area.height - 120, 200, 120));
            assertThat(area.x + area.width).isEqualTo(in(pane, owner).x + owner.getWidth());
            owner.close();
        }
    }

    private static Rectangle in(JLayeredPane pane, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), pane);
    }

    private static Point center(JComponent owner, Component component) {
        Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), owner);
        return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
    }

    private static void mouse(JComponent owner, JComponent source, int id, Point inOwner) {
        Point at = SwingUtilities.convertPoint(owner, inOwner, source);
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
    }
}
