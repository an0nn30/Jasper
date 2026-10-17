package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
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
        @Override void embeddedLabel(boolean value) { embedded=value;label.setVisible(labelShown()); }
        /** A TABS choice is labelled by its tabs; its label stays the accessible name. */
        boolean labelShown() { return !embedded && ((Nodes.Choice)node).kind() != Nodes.ChoiceKind.TABS; }
        /**
         * A DROPDOWN in a table cell (its row sets this on every populate): bare until hovered, focused or open, as wide
         * as its value and chevron, and while bare on a selected row its value and chevron take {@code ink}; null off one.
         */
        void inCell(InteractiveRows.Ink ink) {
            if (!(input instanceof ChoiceCombo combo) || combo.cell && combo.cellInk == ink) return;
            combo.cell = true;
            combo.cellInk = ink;
            component.revalidate(); component.repaint();
        }
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
            } else { input = new SegmentTrack(context(), optionsFace()); own(this::closeOptions); }
            input.putClientProperty("html.disable", true); KitOwnership.mark(input, node.id() + ".input");
            if (input instanceof ChoiceCombo combo) { combo.focus = new ThemePainting.Focus(combo, combo, view); own(combo.focus); }
            var container = (JPanel)component;
            if (input instanceof SegmentTrack) container.setLayout(new SegmentColumn());
            else if (node.kind() == Nodes.ChoiceKind.DROPDOWN) { container.setLayout(new CellLayout((ChoiceCombo) input)); container.putClientProperty(CellLayout.class, input); }
            container.setOpaque(false); container.add(label, BorderLayout.NORTH); container.add(input, BorderLayout.CENTER); container.add(error, BorderLayout.SOUTH);
            label.setLabelFor(input); refresh();
        }
        private JComponent optionLabel(Option option, boolean selected, boolean popup) {
            // A list may measure its rows before the combo is laid out; a Settings dropdown's fixed width stands in until
            // then, so its menu rows never outgrow the menu's padded width.
            int base=input.getWidth()>0?input.getWidth():input instanceof ChoiceCombo fixed?fixed.settingsWidth:0;
            if(option!=null&&(!option.icon().isBlank()||!option.description().isBlank()))return new RichOptions(context(),option,selected,popup,option.id().equals(((Nodes.Choice)node).selectedId()),base-(input instanceof ChoiceCombo combo?popup?(combo.popup==null?0:combo.popup.getInsets().left+combo.popup.getInsets().right):combo.arrowWidth():ThemePainting.metric(context(),DesignMetrics.Role.CHOICE_ARROW_WIDTH)),settings);
            var label = Choices.optionLabel(context(), option == null ? "Choose…" : option.label(), option == null ? Optional.empty() : option.hue(), selected,
                option == null ? SemanticColor.TEXT_PLACEHOLDER : null);
            // A Settings dropdown shows its value, and its menu's rows, at label size, as its row title.
            if (settings) label.setFont(ThemePainting.font(context(), Typography.Role.LABEL));
            return label;
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
                    boolean rich=choice.options().stream().anyMatch(option->!option.icon().isBlank()||!option.description().isBlank());
                    int settingsWidth=settings?ThemePainting.metric(context(),rich?DesignMetrics.Role.SETTINGS_RICH_CHOICE_WIDTH:DesignMetrics.Role.SETTINGS_CHOICE_WIDTH):0;
                    // A wider face than the mock's (DejaVu Sans on Linux) widens a rich dropdown until its widest value fits beside
                    // its widest annotation, so every row of a table of such choices keeps one width.
                    if(settings&&rich)settingsWidth=Math.max(settingsWidth,RichOptions.settingsFaceWidth(context(),choice.options())
                        +ThemePainting.metric(context(),DesignMetrics.Role.SETTINGS_CHOICE_ARROW)+combo.getInsets().left+combo.getInsets().right);
                    ((ChoiceCombo)combo).settingsWidth=settingsWidth;
                    if (combo instanceof SearchChoices.Combo searchable) choice.search().ifPresent(searchable::configure);
                    if (!options.equals(choice.options())) {
                        combo.setModel(new DefaultComboBoxModel<>(choice.options().toArray(Option[]::new))); options = choice.options();
                    }
                    ((ChoiceCombo)combo).refreshPopup();
                    ((ChoiceCombo)combo).face = choice.kind() == Nodes.ChoiceKind.DROPDOWN ? choice.face().orElse(null) : null;
                    combo.setSelectedItem(choice.options().stream().filter(o -> o.id().equals(choice.selectedId())).findFirst().orElse(null));
                } else if (input instanceof PickerButton picker) {
                    Option option = choice.options().stream().filter(o -> o.id().equals(choice.selectedId())).findFirst().orElse(null);
                    picker.face=choice.face().orElse(null);
                    picker.empty = picker.face==null ? option == null : picker.face.primary().isEmpty();
                    picker.setText(picker.face==null ? option == null ? "Choose…" : option.label() : picker.empty ? "Choose…" : picker.face.primary());
                    picker.setForeground(ThemePainting.color(context(), picker.textRole(false)));
                    picker.setIcon(choice.icon().isEmpty() ? null : context().icons().icon(choice.icon(), context().theme().semantic(), SemanticColor.TEXT_SECONDARY,
                        com.formdev.flatlaf.util.UIScale.scale(context().icons().size(context().metrics(), Nodes.IconSize.CONTROL))));
                    picker.setDisabledIcon(picker.getIcon()); picker.setHorizontalAlignment(SwingConstants.LEADING);
                    int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING), pad = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
                    picker.setBorder(new EmptyBorder(ring, pad, ring, pad + com.formdev.flatlaf.util.UIScale.scale(context().icons().size(context().metrics(), Nodes.IconSize.CAPTION)) + ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP)));
                } else {
                    if (!options.equals(choice.options())) {
                        closeOptions(); input.removeAll(); options = choice.options(); input.setLayout(optionsFace().layout(context()));
                        for (int i = 0; i < options.size(); i++) {
                            Option option = options.get(i); var button = new SegmentButton(context(), optionsFace()); button.setText(option.label());
                            KitOwnership.mark(button, choice.id() + ".option." + option.id()); button.setOpaque(false); button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false);
                            button.setRolloverEnabled(true); button.putClientProperty("html.disable", true);
                            button.focus = new ThemePainting.Focus(button, button, view); optionLifetimes.add(button.focus);
                            ActionListener listener = event -> select(option.id()); button.addActionListener(listener); optionLifetimes.add(() -> button.removeActionListener(listener));
                            int index = i;
                            bind(button, "LEFT", () -> navigate(index - 1)); bind(button, "RIGHT", () -> navigate(index + 1));
                            bind(button, "UP", () -> navigate(index - columns()));
                            bind(button, "DOWN", () -> navigate(index + columns()));
                            bind(button, "HOME", () -> navigate(0)); bind(button, "END", () -> navigate(options.size() - 1));
                            input.add(button);
                        }
                    }
                    // Form and sheet segments follow the measured form track; settings and identity sections keep theirs.
                    boolean form = !settings && !identitySection;
                    int track = optionsFace().trackPadding(context(), form);
                    input.setBorder(new EmptyBorder(track, track, track, track));
                    for (int i = 0; i < input.getComponentCount(); i++) {
                        var button = (SegmentButton)input.getComponent(i); Option option = options.get(i);
                        button.form = form; if (form) button.setIconTextGap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
                        button.setEnabled(input.isEnabled()); button.setSelected(option.id().equals(choice.selectedId())); button.settings = settings;
                        optionsFace().style(button, option, settings, identitySection, track, choice.options().size());
                    }
                }
                label.setVisible(labelShown());label.setText(choice.label()); label.setFont(ThemePainting.font(context(), Typography.Role.LABEL)); label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY)); label.setEnabled(input.isEnabled());
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
        /** The face this choice's options draw with: the tabs point's for a TABS choice, the exclusive point's otherwise. */
        ChoiceFace optionsFace() { return ((Nodes.Choice) node).kind() == Nodes.ChoiceKind.TABS ? context().faces().tabs : context().faces().exclusive; }
        /** How many options Up and Down move by: a segment line's worth, or every option on a radio row. */
        private int columns() { return input.getLayout() instanceof SegmentLayout layout ? layout.columns(input, input.getWidth()) : Math.max(1, options.size()); }
        @Override JComponent focusTarget() { return input.getComponentCount() > 0 && input instanceof SegmentTrack ? (JComponent)input.getComponent(0) : input; }
    }
    /** An option row as the kit's dropdowns paint it; {@code role} overrides the text color (for placeholders). */
    static JLabel optionLabel(KitContext context, String text, Optional<Nodes.Hue> hue, boolean selected, SemanticColor role) {
        var renderer = new Labels.LiteralLabel() {
            @Override protected void paintComponent(Graphics graphics) {
                if (selected) {
                    var g=(Graphics2D)graphics.create();
                    // A highlighted option is a plain rectangle across the row (spec 2).
                    try { g.setColor(ThemePainting.color(context,SemanticColor.SELECTION_BG)); g.fillRect(0,0,getWidth(),getHeight());
                    } finally {g.dispose();}
                }
                super.paintComponent(graphics);
            }
        };
        renderer.putClientProperty("html.disable",true); renderer.setOpaque(false);
        renderer.setText(text);
        renderer.setFont(ThemePainting.font(context, Typography.Role.BODY));
        renderer.setForeground(ThemePainting.color(context, selected ? SemanticColor.TEXT_ONSELECTION : role != null ? role : SemanticColor.TEXT_PRIMARY));
        renderer.setBackground(ThemePainting.color(context, selected ? SemanticColor.SELECTION_BG : SemanticColor.SURFACE_MENU));
        hue.ifPresent(h -> renderer.setIcon(new HueDot(context, h)));
        int pad = ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
        renderer.setBorder(new EmptyBorder(0, pad, 0, pad)); return renderer;
    }
    /** A hue's tile surface role colour, the base a tile or chip applies {@link ThemePainting#tileShare} to. */
    static Color hueSurface(KitContext context, Nodes.Hue hue) {
        return ThemePainting.color(context, switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE_SURFACE; case GREEN -> SemanticColor.HUE_GREEN_SURFACE; case AMBER -> SemanticColor.HUE_AMBER_SURFACE;
            case PURPLE -> SemanticColor.HUE_PURPLE_SURFACE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL_SURFACE;
        });
    }
    static Color hue(KitContext context, Nodes.Hue hue) {
        return ThemePainting.color(context, switch (hue) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL, NONE -> SemanticColor.HUE_NEUTRAL;
        });
    }
    record HueDot(KitContext context, Nodes.Hue hue) implements javax.swing.Icon {
        @Override public int getIconWidth() { return ThemePainting.metric(context, DesignMetrics.Role.STATUS_DOT); }
        @Override public int getIconHeight() { return getIconWidth(); }
        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            var g = (Graphics2D)graphics.create(); try { ThemePainting.antialias(g); g.setColor(Choices.hue(context, hue)); g.fillOval(x, y, getIconWidth(), getIconHeight()); } finally { g.dispose(); }
        }
    }
    static final class PickerButton extends Buttons.KitButton {
        final KitContext context; boolean empty; Nodes.PickerFace face;
        PickerButton(KitContext context) { super(context); this.context = context; }
        // A dropdown trigger keeps the control edge; secondary buttons have their own.
        @Override Color border() { return ThemePainting.color(context, SemanticColor.BORDER_CONTROL); }
        @Override public Dimension getPreferredSize(){if(face==null)return super.getPreferredSize();return new Dimension(ThemePainting.metric(context,DesignMetrics.Role.PICKER_FACE_WIDTH),ThemePainting.height(context,face.density()==Nodes.PickerDensity.COMPACT ? DesignMetrics.Role.PICKER_COMPACT_HEIGHT : DesignMetrics.Role.PICKER_DEFAULT_HEIGHT,face.density()==Nodes.PickerDensity.COMPACT?Typography.Role.PICKER_COMPACT:Typography.Role.BODY));}
        @Override Color fill(boolean hover) {
            return ThemePainting.color(context, hover ? SemanticColor.SURFACE_CONTROL_HOVER : SemanticColor.SURFACE_CONTROL);
        }
        @Override SemanticColor textRole(boolean hover) { return empty ? SemanticColor.TEXT_PLACEHOLDER : SemanticColor.TEXT_PRIMARY; }
        @Override protected void paintComponent(Graphics graphics) {
            if(face!=null){PickerMenus.paintFace(this,graphics,context,face,empty,focus!=null&&focus.visible());return;}
            super.paintComponent(graphics);
            var g = (Graphics2D)graphics.create();
            try {
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                context.faces().buttons.paintPickerArrow(this, g);
            } finally { g.dispose(); }
        }
        /** The default look's chevron at the button's trailing padding. */
        void paintChevron(Graphics2D g) {
            var icon = context.icons().icon("chevron.down", context.theme().semantic(), SemanticColor.TEXT_SECONDARY,
                com.formdev.flatlaf.util.UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CAPTION)));
            icon.paintIcon(this, g, getWidth()-ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING)-icon.getIconWidth(), (getHeight()-icon.getIconHeight())/2);
        }
    }
    /**
     * The default look's dropdown chevron in {@code role} in {@code arrow}, whose trailing edge lies {@code beyond} inside
     * the combo's: the picker face's measured glyph and trailing inset; a Settings dropdown sets it at a fixed distance
     * from its own arrow lane's trailing edge.
     */
    static void paintChevron(JComponent arrow, Graphics2D graphics, KitContext context, int beyond, boolean settings, SemanticColor role) {
        var icon = context.icons().icon("chevron.down", context.theme().semantic(), role,
            ThemePainting.metric(context, settings ? DesignMetrics.Role.SETTINGS_CHOICE_CHEVRON : DesignMetrics.Role.CHOICE_CHEVRON));
        int x = settings ? arrow.getWidth() - ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_CHOICE_CHEVRON_INSET) - icon.getIconWidth()
            : arrow.getWidth() + beyond - ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON_INSET) - icon.getIconWidth();
        icon.paintIcon(arrow, graphics, Math.max(0, x), (arrow.getHeight()-icon.getIconHeight())/2);
    }
    /** A dropdown's menu: as wide as its field, wider only when its widest row needs it (spec 3). */
    static final class KitComboPopup extends BasicComboPopup {
        final KitContext context;
        @SuppressWarnings("unchecked")
        KitComboPopup(JComboBox<?> combo, KitContext context) { super((JComboBox<Object>) combo); this.context = context; setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) { Menus.paintSurface(this, graphics, context); }
        @Override protected Rectangle computePopupBounds(int px, int py, int pw, int ph) { return super.computePopupBounds(px, py, popupWidth(pw), ph); }
        @Override protected int getPopupHeightForRowCount(int maxRowCount) { return menuHeight(maxRowCount); }
        /**
         * The menu's height for up to {@code maxRowCount} rows. Swing adds up its renderer's heights, but plain rows take the
         * list's fixed height ({@code ChoiceCombo.refreshPopup}), so a menu of plain rows measures those.
         */
        int menuHeight(int maxRowCount) {
            int row = list.getFixedCellHeight(), rows = Math.min(maxRowCount, comboBox.getItemCount());
            if (row <= 0 || rows == 0) return super.getPopupHeightForRowCount(maxRowCount);
            int height = rows * row;
            for (var border : new javax.swing.border.Border[] { scroller.getViewportBorder(), scroller.getBorder() })
                if (border != null) { var insets = border.getBorderInsets(null); height += insets.top + insets.bottom; }
            return height;
        }
        /** The field's width {@code fieldWidth}, or the widest row's with the menu's insets (and its scrollbar, when the rows scroll) when that is wider. */
        int popupWidth(int fieldWidth) {
            var insets = getInsets();
            int bar = comboBox.getItemCount() > comboBox.getMaximumRowCount() ? scroller.getVerticalScrollBar().getPreferredSize().width : 0;
            return Math.max(fieldWidth, list.getPreferredSize().width + bar + insets.left + insets.right);
        }
        /** The menu's padding above and below its rows, its 1 px edge beside them. */
        void restyle() {
            int padding = ThemePainting.metric(context, DesignMetrics.Role.MENU_PADDING), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            setBorder(new EmptyBorder(padding, line, padding, line));
        }
    }

    /**
     * How far a table cell reaches back into the column gap so a cell dropdown's value starts under its column's title:
     * the dropdown's text inset while it is in a cell, else 0 (every other cell, and every header title).
     */
    static int cellLead(Component cell) {
        return cell instanceof JComponent panel && panel.getClientProperty(CellLayout.class) instanceof ChoiceCombo combo && combo.cell ? combo.textLead() : 0;
    }
    /** A choice's label, input and error, where a cell dropdown keeps its own width, centred on the line it is given. */
    static final class CellLayout extends BorderLayout {
        final ChoiceCombo combo;
        CellLayout(ChoiceCombo combo) { this.combo = combo; }
        @Override public void layoutContainer(Container parent) {
            super.layoutContainer(parent);
            if (!combo.cell) return;
            var size = combo.getPreferredSize();
            int height = Math.min(combo.getHeight(), size.height);
            combo.setBounds(combo.getX(), combo.getY() + (combo.getHeight() - height) / 2, Math.min(combo.getWidth(), size.width), height);
        }
    }
    static class ChoiceCombo extends JComboBox<Option> {
        final KitContext context; ThemePainting.Focus focus; KitComboPopup popup; int settingsWidth; boolean compactHeight;
        /** A DROPDOWN's rich closed face (its owner keeps it describing the selected option); null paints the value. */
        Nodes.PickerFace face;
        boolean hover;
        /** In a table cell (set by {@link ChoiceControl#inCell}); the ink of its selected row while it is bare, else null. */
        boolean cell;
        InteractiveRows.Ink cellInk;
        /** A cell dropdown shows only its value and chevron until it is hovered, focused or open. */
        boolean bare() { return cell && !hover && !(focus != null && focus.visible()) && !isPopupVisible(); }
        /** The chevron's role: the selected row's supporting ink while this cell dropdown is bare (plan 3c), else text.secondary. */
        SemanticColor arrowRole() { return bare() && cellInk != null ? cellInk.supporting : SemanticColor.TEXT_SECONDARY; }
        /** The closed value is painted in the combo's foreground (BasicComboBoxUI), so a bare value on a selected row takes the row's ink here. */
        @Override public Color getForeground() { return bare() && cellInk != null ? ThemePainting.color(context, cellInk.text) : super.getForeground(); }
        /** A rich value's trailing chevron lane; a Settings dropdown's lane also holds the gap before its chevron. */
        int arrowWidth() { return ThemePainting.metric(context, settingsWidth > 0 ? DesignMetrics.Role.SETTINGS_CHOICE_ARROW : DesignMetrics.Role.CHOICE_ARROW_WIDTH); }
        ChoiceCombo(KitContext context) {
            this.context = context; setOpaque(false);
            setBorder(new EmptyBorder(ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING), 0, ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING), 0));
            setUI(kitUi());
            addMouseListener(rollover);
        }
        /**
         * Hover over the combo and its chevron button, a mouse target of its own: crossing between them keeps the hover,
         * and only an exit to a point outside the combo ends it.
         */
        final java.awt.event.MouseAdapter rollover = new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                hover = contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), ChoiceCombo.this)); repaint();
            }
        };
        /** Swing reinstalls delegates on theme changes; ordinary choices retain kit painting and popup roles. */
        @Override public void updateUI() {
            if (context == null) { super.updateUI(); return; }
            setUI(kitUi());
            if (getRenderer() instanceof Component renderer) SwingUtilities.updateComponentTreeUI(renderer);
            refreshPopup();
        }
        /** The kit's popup for this combo; a searchable combo supplies its own. */
        ComboPopup createKitPopup(JComboBox<Object> comboBox) {
            popup = new KitComboPopup(comboBox, context);
            return popup;
        }
        final BasicComboBoxUI kitUi() { return new KitUi(); }
        /** Kit painting around Swing's combo behaviour; distinct class so a reinstall can be recognised. */
        class KitUi extends BasicComboBoxUI {
                @Override protected void installDefaults() { super.installDefaults(); padding = new Insets(0,0,0,0); }
                @Override protected ComboPopup createPopup() { return createKitPopup(comboBox); }
                private boolean rich(){for(int i=0;i<getItemCount();i++){var option=getItemAt(i);if(!option.icon().isBlank()||!option.description().isBlank())return true;}return false;}
                @Override protected Rectangle rectangleForCurrentValue() { var bounds=super.rectangleForCurrentValue();if(rich())bounds.width=comboBox.getWidth()-comboBox.getInsets().left-comboBox.getInsets().right-arrowWidth();return bounds; }
                @Override protected LayoutManager createLayoutManager(){return new ComboBoxLayoutManager(){@Override public void layoutContainer(Container parent){super.layoutContainer(parent);if(arrowButton!=null&&rich()){int width=arrowWidth();var i=comboBox.getInsets();arrowButton.setBounds(comboBox.getWidth()-i.right-width,i.top,width,comboBox.getHeight()-i.top-i.bottom);}}};}
                @Override protected JButton createArrowButton() { return new JButton() {
                    { setBorder(new EmptyBorder(0, 0, 0, 0)); setContentAreaFilled(false); setOpaque(false); setFocusable(false); addMouseListener(ChoiceCombo.this.rollover); }
                    @Override protected void paintComponent(Graphics graphics) {
                        context.faces().buttons.paintArrow(ChoiceCombo.this, this, (Graphics2D) graphics, comboBox.getWidth() - getX() - getWidth(), settingsWidth > 0);
                    }
                }; }
                @Override public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {}
        }
        void refreshPopup() {
            if (popup == null) return;
            popup.restyle();
            var list = popup.getList();
            list.setBackground(ThemePainting.color(context, SemanticColor.SURFACE_MENU));
            list.setForeground(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
            list.setSelectionBackground(ThemePainting.color(context, SemanticColor.SELECTION_BG));
            list.setSelectionForeground(ThemePainting.color(context, SemanticColor.TEXT_ONSELECTION));
            boolean rich=false;for(int i=0;i<getItemCount();i++){var option=getItemAt(i);if(!option.icon().isBlank()||!option.description().isBlank()){rich=true;break;}}
            // Rows are the menu's height in the field's own type (a Settings dropdown shows its value at label size).
            var type = settingsWidth > 0 ? Typography.Role.LABEL : Typography.Role.BODY;
            list.setFixedCellHeight(rich?-1:ThemePainting.height(context, DesignMetrics.Role.MENU_ITEM_HEIGHT, type));
            // A Settings dropdown is a fixed width, so its rich rows are exactly the menu's padded width: the list never
            // keeps a row width it measured before the dropdown was laid out.
            list.setFixedCellWidth(rich && settingsWidth > 0 ? Math.max(1, settingsWidth - popup.getInsets().left - popup.getInsets().right) : -1);
            list.setFont(ThemePainting.font(context, type));
            popup.revalidate(); popup.repaint(); list.repaint();
        }
        @Override public Dimension getPreferredSize() {
            var size = super.getPreferredSize();
            int height = face != null ? ThemePainting.height(context, DesignMetrics.Role.PICKER_DEFAULT_HEIGHT, Typography.Role.BODY)
                : ThemePainting.height(context, compactHeight ? DesignMetrics.Role.FULL_SETTING_CHOICE_HEIGHT : DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY);
            return new Dimension(settingsWidth>0?settingsWidth:cell && naturalWidth() > 0 ? cellWidth() : Math.max(size.width, naturalWidth()), compactHeight && face == null ? height : Math.max(size.height, height));
        }
        /**
         * The width a dropdown at its natural size needs to show its longest plain option whole: the value's padding on
         * both sides, then the chevron lane and its trailing inset. Swing's estimate omits the kit's own chevron lane.
         */
        int naturalWidth() {
            if (face != null) return 0;
            var metrics = getFontMetrics(ThemePainting.font(context, Typography.Role.BODY));
            int text = 0;
            for (int i = 0; i < getItemCount(); i++) {
                var option = getItemAt(i);
                if (!option.icon().isBlank() || !option.description().isBlank()) return 0;
                text = Math.max(text, metrics.stringWidth(option.label()));
            }
            var insets = getInsets();
            return insets.left + insets.right + text + 2 * ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING)
                + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_ARROW_WIDTH) + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON_INSET);
        }
        /** A cell dropdown's width: its selected value (or the placeholder), then the chevron, so the chevron follows the value. */
        int cellWidth() {
            var metrics = getFontMetrics(ThemePainting.font(context, Typography.Role.BODY));
            String value = getSelectedItem() instanceof Option selected ? selected.label() : "Choose…";
            var insets = getInsets();
            return insets.left + insets.right + metrics.stringWidth(value) + 2 * ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING)
                + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_ARROW_WIDTH) + ThemePainting.metric(context, DesignMetrics.Role.CHOICE_CHEVRON_INSET);
        }
        /** Where the closed value's text starts inside the combo: its border inset, then the renderer's padding. */
        int textLead() {
            return getInsets().left + ThemePainting.metric(context, DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
        }
        @Override public void paint(Graphics graphics) {
            if (face != null) {
                // The rich face is the whole closed control: tile, primary, caption and chevron; the popup lists the options.
                PickerMenus.paintFace(this, graphics, context, face, face.primary().isEmpty(), focus != null && focus.visible(), hover, isPopupVisible());
                return;
            }
            // Disabled fades the closed face as one layer; the classic face's is already flat, so it reads paler than a disabled button (plan 4b, X13).
            var g = (Graphics2D)graphics.create(); try { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); super.paint(g); } finally { g.dispose(); }
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (bare()) { super.paintComponent(graphics); return; }
            var g = (Graphics2D)graphics.create(); try { context.faces().buttons.paintCombo(this, g, hover, focus != null && focus.visible()); super.paintComponent(g); } finally { g.dispose(); }
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

    /**
     * Segments share one line while their labels fit: equal cells when every cell holds the widest label, otherwise each
     * its own label and an even share of the rest. They use another row only before an option's label would be clipped.
     */
    static final class SegmentLayout implements Composition.WidthLayout {
        private Dimension cell(Container parent) {
            int width = 0, height = 0;
            for (Component child : parent.getComponents()) {
                Dimension size = child.getPreferredSize();
                width = Math.max(width, size.width); height = Math.max(height, size.height);
            }
            return new Dimension(width, height);
        }
        private static int labels(Container parent) {
            int width = 0;
            for (Component child : parent.getComponents()) width += child.getPreferredSize().width;
            return width;
        }
        int columns(Container parent, int width) {
            Insets insets = parent.getInsets(); int room = Math.max(0, width - insets.left - insets.right);
            if (labels(parent) <= room) return Math.max(1, parent.getComponentCount());
            return Math.max(1, Math.min(parent.getComponentCount(), room / Math.max(1, cell(parent).width)));
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
            Insets insets = parent.getInsets(); int columns = columns(parent, parent.getWidth()), count = parent.getComponentCount();
            if (columns == count) {
                int room = Math.max(0, parent.getWidth() - insets.left - insets.right);
                if (count * cell(parent).width <= room) { new GridLayout(1, columns, 0, 0).layoutContainer(parent); return; }
                int spare = room - labels(parent), x = insets.left, height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
                for (int index = 0; index < count; index++) {
                    var child = parent.getComponent(index);
                    int width = child.getPreferredSize().width + spare * (index + 1) / count - spare * index / count;
                    child.setBounds(x, insets.top, width, height); x += width;
                }
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
        final ChoiceFace face;
        SegmentTrack(KitContext context, ChoiceFace face) { this.context = context; this.face = face; setOpaque(false); int pad = ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_PADDING); setBorder(new EmptyBorder(pad, pad, pad, pad)); }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create(); try { face.paintTrack(this, g); } finally { g.dispose(); }
        }
        /** The segment track, and the selected segment's soft shadow in Light (the default face). */
        void paintSegments(Graphics2D g) {
            { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SEGMENT_TRACK), ThemePainting.color(context, SemanticColor.BORDER_INPUT), false, false, DesignMetrics.Role.SEGMENT_TRACK_RADIUS);
                // The selected segment's soft shadow in Light; the track paints it, since the segment's own bounds would clip it.
                if (!context.theme().dark()) for (var child : getComponents())
                    if (child instanceof SegmentButton segment && segment.isVisible() && segment.isSelected())
                        ThemePainting.softShadow(g, new java.awt.geom.Rectangle2D.Double(segment.getX(), segment.getY(), segment.getWidth(), segment.getHeight()),
                            ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_ITEM_RADIUS), UIScale.scale(1),
                            ThemePainting.metric(context, DesignMetrics.Role.SEGMENT_SHADOW_BLUR), context.metrics().value(DesignMetrics.Role.SEGMENT_SHADOW_PERCENT_LIGHT) / 100.0);
            }
        }
    }
    static final class SegmentButton extends Buttons.KitButton {
        final KitContext context;
        boolean form;
        /** In a Settings row the track is one field high: its inset segments fill the rest. */
        boolean settings;
        /** The face this option draws with, its choice's. */
        final ChoiceFace face;
        SegmentButton(KitContext context, ChoiceFace face) { super(context); this.context = context; this.face = face; kind = Nodes.ButtonKind.GHOST; }
        // An option's face belongs to its choice face, never to the buttons face.
        @Override boolean fadesDisabled() { return true; }
        @Override void paintKitFace(Graphics2D g, boolean hover, boolean focused) { paintRaised(g, hover, focused); }
        @Override SemanticColor textRole(boolean hover) { return isSelected() ? SemanticColor.TEXT_PRIMARY : SemanticColor.TEXT_SECONDARY; }
        @Override Color textColor(boolean hover) {
            // The selected segment is not an accent surface: primary text, held to the text floor on its fill.
            return isSelected() ? face.selectedText(context) : ThemePainting.color(context, textRole(hover));
        }
        @Override public Dimension getPreferredSize() { return face.optionSize(this); }
        /** The segment's size (the default face). */
        Dimension segmentSize() {
            var size = super.getPreferredSize();
            return new Dimension(size.width, settings ? ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.LABEL) - 2 * ThemePainting.metric(context, DesignMetrics.Role.SETTINGS_SEGMENT_INSET)
                : ThemePainting.height(context, form ? DesignMetrics.Role.FORM_SEGMENT_HEIGHT : DesignMetrics.Role.PANEL_ACTION, Typography.Role.LABEL));
        }
        @Override protected void paintComponent(Graphics graphics) { face.paintOption(this, graphics, () -> super.paintComponent(graphics)); }
        /** The selected segment's fill, then {@code label}, the button's own glyph and label (the default face). */
        void paintSegment(Graphics graphics, Runnable label) {
            var g = (Graphics2D)graphics.create();
            try {
                if (isSelected()) { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); ThemePainting.rounded(g, this, context, ThemePainting.color(context, SemanticColor.SEGMENT_SELECTED_BG), null, false, false, DesignMetrics.Role.SEGMENT_ITEM_RADIUS); }
                label.run();
            } finally { g.dispose(); }
        }
    }
}
