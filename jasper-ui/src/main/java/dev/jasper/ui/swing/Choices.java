package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/** Semantic exclusive choices. Selection events identify the node; picker actions identify the action. */
final class Choices {
    private Choices() {}
    static final class ChoiceControl extends ControlRegistry.Control {
        final JLabel label = Labels.literal(), error = Labels.literal();
        final JComponent input;
        final ArrayList<AutoCloseable> optionLifetimes = new ArrayList<>();
        List<Option> options = List.of();
        boolean embedded, settings, identitySection;
        /** A FULL-form setting row's trailing dropdown uses the compact setting-control height. */
        boolean fullSetting;
        @Override void embeddedLabel(boolean value) { embedded=value;label.setVisible(!value); }
        @Override int firstLineHeight() { return input.getHeight() > 0 ? input.getY() + input.getHeight() : input.getPreferredSize().height; }
        boolean applying;
        ChoiceControl(KitView view, Nodes.Choice node) {
            super(view, node, new JPanel(new BorderLayout()));
            if (node.kind() == Nodes.ChoiceKind.DROPDOWN) {
                var combo = new ChoiceCombo(context()); input = combo;
                combo.setRenderer((list, value, index, selected, focus) -> optionLabel(value, selected && index >= 0, index >= 0));
                ActionListener listener = event -> {
                    if (applying) return;
                    if (!interactive()) { refresh(); return; }
                    if (combo.getSelectedItem() instanceof Option selected) emit(new UiEvent.Selected(this.node.id(), Set.of(selected.id())));
                };
                combo.addActionListener(listener); own(() -> combo.removeActionListener(listener));
            } else if (node.kind() == Nodes.ChoiceKind.SEARCHABLE) {
                var combo = new SearchChoices.Combo(context()); input = combo;
                combo.setRenderer((list, value, index, selected, focus) -> optionLabel(value, selected && index >= 0, index >= 0));
                ActionListener listener = event -> {
                    if (applying || combo.freeEntry) return;
                    if (!interactive()) { refresh(); return; }
                    if (combo.getSelectedItem() instanceof Option selected) emit(new UiEvent.Selected(this.node.id(), Set.of(selected.id())));
                };
                combo.addActionListener(listener); own(() -> combo.removeActionListener(listener));
                combo.entered = text -> { if (!applying) { if (interactive()) emit(new UiEvent.Edited(this.node.id(), text)); else refresh(); } };
                combo.submitted = () -> {
                    String action = ((Nodes.Choice)this.node).action();
                    if (action.isBlank() || !interactive()) return false;
                    emit(new UiEvent.Action(action));
                    return true;
                };
                own(() -> { combo.entered = text -> {}; combo.submitted = () -> false; combo.dispose(); });
            } else if (node.kind() == Nodes.ChoiceKind.PICKER) {
                var picker = new PickerButton(context()); input = picker;
                picker.kind = Nodes.ButtonKind.SECONDARY; picker.setContentAreaFilled(false); picker.setBorderPainted(false); picker.setFocusPainted(false); picker.setOpaque(false);
                picker.focus = new ThemePainting.Focus(picker, picker, view); own(picker.focus);
                ActionListener listener = event -> { String action = ((Nodes.Choice)this.node).action(); if (!action.isBlank()) emit(new UiEvent.Action(action)); };
                picker.addActionListener(listener); own(() -> picker.removeActionListener(listener));
                bind(picker,"ENTER",()->picker.doClick(0));bind(picker,"DOWN",()->picker.doClick(0));
            } else { input = new SegmentTrack(context()); own(this::closeOptions); }
            input.putClientProperty("html.disable", true); KitOwnership.mark(input, node.id() + ".input");
            if (input instanceof ChoiceCombo combo) { combo.focus = new ThemePainting.Focus(combo, combo, view); own(combo.focus); }
            var container = (JPanel)component;
            if (input instanceof SegmentTrack) container.setLayout(new SegmentColumn());
            container.setOpaque(false); container.add(label, BorderLayout.NORTH); container.add(input, BorderLayout.CENTER); container.add(error, BorderLayout.SOUTH);
            label.setLabelFor(input); refresh();
        }
        private JComponent optionLabel(Option option, boolean selected, boolean popup) {
            if(option!=null&&(!option.icon().isBlank()||!option.description().isBlank()))return new RichOptions(context(),option,selected,popup,option.id().equals(((Nodes.Choice)node).selectedId()),input.getWidth()-(popup?2*ThemePainting.metric(context(),DesignMetrics.Role.MENU_SEPARATOR_MARGIN):ThemePainting.metric(context(),DesignMetrics.Role.CHOICE_ARROW_WIDTH)));
            return Choices.optionLabel(context(), option == null ? "Choose…" : option.label(), option == null ? Optional.empty() : option.hue(), selected,
                option == null ? SemanticColor.TEXT_TERTIARY : null);
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
                    ((ChoiceCombo)combo).compactHeight=fullSetting;
                    ((ChoiceCombo)combo).settingsWidth=settings?ThemePainting.metric(context(),choice.options().stream().anyMatch(option->!option.icon().isBlank()||!option.description().isBlank())?DesignMetrics.Role.SETTINGS_RICH_CHOICE_WIDTH:DesignMetrics.Role.SETTINGS_CHOICE_WIDTH):0;
                    if (combo instanceof SearchChoices.Combo searchable) choice.search().ifPresent(searchable::configure);
                    if (!options.equals(choice.options())) {
                        combo.setModel(new DefaultComboBoxModel<>(choice.options().toArray(Option[]::new))); options = choice.options();
                    }
                    ((ChoiceCombo)combo).refreshPopup();
                    combo.setSelectedItem(choice.options().stream().filter(o -> o.id().equals(choice.selectedId())).findFirst().orElse(null));
                } else if (input instanceof PickerButton picker) {
                    Option option = choice.options().stream().filter(o -> o.id().equals(choice.selectedId())).findFirst().orElse(null);
                    picker.face=choice.face().orElse(null);
                    picker.empty = picker.face==null ? option == null : picker.face.primary().isEmpty();
                    picker.setText(picker.face==null ? option == null ? "Choose…" : option.label() : picker.empty ? "Choose…" : picker.face.primary());
                    picker.setForeground(ThemePainting.color(context(), picker.textRole(false)));
                    picker.setIcon(choice.icon().isEmpty() ? null : context().icons().icon(choice.icon(), context().theme().semantic(), SemanticColor.TEXT_SECONDARY,
                        com.formdev.flatlaf.util.UIScale.scale(context().icons().size(Nodes.IconSize.CONTROL))));
                    picker.setDisabledIcon(picker.getIcon()); picker.setHorizontalAlignment(SwingConstants.LEADING);
                    int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING), pad = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
                    picker.setBorder(new EmptyBorder(ring, pad, ring, pad + com.formdev.flatlaf.util.UIScale.scale(context().icons().size(Nodes.IconSize.CAPTION)) + ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP)));
                } else {
                    if (!options.equals(choice.options())) {
                        closeOptions(); input.removeAll(); options = choice.options(); input.setLayout(new SegmentLayout());
                        for (int i = 0; i < options.size(); i++) {
                            Option option = options.get(i); var button = new SegmentButton(context()); button.setText(option.label());
                            KitOwnership.mark(button, choice.id() + ".option." + option.id()); button.setOpaque(false); button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false);
                            button.setRolloverEnabled(true); button.putClientProperty("html.disable", true);
                            button.focus = new ThemePainting.Focus(button, button, view); optionLifetimes.add(button.focus);
                            ActionListener listener = event -> select(option.id()); button.addActionListener(listener); optionLifetimes.add(() -> button.removeActionListener(listener));
                            int index = i;
                            bind(button, "LEFT", () -> navigate(index - 1)); bind(button, "RIGHT", () -> navigate(index + 1));
                            bind(button, "UP", () -> navigate(index - ((SegmentLayout)input.getLayout()).columns(input, input.getWidth())));
                            bind(button, "DOWN", () -> navigate(index + ((SegmentLayout)input.getLayout()).columns(input, input.getWidth())));
                            bind(button, "HOME", () -> navigate(0)); bind(button, "END", () -> navigate(options.size() - 1));
                            input.add(button);
                        }
                    }
                    // Form and sheet segments follow the measured form track; settings and identity sections keep theirs.
                    boolean form = !settings && !identitySection;
                    int track = ThemePainting.metric(context(), form ? DesignMetrics.Role.FORM_SEGMENT_PADDING : DesignMetrics.Role.SEGMENT_PADDING);
                    input.setBorder(new EmptyBorder(track, track, track, track));
                    for (int i = 0; i < input.getComponentCount(); i++) {
                        var button = (SegmentButton)input.getComponent(i); Option option = options.get(i);
                        button.form = form; button.setIconTextGap(ThemePainting.metric(context(), form ? DesignMetrics.Role.SEGMENT_HUE_GAP : DesignMetrics.Role.ICON_GAP));
                        button.setEnabled(input.isEnabled()); button.setSelected(option.id().equals(choice.selectedId()));
                        var font = context().typography().font(settings?Typography.Role.BODY:Typography.Role.LABEL, !settings && button.isSelected());
                        button.setFont(SwingTypography.effective(font));
                        button.setForeground(button.textColor(false));
                        button.setIcon(option.hue().map(h -> (javax.swing.Icon)new HueDot(context(), h)).orElse(null)); button.setDisabledIcon(button.getIcon());
                        int pad = ThemePainting.metric(context(), settings?DesignMetrics.Role.SETTINGS_SEGMENT_PADDING:DesignMetrics.Role.SEGMENT_HORIZONTAL_PADDING);
                        if(identitySection)pad=Math.max(pad,(ThemePainting.metric(context(),DesignMetrics.Role.IDENTITY_SECTION_WIDTH)/Math.max(1,choice.options().size())-button.getFontMetrics(button.getFont()).stringWidth(option.label()))/2);
                        button.setBorder(new EmptyBorder(0, pad, 0, pad));
                    }
                }
                label.setVisible(!embedded);label.setText(choice.label()); label.setFont(ThemePainting.font(context(), Typography.Role.LABEL)); label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY)); label.setEnabled(input.isEnabled());
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
    /** An option row as the kit's dropdowns paint it; {@code role} overrides the text color (for placeholders). */
    static JLabel optionLabel(KitContext context, String text, Optional<Nodes.Hue> hue, boolean selected, SemanticColor role) {
        var renderer = new Labels.LiteralLabel() {
            @Override protected void paintComponent(Graphics graphics) {
                if (selected) {
                    var g=(Graphics2D)graphics.create();
                    try { ThemePainting.antialias(g);g.setColor(ThemePainting.color(context,SemanticColor.ACCENT));
                        int r=ThemePainting.metric(context,DesignMetrics.Role.MENU_ITEM_RADIUS);
                        g.fillRoundRect(0,0,getWidth(),getHeight(),r*2,r*2);
                    } finally {g.dispose();}
                }
                super.paintComponent(graphics);
            }
        };
        renderer.putClientProperty("html.disable",true); renderer.setOpaque(false);
        renderer.setText(text);
        renderer.setFont(ThemePainting.font(context, Typography.Role.BODY));
        renderer.setForeground(ThemePainting.color(context, selected ? SemanticColor.TEXT_ONACCENT : role != null ? role : SemanticColor.TEXT_PRIMARY));
        renderer.setBackground(ThemePainting.color(context, selected ? SemanticColor.ACCENT : SemanticColor.SURFACE_MENU));
        hue.ifPresent(h -> renderer.setIcon(new HueDot(context, h)));
        int pad = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
        renderer.setBorder(new EmptyBorder(0, pad, 0, pad)); return renderer;
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
        final KitContext context; boolean empty; Nodes.PickerFace face;
        PickerButton(KitContext context) { super(context); this.context = context; }
        @Override public Dimension getPreferredSize(){if(face==null)return super.getPreferredSize();return new Dimension(ThemePainting.metric(context,DesignMetrics.Role.PICKER_FACE_WIDTH),ThemePainting.height(context,face.density()==Nodes.PickerDensity.COMPACT ? DesignMetrics.Role.PICKER_COMPACT_HEIGHT : DesignMetrics.Role.PICKER_DEFAULT_HEIGHT,face.density()==Nodes.PickerDensity.COMPACT?Typography.Role.PICKER_COMPACT:Typography.Role.BODY));}
        @Override Color fill(boolean hover) {
            return ThemePainting.color(context, hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_CONTROL);
        }
        @Override SemanticColor textRole(boolean hover) { return empty ? SemanticColor.TEXT_TERTIARY : SemanticColor.TEXT_PRIMARY; }
        @Override protected void paintComponent(Graphics graphics) {
            if(face!=null){PickerMenus.paintFace(this,graphics,context,face,empty,focus!=null&&focus.visible());return;}
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
    static class ChoiceCombo extends JComboBox<Option> {
        final KitContext context; ThemePainting.Focus focus; BasicComboPopup popup; int settingsWidth; boolean compactHeight;
        ChoiceCombo(KitContext context) {
            this.context = context; setOpaque(false);
            setBorder(new EmptyBorder(ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING), 0, ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING), 0));
            setUI(kitUi());
        }
        /** Swing reinstalls delegates on theme changes; ordinary choices retain kit painting and popup roles. */
        @Override public void updateUI() {
            if (context == null) { super.updateUI(); return; }
            setUI(kitUi());
            if (getRenderer() instanceof Component renderer) SwingUtilities.updateComponentTreeUI(renderer);
            refreshPopup();
        }
        /** The kit's popup for this combo; a searchable combo supplies its own. */
        ComboPopup createKitPopup(JComboBox<Object> comboBox) {
            popup = new BasicComboPopup(comboBox) {
                @Override protected void paintComponent(Graphics graphics) { Menus.paintSurface(this, graphics, context); }
            };
            popup.setOpaque(false);
            return popup;
        }
        final BasicComboBoxUI kitUi() { return new KitUi(); }
        /** Kit painting around Swing's combo behaviour; distinct class so a reinstall can be recognised. */
        class KitUi extends BasicComboBoxUI {
                @Override protected void installDefaults() { super.installDefaults(); padding = new Insets(0,0,0,0); }
                @Override protected ComboPopup createPopup() { return createKitPopup(comboBox); }
                private boolean rich(){for(int i=0;i<getItemCount();i++){var option=getItemAt(i);if(!option.icon().isBlank()||!option.description().isBlank())return true;}return false;}
                @Override protected Rectangle rectangleForCurrentValue() { var bounds=super.rectangleForCurrentValue();if(rich())bounds.width=comboBox.getWidth()-comboBox.getInsets().left-comboBox.getInsets().right-ThemePainting.metric(context,DesignMetrics.Role.CHOICE_ARROW_WIDTH);return bounds; }
                @Override protected LayoutManager createLayoutManager(){return new ComboBoxLayoutManager(){@Override public void layoutContainer(Container parent){super.layoutContainer(parent);if(arrowButton!=null&&rich()){int width=ThemePainting.metric(context,DesignMetrics.Role.CHOICE_ARROW_WIDTH);var i=comboBox.getInsets();arrowButton.setBounds(comboBox.getWidth()-i.right-width,i.top,width,comboBox.getHeight()-i.top-i.bottom);}}};}
                @Override protected JButton createArrowButton() { return new JButton() {
                    { setBorder(new EmptyBorder(0, 0, 0, 0)); setContentAreaFilled(false); setOpaque(false); setFocusable(false); }
                    @Override protected void paintComponent(Graphics graphics) {
                        // The dropdown chevron shares the picker face's measured glyph and trailing inset.
                        var icon = context.icons().icon("chevron.down", context.theme().semantic(), SemanticColor.TEXT_SECONDARY,
                            ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON));
                        int beyond = comboBox.getWidth() - getX() - getWidth();
                        icon.paintIcon(this, graphics, getWidth() + beyond - ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON_INSET) - icon.getIconWidth(), (getHeight()-icon.getIconHeight())/2);
                    }
                }; }
                @Override public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {}
        }
        void refreshPopup() {
            if (popup == null) return;
            int pad = ThemePainting.metric(context, DesignMetrics.Role.MENU_SEPARATOR_MARGIN);
            popup.setBorder(new EmptyBorder(pad,pad,pad,pad));
            var list = popup.getList();
            list.setBackground(ThemePainting.color(context, SemanticColor.SURFACE_MENU));
            list.setForeground(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
            list.setSelectionBackground(ThemePainting.color(context, SemanticColor.ACCENT));
            list.setSelectionForeground(ThemePainting.color(context, SemanticColor.TEXT_ONACCENT));
            boolean rich=false;for(int i=0;i<getItemCount();i++){var option=getItemAt(i);if(!option.icon().isBlank()||!option.description().isBlank()){rich=true;break;}}
            list.setFixedCellHeight(rich?-1:ThemePainting.height(context, DesignMetrics.Role.BUTTON_HEIGHT, Typography.Role.BODY));
            list.setFont(ThemePainting.font(context, Typography.Role.BODY));
            popup.revalidate(); popup.repaint(); list.repaint();
        }
        @Override public Dimension getPreferredSize() { var size = super.getPreferredSize(); int nominal = ThemePainting.height(context, compactHeight ? DesignMetrics.Role.FULL_SETTING_CHOICE_HEIGHT : DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY); return new Dimension(settingsWidth>0?settingsWidth:size.width, compactHeight ? nominal : Math.max(size.height, nominal)); }
        @Override public void paint(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); super.paint(g); } finally { g.dispose(); }
        }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try { ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SURFACE_CONTROL), ThemePainting.color(context, SemanticColor.BORDER_CONTROL), focus != null && focus.visible(), false); super.paintComponent(g); } finally { g.dispose(); }
        }
    }
    /** Propagates the segmented track's width-dependent height through its labelled control wrapper. */
    static final class SegmentColumn extends BorderLayout implements Composition.WidthLayout {
        @Override public int height(Container parent, int width) {
            Insets insets = parent.getInsets();
            int height = insets.top + insets.bottom, count = 0;
            for (Component child : parent.getComponents()) if (child.isVisible()) {
                height += Composition.height(child, Math.max(0, width - insets.left - insets.right));
                if (count++ > 0) height += getVgap();
            }
            return height;
        }
    }

    /** Equal-width segments use another row before any option's distinguishing label is clipped. */
    static final class SegmentLayout implements Composition.WidthLayout {
        private Dimension cell(Container parent) {
            int width = 0, height = 0;
            for (Component child : parent.getComponents()) {
                Dimension size = child.getPreferredSize();
                width = Math.max(width, size.width); height = Math.max(height, size.height);
            }
            return new Dimension(width, height);
        }
        int columns(Container parent, int width) {
            Insets insets = parent.getInsets();
            return Math.max(1, Math.min(parent.getComponentCount(), Math.max(0, width - insets.left - insets.right) / Math.max(1, cell(parent).width)));
        }
        @Override public int height(Container parent, int width) {
            Insets insets = parent.getInsets();
            int columns = columns(parent, width);
            return insets.top + insets.bottom + ((parent.getComponentCount() + columns - 1) / columns) * cell(parent).height;
        }
        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets(); Dimension cell = cell(parent);
            return new Dimension(insets.left + insets.right + parent.getComponentCount() * cell.width, insets.top + insets.bottom + cell.height);
        }
        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets(); int columns = columns(parent, parent.getWidth());
            if (columns == parent.getComponentCount()) {
                new GridLayout(1, columns, 0, 0).layoutContainer(parent);
                return;
            }
            int rows = Math.max(1, (parent.getComponentCount() + columns - 1) / columns);
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            for (int index = 0; index < parent.getComponentCount(); index++) {
                int column = index % columns, row = index / columns;
                int left = column * width / columns, right = (column + 1) * width / columns;
                int top = row * height / rows, bottom = (row + 1) * height / rows;
                parent.getComponent(index).setBounds(insets.left + left, insets.top + top, right - left, bottom - top);
            }
        }
    }

    static final class SegmentTrack extends JPanel {
        final KitContext context;
        SegmentTrack(KitContext context) { this.context = context; setOpaque(false); int pad = ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_PADDING); setBorder(new EmptyBorder(pad, pad, pad, pad)); }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SURFACE_INPUT), ThemePainting.color(context, SemanticColor.BORDER_SUBTLE), false, false, DesignMetrics.Role.SEGMENT_TRACK_RADIUS); } finally { g.dispose(); }
        }
    }
    static final class SegmentButton extends Buttons.KitButton {
        final KitContext context;
        boolean form;
        SegmentButton(KitContext context) { super(context); this.context = context; kind = Nodes.ButtonKind.GHOST; }
        @Override SemanticColor textRole(boolean hover) { return isSelected() ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_SECONDARY; }
        @Override Color textColor(boolean hover) {
            return isSelected() ? dev.jasper.ui.theme.SurfaceContrast.on(context.theme().semantic(), SemanticColor.TEXT_ONACCENT,
                SemanticColor.SEGMENT_SELECTED_BG, dev.jasper.ui.theme.SurfaceContrast.TEXT) : super.textColor(hover);
        }
        @Override public Dimension getPreferredSize() { var size = super.getPreferredSize(); return new Dimension(size.width, ThemePainting.height(context, form ? DesignMetrics.Role.FORM_SEGMENT_HEIGHT : DesignMetrics.Role.PANEL_ACTION, Typography.Role.LABEL)); }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                if (isSelected()) { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SEGMENT_SELECTED_BG), null, false, false, DesignMetrics.Role.SEGMENT_ITEM_RADIUS); }
                super.paintComponent(graphics);
            } finally { g.dispose(); }
        }
    }
}
