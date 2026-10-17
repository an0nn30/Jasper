package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.swing.ChromePainter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: flush frames are divided by 1 px rules with 6 px drag areas, and the focused frame is known. */
@ExtendWith(EdtTestExtension.class)
class FlushFramesTest {
    private static ToolWindowArea area(boolean flush) {
        EnterpriseLook.install(false);
        var area = new ToolWindowArea(new JPanel(), new DockState(), EnterpriseLook.kit(), flush);
        DockTestSupport.show(area, Slot.LEFT_TOP, new JLabel("top"), 300);
        DockTestSupport.show(area, Slot.LEFT_BOTTOM, new JLabel("bottom"), 300);
        DockTestSupport.show(area, Slot.RIGHT_TOP, new JLabel("right"), 350);
        return area;
    }

    private static Rectangle frame(ToolWindowArea area, Slot slot) { return area.content(slot).getParent().getBounds(); }

    private static void mouse(Component target, int id, int x, int y) {
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    @Test void flushFramesAreDividedByPaintedOnePixelRules() throws Exception {
        try {
            var area = area(true);
            var image = MockCrops.render(area, 1200, 763, 1);
            var rule = new ChromePainter(EnterpriseLook.kit()).dockRule(EnterpriseLook.theme(false));
            Rectangle top = frame(area, Slot.LEFT_TOP), bottom = frame(area, Slot.LEFT_BOTTOM);
            int x = top.x + top.width, y = top.y + top.height;
            assertThat(area.metrics().divider()).isEqualTo(1);
            assertThat(new Color(image.getRGB(x, 200))).as("the rule beside the left frames").isEqualTo(rule);
            assertThat(new Color(image.getRGB(x + 1, 200))).as("one pixel wide").isNotEqualTo(rule);
            assertThat(new Color(image.getRGB(100, y))).as("the rule between the left frames").isEqualTo(rule);
            assertThat(bottom.y).isEqualTo(y + 1);
            assertThat(top.x).as("no gutter in the classic catalog").isZero();
            JComponent handle = area.divider(DockGeometry.Handle.LEFT_WIDTH);
            assertThat(handle.getWidth()).as("a 6 px drag area").isEqualTo(6);
            assertThat(handle.getX()).isLessThan(x);
            assertThat(handle.getX() + handle.getWidth()).isGreaterThan(x + 1);
            assertThat(area.getComponentZOrder(handle)).as("above the frames").isLessThan(area.getComponentZOrder(area.content(Slot.LEFT_TOP).getParent()));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void draggingFromTheEdgeOfADragAreaMovesTheRuleByTheDrag() throws Exception {
        try {
            for (boolean flush : new boolean[] {true, false}) {
                var area = area(flush);
                MockCrops.render(area, 1200, 763, 1);
                JComponent handle = area.divider(DockGeometry.Handle.LEFT_WIDTH);
                mouse(handle, MouseEvent.MOUSE_PRESSED, 0, 100);
                mouse(handle, MouseEvent.MOUSE_DRAGGED, 40, 100);
                mouse(handle, MouseEvent.MOUSE_RELEASED, 40, 100);
                assertThat(area.state().size(Side.LEFT)).as(flush ? "flush" : "standard").isEqualTo(340);
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void theFrameHoldingFocusIsTheFocusedOne() throws Exception {
        try {
            var area = area(true);
            MockCrops.render(area, 1200, 763, 1);
            area.focusMoved(area.content(Slot.LEFT_TOP));
            assertThat(area.focusedSlot()).hasValue(Slot.LEFT_TOP);
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isTrue();
            assertThat(area.headerOf(Slot.RIGHT_TOP).focused()).isFalse();
            area.focusMoved(area.content(Slot.RIGHT_TOP));
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isFalse();
            assertThat(area.headerOf(Slot.RIGHT_TOP).focused()).isTrue();
            area.focusMoved(new JPanel());
            assertThat(area.focusedSlot()).as("focus in the terminal: no frame").isEmpty();
            var standard = area(false);
            standard.focusMoved(standard.content(Slot.LEFT_TOP));
            assertThat(standard.focusedSlot()).as("standard frames track no focus").isEmpty();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void standardFramesKeepTheirSixPixelGapsAndPaintNoRule() throws Exception {
        try {
            var area = area(false);
            var image = MockCrops.render(area, 1200, 763, 1);
            Rectangle top = frame(area, Slot.LEFT_TOP), right = frame(area, Slot.RIGHT_TOP);
            assertThat(area.metrics().divider()).isEqualTo(6);
            assertThat(area.divider(DockGeometry.Handle.LEFT_WIDTH).getWidth()).isEqualTo(6);
            var app = EnterpriseLook.theme(false).semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP);
            assertThat(new Color(image.getRGB(top.x + top.width + 3, 200))).as("the gap shows the window surface").isEqualTo(app);
            assertThat(right.x).as("a 6 px gap before the right frame").isGreaterThanOrEqualTo(top.x + top.width + 6);
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
