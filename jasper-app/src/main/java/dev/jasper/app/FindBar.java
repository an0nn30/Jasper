package dev.jasper.app;

import dev.jasper.terminal.search.SearchQuery;

import dev.jasper.terminal.search.FindResult;
import dev.jasper.terminal.view.TerminalView;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A pane-local, debounced search UI. Matching and generation checks belong to TerminalView. */
final class FindBar extends JPanel {
    private final TerminalView view;
    private final JTextField query = new JTextField(18);
    private final JToggleButton regex = new JToggleButton("Regex");
    private final JToggleButton caseSensitive = new JToggleButton("Case");
    private final JLabel count = new JLabel("0 / 0");
    private final Timer debounce;
    private FindResult result = new FindResult(0, 0, null);
    private boolean disposed;
    private boolean searching;
    private boolean dirty;
    private long generation;
    private long pendingNavigation;

    FindBar(TerminalView view) {
        super(new FlowLayout(FlowLayout.LEADING, 4, 3));
        this.view = view;
        debounce = new Timer(180, event -> search());
        debounce.setRepeats(false);
        query.getAccessibleContext().setAccessibleName("Find in terminal");
        query.setToolTipText("Search each terminal row; matches do not span wrapped rows");
        add(new JLabel("Find:")); add(query);
        add(button("Previous", this::previous)); add(button("Next", this::next));
        add(caseSensitive); add(regex); add(count); add(button("Close", this::close));
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { schedule(); }
            public void removeUpdate(DocumentEvent event) { schedule(); }
            public void changedUpdate(DocumentEvent event) { schedule(); }
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
            if (result.error() == null) showResult(found);
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) refreshShowing();
        });
        setVisible(false);
    }

    private static JButton button(String label, Runnable task) {
        JButton button = new JButton(label);
        button.addActionListener(event -> task.run());
        return button;
    }

    private static void bind(JComponent component, KeyStroke stroke, String name, Runnable task) {
        component.getInputMap().put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent event) { task.run(); }
        });
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

    private void schedule() {
        if (isVisible() && !disposed) {
            // Cancel old matching immediately, including during the debounce interval.
            invalidateSearch(); dirty = true;
            view.clearFind(); showResult(new FindResult(0, 0, null));
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
            showResult(navigated);
        });
    }

    void next() { navigate(1); }
    void previous() { navigate(-1); }

    private void navigate(int direction) {
        if (disposed) return;
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
        count.setText(found.error() == null ? found.current() + " / " + found.count() : "Invalid regex");
        count.setToolTipText(found.error());
        count.getAccessibleContext().setAccessibleDescription(found.error());
    }

    void close() {
        invalidateSearch(); dirty = true; view.clearFind(); showResult(new FindResult(0, 0, null));
        setVisible(false); view.requestFocusInWindow();
    }

    void dispose() { close(); disposed = true; view.setFindResultListener(null); }
    JTextField queryField() { return query; }
    JToggleButton regexButton() { return regex; }
    FindResult result() { return result; }
}
