package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JSeparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@ExtendWith(EdtTestExtension.class)
class ToolStripeTest {
    private static int s(int value) { return UIScale.scale(value); }

    private static ToolStripe.Button window(String id, Slot slot) { return new ToolStripe.Button(id, id.toUpperCase(), null, slot, false); }

    private static List<Rectangle> separators(ToolStripe stripe) {
        return Arrays.stream(stripe.getComponents()).filter(JSeparator.class::isInstance).map(Component::getBounds).toList();
    }

    private static Action action(String name, List<String> events) {
        return new AbstractAction(name) {
            @Override public void actionPerformed(ActionEvent event) { events.add(name); }
        };
    }

    private static void layout(ToolStripe stripe, int height) {
        stripe.setSize(stripe.getPreferredSize().width, s(height));
        stripe.doLayout();
    }

    @Test void theLeftStripeStacksItsGroupsPutsActionsAfterTheSecondAndPinsTheFoot() {
        var stripe = new ToolStripe(Side.LEFT);
        stripe.render(List.of(window("a", Slot.LEFT_TOP), window("c", Slot.LEFT_BOTTOM), window("d", Slot.BOTTOM_LEFT),
            window("b", Slot.LEFT_TOP), window("x", Slot.RIGHT_TOP)), List.of(action("Open", new ArrayList<>())));
        layout(stripe, 400);
        List<AbstractButton> buttons = stripe.buttons();
        assertThat(buttons).extracting(AbstractButton::getToolTipText).as("the right stripe's window is not here")
            .containsExactly("A", "B", "C", "Open", "D");
        int square = s(22), step = square + s(4);
        assertThat(buttons.get(0).getBounds()).isEqualTo(new Rectangle(s(3), s(4), square, square));
        assertThat(buttons.get(1).getY()).isEqualTo(s(4) + step);
        assertThat(separators(stripe)).hasSize(2);
        assertThat(separators(stripe).get(0)).isEqualTo(new Rectangle(s(7), s(4) + 2 * step + s(3), s(14), s(1)));
        assertThat(buttons.get(2).getY()).as("the second group under its separator").isEqualTo(s(4) + 2 * step + s(8));
        assertThat(buttons.get(3).getY()).as("actions behind their own separator").isEqualTo(s(4) + 3 * step + 2 * s(8));
        assertThat(buttons.get(4).getBounds()).as("the foot is pinned to the bottom")
            .isEqualTo(new Rectangle(s(3), s(400) - s(4) - square, square, square));
        layout(stripe, 600);
        assertThat(buttons.get(4).getY()).isEqualTo(s(600) - s(4) - square);
    }

    @Test void separatorsAppearOnlyForASecondGroupOrForActionsBelowWindows() {
        var stripe = new ToolStripe(Side.LEFT);
        stripe.render(List.of(window("a", Slot.LEFT_TOP), window("d", Slot.BOTTOM_LEFT)), List.of());
        layout(stripe, 400);
        assertThat(separators(stripe)).isEmpty();
        stripe.render(List.of(), List.of(action("Open", new ArrayList<>())));
        layout(stripe, 400);
        assertThat(separators(stripe)).as("actions alone need no separator").isEmpty();
        stripe.render(List.of(window("c", Slot.LEFT_BOTTOM)), List.of());
        layout(stripe, 400);
        assertThat(separators(stripe)).as("the second group keeps its separator").singleElement()
            .isEqualTo(new Rectangle(s(7), s(4) + s(3), s(14), s(1)));
        assertThat(stripe.buttons().getFirst().getY()).isEqualTo(s(4) + s(8));
    }

    @Test void theRightStripeShowsTheRightSlotsAndAnEmptyStripeIsEmpty() {
        var stripe = new ToolStripe(Side.RIGHT);
        assertThat(stripe.empty()).isTrue();
        assertThat(stripe.getAccessibleContext().getAccessibleName()).isEqualTo("Right tool windows");
        stripe.render(List.of(window("a", Slot.RIGHT_TOP), window("b", Slot.RIGHT_BOTTOM), window("c", Slot.BOTTOM_RIGHT),
            window("l", Slot.LEFT_TOP), window("f", Slot.BOTTOM_LEFT)), List.of());
        layout(stripe, 300);
        assertThat(stripe.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("A", "B", "C");
        assertThat(stripe.buttons().get(2).getY()).isEqualTo(s(300) - s(4) - s(22));
        assertThat(stripe.empty()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolStripe(Side.BOTTOM));
    }

    @Test void aClickTogglesAndTheRightClickMenuMovesOrHides() {
        List<String> events = new ArrayList<>();
        var stripe = new ToolStripe(Side.LEFT);
        stripe.onToggle = id -> events.add("toggle:" + id);
        stripe.onMove = (id, slot) -> events.add("move:" + id + ":" + slot);
        stripe.onHide = id -> events.add("hide:" + id);
        stripe.render(List.of(new ToolStripe.Button("a", "A", null, Slot.LEFT_TOP, true),
            new ToolStripe.Button("b", "B", null, Slot.BOTTOM_LEFT, false)), List.of(action("Open", events)));
        var buttons = stripe.buttons();
        assertThat(buttons.get(0).isSelected()).isTrue();
        assertThat(buttons.get(2).isSelected()).isFalse();
        buttons.get(0).doClick();
        buttons.get(1).doClick();
        var menu = buttons.get(0).getComponentPopupMenu();
        ToolWindowHeaderTest.item(menu, "Right Bottom").doClick();
        ToolWindowHeaderTest.item(menu, "Hide").doClick();
        assertThat(ToolWindowHeaderTest.item(buttons.get(2).getComponentPopupMenu(), "Hide").isEnabled()).isFalse();
        assertThat(buttons.get(1).getComponentPopupMenu()).as("an action has no window menu").isNull();
        assertThat(events).containsExactly("toggle:a", "Open", "move:a:RIGHT_BOTTOM", "hide:a");
    }

    @Test void aRightClickMenuChecksTheButtonsModeAndChoosingAnotherSetsIt() {
        List<String> events = new ArrayList<>();
        var stripe = new ToolStripe(Side.LEFT);
        stripe.onMode = (id, mode) -> events.add("mode:" + id + ":" + mode);
        stripe.render(List.of(new ToolStripe.Button("a", "A", null, Slot.LEFT_TOP, false, Optional.empty(), OptionalInt.empty(), ToolWindowMode.FLOAT),
            new ToolStripe.Button("b", "B", null, Slot.LEFT_TOP, true)), List.of());
        var floating = stripe.buttons().get(0).getComponentPopupMenu();
        assertThat(((javax.swing.JCheckBoxMenuItem) ToolWindowHeaderTest.item(floating, "Float")).isSelected()).isTrue();
        assertThat(((javax.swing.JCheckBoxMenuItem) ToolWindowHeaderTest.item(floating, "Docked")).isSelected()).isFalse();
        ToolWindowHeaderTest.item(floating, "Float").doClick();
        assertThat(events).as("the current mode does nothing").isEmpty();
        ToolWindowHeaderTest.item(floating, "Docked").doClick();
        ToolWindowHeaderTest.item(floating, "Window").doClick();
        assertThat(events).containsExactly("mode:a:DOCKED", "mode:a:WINDOW");
        assertThat(((javax.swing.JCheckBoxMenuItem) ToolWindowHeaderTest.item(stripe.buttons().get(1).getComponentPopupMenu(), "Docked")).isSelected())
            .as("a button built without a mode is docked").isTrue();
    }

    @Test void aBadgeChangeKeepsTheButtonsMode() {
        var stripe = new ToolStripe(Side.LEFT);
        stripe.render(List.of(new ToolStripe.Button("a", "A", null, Slot.LEFT_TOP, false, Optional.empty(), OptionalInt.empty(), ToolWindowMode.WINDOW)), List.of());
        stripe.updateBadge("a", Optional.of(new PanelBadge(1, PanelBadge.Tone.ACCENT, "1 new item")));
        assertThat(stripe.window("a")).hasValueSatisfying(window -> {
            assertThat(window.badge()).as("the badge changed").isPresent();
            assertThat(window.mode()).as("the mode is kept").isEqualTo(ToolWindowMode.WINDOW);
        });
        assertThat(stripe.window("x")).as("a window this stripe does not show").isEmpty();
    }

    @Test void theStripeLaysOutItsButtonsFromItsWidth() {
        var stripe = new ToolStripe(Side.RIGHT);
        stripe.render(List.of(window("a", Slot.RIGHT_TOP), window("b", Slot.RIGHT_BOTTOM)), List.of());
        for (int width : new int[]{28, 36, 64}) {
            stripe.setRailWidth(width);
            assertThat(stripe.getPreferredSize().width).as("width %d", width).isEqualTo(s(width));
            assertThat(stripe.getMinimumSize().width).isEqualTo(s(width));
            layout(stripe, 400);
            int side = s(width - 6);
            assertThat(stripe.buttons().get(0).getBounds()).isEqualTo(new Rectangle(s(3), s(4), side, side));
            assertThat(separators(stripe).getFirst().width).isEqualTo(s(width - 14));
            assertThat(stripe.buttons().get(1).getY()).as("a button, then a separator").isEqualTo(s(4) + side + s(4) + s(8));
            assertThat(stripe.buttons().get(1).getWidth()).isEqualTo(side);
        }
        for (var button : stripe.buttons())
            assertThat(((java.util.Map<?, ?>) button.getClientProperty("FlatLaf.style")).get("arc")).as("the rail keeps its button arc").isEqualTo(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.RAIL_BUTTON_ARC));
    }

    @Test void aBadgeUpdateChangesOneButtonInPlaceAndLeavesTheStripeAndItsDragAlone() {
        var stripe = new ToolStripe(Side.LEFT);
        var icon = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
        stripe.render(List.of(new ToolStripe.Button("a", "A", icon, Slot.LEFT_TOP, false),
            new ToolStripe.Button("b", "B", icon, Slot.LEFT_TOP, true),
            new ToolStripe.Button("x", "X", icon, Slot.RIGHT_TOP, false)), List.of());
        layout(stripe, 400);
        stripe.beginDrag("b");
        stripe.showGap(Slot.LEFT_TOP, 0);
        stripe.doLayout();
        AbstractButton a = stripe.buttons().get(0), b = stripe.buttons().get(1);
        Component[] children = stripe.getComponents();
        Rectangle gap = stripe.gapBounds().orElseThrow(), aBounds = a.getBounds();
        assertThat(b.getWidth()).as("lifted").isZero();

        var badge = new PanelBadge(3, PanelBadge.Tone.ERROR, "3 unread notifications, 1 error");
        stripe.updateBadge("a", Optional.of(badge));
        stripe.updateBadge("b", Optional.of(badge));
        stripe.updateBadge("x", Optional.of(badge));
        stripe.updateBadge("nobody", Optional.of(badge));

        assertThat(stripe.getComponents()).as("nothing rebuilt").containsExactly(children);
        assertThat(stripe.buttons()).containsExactly(a, b);
        assertThat(stripe.dragging()).isTrue();
        assertThat(stripe.gapBounds()).hasValue(gap);
        assertThat(a.getBounds()).as("no layout ran").isEqualTo(aBounds);
        assertThat(b.getWidth()).as("still lifted").isZero();
        assertThat(a.getIcon()).isInstanceOfSatisfying(BadgedIcon.class, badged -> assertThat(badged.tone()).isEqualTo(PanelBadge.Tone.ERROR));
        assertThat(a.getToolTipText()).isEqualTo("A (3 unread notifications, 1 error)");
        assertThat(a.getAccessibleContext().getAccessibleName()).isEqualTo("A");
        assertThat(a.getAccessibleContext().getAccessibleDescription()).isEqualTo("3 unread notifications, 1 error");
        assertThat(b.getIcon()).isInstanceOf(BadgedIcon.class);
        assertThat(a.isSelected()).isFalse();
        assertThat(b.isSelected()).as("a badge leaves the selection alone").isTrue();

        stripe.updateBadge("a", Optional.empty());
        assertThat(a.getIcon()).isSameAs(icon);
        assertThat(a.getToolTipText()).isEqualTo("A");
        assertThat(a.getAccessibleContext().getAccessibleDescription()).as("falls back to the tooltip, which has no badge").isEqualTo("A");

        stripe.endDrag();
        stripe.render(List.of(new ToolStripe.Button("a", "A", icon, Slot.LEFT_TOP, false)), List.of());
        stripe.updateBadge("b", Optional.of(badge));
        assertThat(stripe.buttons()).as("a render forgets the windows it dropped").hasSize(1);
        assertThat(stripe.buttons().getFirst().getIcon()).isSameAs(icon);
    }

    @Test void refreshingTheActionsRereadsTheirNamesInPlaceAndLeavesTheStripeAndItsDragAlone() {
        var stripe = new ToolStripe(Side.LEFT);
        Action open = action("Open", new ArrayList<>()), other = action("Other", new ArrayList<>());
        stripe.render(List.of(window("a", Slot.LEFT_TOP), window("b", Slot.LEFT_TOP)), List.of(open, other));
        layout(stripe, 400);
        stripe.beginDrag("b");
        stripe.showGap(Slot.LEFT_TOP, 0);
        stripe.doLayout();
        AbstractButton b = stripe.buttons().get(1), openButton = stripe.buttons().get(2), otherButton = stripe.buttons().get(3);
        Component[] children = stripe.getComponents();
        Rectangle gap = stripe.gapBounds().orElseThrow(), openBounds = openButton.getBounds();
        assertThat(b.getWidth()).as("lifted").isZero();

        open.putValue(Action.NAME, "Open Now");
        open.setEnabled(false);
        stripe.refreshActions();

        assertThat(stripe.getComponents()).as("nothing rebuilt").containsExactly(children);
        assertThat(stripe.buttons().get(2)).as("the same button").isSameAs(openButton);
        assertThat(openButton.getToolTipText()).isEqualTo("Open Now");
        assertThat(openButton.getAccessibleContext().getAccessibleName()).isEqualTo("Open Now");
        assertThat(openButton.isEnabled()).isFalse();
        assertThat(otherButton.getToolTipText()).isEqualTo("Other");
        assertThat(otherButton.isEnabled()).isTrue();
        assertThat(stripe.dragging()).isTrue();
        assertThat(stripe.gapBounds()).hasValue(gap);
        assertThat(openButton.getBounds()).as("no layout ran").isEqualTo(openBounds);
        assertThat(b.getWidth()).as("still lifted").isZero();

        stripe.endDrag();
        stripe.render(List.of(), List.of());
        stripe.refreshActions();
        assertThat(stripe.buttons()).as("a render forgets the actions it dropped").isEmpty();
    }
}
