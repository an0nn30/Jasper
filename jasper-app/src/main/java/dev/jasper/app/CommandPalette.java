package dev.jasper.app;

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
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.AttributedCharacterIterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** The themed search card for any scope. Its host owns placement, focus, scope switching and key routing. */
final class CommandPalette extends JPanel {
    private static final int WIDTH = 560;
    private static final int INPUT_HEIGHT = 56;
    private static final int ROW_HEIGHT = 40;
    private static final int LABEL_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 24;
    private static final int COMPACT_CHIP_WIDTH = 420;

    private final boolean macOs;
    private final ObjIntConsumer<PaletteRow> execute;
    private final JTextField query = new JTextField();
    private final JButton escape = new JButton("Esc");
    private final ChipLabel chip = new ChipLabel();
    private final JPanel inputRow = new FixedHeightPanel(INPUT_HEIGHT);
    private final DefaultListModel<PaletteRow> model = new DefaultListModel<>();
    private final JList<PaletteRow> results = new JList<>(model);
    private final JLabel sectionLabel = new JLabel("Recent");
    private final JLabel footer = new JLabel();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JLabel empty = new JLabel("No matching commands", SwingConstants.CENTER);
    private final JScrollPane scrollingResults = new JScrollPane(results, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private final ResultRenderer renderer;
    private Color surfaceBorder;
    private boolean composing;
    private String scopeLabel = "Commands";
    private List<PaletteVerb> verbs = List.of(new PaletteVerb("run", "Run"));
    private int preferredRows = 5;

    CommandPalette(boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteRow> execute, Runnable escape,
                   Runnable chipClicked) {
        super(new BorderLayout());
        this.macOs = macOs;
        this.execute = Objects.requireNonNull(execute);
        Objects.requireNonNull(queryChanged);
        Objects.requireNonNull(escape);
        Objects.requireNonNull(chipClicked);
        renderer = new ResultRenderer(macOs);

        setOpaque(false);
        query.setOpaque(false);
        query.setBorder(BorderFactory.createEmptyBorder());
        query.putClientProperty("JTextField.placeholderText", "Type a command…");
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

        this.escape.setFocusable(false);
        this.escape.setOpaque(false);
        this.escape.setContentAreaFilled(false);
        this.escape.setMargin(new Insets(0, UIScale.scale(7), 0, UIScale.scale(7)));
        this.escape.getAccessibleContext().setAccessibleName("Dismiss command palette");
        this.escape.addActionListener(event -> escape.run());
        var escapeHolder = new JPanel(new GridBagLayout());
        escapeHolder.setOpaque(false);
        escapeHolder.add(this.escape);
        chip.set("Commands", null);
        chip.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) chipClicked.run(); }
        });
        var chipHolder = new JPanel(new GridBagLayout());
        chipHolder.setOpaque(false);
        chipHolder.add(chip);
        inputRow.setOpaque(false);
        inputRow.setLayout(new BorderLayout(UIScale.scale(10), 0));
        inputRow.add(chipHolder, BorderLayout.LINE_START);
        inputRow.add(query, BorderLayout.CENTER);
        inputRow.add(escapeHolder, BorderLayout.LINE_END);
        add(inputRow, BorderLayout.NORTH);

        for (JLabel label : List.of(sectionLabel, footer)) {
            label.setOpaque(false);
            label.putClientProperty("html.disable", Boolean.TRUE);
            label.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(16), 0, UIScale.scale(16)));
            label.setVisible(false);
        }
        sectionLabel.setPreferredSize(new Dimension(0, UIScale.scale(LABEL_HEIGHT)));
        footer.setPreferredSize(new Dimension(0, UIScale.scale(FOOTER_HEIGHT)));

        results.setOpaque(false);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setFocusable(false);
        results.setFixedCellHeight(UIScale.scale(ROW_HEIGHT));
        results.setVisibleRowCount(1);
        results.setCellRenderer(renderer);
        results.getAccessibleContext().setAccessibleName("Commands");
        results.getAccessibleContext().setAccessibleDescription("0 results");
        results.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                int index = results.locationToIndex(event.getPoint());
                if (index < 0) return;
                var bounds = results.getCellBounds(index, index);
                if (bounds != null && bounds.contains(event.getPoint())) execute.accept(model.get(index), 0);
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
        body.add(sectionLabel, BorderLayout.NORTH);
        body.add(cards, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
        refreshTheme();
    }

    JTextField queryField() { return query; }
    JList<PaletteRow> resultList() { return results; }
    JLabel chip() { return chip; }
    JLabel footer() { return footer; }
    JLabel sectionLabel() { return sectionLabel; }

    void setScope(String label, Icon icon, String placeholder, List<PaletteVerb> verbs, int preferredRows, boolean monospace) {
        scopeLabel = Objects.requireNonNull(label);
        this.verbs = List.copyOf(verbs);
        this.preferredRows = Math.max(1, preferredRows);
        chip.set(label, icon);
        chip.getAccessibleContext().setAccessibleName("Scope: " + label);
        query.putClientProperty("JTextField.placeholderText", placeholder);
        query.getAccessibleContext().setAccessibleName("Search " + label.toLowerCase(Locale.ROOT));
        results.getAccessibleContext().setAccessibleName(label);
        footer.setText(footerText(this.verbs, macOs));
        footer.setVisible(this.verbs.size() > 1);
        renderer.setMonospace(monospace);
        empty.setText("No matching " + label.toLowerCase(Locale.ROOT));
        revalidate(); repaint();
    }

    static String footerText(List<PaletteVerb> verbs, boolean macOs) {
        if (verbs.size() < 2) return "";
        String enter = macOs ? "⏎" : "Enter";
        String primary = macOs ? "⌘⏎" : "Ctrl+Enter";
        return enter + " " + verbs.get(0).label() + "  " + primary + " " + verbs.get(1).label();
    }

    void setResults(List<PaletteRow> rows, String label, String selectionId) {
        if (rows.size() > PaletteResults.MAX_ROWS) throw new IllegalArgumentException("Too many palette results");
        int selected = 0;
        model.clear();
        for (int i = 0; i < rows.size(); i++) {
            PaletteRow row = rows.get(i);
            model.addElement(row);
            if (row.id().equals(selectionId)) selected = i;
        }
        sectionLabel.setText(label == null ? "" : label);
        sectionLabel.setVisible(label != null && !rows.isEmpty());
        if (rows.isEmpty()) results.clearSelection(); else results.setSelectedIndex(selected);
        cardLayout.show(cards, rows.isEmpty() ? "empty" : "results");
        results.setVisibleRowCount(Math.max(1, Math.min(rows.size(), preferredRows)));
        results.getAccessibleContext().setAccessibleDescription(rows.size() + " results"
            + (footer.isVisible() ? "; " + footer.getText() : ""));
        revalidate();
        if (!rows.isEmpty()) {
            scrollingResults.doLayout();
            scrollingResults.getViewport().doLayout();
            results.ensureIndexIsVisible(selected);
        }
        repaint();
    }

    void refreshTheme() {
        Color background = color("Jasper.paletteBackground", "Panel.background", Color.DARK_GRAY);
        Color foreground = color("Jasper.paletteForeground", "Label.foreground", Color.WHITE);
        Color muted = color("Jasper.paletteMutedForeground", "Label.disabledForeground", Color.GRAY);
        Color border = color("Jasper.paletteBorder", "Component.borderColor", muted);
        Color accent = color("Jasper.paletteAccent", "Component.focusedBorderColor", foreground);
        Color selection = color("Jasper.paletteSelectionBackground", "List.selectionBackground", background);
        Color selectionForeground = color("Jasper.paletteSelectionForeground", "List.selectionForeground", foreground);

        setBackground(background);
        setForeground(foreground);
        surfaceBorder = border;
        inputRow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, UIScale.scale(1), 0, border),
            BorderFactory.createEmptyBorder(0, UIScale.scale(12), 0, UIScale.scale(10))));
        query.setForeground(foreground);
        query.setCaretColor(accent);
        query.setSelectionColor(selection);
        query.setSelectedTextColor(selectionForeground);
        escape.setForeground(muted);
        escape.setBorder(BorderFactory.createLineBorder(border, UIScale.scale(1), true));
        chip.colors(accent, selection, border);
        sectionLabel.setForeground(muted);
        footer.setForeground(muted);
        empty.setForeground(muted);
        results.setBackground(background);
        results.setForeground(foreground);
        Font uiFont = UIManager.getFont("Label.font");
        if (uiFont != null) {
            results.setFont(uiFont);
            chip.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(11f)));
            footer.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(11f)));
        }
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
        if (number >= 1 && number <= Math.min(5, model.size())) execute.accept(model.get(number - 1), 0);
    }

    void executeSelected() { executeSelected(0); }

    void executeSelected(int verb) {
        PaletteRow row = results.getSelectedValue();
        if (row != null) execute.accept(row, verb);
    }

    boolean composing() { return composing; }

    @Override public void doLayout() {
        chip.setCompact(getWidth() < UIScale.scale(COMPACT_CHIP_WIDTH));
        super.doLayout();
    }

    @Override public Dimension getPreferredSize() {
        int rows = Math.max(1, Math.min(model.size(), preferredRows));
        int label = sectionLabel.isVisible() ? LABEL_HEIGHT : 0;
        int footerHeight = footer.isVisible() ? FOOTER_HEIGHT : 0;
        return new Dimension(UIScale.scale(WIDTH), UIScale.scale(INPUT_HEIGHT + rows * ROW_HEIGHT + label + footerHeight));
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

    /** The scope pill at the left of the input: icon plus label, or icon only in narrow cards. */
    private static final class ChipLabel extends JLabel {
        private String fullText = "";
        private Color fill, outline;
        private boolean compact;

        ChipLabel() {
            setOpaque(false);
            putClientProperty("html.disable", Boolean.TRUE);
            setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(8), 0, UIScale.scale(8)));
            setIconTextGap(UIScale.scale(5));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        void set(String label, Icon icon) { fullText = label; setIcon(icon); setText(compact && icon != null ? null : label); }
        void setCompact(boolean value) {
            if (compact == value) return;
            compact = value; setText(compact && getIcon() != null ? null : fullText); revalidate();
        }
        void colors(Color foreground, Color fill, Color outline) {
            setForeground(foreground); this.fill = fill; this.outline = outline; repaint();
        }

        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, UIScale.scale(24));
        }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = UIScale.scale(12);
                if (fill != null) { g.setColor(fill); g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc); }
                if (outline != null) { g.setColor(outline); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc); }
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
    }

    private static final class ResultRenderer extends JPanel implements ListCellRenderer<PaletteRow> {
        private final boolean macOs;
        private final JLabel icon = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel tag = new JLabel();
        private final BadgeLabel badge = new BadgeLabel();
        private Color foreground, muted, selectionForeground, selection;
        private Font uiTitle = getFont(), monoTitle = getFont();
        private boolean monospace, selected;

        ResultRenderer(boolean macOs) {
            this.macOs = macOs;
            setOpaque(false);
            setLayout(null);
            for (JLabel label : List.of(icon, title, detail, tag, badge)) {
                label.setOpaque(false);
                label.putClientProperty("html.disable", Boolean.TRUE);
                add(label);
            }
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            badge.setHorizontalAlignment(SwingConstants.CENTER);
        }

        void setMonospace(boolean value) { monospace = value; }

        void refreshTheme(Color foreground, Color muted, Color border, Color selection, Color selectionForeground) {
            this.foreground = foreground; this.muted = muted; this.selection = selection; this.selectionForeground = selectionForeground;
            title.setForeground(foreground);
            detail.setForeground(muted);
            tag.setForeground(muted);
            badge.colors(muted, border, selectionForeground);
            Font uiFont = UIManager.getFont("Label.font");
            if (uiFont == null) uiFont = getFont();
            uiTitle = uiFont.deriveFont(Font.PLAIN, UIScale.scale(13f));
            monoTitle = new Font(Font.MONOSPACED, Font.PLAIN, UIScale.scale(13));
            icon.setFont(uiTitle);
            detail.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(11f)));
            tag.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(11f)));
            badge.setFont(uiFont.deriveFont(Font.PLAIN, UIScale.scale(10f)));
        }

        @Override public Component getListCellRendererComponent(JList<? extends PaletteRow> list, PaletteRow row,
                                                                 int index, boolean selected, boolean cellHasFocus) {
            this.selected = selected;
            icon.setIcon(row.icon());
            title.setFont(monospace ? monoTitle : uiTitle);
            title.setText(row.title());
            detail.setText(row.detail() == null ? "" : row.detail());
            tag.setText(row.tag() == null ? "" : row.tag());
            badge.setText((macOs ? "⌘" : "Ctrl+") + (index + 1));
            badge.setVisible(index < 5);
            title.setForeground(selected ? selectionForeground : row.enabled() ? foreground : muted);
            detail.setForeground(selected ? selectionForeground : muted);
            tag.setForeground(selected ? selectionForeground : muted);
            badge.selected(selected);
            return this;
        }

        @Override public void doLayout() {
            int side = UIScale.scale(12), iconWidth = UIScale.scale(20), gap = UIScale.scale(10);
            int titleStart = side + iconWidth + gap;
            int rowHeight = getHeight();
            icon.setBounds(side, 0, iconWidth, rowHeight);
            int titleEnd = getWidth() - side;
            if (badge.isVisible()) {
                int badgeWidth = Math.min(Math.max(0, getWidth() - titleStart), badge.getPreferredSize().width + UIScale.scale(12));
                int badgeX = Math.max(titleStart, getWidth() - side - badgeWidth);
                badge.setBounds(badgeX, (rowHeight - UIScale.scale(22)) / 2, badgeWidth, UIScale.scale(22));
                titleEnd = Math.max(titleStart, badgeX - gap);
            } else badge.setBounds(0, 0, 0, 0);
            int available = titleEnd - titleStart;
            int titlePreferred = Math.max(title.getPreferredSize().width, detail.getText().isEmpty() ? 0 : detail.getPreferredSize().width);
            int tagWidth = tag.getText().isEmpty() ? 0 : tag.getPreferredSize().width;
            boolean showTag = tagWidth > 0 && titlePreferred + UIScale.scale(12) + tagWidth <= available;
            tag.setVisible(showTag);
            int titleWidth = available;
            if (showTag) {
                int tagX = titleEnd - tagWidth;
                tag.setBounds(tagX, 0, tagWidth, rowHeight);
                titleWidth = Math.max(0, tagX - UIScale.scale(12) - titleStart);
            } else tag.setBounds(0, 0, 0, 0);
            boolean hasDetail = !detail.getText().isEmpty();
            detail.setVisible(hasDetail);
            if (hasDetail) {
                title.setBounds(titleStart, UIScale.scale(3), Math.max(0, titleWidth), UIScale.scale(19));
                detail.setBounds(titleStart, UIScale.scale(21), Math.max(0, titleWidth), UIScale.scale(16));
            } else {
                title.setBounds(titleStart, 0, Math.max(0, titleWidth), rowHeight);
                detail.setBounds(0, 0, 0, 0);
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
                    int insetX = UIScale.scale(4), insetY = UIScale.scale(2), arc = UIScale.scale(12);
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
}
