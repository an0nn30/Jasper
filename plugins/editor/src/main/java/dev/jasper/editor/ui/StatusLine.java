package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.syntax.Languages;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/** {@code Ln 12, Col 4 · Python · UTF-8 · LF · Spaces: 4 · Wrap}; the language and Wrap are clickable (spec 2). */
public final class StatusLine extends JPanel {
    private final JLabel notice = label(), position = label(), encoding = label(), separator = label(), indent = label();
    private final JButton language = new JButton();
    private final JToggleButton wrap = new JToggleButton("Wrap");

    /**
     * @param chooseLanguage opens the language picker
     * @param toggleWrap toggles soft wrap for the current tab
     */
    public StatusLine(Runnable chooseLanguage, Runnable toggleWrap) {
        super(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        language.addActionListener(event -> chooseLanguage.run());
        wrap.addActionListener(event -> toggleWrap.run());
        for (AbstractButton button : List.of(language, wrap)) {
            button.putClientProperty("JButton.buttonType", "borderless");
            button.setFocusable(false);
        }
        add(notice);
        add(position);
        add(language);
        add(encoding);
        add(separator);
        add(indent);
        add(wrap);
        display(Optional.empty(), 4);
    }

    /**
     * @param tab the selected tab, or empty
     * @param tabSize the settings' tab size
     */
    public void display(Optional<EditorTab> tab, int tabSize) {
        boolean ready = tab.isPresent() && tab.get().session().state() == DocumentSession.State.READY;
        for (JComponent part : List.of(position, language, encoding, separator, indent, wrap)) part.setVisible(ready);
        if (tab.isEmpty()) { notice.setText(""); return; }
        DocumentSession session = tab.get().session();
        notice.setText(session.saving() ? "Saving…" : session.notice().orElse(""));
        if (!ready) return;
        RSyntaxTextArea area = tab.get().area();
        int line = area.getCaretLineNumber();
        int start;
        try { start = area.getLineStartOffset(line); }
        catch (BadLocationException impossible) { start = 0; }
        position.setText("Ln " + (line + 1) + ", Col " + (area.getCaretPosition() - start + 1));
        language.setText(Languages.name(session.style()));
        encoding.setText(session.format().label());
        String ending = session.format().separator().label();
        separator.setText(session.mixed() ? "Mixed → " + ending : ending);
        indent.setText((session.tabs() ? "Tab: " : "Spaces: ") + tabSize);
        wrap.setSelected(session.wrap());
    }

    String positionText() { return position.getText(); }
    String languageText() { return language.getText(); }
    String encodingText() { return encoding.getText(); }
    String separatorText() { return separator.getText(); }
    String indentText() { return indent.getText(); }
    String noticeText() { return notice.getText(); }
    boolean wrapSelected() { return wrap.isSelected(); }

    private static JLabel label() {
        var label = new JLabel();
        label.putClientProperty("html.disable", true);
        return label;
    }
}
