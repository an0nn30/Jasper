package dev.jasper.remote.ui;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Edits the display name used for hosts with no explicit group. Enter renames from the name field only (its trailing
 * action, as its action listener did); no button is the form's primary, and Escape cancels. UI thread only.
 */
public final class GroupNamePanel implements AutoCloseable {
    public static final String FORM = "group-name", NAME = "group-name.name", ERROR = "group-name.error", RENAME = "group-name.rename",
        RENAME_BUTTON = "group-name.rename-button", CANCEL = "group-name.cancel";
    private final UiView view;
    private String name, error = "";
    private boolean closed;

    public GroupNamePanel(UiKit kit, String current, Consumer<String> rename, Runnable cancel) {
        name = current;
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed) return;
            switch (event) {
                case UiEvent.Edited edited when edited.id().equals(NAME) -> { name = edited.text(); render(); }
                case UiEvent.Action action when action.id().equals(RENAME) -> rename.accept(name);
                case UiEvent.Action action when action.id().equals(CANCEL) -> cancel.run();
                default -> { }
            }
        });
    }

    public UiView view() { return view; }

    public void showError(String message) { if (closed) return; error = message; render(); }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private Nodes.Form form() {
        var sections = List.<Nodes.Node>of(
            new Nodes.Field(NAME, ControlState.READY, Nodes.FieldKind.TEXT, name, "", "Default group name", RENAME),
            new Nodes.Text(ERROR, new ControlState(!error.isEmpty(), true, false, ""), error, Nodes.TextRole.BODY));
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(RENAME_BUTTON, ControlState.READY, "Rename", "", Nodes.ButtonKind.SECONDARY, RENAME, ""));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Rename default group", Optional.empty(), sections, Optional.empty(), actions, CANCEL);
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
