package dev.jasper.app.palette;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
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

/**
 * Jasper's Search Everywhere: a tab row (All, then each scope), a full-width search field, a one-line
 * result list with section headers, and a hint bar with the selected row's detail and its scope's other
 * verbs. Every colour comes from the installed theme ({@code SearchEverywhere.*}, {@code List.*},
 * {@code Popup.borderColor}); a key a theme lacks falls back to one FlatLaf always defines. The host owns
 * placement, focus, tab switching and key routing.
 */
public final class CommandPalette extends JPanel {
    static final int WIDTH = 680;
    static final int TAB_HEIGHT = 30;
    static final int FIELD_HEIGHT = 40;
    static final int ROW_HEIGHT = 24;
    static final int HEADER_HEIGHT = 22;
    static final int HINT_HEIGHT = 26;
    static final int STEP_ROW_HEIGHT = 36;
    static final int MAX_VISIBLE_LINES = 15;

    /** A tab above the search field: All, or one scope. */
    public record Tab(String id, String label, Icon icon, String tooltip) {
        public Tab { Objects.requireNonNull(id); Objects.requireNonNull(label); }
    }

    private final boolean macOs;
    private final ObjIntConsumer<PaletteEntry> execute;
    private final Consumer<String> tabSelected;
    private final JPanel tabStrip = new FixedHeightPanel(TAB_HEIGHT);
    private final List<TabLabel> tabLabels = new ArrayList<>();
    private final JPanel fieldRow = new FixedHeightPanel(FIELD_HEIGHT);
    private final JTextField query = new JTextField();
    private final DefaultListModel<PaletteEntry> model = new DefaultListModel<>();
    private final JList<PaletteEntry> list = new JList<>(model);
    private final JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    private final JLabel empty = new JLabel("", SwingConstants.CENTER);
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JPanel stepPanel = new JPanel();
    private final JLabel stepTitle = new JLabel();
    private final List<JTextField> stepFields = new ArrayList<>();
    private final List<String> stepNames = new ArrayList<>();
    private final List<JLabel> stepLabels = new ArrayList<>();
    private final JLabel stepError = new JLabel();
    private JPanel errorRow;
    private final JPanel hintBar = new FixedHeightPanel(HINT_HEIGHT);
    private final JLabel hint = new JLabel();
    private final JPanel hintActions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
    private final List<Integer> hintVerbs = new ArrayList<>();
    private String listCard = "empty";
    private int stepFocus = -1;
    private int hover = -1;
    private boolean composing;
    private Color tabSelectedBackground, tabSelectedForeground, foreground, infoForeground, separatorColor,
        separatorForeground, selectionBackground, selectionForeground, hoverBackground, advertiserForeground, linkColor;
    private Font font, smallFont, monoFont;

    CommandPalette(boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteEntry> execute,
                   Consumer<String> tabSelected) {
        super(new BorderLayout());
        this.macOs = macOs;
        this.execute = Objects.requireNonNull(execute);
        this.tabSelected = Objects.requireNonNull(tabSelected);
        Objects.requireNonNull(queryChanged);
        setOpaque(true);

        tabStrip.setLayout(new FlowLayout(FlowLayout.LEADING, 0, 0));
        query.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search everywhere");
        query.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
            private void changed() { queryChanged.accept(query.getText()); }
        });
        query.addInputMethodListener(new InputMethodListener() {
            @Override public void inputMethodTextChanged(InputMethodEvent event) { composing = uncommittedCharacters(event) > 0; }
            @Override public void caretPositionChanged(InputMethodEvent event) {}
        });
        fieldRow.setLayout(new BorderLayout());
        fieldRow.add(query, BorderLayout.CENTER);
        var top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(tabStrip);
        top.add(fieldRow);
        add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setCellRenderer(new EntryRenderer());
        list.getAccessibleContext().setAccessibleDescription("0 results");
        list.addListSelectionListener(event -> updateHint());
        var mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                int index = entryAt(event.getPoint());
                if (index < 0) return;
                list.setSelectedIndex(index);
                execute.accept(model.get(index), 0);
            }
            @Override public void mouseMoved(MouseEvent event) { setHover(entryAt(event.getPoint())); }
            @Override public void mouseExited(MouseEvent event) { setHover(-1); }
        };
        list.addMouseListener(mouse);
        list.addMouseMotionListener(mouse);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        empty.putClientProperty("html.disable", Boolean.TRUE);
        stepPanel.setLayout(new BoxLayout(stepPanel, BoxLayout.Y_AXIS));
        for (JLabel label : List.of(stepTitle, stepError, hint)) label.putClientProperty("html.disable", Boolean.TRUE);
        cards.add(scroll, "results");
        cards.add(empty, "empty");
        cards.add(stepPanel, "step");
        cardLayout.show(cards, listCard);
        add(cards, BorderLayout.CENTER);

        hintActions.setOpaque(false);
        hintBar.setLayout(new BorderLayout());
        hintBar.add(hint, BorderLayout.CENTER);
        hintBar.add(hintActions, BorderLayout.EAST);
        add(hintBar, BorderLayout.SOUTH);
        setTabs(List.of(new Tab(PaletteScope.ALL_ID, "All", null, null)), PaletteScope.ALL_ID);
        refreshTheme();
    }

    public JTextField queryField() { return query; }
    JList<PaletteEntry> entryList() { return list; }
    JPanel tabStrip() { return tabStrip; }
    JPanel hintBar() { return hintBar; }
    JLabel stepTitle() { return stepTitle; }

    /** Shows {@code tabs} in order and marks {@code selectedId}; the field and list are named after it. */
    void setTabs(List<Tab> tabs, String selectedId) {
        tabStrip.removeAll();
        tabLabels.clear();
        String name = "All";
        for (Tab tab : tabs) {
            var label = new TabLabel(tab, tab.id().equals(selectedId));
            if (label.selected()) name = tab.label();
            tabLabels.add(label);
            tabStrip.add(label);
        }
        query.getAccessibleContext().setAccessibleName("Search " + name.toLowerCase(Locale.ROOT));
        list.getAccessibleContext().setAccessibleName(name);
        applyTabColors();
        tabStrip.revalidate();
        tabStrip.repaint();
    }

    List<String> tabIds() { return tabLabels.stream().map(TabLabel::id).toList(); }
    String selectedTabId() { return tabLabels.stream().filter(TabLabel::selected).map(TabLabel::id).findFirst().orElse(null); }
    /** As a left click on that tab; an unknown id does nothing. */
    void clickTab(String id) { if (tabIds().contains(id)) tabSelected.accept(id); }
    void setPlaceholder(String text) { query.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, text); }

    /** Replaces the list, selecting {@code selectionKey} when present and otherwise the first selectable entry. */
    void setEntries(List<PaletteEntry> entries, String selectionKey, String emptyText) {
        long rows = entries.stream().filter(entry -> entry instanceof PaletteEntry.Item).count();
        if (rows > PaletteResults.MAX_ROWS) throw new IllegalArgumentException("Too many palette results");
        int selected = -1, first = -1;
        model.clear();
        hover = -1;
        for (int i = 0; i < entries.size(); i++) {
            PaletteEntry entry = entries.get(i);
            model.addElement(entry);
            if (entry.selectable() && first < 0) first = i;
            if (selectionKey != null && selectionKey.equals(entry.key())) selected = i;
        }
        if (selected < 0) selected = first;
        empty.setText(emptyText == null ? "" : emptyText);
        if (selected < 0) list.clearSelection(); else list.setSelectedIndex(selected);
        listCard = first < 0 ? "empty" : "results";
        if (!stepShowing()) cardLayout.show(cards, listCard);
        list.getAccessibleContext().setAccessibleDescription(rows + " results");
        updateHint();
        revalidate();
        if (selected >= 0) {
            scroll.doLayout();
            scroll.getViewport().doLayout();
            reveal(selected);
        }
        repaint();
    }

    PaletteEntry selectedEntry() { return list.getSelectedValue(); }

    String selectedKey() {
        PaletteEntry entry = list.getSelectedValue();
        return entry == null ? null : entry.key();
    }

    /** Selects the first row with {@code rowId}, whichever scope it came from. */
    void selectRow(String rowId) {
        for (int i = 0; i < model.size(); i++)
            if (model.get(i) instanceof PaletteEntry.Item item && item.row().id().equals(rowId)) {
                list.setSelectedIndex(i);
                reveal(i);
                return;
            }
    }

    /** Moves {@code delta} selectable entries, skipping headers and stopping at either end. */
    void selectRelative(int delta) {
        int index = list.getSelectedIndex();
        if (index < 0 || delta == 0) return;
        int direction = Integer.signum(delta), remaining = Math.abs(delta), target = index;
        for (int i = index + direction; remaining > 0 && i >= 0 && i < model.size(); i += direction)
            if (model.get(i).selectable()) { target = i; remaining--; }
        list.setSelectedIndex(target);
        reveal(target);
    }

    void executeSelected() { executeSelected(0); }

    void executeSelected(int verb) {
        PaletteEntry entry = list.getSelectedValue();
        if (entry != null) execute.accept(entry, verb);
    }

    boolean composing() { return composing; }
    int itemHeight() { return UIScale.scale(ROW_HEIGHT); }
    String hintText() { return hint.getText(); }

    List<String> hintActionTexts() {
        var texts = new ArrayList<String>();
        for (Component action : hintActions.getComponents()) texts.add(((JLabel) action).getText());
        return texts;
    }

    /** As a click on the {@code index}th hint action. */
    void clickHintAction(int index) { executeSelected(hintVerbs.get(index)); }

    /** Shows a form instead of the list; the tabs and query stay and the hint bar hides. The first field takes focus. */
    void showStep(String title, List<PaletteStep.Field> fields) {
        stepPanel.removeAll(); stepFields.clear(); stepNames.clear(); stepLabels.clear();
        stepTitle.setText(title);
        stepPanel.add(row(stepTitle));
        for (PaletteStep.Field field : fields) {
            var label = new JLabel(field.label());
            label.putClientProperty("html.disable", Boolean.TRUE);
            label.setPreferredSize(new Dimension(UIScale.scale(140), 0));
            var text = new JTextField(field.prefill());
            text.getAccessibleContext().setAccessibleName(field.label());
            var line = new FixedHeightPanel(STEP_ROW_HEIGHT);
            line.setLayout(new BorderLayout(UIScale.scale(10), 0));
            line.setOpaque(false);
            line.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(12), UIScale.scale(4), UIScale.scale(12)));
            line.add(label, BorderLayout.LINE_START);
            line.add(text, BorderLayout.CENTER);
            stepPanel.add(line);
            stepFields.add(text); stepNames.add(field.name()); stepLabels.add(label);
        }
        stepError.setText("");
        stepError.setVisible(false);
        errorRow = row(stepError);
        errorRow.setVisible(false);
        stepPanel.add(errorRow);
        applyStepColors();
        cardLayout.show(cards, "step");
        hintBar.setVisible(false);
        stepFocus = 0;
        stepFields.getFirst().requestFocusInWindow();
        stepFields.getFirst().selectAll();
        revalidate(); repaint();
    }

    void hideStep() {
        stepPanel.removeAll(); stepFields.clear(); stepNames.clear(); stepLabels.clear();
        errorRow = null;
        stepFocus = -1;
        hintBar.setVisible(true);
        cardLayout.show(cards, listCard);
        revalidate(); repaint();
    }

    boolean stepShowing() { return stepFocus >= 0; }
    List<JTextField> stepFields() { return List.copyOf(stepFields); }
    int stepFocusIndex() { return stepFocus; }
    JLabel stepError() { return stepError; }

    /** Moves focus to the next (or previous) field, wrapping. */
    void focusStepField(int delta) {
        if (stepFields.isEmpty()) return;
        stepFocus = Math.floorMod(stepFocus + delta, stepFields.size());
        JTextField field = stepFields.get(stepFocus);
        field.requestFocusInWindow();
        field.selectAll();
    }

    Map<String, String> stepValues() {
        var values = new LinkedHashMap<String, String>();
        for (int i = 0; i < stepFields.size(); i++) values.put(stepNames.get(i), stepFields.get(i).getText());
        return values;
    }

    void setStepError(String message) {
        stepError.setText(message == null ? "" : message);
        stepError.setVisible(message != null);
        if (errorRow != null) errorRow.setVisible(message != null);
        revalidate(); repaint();
    }

    void refreshTheme() {
        Color headerBackground = color("SearchEverywhere.Header.background", "Panel.background");
        tabSelectedBackground = color("SearchEverywhere.Tab.selectedBackground", "List.selectionInactiveBackground");
        tabSelectedForeground = color("SearchEverywhere.Tab.selectedForeground", "Label.foreground");
        foreground = color("List.foreground", "Label.foreground");
        infoForeground = color("SearchEverywhere.SearchField.infoForeground", "Label.disabledForeground");
        separatorColor = color("SearchEverywhere.List.separatorColor", "Separator.foreground");
        separatorForeground = color("SearchEverywhere.List.separatorForeground", "Label.disabledForeground");
        Color listBackground = color("List.background", "Panel.background");
        selectionBackground = UIManager.getColor("List.selectionBackground");
        selectionForeground = UIManager.getColor("List.selectionForeground");
        hoverBackground = UIManager.getColor("List.hoverBackground");
        Color advertiserBackground = color("SearchEverywhere.Advertiser.background", "Panel.background");
        advertiserForeground = color("SearchEverywhere.Advertiser.foreground", "Label.disabledForeground");
        linkColor = UIManager.getColor("Component.linkColor");
        Color border = color("Popup.borderColor", "PopupMenu.borderColor");
        Color fieldBackground = color("SearchEverywhere.SearchField.background", "TextField.background");
        Color fieldBorder = color("SearchEverywhere.SearchField.borderColor", "Component.borderColor");
        font = UIManager.getFont("Label.font");
        smallFont = font.deriveFont(Font.PLAIN, font.getSize2D() - UIScale.scale(1f));
        monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 13).deriveFont(font.getSize2D());

        setBackground(listBackground);
        setBorder(BorderFactory.createLineBorder(border, UIScale.scale(1)));
        tabStrip.setBackground(headerBackground);
        fieldRow.setBackground(fieldBackground);
        fieldRow.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(6), UIScale.scale(8), UIScale.scale(6), UIScale.scale(8)));
        query.putClientProperty(FlatClientProperties.STYLE, "background: " + hex(fieldBackground)
            + "; borderColor: " + hex(fieldBorder) + "; placeholderForeground: " + hex(infoForeground));
        query.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, AppIcons.icon("search"));
        query.setFont(font);
        list.setBackground(listBackground);
        list.setForeground(foreground);
        list.setSelectionBackground(selectionBackground);
        list.setSelectionForeground(selectionForeground);
        list.setFont(font);
        scroll.getViewport().setBackground(listBackground);
        cards.setBackground(listBackground);
        stepPanel.setBackground(listBackground);
        empty.setForeground(infoForeground);
        empty.setFont(font);
        hintBar.setBackground(advertiserBackground);
        hintBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(UIScale.scale(1), 0, 0, 0, separatorColor),
            BorderFactory.createEmptyBorder(0, UIScale.scale(12), 0, UIScale.scale(12))));
        hint.setForeground(advertiserForeground);
        hint.setFont(smallFont);
        applyTabColors();
        applyHintColors();
        applyStepColors();
        revalidate(); repaint();
    }

    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        int height = TAB_HEIGHT + FIELD_HEIGHT + (stepShowing()
            ? HEADER_HEIGHT + stepFields.size() * STEP_ROW_HEIGHT + (stepError.isVisible() ? HEADER_HEIGHT : 0)
            : listHeight() + HINT_HEIGHT);
        return new Dimension(UIScale.scale(WIDTH), UIScale.scale(height) + insets.top + insets.bottom);
    }

    private int listHeight() {
        if ("empty".equals(listCard)) return ROW_HEIGHT;
        int height = 0;
        for (int i = 0; i < Math.min(model.size(), MAX_VISIBLE_LINES); i++)
            height += model.get(i) instanceof PaletteEntry.Header ? HEADER_HEIGHT : ROW_HEIGHT;
        return height;
    }

    private void reveal(int index) {
        if (index > 0 && !model.get(index - 1).selectable()) list.ensureIndexIsVisible(index - 1);
        list.ensureIndexIsVisible(index);
    }

    private int entryAt(Point point) {
        int index = list.locationToIndex(point);
        if (index < 0) return -1;
        Rectangle bounds = list.getCellBounds(index, index);
        return bounds != null && bounds.contains(point) && model.get(index).selectable() ? index : -1;
    }

    private void setHover(int index) {
        if (index == hover) return;
        hover = index;
        list.repaint();
    }

    private void updateHint() {
        hintActions.removeAll();
        hintVerbs.clear();
        String text = "";
        PaletteEntry entry = list.getSelectedValue();
        if (entry instanceof PaletteEntry.Item item) {
            PaletteRow row = item.row();
            text = row.detail() != null ? row.detail() : row.tag() != null ? row.tag() : "";
            List<PaletteVerb> verbs = item.scope().verbs();
            for (int verb = 1; verb < Math.min(3, verbs.size()); verb++) {
                int chosen = verb;
                var action = new SizedLabel(verbs.get(verb).label() + " " + keys(verb), HINT_HEIGHT - 1);
                action.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(14), 0, 0));
                action.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                action.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) executeSelected(chosen); }
                });
                hintActions.add(action);
                hintVerbs.add(verb);
            }
        } else if (entry instanceof PaletteEntry.More more) {
            text = "Show every match in " + more.scope().label();
        }
        hint.setText(text);
        applyHintColors();
        hintActions.revalidate();
        hintActions.repaint();
    }

    private String keys(int verb) {
        return (macOs ? new String[]{"⏎", "⌘⏎", "⇧⏎"} : new String[]{"Enter", "Ctrl+Enter", "Shift+Enter"})[verb];
    }

    private void applyTabColors() {
        for (TabLabel label : tabLabels) {
            label.setForeground(label.selected() ? tabSelectedForeground : foreground);
            if (font != null) label.setFont(font);
        }
    }

    private void applyHintColors() {
        for (Component action : hintActions.getComponents()) {
            action.setForeground(linkColor);
            if (smallFont != null) action.setFont(smallFont);
        }
    }

    private void applyStepColors() {
        if (foreground == null) return;
        stepTitle.setForeground(separatorForeground);
        stepTitle.setFont(smallFont);
        for (JLabel label : stepLabels) { label.setForeground(infoForeground); label.setFont(smallFont); }
        stepError.setForeground(UIManager.getColor("Actions.Red"));
        stepError.setFont(smallFont);
    }

    private static JPanel row(JLabel label) {
        var row = new FixedHeightPanel(HEADER_HEIGHT);
        row.setLayout(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(12), 0, UIScale.scale(12)));
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private static Color color(String key, String fallbackKey) {
        Color value = UIManager.getColor(key);
        return value != null ? value : UIManager.getColor(fallbackKey);
    }

    private static String hex(Color color) { return String.format("#%06x", color.getRGB() & 0xffffff); }

    private static int uncommittedCharacters(InputMethodEvent event) {
        AttributedCharacterIterator text = event.getText();
        if (text == null) return 0;
        int length = text.getEndIndex() - text.getBeginIndex();
        return Math.max(0, length - event.getCommittedCharacterCount());
    }

    private static class FixedHeightPanel extends JPanel {
        private final int logicalHeight;

        FixedHeightPanel(int logicalHeight) { this.logicalHeight = logicalHeight; }

        @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(logicalHeight)); }
        @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(logicalHeight)); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, UIScale.scale(logicalHeight)); }
    }

    /** A label whose height is fixed in logical pixels, so tabs and hints stay aligned under any font. */
    private static class SizedLabel extends JLabel {
        private final int logicalHeight;

        SizedLabel(String text, int logicalHeight) {
            super(text);
            this.logicalHeight = logicalHeight;
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(logicalHeight)); }
    }

    private final class TabLabel extends SizedLabel {
        private final String id;
        private final boolean selected;

        TabLabel(Tab tab, boolean selected) {
            super(tab.label(), TAB_HEIGHT);
            this.id = tab.id();
            this.selected = selected;
            setIcon(tab.icon());
            setIconTextGap(UIScale.scale(4));
            setToolTipText(tab.tooltip());
            setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(10), 0, UIScale.scale(10)));
            getAccessibleContext().setAccessibleName(tab.label() + " tab");
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) tabSelected.accept(id); }
            });
        }

        String id() { return id; }
        boolean selected() { return selected; }

        @Override protected void paintComponent(Graphics graphics) {
            if (selected && tabSelectedBackground != null) {
                graphics.setColor(tabSelectedBackground);
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(graphics);
        }
    }

    private final class EntryRenderer extends JPanel implements ListCellRenderer<PaletteEntry> {
        private final JLabel icon = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel tag = new JLabel();
        private PaletteEntry entry;
        private boolean selected, hovered, first;

        EntryRenderer() {
            setLayout(null);
            setOpaque(false);
            for (JLabel label : List.of(icon, title, detail, tag)) {
                label.setOpaque(false);
                label.putClientProperty("html.disable", Boolean.TRUE);
                add(label);
            }
            icon.setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override public Component getListCellRendererComponent(JList<? extends PaletteEntry> source, PaletteEntry value,
                                                                 int index, boolean isSelected, boolean cellHasFocus) {
            entry = value;
            selected = isSelected && value.selectable();
            hovered = index == hover && !selected && value.selectable();
            first = index == 0;
            icon.setIcon(null);
            detail.setText("");
            tag.setText("");
            switch (value) {
                case PaletteEntry.Header header -> {
                    title.setText(header.label());
                    title.setFont(smallFont);
                    title.setForeground(separatorForeground);
                }
                case PaletteEntry.Item item -> {
                    PaletteRow row = item.row();
                    icon.setIcon(row.icon());
                    title.setText(row.title());
                    title.setFont(item.scope().monospaceRows() ? monoFont : font);
                    title.setForeground(selected ? selectionForeground : row.enabled() ? foreground : infoForeground);
                    detail.setText(row.detail() == null ? "" : row.detail());
                    detail.setFont(font);
                    detail.setForeground(selected ? selectionForeground : infoForeground);
                    tag.setText(row.tag() == null ? "" : row.tag());
                    tag.setFont(smallFont);
                    tag.setForeground(selected ? selectionForeground : infoForeground);
                }
                case PaletteEntry.More more -> {
                    title.setText("More in " + more.scope().label() + "…");
                    title.setFont(font);
                    title.setForeground(selected ? selectionForeground : infoForeground);
                }
            }
            return this;
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(0, UIScale.scale(entry instanceof PaletteEntry.Header ? HEADER_HEIGHT : ROW_HEIGHT));
        }

        @Override public void doLayout() {
            int side = UIScale.scale(8), gap = UIScale.scale(8), iconSize = UIScale.scale(16), height = getHeight();
            if (entry instanceof PaletteEntry.Header) {
                title.setBounds(side, 0, Math.max(0, getWidth() - 2 * side), height);
                for (JLabel unused : List.of(icon, detail, tag)) unused.setBounds(0, 0, 0, 0);
                return;
            }
            icon.setBounds(side, (height - iconSize) / 2, iconSize, iconSize);
            int start = side + iconSize + gap, end = getWidth() - side;
            int titleWidth = title.getPreferredSize().width;
            int tagWidth = tag.getText().isEmpty() ? 0 : tag.getPreferredSize().width;
            int detailWidth = detail.getText().isEmpty() ? 0 : detail.getPreferredSize().width;
            // When space runs out the tag goes first; then the detail, and finally the title, are cut short.
            boolean showTag = tagWidth > 0 && start + titleWidth + gap + tagWidth <= end;
            int textEnd = showTag ? end - tagWidth - gap : end;
            tag.setBounds(showTag ? end - tagWidth : 0, 0, showTag ? tagWidth : 0, height);
            int shownTitle = Math.max(0, Math.min(titleWidth, textEnd - start));
            title.setBounds(start, 0, shownTitle, height);
            int detailStart = start + shownTitle + gap;
            detail.setBounds(detailStart, 0, Math.max(0, Math.min(detailWidth, textEnd - detailStart)), height);
        }

        @Override protected void paintComponent(Graphics graphics) {
            // CellRendererPane assigns the bounds just before painting; lay the labels out at that width.
            doLayout();
            Color fill = selected ? selectionBackground : hovered ? hoverBackground : null;
            if (fill != null) {
                graphics.setColor(fill);
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
            if (entry instanceof PaletteEntry.Header && !first) {
                graphics.setColor(separatorColor);
                graphics.fillRect(0, 0, getWidth(), UIScale.scale(1));
            }
            super.paintComponent(graphics);
        }
    }
}
