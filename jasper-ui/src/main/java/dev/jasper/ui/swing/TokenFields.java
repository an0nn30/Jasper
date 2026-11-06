package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.StyleConstants;

/**
 * The token field (SDK 0.29.0): its tokens as chips the look's tag face draws, each with a remove button inside it, and
 * the text being typed after them, in a field's box that grows a line at a time as the chips wrap.
 */
final class TokenFields {
    /** The field's width measured alone, in widths of 'm': an ordinary text field's ({@code TextFields}). */
    private static final int COLUMNS = 16;
    /** The least width the typed text takes beside the chips before it moves to a line of its own, in widths of 'm'. */
    private static final int INPUT_COLUMNS = 4;

    private TokenFields() {}

    static final class TokenControl extends ControlRegistry.Control {
        final JPanel container;
        final JLabel label = Labels.literal(), error = Labels.literal();
        final JTextField input = new JTextField();
        final TextFields.FieldSurface surface;
        /** The tokens the field shows: the descriptor's, then the user's changes. */
        List<String> tokens;
        /** One chip per shown token, in order. */
        final List<Token> chips = new ArrayList<>();
        /** An enclosing row names the field, so its label does not show. */
        boolean embedded;
        /** The field is reporting a change: an update meanwhile replaces the tokens whenever it differs from them. */
        boolean notifying;
        /** An input method holds uncommitted (composed) text in the field. */
        boolean composing;

        TokenControl(KitView view, Nodes.TokenField node) {
            super(view, node, new JPanel());
            container = (JPanel) component;
            container.setOpaque(false);
            container.setLayout(new Composition.Vertical(0) {
                @Override int gap(Component previous, Component next) { return ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP); }
            });
            input.setOpaque(false); input.setBorder(new EmptyBorder(0, 0, 0, 0));
            KitOwnership.mark(input, node.id() + ".input");
            // As every kit text field: "none" hides the look and feel's Ctrl+H, so the window's keymap takes it.
            if (TextFields.releasesCtrlH(System.getProperty("os.name", ""))) input.getInputMap(JComponent.WHEN_FOCUSED).put(TextFields.CTRL_H, "none");
            ((AbstractDocument) input.getDocument()).setDocumentFilter(new Commas());
            surface = new TextFields.FieldSurface(context(), input, true);
            surface.setLayout(new TokenLayout(this));
            var focus = new ThemePainting.Focus(input, surface, view); surface.focus = focus; own(focus);
            surface.add(input);
            KitOwnership.mark(label, node.id() + ".label"); KitOwnership.mark(error, node.id() + ".error");
            label.setLabelFor(input);
            container.add(label); container.add(surface); container.add(error);
            listen();
            tokens = node.tokens();
            own(this::clearChips);
            refresh();
        }

        /** The keys, the focus, the input method and the typed text, each listener owned by this control. */
        private void listen() {
            var keys = new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    if (!interactive() || composing || event.getModifiersEx() != 0) return;
                    if (event.getKeyCode() == KeyEvent.VK_ENTER && !input.getText().isBlank()) { event.consume(); commit(); }
                    else if (event.getKeyCode() == KeyEvent.VK_BACK_SPACE && input.getDocument().getLength() == 0 && !tokens.isEmpty()) {
                        event.consume(); change(tokens.subList(0, tokens.size() - 1));
                    }
                }
            };
            input.addKeyListener(keys); own(() -> input.removeKeyListener(keys));
            var leaving = new FocusAdapter() {
                @Override public void focusLost(FocusEvent event) { if (!event.isTemporary()) commit(); }
            };
            input.addFocusListener(leaving); own(() -> input.removeFocusListener(leaving));
            InputMethodListener composition = new InputMethodListener() {
                @Override public void inputMethodTextChanged(InputMethodEvent event) { composing = TextFields.FieldControl.uncommitted(event) > 0; }
                @Override public void caretPositionChanged(InputMethodEvent event) {}
            };
            input.addInputMethodListener(composition); own(() -> input.removeInputMethodListener(composition));
            // The typed text can move to a line of its own, and the hint shows only while nothing is typed.
            var edits = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { edited(); }
                @Override public void removeUpdate(DocumentEvent event) { edited(); }
                @Override public void changedUpdate(DocumentEvent event) { edited(); }
            };
            input.getDocument().addDocumentListener(edits); own(() -> input.getDocument().removeDocumentListener(edits));
            // A press on the box, a chip or the gaps between them puts the caret in the typed text.
            var presses = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { if (interactive() && SwingUtilities.isLeftMouseButton(event)) input.requestFocusInWindow(); }
            };
            surface.addMouseListener(presses); own(() -> surface.removeMouseListener(presses));
        }

        private void edited() {
            var font = ThemePainting.font(context(), Typography.Role.BODY, input.getText());
            if (!font.equals(input.getFont())) input.setFont(font);
            surface.revalidate(); surface.repaint();
        }

        /** Takes the typed text as a token, or drops it when the token rule refuses it; the text is cleared either way. */
        void commit() {
            if (closed || !interactive()) return;
            String typed = input.getText();
            if (typed.isEmpty()) return;
            input.setText("");
            add(List.of(typed));
        }

        /** Adds the {@code typed} tokens the token rule keeps beside the shown ones. */
        void add(List<String> typed) {
            var next = new ArrayList<>(tokens); next.addAll(typed);
            change(Nodes.TokenField.kept(next));
        }

        void remove(String token) {
            var next = new ArrayList<>(tokens); next.remove(token);
            change(next);
        }

        /** Shows {@code next} and reports it, once, unless it is what the field shows already. */
        void change(List<String> next) {
            if (closed || !interactive() || next.equals(tokens)) return;
            tokens = List.copyOf(next);
            chips();
            notifying = true;
            try { emit(new UiEvent.TokensChanged(node.id(), tokens)); } finally { notifying = false; }
        }

        @Override void update(Nodes.Node next) {
            var previous = (Nodes.TokenField) node; var field = (Nodes.TokenField) next;
            if (!previous.tokens().equals(field.tokens()) || notifying && !tokens.equals(field.tokens())) tokens = field.tokens();
            super.update(next);
        }

        @Override void embeddedLabel(boolean value) { embedded = value; label.setVisible(labelShown()); }
        @Override void prompt(boolean value) { surface.prompt = value; label.setVisible(labelShown()); surface.revalidate(); }
        /** Drawn over the field, as a STANDARD field's, unless an enclosing row names the field or it stands in a prompt. */
        private boolean labelShown() { return !embedded && !surface.prompt; }
        @Override JComponent focusTarget() { return input; }
        @Override int firstLineHeight() {
            int above = label.isVisible() ? label.getPreferredSize().height + ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP) : 0;
            return above + fieldHeight();
        }

        /** The height of the field's box holding one line. */
        int fieldHeight() { return ThemePainting.height(context(), surface.prompt ? DesignMetrics.Role.PROMPT_FIELD_HEIGHT : DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY); }

        @Override void refresh() {
            var field = (Nodes.TokenField) node; state();
            boolean enabled = component.isEnabled();
            input.setEnabled(enabled); input.setEditable(enabled); surface.setEnabled(enabled);
            var cursor = Cursor.getPredefinedCursor(enabled ? Cursor.TEXT_CURSOR : Cursor.DEFAULT_CURSOR);
            input.setCursor(cursor); surface.setCursor(cursor);
            edited();
            var text = ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY);
            input.setForeground(text); input.setDisabledTextColor(text); input.setCaretColor(text);
            input.setSelectionColor(ThemePainting.color(context(), SemanticColor.SELECTION_BG));
            input.setSelectedTextColor(text);
            // The kit surface owns placeholder painting; FlatLaf must not paint a second copy.
            input.putClientProperty("JTextField.placeholderText", null);
            surface.role = Typography.Role.BODY; surface.heightRole = DesignMetrics.Role.FIELD_HEIGHT;
            surface.error = !field.state().error().isEmpty();
            int padding = ThemePainting.metric(context(), DesignMetrics.Role.FIELD_HORIZONTAL_PADDING);
            surface.setBorder(new EmptyBorder(0, padding, 0, padding));
            input.getAccessibleContext().setAccessibleName(field.label());
            input.getAccessibleContext().setAccessibleDescription(field.state().error().isEmpty() ? field.hint() : field.state().error());
            label.setText(field.label()); label.setVisible(labelShown()); label.setEnabled(enabled);
            label.setFont(ThemePainting.font(context(), Typography.Role.LABEL)); label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY));
            error.setText(field.state().error()); error.setVisible(!field.state().error().isEmpty()); error.setEnabled(enabled);
            error.setFont(ThemePainting.font(context(), Typography.Role.CAPTION)); error.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_ERROR));
            chips();
            container.revalidate(); container.repaint();
        }

        /** Makes one chip for each shown token, in order, and styles them for the current look and state. */
        private void chips() {
            boolean same = chips.size() == tokens.size();
            for (int i = 0; same && i < tokens.size(); i++) same = chips.get(i).token.equals(tokens.get(i));
            if (!same) {
                clearChips();
                for (int i = 0; i < tokens.size(); i++) {
                    var token = new Token(this, tokens.get(i), i);
                    chips.add(token); surface.add(token.chip.component, i);
                }
            }
            for (var token : chips) token.refresh();
            surface.hint = tokens.isEmpty() ? ((Nodes.TokenField) node).hint() : "";
            surface.revalidate(); surface.repaint();
        }

        private void clearChips() {
            for (var token : chips) { surface.remove(token.chip.component); token.close(); }
            chips.clear();
        }

        /** Each comma typed or pasted adds the text before it, as Enter does; the text after the last comma stays typed. */
        final class Commas extends DocumentFilter {
            @Override public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws BadLocationException {
                replace(bypass, offset, 0, text, attributes);
            }
            @Override public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
                // An input method's composed text is not typed yet: its commas wait for it to commit.
                if (text == null || text.indexOf(',') < 0 || attributes != null && attributes.isDefined(StyleConstants.ComposedTextAttribute)) {
                    super.replace(bypass, offset, length, text, attributes);
                    return;
                }
                var document = bypass.getDocument();
                String current = document.getText(0, document.getLength());
                String[] parts = (current.substring(0, offset) + text + current.substring(offset + length)).split(",", -1);
                bypass.replace(0, document.getLength(), parts[parts.length - 1], attributes);
                add(Arrays.asList(parts).subList(0, parts.length - 1));
            }
        }
    }

    /** One token's chip, drawn by the look's tag face, with its remove button inside the chip after the token. */
    static final class Token implements AutoCloseable {
        final String token;
        final Indicators.ChipControl chip;
        final Buttons.KitButton remove;
        private final TokenControl owner;

        Token(TokenControl owner, String token, int index) {
            this.owner = owner; this.token = token;
            chip = new Indicators.ChipControl(owner.view, new Nodes.Chip(owner.node.id() + ".token." + index, ControlState.READY, token, Nodes.Hue.NEUTRAL));
            remove = new Buttons.KitButton(owner.context());
            remove.kind = Nodes.ButtonKind.GHOST; remove.inner = true; remove.embedded = true;
            KitOwnership.mark(remove, owner.node.id() + ".remove." + index);
            remove.setOpaque(false); remove.setContentAreaFilled(false); remove.setBorderPainted(false); remove.setFocusPainted(false);
            remove.setRolloverEnabled(true); remove.setFocusable(false); remove.setBorder(new EmptyBorder(0, 0, 0, 0));
            // The field's box fades the whole field while it takes no input: the chips and their buttons do not fade again.
            remove.disabledOpacity = 1f;
            remove.addActionListener(event -> owner.remove(token));
            chip.component.setLayout(new Removal());
            chip.component.add(remove);
        }

        void refresh() {
            var context = owner.context();
            boolean live = owner.interactive();
            chip.refresh();
            int extent = UIScale.scale(context.icons().size(context.metrics(), Nodes.IconSize.CAPTION));
            remove.setEnabled(live);
            remove.setIcon(context.icons().icon("close", context.theme(), remove.iconRole(false), extent));
            remove.setDisabledIcon(remove.getIcon());
            remove.putClientProperty("Jasper.button.icon", "close");
            remove.setToolTipText("Remove " + token);
            remove.getAccessibleContext().setAccessibleName("Remove " + token);
            remove.setCursor(Cursor.getPredefinedCursor(live ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            // The chip's own padding, then the button and the padding again after the token.
            int padding = ThemePainting.metric(context, DesignMetrics.Role.CHIP_PADDING);
            chip.component.setBorder(new EmptyBorder(0, padding, 0, padding + remove.getPreferredSize().width + padding));
            chip.component.revalidate(); chip.component.repaint();
        }

        @Override public void close() {
            remove.setIcon(null); remove.setDisabledIcon(null); remove.putClientProperty("Jasper.button.icon", null);
            chip.close();
        }
    }

    /** Places a chip's remove button in the chip's trailing padding, centred there and on the chip's height. */
    static final class Removal implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) {}
        @Override public void removeLayoutComponent(Component component) {}
        // The chip measures itself, from its text and its padding, which holds the button.
        @Override public Dimension preferredLayoutSize(Container parent) { return new Dimension(); }
        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(); }
        @Override public void layoutContainer(Container parent) {
            int trailing = parent.getInsets().right;
            for (Component child : parent.getComponents()) {
                var size = child.getPreferredSize();
                child.setBounds(parent.getWidth() - trailing + (trailing - size.width) / 2, (parent.getHeight() - size.height) / 2, size.width, size.height);
            }
        }
    }

    /**
     * The field's box: the chips in lines, the typed text after the last chip while at least {@link #INPUT_COLUMNS} fit
     * beside it (else on a line of its own), each line as tall as the tallest of them. One line stands centred in a
     * field's height; each further line adds the room a line leaves above and below itself in that height.
     */
    static final class TokenLayout implements Composition.WidthLayout {
        private final TokenControl owner;

        TokenLayout(TokenControl owner) { this.owner = owner; }

        @Override public int height(Container parent, int width) { return arrange(parent, width, false); }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int widest = columns(COLUMNS);
            for (var token : owner.chips) widest = Math.max(widest, token.chip.component.getPreferredSize().width);
            int width = insets.left + insets.right + widest;
            return new Dimension(width, height(parent, parent.getWidth() > 0 ? parent.getWidth() : width));
        }

        @Override public void layoutContainer(Container parent) { arrange(parent, parent.getWidth(), true); }

        private int columns(int count) { return owner.input.getFontMetrics(owner.input.getFont()).charWidth('m') * count; }

        /** The height the box needs at {@code width}; with {@code place}, also gives each chip and the typed text its bounds. */
        private int arrange(Container parent, int width, boolean place) {
            Insets insets = parent.getInsets();
            int available = Math.max(1, width - insets.left - insets.right);
            int gap = ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP);
            var input = owner.input;
            var inputSize = input.getPreferredSize();
            int line = inputSize.height;
            for (var token : owner.chips) line = Math.max(line, token.chip.component.getPreferredSize().height);
            int field = owner.fieldHeight(), spacing = Math.max(0, (field - line) / 2);
            int count = owner.chips.size(), x = 0, row = 0;
            int[] xs = new int[count + 1], rows = new int[count + 1];
            for (int i = 0; i < count; i++) {
                int chip = Math.min(available, owner.chips.get(i).chip.component.getPreferredSize().width);
                if (x > 0 && x + chip > available) { row++; x = 0; }
                xs[i] = x; rows[i] = row; x += chip + gap;
            }
            if (x > 0 && available - x < Math.max(columns(INPUT_COLUMNS), inputSize.width)) { row++; x = 0; }
            xs[count] = x; rows[count] = row;
            int block = (row + 1) * line + row * spacing;
            if (place) {
                int top = insets.top + Math.max(0, (parent.getHeight() - insets.top - insets.bottom - block) / 2);
                for (int i = 0; i < count; i++) {
                    var chip = owner.chips.get(i).chip.component; var size = chip.getPreferredSize();
                    chip.setBounds(insets.left + xs[i], top + rows[i] * (line + spacing) + (line - size.height) / 2, Math.min(available, size.width), size.height);
                }
                input.setBounds(insets.left + xs[count], top + row * (line + spacing) + (line - inputSize.height) / 2, available - xs[count], inputSize.height);
            }
            return insets.top + insets.bottom + Math.max(field, block + 2 * spacing);
        }
    }
}
