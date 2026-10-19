package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.layout.Orientation;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Workspace tabs spec 10: a split of the workspace, its dividers and their drags (workspace plan 3a Rulings 9). */
class SplitContainerTest {
    private ThemeController themes;
    private final List<List<Integer>> paths = new ArrayList<>();
    private final List<List<Double>> reported = new ArrayList<>();

    @BeforeEach void theme() throws Exception { edt(() -> themes = ThemeTestSupport.controller(true)); }
    @AfterEach void restore() throws Exception { edt(() -> { themes.configure(true, TerminalSchemeChoice.MATCH); themes.close(); }); }

    /** A child whose minimum is {@code size} by {@code size + 10}. */
    private static JPanel child(int size) {
        var panel = new JPanel();
        panel.setMinimumSize(new Dimension(size, size + 10));
        panel.setPreferredSize(new Dimension(2 * size, 2 * size));
        return panel;
    }

    private SplitContainer split(Orientation orientation, List<Double> weights, JComponent... children) {
        return new SplitContainer(orientation, List.of(1, 0), weights, List.of(children), (path, values) -> {
            paths.add(path);
            reported.add(values);
        });
    }

    private static void mouse(JComponent component, int id, int x, int y) {
        component.dispatchEvent(new MouseEvent(component, id, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    @Test void childrenShareTheLengthByWeightWithADividerBetweenEachTwo() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10); var c = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.25, 0.25), a, b, c);
            int divider = SplitContainer.dividerSize();
            split.setSize(400 + 2 * divider, 300); split.doLayout();
            assertThat(a.getBounds()).isEqualTo(new Rectangle(0, 0, 200, 300));
            assertThat(split.divider(0).getBounds()).isEqualTo(new Rectangle(200, 0, divider, 300));
            assertThat(b.getBounds()).isEqualTo(new Rectangle(200 + divider, 0, 100, 300));
            assertThat(split.divider(1).getBounds()).isEqualTo(new Rectangle(300 + divider, 0, divider, 300));
            assertThat(c.getBounds()).isEqualTo(new Rectangle(300 + 2 * divider, 0, 100, 300));
            var stacked = split(Orientation.VERTICAL, List.of(0.75, 0.25), child(10), child(10));
            stacked.setSize(300, 200 + divider); stacked.doLayout();
            assertThat(stacked.children().get(0).getBounds()).isEqualTo(new Rectangle(0, 0, 300, 150));
            assertThat(stacked.children().get(1).getBounds()).isEqualTo(new Rectangle(0, 150 + divider, 300, 50));
            assertThat(stacked.weights()).containsExactly(0.75, 0.25);
            assertThat(stacked.path()).containsExactly(1, 0);
            assertThat(stacked.orientation()).isEqualTo(Orientation.VERTICAL);
            assertThat(stacked.divider(0).getCursor().getType()).isEqualTo(Cursor.N_RESIZE_CURSOR);
        });
    }

    @Test void aDividerDragMovesItsBoundaryAndReportsTheWeightsOnRelease() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10); var c = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.25, 0.25), a, b, c);
            int divider = SplitContainer.dividerSize();
            split.setSize(400 + 2 * divider, 300); split.doLayout();
            var rule = split.divider(1);
            mouse(rule, MouseEvent.MOUSE_PRESSED, 2, 50);
            mouse(rule, MouseEvent.MOUSE_DRAGGED, 2 - 40, 50);
            assertThat(b.getWidth()).as("laid out while dragging").isEqualTo(60);
            assertThat(c.getWidth()).isEqualTo(140);
            assertThat(a.getWidth()).as("the other children stay").isEqualTo(200);
            assertThat(reported).as("nothing is written before the release").isEmpty();
            // AWT delivers a drag's events relative to the divider where it is now, and it followed the pointer 40 left:
            // the pointer has not moved since the last drag event, so it is still 2 into the divider.
            mouse(rule, MouseEvent.MOUSE_RELEASED, 2, 50);
            assertThat(paths).containsExactly(List.of(1, 0));
            assertThat(reported).hasSize(1);
            assertThat(reported.getFirst().get(0)).isEqualTo(0.5);
            assertThat(reported.getFirst().get(1)).isCloseTo(0.15, within(1e-9));
            assertThat(reported.getFirst().get(2)).isCloseTo(0.35, within(1e-9));
            mouse(rule, MouseEvent.MOUSE_PRESSED, 2, 50);
            mouse(rule, MouseEvent.MOUSE_RELEASED, 2, 50);
            assertThat(reported).as("a click that moves nothing writes nothing").hasSize(1);
        });
    }

    @Test void aDragKeepsATenthOnEachSide() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.5), a, b);
            int divider = SplitContainer.dividerSize();
            split.setSize(1000 + divider, 300); split.doLayout();
            var rule = split.divider(0);
            mouse(rule, MouseEvent.MOUSE_PRESSED, 2, 5);
            mouse(rule, MouseEvent.MOUSE_DRAGGED, 2 + 2000, 5);
            assertThat(split.weights().get(0)).isCloseTo(0.9, within(1e-9));
            assertThat(b.getWidth()).as("the far side keeps a tenth").isEqualTo(100);
            mouse(rule, MouseEvent.MOUSE_DRAGGED, 2 - 2000, 5);
            assertThat(split.weights().get(0)).isCloseTo(0.1, within(1e-9));
            assertThat(a.getWidth()).isEqualTo(100);
            mouse(rule, MouseEvent.MOUSE_RELEASED, 2 - 2000, 5);
            assertThat(reported).singleElement().satisfies(values -> {
                assertThat(values.get(0)).isCloseTo(0.1, within(1e-9));
                assertThat(values.get(1)).isCloseTo(0.9, within(1e-9));
            });
            var narrow = split(Orientation.VERTICAL, List.of(0.05, 0.05, 0.9), child(1), child(1), child(1));
            narrow.setSize(100, 1000 + 2 * divider); narrow.doLayout();
            mouse(narrow.divider(0), MouseEvent.MOUSE_PRESSED, 5, 2);
            mouse(narrow.divider(0), MouseEvent.MOUSE_DRAGGED, 5, 300);
            mouse(narrow.divider(0), MouseEvent.MOUSE_RELEASED, 5, 300);
            assertThat(narrow.weights()).as("two neighbours under two tenths together cannot trade")
                .containsExactly(0.05, 0.05, 0.9);
            assertThat(reported).hasSize(1);
        });
    }

    @Test void weightsFromTheLayoutLayOutWithoutReporting() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.5), a, b);
            split.setSize(1000 + SplitContainer.dividerSize(), 300); split.doLayout();
            split.setWeights(List.of(0.3, 0.7)); split.doLayout();
            assertThat(a.getWidth()).isEqualTo(300);
            assertThat(split.weights()).containsExactly(0.3, 0.7);
            assertThat(reported).isEmpty();
        });
    }

    @Test void aThemeChangeKeepsTheDividers() throws Exception {
        edt(() -> {
            var a = child(10); var b = child(10);
            var split = split(Orientation.HORIZONTAL, List.of(0.7, 0.3), a, b);
            split.setSize(1000 + SplitContainer.dividerSize(), 300); split.doLayout();
            var before = a.getBounds();
            themes.configure(false, TerminalSchemeChoice.MATCH);
            SwingUtilities.updateComponentTreeUI(split); split.doLayout();
            assertThat(a.getBounds()).isEqualTo(before);
            assertThat(split.weights()).containsExactly(0.7, 0.3);
            assertThat(reported).isEmpty();
        });
    }

    @Test void theMinimumSizeAddsTheChildrenAlongAndTakesTheLargestAcross() throws Exception {
        edt(() -> {
            int divider = SplitContainer.dividerSize();
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.5), child(40), child(60));
            assertThat(split.getMinimumSize()).isEqualTo(new Dimension(100 + divider, 70));
            var stacked = split(Orientation.VERTICAL, List.of(0.5, 0.5), child(40), child(60));
            assertThat(stacked.getMinimumSize()).isEqualTo(new Dimension(60, 120 + divider));
        });
    }

    @Test void dividersPaintTheWindowBorderRuleAndStaySolidUnderGlass() throws Exception {
        edt(() -> {
            var split = split(Orientation.HORIZONTAL, List.of(0.5, 0.5), child(10), child(10));
            var surface = new Color(30, 40, 50);
            split.setBackground(surface);
            split.setGlass(true);
            assertThat(split.isOpaque()).as("translucent children show through").isFalse();
            split.setSize(200 + SplitContainer.dividerSize(), 100); split.doLayout();
            var rule = split.divider(0);
            assertThat(rule.isOpaque()).isTrue();
            assertThat(rule.getCursor().getType()).isEqualTo(Cursor.E_RESIZE_CURSOR);
            var image = new BufferedImage(rule.getWidth(), rule.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try { rule.paint(g); } finally { g.dispose(); }
            assertThat(new Color(image.getRGB(rule.getWidth() / 2, 50), true)).as("the rule")
                .isEqualTo(ThemeTestSupport.color(true, SemanticColor.BORDER_WINDOW));
            assertThat(new Color(image.getRGB(0, 50), true)).as("solid behind the rule").isEqualTo(surface);
            split.setGlass(false);
            assertThat(split.isOpaque()).isTrue();
        });
    }
}
