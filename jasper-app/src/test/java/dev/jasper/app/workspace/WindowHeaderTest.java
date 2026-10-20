package dev.jasper.app.workspace;

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
        SwingUtilities.invokeAndWait(() -> ThemeTestSupport.install(true));
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

    @Test void theFieldIsCentredAndTheToolbarTakesTheRightSide() {
        var header = header();
        layout(header, 1400);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(500, 4, 400, 22));
        assertThat(header.toolbar().getBounds()).isEqualTo(new Rectangle(912, 0, 488, 30));
        assertThat(header.toolbar().overflowButton().isVisible()).isFalse();
    }

    @Test void theFieldStaysCentredWhileToolbarButtonsOverflow() {
        var header = header();
        layout(header, 740);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(170, 4, 400, 22));
        assertThat(header.toolbar().getBounds()).isEqualTo(new Rectangle(582, 0, 158, 30));
        assertThat(header.toolbar().overflowButton().isVisible()).isTrue();
        layout(header, 540);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(70, 4, 400, 22));
        assertThat(header.toolbar().getWidth()).isEqualTo(58);
    }

    @Test void theFieldShrinksAroundTheCentreOnlyWhenASideCannotGiveWayAndThenCollapses() {
        var header = header();
        layout(header, 440);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(52, 4, 336, 22));
        assertThat(header.field().collapsed()).isFalse();
        layout(header, 250);
        assertThat(header.field().collapsed()).isTrue();
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(114, 4, 22, 22));
        layout(header, 1400);
        assertThat(header.field().collapsed()).isFalse();
        assertThat(header.field().getWidth()).isEqualTo(400);
    }

    @Test void aHiddenToolbarAndInsetsLeaveTheFieldCentred() {
        var header = header();
        header.toolbar().setVisible(false);
        layout(header, 900);
        assertThat(header.toolbar().getWidth()).isZero();
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(250, 4, 400, 22));
        header.toolbar().setVisible(true);
        header.setBorder(BorderFactory.createEmptyBorder(0, 100, 0, 50));
        layout(header, 1400);
        assertThat(header.field().getX()).isEqualTo(500);
        assertThat(header.toolbar().getX() + header.toolbar().getWidth()).isEqualTo(1350);
        assertThat(header.getMinimumSize().width).as("symmetric around the centre").isEqualTo(2 * (100 + 12) + 22);
    }

    @Test void theLeadingSideGetsTheRoomLeftOfTheFieldAndItsMinimumCanShrinkTheField() {
        var header = header();
        var room = new AtomicInteger(-1);
        header.setLeading(space -> { room.set(space); return Math.min(space, 300); }, () -> 100);
        layout(header, 1400);
        assertThat(room).hasValue(488);
        assertThat(header.field().getX()).isEqualTo(500);
        layout(header, 600);
        assertThat(header.field().getBounds()).isEqualTo(new Rectangle(112, 4, 376, 22));
        assertThat(room).hasValue(100);
    }

    @Test void theCentreIsTheRootPanesNotTheHeaders() {
        var header = header();
        var root = new JRootPane();
        var content = new JPanel(null);
        root.setContentPane(content);
        root.setSize(1400, 800); root.doLayout(); root.getLayeredPane().doLayout();
        content.add(header);
        header.setBounds(100, 0, 1000, 30); header.doLayout();
        assertThat(header.centre()).isEqualTo(600);
        assertThat(header.field().getX()).isEqualTo(600 - 200);
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
