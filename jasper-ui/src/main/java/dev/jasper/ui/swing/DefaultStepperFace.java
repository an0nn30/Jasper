package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.util.List;
import javax.swing.*;

/** The default stepper: "−" leading and "+" trailing a centred value; a SLIDER is a slider. */
final class DefaultStepperFace implements StepperFace {
    @Override public void arrange(JPanel surface, JButton decrease, JButton increase) {
        surface.add(decrease, BorderLayout.WEST); surface.add(increase, BorderLayout.EAST);
    }
    @Override public int alignment() { return SwingConstants.CENTER; }
    @Override public Dimension stepSize(KitContext context, int direction) {
        return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.STEPPER_BUTTON_WIDTH),
            ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY));
    }
    @Override public void paintStep(JButton button, Graphics2D g, KitContext context, int direction, boolean focus, Runnable standard) { standard.run(); }
    @Override public void styleValue(KitContext context, JComponent value, JLabel unit) {
        // A compact value reads at label size, its unit set after one space.
        value.setFont(ThemePainting.font(context, Typography.Role.LABEL));
        unit.setFont(value.getFont()); unit.setForeground(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
    }
    @Override public int valueWidth(KitContext context) { return ThemePainting.metric(context, DesignMetrics.Role.STEPPER_VALUE_WIDTH); }
    @Override public Insets surfaceInsets(KitContext context) { return new Insets(0, 0, 0, 0); }
    @Override public boolean slider() { return true; }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
