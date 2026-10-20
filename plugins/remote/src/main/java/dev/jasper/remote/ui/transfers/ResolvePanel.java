package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.ConflictDecision;
import dev.jasper.remote.transfer.TransferEntry;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * One transfer entry's decision on a host-rendered grouped kit form: a Conflict card with the entry's path (technical
 * text) and what went wrong, then the "Apply to the rest of this transfer" setting; the footer offers Cancel, Restart
 * file, Rename, Skip and the kind-appropriate Replace (files) or Merge (folders), each enabled only where it fits.
 * Replace or Merge is the visual primary, but the form opts out of submission, so Enter decides nothing (the former
 * dialog had no default button); Escape cancels. The entry's path and error are literal text. UI thread only;
 * {@link #close()} makes late callbacks inert.
 */
public final class ResolvePanel implements AutoCloseable {
    public record Choice(ConflictDecision decision, boolean remaining) { }
    public static final String FORM = "resolve", PATH = "resolve.path", ERROR = "resolve.error", REMAINING = "resolve.remaining",
        REMAINING_ROW = "resolve.remaining-row", REPLACE = "resolve.replace", MERGE = "resolve.merge", SKIP = "resolve.skip",
        RENAME = "resolve.rename", RESTART = "resolve.restart", CANCEL = "resolve.cancel", SECTION = "resolve.conflict";
    private final TransferEntry entry;
    private final boolean folder, replaceable, mergeable;
    private final UiView view;
    private boolean remaining, closed;

    public ResolvePanel(UiKit kit, TransferEntry entry, Consumer<Choice> decide, Runnable restartFile, Runnable close) {
        this.entry = entry;
        folder = entry.sourceInfo().kind() == FileEntry.Kind.DIRECTORY;
        replaceable = entry.expectedTarget().filter(target -> target.kind() == entry.sourceInfo().kind()).isPresent();
        mergeable = entry.expectedTarget().filter(target -> target.kind() == FileEntry.Kind.DIRECTORY).isPresent();
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed) return;
            switch (event) {
                case UiEvent.Toggled toggled when toggled.id().equals(REMAINING) -> { remaining = toggled.checked(); render(); }
                case UiEvent.Action action -> {
                    switch (action.id()) {
                        case REPLACE -> { if (!folder && replaceable) decide.accept(new Choice(ConflictDecision.REPLACE, remaining)); }
                        case MERGE -> { if (folder && mergeable) decide.accept(new Choice(ConflictDecision.MERGE, remaining)); }
                        case SKIP -> decide.accept(new Choice(ConflictDecision.SKIP, remaining));
                        case RENAME -> decide.accept(new Choice(ConflictDecision.RENAME, false));
                        case RESTART -> restartFile.run();
                        case CANCEL -> close.run();
                        default -> { }
                    }
                }
                default -> { }
            }
        });
    }

    public UiView view() { return view; }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private Nodes.Form form() {
        var card = new Nodes.Card(SECTION + ".card", ControlState.READY, Nodes.CardKind.MIXED, List.of(
            new Nodes.FormRow(SECTION + ".path-row", ControlState.READY, folder ? "Folder" : "File", "",
                new Nodes.Text(PATH, ControlState.READY, entry.relative(), Nodes.TextRole.MONO), Optional.empty()),
            new Nodes.FormRow(SECTION + ".error-row", new ControlState(!entry.error().isBlank(), true, false, ""), "", "",
                new Nodes.Text(ERROR, ControlState.READY, entry.error(), Nodes.TextRole.SUPPORTING, Optional.of(Nodes.Severity.WARNING), OptionalInt.empty()), Optional.empty()),
            new Nodes.SettingRow(REMAINING_ROW, ControlState.READY, "Apply to the rest of this transfer", "Use this answer for every other conflict it meets",
                new Nodes.Toggle(REMAINING, ControlState.READY, "Apply to the rest of this transfer", remaining, REMAINING, false), false)));
        var actions = List.of(
            button(CANCEL, "Cancel", true, true, Nodes.ButtonKind.SECONDARY),
            button(RESTART, "Restart file", true, true, Nodes.ButtonKind.SECONDARY),
            button(RENAME, "Rename…", true, true, Nodes.ButtonKind.SECONDARY),
            button(SKIP, "Skip", true, true, Nodes.ButtonKind.SECONDARY),
            button(REPLACE, "Replace", !folder, replaceable, folder ? Nodes.ButtonKind.SECONDARY : Nodes.ButtonKind.PRIMARY),
            button(MERGE, "Merge", folder, mergeable, folder ? Nodes.ButtonKind.PRIMARY : Nodes.ButtonKind.SECONDARY));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Resolve transfer", Optional.empty(),
            List.of(new Nodes.Section(SECTION, ControlState.READY, "Conflict", card)), Optional.empty(), actions, CANCEL, Nodes.FormSubmission.NONE);
    }

    private static Nodes.Button button(String id, String label, boolean visible, boolean enabled, Nodes.ButtonKind kind) {
        return new Nodes.Button(id, new ControlState(visible, enabled, false, ""), label, "", kind, id, "");
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
