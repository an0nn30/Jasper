package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** The stripe during a dock drag: all three groups, the lifted button, the gap, and the groups a drag resolves. */
@ExtendWith(EdtTestExtension.class)
class ToolStripeDragTest {
    private static int s(int value) { return UIScale.scale(value); }

    /** Left stripe, 400px tall: a and b on top, c at the foot, one rail action. */
    private static ToolStripe stripe() {
        var stripe = new ToolStripe(Side.LEFT);
        stripe.render(List.of(new ToolStripe.Button("a", "A", null, Slot.LEFT_TOP, true), new ToolStripe.Button("b", "B", null, Slot.LEFT_TOP, false),
            new ToolStripe.Button("c", "C", null, Slot.BOTTOM_LEFT, false)), List.of(new AbstractAction("Open") {
                @Override public void actionPerformed(ActionEvent event) { }
            }));
        stripe.setSize(stripe.getPreferredSize().width, s(400));
        stripe.doLayout();
        return stripe;
    }

    private static AbstractButton button(ToolStripe stripe, String tip) {
        return stripe.buttons().stream().filter(button -> tip.equals(button.getToolTipText())).findFirst().orElseThrow();
    }

    private static List<Rectangle> separators(ToolStripe stripe) {
        return Arrays.stream(stripe.getComponents()).filter(JSeparator.class::isInstance).map(Component::getBounds)
            .filter(bounds -> !bounds.isEmpty()).toList();
    }

    @Test void aDragShowsAllThreeGroupsAndLiftsTheDraggedButton() {
        var stripe = stripe();
        int step = s(22) + s(4);
        assertThat(separators(stripe)).as("normally only the actions' separator").hasSize(1);
        stripe.beginDrag("a");
        assertThat(stripe.dragging()).isTrue();
        assertThat(button(stripe, "A").getBounds().isEmpty()).as("the dragged button is lifted out").isTrue();
        assertThat(button(stripe, "A").isVisible()).as("but stays a visible child, so its drag events keep coming").isTrue();
        assertThat(button(stripe, "B").getY()).isEqualTo(s(4));
        assertThat(separators(stripe)).as("the second group's separator joins").hasSize(2);
        assertThat(button(stripe, "Open").getY()).as("after the top group's kept step and the empty second group's drop space")
            .isEqualTo(s(4) + 2 * step + s(8) + step + s(8));
        assertThat(button(stripe, "C").getY()).isEqualTo(s(400) - s(4) - s(22));
        assertThat(stripe.gapBounds()).isEmpty();
    }

    @Test void theDropGroupsSplitTheStripeAndLeaveTheLiftedButtonOut() {
        var stripe = stripe();
        stripe.beginDrag("a");
        List<DockDropResolver.Group> groups = stripe.dropGroups();
        assertThat(groups).extracting(DockDropResolver.Group::slot).containsExactly(Slot.LEFT_TOP, Slot.LEFT_BOTTOM, Slot.BOTTOM_LEFT);
        int width = stripe.getWidth(), step = s(22) + s(4);
        int split = s(4) + 2 * step + s(4), end = s(4) + 2 * step + s(8) + step + s(8) + step, footTop = s(400) - s(4) - s(22);
        int boundary = (end + footTop) / 2;
        assertThat(groups.get(0).bounds()).as("the top group keeps the lifted button's step").isEqualTo(new Rectangle(0, 0, width, split));
        assertThat(groups.get(0).buttons()).containsExactly(new Rectangle(s(3), s(4), s(22), s(22)));
        assertThat(groups.get(1).bounds()).isEqualTo(new Rectangle(0, split, width, boundary - split));
        assertThat(groups.get(1).buttons()).isEmpty();
        assertThat(groups.get(2).bounds()).isEqualTo(new Rectangle(0, boundary, width, s(400) - boundary));
        assertThat(groups.get(2).buttons()).containsExactly(new Rectangle(s(3), footTop, s(22), s(22)));
    }

    /** Left stripe, 400px tall: a, b and c on top, d in the second group. */
    private static ToolStripe threeAndOne() {
        var stripe = new ToolStripe(Side.LEFT);
        stripe.render(List.of(new ToolStripe.Button("a", "A", null, Slot.LEFT_TOP, true), new ToolStripe.Button("b", "B", null, Slot.LEFT_TOP, false),
            new ToolStripe.Button("c", "C", null, Slot.LEFT_TOP, false), new ToolStripe.Button("d", "D", null, Slot.LEFT_BOTTOM, false)), List.of());
        stripe.setSize(stripe.getPreferredSize().width, s(400));
        stripe.doLayout();
        return stripe;
    }

    @Test void theLiftedButtonKeepsItsStepSoLaterGroupsStayPutAndAGapThereUsesIt() {
        var stripe = threeAndOne();
        int step = s(22) + s(4);
        Rectangle home = button(stripe, "C").getBounds();
        stripe.beginDrag("z");
        Rectangle secondGroupFirst = button(stripe, "D").getBounds();
        stripe.endDrag();
        stripe.beginDrag("c");
        assertThat(button(stripe, "C").getBounds().isEmpty()).as("lifted out").isTrue();
        assertThat(button(stripe, "D").getBounds()).as("the second group does not slide up into the lifted button's old square")
            .isEqualTo(secondGroupFirst);
        assertThat(stripe.dropGroups().get(0).bounds().contains(new Point((int) home.getCenterX(), (int) home.getCenterY())))
            .as("the old square still belongs to the top group").isTrue();
        assertThat(stripe.dropGroups().get(0).buttons()).as("but it is not one of the group's buttons").hasSize(2);
        stripe.showGap(Slot.LEFT_TOP, 2);
        assertThat(stripe.gapBounds()).as("a gap at the home index is the old square").hasValue(home);
        assertThat(button(stripe, "D").getBounds()).as("the gap used the kept step; nothing grows").isEqualTo(secondGroupFirst);
        stripe.showGap(Slot.LEFT_BOTTOM, 0);
        assertThat(stripe.gapBounds()).as("a gap in another group leaves the kept step empty above it").hasValue(secondGroupFirst);
        assertThat(button(stripe, "D").getY()).isEqualTo(secondGroupFirst.y + step);
    }

    @Test void theGapOpensAtTheIndexInTheTargetGroup() {
        var stripe = stripe();
        int step = s(22) + s(4);
        stripe.beginDrag("a");
        stripe.showGap(Slot.LEFT_TOP, 0);
        assertThat(stripe.gapBounds()).hasValue(new Rectangle(s(3), s(4), s(22), s(22)));
        assertThat(button(stripe, "B").getY()).as("the buttons part").isEqualTo(s(4) + step);
        stripe.showGap(Slot.LEFT_BOTTOM, 0);
        assertThat(stripe.gapBounds()).as("in the empty second group's drop space, after the top group's kept step")
            .hasValue(new Rectangle(s(3), s(4) + 2 * step + s(8), s(22), s(22)));
        assertThat(button(stripe, "B").getY()).isEqualTo(s(4));
        stripe.showGap(Slot.BOTTOM_LEFT, 0);
        assertThat(stripe.gapBounds()).as("the foot grows upward").hasValue(new Rectangle(s(3), s(400) - s(4) - s(22) - step, s(22), s(22)));
        assertThat(button(stripe, "C").getY()).isEqualTo(s(400) - s(4) - s(22));
        stripe.showGap(Slot.BOTTOM_LEFT, 7);
        assertThat(stripe.gapBounds()).as("an index past the end clamps").hasValue(new Rectangle(s(3), s(400) - s(4) - s(22), s(22), s(22)));
        stripe.showGap(Slot.RIGHT_TOP, 0);
        assertThat(stripe.gapBounds()).as("a target on the other stripe").isEmpty();
        stripe.showGap(null, 0);
        assertThat(stripe.gapBounds()).isEmpty();
    }

    @Test void endingTheDragRestoresTheNormalStripe() {
        var stripe = stripe();
        stripe.beginDrag("a");
        stripe.showGap(Slot.LEFT_BOTTOM, 0);
        stripe.endDrag();
        assertThat(stripe.dragging()).isFalse();
        assertThat(stripe.gapBounds()).isEmpty();
        assertThat(separators(stripe)).hasSize(1);
        assertThat(Arrays.stream(stripe.getComponents()).filter(JSeparator.class::isInstance)).as("the drag's separator is gone").hasSize(1);
        assertThat(button(stripe, "A").getBounds()).isEqualTo(new Rectangle(s(3), s(4), s(22), s(22)));
        stripe.showGap(Slot.LEFT_TOP, 0);
        assertThat(stripe.gapBounds()).as("no gap outside a drag").isEmpty();
    }

    @Test void aDragFromTheOtherStripeLiftsNothingHereAndARenderEndsTheDragView() {
        var stripe = stripe();
        stripe.beginDrag("z");
        assertThat(button(stripe, "A").getBounds().isEmpty()).isFalse();
        stripe.render(List.of(), List.of());
        assertThat(stripe.dragging()).isFalse();
    }

    @Test void dragSourcesHearOfEveryWindowButtonButNoAction() {
        List<String> seen = new ArrayList<>();
        var stripe = new ToolStripe(Side.RIGHT);
        stripe.dragSource = (component, id) -> seen.add(((AbstractButton) component).getToolTipText() + ":" + id);
        stripe.render(List.of(new ToolStripe.Button("x", "X", null, Slot.RIGHT_TOP, false),
            new ToolStripe.Button("y", "Y", null, Slot.BOTTOM_RIGHT, false)), List.of(new AbstractAction("Open") {
                @Override public void actionPerformed(ActionEvent event) { }
            }));
        assertThat(seen).containsExactly("X:x", "Y:y");
    }

    @Test void theAreaOffersEveryNonControlHeaderSurfaceAsADragSource() {
        List<String> seen = new ArrayList<>();
        var area = new ToolWindowArea(new JPanel(), new DockState());
        area.dragSource = (component, id) -> seen.add((component instanceof ToolWindowHeader ? "header" : component instanceof JLabel ? "label" : "empty-space") + ":" + id);
        DockTestSupport.show(area, Slot.LEFT_TOP, new JLabel("hosts"), 260);
        assertThat(seen).containsExactly("header:test.left-top", "label:test.left-top", "label:test.left-top", "empty-space:test.left-top");
    }
}
