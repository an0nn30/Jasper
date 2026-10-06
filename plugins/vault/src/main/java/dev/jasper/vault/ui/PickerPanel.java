package dev.jasper.vault.ui;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.service.PickPrompt;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** The account picker: a list of descriptors (rows keyed by credential id), Choose and Cancel. UI thread only. */
public final class PickerPanel implements AutoCloseable {
    static final String FORM = "picker", LIST = "choices", CHOOSE = "choose", CANCEL = "cancel";
    /** Full rows the former 240-pixel list showed (10.8 at its 22-pixel rows), measured at baseline f16ae32f. */
    static final int VISIBLE_ROWS = 10;
    private final List<CredentialDescriptor> choices;
    private final Consumer<UUID> onChoose;
    private final Runnable onCancel;
    private final UiView view;
    private String selected = "";
    private boolean closed;

    public PickerPanel(UiKit kit, PickPrompt prompt) { this(kit, prompt.choices(), prompt::choose, prompt::cancel); }

    PickerPanel(UiKit kit, List<CredentialDescriptor> choices, Consumer<UUID> onChoose, Runnable onCancel) {
        this.choices = List.copyOf(choices); this.onChoose = onChoose; this.onCancel = onCancel;
        view = kit.create(new ViewSpec(node()), this::handle);
    }

    public UiView view() { return view; }

    static String label(CredentialDescriptor descriptor) {
        return descriptor.subtitle().isBlank() ? descriptor.name() : descriptor.name() + "  —  " + descriptor.subtitle();
    }

    private void handle(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Selected chosen when chosen.id().equals(LIST) -> {
                selected = chosen.ids().stream().findFirst().orElse(""); view.update(new ViewSpec(node()));
            }
            case UiEvent.Action action when action.id().equals(CHOOSE) -> { if (!selected.isEmpty()) onChoose.accept(UUID.fromString(selected)); }
            case UiEvent.Action action when action.id().equals(CANCEL) -> onCancel.run();
            default -> { }
        }
    }

    /**
     * No activate action: as with the former list, a double click selects only and Enter reaches the form, which
     * presses Choose (the default button) once a row is selected.
     */
    private Nodes.Form node() {
        var items = choices.stream().map(d -> new ListItem(d.id().toString(), Optional.empty(),
            List.<Nodes.Node>of(new Nodes.Text("title." + d.id(), ControlState.READY, label(d), Nodes.TextRole.BODY)), List.of(), List.of())).toList();
        var list = new Nodes.ItemList(LIST, ControlState.READY, Nodes.ListKind.SINGLE, items, selected.isEmpty() ? Set.of() : Set.of(selected), "",
            List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(VISIBLE_ROWS));
        var actions = List.of(new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(CHOOSE, new ControlState(true, !selected.isEmpty(), false, ""), "Choose", "", Nodes.ButtonKind.PRIMARY, CHOOSE, ""));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Choose a credential", Optional.empty(), List.of(list), Optional.empty(), actions, CANCEL);
    }

    String selected() { return selected; }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
