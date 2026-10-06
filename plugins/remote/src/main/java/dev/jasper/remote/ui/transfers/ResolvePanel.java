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
import java.util.function.Consumer;

/**
 * One transfer entry's decision on a host-rendered kit form: Replace for files, Merge for folders, Skip, Rename or Restart
 * file, each enabled only where it fits, and "Apply to the rest of this transfer" (a switch). No button is the form's
 * primary, so Enter decides nothing (the former dialog had no default button); Escape cancels. The entry's path and error
 * are literal text. UI thread only; {@link #close()} makes late callbacks inert.
 */
public final class ResolvePanel implements AutoCloseable {
    public record Choice(ConflictDecision decision, boolean remaining) { }
    public static final String FORM = "resolve", PATH = "resolve.path", ERROR = "resolve.error", REMAINING = "resolve.remaining",
        REMAINING_ROW = "resolve.remaining-row", REPLACE = "resolve.replace", MERGE = "resolve.merge", SKIP = "resolve.skip",
        RENAME = "resolve.rename", RESTART = "resolve.restart", CANCEL = "resolve.cancel";
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
        var sections = List.<Nodes.Node>of(
            new Nodes.Text(PATH, ControlState.READY, entry.relative(), Nodes.TextRole.BODY_STRONG),
            new Nodes.Text(ERROR, ControlState.READY, entry.error(), Nodes.TextRole.BODY),
            new Nodes.SettingRow(REMAINING_ROW, ControlState.READY, "Apply to the rest of this transfer", "",
                new Nodes.Toggle(REMAINING, ControlState.READY, "Apply to the rest of this transfer", remaining, REMAINING, false), false));
        var actions = List.of(
            button(REPLACE, "Replace", !folder, replaceable),
            button(MERGE, "Merge", folder, mergeable),
            button(SKIP, "Skip", true, true),
            button(RENAME, "Rename…", true, true),
            button(RESTART, "Restart file", true, true),
            button(CANCEL, "Cancel", true, true));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Resolve transfer", Optional.empty(), sections, Optional.empty(), actions, CANCEL);
    }

    private static Nodes.Button button(String id, String label, boolean visible, boolean enabled) {
        return new Nodes.Button(id, new ControlState(visible, enabled, false, ""), label, "", Nodes.ButtonKind.SECONDARY, id, "");
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
