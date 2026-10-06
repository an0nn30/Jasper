package dev.jasper.remote.ui.transfers;

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
 * "Rename destination" for one conflicting transfer entry, on a host-rendered kit form: the name field starts with the
 * source's name and "Use name" applies it. As before, Enter in the field applies nothing (the former field had no action
 * and the dialog no default button); Escape closes the dialog. UI thread only; {@link #close()} makes late callbacks inert.
 */
public final class RenamePanel implements AutoCloseable {
    public static final String FORM = "rename", NAME = "rename.name", USE = "rename.use", CLOSE = "rename.close";
    private final UiView view;
    private String name;
    private boolean closed;

    public RenamePanel(UiKit kit, String initial, Consumer<String> use, Runnable close) {
        name = initial;
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed) return;
            switch (event) {
                case UiEvent.Edited edited when edited.id().equals(NAME) -> { name = edited.text(); render(); }
                case UiEvent.Action action when action.id().equals(USE) -> use.accept(name);
                case UiEvent.Action action when action.id().equals(CLOSE) -> close.run();
                default -> { }
            }
        });
    }

    public UiView view() { return view; }

    /** The name as typed. */
    public String name() { return name; }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private Nodes.Form form() {
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Rename destination", Optional.empty(),
            List.of(new Nodes.Field(NAME, ControlState.READY, Nodes.FieldKind.TEXT, name, "", "Destination name", "")), Optional.empty(),
            List.of(new Nodes.Button(USE, ControlState.READY, "Use name", "", Nodes.ButtonKind.SECONDARY, USE, "")), CLOSE);
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
