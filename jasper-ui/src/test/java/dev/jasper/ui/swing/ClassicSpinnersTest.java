package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's stepper point: the classic face is a spinner, and a SLIDER draws the same. */
@SuppressWarnings("try")
class ClassicSpinnersTest {
    static Nodes.Field number(String id, Nodes.FieldPresentation presentation) {
        return new Nodes.Field(id, ControlState.READY, Nodes.FieldKind.NUMBER, "34", "px", "Toolbar height", "", Optional.of(new Nodes.Numeric(28, 64, 1, 0)), presentation);
    }

    @Test void aSpinnerIsARightAlignedDataValueWithStackedArrows() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(number("height", Nodes.FieldPresentation.STEPPER)), e -> {})) {
                KitFixtures.capture(context, view.spec(), 200, 40, "spinner");
                KitFixtures.render(view, 200, 40, 1);
                var up = find(view, "height.plus", JButton.class); var down = find(view, "height.minus", JButton.class);
                assertThat(up.getX()).as("stacked").isEqualTo(down.getX());
                assertThat(up.getY()).isLessThan(down.getY());
                assertThat(up.getWidth()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.SPINNER_ARROW_WIDTH));
                var input = (JTextField) ((TextFields.FieldControl) view.control("height")).input;
                assertThat(input.getHorizontalAlignment()).isEqualTo(SwingConstants.RIGHT);
                assertThat(input.getFont()).isEqualTo(ThemePainting.font(context, dev.jasper.ui.metrics.Typography.Role.MONO));
                up.doClick(0);
                assertThat(input.getText()).isEqualTo("35");
            } finally { release(context); }
        });
    }

    /** Review Focus 5: a SLIDER under the classic kit is a spinner whose arrows step the value. */
    @Test void aSliderIsASpinnerUnderTheClassicKit() {
        Edt.run(() -> {
            var classic = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var standard = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(classic, new ViewSpec(number("opacity", Nodes.FieldPresentation.SLIDER)), e -> {});
                 var plain = new KitView(standard, new ViewSpec(number("opacity", Nodes.FieldPresentation.SLIDER)), e -> {})) {
                KitFixtures.render(view, 200, 40, 1); KitFixtures.render(plain, 200, 40, 1);
                assertThat(view.control("opacity")).isInstanceOf(NumericControls.Stepper.class);
                assertThat(plain.control("opacity")).isInstanceOf(NumericControls.Slider.class);
                find(view, "opacity.minus", JButton.class).doClick(0);
                assertThat(((TextFields.FieldControl) view.control("opacity")).input.getText()).isEqualTo("33");
            } finally { release(classic); release(standard); }
        });
    }

    @Test void theDefaultKitKeepsTheStepper() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, new ViewSpec(number("height", Nodes.FieldPresentation.STEPPER)), e -> {})) {
                KitFixtures.render(view, 200, 40, 1);
                JButton minus = find(view, "height.minus", JButton.class), plus = find(view, "height.plus", JButton.class);
                assertThat(minus.getX()).as("minus leads, plus trails").isLessThan(plus.getX());
                assertThat(minus.getText()).isEqualTo("−");
            } finally { release(context); }
        });
    }
}
