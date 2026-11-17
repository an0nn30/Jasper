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
 * The token field: its tokens as chips the look's tag face draws, each with a remove button inside it, and
 * the text being typed after them, in a field's box that grows a line at a time as the chips wrap.
 */
final class TokenFields {
    /** The field's width measured alone, in widths of 'm': an ordinary text field's ({@code TextFields}). */
    private static final int COLUMNS = 16;
    /** The least width the typed text takes beside the chips before it moves to a line of its own, in widths of 'm'. */
    private static final int INPUT_COLUMNS = 4;
    /** What ends a token in typed or pasted text: a comma, a line break (LF, CR or both) or a tab. */
    static final java.util.regex.Pattern SEPARATORS = java.util.regex.Pattern.compile("[,\\r\\n\\t]");

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
        /** The token whose chip the keyboard has selected, the focus staying in the input; null while the typed text has the keys. */
        String selected;
        /** The chip last announced as the input's active descendant; null for none. */
        private JComponent announced;

        TokenControl(KitView view, Nodes.TokenField node) {
            super(view, node, new JPanel());
            container = (JPanel) component;
            container.setOpaque(false);
            container.setLayout(new Composition.Vertical(0) {
                @Override int gap(Component previous, Component next) { return ThemePainting.metric(context(), DesignMetrics.Role.ICON_GAP); }
            });
            input.setOpaque(false); input.setBorder(new EmptyBorder(0, 0, 0, 0));
            KitOwnership.mark(input, node.id() + ".input");
            TextFields.releaseCtrlH(input);
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
                    if (!interactive() || composing) return;
                    int code = event.getKeyCode();
                    boolean plain = event.getModifiersEx() == 0;
                    // A selected chip takes its keys; any other key returns to the typed text and does there what it does.
                    if (selected != null) {
                        if (plain && chipKey(code)) { event.consume(); return; }
                        if (!modifier(code)) select(null);
                    }
                    if (!plain) return;
                    if (code == KeyEvent.VK_ENTER && !input.getText().isBlank()) { event.consume(); commit(); }
                    else if (code == KeyEvent.VK_BACK_SPACE && input.getDocument().getLength() == 0 && !tokens.isEmpty()) {
                        event.consume(); change(tokens.subList(0, tokens.size() - 1));
                    } else if ((code == KeyEvent.VK_LEFT || code == KeyEvent.VK_KP_LEFT) && !tokens.isEmpty() && input.getCaretPosition() == 0
                            && input.getSelectionStart() == input.getSelectionEnd()) {
                        event.consume(); select(tokens.getLast());
                    }
                }
            };
            input.addKeyListener(keys); own(() -> input.removeKeyListener(keys));
            var leaving = new FocusAdapter() {
                @Override public void focusLost(FocusEvent event) { if (!event.isTemporary()) commit(); select(null); }
            };
            input.addFocusListener(leaving); own(() -> input.removeFocusListener(leaving));
            InputMethodListener composition = new InputMethodListener() {
                @Override public void inputMethodTextChanged(InputMethodEvent event) { composing = TextFields.FieldControl.uncommitted(event) > 0; }
                @Override public void caretPositionChanged(InputMethodEvent event) {}
            };
            input.addInputMethodListener(composition); own(() -> input.removeInputMethodListener(composition));
            // The typed text can move to a line of its own, and the hint shows only while nothing is typed.
            var edits = new DocumentListener() {
                // Typing (or pasting) returns from a selected chip to the typed text.
                @Override public void insertUpdate(DocumentEvent event) { if (selected != null) select(null); edited(); }
                @Override public void removeUpdate(DocumentEvent event) { edited(); }
                @Override public void changedUpdate(DocumentEvent event) { edited(); }
            };
            input.getDocument().addDocumentListener(edits); own(() -> input.getDocument().removeDocumentListener(edits));
            // A press on the box, a chip or the gaps between them puts the caret in the typed text.
            var presses = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    select(null);
                    if (interactive() && SwingUtilities.isLeftMouseButton(event) && event.getComponent() != input) input.requestFocusInWindow();
                }
            };
            surface.addMouseListener(presses); input.addMouseListener(presses);
            own(() -> { surface.removeMouseListener(presses); input.removeMouseListener(presses); });
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

        /** Whether {@code code} is only a modifier, pressed on its way to a key: it leaves a selected chip selected. */
        private static boolean modifier(int code) {
            return code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH;
        }

        /**
         * A key the selected chip takes: Left, Right, Home and End move among the chips (Right past the last returns to the
         * typed text), Backspace or Delete removes it and selects the chip before or after it, and Escape returns to the
         * typed text. Whether it took the key.
         */
        private boolean chipKey(int code) {
            int at = tokens.indexOf(selected);
            switch (code) {
                case KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> select(tokens.get(Math.max(0, at - 1)));
                case KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> select(at + 1 < tokens.size() ? tokens.get(at + 1) : null);
                case KeyEvent.VK_HOME -> select(tokens.getFirst());
                case KeyEvent.VK_END -> select(tokens.getLast());
                case KeyEvent.VK_BACK_SPACE -> removeSelected(true);
                case KeyEvent.VK_DELETE -> removeSelected(false);
                case KeyEvent.VK_ESCAPE -> select(null);
                default -> { return false; }
            }
            return true;
        }

        /** Removes the selected chip's token and selects its neighbour, the one before or after it; none when it was the last. */
        private void removeSelected(boolean before) {
            int at = tokens.indexOf(selected);
            var next = new ArrayList<>(tokens); next.remove(at);
            selected = next.isEmpty() ? null : next.get(before ? Math.max(0, at - 1) : Math.min(at, next.size() - 1));
            change(next);
        }

        /** Selects {@code token}'s chip, or none for null (or a token the field does not show), and shows and announces it. */
        void select(String token) {
            String next = token != null && tokens.contains(token) ? token : null;
            if (closed || java.util.Objects.equals(next, selected)) return;
            selected = next;
            showSelection();
        }

        /**
         * The selected chip in the focus ring and every other without; the selection published on the input, and announced
         * as its active descendant; the caret hidden meanwhile.
         */
        private void showSelection() {
            if (selected != null && !tokens.contains(selected)) selected = null;
            double radius = ThemePainting.metric(context(), DesignMetrics.Role.CHIP_RADIUS);
            Token chosen = null;
            for (var token : chips) {
                boolean on = token.token.equals(selected);
                if (on) chosen = token;
                ThemePainting.focusRing(token.chip.component, context(), on, radius);
            }
            input.putClientProperty(KitOwnership.SELECTED_TOKEN, selected);
            input.getCaret().setVisible(selected == null && input.isFocusOwner());
            JComponent now = chosen == null ? null : chosen.chip.component;
            if (now != announced) {
                var before = announced; announced = now;
                input.getAccessibleContext().firePropertyChange(javax.accessibility.AccessibleContext.ACCESSIBLE_ACTIVE_DESCENDANT_PROPERTY, before, now);
            }
            // The ring is the halo the view paints round the chip.
            view.repaint();
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
            var field = (Nodes.TokenField) node;
            surface.hint = tokens.isEmpty() ? field.hint() : "";
            // Assistive technology reads the tokens with the field ("Tags: work, news"), then its error or else its hint.
            String note = field.state().error().isEmpty() ? field.hint() : field.state().error();
            String named = tokens.isEmpty() ? "" : field.label() + ": " + String.join(", ", tokens);
            input.getAccessibleContext().setAccessibleDescription(named.isEmpty() ? note : note.isEmpty() ? named : named + ". " + note);
            showSelection();
            surface.revalidate(); surface.repaint();
        }

        private void clearChips() {
            for (var token : chips) { surface.remove(token.chip.component); token.close(); }
            chips.clear();
        }

        /**
         * Each separator typed or pasted (a comma, a line break or a tab) adds the text before it, as Enter does, all in one
         * change; the text after the last separator stays typed. Line breaks reach this filter as typed: a field's
         * document turns them into spaces only without a filter.
         */
        final class Commas extends DocumentFilter {
            @Override public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws BadLocationException {
                replace(bypass, offset, 0, text, attributes);
            }
            @Override public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
                // An input method's composed text is not typed yet: its commas wait for it to commit.
                if (text == null || !SEPARATORS.matcher(text).find() || attributes != null && attributes.isDefined(StyleConstants.ComposedTextAttribute)) {
                    super.replace(bypass, offset, length, text, attributes);
                    return;
                }
                var document = bypass.getDocument();
                String current = document.getText(0, document.getLength());
                String[] parts = SEPARATORS.split(current.substring(0, offset) + text + current.substring(offset + length), -1);
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
            // The user's own words keep the case they were typed in, whatever the look writes its status tags in (H19).
            chip.entered = true;
            remove = new Buttons.KitButton(owner.context());
            remove.kind = Nodes.ButtonKind.GHOST; remove.inner = true; remove.embedded = true;
            KitOwnership.mark(remove, owner.node.id() + ".remove." + index);
            remove.setOpaque(false); remove.setContentAreaFilled(false); remove.setBorderPainted(false); remove.setFocusPainted(false);
            remove.setRolloverEnabled(true); remove.setFocusable(false); remove.setBorder(new EmptyBorder(0, 0, 0, 0));
            // The field's box fades the whole field while it takes no input: the chips and their buttons do not fade again.
            remove.disabledOpacity = 1f;
            remove.addActionListener(event -> owner.remove(token));
            // The field is one Tab stop: the keyboard reaches a chip from the input (Left at its start), not by Tab.
            chip.component.setFocusable(false);
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
