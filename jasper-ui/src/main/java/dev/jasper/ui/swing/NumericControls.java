package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Retained numeric editing, bounded steppers and a genuine pointer/keyboard slider. */
final class NumericControls {
    private NumericControls() { }
    static final class Stepper extends TextFields.FieldControl {
        private JButton minus, plus;
        private JLabel unit;
        Stepper(KitView view, Nodes.Field field) {
            super(view, field);
            unit = Labels.literal();
            var center = new JPanel(new BorderLayout()); center.setOpaque(false);
            surface.remove(input); center.add(input, BorderLayout.CENTER); center.add(unit, BorderLayout.EAST);
            surface.add(center, BorderLayout.CENTER);
            ((JTextField)input).setColumns(3); ((JTextField)input).setHorizontalAlignment(SwingConstants.CENTER);
            minus = button("minus", "−", -1); plus = button("plus", "+", 1);
            surface.add(minus, BorderLayout.WEST); surface.add(plus, BorderLayout.EAST);
            refresh();
        }
        private JButton button(String suffix, String text, int direction) {
            var cue = new ThemePainting.Focus[1];
            var button = new JButton(text) {
                @Override public Dimension getPreferredSize() { return new Dimension(ThemePainting.metric(context(), DesignMetrics.Role.STEPPER_BUTTON_WIDTH), ThemePainting.height(context(), DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY)); }
                @Override protected void paintComponent(Graphics graphics) {
                    var g = (Graphics2D)graphics.create();
                    try {
                        ThemePainting.antialias(g);
                        if(cue[0]!=null&&cue[0].visible()) ThemePainting.rounded(g,this,context(),null,ThemePainting.color(context(),SemanticColor.BORDER_FOCUS),false,false);
                        g.setColor(ThemePainting.color(context(), SemanticColor.BORDER_INPUT));
                        int x = direction < 0 ? getWidth() - 1 : 0; g.drawLine(x, 0, x, getHeight());
                        if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                        g.setColor(getForeground()); g.setFont(getFont()); var fm = g.getFontMetrics();
                        g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    } finally { g.dispose(); }
                }
            };
            button.setOpaque(false); button.setBorderPainted(false); button.setContentAreaFilled(false);
            KitOwnership.mark(button, node.id() + "." + suffix);
            cue[0] = new ThemePainting.Focus(button, button, view); own(cue[0]);
            java.awt.event.ActionListener action = event -> { if (interactive()) input.setText(TextFields.NumberFilter.step(input.getText(), ((Nodes.Field)node).numeric().orElseThrow(), direction)); };
            button.addActionListener(action); own(() -> button.removeActionListener(action));
            return button;
        }
        @Override void refresh() {
            super.refresh();
            if (unit == null) return;
            var field = (Nodes.Field)node;
            surface.hint = ""; surface.setBorder(new EmptyBorder(0, 0, 0, 0));
            unit.setText(field.hint().isBlank() ? "" : " " + field.hint() + " ");
            unit.setFont(input.getFont()); unit.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_TERTIARY));
            for (var button : new JButton[]{minus, plus}) {
                button.setFont(input.getFont()); button.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY));
                button.setEnabled(interactive());
                button.getAccessibleContext().setAccessibleName((button == minus ? "Decrease " : "Increase ") + field.label());
            }
            try {
                double value = Double.parseDouble(input.getText()); var numeric = field.numeric().orElseThrow();
                minus.setEnabled(interactive() && value > numeric.min()); plus.setEnabled(interactive() && value < numeric.max());
            } catch (NumberFormatException ignored) { }
        }
    }
    static final class Slider extends ControlRegistry.Control {
        final JSlider input = new KitSlider();
        ThemePainting.Focus focus;
        final JLabel value = Labels.literal();
        boolean applying;
        Slider(KitView view, Nodes.Field field) {
            super(view, field, new JPanel(new BorderLayout()));
            component.setOpaque(false); input.setOpaque(false); KitOwnership.mark(input, field.id() + ".input");
            component.add(input, BorderLayout.CENTER); component.add(value, BorderLayout.EAST);
            focus = new ThemePainting.Focus(input, input, view); own(focus);
            javax.swing.event.ChangeListener listener = event -> { if (!applying && interactive()) emit(new UiEvent.Edited(node.id(), TextFields.NumberFilter.text(number(input.getValue())))); };
            input.addChangeListener(listener); own(() -> input.removeChangeListener(listener)); refresh();
        }
        private final class KitSlider extends JSlider {
            @Override public void updateUI() {
                setUI(new javax.swing.plaf.basic.BasicSliderUI(this) {
                @Override public void paintFocus(Graphics graphics) { if(focus!=null&&focus.visible()){var g=(Graphics2D)graphics.create();try{ThemePainting.rounded(g,KitSlider.this,context(),null,ThemePainting.color(context(),SemanticColor.BORDER_FOCUS),false,false);}finally{g.dispose();}} }
                @Override public void paintTrack(Graphics graphics) {
                    var g = (Graphics2D)graphics.create();
                    try { ThemePainting.antialias(g); int h = ThemePainting.metric(context(), DesignMetrics.Role.SLIDER_TRACK); int y = trackRect.y + (trackRect.height - h) / 2;
                        g.setColor(ThemePainting.color(context(), SemanticColor.BORDER_CONTROL)); g.fillRoundRect(trackRect.x, y, trackRect.width, h, h, h);
                        g.setColor(ThemePainting.color(context(), SemanticColor.ACCENT)); g.fillRoundRect(trackRect.x, y, Math.max(0, thumbRect.x + thumbRect.width / 2 - trackRect.x), h, h, h);
                    } finally { g.dispose(); }
                }
                @Override protected Dimension getThumbSize() { int size = ThemePainting.metric(context(), DesignMetrics.Role.SLIDER_THUMB); return new Dimension(size, size); }
                @Override public void paintThumb(Graphics graphics) {
                    var g = (Graphics2D)graphics.create(); try { ThemePainting.antialias(g); g.setColor(ThemePainting.color(context(), SemanticColor.TOGGLE_KNOB)); g.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height); g.setColor(ThemePainting.color(context(), SemanticColor.BORDER_CONTROL)); g.drawOval(thumbRect.x, thumbRect.y, thumbRect.width - 1, thumbRect.height - 1); } finally {g.dispose();}
                }
            });
            }
        }
        double number(int tick) { var numeric = ((Nodes.Field)node).numeric().orElseThrow(); return Math.min(numeric.max(), numeric.min() + tick * numeric.step()); }
        @Override void refresh() {
            state(); var field = (Nodes.Field)node; var numeric = field.numeric().orElseThrow(); applying = true;
            try { input.setMinimum(0); input.setMaximum((int)Math.ceil((numeric.max() - numeric.min()) / numeric.step())); input.setValue((int)Math.round((Double.parseDouble(field.value()) - numeric.min()) / numeric.step())); }
            finally { applying = false; }
            input.setEnabled(interactive()); input.getAccessibleContext().setAccessibleName(field.label());
            value.setText(field.value() + field.hint()); value.setFont(ThemePainting.font(context(), Typography.Role.BODY)); value.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            int gap = ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_GAP); value.setBorder(new EmptyBorder(0, gap, 0, 0));
            component.setPreferredSize(new Dimension(ThemePainting.metric(context(), DesignMetrics.Role.SLIDER_WIDTH), ThemePainting.height(context(), DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY)));
        }
        @Override JComponent focusTarget() { return input; }
    }
}
