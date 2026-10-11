package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicProgressBarUI;
import static javax.swing.SwingConstants.*;

/** Noninteractive semantic chips, status dots and progress/strength values. */
final class Indicators {
    private Indicators() {}
    static final class ChipControl extends ControlRegistry.Control {
        ChipControl(KitView view, Nodes.Chip node) { super(view, node, new ChipLabel(view.context())); refresh(); }
        @Override void refresh() {
            var chip = (Nodes.Chip)node; state(); var label = (JLabel)component;
            label.setText(chip.label()); label.setFont(ThemePainting.font(context(), Typography.Role.CHIP)); label.setForeground(Choices.hue(context(), chip.hue()));
            int padding = ThemePainting.metric(context(), DesignMetrics.Role.CHIP_PADDING); label.setBorder(new EmptyBorder(0, padding, 0, padding));
            label.getAccessibleContext().setAccessibleName(chip.label()); label.revalidate(); label.repaint();
        }
    }
    static final class ChipLabel extends Labels.LiteralLabel {
        final KitContext context;
        ChipLabel(KitContext context) { this.context = context; putClientProperty("html.disable", true); }
        @Override public Dimension getPreferredSize() { var size = super.getPreferredSize(); return new Dimension(size.width, Math.max(size.height, ThemePainting.metric(context, DesignMetrics.Role.CHIP_HEIGHT))); }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                int radius = ThemePainting.metric(context, DesignMetrics.Role.CHIP_RADIUS);
                g.setColor(ColorMath.opacity(getForeground(), .16)); g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
    }
    static final class DotControl extends ControlRegistry.Control {
        DotControl(KitView view, Nodes.StatusDot node) { super(view, node, new Dot(view.context())); refresh(); }
        @Override void refresh() {
            var dot = (Nodes.StatusDot)node; state(); component.setForeground(ThemePainting.color(context(), switch (dot.kind()) {
                case SUCCESS -> SemanticColor.STATUS_SUCCESS; case WARNING -> SemanticColor.STATUS_WARNING;
                case DANGER -> SemanticColor.STATUS_DANGER; case IDLE -> SemanticColor.STATUS_IDLE;
            })); component.getAccessibleContext().setAccessibleName(dot.kind().name()); component.repaint();
        }
    }
    static final class Dot extends JPanel {
        final KitContext context;
        boolean panelRow;
        Dot(KitContext context) { this.context = context; setOpaque(false); }
        /** Tooltips are literal text: row tooltips carry user data, and Swing HTML could load remote resources. */
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        @Override public Dimension getPreferredSize() { int size = ThemePainting.metric(context, panelRow ? DesignMetrics.Role.PANEL_ROW_DOT : DesignMetrics.Role.STATUS_DOT); return new Dimension(size, size + (panelRow ? 2*ThemePainting.metric(context,DesignMetrics.Role.PANEL_ROW_OPTICAL_OFFSET) : 0)); }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try {
                ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                int size = ThemePainting.metric(context, panelRow ? DesignMetrics.Role.PANEL_ROW_DOT : DesignMetrics.Role.STATUS_DOT); g.setColor(getForeground()); g.fillOval((getWidth()-size)/2, (getHeight()-size)/2 + (panelRow ? ThemePainting.metric(context,DesignMetrics.Role.PANEL_ROW_OPTICAL_OFFSET) : 0), size, size);
            } finally { g.dispose(); }
        }
    }
    static final class ProgressControl extends ControlRegistry.Control {
        final ProgressBar progress;
        final Timer animation;
        AutoCloseable effect;
        ProgressControl(KitView view, Nodes.Progress node) {
            super(view, node, new ProgressBar(view.context())); progress = (ProgressBar)component;
            animation = new Timer(32, event -> {
                if (!view.motion().enabled()) { settle(); return; }
                progress.phase = (progress.phase + .035) % 1; progress.repaint();
            }); progress.animation = animation;
            // Animate only while actually showing: not before the view is mounted, under a hidden ancestor or once removed.
            java.awt.event.HierarchyListener showing = event -> {
                if ((event.getChangeFlags() & (java.awt.event.HierarchyEvent.SHOWING_CHANGED | java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED)) != 0) animate();
            };
            progress.addHierarchyListener(showing);
            own(() -> progress.removeHierarchyListener(showing)); own(this::settle); refresh();
        }
        private void animate() {
            if (progress.isIndeterminate() && view.motion().enabled() && component.isEnabled() && component.isShowing()) {
                if (!animation.isRunning()) { effect = view.motion().register(this::settle); animation.start(); }
            } else settle();
        }
        private void settle() {
            animation.stop(); progress.phase = 0;
            if (effect != null) { try { effect.close(); } catch (Exception failure) { view.report(failure); } effect = null; }
            progress.repaint();
        }
        @Override void refresh() {
            var value = (Nodes.Progress)node; state(); progress.setIndeterminate(value.fraction() == -1); progress.setValue((int)Math.round(Math.max(0, value.fraction())*1000));
            progress.setString(value.label()); progress.setFont(ThemePainting.font(context(), Typography.Role.CAPTION));
            progress.setForeground(ThemePainting.color(context(), SemanticColor.ACCENT)); progress.setBackground(ThemePainting.color(context(), SemanticColor.SURFACE_CONTROL));
            progress.getAccessibleContext().setAccessibleName(value.label()); progress.getAccessibleContext().setAccessibleDescription(value.fraction() == -1 ? "In progress" : (int)Math.round(value.fraction()*100) + "%");
            animate();
            progress.revalidate(); progress.repaint();
        }
    }
    static final class ProgressBar extends JProgressBar {
        final KitContext context; double phase;
        /** The owning control's indeterminate animation (package-private for lifecycle tests). */
        Timer animation;
        ProgressBar(KitContext context) {
            super(0, 1000); this.context = context; setOpaque(false); setBorderPainted(false);
            setUI(new BasicProgressBarUI() { @Override protected void startAnimationTimer() {} @Override protected void stopAnimationTimer() {} });
        }
        /** Tooltips are literal text: row tooltips carry user data, and Swing HTML could load remote resources. */
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        @Override public Dimension getPreferredSize() { return new Dimension(getFontMetrics(getFont()).stringWidth(getString()) + 2*ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING), ThemePainting.height(context, DesignMetrics.Role.INLINE_ACTION, Typography.Role.CAPTION)); }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try {
                ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                int h = ThemePainting.metric(context, DesignMetrics.Role.PROGRESS_HEIGHT); g.setColor(getBackground()); g.fillRoundRect(0, 0, getWidth(), h, h, h);
                g.setColor(getForeground()); int width = isIndeterminate() ? getWidth()/3 : (int)Math.round(getWidth()*getPercentComplete());
                int x = isIndeterminate() ? (int)Math.round((getWidth()-width)*phase) : 0; g.fillRoundRect(x, 0, width, h, h, h);
                g.setFont(getFont()); g.setColor(ThemePainting.color(context, SemanticColor.TEXT_SECONDARY));
                String clipped = SwingUtilities.layoutCompoundLabel(this, g.getFontMetrics(), getString(), null, CENTER, RIGHT, CENTER, RIGHT,
                    new Rectangle(0, h, getWidth(), Math.max(0, getHeight()-h)), new Rectangle(), new Rectangle(), 0);
                if (clipped != null) g.drawString(clipped, getWidth()-g.getFontMetrics().stringWidth(clipped), getHeight()-g.getFontMetrics().getDescent());
            } finally { g.dispose(); }
        }
    }
    static final class StrengthControl extends ControlRegistry.Control {
        StrengthControl(KitView view, Nodes.Strength node) { super(view, node, new StrengthMeter(view.context())); refresh(); }
        @Override void refresh() {
            var strength = (Nodes.Strength)node; state(); var meter = (StrengthMeter)component; meter.filled = strength.filled(); meter.label = strength.label();
            meter.setFont(ThemePainting.font(context(), Typography.Role.CAPTION)); meter.setForeground(ThemePainting.color(context(), switch (strength.filled()) {
                case 1 -> SemanticColor.STATUS_DANGER; case 2 -> SemanticColor.STATUS_WARNING; case 3 -> SemanticColor.ACCENT; case 4 -> SemanticColor.STATUS_SUCCESS; default -> SemanticColor.TEXT_TERTIARY;
            })); meter.getAccessibleContext().setAccessibleName(strength.label()); meter.getAccessibleContext().setAccessibleDescription(strength.filled() + " of 4"); meter.revalidate(); meter.repaint();
        }
    }
    static final class StrengthMeter extends JPanel {
        final KitContext context; int filled; String label = "";
        StrengthMeter(KitContext context) { this.context = context; setOpaque(false); }
        /** Tooltips are literal text: row tooltips carry user data, and Swing HTML could load remote resources. */
        @Override public JToolTip createToolTip() { var tip = super.createToolTip(); tip.putClientProperty("html.disable", true); return tip; }
        @Override public Dimension getPreferredSize() { if (label.isEmpty()) return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.STRENGTH_WIDTH), ThemePainting.metric(context, DesignMetrics.Role.STRENGTH_HEIGHT)); return new Dimension(Math.max(getFontMetrics(getFont()).stringWidth(label), 4*ThemePainting.metric(context, DesignMetrics.Role.STRENGTH_HEIGHT)) + 3*ThemePainting.metric(context, DesignMetrics.Role.STRENGTH_GAP), ThemePainting.height(context, DesignMetrics.Role.INLINE_ACTION, Typography.Role.CAPTION)); }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try {
                ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                int gap = ThemePainting.metric(context, DesignMetrics.Role.STRENGTH_GAP), height = ThemePainting.metric(context, DesignMetrics.Role.STRENGTH_HEIGHT);
                int radius = ThemePainting.metric(context, DesignMetrics.Role.STRENGTH_RADIUS); double width = Math.max(0, (getWidth()-3.0*gap)/4);
                for (int i=0; i<4; i++) { g.setColor(i<filled ? getForeground() : ThemePainting.color(context, SemanticColor.SURFACE_CONTROL)); int x=(int)Math.round(i*(width+gap)); int end=(int)Math.round(i*(width+gap)+width); g.fillRoundRect(x, 0, end-x, height, radius * 2, radius * 2); }
                g.setFont(getFont()); g.setColor(getForeground());
                Rectangle text = new Rectangle(); String clipped = SwingUtilities.layoutCompoundLabel(this, g.getFontMetrics(), label, null, CENTER, RIGHT, CENTER, RIGHT,
                    new Rectangle(0, height, getWidth(), Math.max(0, getHeight()-height)), new Rectangle(), text, 0);
                if (clipped != null) g.drawString(clipped, text.x, text.y+g.getFontMetrics().getAscent());
            } finally { g.dispose(); }
        }
    }
}
