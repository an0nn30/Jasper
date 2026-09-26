package dev.jasper.app.workspace;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.terminal.search.FindResult;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.view.TerminalView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A pane-local, debounced search UI. Matching and generation checks belong to TerminalView. */
final class FindBar extends JPanel {
    private static final int RECENT_LIMIT = 10;
    private final TerminalView view;
    private final JTextField query = new JTextField(18);
    private final JToggleButton regex = new JToggleButton();
    private final JToggleButton caseSensitive = new JToggleButton();
    private final JLabel count = new JLabel("");
    private final JButton clear = new JButton();
    private final Deque<String> recent = new ArrayDeque<>();
    private final Timer debounce;
    private FindResult result = new FindResult(0, 0, null);
    private boolean disposed;
    private boolean searching;
    private boolean dirty;
    private boolean missing;
    private long generation;
    private long pendingNavigation;

    FindBar(TerminalView view) {
        this.view = view;
        debounce = new Timer(180, event -> search());
        debounce.setRepeats(false);
        query.getAccessibleContext().setAccessibleName("Find in terminal");
        query.setToolTipText("Search each terminal row; matches do not span wrapped rows");
        build();
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { edited(); }
            public void removeUpdate(DocumentEvent event) { edited(); }
            public void changedUpdate(DocumentEvent event) { edited(); }
        });
        regex.addActionListener(event -> schedule());
        caseSensitive.addActionListener(event -> schedule());
        bind(query, KeyStroke.getKeyStroke("ENTER"), "next", this::next);
        bind(query, KeyStroke.getKeyStroke("shift ENTER"), "previous", this::previous);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        getActionMap().put("close", new AbstractAction() {
            public void actionPerformed(ActionEvent event) { close(); }
        });
        view.setFindResultListener(found -> {
            invalidateSearch(); dirty = true;
            if (result.error() == null) { miss(found.count() == 0 && !query.getText().isEmpty()); showResult(found); }
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) refreshShowing();
        });
        setVisible(false);
    }

    /** IntelliJ's find row: history and toggles inside the field, then count, arrows and a far-right close. */
    private void build() {
        setLayout(new BorderLayout(UIScale.scale(6), 0));
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(2), UIScale.scale(6), UIScale.scale(3), UIScale.scale(6)));
        JButton history = iconButton("findHistory", "Recent searches", "Recent searches", "searchWithHistory", null);
        history.addActionListener(event -> recentMenu().show(history, 0, history.getHeight()));
        configure(clear, "clearFind", "Clear search", "Clear search", "close");
        clear.addActionListener(event -> { query.setText(""); query.requestFocusInWindow(); });
        clear.setVisible(false);
        configure(caseSensitive, "caseSensitive", "Case sensitive", "Match case", "matchCase");
        configure(regex, "regex", "Regular expression", "Regular expression", "regex");
        var trailing = new JToolBar();
        trailing.setFloatable(false); trailing.setOpaque(false); trailing.setBorder(BorderFactory.createEmptyBorder());
        trailing.add(clear); trailing.addSeparator(); trailing.add(caseSensitive); trailing.add(regex);
        query.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, history);
        query.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, trailing);
        query.putClientProperty(FlatClientProperties.STYLE, "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0");
        count.setName("findCount");
        var controls = new JPanel(new FlowLayout(FlowLayout.LEADING, UIScale.scale(4), 0));
        controls.setOpaque(false);
        controls.add(count);
        controls.add(iconButton("previousMatch", "Previous", "Previous match (Shift+Enter)", "previousOccurence", this::previous));
        controls.add(iconButton("nextMatch", "Next", "Next match (Enter)", "nextOccurence", this::next));
        controls.add(Box.createHorizontalStrut(UIScale.scale(16)));
        controls.add(iconButton("closeFind", "Close", "Close (Escape)", "close", this::close));
        add(query, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
    }

    private static JButton iconButton(String name, String accessible, String tooltip, String artwork, Runnable task) {
        JButton button = new JButton();
        configure(button, name, accessible, tooltip, artwork);
        if (task != null) button.addActionListener(event -> task.run());
        return button;
    }

    private static void configure(AbstractButton button, String name, String accessible, String tooltip, String artwork) {
        button.setName(name);
        // AbstractButton normalizes a constructor-supplied null to "" but honors an explicit setText(null).
        button.setText(null);
        button.setIcon(AppIcons.chrome(artwork));
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(accessible);
        button.setFocusable(false);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    }

    private static void bind(JComponent component, KeyStroke stroke, String name, Runnable task) {
        component.getInputMap().put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent event) { task.run(); }
        });
    }

    @Override public void updateUI() {
        super.updateUI();
        // A theme change replaces UIResource colours; keep the no-match tint.
        if (query != null) miss(missing);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor("Jasper.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    @Override public void removeNotify() {
        dirty |= searching;
        cancelSearch(); // a temporary reparent cancels work, not the user's queued navigation
        super.removeNotify();
    }

    @Override public void addNotify() {
        super.addNotify();
        refreshShowing();
    }

    private void refreshShowing() {
        if (!isShowing()) {
            dirty |= searching;
            cancelSearch(); // retain the query, completed result and queued navigation across hidden tabs
        } else if (dirty && !disposed) {
            debounce.restart();
        }
    }

    void open() {
        if (disposed) return;
        setVisible(true); query.requestFocusInWindow(); query.selectAll(); schedule();
    }

    private void edited() {
        clear.setVisible(!query.getText().isEmpty());
        schedule();
    }

    private void schedule() {
        if (isVisible() && !disposed) {
            // Cancel old matching immediately, including during the debounce interval.
            invalidateSearch(); dirty = true;
            view.clearFind(); miss(false); showResult(new FindResult(0, 0, null));
            if (isShowing()) debounce.restart();
        }
    }

    private void search() {
        if (disposed || !isShowing()) return;
        searching = true; dirty = false;
        long request = generation;
        view.findAsync(new SearchQuery(query.getText(), regex.isSelected(), caseSensitive.isSelected()), found -> {
            if (disposed || !isShowing() || request != generation) return;
            searching = false;
            FindResult navigated = found;
            if (found.error() == null && found.count() > 0) {
                long steps = pendingNavigation % found.count();
                while (steps > 0) { navigated = view.findNext(); steps--; }
                while (steps < 0) { navigated = view.findPrevious(); steps++; }
            }
            pendingNavigation = 0;
            miss(navigated.error() != null || navigated.count() == 0 && !query.getText().isEmpty());
            showResult(navigated);
        });
    }

    void next() { navigate(1); }
    void previous() { navigate(-1); }

    private void navigate(int direction) {
        if (disposed) return;
        remember();
        if (dirty || searching) {
            pendingNavigation += direction;
            if (!searching) { debounce.stop(); search(); }
        } else if (result.error() == null) {
            showResult(direction > 0 ? view.findNext() : view.findPrevious());
        }
    }

    private void cancelSearch() {
        generation++; debounce.stop(); searching = false;
    }

    private void invalidateSearch() {
        cancelSearch(); pendingNavigation = 0;
    }

    private void showResult(FindResult found) {
        result = found;
        if (found.error() != null) count.setText("Invalid regex");
        else if (found.count() > 0) count.setText(found.current() + "/" + found.count());
        else count.setText(missing ? "0 results" : "");
        count.setToolTipText(found.error());
        count.getAccessibleContext().setAccessibleDescription(found.error());
    }

    /** IntelliJ tints the field when nothing matches or the pattern is invalid. */
    private void miss(boolean value) {
        missing = value;
        query.putClientProperty(FlatClientProperties.OUTLINE, value ? FlatClientProperties.OUTLINE_ERROR : null);
        // The tint is a plain Color so the field's own updateUI keeps it; the default stays a
        // UIResource so a theme switch restores the new theme's background.
        Color error = UIManager.getColor("Jasper.findErrorBackground");
        query.setBackground(value && error != null ? new Color(error.getRGB(), true) : UIManager.getColor("TextField.background"));
    }

    private void remember() {
        String text = query.getText();
        if (text.isBlank()) return;
        recent.remove(text); recent.addFirst(text);
        while (recent.size() > RECENT_LIMIT) recent.removeLast();
    }

    /** This pane's recent queries, newest first; held in memory only. */
    JPopupMenu recentMenu() {
        var menu = new JPopupMenu();
        if (recent.isEmpty()) {
            var none = new JMenuItem("No recent searches"); none.setEnabled(false); menu.add(none);
        }
        for (String text : recent) {
            var item = new JMenuItem(text);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { query.setText(text); query.requestFocusInWindow(); });
            menu.add(item);
        }
        return menu;
    }

    void close() {
        remember();
        invalidateSearch(); dirty = true; view.clearFind(); miss(false); showResult(new FindResult(0, 0, null));
        setVisible(false); view.requestFocusInWindow();
    }

    void dispose() { close(); disposed = true; view.setFindResultListener(null); }
    JTextField queryField() { return query; }
    JToggleButton regexButton() { return regex; }
    JToggleButton caseButton() { return caseSensitive; }
    JLabel countLabel() { return count; }
    boolean missing() { return missing; }
    FindResult result() { return result; }
}
