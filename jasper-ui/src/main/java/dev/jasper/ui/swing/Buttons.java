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
        /** Host-supplied artwork shown instead of the node's named icon (borrowed panel headers only); null for named icons. */
        javax.swing.Icon artwork;
        ButtonControl(KitView view, Nodes.Button node) {
            this(view, node, view::dispatch);
        }
        ButtonControl(KitView view, Nodes.Button node, java.util.function.Consumer<UiEvent> events) { this(view, node, events, null); }
        ButtonControl(KitView view, Nodes.Button node, java.util.function.Consumer<UiEvent> events, javax.swing.Icon artwork) {
            super(view, node, new KitButton(view.context())); button = (KitButton)component; this.artwork = artwork;
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
            var value = (Nodes.Button)node; state(); button.kind = value.kind(); button.iconSeverity = value.iconSeverity().orElse(null);
            button.toggle = value.selected().isPresent(); button.getModel().setSelected(value.selected().orElse(false));
            button.setText(value.label()); button.setFont(ThemePainting.font(context(), Typography.Role.LABEL));
            button.setForeground(ThemePainting.color(context(), button.textRole(false)));
            boolean embedded = value.presentation() == Nodes.ButtonPresentation.EMBEDDED;
            button.embedded = embedded;
            if (artwork != null) { button.setIcon(artwork); button.putClientProperty("Jasper.button.icon", null); }
            else {
                button.setIcon(value.icon().isEmpty() ? null : context().icons().icon(value.icon(), context().theme().semantic(), button.iconRole(false),
                    UIScale.scale(context().icons().size(embedded && value.label().isEmpty() ? Nodes.IconSize.RAIL : Nodes.IconSize.CONTROL))));
                button.putClientProperty("Jasper.button.icon", value.icon().isEmpty() ? null : value.icon());
            }
            button.setDisabledIcon(button.getIcon());
            button.setToolTipText(value.tooltip().isEmpty() ? null : value.tooltip());
            button.getAccessibleContext().setAccessibleName(value.label().isEmpty() ? value.tooltip() : value.label());
            button.getAccessibleContext().setAccessibleDescription(value.state().error());
            int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
            int horizontal = ThemePainting.metric(context(), value.kind() == Nodes.ButtonKind.PRIMARY ? DesignMetrics.Role.PRIMARY_BUTTON_HORIZONTAL_PADDING : DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);
            button.setBorder(embedded ? new EmptyBorder(0, 0, 0, 0)
                : new EmptyBorder(ring, value.label().isEmpty() ? ring : horizontal, ring, value.label().isEmpty() ? ring : horizontal));
            button.setIconTextGap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
            button.setCursor(Cursor.getPredefinedCursor(button.isEnabled() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            button.revalidate(); button.repaint();
        }
    }
    static class KitButton extends JButton {
        private final KitContext context;
        Nodes.ButtonKind kind;
        boolean hero, panel;
        /** Content-sized for a host arrangement (ButtonPresentation.EMBEDDED): no minimum height and no resting or hover fill. */
        boolean embedded;
        float disabledOpacity=.42f;
        /** A severity tinting the glyph at rest and on hover (a favourite's warning star); null takes the kind's text colour. */
        Nodes.Severity iconSeverity;
        /** A toggle button (Button.selected present): assistive technology reads it as one, checked while selected. */
        boolean toggle;
        ThemePainting.Focus focus;
        KitButton(KitContext context) { this.context = context; }
        // A push-button model never flips its selection on a click: the owner confirms a toggle's state in its next update.
        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJButton() {
                @Override public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return toggle ? javax.accessibility.AccessibleRole.TOGGLE_BUTTON : super.getAccessibleRole();
                }
                @Override public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
                    var states = super.getAccessibleStateSet();
                    if (toggle && isSelected()) { states.add(javax.accessibility.AccessibleState.CHECKED); states.add(javax.accessibility.AccessibleState.PRESSED); }
                    return states;
                }
            };
            return accessibleContext;
        }
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        SemanticColor textRole(boolean hover) {
            if (toggle && isSelected()) return SemanticColor.TEXT_PRIMARY;
            return switch (kind) {
                case PRIMARY -> SemanticColor.TEXT_ONACCENT; case SECONDARY -> SemanticColor.TEXT_PRIMARY;
                case GHOST -> hover ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_SECONDARY;
                case DESTRUCTIVE_GHOST -> hover ? SemanticColor.STATUS_DANGER : SemanticColor.TEXT_SECONDARY;
                case LINK -> hover ? SemanticColor.TEXT_LINK_HOVER : SemanticColor.TEXT_LINK;
            };
        }
        /** The glyph's colour: the severity's (as on a kit Icon) when present, otherwise the text colour. */
        SemanticColor iconRole(boolean hover) {
            return iconSeverity == null ? textRole(hover) : Labels.tint(iconSeverity);
        }
        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            if (embedded) return size;
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
                // A selected toggle shows the segmented control's selected fill, embedded or not.
                Color fill = toggle && isSelected() ? ThemePainting.color(context, SemanticColor.SEGMENT_SELECTED_BG) : embedded ? null : fill(hover);
                if (isEnabled() && getModel().isPressed() && fill != null) {
                    Color darkened = ColorMath.mix(ThemePainting.color(context, SemanticColor.SURFACE_APP), fill, .08);
                    fill = new Color(darkened.getRed(), darkened.getGreen(), darkened.getBlue(), fill.getAlpha());
                }
                Color border = toggle && isSelected() ? ThemePainting.color(context, SemanticColor.BORDER_CONTROL) : embedded ? null : border();
                ThemePainting.rounded(g, this, context, fill, border, !embedded && focus != null && focus.visible(), false);
                g.setFont(getFont()); g.setColor(ThemePainting.color(context, textRole(hover)));
                Insets insets = getInsets(); Rectangle icon = new Rectangle(), text = new Rectangle();
                String clipped = SwingUtilities.layoutCompoundLabel(this, g.getFontMetrics(), getText(), getIcon(), CENTER, getHorizontalAlignment(), CENTER, RIGHT,
                    new Rectangle(insets.left, insets.top, getWidth() - insets.left - insets.right, getHeight() - insets.top - insets.bottom), icon, text, getIconTextGap());
                if (getIcon() != null) {
                    // Re-tint the same named icon for hover without altering component identity.
                    String name = null;
                    if (getClientProperty("Jasper.button.icon") instanceof String value) name = value;
                    javax.swing.Icon artwork = name == null ? getIcon() : context.icons().icon(name, context.theme().semantic(), iconRole(hover), getIcon().getIconWidth());
                    artwork.paintIcon(this, g, icon.x, icon.y);
                }
                if (clipped != null) g.drawString(clipped, text.x, text.y + g.getFontMetrics().getAscent());
                if (embedded && isEnabled() && focus != null && focus.visible()) {
                    // An embedded button has no outer ring room: its keyboard focus cue is a hairline inside its bounds.
                    int radius = ThemePainting.metric(context, DesignMetrics.Role.CONTROL_RADIUS);
                    g.setComposite(AlphaComposite.SrcOver);
                    g.setColor(ThemePainting.color(context, SemanticColor.BORDER_FOCUS));
                    g.setStroke(new BasicStroke(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE)));
                    g.draw(new java.awt.geom.RoundRectangle2D.Double(.5, .5, getWidth() - 1, getHeight() - 1, radius * 2.0, radius * 2.0));
                }
            } finally { g.dispose(); }
        }
    }
}
