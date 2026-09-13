package dev.moray.app;

import com.formdev.flatlaf.util.UIScale;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** The themed command search card. Its host owns placement, focus and key routing. */
final class CommandPalette extends JPanel {
    private static final int WIDTH = 560;
    private static final int INPUT_HEIGHT = 56;
    private static final int ROW_HEIGHT = 40;
    private static final int LABEL_HEIGHT = 24;

    private final Consumer<Command> execute;
    private final JTextField query = new JTextField();
    private final JButton escape = new JButton("Esc");
    private final JPanel inputRow = new FixedHeightPanel(INPUT_HEIGHT);
    private final DefaultListModel<Command> model = new DefaultListModel<>();
    private final JList<Command> results = new JList<>(model);
    private final JLabel recentLabel = new JLabel("Recent");
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JLabel empty = new JLabel("No matching commands", SwingConstants.CENTER);
    private final JScrollPane scrollingResults = new JScrollPane(results, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private final ResultRenderer renderer;
    private Color surfaceBorder;
    private boolean composing;

    CommandPalette(boolean macOs, Consumer<String> queryChanged, Consumer<Command> execute, Runnable dismiss) {
        super(new BorderLayout());
        this.execute = Objects.requireNonNull(execute);
        Objects.requireNonNull(queryChanged);
        Objects.requireNonNull(dismiss);
        renderer = new ResultRenderer(macOs);

        setOpaque(false);
        query.setOpaque(false);
        query.setBorder(BorderFactory.createEmptyBorder());
        query.putClientProperty("JTextField.placeholderText", "Type a command\u2026");
        query.getAccessibleContext().setAccessibleName("Search commands");
        query.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
            private void changed() { queryChanged.accept(query.getText()); }
        });
        query.addInputMethodListener(new InputMethodListener() {
            @Override public void inputMethodTextChanged(InputMethodEvent event) {
                composing = uncommittedCharacters(event) > 0;
            }
            @Override public void caretPositionChanged(InputMethodEvent event) {}
        });

        escape.setFocusable(false);
        escape.setOpaque(false);
        escape.setContentAreaFilled(false);
        escape.setMargin(new Insets(0, UIScale.scale(7), 0, UIScale.scale(7)));
        escape.getAccessibleContext().setAccessibleName("Dismiss command palette");
        escape.addActionListener(event -> dismiss.run());
        var escapeHolder = new JPanel(new GridBagLayout());
        escapeHolder.setOpaque(false);
        escapeHolder.add(escape);
        inputRow.setOpaque(false);
        inputRow.setLayout(new BorderLayout(UIScale.scale(10), 0));
        inputRow.add(query, BorderLayout.CENTER);
        inputRow.add(escapeHolder, BorderLayout.LINE_END);
        add(inputRow, BorderLayout.NORTH);

        recentLabel.setOpaque(false);
        recentLabel.putClientProperty("html.disable", Boolean.TRUE);
        recentLabel.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(16), 0, UIScale.scale(16)));
        recentLabel.setPreferredSize(new Dimension(0, UIScale.scale(LABEL_HEIGHT)));
        recentLabel.setVisible(false);

        results.setOpaque(false);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setFocusable(false);
        results.setFixedCellHeight(UIScale.scale(ROW_HEIGHT));
        results.setVisibleRowCount(1);
        results.setCellRenderer(renderer);
        results.getAccessibleContext().setAccessibleName("Commands");
        results.getAccessibleContext().setAccessibleDescription("0 commands");
        results.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                int index = results.locationToIndex(event.getPoint());
                if (index < 0) return;
                var bounds = results.getCellBounds(index, index);
                if (bounds != null && bounds.contains(event.getPoint())) execute.accept(model.get(index));
            }
        });

        cards.setOpaque(false);
        empty.setOpaque(false);
        empty.putClientProperty("html.disable", Boolean.TRUE);
        empty.setPreferredSize(new Dimension(0, UIScale.scale(ROW_HEIGHT)));
        scrollingResults.setBorder(BorderFactory.createEmptyBorder());
        scrollingResults.setOpaque(false); scrollingResults.getViewport().setOpaque(false);
        cards.add(scrollingResults, "results");
        cards.add(empty, "empty");
        cardLayout.show(cards, "empty");

        var body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(recentLabel, BorderLayout.NORTH);
        body.add(cards, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        refreshTheme();
    }

    JTextField queryField() { return query; }
    JList<Command> resultList() { return results; }

    void setResults(List<Command> commands, boolean recent, String preserveSelectionId) {
        if (commands.size() > (recent ? 3 : 5)) throw new IllegalArgumentException("Too many palette results");
        int selected = 0;
        model.clear();
        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            model.addElement(command);
            if (command.id().equals(preserveSelectionId)) selected = i;
        }
        recentLabel.setVisible(recent && !commands.isEmpty());
        if (commands.isEmpty()) results.clearSelection(); else results.setSelectedIndex(selected);
        cardLayout.show(cards, commands.isEmpty() ? "empty" : "results");
        results.setVisibleRowCount(Math.max(1, commands.size()));
        results.getAccessibleContext().setAccessibleDescription(commands.size() + " commands");
        revalidate();
        if (!commands.isEmpty()) {
            scrollingResults.doLayout();
            scrollingResults.getViewport().doLayout();
            results.ensureIndexIsVisible(selected);
        }
        repaint();
    }

    void refreshTheme() {
        Color background = color("Moray.paletteBackground", "Panel.background", Color.DARK_GRAY);
        Color foreground = color("Moray.paletteForeground", "Label.foreground", Color.WHITE);
        Color muted = color("Moray.paletteMutedForeground", "Label.disabledForeground", Color.GRAY);
        Color border = color("Moray.paletteBorder", "Component.borderColor", muted);
        Color accent = color("Moray.paletteAccent", "Component.focusedBorderColor", foreground);
        Color selection = color("Moray.paletteSelectionBackground", "List.selectionBackground", background);
        Color selectionForeground = color("Moray.paletteSelectionForeground", "List.selectionForeground", foreground);

        setBackground(background);
        setForeground(foreground);
        surfaceBorder = border;
        inputRow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, UIScale.scale(1), 0, border),
            BorderFactory.createEmptyBorder(0, UIScale.scale(16), 0, UIScale.scale(10))));
        query.setForeground(foreground);
        query.setCaretColor(accent);
        query.setSelectionColor(selection);
        query.setSelectedTextColor(selectionForeground);
        escape.setForeground(muted);
        escape.setBorder(BorderFactory.createLineBorder(border, UIScale.scale(1), true));
        recentLabel.setForeground(muted);
        empty.setForeground(muted);
        results.setBackground(background);
        results.setForeground(foreground);
        Font uiFont = UIManager.getFont("Label.font");
        if (uiFont != null) results.setFont(uiFont);
        results.setSelectionBackground(selection);
        results.setSelectionForeground(selectionForeground);
        renderer.refreshTheme(foreground, muted, border, selection, selectionForeground);
        revalidate(); repaint();
    }

    void selectRelative(int delta) {
        if (model.isEmpty()) return;
        int index = Math.max(0, Math.min(model.size() - 1, results.getSelectedIndex() + delta));
        results.setSelectedIndex(index);
        results.ensureIndexIsVisible(index);
    }

    void executeNumber(int number) {
        if (number >= 1 && number <= model.size()) execute.accept(model.get(number - 1));
    }

    void executeSelected() {
        Command command = results.getSelectedValue();
        if (command != null) execute.accept(command);
    }

    void setOpeningLabel(String label) {
        if (!label.equals("Recent") && !label.equals("Suggested"))
            throw new IllegalArgumentException("Unknown opening label: " + label);
        recentLabel.setText(label);
    }

    boolean composing() { return composing; }

    @Override public Dimension getPreferredSize() {
        int rows = Math.max(1, model.size());
        int label = recentLabel.isVisible() ? UIScale.scale(LABEL_HEIGHT) : 0;
        return new Dimension(UIScale.scale(WIDTH), UIScale.scale(INPUT_HEIGHT + rows * ROW_HEIGHT) + label);
    }

    @Override protected void paintComponent(Graphics graphics) {
        paintSurface(graphics, getWidth(), getHeight(), getBackground(), surfaceBorder, 12);
    }

    static void paintSurface(Graphics graphics, int width, int height, Color background, Color border, int radius) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = UIScale.scale(radius * 2);
            g.setColor(background);
            g.fillRoundRect(0, 0, width, height, diameter, diameter);
            if (border != null) {
                g.setColor(border);
                g.drawRoundRect(0, 0, width - 1, height - 1, diameter, diameter);
            }
        } finally { g.dispose(); }
    }

    private static int uncommittedCharacters(InputMethodEvent event) {
        AttributedCharacterIterator text = event.getText();
        if (text == null) return 0;
        int length = text.getEndIndex() - text.getBeginIndex();
        return Math.max(0, length - event.getCommittedCharacterCount());
    }

    private static Color color(String key, String fallbackKey, Color fallback) {
        Color value = UIManager.getColor(key);
        if (value == null) value = UIManager.getColor(fallbackKey);
        return value == null ? fallback : value;
    }

    private static final class FixedHeightPanel extends JPanel {
        private final int logicalHeight;

        FixedHeightPanel(int logicalHeight) {
            this.logicalHeight = logicalHeight;
        }

        @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(logicalHeight)); }
        @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(logicalHeight)); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, UIScale.scale(logicalHeight)); }
    }

    private static final class ResultRenderer extends JPanel implements ListCellRenderer<Command> {
        private final boolean macOs;
        private final JLabel icon = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel shortcut = new JLabel();
        private final BadgeLabel badge = new BadgeLabel();
        private Color foreground;
        private Color muted;
        private Color selectionForeground;
        private Color selection;
        private boolean selected;

        ResultRenderer(boolean macOs) {
            this.macOs = macOs;
            setOpaque(false);
            setLayout(null);
            for (JLabel label : List.of(icon, title, shortcut, badge)) {
                label.setOpaque(false);
                label.putClientProperty("html.disable", Boolean.TRUE);
                add(label);
            }
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            badge.setHorizontalAlignment(SwingConstants.CENTER);
        }

        void refreshTheme(Color foreground, Color muted, Color border, Color selection,
                          Color selectionForeground) {
            this.foreground = foreground;
            this.muted = muted;
            this.selection = selection;
            this.selectionForeground = selectionForeground;
            title.setForeground(foreground);
            shortcut.setForeground(muted);
            badge.colors(muted, border, selectionForeground);
            Font uiFont = UIManager.getFont("Label.font");
            if (uiFont == null) uiFont = getFont();
            title.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(13f)));
            icon.setFont(title.getFont());
            shortcut.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(11f)));
            badge.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(10f)));
        }

        @Override public Component getListCellRendererComponent(JList<? extends Command> list, Command command,
                                                                 int index, boolean selected,
                                                                 boolean cellHasFocus) {
            this.selected = selected;
            icon.setIcon(command.icon());
            title.setText(command.title());
            shortcut.setText(formatShortcut(command.action().getValue(Action.ACCELERATOR_KEY), macOs));
            badge.setText((macOs ? "\u2318" : "Ctrl+") + (index + 1));
            title.setForeground(selected ? selectionForeground : foreground);
            shortcut.setForeground(selected ? selectionForeground : muted);
            badge.selected(selected);
            return this;
        }

        @Override public void doLayout() {
            int side = UIScale.scale(12);
            int iconWidth = UIScale.scale(20);
            int gap = UIScale.scale(10);
            int badgeGap = UIScale.scale(10);
            int titleStart = side + iconWidth + gap;
            int badgeWidth = Math.min(Math.max(0, getWidth() - titleStart),
                badge.getPreferredSize().width + UIScale.scale(12));
            int badgeX = Math.max(titleStart, getWidth() - side - badgeWidth);
            int titleEnd = Math.max(titleStart, badgeX - badgeGap);
            int rowHeight = getHeight();

            icon.setBounds(side, 0, iconWidth, rowHeight);
            badge.setBounds(badgeX, (rowHeight - UIScale.scale(22)) / 2, badgeWidth, UIScale.scale(22));

            int shortcutWidth = shortcut.getText().isEmpty() ? 0 : shortcut.getPreferredSize().width;
            int titlePreferred = title.getPreferredSize().width;
            int available = titleEnd - titleStart;
            boolean showShortcut = shortcutWidth > 0
                && titlePreferred + UIScale.scale(12) + shortcutWidth <= available;
            shortcut.setVisible(showShortcut);
            if (showShortcut) {
                int shortcutX = titleEnd - shortcutWidth;
                shortcut.setBounds(shortcutX, 0, shortcutWidth, rowHeight);
                title.setBounds(titleStart, 0, Math.max(0, shortcutX - UIScale.scale(12) - titleStart), rowHeight);
            } else {
                shortcut.setBounds(0, 0, 0, 0);
                title.setBounds(titleStart, 0, Math.max(0, available), rowHeight);
            }
        }

        @Override protected void paintComponent(Graphics graphics) {
            // CellRendererPane assigns this component's bounds immediately before painting;
            // lay out its null-layout children at that final width.
            doLayout();
            if (selected) {
                var g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(selection);
                    int insetX = UIScale.scale(4);
                    int insetY = UIScale.scale(2);
                    int arc = UIScale.scale(12);
                    g.fillRoundRect(insetX, insetY, Math.max(0, getWidth() - insetX * 2),
                        Math.max(0, getHeight() - insetY * 2), arc, arc);
                } finally { g.dispose(); }
            }
            super.paintComponent(graphics);
        }
    }

    private static final class BadgeLabel extends JLabel {
        private Color border;
        private Color selectedForeground;
        private Color normalForeground;
        private boolean selected;

        void colors(Color foreground, Color border, Color selectedForeground) {
            normalForeground = foreground;
            this.border = border;
            this.selectedForeground = selectedForeground;
            setForeground(foreground);
        }

        void selected(boolean selected) {
            this.selected = selected;
            setForeground(selected ? selectedForeground : normalForeground);
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(border);
                int arc = UIScale.scale(8);
                g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), arc, arc);
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
    }

    private static String formatShortcut(Object value, boolean macOs) {
        if (!(value instanceof KeyStroke stroke)) return "";
        int modifiers = stroke.getModifiers();
        String key = stroke.getKeyCode() == 0
            ? String.valueOf(stroke.getKeyChar()) : KeyEvent.getKeyText(stroke.getKeyCode());
        if (macOs) {
            var text = new StringBuilder();
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) text.append("\u2303");
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) text.append("\u2325");
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) text.append("\u21e7");
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) text.append("\u2318");
            return text.append(key).toString();
        }
        var names = new ArrayList<String>();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) names.add("Ctrl");
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) names.add("Alt");
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) names.add("Shift");
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) names.add("Meta");
        names.add(key);
        return String.join("+", names);
    }
}
