package dev.jasper.editor.vim.ui;

import dev.jasper.editor.vim.VimEditor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The strip under a tab while Vim mode is on (spec 5.2): the mode or the last message on the left, the partly typed
 * command on the right. The bell flashes it briefly; Jasper never beeps. While {@code :}, {@code /} or {@code ?} reads
 * a line, the left side is the command line, with its cursor drawn as a thin bar. When a command line says more than
 * one line, the output panel above the strip shows it (errors in red) until a key is typed, and the strip says so;
 * a question waiting for its answer ({@code :s///c}) shows there too.
 */
public final class VimBar extends JPanel {
    private static final Color ERROR = new Color(0xD9, 0x4F, 0x4F);
    /** The output panel shows at most this many lines at once; more scroll. */
    private static final int OUTPUT_ROWS = 12;

    private final CommandLabel status = new CommandLabel();
    private final DefaultListModel<VimEditor.OutputLine> outputModel = new DefaultListModel<>();
    private final JList<VimEditor.OutputLine> outputList = new JList<>(outputModel);
    private final JScrollPane output = new JScrollPane(outputList);
    private final JLabel command = label();
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
        outputList.setFocusable(false);
        outputList.setCellRenderer((list, line, index, selected, focused) -> {
            JLabel label = label();
            label.setText(line.text().isEmpty() ? " " : visible(line.text()));
            label.setFont(list.getFont());
            label.setForeground(line.error() ? ERROR : UIManager.getColor("Label.foreground"));
            return label;
        });
        output.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        output.setVisible(false);
        add(output, BorderLayout.NORTH);
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
        outputList.setFont(font);
        colors();
    }

    String statusText() { return status.getText().strip(); }

    /** @return the command line's cursor index while one is shown, else -1 */
    int commandLineCursor() { return status.caret; }

    String commandText() { return command.getText().strip(); }

    boolean flashing() { return flashing; }

    /** @return the output panel's lines while it is shown, else empty */
    java.util.List<String> outputLines() {
        if (!output.isVisible()) return java.util.List.of();
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < outputModel.size(); i++) lines.add(outputModel.get(i).text());
        return lines;
    }

    private void refresh() {
        if (editor == null) {
            status.setText(" ");
            command.setText(" ");
            showOutput(java.util.List.of());
            return;
        }
        showOutput(editor.output());
        var line = editor.commandLineText();
        var prompt = editor.prompt();
        if (line.isPresent()) {
            status.setText(visible(line.get()));
            status.caret = editor.commandLineCursor();
            status.beforeCaret = visible(line.get().substring(0, Math.min(line.get().length(), status.caret)));
            status.repaint();
        } else if (prompt.isPresent()) {
            status.setText(visible(prompt.get()));
            status.caret = -1;
        } else {
            String message = editor.message();
            String text = message.isEmpty() ? editor.showmode() ? editor.mode().label() : "" : message;
            // A space keeps the bar one line high when there is nothing to show.
            status.setText(text.isEmpty() ? " " : text);
            status.caret = -1;
        }
        command.setText(editor.showcmd().isEmpty() ? " " : editor.showcmd());
        if (editor.bells() != bells) {
            bells = editor.bells();
            flashing = true;
            flash.restart();
        }
        colors();
    }

    /** The output panel: shown with the lines, up to {@link #OUTPUT_ROWS} of them at once, or hidden. */
    private void showOutput(java.util.List<VimEditor.OutputLine> lines) {
        boolean same = lines.size() == outputModel.size();
        for (int i = 0; same && i < lines.size(); i++) same = lines.get(i).equals(outputModel.get(i));
        if (!same) {
            outputModel.clear();
            outputModel.addAll(lines);
            outputList.setVisibleRowCount(Math.max(1, Math.min(OUTPUT_ROWS, lines.size())));
            if (!lines.isEmpty()) outputList.ensureIndexIsVisible(lines.size() - 1);
        }
        if (output.isVisible() != !lines.isEmpty()) {
            output.setVisible(!lines.isEmpty());
            revalidate();
        }
    }

    private void colors() {
        Color background = UIManager.getColor("Panel.background");
        Color highlight = UIManager.getColor("Component.focusColor");
        setBackground(flashing && highlight != null ? highlight : background);
        outputList.setBackground(background);
        output.getViewport().setBackground(background);
        Color foreground = UIManager.getColor("Label.foreground");
        status.setForeground(editor != null && editor.messageIsError() ? ERROR : foreground);
        command.setForeground(foreground);
    }

    /** Control characters typed with CTRL-V show as {@code ^X}, as on Vim's command line; the cursor index is kept. */
    private static String visible(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20) out.append('^').append((char) (c + '@'));
            else if (c == 0x7f) out.append("^?");
            else out.append(c);
        }
        return out.toString();
    }

    /** The left label, which draws the command line's cursor after {@link #beforeCaret}. */
    private static final class CommandLabel extends JLabel {
        int caret = -1;
        String beforeCaret = "";

        CommandLabel() {
            super(" ");
            putClientProperty("html.disable", true);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (caret < 0) return;
            FontMetrics metrics = g.getFontMetrics(getFont());
            int x = getInsets().left + metrics.stringWidth(beforeCaret);
            g.setColor(getForeground());
            g.fillRect(x, getInsets().top + 1, 1, Math.max(1, getHeight() - getInsets().top - getInsets().bottom - 2));
        }
    }

    private static JLabel label() {
        var label = new JLabel(" ");
        label.putClientProperty("html.disable", true);
        return label;
    }
}
