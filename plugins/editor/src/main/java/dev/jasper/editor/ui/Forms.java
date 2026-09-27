package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Small form helpers shared by the Editor's dialogs. */
final class Forms {
    private Forms() { }

    /** Makes the button the root pane's default while the content shows, and releases it when the content goes. */
    static void defaultButton(JComponent content, JButton button) {
        content.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
            JRootPane root = SwingUtilities.getRootPane(content);
            if (root == null) return;
            if (content.isShowing()) root.setDefaultButton(button);
            else if (root.getDefaultButton() == button) root.setDefaultButton(null);
        });
    }

    /** A question's content: its message and one button per choice, the first the default. */
    static JPanel question(Question question, Consumer<String> answer) {
        var message = new JTextArea(question.message());
        message.setEditable(false);
        message.setFocusable(false);
        message.setOpaque(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setColumns(36);
        message.setFont(UIManager.getFont("Label.font"));
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton first = null;
        for (String choice : question.choices()) {
            var button = new JButton(choice);
            button.addActionListener(event -> answer.accept(choice));
            buttons.add(button);
            if (first == null) first = button;
        }
        var content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        content.add(message, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        defaultButton(content, first);
        return content;
    }

    /** "Saving 3 files…" over an indeterminate bar, with Cancel. */
    static JPanel progress(String message, JButton cancel) {
        var label = new JLabel(message);
        label.putClientProperty("html.disable", true);
        var bar = new JProgressBar();
        bar.setIndeterminate(true);
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(cancel);
        var content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        content.add(label, BorderLayout.NORTH);
        content.add(bar, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        return content;
    }

    /** Puts a key binding on a component: {@code stroke} runs {@code action} under {@code condition}. */
    static void bind(JComponent component, int condition, KeyStroke stroke, String name, Runnable action) {
        component.getInputMap(condition).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }
}
