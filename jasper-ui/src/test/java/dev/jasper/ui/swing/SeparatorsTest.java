package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's Separator: a hairline between two shown items of an INLINE or ACTIONS stack. */
@SuppressWarnings("try")
class SeparatorsTest {
    static final ControlState READY = ControlState.READY, HIDDEN = new ControlState(false, true, false, "");
    static Nodes.Button button(String id, ControlState state) { return new Nodes.Button(id, state, id, "", ButtonKind.GHOST, id, ""); }
    static ViewSpec bar(ControlState a, ControlState b, ControlState c) {
        return new ViewSpec(new Stack("bar", READY, LayoutRole.ACTIONS, List.of(button("a", a), new Separator("first", READY), button("b", b),
            new Separator("second", READY), button("c", c))));
    }
    static boolean shown(KitView view, String id) { return find(view, id, JComponent.class).isVisible(); }

    @Test void aSeparatorIsAHairlineBetweenItsNeighbours() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            var spec = new ViewSpec(new Stack("toolbar", READY, LayoutRole.INLINE, List.of(button("back", READY), new Separator("rule", READY), button("hidden-files", READY))));
            try (var view = new KitView(context, spec, e -> {})) {
                var image = KitFixtures.render(view, 400, 40, 1);
                var rule = find(view, "rule", JComponent.class);
                var back = find(view, "back", JComponent.class);
                var next = find(view, "hidden-files", JComponent.class);
                assertThat(rule.isVisible()).isTrue();
                assertThat(rule.getWidth()).isEqualTo(UIScale.scale(context.metrics().value(DesignMetrics.Role.HAIRLINE)));
                assertThat(rule.getHeight()).isEqualTo(UIScale.scale(context.metrics().value(DesignMetrics.Role.ICON_CONTROL)));
                assertThat(rule.getX()).isGreaterThanOrEqualTo(back.getX() + back.getWidth()).isLessThan(next.getX());
                assertThat(rule.getAccessibleContext().getAccessibleRole()).isEqualTo(javax.accessibility.AccessibleRole.SEPARATOR);
                var at = SwingUtilities.convertPoint(rule.getParent(), rule.getX(), rule.getY() + rule.getHeight() / 2, view);
                assertThat(image.getRGB(at.x, at.y)).isEqualTo(KitFixtures.marker(SemanticColor.BORDER_SUBTLE).getRGB());
            } finally { release(context); }
        });
    }

    @Test void aSeparatorWithNoShownItemOnOneSideIsHidden() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, bar(READY, READY, READY), e -> {})) {
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).containsExactly(true, true);
                view.update(bar(HIDDEN, READY, READY));
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).as("nothing shown before the first").containsExactly(false, true);
                view.update(bar(READY, HIDDEN, READY));
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).as("one rule between a and c").containsExactly(true, false);
                view.update(bar(READY, READY, HIDDEN));
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).as("nothing shown after the second").containsExactly(true, false);
                view.update(bar(READY, READY, READY));
                assertThat(List.of(shown(view, "first"), shown(view, "second"))).containsExactly(true, true);
                view.update(bar(HIDDEN, READY, READY));
                view.control("first").refresh();
                assertThat(shown(view, "first")).as("a refresh (an appearance change) keeps it hidden").isFalse();
            } finally { release(context); }
        });
    }
}
