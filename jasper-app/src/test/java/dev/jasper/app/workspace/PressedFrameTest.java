package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Plan 12 Rulings 18 (spec 10.7): any press inside a flush frame activates it, as IntelliJ activates a tool window. */
@ExtendWith(EdtTestExtension.class)
class PressedFrameTest {
    final List<Component> focused = new ArrayList<>();
    final JPanel terminal = new JPanel();

    ToolWindowArea area(boolean flush, JComponent top) {
        EnterpriseLook.install(false);
        var area = new ToolWindowArea(terminal, new DockState(), EnterpriseLook.kit(), flush);
        area.focuser = focused::add;
        DockTestSupport.show(area, Slot.LEFT_TOP, top, 300);
        DockTestSupport.show(area, Slot.RIGHT_TOP, new JLabel("right"), 350);
        MockCrops.render(area, 1200, 763, 1);
        return area;
    }

    /** Runs what this EDT turn queued (the test method itself runs on the EDT). */
    static void flushEdt() {
        var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        SwingUtilities.invokeLater(loop::exit);
        loop.enter();
    }

    /** Review Focus 1. */
    @Test void aPressOnBlankSpaceOrTheHeaderStripActivatesTheFrame() throws Exception {
        try {
            var area = area(true, new JLabel("top"));
            area.pressed(area.content(Slot.LEFT_TOP).getParent());
            assertThat(area.focusedSlot()).hasValue(Slot.LEFT_TOP);
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isTrue();
            area.pressed(area.headerOf(Slot.RIGHT_TOP));
            assertThat(area.focusedSlot()).hasValue(Slot.RIGHT_TOP);
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void thePressMovesTheKeyboardToTheFramesFirstKeyedControlOrTheFrame() throws Exception {
        try {
            var content = new JPanel(new BorderLayout());
            var list = new JList<>(new String[] {"Mac"});
            content.add(new JLabel("Hosts"), BorderLayout.NORTH); content.add(list, BorderLayout.CENTER);
            var area = area(true, content);
            area.pressed(content);
            flushEdt();
            assertThat(focused).containsExactly(list);
            focused.clear();
            area.pressed(area.content(Slot.RIGHT_TOP));
            flushEdt();
            assertThat(focused).as("nothing keyed: the frame itself").containsExactly(area.content(Slot.RIGHT_TOP).getParent());
            assertThat(focused.getFirst().isFocusable()).isTrue();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 1. */
    @Test void aPressInTheTerminalHandsTheTintBack() throws Exception {
        try {
            var area = area(true, new JLabel("top"));
            area.pressed(area.content(Slot.LEFT_TOP));
            area.pressed(terminal);
            assertThat(area.focusedSlot()).isEmpty();
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 1 and Rulings 18: dragging a divider is not leaving the tool window, as IntelliJ's splitters are not. */
    @Test void aPressOnADividerKeepsTheActiveFrame() throws Exception {
        try {
            var area = area(true, new JLabel("top"));
            area.pressed(area.content(Slot.LEFT_TOP));
            var divider = area.divider(DockGeometry.Handle.LEFT_WIDTH);
            assertThat(divider.getWidth()).as("the left side's divider is laid out").isPositive();
            area.pressed(divider);
            assertThat(area.focusedSlot()).hasValue(Slot.LEFT_TOP);
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isTrue();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aRealMousePressReachesTheArea() throws Exception {
        try {
            var area = area(true, new JLabel("top"));
            var label = area.content(Slot.LEFT_TOP);
            area.addNotify();
            // Screen coordinates given: the area has a lightweight peer but no window, so none can be read from it.
            label.dispatchEvent(new MouseEvent(label, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 2, 2, 2, 2, 1, false, MouseEvent.BUTTON1));
            assertThat(area.focusedSlot()).hasValue(Slot.LEFT_TOP);
            area.removeNotify();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void openingFromTheStripeActivatesTheFrame() throws Exception {
        try {
            var area = area(true, new JLabel("top"));
            area.activate(DockTestSupport.id(Slot.RIGHT_TOP));
            flushEdt();
            assertThat(area.focusedSlot()).hasValue(Slot.RIGHT_TOP);
            assertThat(focused).containsExactly(area.content(Slot.RIGHT_TOP).getParent());
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void standardFramesIgnorePresses() throws Exception {
        try {
            var area = area(false, new JLabel("top"));
            area.pressed(area.content(Slot.LEFT_TOP));
            area.activate(DockTestSupport.id(Slot.LEFT_TOP));
            flushEdt();
            assertThat(area.focusedSlot()).isEmpty();
            assertThat(focused).isEmpty();
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
