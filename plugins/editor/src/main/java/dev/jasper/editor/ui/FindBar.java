package dev.jasper.editor.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

/** Find and replace in the selected tab (spec 2), using RSyntaxTextArea's {@code SearchEngine}. Hidden until opened. */
public final class FindBar extends JPanel {
    private final Supplier<Optional<RSyntaxTextArea>> target;
    private final JTextField find = new JTextField(22);
    private final JTextField replace = new JTextField(22);
    private final JToggleButton matchCase = toggle("Aa", "Match case");
    private final JToggleButton wholeWord = toggle("W", "Whole word");
    private final JToggleButton regex = toggle(".*", "Regular expression");
    private final JLabel status = new JLabel();
    private final JPanel replaceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));

    /** @param target the selected tab's text area, if any */
    public FindBar(Supplier<Optional<RSyntaxTextArea>> target) {
        super(new BorderLayout());
        this.target = target;
        setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        find.putClientProperty("JTextField.placeholderText", "Find");
        replace.putClientProperty("JTextField.placeholderText", "Replace");
        status.putClientProperty("html.disable", true);
        var findRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        findRow.add(find);
        findRow.add(button("Previous", this::previous));
        findRow.add(button("Next", this::next));
        findRow.add(matchCase);
        findRow.add(wholeWord);
        findRow.add(regex);
        findRow.add(status);
        findRow.add(button("Close", this::close));
        replaceRow.add(replace);
        replaceRow.add(button("Replace", this::replaceOne));
        replaceRow.add(button("Replace All", this::replaceAll));
        add(findRow, BorderLayout.NORTH);
        add(replaceRow, BorderLayout.SOUTH);
        find.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { update(); }
            @Override public void removeUpdate(DocumentEvent event) { update(); }
            @Override public void changedUpdate(DocumentEvent event) { }
        });
        for (JToggleButton toggle : new JToggleButton[] {matchCase, wholeWord, regex}) toggle.addActionListener(event -> update());
        key(find, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "next", this::next);
        key(find, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "previous", this::previous);
        key(replace, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "replace", this::replaceOne);
        for (JTextField field : new JTextField[] {find, replace}) key(field, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close", this::close);
        setVisible(false);
    }

    /** @param withReplace whether the replace row shows; a one-line selection becomes the search text */
    public void open(boolean withReplace) {
        target.get().ifPresent(area -> {
            String selected = area.getSelectedText();
            if (selected != null && !selected.isEmpty() && !selected.contains("\n")) find.setText(selected);
        });
        replaceRow.setVisible(withReplace);
        setVisible(true);
        revalidate();
        find.requestFocusInWindow();
        find.selectAll();
        update();
    }

    /** Hides the bar, clears the highlights and returns focus to the text. */
    public void close() {
        setVisible(false);
        revalidate();
        target.get().ifPresent(area -> { area.clearMarkAllHighlights(); area.requestFocusInWindow(); });
    }

    public void next() { search(true); }
    public void previous() { search(false); }

    public void replaceOne() {
        target.get().ifPresent(area -> guarded(() -> {
            SearchContext context = context(true);
            context.setReplaceWith(replace.getText());
            if (SearchEngine.replace(area, context).wasFound()) update();
            else status.setText("No matches");
        }));
    }

    public void replaceAll() {
        target.get().ifPresent(area -> guarded(() -> {
            SearchContext context = context(true);
            context.setReplaceWith(replace.getText());
            status.setText("Replaced " + SearchEngine.replaceAll(area, context).getCount());
        }));
    }

    JTextField findField() { return find; }
    JTextField replaceField() { return replace; }
    JToggleButton matchCase() { return matchCase; }
    JToggleButton wholeWord() { return wholeWord; }
    JToggleButton regex() { return regex; }
    String status() { return status.getText(); }
    boolean replaceVisible() { return replaceRow.isVisible(); }

    private void update() {
        Optional<RSyntaxTextArea> area = target.get();
        if (area.isEmpty() || find.getText().isEmpty()) {
            area.ifPresent(RSyntaxTextArea::clearMarkAllHighlights);
            status.setText("");
            return;
        }
        guarded(() -> {
            int count = SearchEngine.markAll(area.get(), context(true)).getMarkedCount();
            status.setText(count == 0 ? "No matches" : count == 1 ? "1 match" : count + " matches");
        });
    }

    private void search(boolean forward) {
        if (find.getText().isEmpty()) return;
        target.get().ifPresent(area -> guarded(() -> {
            SearchResult result = SearchEngine.find(area, context(forward));
            if (!result.wasFound()) status.setText("No matches");
        }));
    }

    private SearchContext context(boolean forward) {
        // RSyntaxTextArea's SearchEngine swallows an invalid regex internally (returns no match
        // instead of throwing), so validate the pattern ourselves and let guarded() report it.
        if (regex.isSelected()) Pattern.compile(find.getText());
        var context = new SearchContext(find.getText());
        context.setMatchCase(matchCase.isSelected());
        context.setWholeWord(wholeWord.isSelected());
        context.setRegularExpression(regex.isSelected());
        context.setSearchForward(forward);
        context.setSearchWrap(true);
        context.setMarkAll(true);
        return context;
    }

    private void guarded(Runnable search) {
        try { search.run(); }
        catch (PatternSyntaxException bad) { status.setText("Invalid pattern"); }
    }

    private static JToggleButton toggle(String text, String tooltip) {
        var toggle = new JToggleButton(text);
        toggle.setToolTipText(tooltip);
        toggle.getAccessibleContext().setAccessibleName(tooltip);
        toggle.setFocusable(false);
        return toggle;
    }

    private static JButton button(String text, Runnable action) {
        var button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static void key(JComponent component, KeyStroke stroke, String name, Runnable action) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }
}
