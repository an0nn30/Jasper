package dev.jasper.app.windows;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * The file chooser's two questions as modal kit sheets over the chooser, in place of option panes: whether to replace
 * an existing file, and a new folder's name. They follow the dialog rules: the name field is focused, Enter gives the
 * primary answer (Create is disabled while the name is blank), Escape or closing the window cancels, and focus returns
 * to the chooser when the sheet closes. Headless and testable; {@link NativeShells} supplies the modal window. EDT only.
 */
public final class ChooserPrompts {
    /** A modal window over the chooser; {@link #show} returns once it closes. */
    public interface Modal {
        /** Puts the sheet in the window. */
        void setContent(JComponent content);

        /** Shows the window and blocks until it closes; {@code shown} runs once it is on screen. */
        void show(Runnable shown);

        /** Closes the window; harmless when it is already closed. */
        void close();
    }

    static final String REPLACE = "replace", CANCEL = "cancel", CREATE = "create", NAME = "name";
    private final Supplier<KitContext> kit;
    private final Function<String, Modal> modals;

    /**
     * @param kit the chrome kit context the sheets are built with
     * @param modals makes a modal window over the chooser with the given title
     */
    public ChooserPrompts(Supplier<KitContext> kit, Function<String, Modal> modals) {
        this.kit = Objects.requireNonNull(kit, "kit");
        this.modals = Objects.requireNonNull(modals, "modals");
    }

    /** Whether to replace {@code name}, which already exists. No unless the user answers Replace. */
    public boolean confirmReplace(String name) {
        Modal modal = modals.apply("Confirm Save As");
        boolean[] replace = {false};
        var view = new KitView(kit.get(), new ViewSpec(new Nodes.Form("replace-sheet", ControlState.READY, Nodes.FormKind.SHEET,
            "Replace Existing File?", Optional.empty(),
            List.of(new Nodes.Text("replace-message", ControlState.READY, name + " already exists. Do you want to replace it?", Nodes.TextRole.DESCRIPTION)),
            Optional.empty(), List.of(new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
                new Nodes.Button(REPLACE, ControlState.READY, "Replace", "", Nodes.ButtonKind.PRIMARY, REPLACE, "")), CANCEL)),
            event -> {
                if (!(event instanceof UiEvent.Action action)) return;
                if (action.id().equals(REPLACE)) replace[0] = true;
                if (action.id().equals(REPLACE) || action.id().equals(CANCEL)) modal.close();
            });
        try {
            modal.setContent(view);
            modal.show(() -> { });
        } finally {
            modal.close();
            view.close();
        }
        return replace[0];
    }

    /** A new folder's name, stripped; empty when the user cancels. */
    public Optional<String> askFolderName() {
        Modal modal = modals.apply("New Folder");
        String[] typed = {""};
        var answer = new java.util.concurrent.atomic.AtomicReference<Optional<String>>(Optional.empty());
        KitView[] view = new KitView[1];
        view[0] = new KitView(kit.get(), folderSpec(""), event -> {
            switch (event) {
                case UiEvent.Edited edited when edited.id().equals(NAME) -> {
                    typed[0] = edited.text();
                    if (view[0] != null && !view[0].isClosed()) view[0].update(folderSpec(typed[0]));
                }
                case UiEvent.Action action when action.id().equals(CREATE) -> {
                    if (typed[0].strip().isEmpty()) return;
                    answer.set(Optional.of(typed[0].strip()));
                    modal.close();
                }
                case UiEvent.Action action when action.id().equals(CANCEL) -> modal.close();
                default -> { }
            }
        });
        try {
            modal.setContent(view[0]);
            modal.show(() -> { if (!view[0].isClosed()) view[0].focus(NAME); });
        } finally {
            modal.close();
            view[0].close();
        }
        return answer.get();
    }

    private static ViewSpec folderSpec(String value) {
        boolean ready = !value.strip().isEmpty();
        return new ViewSpec(new Nodes.Form("folder-sheet", ControlState.READY, Nodes.FormKind.SHEET, "New Folder", Optional.empty(),
            // The canonical sheet body: a form card with one labelled row.
            List.of(new Nodes.Card("folder-card", ControlState.READY, Nodes.CardKind.FORM, List.of(new Nodes.FormRow("folder-row", ControlState.READY,
                "Name", "", new Nodes.Field(NAME, ControlState.READY, Nodes.FieldKind.TEXT, value, "", "Folder name", ""), Optional.empty())))), Optional.empty(),
            List.of(new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
                new Nodes.Button(CREATE, new ControlState(true, ready, false, ""), "Create", "", Nodes.ButtonKind.PRIMARY, CREATE, "")), CANCEL));
    }
}
