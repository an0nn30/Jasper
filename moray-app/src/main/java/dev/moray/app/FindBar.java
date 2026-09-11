package dev.moray.app;

import dev.moray.terminal.FindResult;
import dev.moray.terminal.TerminalView;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
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
        view.setFindResultListener(this::showResult);
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

    void open() {
        if (disposed) return;
        setVisible(true); query.requestFocusInWindow(); query.selectAll(); schedule();
    }

    private void schedule() {
        if (isVisible() && !disposed) {
            // Cancel old matching immediately, including during the debounce interval.
            view.clearFind(); showResult(new FindResult(0, 0, null)); debounce.restart();
        }
    }

    private void search() {
        if (!disposed && isVisible()) view.findAsync(query.getText(), regex.isSelected(),
            caseSensitive.isSelected(), found -> {
                if (!disposed && isVisible()) showResult(found);
            });
    }

    void next() {
        if (debounce.isRunning()) { debounce.stop(); search(); }
        else showResult(view.findNext());
    }

    void previous() {
        if (debounce.isRunning()) { debounce.stop(); search(); }
        else showResult(view.findPrevious());
    }

    private void showResult(FindResult found) {
        result = found;
        count.setText(found.error() == null ? found.current() + " / " + found.count() : "Invalid regex");
        count.setToolTipText(found.error());
        count.getAccessibleContext().setAccessibleDescription(found.error());
    }

    void close() {
        debounce.stop(); view.clearFind(); showResult(new FindResult(0, 0, null));
        setVisible(false); view.requestFocusInWindow();
    }

    void dispose() { close(); disposed = true; view.setFindResultListener(null); }
    JTextField queryField() { return query; }
    JToggleButton regexButton() { return regex; }
    FindResult result() { return result; }
}
