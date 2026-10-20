package dev.jasper.editor.vim.ui;

import dev.jasper.editor.vim.VimEditor;
import dev.jasper.sdk.ui.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.Timer;

/** Vim feedback model rendered by the document tab's kit view. Jasper's bell is a brief accent text flash. */
public final class VimBar {
    private static final int OUTPUT_ROWS = 12;
    private final Runnable changed;
    private final Consumer<String> reveal;
    private final Runnable listener = this::refresh;
    private final Timer flash;
    private VimEditor editor;
    private int bells;
    private boolean flashing;
    private List<VimEditor.OutputLine> output = List.of();

    /**
     * @param changed renders the current subtree before reveal runs
     * @param reveal scrolls the last output text into view
     */
    public VimBar(Runnable changed, Consumer<String> reveal) {
        this.changed = changed;
        this.reveal = reveal;
        flash = new Timer(150, event -> { flashing = false; changed.run(); });
        flash.setRepeats(false);
    }

    /** Attaches the tab's editor, without replaying an earlier bell. */
    public void attach(VimEditor attached) {
        detach();
        editor = attached;
        bells = attached.bells();
        attached.addListener(listener);
        refresh();
    }

    /** Revokes updates and stops the bell timer on Vim disable or tab close. */
    public void detach() {
        if (editor != null) editor.removeListener(listener);
        editor = null;
        flash.stop();
        flashing = false;
        output = List.of();
        changed.run();
    }

    /** The passive output rows above the mode/command strip; typography and colors belong to the kit. */
    public Nodes.Stack node(boolean documentVisible) {
        String status = " ";
        OptionalInt caret = OptionalInt.empty();
        Optional<Nodes.Severity> severity = Optional.empty();
        String command = " ";
        if (editor != null) {
            var line = editor.commandLineText();
            if (line.isPresent()) {
                status = visible(line.get());
                caret = OptionalInt.of(visible(line.get().substring(0, Math.min(line.get().length(), editor.commandLineCursor()))).length());
            } else if (editor.prompt().isPresent()) status = visible(editor.prompt().orElseThrow());
            else status = nonempty(editor.message().isEmpty() ? editor.modeText() : editor.message());
            if (flashing && status.isBlank()) status = "Bell";
            command = nonempty(editor.showcmd());
            severity = flashing ? Optional.of(Nodes.Severity.INFO)
                : editor.messageIsError() ? Optional.of(Nodes.Severity.DANGER) : Optional.empty();
        }
        var rows = new ArrayList<ListItem>();
        for (int i = 0; i < output.size(); i++) {
            var line = output.get(i);
            rows.add(new ListItem("vim.output.row." + i, Optional.empty(), List.of(new Nodes.Text(outputText(i), ControlState.READY,
                nonempty(visible(line.text())), Nodes.TextRole.MONO, line.error() ? Optional.of(Nodes.Severity.DANGER) : Optional.empty(), OptionalInt.empty())), List.of(), List.of()));
        }
        var list = new Nodes.ItemList("vim.output", new ControlState(!rows.isEmpty(), true, false, ""), Nodes.ListKind.COMPACT,
            rows, Set.of(), "", List.of(), Nodes.SelectionMode.NONE, Optional.empty(), OptionalInt.of(Math.max(1, Math.min(OUTPUT_ROWS, rows.size()))));
        var strip = new Nodes.Stack("vim.strip", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Text("vim.status", ControlState.READY, status, Nodes.TextRole.MONO, severity, caret),
            new Nodes.Text("vim.command", ControlState.READY, command, Nodes.TextRole.MONO)));
        return new Nodes.Stack("vim.bar", new ControlState(documentVisible && editor != null, true, false, ""), Nodes.LayoutRole.VERTICAL, List.of(list, strip));
    }

    private void refresh() {
        var lines = editor == null ? List.<VimEditor.OutputLine>of() : editor.output();
        boolean outputChanged = !output.equals(lines);
        output = List.copyOf(lines);
        if (editor != null && editor.bells() != bells) {
            bells = editor.bells();
            flashing = true;
            flash.restart();
        }
        changed.run();
        if (outputChanged && !output.isEmpty()) reveal.accept(outputText(output.size() - 1));
    }

    private static String outputText(int index) { return "vim.output.text." + index; }
    private static String nonempty(String text) { return text.isEmpty() ? " " : text; }

    /** CTRL-V control characters display as ^X; the descriptor caret is converted to that display index. */
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
}
