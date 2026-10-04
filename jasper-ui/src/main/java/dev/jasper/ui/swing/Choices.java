package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;

/** Semantic exclusive choices. Selection events identify the node; picker actions identify the action. */
final class Choices {
    private Choices() {}
    static final class ChoiceControl extends ControlRegistry.Control {
        final JLabel label = Labels.literal(), error = Labels.literal();
        final JComponent input;
        final ArrayList<AutoCloseable> optionLifetimes = new ArrayList<>();
        List<Option> options = List.of();
        boolean applying;
        ChoiceControl(KitView view, Nodes.Choice node) {
            super(view, node, new JPanel(new BorderLayout()));
            if (node.kind() == Nodes.ChoiceKind.DROPDOWN) {
                var combo = new ChoiceCombo(context()); input = combo;
                combo.setRenderer((list, value, index, selected, focus) -> optionLabel(value, selected));
                ActionListener listener = event -> {
                    if (applying) return;
                    if (!interactive()) { refresh(); return; }
                    if (combo.getSelectedItem() instanceof Option selected) emit(new UiEvent.Selected(this.node.id(), Set.of(selected.id())));
                };
                combo.addActionListener(listener); own(() -> combo.removeActionListener(listener));
            } else if (node.kind() == Nodes.ChoiceKind.PICKER) {
                var picker = new PickerButton(context()); input = picker;
                picker.kind = Nodes.ButtonKind.SECONDARY; picker.setContentAreaFilled(false); picker.setBorderPainted(false); picker.setFocusPainted(false); picker.setOpaque(false);
                picker.focus = new ThemePainting.Focus(picker); own(picker.focus);
                ActionListener listener = event -> { String action = ((Nodes.Choice)this.node).action(); if (!action.isBlank()) emit(new UiEvent.Action(action)); };
                picker.addActionListener(listener); own(() -> picker.removeActionListener(listener));
            } else { input = new SegmentTrack(context()); own(this::closeOptions); }
            input.putClientProperty("html.disable", true); KitOwnership.mark(input, node.id() + ".input");
            if (input instanceof ChoiceCombo combo) { combo.focus = new ThemePainting.Focus(combo); own(combo.focus); }
            var container = (JPanel)component; container.setOpaque(false); container.add(label, BorderLayout.NORTH); container.add(input, BorderLayout.CENTER); container.add(error, BorderLayout.SOUTH);
            label.setLabelFor(input); refresh();
        }
        private JLabel optionLabel(Option option, boolean selected) {
            var renderer = Labels.literal(); renderer.setOpaque(selected);
            renderer.setText(option == null ? "Choose…" : option.label());
            renderer.setFont(ThemePainting.font(context(), Typography.Role.BODY));
            renderer.setForeground(ThemePainting.color(context(), selected ? SemanticColor.TEXT_ONACCENT : option == null ? SemanticColor.TEXT_TERTIARY : SemanticColor.TEXT_PRIMARY));
            renderer.setBackground(ThemePainting.color(context(), selected ? SemanticColor.ACCENT : SemanticColor.SURFACE_MENU));
            if (option != null && option.hue().isPresent()) renderer.setIcon(new HueDot(context(), option.hue().get()));
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            renderer.setBorder(new EmptyBorder(0, pad, 0, pad)); return renderer;
        }
        private void closeOptions() {
            for (var lifetime : optionLifetimes) try { lifetime.close(); } catch (Exception failure) { view.report(failure); }
            optionLifetimes.clear();
        }
        @SuppressWarnings("unchecked")
        @Override void refresh() {
            var choice = (Nodes.Choice)node; state(); applying = true;
            try {
                input.setEnabled(component.isEnabled()); input.getAccessibleContext().setAccessibleName(choice.label());
                input.getAccessibleContext().setAccessibleDescription(choice.state().error());
                input.setFont(ThemePainting.font(context(), Typography.Role.BODY));
                input.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY)); input.setBackground(ThemePainting.color(context(), SemanticColor.SURFACE_CONTROL));
                if (input instanceof JComboBox<?> raw) {
                    var combo = (JComboBox<Option>)raw;
                    if (!options.equals(choice.options())) {
                        combo.setModel(new DefaultComboBoxModel<>(choice.options().toArray(Option[]::new))); options = choice.options();
                    }
                    combo.setSelectedItem(choice.options().stream().filter(o -> o.id().equals(choice.selectedId())).findFirst().orElse(null));
                } else if (input instanceof PickerButton picker) {
                    Option option = choice.options().stream().filter(o -> o.id().equals(choice.selectedId())).findFirst().orElse(null);
                    picker.empty = option == null;
                    picker.setText(option == null ? "Choose…" : option.label());
                    picker.setForeground(ThemePainting.color(context(), picker.textRole(false)));
                    picker.setIcon(context().icons().icon("key", context().theme().semantic(), SemanticColor.TEXT_SECONDARY,
                        com.formdev.flatlaf.util.UIScale.scale(context().icons().size(Nodes.IconSize.CONTROL))));
                    picker.setDisabledIcon(picker.getIcon()); picker.setHorizontalAlignment(SwingConstants.LEADING);
                    int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING), pad = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
                    picker.setBorder(new EmptyBorder(ring, pad, ring, pad + com.formdev.flatlaf.util.UIScale.scale(context().icons().size(Nodes.IconSize.CAPTION)) + ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP)));
                } else {
                    if (!options.equals(choice.options())) {
                        closeOptions(); input.removeAll(); options = choice.options(); input.setLayout(new GridLayout(1, Math.max(1, options.size()), 0, 0));
                        for (int i = 0; i < options.size(); i++) {
                            Option option = options.get(i); var button = new SegmentButton(context()); button.setText(option.label());
                            KitOwnership.mark(button, choice.id() + ".option." + option.id()); button.setOpaque(false); button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false);
                            button.setRolloverEnabled(true); button.putClientProperty("html.disable", true);
                            button.focus = new ThemePainting.Focus(button); optionLifetimes.add(button.focus);
                            ActionListener listener = event -> select(option.id()); button.addActionListener(listener); optionLifetimes.add(() -> button.removeActionListener(listener));
                            int index = i;
                            bind(button, "LEFT", () -> navigate(index - 1)); bind(button, "RIGHT", () -> navigate(index + 1));
                            bind(button, "HOME", () -> navigate(0)); bind(button, "END", () -> navigate(options.size() - 1));
                            input.add(button);
                        }
                    }
                    for (int i = 0; i < input.getComponentCount(); i++) {
                        var button = (SegmentButton)input.getComponent(i); Option option = options.get(i);
                        button.setEnabled(input.isEnabled()); button.setSelected(option.id().equals(choice.selectedId()));
                        button.setFont(ThemePainting.font(context(), Typography.Role.LABEL));
                        button.setForeground(ThemePainting.color(context(), button.isSelected() ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_SECONDARY));
                        button.setIcon(option.hue().map(h -> (javax.swing.Icon)new HueDot(context(), h)).orElse(null)); button.setDisabledIcon(button.getIcon());
                        int pad = ThemePainting.metric(context(), DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);
                        button.setBorder(new EmptyBorder(0, pad, 0, pad));
                    }
                }
                label.setText(choice.label()); label.setFont(ThemePainting.font(context(), Typography.Role.LABEL)); label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY)); label.setEnabled(input.isEnabled());
                error.setText(choice.state().error()); error.setVisible(!choice.state().error().isEmpty()); error.setEnabled(input.isEnabled());
                error.setFont(ThemePainting.font(context(), Typography.Role.CAPTION)); error.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ERROR));
                ((BorderLayout)component.getLayout()).setVgap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
            } finally { applying = false; }
            component.revalidate(); component.repaint();
        }
        private void bind(JButton button, String stroke, Runnable action) {
            KeyStroke key = KeyStroke.getKeyStroke(stroke); String name = "choice." + stroke;
            button.getInputMap(JComponent.WHEN_FOCUSED).put(key, name); button.getActionMap().put(name, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { if (interactive()) action.run(); }
            });
            optionLifetimes.add(() -> { button.getInputMap(JComponent.WHEN_FOCUSED).remove(key); button.getActionMap().remove(name); });
        }
        private void navigate(int index) {
            if (options.isEmpty()) return; index = Math.floorMod(index, options.size());
            input.getComponent(index).requestFocusInWindow(); select(options.get(index).id());
        }
        private void select(String id) { emit(new UiEvent.Selected(node.id(), Set.of(id))); }
        @Override JComponent focusTarget() { return input.getComponentCount() > 0 && input instanceof SegmentTrack ? (JComponent)input.getComponent(0) : input; }
    }
    static Color hue(KitContext context, Nodes.Hue hue) {
        return ThemePainting.color(context, switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL;
        });
    }
    private record HueDot(KitContext context, Nodes.Hue hue) implements javax.swing.Icon {
        @Override public int getIconWidth() { return ThemePainting.metric(context, DesignMetrics.Role.STATUS_DOT); }
        @Override public int getIconHeight() { return getIconWidth(); }
        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            var g = (Graphics2D)graphics.create(); try { ThemePainting.antialias(g); g.setColor(Choices.hue(context, hue)); g.fillOval(x, y, getIconWidth(), getIconHeight()); } finally { g.dispose(); }
        }
    }
    static final class PickerButton extends Buttons.KitButton {
        final KitContext context; boolean empty;
        PickerButton(KitContext context) { super(context); this.context = context; }
        @Override SemanticColor textRole(boolean hover) { return empty ? SemanticColor.TEXT_TERTIARY : SemanticColor.TEXT_PRIMARY; }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            var g = (Graphics2D)graphics.create();
            try {
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                var icon = context.icons().icon("chevron.down", context.theme().semantic(), SemanticColor.TEXT_SECONDARY,
                    com.formdev.flatlaf.util.UIScale.scale(context.icons().size(Nodes.IconSize.CAPTION)));
                icon.paintIcon(this, g, getWidth()-ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING)-icon.getIconWidth(), (getHeight()-icon.getIconHeight())/2);
            } finally { g.dispose(); }
        }
    }
    static final class ChoiceCombo extends JComboBox<Option> {
        final KitContext context; ThemePainting.Focus focus;
        ChoiceCombo(KitContext context) {
            this.context = context; setOpaque(false);
            setBorder(new EmptyBorder(ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING), 0, ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING), 0));
            setUI(new BasicComboBoxUI() {
                @Override protected JButton createArrowButton() { return new JButton() {
                    { setBorder(new EmptyBorder(0, 0, 0, 0)); setContentAreaFilled(false); setOpaque(false); setFocusable(false); }
                    @Override protected void paintComponent(Graphics graphics) {
                        var icon = context.icons().icon("chevron.down", context.theme().semantic(), SemanticColor.TEXT_SECONDARY,
                            com.formdev.flatlaf.util.UIScale.scale(context.icons().size(Nodes.IconSize.CAPTION)));
                        icon.paintIcon(this, graphics, (getWidth()-icon.getIconWidth())/2, (getHeight()-icon.getIconHeight())/2);
                    }
                }; }
                @Override public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {}
            });
        }
        @Override public Dimension getPreferredSize() { var size = super.getPreferredSize(); return new Dimension(size.width, Math.max(size.height, ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY))); }
        @Override public void paint(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); super.paint(g); } finally { g.dispose(); }
        }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try { ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SURFACE_CONTROL), ThemePainting.color(context, SemanticColor.BORDER_CONTROL), focus != null && focus.visible(), false); super.paintComponent(g); } finally { g.dispose(); }
        }
    }
    static final class SegmentTrack extends JPanel {
        final KitContext context;
        SegmentTrack(KitContext context) { this.context = context; setOpaque(false); int pad = ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_PADDING); setBorder(new EmptyBorder(pad, pad, pad, pad)); }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try { ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_INPUT)); int r = ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_TRACK_RADIUS); g.fillRoundRect(0, 0, getWidth(), getHeight(), r, r); g.setColor(ThemePainting.color(context, SemanticColor.BORDER_SUBTLE)); g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, r, r); } finally { g.dispose(); }
        }
    }
    static final class SegmentButton extends Buttons.KitButton {
        final KitContext context;
        SegmentButton(KitContext context) { super(context); this.context = context; kind = Nodes.ButtonKind.GHOST; }
        @Override SemanticColor textRole(boolean hover) { return isSelected() ? SemanticColor.TEXT_ONACCENT : SemanticColor.TEXT_SECONDARY; }
        @Override public Dimension getPreferredSize() { var size = super.getPreferredSize(); return new Dimension(size.width, ThemePainting.height(context, DesignMetrics.Role.PANEL_ACTION, Typography.Role.LABEL)); }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                if (isSelected()) { ThemePainting.antialias(g); if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); g.setColor(ThemePainting.color(context, SemanticColor.SEGMENT_SELECTED_BG)); int r = ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_ITEM_RADIUS); g.fillRoundRect(0, 0, getWidth(), getHeight(), r, r); }
                super.paintComponent(graphics);
            } finally { g.dispose(); }
        }
    }
}
