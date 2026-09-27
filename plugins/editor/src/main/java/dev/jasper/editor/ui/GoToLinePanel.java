package dev.jasper.editor.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Go to Line: a line number from 1 to the tab's line count; anything else is marked and ignored. */
public final class GoToLinePanel extends JPanel {
    private final JTextField field = new JTextField(8);
    private final int lineCount;
    private final IntConsumer go;

    /**
     * @param currentLine the caret's line, from 1
     * @param lineCount the tab's line count
     * @param go receives a valid line, from 1
     * @param cancel closes the dialog
     */
    public GoToLinePanel(int currentLine, int lineCount, IntConsumer go, Runnable cancel) {
        super(new BorderLayout(0, 10));
        this.lineCount = lineCount;
        this.go = go;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        field.setText(String.valueOf(currentLine));
        field.addActionListener(event -> submit());
        var ok = new JButton("Go");
        ok.addActionListener(event -> submit());
        var no = new JButton("Cancel");
        no.addActionListener(event -> cancel.run());
        var row = new JPanel(new BorderLayout(8, 0));
        row.add(new JLabel("Line (1–" + lineCount + "):"), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(no);
        buttons.add(ok);
        add(row, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        Forms.defaultButton(this, ok);
    }

    /** Goes to the typed line when it is valid; otherwise outlines the field in the theme's error colour. */
    public void submit() {
        try {
            int line = Integer.parseInt(field.getText().strip());
            if (line >= 1 && line <= lineCount) {
                field.putClientProperty("JComponent.outline", null);
                go.accept(line);
                return;
            }
        } catch (NumberFormatException notANumber) {
            // Marked below.
        }
        field.putClientProperty("JComponent.outline", "error");
    }

    JTextField field() { return field; }
}
