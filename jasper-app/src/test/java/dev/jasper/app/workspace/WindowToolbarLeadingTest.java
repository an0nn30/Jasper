package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 10.3 and the mocks: the classic toolbar packs left with the classic spacing and gives way as the trailing one does. */
class WindowToolbarLeadingTest {
    static final WindowToolbar.Leading CLASSIC = new WindowToolbar.Leading(8, 8, 10, 8, 24);

    /** The overflow button styles itself from the installed theme: install one rather than rely on another class's. */
    @BeforeEach void installTheme() throws Exception { edt(() -> dev.jasper.app.appearance.ThemeTestSupport.install(false)); }

    static JButton button(WindowToolbar bar, String label, int width, int priority) {
        var button = new JButton(new AbstractAction(label) { @Override public void actionPerformed(ActionEvent event) { } }) {
            @Override public Dimension getPreferredSize() { return new Dimension(width, 24); }
        };
        WindowToolbar.entry(button, priority, () -> new JMenuItem(label));
        bar.add(button);
        return button;
    }

    @Test void controlsPackLeftWithTheMocksSpacing() throws Exception {
        edt(() -> {
            var bar = new WindowToolbar(() -> { });
            bar.packLeading(CLASSIC);
            bar.setBarHeight(34);
            var first = button(bar, "New Session", 96, 90);
            var rule = new JSeparator(SwingConstants.VERTICAL); bar.add(rule);
            var split = button(bar, "Split", 54, 90);
            var zoom = button(bar, "Maximize", 66, 10);
            var view = new JPanel(); view.setPreferredSize(new Dimension(300, 22)); WindowToolbar.view(view, 40);
            var plugin = new JSeparator(SwingConstants.VERTICAL); bar.add(plugin); bar.add(view);
            bar.finish();
            assertThat(bar.leading()).isTrue();
            assertThat(bar.fullWidth()).isEqualTo(8 + 96 + 21 + 54 + 8 + 66 + 21 + 300 + 8);
            bar.setSize(1000, 34); bar.doLayout();
            assertThat(first.getBounds()).as("8 from the edge, 24 tall, centred").isEqualTo(new Rectangle(8, 5, 96, 24));
            assertThat(rule.getBounds()).as("10 after the button, 18 tall, 8 from top and bottom").isEqualTo(new Rectangle(114, 8, 1, 18));
            assertThat(split.getX()).as("10 after the rule").isEqualTo(125);
            assertThat(zoom.getX()).as("8 after Split").isEqualTo(125 + 54 + 8);
            assertThat(view.getBounds()).as("a view keeps its height").isEqualTo(new Rectangle(187 + 66 + 21, 6, 300, 22));
            assertThat(bar.overflowButton().isVisible()).isFalse();
        });
    }

    @Test void aNarrowRowOverflowsTheLowestPriorityFirstAndThenHidesTheView() throws Exception {
        edt(() -> {
            var bar = new WindowToolbar(() -> { });
            bar.packLeading(CLASSIC);
            bar.setBarHeight(34);
            var first = button(bar, "New Session", 96, 90);
            var zoom = button(bar, "Maximize", 66, 10);
            var view = new JPanel(); view.setPreferredSize(new Dimension(300, 22)); WindowToolbar.view(view, 40);
            bar.add(new JSeparator(SwingConstants.VERTICAL)); bar.add(view);
            bar.finish();
            bar.setSize(8 + 96 + 21 + 300 + 8 + 24 + 8, 34); bar.doLayout();
            assertThat(zoom.isVisible()).as("Maximize (priority 10) moves into the menu before the view (40)").isFalse();
            assertThat(view.isVisible()).isTrue();
            assertThat(bar.overflowButton().getX()).as("the overflow button follows the last shown control").isEqualTo(8 + 96 + 21 + 300 + 8);
            bar.setSize(300, 34); bar.doLayout();
            assertThat(view.isVisible()).as("then the view hides; it never overflows").isFalse();
            assertThat(bar.overflowMenu().getComponentCount()).as("only Maximize is in the menu").isEqualTo(1);
            assertThat(bar.overflowButton().getX()).isEqualTo(8 + 96 + 8);
            assertThat(first.isVisible()).isTrue();
            bar.setSize(1000, 34); bar.doLayout();
            assertThat(view.isVisible() && zoom.isVisible()).as("room again").isTrue();
            assertThat(bar.overflowButton().isVisible()).isFalse();
        });
    }

    @Test void aTrailingRowLaysOutAsBefore() throws Exception {
        edt(() -> {
            var bar = new WindowToolbar(() -> { });
            var settings = button(bar, "Settings", 60, 100);
            bar.finish();
            bar.setSize(400, 30); bar.doLayout();
            assertThat(bar.leading()).isFalse();
            assertThat(settings.getX() + settings.getWidth()).as("packed right as today").isGreaterThan(300);
        });
    }
}
