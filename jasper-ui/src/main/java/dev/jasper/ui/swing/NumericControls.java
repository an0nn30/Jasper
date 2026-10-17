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
            var center = new JPanel(new ValueLayout()); center.setOpaque(false);
            surface.remove(input); center.add(input); center.add(unit);
            surface.add(center, BorderLayout.CENTER);
            ((JTextField)input).setHorizontalAlignment(context().faces().stepper.alignment());
            javax.swing.event.DocumentListener resize = new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { center.revalidate(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { center.revalidate(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { }
            };
            var document = ((JTextField)input).getDocument(); document.addDocumentListener(resize); own(() -> document.removeDocumentListener(resize));
            minus = button("minus", "−", -1); plus = button("plus", "+", 1);
            context().faces().stepper.arrange(surface, minus, plus);
            refresh();
        }
        /** The value and its unit read as one centred phrase; the value cell keeps a stable minimum width. */
        private final class ValueLayout implements LayoutManager {
            int valueWidth() { var text = ((JTextField)input); var metrics = text.getFontMetrics(text.getFont());
                return Math.max(metrics.stringWidth(text.getText()), metrics.charWidth('0')) + text.getInsets().left + text.getInsets().right + 1; }
            int unitWidth() { return unit.getText().isEmpty() ? 0 : unit.getPreferredSize().width; }
            @Override public void addLayoutComponent(String name, Component component) { }
            @Override public void removeLayoutComponent(Component component) { }
            @Override public Dimension preferredLayoutSize(Container parent) {
                int pad = ThemePainting.metric(context(), DesignMetrics.Role.STEPPER_VALUE_PADDING);
                return new Dimension(Math.max(context().faces().stepper.valueWidth(context()), valueWidth() + unitWidth() + 2 * pad), input.getPreferredSize().height);
            }
            @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
            @Override public void layoutContainer(Container parent) {
                int value = valueWidth(), unitWidth = unitWidth();
                // The default centres the value and its unit; the classic spinner sets them against the arrows.
                int x = context().faces().stepper.alignment() == SwingConstants.RIGHT
                    ? Math.max(0, parent.getWidth() - value - unitWidth - ThemePainting.metric(context(), DesignMetrics.Role.STEPPER_VALUE_PADDING))
                    : Math.max(0, (parent.getWidth() - value - unitWidth) / 2);
                input.setBounds(x, 0, Math.min(value, parent.getWidth()), parent.getHeight());
                unit.setBounds(x + value, 0, unitWidth, parent.getHeight());
            }
        }
        private JButton button(String suffix, String text, int direction) {
            var cue = new ThemePainting.Focus[1];
            var button = new JButton(text) {
                @Override public Dimension getPreferredSize() { return context().faces().stepper.stepSize(context(), direction); }
                @Override protected void paintComponent(Graphics graphics) {
                    var g = (Graphics2D)graphics.create();
                    try { context().faces().stepper.paintStep(this, g, context(), direction, cue[0] != null && cue[0].visible(), () -> standard(g)); }
                    finally { g.dispose(); }
                }
                /** The default look's step button: its focus edge, its rule and its "−" or "+". */
                private void standard(Graphics2D g) {
                    {
                        ThemePainting.antialias(g);
                        if(cue[0]!=null&&cue[0].visible()) ThemePainting.rounded(g,this,context(),null,ThemePainting.color(context(),SemanticColor.BORDER_FOCUS),false,false);
                        g.setColor(ThemePainting.color(context(), SemanticColor.BORDER_INPUT));
                        int x = direction < 0 ? getWidth() - 1 : 0; g.drawLine(x, 0, x, getHeight());
                        if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                        g.setColor(getForeground()); g.setFont(getFont()); var fm = g.getFontMetrics();
                        g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    }
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
            var insets = context().faces().stepper.surfaceInsets(context());
            surface.hint = ""; surface.setBorder(new EmptyBorder(insets.top, insets.left, insets.bottom, insets.right));
            ((JTextField)input).setBorder(new EmptyBorder(0, 0, 0, 0));
            unit.setText(field.hint().isBlank() ? "" : " " + field.hint());
            context().faces().stepper.styleValue(context(), input, unit);
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
                /** The track spans the control; the thumb travels inside it, so its ends stay covered at the limits. */
                @Override public void paintTrack(Graphics graphics) {
                    var g = (Graphics2D)graphics.create();
                    try { ThemePainting.antialias(g); int h = ThemePainting.metric(context(), DesignMetrics.Role.SLIDER_TRACK); int y = thumbRect.y + (thumbRect.height - h) / 2;
                        int x = contentRect.x, width = contentRect.width;
                        g.setColor(ThemePainting.color(context(), SemanticColor.BORDER_CONTROL)); g.fillRoundRect(x, y, width, h, h, h);
                        g.setColor(ThemePainting.color(context(), SemanticColor.ACCENT)); g.fillRoundRect(x, y, Math.max(0, thumbRect.x + thumbRect.width / 2 - x), h, h, h);
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
            value.setText(field.value() + field.hint()); value.setFont(ThemePainting.font(context(), Typography.Role.LABEL)); value.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            // The value reads right-aligned in a stable column, so the track does not move as the number changes.
            int gap = ThemePainting.metric(context(), DesignMetrics.Role.COLUMN_GAP); value.setBorder(new EmptyBorder(0, gap, 0, 0));
            value.setHorizontalAlignment(SwingConstants.RIGHT); value.setPreferredSize(null);
            value.setPreferredSize(new Dimension(Math.max(value.getPreferredSize().width, ThemePainting.metric(context(), DesignMetrics.Role.SLIDER_VALUE_WIDTH)), value.getPreferredSize().height));
            component.setPreferredSize(new Dimension(ThemePainting.metric(context(), DesignMetrics.Role.SLIDER_WIDTH), ThemePainting.height(context(), DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY)));
        }
        @Override JComponent focusTarget() { return input; }
    }
}
