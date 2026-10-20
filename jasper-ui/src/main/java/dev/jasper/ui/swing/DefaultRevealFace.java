package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JPanel;

/** The reveal as SecretFields drew it before plan 10b: a glyph in the field, and glyph buttons beside a stored value. */
final class DefaultRevealFace implements RevealFace {
    @Override public List<PaintPair> pairs() { return List.of(); }

    @Override public void mountInput(SecretFields.SecretControl control) {
        var kind = ((Nodes.Secret) control.node).kind();
        var reveal = control.reveal;
        if (kind == Nodes.SecretKind.DISCREET_INPUT) {
            var slot=new JPanel(new BorderLayout()) {
                @Override public Dimension getPreferredSize() { return reveal.getPreferredSize(); }
            };
            slot.setOpaque(false);slot.add(reveal);control.surface.add(slot,BorderLayout.EAST);
        } else if (!control.multiline && kind != Nodes.SecretKind.CONFIRMATION) control.surface.add(reveal, BorderLayout.EAST);
    }

    @Override public JPanel detailActions(SecretFields.SecretControl control) {
        var actions = new JPanel(new GridLayout(1, 0)) {
            // Reveal and copy keep the detail references' measured spacing at every theme/font refresh.
            @Override public void doLayout() { ((GridLayout) getLayout()).setHgap(ThemePainting.metric(control.context(), DesignMetrics.Role.DETAIL_ACTION_GAP)); super.doLayout(); }
            @Override public Dimension getPreferredSize() { ((GridLayout) getLayout()).setHgap(ThemePainting.metric(control.context(), DesignMetrics.Role.DETAIL_ACTION_GAP)); return super.getPreferredSize(); }
        };
        actions.setOpaque(false);
        actions.add(control.reveal);
        actions.add(control.copy);
        return actions;
    }

    @Override public void prompt(SecretFields.SecretControl control, boolean prompt) { }

    @Override public void visibility(SecretFields.SecretControl control) {
        var kind=((Nodes.Secret)control.node).kind();
        control.reveal.setVisible(!control.multiline && kind!=Nodes.SecretKind.CONFIRMATION
            && (kind!=Nodes.SecretKind.DISCREET_INPUT || control.focusWithin || control.buffer.getLength()>0));
    }

    @Override public void style(SecretFields.SecretControl control) {
        var reveal = control.reveal; var context = control.context();
        reveal.setToolTipText(control.revealed ? "Hide password" : "Show password");
        reveal.getAccessibleContext().setAccessibleName(reveal.getToolTipText());
        reveal.getAccessibleContext()
                .setAccessibleDescription(control.revealed ? "Password visible" : "Password hidden");
        reveal.setSelected(control.revealed);
        reveal.putClientProperty("Jasper.button.icon", "reveal");
        reveal.setIcon(
                context
                        .icons()
                        .icon(
                                "reveal",
                                context.theme().semantic(),
                                SemanticColor.TEXT_SECONDARY,
                                com.formdev.flatlaf.util.UIScale.scale(
                                        context.icons().size(context.metrics(), Nodes.IconSize.CONTROL))));
        reveal.setDisabledIcon(reveal.getIcon());
        reveal.repaint();
    }
}
