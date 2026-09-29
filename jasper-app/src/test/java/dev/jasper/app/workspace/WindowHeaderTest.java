package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WindowHeaderTest {
    @BeforeAll static void theme() throws Exception {
        SwingUtilities.invokeAndWait(() -> ThemeTestSupport.install(Theme.DARK));
    }

    /** Four 60 px controls: full width 16 + 4 * 64 - 4 = 268, minimum 16 + 24 = 40 at a 30 px bar. */
    private static WindowHeader header() {
        var toolbar = new WindowToolbar(() -> { });
        for (int i = 0; i < 4; i++) {
            var action = new AbstractAction("Action " + i) { @Override public void actionPerformed(ActionEvent event) { } };
            var button = new JButton(action);
            button.setPreferredSize(new Dimension(60, 24));
            WindowToolbar.entry(button, i, () -> WindowToolbar.menuItem(action));
            toolbar.add(button);
        }
        toolbar.finish();
        return new WindowHeader(new TitleSearchField(() -> { }, () -> ""), toolbar);
    }

    private static void layout(WindowHeader header, int width) {
        header.setSize(width, 30); header.doLayout(); header.toolbar().doLayout();
    }

    @Test void theFieldIsCentredOnTheWindowAndTheToolbarPacksRight() {
        var header = header();
        layout(header, 1400);
        assertThat(header.toolbar().getBounds()).isEqualTo(new Rectangle(1400 - 268, 0, 268, 30));
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(420, 4, 560, 22));
        assertThat(header.field().collapsed()).isFalse();
        assertThat(header.toolbar().overflowButton().isVisible()).isFalse();
    }

    @Test void theFieldSlidesOffCentreBeforeAnythingShrinks() {
        var header = header();
        layout(header, 900);
        assertThat(header.toolbar().getWidth()).isEqualTo(268);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(60, 4, 560, 22));
    }

    @Test void toolbarButtonsOverflowThenTheFieldShrinksThenItCollapses() {
        var header = header();
        layout(header, 700);
        assertThat(header.toolbar().getWidth()).as("room after the field's preferred width").isEqualTo(116);
        assertThat(header.toolbar().overflowButton().isVisible()).isTrue();
        assertThat(header.field().getWidth()).as("the field has not shrunk yet").isEqualTo(560);
        layout(header, 500);
        assertThat(header.toolbar().getWidth()).isEqualTo(40);
        assertThat(header.field().getWidth()).isEqualTo(436);
        assertThat(header.field().collapsed()).isFalse();
        layout(header, 250);
        assertThat(header.field().collapsed()).isTrue();
        assertThat(header.field().getSize()).isEqualTo(new Dimension(22, 22));
        layout(header, 1400);
        assertThat(header.field().collapsed()).as("widening restores it").isFalse();
        assertThat(header.field().getWidth()).isEqualTo(560);
    }

    @Test void aHiddenToolbarLeavesTheFieldAndInsetsKeepClearOfNativeControls() {
        var header = header();
        header.toolbar().setVisible(false);
        layout(header, 900);
        assertThat(header.toolbar().getWidth()).isZero();
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(170, 4, 560, 22));
        header.toolbar().setVisible(true);
        header.setBorder(BorderFactory.createEmptyBorder(0, 100, 0, 50));
        layout(header, 1400);
        assertThat(header.toolbar().getX() + header.toolbar().getWidth()).isEqualTo(1350);
        assertThat(header.field().getX()).as("still centred on the whole header").isEqualTo(420);
        assertThat(header.getMinimumSize().width).isEqualTo(150 + 2 * 12 + 22 + 40);
        assertThat(header.getPreferredSize().height).isEqualTo(30);
    }

    @Test void barHeightChangesAreForwarded() {
        var header = header();
        var seen = new AtomicInteger();
        header.addPropertyChangeListener("barHeight", event -> seen.set((Integer) event.getNewValue()));
        header.toolbar().setBarHeight(40);
        assertThat(seen).hasValue(40);
        assertThat(header.getPreferredSize().height).isEqualTo(40);
    }
}
