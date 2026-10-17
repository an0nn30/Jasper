package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JComponent;

/** The default buttons: raised faces with the primary's inset line and drop, rounded dropdowns with their chevron. */
final class DefaultButtonFace implements ButtonFace {
    @Override public void paintFace(Buttons.KitButton button, Graphics2D g, boolean hover, boolean focused) { button.paintRaised(g, hover, focused); }
    @Override public Color text(Buttons.KitButton button, boolean hover) { return ThemePainting.color(button.context(), button.textRole(hover)); }
    @Override public boolean fades(Buttons.KitButton button) { return true; }
    @Override public Typography.Role label(Nodes.ButtonKind kind, Typography.Role role) { return role; }
    @Override public void paintCombo(Choices.ChoiceCombo combo, Graphics2D g, boolean hover, boolean focused) {
        ThemePainting.rounded(g, combo, combo.context, ThemePainting.color(combo.context, hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_CONTROL),
            ThemePainting.color(combo.context, SemanticColor.BORDER_CONTROL), focused, false);
    }
    @Override public void paintArrow(Choices.ChoiceCombo combo, JComponent arrow, Graphics2D g, int beyond, boolean settings) {
        Choices.paintChevron(arrow, g, combo.context, beyond, settings, combo.arrowRole());
    }
    @Override public void paintPickerArrow(Choices.PickerButton picker, Graphics2D g) { picker.paintChevron(g); }
    @Override public void paintPicker(JComponent owner, Graphics graphics, KitContext context, Nodes.PickerFace face, boolean empty, boolean focus,
                                      boolean hover, boolean expanded) {
        PickerMenus.paintRichFace(owner, graphics, context, face, empty, focus, hover, expanded);
    }
    @Override public boolean tiles() { return true; }
    @Override public int joinedOverlap() { return 0; }
    @Override public Color menuDivider(KitContext context) { return ThemePainting.color(context, SemanticColor.BORDER_MENU); }
    @Override public List<PaintPair> pairs() { return List.of(); }
}
