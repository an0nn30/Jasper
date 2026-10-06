package dev.jasper.vault.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.vault.service.KeyImportPrompt;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Passive import review and passphrase controls; metadata is literal text, never markup. Enter keeps the former
 * dialog's meaning exactly: the form has no primary (the dialog had no default button), Enter in the passphrase field
 * unlocks the key (its action listener did) and Enter anywhere else does nothing, so it never commits. Escape cancels.
 */
public final class KeyImportPanel implements AutoCloseable {
    static final String FORM = "import", STATUS = "status", KEYS = "keys", PASSPHRASE = "passphrase", UNLOCK = "unlock-key",
        COMMIT = "commit", CANCEL = "cancel";
    private final KeyImportPrompt prompt;
    private final UiView view;
    private final Subscription subscription;
    private boolean closed, waiting;

    public KeyImportPanel(UiKit kit, KeyImportPrompt prompt) {
        this.prompt = prompt;
        view = kit.create(new ViewSpec(node()), this::handle);
        subscription = prompt.onChanged(this::refresh);
        refresh();
    }

    public UiView view() { return view; }

    private void handle(UiEvent event) {
        if (closed || !(event instanceof UiEvent.Action action)) return;
        switch (action.id()) {
            case UNLOCK -> {
                if (prompt.phase() != KeyImportPrompt.Phase.PASSPHRASE) return;
                char[][] typed = {new char[0]};
                view.withSecret(PASSPHRASE, chars -> typed[0] = chars.clone());
                view.clearSecret(PASSPHRASE);
                prompt.submitPassphrase(typed[0]); // the prompt owns and erases its copy
            }
            case COMMIT -> prompt.commit();
            case CANCEL -> { if (prompt.phase() != KeyImportPrompt.Phase.COMMITTING) prompt.cancel(); }
            default -> { }
        }
    }

    private void refresh() {
        if (closed) return;
        boolean wanted = prompt.phase() == KeyImportPrompt.Phase.PASSPHRASE;
        if (!wanted) view.clearSecret(PASSPHRASE);
        view.update(new ViewSpec(node()));
        if (wanted && !waiting) view.focus(PASSPHRASE);
        waiting = wanted;
    }

    private Nodes.Form node() {
        var phase = prompt.phase();
        boolean wanted = phase == KeyImportPrompt.Phase.PASSPHRASE;
        var items = new ArrayList<ListItem>();
        int index = 0;
        for (var row : prompt.rows()) {
            String id = "key-" + index++;
            items.add(new ListItem(id, Optional.empty(),
                List.<Nodes.Node>of(new Nodes.Text(id + ".name", ControlState.READY, row.name(), Nodes.TextRole.BODY)),
                List.<Nodes.Node>of(new Nodes.Text(id + ".fingerprint", ControlState.READY, row.fingerprint(), Nodes.TextRole.MONO_SMALL)),
                List.<Nodes.Node>of(new Nodes.Chip(id + ".use", ControlState.READY, row.reused() ? "reuse" : "new", row.reused() ? Nodes.Hue.NEUTRAL : Nodes.Hue.GREEN))));
        }
        var sections = List.<Nodes.Node>of(
            new Nodes.Text(STATUS, ControlState.READY, prompt.status(), Nodes.TextRole.BODY),
            new Nodes.ItemList(KEYS, ControlState.READY, Nodes.ListKind.DOUBLE, items, Set.of(), "", List.of()),
            new Nodes.Secret(PASSPHRASE, new ControlState(wanted, true, false, ""), "", "Key passphrase", "", Nodes.SecretKind.INPUT, "", UNLOCK));
        var actions = new ArrayList<Nodes.Button>();
        actions.add(new Nodes.Button(CANCEL, new ControlState(true, phase != KeyImportPrompt.Phase.COMMITTING, false, ""), "Cancel", "",
            Nodes.ButtonKind.SECONDARY, CANCEL, ""));
        actions.add(new Nodes.Button(COMMIT, new ControlState(true, phase == KeyImportPrompt.Phase.REVIEW, phase == KeyImportPrompt.Phase.COMMITTING, ""),
            "Import and use", "", Nodes.ButtonKind.SECONDARY, COMMIT, ""));
        if (wanted) actions.add(new Nodes.Button(UNLOCK, ControlState.READY, "Unlock key", "", Nodes.ButtonKind.SECONDARY, UNLOCK, ""));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Import SSH keys", Optional.empty(), sections, Optional.empty(), actions,
            phase == KeyImportPrompt.Phase.COMMITTING ? "" : CANCEL);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        subscription.close();
        view.close();
    }
}
