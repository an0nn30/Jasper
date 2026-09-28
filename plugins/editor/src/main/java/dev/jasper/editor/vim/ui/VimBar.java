package dev.jasper.editor.vim.ui;

import dev.jasper.editor.vim.VimEditor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The strip under a tab while Vim mode is on (spec 5.2): the mode or the last message on the left, the partly typed
 * command on the right. The bell flashes it briefly; Jasper never beeps. Plan V2 adds the command line.
 */
public final class VimBar extends JPanel {
    private static final Color ERROR = new Color(0xD9, 0x4F, 0x4F);

    private final JLabel status = label(), command = label();
    private final Runnable listener = this::refresh;
    private final Timer flash;
    private VimEditor editor;
    private int bells;
    private boolean flashing;

    /** An empty bar; {@link #attach} gives it an editor. */
    public VimBar() {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(status, BorderLayout.CENTER);
        add(command, BorderLayout.EAST);
        flash = new Timer(150, event -> {
            flashing = false;
            colors();
        });
        flash.setRepeats(false);
        colors();
    }

    /** @param attached the tab's Vim editor, whose mode, messages and bell the bar shows */
    public void attach(VimEditor attached) {
        detach();
        editor = attached;
        bells = attached.bells();
        attached.addListener(listener);
        refresh();
    }

    /** Stops showing the editor. */
    public void detach() {
        if (editor != null) editor.removeListener(listener);
        editor = null;
        flash.stop();
        flashing = false;
        refresh();
    }

    /** @param font the Editor's font */
    public void apply(Font font) {
        status.setFont(font);
        command.setFont(font);
        colors();
    }

    String statusText() { return status.getText().strip(); }

    String commandText() { return command.getText().strip(); }

    boolean flashing() { return flashing; }

    private void refresh() {
        if (editor == null) {
            status.setText(" ");
            command.setText(" ");
            return;
        }
        String message = editor.message();
        String text = message.isEmpty() ? editor.showmode() ? editor.mode().label() : "" : message;
        // A space keeps the bar one line high when there is nothing to show.
        status.setText(text.isEmpty() ? " " : text);
        command.setText(editor.showcmd().isEmpty() ? " " : editor.showcmd());
        if (editor.bells() != bells) {
            bells = editor.bells();
            flashing = true;
            flash.restart();
        }
        colors();
    }

    private void colors() {
        Color background = UIManager.getColor("Panel.background");
        Color highlight = UIManager.getColor("Component.focusColor");
        setBackground(flashing && highlight != null ? highlight : background);
        Color foreground = UIManager.getColor("Label.foreground");
        status.setForeground(editor != null && editor.messageIsError() ? ERROR : foreground);
        command.setForeground(foreground);
    }

    private static JLabel label() {
        var label = new JLabel(" ");
        label.putClientProperty("html.disable", true);
        return label;
    }
}
