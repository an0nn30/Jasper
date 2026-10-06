package dev.jasper.remote.ui.sftp;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * "New folder" on a host-rendered kit form: the folder name and Create. Enter creates from the name field only (its
 * trailing action, as its action listener did); Create is no default button, and Escape closes the dialog. A rejected
 * name shows its reason beside the button. UI thread only; {@link #close()} makes late callbacks inert.
 */
final class FolderNamePanel implements AutoCloseable {
    static final String FORM="new-folder", NAME="new-folder.name", ERROR="new-folder.error", CREATE="new-folder.create",
        CREATE_BUTTON="new-folder.create-button", CLOSE="new-folder.close";
    private final UiView view;
    private String name="",error="";
    private boolean closed;

    /** {@code create} takes the typed name and returns why it was refused, or empty once accepted. */
    FolderNamePanel(UiKit kit,Function<String,Optional<String>> create,Runnable close) {
        view=kit.create(new ViewSpec(form()),event-> {
            if(closed) return;
            switch(event) {
                case UiEvent.Edited edited when edited.id().equals(NAME) -> { name=edited.text();render(); }
                case UiEvent.Action action when action.id().equals(CREATE) -> create.apply(name).ifPresent(reason-> { error=reason;render(); });
                case UiEvent.Action action when action.id().equals(CLOSE) -> close.run();
                default -> { }
            }
        });
    }

    UiView view() { return view; }

    String error() { return error; }

    private void render() { if(!closed) view.update(new ViewSpec(form())); }

    private Nodes.Form form() {
        return new Nodes.Form(FORM,ControlState.READY,Nodes.FormKind.FULL,"New folder",Optional.empty(),
            List.of(new Nodes.Field(NAME,ControlState.READY,Nodes.FieldKind.TEXT,name,"","Folder name",CREATE)),
            Optional.of(new Nodes.Text(ERROR,new ControlState(!error.isEmpty(),true,false,""),error,Nodes.TextRole.BODY)),
            List.of(new Nodes.Button(CREATE_BUTTON,ControlState.READY,"Create","",Nodes.ButtonKind.SECONDARY,CREATE,"")),CLOSE);
    }

    @Override public void close() { if(closed) return;closed=true;view.close(); }
}
