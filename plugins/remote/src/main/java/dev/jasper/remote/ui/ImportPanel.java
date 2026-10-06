package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.ConfigImport;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Passive, editable config preview on a host-rendered kit form. Existing rows require an explicit Update selection;
 * rows with errors cannot be chosen. No button is the form's primary, so Enter never imports (the former dialog had no
 * default button); Escape cancels. Candidate text is literal. UI thread only.
 */
public final class ImportPanel implements AutoCloseable {
    public static final String FORM = "import", EMPTY = "import.empty", SKIPPED = "import.skipped", STATUS = "import.status",
        REFRESH = "import.refresh", CANCEL = "import.cancel", IMPORT = "import.import";
    private final List<ConfigImport.Candidate> candidates;
    private final List<Boolean> checked = new ArrayList<>();
    private final List<String> details = new ArrayList<>();
    private final String skipped;
    private final UiView view;
    private String status = "", cancelLabel = "Cancel";
    private boolean enabled = true, closed;

    public static String check(int row) { return "import.check." + row; }
    public static String detail(int row) { return "import.detail." + row; }
    public static String choose(int row) { return "import.choose." + row; }

    public ImportPanel(UiKit kit, List<ConfigImport.Candidate> candidates, List<String> skippedEntries,
            Consumer<List<ConfigImport.Candidate>> importSelected, Consumer<ConfigImport.Candidate> pick, Runnable onRefresh, Runnable onCancel) {
        this.candidates = List.copyOf(candidates);
        for (var c : this.candidates) {
            checked.add(c.previous().isEmpty() && c.errors().isEmpty());
            details.add(!c.errors().isEmpty() ? String.join("; ", c.errors()) : c.identities().isEmpty() ? "Choose a stored Vault key or password"
                : String.join("\n", c.identities().stream().map(Object::toString).toList()));
        }
        skipped = skippedEntries.isEmpty() ? "" : "Not importable: " + String.join(", ", skippedEntries);
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed) return;
            switch (event) {
                case UiEvent.Toggled toggled -> {
                    for (int i = 0; i < this.candidates.size(); i++)
                        if (toggled.id().equals(check(i)) && enabled && this.candidates.get(i).errors().isEmpty()) { checked.set(i, toggled.checked()); render(); }
                }
                case UiEvent.Action action when action.id().equals(IMPORT) -> {
                    var selected = new ArrayList<ConfigImport.Candidate>();
                    for (int i = 0; i < this.candidates.size(); i++) if (checked.get(i) && this.candidates.get(i).errors().isEmpty()) selected.add(this.candidates.get(i));
                    importSelected.accept(List.copyOf(selected));
                }
                case UiEvent.Action action when action.id().equals(CANCEL) -> onCancel.run();
                case UiEvent.Action action when action.id().equals(REFRESH) -> onRefresh.run();
                case UiEvent.Action action -> {
                    for (int i = 0; i < this.candidates.size(); i++) if (action.id().equals(choose(i))) pick.accept(this.candidates.get(i));
                }
                default -> { }
            }
        });
    }

    public UiView view() { return view; }

    /** The status line as shown. */
    public String status() { return status; }

    public void credential(UUID candidate, String name) {
        for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).id().equals(candidate)) details.set(i, "Vault: " + name);
        render();
    }
    public void busy() { enabled = false; status = "Reviewing credentials in Vault…"; render(); }
    public void editable() { enabled = true; render(); }
    public void failed(String message) { enabled = true; status = message; render(); }
    public void completed(String message) { enabled = false; cancelLabel = "Close"; status = message; render(); }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private static ControlState state(boolean visible, boolean enabled) { return new ControlState(visible, enabled, false, ""); }

    private Nodes.Form form() {
        var sections = new ArrayList<Nodes.Node>();
        if (candidates.isEmpty()) sections.add(new Nodes.Text(EMPTY, ControlState.READY, "No importable Host entries found", Nodes.TextRole.BODY));
        for (int i = 0; i < candidates.size(); i++) {
            var c = candidates.get(i);
            boolean choosable = enabled && c.errors().isEmpty();
            String label = (c.previous().isPresent() ? "Update: " : "New: ") + c.name() + " — " + c.username() + "@" + c.hostname() + ":" + c.port();
            sections.add(new Nodes.Stack("import.row." + i, ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                new Nodes.Stack("import.line." + i, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                    new Nodes.Toggle(check(i), state(true, choosable), label, checked.get(i), check(i), false),
                    new Nodes.Button(choose(i), state(c.identities().isEmpty(), choosable), "Choose credential…", "", Nodes.ButtonKind.SECONDARY, choose(i), ""))),
                new Nodes.Text(detail(i), ControlState.READY, details.get(i), Nodes.TextRole.DESCRIPTION))));
        }
        sections.add(new Nodes.Text(SKIPPED, state(!skipped.isEmpty(), true), skipped, Nodes.TextRole.DESCRIPTION));
        var actions = List.of(
            new Nodes.Button(REFRESH, state(true, enabled), "Refresh", "", Nodes.ButtonKind.SECONDARY, REFRESH, ""),
            new Nodes.Button(CANCEL, ControlState.READY, cancelLabel, "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(IMPORT, state(true, enabled && !candidates.isEmpty()), "Import", "", Nodes.ButtonKind.SECONDARY, IMPORT, ""));
        // The status sits beside the buttons, as it sat above them, so a long preview never scrolls it away.
        var statusLine = new Nodes.Text(STATUS, state(!status.isEmpty(), true), status, Nodes.TextRole.BODY);
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Import from ~/.ssh/config", Optional.empty(), sections, Optional.of(statusLine), actions, CANCEL);
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
