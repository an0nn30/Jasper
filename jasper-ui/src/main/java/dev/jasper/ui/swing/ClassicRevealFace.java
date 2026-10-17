package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The classic reveal of the Enterprise mocks (plan 10b Rulings 1): a "Show" checkbox after an input (the kit's checkbox
 * face paints the box), "Show password" under a prompt's field, and a stored value's "Show" (or "Hide") and "Copy" as
 * secondary buttons with text.
 */
final class ClassicRevealFace implements RevealFace {
    private final SemanticColor surface;

    ClassicRevealFace(SemanticColor dialogSurface) { surface = dialogSurface; }

    private PaintPair label() { return new PaintPair(SemanticColor.TEXT_PRIMARY, surface, SurfaceContrast.Guard.TEXT); }

    @Override public List<PaintPair> pairs() { return List.of(label()); }

    @Override public void mountInput(SecretFields.SecretControl control) {
        var kind = ((Nodes.Secret) control.node).kind();
        if (control.multiline || control.detail || kind == Nodes.SecretKind.CONFIRMATION) return;
        var box = new Switches.SwitchButton(control.context());
        KitOwnership.mark(box, control.node.id() + ".show");
        // The face paints the whole box, as a toggle control's: no look-and-feel fill, border or focus.
        box.setContentAreaFilled(false); box.setBorderPainted(false); box.setFocusPainted(false); box.setOpaque(false);
        box.focus = new ThemePainting.Focus(box, box, control.view);
        control.own(box.focus);
        ActionListener toggle = event -> { if (control.interactive()) control.view.revealSecrets(control, !control.revealed); };
        box.addActionListener(toggle);
        control.own(() -> box.removeActionListener(toggle));
        var text = Labels.literal();
        KitOwnership.mark(text, control.node.id() + ".show.label");
        text.setLabelFor(box);
        var click = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (box.isEnabled()) box.doClick(0); }
        };
        text.addMouseListener(click);
        control.own(() -> text.removeMouseListener(click));
        var row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(box, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        var field = new JPanel(new BorderLayout());
        field.setOpaque(false);
        field.add(control.surface, BorderLayout.CENTER);
        field.add(row, BorderLayout.EAST);
        control.box = box; control.boxLabel = text; control.boxRow = row; control.fieldRow = field;
    }

    @Override public JPanel detailActions(SecretFields.SecretControl control) {
        // Show leads and Copy follows one gap after it at its preferred width; without Copy no gap trails.
        var actions = new JPanel(new BorderLayout()) {
            private void gap() { ((BorderLayout) getLayout()).setHgap(control.copy.isVisible() ? ThemePainting.metric(control.context(), DesignMetrics.Role.DETAIL_ACTION_GAP) : 0); }
            @Override public void doLayout() { gap(); super.doLayout(); }
            @Override public Dimension getPreferredSize() { gap(); return super.getPreferredSize(); }
        };
        actions.setOpaque(false);
        actions.add(control.reveal, BorderLayout.WEST);
        actions.add(control.copy, BorderLayout.CENTER);
        return actions;
    }

    @Override public void prompt(SecretFields.SecretControl control, boolean prompt) {
        if (control.boxRow == null) return;
        control.fieldRow.remove(control.boxRow);
        control.fieldRow.add(control.boxRow, prompt ? BorderLayout.SOUTH : BorderLayout.EAST);
        style(control);
        control.fieldRow.revalidate();
    }

    @Override public void visibility(SecretFields.SecretControl control) {
        control.reveal.setVisible(control.detail);
        if (control.boxRow != null) control.boxRow.setVisible(true);
    }

    @Override public void style(SecretFields.SecretControl control) {
        var context = control.context(); var reveal = control.reveal; boolean revealed = control.revealed;
        reveal.setToolTipText(revealed ? "Hide password" : "Show password");
        reveal.getAccessibleContext().setAccessibleName(reveal.getToolTipText());
        reveal.getAccessibleContext().setAccessibleDescription(revealed ? "Password visible" : "Password hidden");
        reveal.setSelected(revealed);
        if (control.detail) {
            button(control, reveal, revealed ? "Hide" : "Show");
            button(control, control.copy, "Copy");
        }
        if (control.box != null) {
            boolean prompt = control.surface.prompt;
            control.box.setSelected(revealed);
            control.box.position = revealed ? 1 : 0;
            control.box.setEnabled(control.component.isEnabled());
            int ring = ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING);
            control.box.setBorder(new EmptyBorder(ring, ring, ring, ring));
            control.box.getAccessibleContext().setAccessibleName("Show password");
            control.boxLabel.setText(prompt ? "Show password" : "Show");
            control.boxLabel.setFont(ThemePainting.font(context, Typography.Role.BODY));
            control.boxLabel.setForeground(ThemePainting.painted(context, label()));
            control.boxLabel.setEnabled(control.component.isEnabled());
            ((BorderLayout) control.boxRow.getLayout()).setHgap(ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_LABEL_GAP));
            ((BorderLayout) control.fieldRow.getLayout()).setHgap(ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP));
            ((BorderLayout) control.fieldRow.getLayout()).setVgap(ThemePainting.metric(context, DesignMetrics.Role.ICON_GAP));
            control.box.repaint();
        }
        reveal.repaint();
    }

    /** A stored value's action as a secondary button with text: the classic buttons face paints it. */
    private static void button(SecretFields.SecretControl control, Buttons.KitButton button, String text) {
        var context = control.context();
        button.kind = Nodes.ButtonKind.SECONDARY;
        button.putClientProperty("Jasper.button.icon", null);
        button.setIcon(null);
        button.setDisabledIcon(null);
        button.setText(text);
        button.setFont(ThemePainting.font(context, Typography.Role.LABEL));
        int side = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
        button.setBorder(new EmptyBorder(0, side, 0, side));
        button.repaint();
    }
}
