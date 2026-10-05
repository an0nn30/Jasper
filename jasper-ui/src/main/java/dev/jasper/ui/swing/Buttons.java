package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Five button treatments sharing semantics, content fitting and owned action/focus listeners. */
final class Buttons {
    private Buttons() {}
    static final class ButtonControl extends ControlRegistry.Control {
        final KitButton button;
        ButtonControl(KitView view, Nodes.Button node) {
            this(view, node, view::dispatch);
        }
        ButtonControl(KitView view, Nodes.Button node, java.util.function.Consumer<UiEvent> events) {
            super(view, node, new KitButton(view.context())); button = (KitButton)component;
            button.putClientProperty("html.disable", true);
            button.setOpaque(false); button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false);
            button.setRolloverEnabled(true); button.setHorizontalAlignment(SwingConstants.CENTER);
            button.focus = new ThemePainting.Focus(button, button, view); own(button.focus);
            ActionListener listener = event -> {
                if (interactive()) try { events.accept(new UiEvent.Action(((Nodes.Button)this.node).action())); }
                catch (Throwable failure) { view.report(failure); }
            };
            button.addActionListener(listener); own(() -> button.removeActionListener(listener)); refresh();
        }
        @Override void hero(boolean value) { button.hero=value; }
        @Override void refresh() {
            var value = (Nodes.Button)node; state(); button.kind = value.kind();
            button.setText(value.label()); button.setFont(ThemePainting.font(context(), Typography.Role.LABEL));
            button.setForeground(ThemePainting.color(context(), button.textRole(false)));
            button.setIcon(value.icon().isEmpty() ? null : context().icons().icon(value.icon(), context().theme().semantic(), button.textRole(false),
                UIScale.scale(context().icons().size(Nodes.IconSize.CONTROL))));
            button.setDisabledIcon(button.getIcon()); button.putClientProperty("Jasper.button.icon", value.icon().isEmpty() ? null : value.icon());
            button.setToolTipText(value.tooltip().isEmpty() ? null : value.tooltip());
            button.getAccessibleContext().setAccessibleName(value.label().isEmpty() ? value.tooltip() : value.label());
            button.getAccessibleContext().setAccessibleDescription(value.state().error());
            int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
            int horizontal = ThemePainting.metric(context(), value.kind() == Nodes.ButtonKind.PRIMARY ? DesignMetrics.Role.PRIMARY_BUTTON_HORIZONTAL_PADDING : DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);
            button.setBorder(new EmptyBorder(ring, value.label().isEmpty() ? ring : horizontal, ring, value.label().isEmpty() ? ring : horizontal));
            button.setIconTextGap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
            button.setCursor(Cursor.getPredefinedCursor(button.isEnabled() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            button.revalidate(); button.repaint();
        }
    }
    static class KitButton extends JButton {
        private final KitContext context;
        Nodes.ButtonKind kind;
        boolean hero, panel;
        float disabledOpacity=.42f;
        ThemePainting.Focus focus;
        KitButton(KitContext context) { this.context = context; }
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        SemanticColor textRole(boolean hover) {
            return switch (kind) {
                case PRIMARY -> SemanticColor.TEXT_ONACCENT; case SECONDARY -> SemanticColor.TEXT_PRIMARY;
                case GHOST -> hover ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_SECONDARY;
                case DESTRUCTIVE_GHOST -> hover ? SemanticColor.STATUS_DANGER : SemanticColor.TEXT_SECONDARY;
                case LINK -> hover ? SemanticColor.TEXT_LINK_HOVER : SemanticColor.TEXT_LINK;
            };
        }
        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            int height = ThemePainting.height(context, hero ? DesignMetrics.Role.HERO_BUTTON_HEIGHT : panel ? DesignMetrics.Role.PANEL_ACTION : DesignMetrics.Role.BUTTON_HEIGHT, Typography.Role.LABEL);
            return new Dimension(getText() == null || getText().isEmpty() ? height : size.width, Math.max(height, size.height));
        }
        /** Shared painter permits a real specialized owner to select its semantic surface. */
        Color fill(boolean hover) {
            return switch (kind) {
                case PRIMARY -> ThemePainting.color(context, hover ? SemanticColor.ACCENT_HOVER : SemanticColor.ACCENT);
                case SECONDARY -> ThemePainting.color(context, hover ? SemanticColor.BUTTON_SECONDARY_BG_HOVER : SemanticColor.BUTTON_SECONDARY_BG);
                case GHOST -> hover ? ColorMath.opacity(ThemePainting.color(context, SemanticColor.HOVER_OVERLAY), .08) : null;
                case DESTRUCTIVE_GHOST -> hover ? ColorMath.opacity(ThemePainting.color(context, SemanticColor.STATUS_DANGER), .15) : null;
                case LINK -> hover ? ColorMath.opacity(ThemePainting.color(context, SemanticColor.ACCENT), .08) : null;
            };
        }
        Color border() { return kind == Nodes.ButtonKind.SECONDARY ? ThemePainting.color(context, SemanticColor.BORDER_CONTROL) : null; }
        @Override protected void paintComponent(Graphics graphics) {
            if (kind == null) return;
            var g = (Graphics2D)graphics.create();
            try {
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(disabledOpacity));
                boolean hover = isEnabled() && getModel().isRollover();
                Color fill = fill(hover);
                if (isEnabled() && getModel().isPressed() && fill != null) {
                    Color darkened = ColorMath.mix(ThemePainting.color(context, SemanticColor.SURFACE_APP), fill, .08);
                    fill = new Color(darkened.getRed(), darkened.getGreen(), darkened.getBlue(), fill.getAlpha());
                }
                Color border = border();
                ThemePainting.rounded(g, this, context, fill, border, focus != null && focus.visible(), false);
                g.setFont(getFont()); g.setColor(ThemePainting.color(context, textRole(hover)));
                Insets insets = getInsets(); Rectangle icon = new Rectangle(), text = new Rectangle();
                String clipped = SwingUtilities.layoutCompoundLabel(this, g.getFontMetrics(), getText(), getIcon(), CENTER, getHorizontalAlignment(), CENTER, RIGHT,
                    new Rectangle(insets.left, insets.top, getWidth() - insets.left - insets.right, getHeight() - insets.top - insets.bottom), icon, text, getIconTextGap());
                if (getIcon() != null) {
                    // Re-tint the same named icon for hover without altering component identity.
                    String name = null;
                    if (getClientProperty("Jasper.button.icon") instanceof String value) name = value;
                    javax.swing.Icon artwork = name == null ? getIcon() : context.icons().icon(name, context.theme().semantic(), textRole(hover), getIcon().getIconWidth());
                    artwork.paintIcon(this, g, icon.x, icon.y);
                }
                if (clipped != null) g.drawString(clipped, text.x, text.y + g.getFontMetrics().getAscent());
            } finally { g.dispose(); }
        }
    }
}
