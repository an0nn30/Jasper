package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Switches report intent once; only descriptor updates change the confirmed displayed state. A pending switch shows its
 * knob midway on the warning colour and stays operable.
 */
final class Switches {
    private Switches() {}
    static final class ToggleControl extends ControlRegistry.Control {
        final SwitchButton button;
        final Timer animation;
        AutoCloseable effect;
        long start;
        double from, target;
        ToggleControl(KitView view, Nodes.Toggle node) {
            super(view, node, new SwitchButton(view.context())); button = (SwitchButton)component;
            button.position = position(node); target = button.position;
            button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false); button.setOpaque(false);
            button.focus = new ThemePainting.Focus(button, button, view); own(button.focus);
            animation = new Timer(16, event -> frame()); own(this::settle);
            ActionListener action = event -> emit(new UiEvent.Toggled(this.node.id(), !((Nodes.Toggle)this.node).checked()));
            button.addActionListener(action); own(() -> button.removeActionListener(action)); refresh();
        }
        /** Where the knob rests: off, on, or midway while the domain confirms a change. */
        static double position(Nodes.Toggle toggle) { return toggle.pending() ? .5 : toggle.checked() ? 1 : 0; }
        private void frame() {
            if (!view.motion().enabled()) { settle(); return; }
            double elapsed = (System.nanoTime() - start) / 135_000_000.0;
            if (elapsed >= 1) { settle(); return; }
            button.position = from + (target - from) * (1 - Math.pow(1 - elapsed, 3)); button.repaint();
        }
        private void settle() {
            animation.stop(); button.position = target;
            if (effect != null) { try { effect.close(); } catch (Exception failure) { view.report(failure); } effect = null; }
            button.repaint();
        }
        @Override void refresh() {
            var toggle = (Nodes.Toggle)node; state(); button.compact = toggle.compact(); button.error = !toggle.state().error().isEmpty(); button.pending = toggle.pending();
            button.setSelected(toggle.checked()); button.getAccessibleContext().setAccessibleName(toggle.label());
            button.getAccessibleContext().setAccessibleDescription(!toggle.state().error().isEmpty() ? toggle.state().error()
                : !toggle.tooltip().isBlank() ? toggle.tooltip() : toggle.pending() ? "Pending" : toggle.tooltip());
            button.setToolTipText(!toggle.state().error().isEmpty() ? toggle.state().error() : toggle.tooltip().isBlank() ? toggle.label() : toggle.tooltip());
            double next = position(toggle);
            if (next != target) {
                from = button.position; target = next;
                if (view.motion().enabled() && button.face.moves()) {
                    if (effect != null) try { effect.close(); } catch (Exception failure) { view.report(failure); }
                    effect = view.motion().register(this::settle); start = System.nanoTime(); animation.start();
                } else settle();
            } else if (!view.motion().enabled()) settle();
            int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING); button.setBorder(new EmptyBorder(ring, ring, ring, ring));
            button.revalidate(); button.repaint();
        }
    }
    static final class SwitchButton extends JButton {
        final KitContext context;
        ThemePainting.Focus focus;
        double position;
        boolean compact, error, pending;
        /** Whether the switch sits on a selected row's accent fill; set by the row before each population. */
        boolean onSelection;
        /** The selected row's fill and ink, while {@link #onSelection}. */
        InteractiveRows.Ink ink = InteractiveRows.Ink.SELECTION;
        /** The kit's toggle face, fixed for the button's life. */
        final ToggleFace face;
        SwitchButton(KitContext context) { this.context = context; face = context.faces().toggle; }
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        @Override public Dimension getPreferredSize() { return face.preferredSize(this); }
        /** The switch's size, its focus reservation included (the default face). */
        Dimension switchSize() {
            int ring = ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING);
            return new Dimension(2*ring + ThemePainting.metric(context, compact ? DesignMetrics.Role.COMPACT_SWITCH_WIDTH : DesignMetrics.Role.SWITCH_WIDTH),
                2*ring + ThemePainting.metric(context, compact ? DesignMetrics.Role.COMPACT_SWITCH_HEIGHT : DesignMetrics.Role.SWITCH_HEIGHT));
        }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try { face.paint(this, g); } finally { g.dispose(); }
        }
        /** Paints the switch: its track, knob and halo (the default face). */
        void paintSwitch(Graphics2D g) {
            {
                ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                int ring = ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING);
                int w = ThemePainting.metric(context, compact ? DesignMetrics.Role.COMPACT_SWITCH_WIDTH : DesignMetrics.Role.SWITCH_WIDTH);
                int h = ThemePainting.metric(context, compact ? DesignMetrics.Role.COMPACT_SWITCH_HEIGHT : DesignMetrics.Role.SWITCH_HEIGHT);
                int knob = ThemePainting.metric(context, compact ? DesignMetrics.Role.COMPACT_SWITCH_KNOB : DesignMetrics.Role.SWITCH_KNOB);
                int x = (getWidth()-w)/2, y = (getHeight()-h)/2;
                if (error || focus.visible()) {
                    g.setColor(ThemePainting.focusHaloColor(context, error));
                    g.fillRoundRect(x-ring, y-ring, w+2*ring, h+2*ring, h+2*ring, h+2*ring);
                }
                Color on = ThemePainting.color(context, SemanticColor.ACCENT), off = ThemePainting.color(context, SemanticColor.TOGGLE_TRACK_OFF),
                    knobOn = ThemePainting.color(context, SemanticColor.TOGGLE_KNOB), knobOff = knobOn;
                // On a selected row's fill the ON track takes the fill's ink around a knob of the fill's colour, the OFF track a tint of it.
                if (onSelection) {
                    on = ThemePainting.color(context, ink.text); off = ColorMath.opacity(on, ThemePainting.tileShare(context));
                    knobOn = ThemePainting.color(context, ink.fill);
                }
                if (pending) { on = off = ThemePainting.color(context, SemanticColor.STATUS_WARNING); knobOn = knobOff; }
                Color rgb = ColorMath.mix(on, off, position);
                g.setColor(new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), (int)Math.round(on.getAlpha()*position + off.getAlpha()*(1-position))));
                g.fillRoundRect(x, y, w, h, h, h);
                int inset = (h-knob)/2, knobX = x+inset+(int)Math.round((w-knob-2*inset)*position);
                g.setColor(ColorMath.opacity(Color.BLACK, context.metrics().value(DesignMetrics.Role.SWITCH_KNOB_SHADOW_PERCENT) / 100.0)); g.fillOval(knobX, y+inset+com.formdev.flatlaf.util.UIScale.scale(1), knob, knob);
                g.setColor(ColorMath.mix(knobOn, knobOff, position)); g.fillOval(knobX, y+inset, knob, knob);
            }
        }
    }
}
