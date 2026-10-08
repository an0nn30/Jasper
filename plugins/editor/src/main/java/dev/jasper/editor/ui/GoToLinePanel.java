package dev.jasper.editor.ui;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * Go to Line on an owned kit sheet: a line number from 1 to the tab's line count. Enter in the field submits (its
 * trailing action, as its action listener did) and Go is the form's primary, the former default button; anything else
 * puts an error on the field (which keeps the dialog open and disables Go until the next edit). Escape cancels.
 * UI thread only; the surface that mounts {@link #view()} disposes it, and {@link #close()} makes late calls inert.
 */
public final class GoToLinePanel implements AutoCloseable {
    public static final String FORM = "go-to-line", LINE = "go-to-line.line", GO = "go-to-line.go", CANCEL = "go-to-line.cancel",
        GO_ACTION = "go", CANCEL_ACTION = "cancel";
    private final UiView view;
    private final int lineCount;
    private final IntConsumer go;
    private String text, error = "";
    private boolean closed;

    /**
     * @param kit the plugin's kit
     * @param currentLine the caret's line, from 1
     * @param lineCount the tab's line count
     * @param go receives a valid line, from 1
     * @param cancel closes the dialog
     */
    public GoToLinePanel(UiKit kit, int currentLine, int lineCount, IntConsumer go, Runnable cancel) {
        this.lineCount = lineCount;
        this.go = go;
        text = String.valueOf(currentLine);
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed) return;
            switch (event) {
                case UiEvent.Edited edited when edited.id().equals(LINE) -> { text = edited.text(); error = ""; render(); }
                case UiEvent.Action action when action.id().equals(GO_ACTION) -> submit();
                case UiEvent.Action action when action.id().equals(CANCEL_ACTION) -> cancel.run();
                default -> { }
            }
        });
    }

    /** @return the owned view to mount */
    public UiView view() { return view; }

    /** Goes to the typed line when it is valid; otherwise marks the field with an error and stays open. */
    public void submit() {
        if (closed) return;
        try {
            int line = Integer.parseInt(text.strip());
            if (line >= 1 && line <= lineCount) {
                if (!error.isEmpty()) { error = ""; render(); }
                go.accept(line);
                return;
            }
        } catch (NumberFormatException notANumber) {
            // Marked below.
        }
        error = "Enter a line from 1 to " + lineCount;
        render();
    }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private Nodes.Form form() {
        var field = new Nodes.Field(LINE, new ControlState(true, true, false, error), Nodes.FieldKind.TEXT, text, "",
            "Line (1–" + lineCount + "):", GO_ACTION);
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL_ACTION, ""),
            new Nodes.Button(GO, ControlState.READY, "Go", "", Nodes.ButtonKind.PRIMARY, GO_ACTION, ""));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.SHEET, "Go to Line", Optional.empty(), List.of(field), Optional.empty(),
            actions, CANCEL_ACTION);
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
