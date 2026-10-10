package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.*;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.*;

/** Persistent documents, guarded numeric edits and literal labels/validation for ordinary fields. */
final class TextFields {
    private TextFields() {}
    static final class FieldControl extends ControlRegistry.Control {
        final JTextComponent input;
        final JLabel label = Labels.literal(), error = Labels.literal(), leading = Labels.literal();
        final FieldSurface surface;
        final JPanel line = new JPanel(new TrailingFieldLayout()), container;
        final Buttons.ButtonControl browse;
        final JScrollPane scroll;
        final DocumentListener listener;
        boolean embedded;
        boolean applying;
        boolean notifying;
        /** An input method holds uncommitted (composed) text in the field. */
        boolean composing;
        long valueRevision;
        FieldControl(KitView view, Nodes.Field node) {
            super(view, node, new JPanel(new BorderLayout())); container = (JPanel)component;
            input = node.kind() == Nodes.FieldKind.TEXTAREA ? new JTextArea(3, 16) : new JTextField(node.kind() == Nodes.FieldKind.PORT ? 6 : 16);
            if (input instanceof JTextArea area) { area.setLineWrap(true); area.setWrapStyleWord(true); }
            input.setOpaque(false); input.setBorder(new EmptyBorder(0, 0, 0, 0)); KitOwnership.mark(input, node.id() + ".input");
            if (node.kind() == Nodes.FieldKind.PORT) ((AbstractDocument)input.getDocument()).setDocumentFilter(new NumericFilter());
            if (node.kind() != Nodes.FieldKind.TEXTAREA && node.kind() != Nodes.FieldKind.PATH) bindSubmit();
            if (node.kind() != Nodes.FieldKind.TEXTAREA) {
                bindKey("shift ENTER", "field.alternate", () -> !((Nodes.Field)this.node).alternateAction().isBlank(),
                    () -> emit(new UiEvent.Action(((Nodes.Field)this.node).alternateAction())));
            }
            own(() -> listKeys(false));
            bindKey("ESCAPE", "field.cancel", () -> !((Nodes.Field)this.node).cancelAction().isBlank(),
                () -> emit(new UiEvent.Action(((Nodes.Field)this.node).cancelAction())));
            if (node.kind() == Nodes.FieldKind.NUMBER) {
                ((AbstractDocument)input.getDocument()).setDocumentFilter(new NumberFilter(() -> ((Nodes.Field)this.node).numeric().orElseThrow()));
                bindStep("UP", 1); bindStep("DOWN", -1); bindStep("KP_UP", 1); bindStep("KP_DOWN", -1);
            }
            surface = new FieldSurface(context(), input, node.kind() == Nodes.FieldKind.TEXTAREA);
            var focus = new ThemePainting.Focus(input, surface, view); surface.focus = focus; own(focus);
            if (node.kind() == Nodes.FieldKind.TEXTAREA) {
                scroll = new JScrollPane(input); scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
                scroll.setOpaque(false); scroll.getViewport().setOpaque(false); surface.add(scroll, BorderLayout.CENTER);
            } else { scroll = null; surface.add(input, BorderLayout.CENTER); }
            if (node.kind() == Nodes.FieldKind.SEARCH || node.kind() == Nodes.FieldKind.PORT) surface.add(leading, BorderLayout.WEST);
            KitOwnership.mark(label, node.id() + ".label"); KitOwnership.mark(error, node.id() + ".error"); KitOwnership.mark(leading, node.id() + ".leading");
            label.setLabelFor(input); label.setText(node.label());
            line.setOpaque(false); line.add(surface, BorderLayout.CENTER);
            if (node.kind() == Nodes.FieldKind.PATH) {
                browse = new Buttons.ButtonControl(view, browseNode(node)); line.add(browse.component, BorderLayout.EAST); own(browse);
            } else browse = null;
            container.setOpaque(false); container.add(label, BorderLayout.NORTH); container.add(line, BorderLayout.CENTER); container.add(error, BorderLayout.SOUTH);
            listener = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { changed(); }
                @Override public void removeUpdate(DocumentEvent e) { changed(); }
                @Override public void changedUpdate(DocumentEvent e) { changed(); }
                private void changed() {
                    if (applying) return;
                    notifying = true;
                    try { emit(new UiEvent.Edited(FieldControl.this.node.id(), input.getText())); }
                    finally { notifying = false; }
                }
            };
            input.getDocument().addDocumentListener(listener); own(() -> input.getDocument().removeDocumentListener(listener));
            java.awt.event.InputMethodListener composition = new java.awt.event.InputMethodListener() {
                @Override public void inputMethodTextChanged(java.awt.event.InputMethodEvent event) { composing = uncommitted(event) > 0; }
                @Override public void caretPositionChanged(java.awt.event.InputMethodEvent event) {}
            };
            input.addInputMethodListener(composition); own(() -> input.removeInputMethodListener(composition));
            setValue(node.value());
            refresh();
        }
        /**
         * Enter in a single-line field with a trailing action runs that action (for example adding a typed entry) and
         * stays with the field; without one, Enter passes on to the containing form or dialog.
         */
        private void bindSubmit() {
            KeyStroke key = KeyStroke.getKeyStroke("ENTER"); String name = "field.submit";
            input.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
            input.getActionMap().put(name, new AbstractAction() {
                @Override public boolean isEnabled() { return !closed && !((Nodes.Field)node).trailingAction().isBlank(); }
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    if (isEnabled()) emit(new UiEvent.Action(((Nodes.Field)node).trailingAction()));
                }
            });
            own(() -> { input.getInputMap(JComponent.WHEN_FOCUSED).remove(key); input.getActionMap().remove(name); });
        }
        /**
         * A key the field owns only while {@code when} holds (an action is configured, its list is mounted): otherwise
         * the key passes on, as Enter without a trailing action does.
         */
        private void bindKey(String stroke, String name, java.util.function.BooleanSupplier when, Runnable run) {
            KeyStroke key = KeyStroke.getKeyStroke(stroke);
            input.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
            input.getActionMap().put(name, new AbstractAction() {
                @Override public boolean isEnabled() { return !closed && interactive() && when.getAsBoolean(); }
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { if (isEnabled()) run.run(); }
            });
            own(() -> { input.getInputMap(JComponent.WHEN_FOCUSED).remove(key); input.getActionMap().remove(name); });
        }
        /** Arrow and page keys this field owns while it names a list (Field.listId); delta 2 means a page. */
        private static final java.util.Map<String, Integer> LIST_KEYS = java.util.Map.of("UP", -1, "KP_UP", -1, "DOWN", 1, "KP_DOWN", 1, "PAGE_UP", -2, "PAGE_DOWN", 2);
        private boolean listKeys;
        /**
         * Installs the list keys only while the field names a list, and removes them otherwise: an entry in the field's own
         * map, even a disabled one, would hide the look and feel's binding for the same key (FlatLaf's macOS Up/Down caret
         * moves to the line's start and end) and let the key reach an ancestor instead. While the named list is hidden,
         * disabled, busy or inside a hidden container (or the field itself is inactive), each key runs the look and feel's
         * own binding for it, so the field behaves exactly as a plain one.
         */
        private void listKeys(boolean wanted) {
            if (wanted == listKeys) return;
            listKeys = wanted;
            for (var move : LIST_KEYS.entrySet()) {
                KeyStroke key = KeyStroke.getKeyStroke(move.getKey()); String name = "field.list." + move.getKey();
                if (!wanted) { input.getInputMap(JComponent.WHEN_FOCUSED).remove(key); input.getActionMap().remove(name); continue; }
                input.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
                input.getActionMap().put(name, new AbstractAction() {
                    @Override public boolean isEnabled() {
                        if (closed) return false;
                        if (moving() != null) return true;
                        var fallback = lookAndFeel(key); return fallback != null && fallback.isEnabled();
                    }
                    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                        if (closed) return;
                        var rows = moving();
                        if (rows != null) {
                            int delta = move.getValue();
                            rows.follow(Math.abs(delta) == 2 ? Integer.signum(delta) * rows.page() : delta);
                            return;
                        }
                        var fallback = lookAndFeel(key);
                        if (fallback != null && fallback.isEnabled()) fallback.actionPerformed(event);
                    }
                });
            }
        }
        /** The list this field's keys move now: the field is active and its list resolves; otherwise null. */
        private InteractiveRows.RowsControl moving() { return interactive() ? list() : null; }
        /** The look and feel's own action for {@code key} on this input (from its parent maps), or null when it binds none. */
        private javax.swing.Action lookAndFeel(KeyStroke key) {
            var parent = input.getInputMap(JComponent.WHEN_FOCUSED).getParent();
            Object name = parent == null ? null : parent.get(key);
            return name == null ? null : input.getActionMap().get(name);
        }
        /** The interactive, mounted item list this field's arrow keys move, or null. */
        private InteractiveRows.RowsControl list() {
            String id = ((Nodes.Field)node).listId();
            if (id.isBlank()) return null;
            var control = view.controlOrNull(id);
            return control instanceof ItemLists.ListControl rows && rows.rowInteractive() && mounted(rows.component) ? rows : null;
        }
        /** Whether the component and every container above it, up to this view, is visible: no hidden ancestor holds it. */
        private boolean mounted(java.awt.Component component) {
            for (var at = component; at != null; at = at.getParent()) {
                if (!at.isVisible()) return false;
                if (at == view) return true;
            }
            return false;
        }
        /** Arrow keys step a NUMBER field by its step, clamped to its bounds; the edit reaches the host like typing. */
        private void bindStep(String stroke, int direction) {
            KeyStroke key = KeyStroke.getKeyStroke(stroke); String name = "number.step." + stroke;
            input.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
            input.getActionMap().put(name, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    if (!interactive()) return;
                    input.setText(NumberFilter.step(input.getText(), ((Nodes.Field)node).numeric().orElseThrow(), direction));
                }
            });
            own(() -> { input.getInputMap(JComponent.WHEN_FOCUSED).remove(key); input.getActionMap().remove(name); });
        }
        private Nodes.Button browseNode(Nodes.Field field) {
            return new Nodes.Button(field.id() + ".browse", field.state(), "Browse…", "", Nodes.ButtonKind.SECONDARY,
                field.trailingAction().isBlank() ? field.id() + ".browse" : field.trailingAction(), "Browse…");
        }
        @Override void update(Nodes.Node next) {
            var previous = (Nodes.Field)node; var field = (Nodes.Field)next;
            // Publish the incoming shape before any explicit document write: NumberFilter reads this node.
            super.update(next);
            if (!previous.value().equals(field.value()) || notifying && !input.getText().equals(field.value())) {
                long revision = ++valueRevision;
                if (!input.getText().equals(field.value())) {
                    if (notifying) {
                        // A synchronous host may normalize Edited text. Swing forbids document writes
                        // during notification; publish the descriptor now and apply the latest value later.
                        EventQueue.invokeLater(() -> {
                            if (!closed && valueRevision == revision) setValue(field.value());
                        });
                    } else setValue(field.value());
                }
            }
        }
        private void setValue(String value) {
            if (input.getText().equals(value)) return;
            applying = true;
            try { input.setText(value); } finally { applying = false; }
        }
        @Override void embeddedLabel(boolean value) { embedded=value;label.setVisible(labelShown()); }
        @Override void hero(boolean value) { surface.hero=value; label.setVisible(labelShown()); }
        /** A search field is labelled by its glyph and hint; its label stays its accessible name only. */
        boolean labelShown() {
            var field = (Nodes.Field)node;
            return !embedded && !surface.hero && field.kind() != Nodes.FieldKind.SEARCH && field.presentation() == Nodes.FieldPresentation.STANDARD;
        }
        static int uncommitted(java.awt.event.InputMethodEvent event) {
            var text = event.getText();
            if (text == null) return 0;
            return Math.max(0, text.getEndIndex() - text.getBeginIndex() - event.getCommittedCharacterCount());
        }
        @Override JComponent focusTarget() { return input; }
        @Override void refresh() {
            var field = (Nodes.Field)node; state(); label.setVisible(labelShown()); input.setEnabled(component.isEnabled());
            listKeys(!field.listId().isBlank() && field.kind() != Nodes.FieldKind.TEXTAREA && field.kind() != Nodes.FieldKind.NUMBER);
            input.setEditable(component.isEnabled()); surface.setEnabled(component.isEnabled());
            input.setCursor(Cursor.getPredefinedCursor(component.isEnabled() ? Cursor.TEXT_CURSOR : Cursor.DEFAULT_CURSOR));
            if (scroll != null) {
                scroll.setEnabled(component.isEnabled());
                scroll.getVerticalScrollBar().setEnabled(component.isEnabled());
                scroll.getVerticalScrollBar().setBackground(ThemePainting.color(context(), SemanticColor.SURFACE_INPUT));
                scroll.getVerticalScrollBar().setForeground(ThemePainting.color(context(), SemanticColor.BORDER_INPUT));
            }
            var role = field.kind() == Nodes.FieldKind.PORT || field.kind() == Nodes.FieldKind.PATH ? Typography.Role.MONO : Typography.Role.BODY;
            input.setFont(ThemePainting.font(context(), role));
            input.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            input.setDisabledTextColor(input.getForeground()); input.setCaretColor(input.getForeground());
            input.setSelectionColor(ThemePainting.color(context(), SemanticColor.SELECTION_BG));
            input.setSelectedTextColor(ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY));
            // The kit surface owns placeholder painting; FlatLaf must not paint a second copy.
            input.putClientProperty("JTextField.placeholderText", null); surface.hint = field.hint(); surface.role = role;
            input.getAccessibleContext().setAccessibleName(field.label());
            String bounds = field.numeric().map(n -> "From " + NumberFilter.text(n.min()) + " to " + NumberFilter.text(n.max())).orElse("");
            String described = field.state().error().isEmpty() ? field.hint() : field.state().error();
            input.getAccessibleContext().setAccessibleDescription(bounds.isEmpty() ? described : described.isEmpty() ? bounds : described + ". " + bounds);
            label.setText(field.label()); label.setFont(ThemePainting.font(context(), Typography.Role.LABEL));
            label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY)); label.setEnabled(component.isEnabled());
            surface.transparent = field.presentation() == Nodes.FieldPresentation.TRANSPARENT;
            error.setText(field.state().error()); error.setVisible(!field.state().error().isEmpty());
            error.setEnabled(component.isEnabled());
            error.setFont(ThemePainting.font(context(), Typography.Role.CAPTION)); error.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ERROR));
            surface.error = !field.state().error().isEmpty();
            leading.setFont(ThemePainting.font(context(), Typography.Role.MONO)); leading.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_PLACEHOLDER));
            leading.setText(field.kind() == Nodes.FieldKind.PORT ? ":" : "");
            if (field.kind() == Nodes.FieldKind.SEARCH) leading.setIcon(context().icons().icon("search", context().theme().semantic(), SemanticColor.TEXT_PLACEHOLDER,
                com.formdev.flatlaf.util.UIScale.scale(context().icons().size(Nodes.IconSize.CONTROL))));
            int gap = ThemePainting.metric(context(), field.kind() == Nodes.FieldKind.PORT ? DesignMetrics.Role.PORT_PREFIX_GAP : DesignMetrics.Role.ICON_GAP);
            leading.setBorder(new EmptyBorder(0, 0, 0, gap));
            int ring = ThemePainting.metric(context(), DesignMetrics.Role.FOCUS_RING);
            int padding = ThemePainting.metric(context(), field.kind() == Nodes.FieldKind.TEXTAREA ? DesignMetrics.Role.TEXTAREA_PADDING : DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            surface.setBorder(new EmptyBorder(ring * 2, padding, ring * 2, padding));
            if (browse != null) {
                browse.parentEnabled = parentEnabled; browse.update(browseNode(field)); browse.component.setVisible(!field.trailingAction().isBlank());
                // The visible label stays "Browse…"; the accessible name says which field it fills.
                browse.component.getAccessibleContext().setAccessibleName("Browse for " + field.label());
            }
            ((BorderLayout)line.getLayout()).setHgap(ThemePainting.metric(context(), DesignMetrics.Role.BUTTON_GAP));
            ((BorderLayout)container.getLayout()).setVgap(ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP));
            container.revalidate(); container.repaint();
        }
    }
    static final class TrailingFieldLayout extends BorderLayout {
        @Override public void layoutContainer(Container parent) {
            Component trailing=getLayoutComponent(EAST),control=getLayoutComponent(CENTER);
            if(trailing==null||!trailing.isVisible()||control==null){super.layoutContainer(parent);return;}
            Insets i=parent.getInsets();int width=Math.max(0,parent.getWidth()-i.left-i.right),height=Math.max(0,parent.getHeight()-i.top-i.bottom);
            int gap=Math.min(getHgap(),width),tw=Math.min(trailing.getPreferredSize().width,Math.max(0,(width-gap)/2));
            control.setBounds(i.left,i.top,Math.max(0,width-gap-tw),height);trailing.setBounds(parent.getWidth()-i.right-tw,i.top,tw,height);
        }
    }
    static final class NumericFilter extends DocumentFilter {
        @Override public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws BadLocationException {
            replace(bypass, offset, 0, text, attributes);
        }
        @Override public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
            if (text == null || text.chars().allMatch(c -> c >= '0' && c <= '9')) super.replace(bypass, offset, length, text, attributes);
        }
    }
    /** Admits text shaped like a number of the field's bounds while typing: a sign only below zero, limited decimals. */
    static final class NumberFilter extends DocumentFilter {
        final java.util.function.Supplier<Nodes.Numeric> numeric;
        NumberFilter(java.util.function.Supplier<Nodes.Numeric> numeric) { this.numeric = numeric; }
        @Override public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws BadLocationException {
            replace(bypass, offset, 0, text, attributes);
        }
        @Override public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
            var document = bypass.getDocument();
            String current = document.getText(0, document.getLength());
            String next = current.substring(0, offset) + (text == null ? "" : text) + current.substring(offset + length);
            if (admits(next, numeric.get())) super.replace(bypass, offset, length, text, attributes);
        }
        @Override public void remove(FilterBypass bypass, int offset, int length) throws BadLocationException {
            var document = bypass.getDocument();
            String current = document.getText(0, document.getLength());
            if (admits(current.substring(0, offset) + current.substring(offset + length), numeric.get())) super.remove(bypass, offset, length);
        }
        static boolean admits(String text, Nodes.Numeric numeric) {
            String sign = numeric.min() < 0 ? "-?" : "";
            String fraction = numeric.decimals() > 0 ? "(\\.\\d{0," + numeric.decimals() + "})?" : "";
            return text.matches(sign + "\\d*" + fraction);
        }
        static String step(String text, Nodes.Numeric numeric, int direction) {
            var min = java.math.BigDecimal.valueOf(numeric.min()); var max = java.math.BigDecimal.valueOf(numeric.max());
            java.math.BigDecimal value;
            try { value = new java.math.BigDecimal(text); }
            catch (NumberFormatException empty) { value = java.math.BigDecimal.ZERO.max(min).min(max); }
            value = value.add(java.math.BigDecimal.valueOf(numeric.step()).multiply(java.math.BigDecimal.valueOf(direction)));
            value = value.max(min).min(max).setScale(numeric.decimals(), java.math.RoundingMode.HALF_UP);
            return plain(value);
        }
        static String text(double value) { return plain(java.math.BigDecimal.valueOf(value)); }
        private static String plain(java.math.BigDecimal value) {
            String text = value.stripTrailingZeros().toPlainString();
            return text.equals("-0") ? "0" : text;
        }
    }
    static final class FieldSurface extends JPanel {
        boolean hero;
        DesignMetrics.Role heightRole=DesignMetrics.Role.FIELD_HEIGHT,radiusRole=DesignMetrics.Role.CONTROL_RADIUS;
        final KitContext context; final JTextComponent input; final boolean multiline;
        Typography.Role role = Typography.Role.BODY; String hint = ""; boolean error;
        /** The parent surface is the field's backdrop: no input fill, the outline kept. */
        boolean transparent;
        ThemePainting.Focus focus;
        FieldSurface(KitContext context, JTextComponent input, boolean multiline) {
            super(new BorderLayout()); this.context = context; this.input = input; this.multiline = multiline; setOpaque(false);
        }
        @Override public Dimension getPreferredSize() {
            var size = super.getPreferredSize();
            int height = ThemePainting.height(context, hero ? DesignMetrics.Role.HERO_FIELD_HEIGHT : heightRole, role);
            return new Dimension(size.width, multiline ? Math.max(size.height, height) : height);
        }
        /**
         * A single-line input never clips its text: when the designed height leaves less than the input's preferred
         * height, the vertical padding shrinks (the input stays centered) instead of the input.
         */
        @Override public Insets getInsets() { return fitted(super.getInsets()); }
        @Override public Insets getInsets(Insets reuse) {
            Insets fitted = fitted(super.getInsets(reuse));
            if (reuse == null) return fitted;
            reuse.set(fitted.top, fitted.left, fitted.bottom, fitted.right);
            return reuse;
        }
        private Insets fitted(Insets base) {
            if (multiline || input == null || getHeight() <= 0) return base;
            int room = Math.max(0, (getHeight() - input.getPreferredSize().height) / 2);
            return new Insets(Math.min(base.top, room), base.left, Math.min(base.bottom, room), base.right);
        }
        @Override public void paint(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try { if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f)); super.paint(g); }
            finally { g.dispose(); }
        }
        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D)graphics.create();
            try {
                ThemePainting.rounded(g, this, context, transparent ? null : ThemePainting.color(context, SemanticColor.SURFACE_INPUT),
                    ThemePainting.color(context, error ? SemanticColor.BORDER_ERROR : focus.visible() ? SemanticColor.BORDER_FOCUS : SemanticColor.BORDER_INPUT),
                    focus.visible(), error,radiusRole);
            } finally { g.dispose(); }
        }
        @Override protected void paintChildren(Graphics graphics) {
            super.paintChildren(graphics);
            if (input.getDocument().getLength() == 0 && !hint.isEmpty()) {
                var g = (Graphics2D)graphics.create();
                try {
                    Point origin = SwingUtilities.convertPoint(input, 0, 0, this);
                    g.clipRect(origin.x, origin.y, input.getWidth(), input.getHeight());
                    g.setFont(input.getFont()); g.setColor(ThemePainting.color(context, SemanticColor.TEXT_PLACEHOLDER));
                    ThemePainting.antialias(g);
                    int y = multiline ? origin.y + g.getFontMetrics().getAscent() : origin.y + (input.getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
                    g.drawString(hint, origin.x, y);
                } finally { g.dispose(); }
            }
        }
    }
}
