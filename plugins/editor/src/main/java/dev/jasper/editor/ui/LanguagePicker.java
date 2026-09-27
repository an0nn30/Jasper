package dev.jasper.editor.ui;

import dev.jasper.editor.syntax.Languages;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Every language by display name, with a filter field (spec 3.1); Enter or a double-click chooses. */
public final class LanguagePicker extends JPanel {
    private final List<Languages.Language> all = Languages.all();
    private final DefaultListModel<Languages.Language> shown = new DefaultListModel<>();
    private final JList<Languages.Language> list = new JList<>(shown);
    private final JTextField filter = new JTextField(24);
    private final Consumer<String> chosen;

    /**
     * @param current the tab's style, selected at first
     * @param chosen receives the chosen style
     */
    public LanguagePicker(String current, Consumer<String> chosen) {
        super(new BorderLayout(0, 8));
        this.chosen = chosen;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        filter.putClientProperty("JTextField.placeholderText", "Filter languages");
        list.setVisibleRowCount(14);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(owner, ((Languages.Language) value).name(), index, selected, focused);
                putClientProperty("html.disable", true);
                return this;
            }
        });
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { filter(filter.getText()); }
            @Override public void removeUpdate(DocumentEvent event) { filter(filter.getText()); }
            @Override public void changedUpdate(DocumentEvent event) { }
        });
        Forms.bind(filter, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose", this::chooseSelected);
        Forms.bind(filter, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down", () -> move(1));
        Forms.bind(filter, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up", () -> move(-1));
        Forms.bind(list, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose", this::chooseSelected);
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2) chooseSelected(); }
        });
        add(filter, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        filter("");
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).style().equals(current)) { list.setSelectedIndex(i); list.ensureIndexIsVisible(i); }
    }

    /** @param text shows the languages whose names contain it, ignoring case; selects the first */
    public void filter(String text) {
        String needle = text.strip().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Languages.Language language : all) if (language.name().toLowerCase(Locale.ROOT).contains(needle)) shown.addElement(language);
        if (!shown.isEmpty()) list.setSelectedIndex(0);
    }

    /** Chooses the selected language, if any. */
    public void chooseSelected() {
        Languages.Language language = list.getSelectedValue();
        if (language != null) chosen.accept(language.style());
    }

    List<String> visibleNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) names.add(shown.get(i).name());
        return names;
    }

    String selectedName() { return list.getSelectedValue() == null ? "" : list.getSelectedValue().name(); }

    private void move(int delta) {
        if (shown.isEmpty()) return;
        int index = Math.max(0, Math.min(shown.size() - 1, list.getSelectedIndex() + delta));
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }
}
