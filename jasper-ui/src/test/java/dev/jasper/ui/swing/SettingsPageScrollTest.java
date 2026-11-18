package dev.jasper.ui.swing;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SettingsPageScrollTest {
    @Test void aPageScrollsByItsOwnUnitsForFractionalAndIntegralWheelEvents() {
        withPageScroller(new ScrollablePage(), pane -> {
            scroll(pane, 0, 0, 0.25);
            assertThat(pane.getViewport().getViewPosition()).isEqualTo(new Point(0, 6));

            scroll(pane, 0, 1, 1);
            assertThat(pane.getViewport().getViewPosition()).isEqualTo(new Point(0, 30));

            scroll(pane, InputEvent.SHIFT_DOWN_MASK, 1, 1);
            assertThat(pane.getViewport().getViewPosition()).isEqualTo(new Point(12, 30));

            scroll(pane, 0, -8, -8);
            assertThat(pane.getViewport().getViewPosition()).isEqualTo(new Point(12, 0));
        });
    }

    @Test void aPlainPageHasALineStepAndHonorsAnExplicitPixelStep() {
        withPageScroller(new JPanel(), pane -> {
            scroll(pane, 0, 1, 1);
            assertThat(pane.getViewport().getViewPosition().y).isEqualTo(16);

            pane.getVerticalScrollBar().setUnitIncrement(1);
            scroll(pane, 0, 1, 1);
            assertThat(pane.getViewport().getViewPosition().y).isEqualTo(17);
        });
    }

    private static void withPageScroller(JPanel page, java.util.function.Consumer<JScrollPane> check) {
        Edt.run(() -> {
            var previous = UIManager.getLookAndFeel();
            var context = KitFixtures.context();
            try {
                FlatLafInstaller.install(context.theme(), Kit.DEFAULT);
                var pane = SettingsShell.pageScroller(context);
                page.setPreferredSize(new Dimension(600, 600));
                pane.setViewportView(page);
                pane.setSize(240, 180);
                pane.doLayout();
                pane.getViewport().doLayout();
                check.accept(pane);
            } finally {
                context.binding().close();
                context.icons().close();
                try {
                    UIManager.setLookAndFeel(previous);
                } catch (javax.swing.UnsupportedLookAndFeelException failure) {
                    throw new AssertionError(failure);
                }
            }
        });
    }

    private static void scroll(JScrollPane pane, int modifiers, int rotation, double preciseRotation) {
        pane.dispatchEvent(new MouseWheelEvent(pane, MouseEvent.MOUSE_WHEEL, 1, modifiers, 30, 30, 30, 30, 0, false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation, preciseRotation));
    }

    private static final class ScrollablePage extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? 24 : 12;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }
        @Override public boolean getScrollableTracksViewportWidth() { return false; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
